<template>
  <div class="p-6 max-w-4xl">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Realms</h1>
        <p class="text-sm text-gray-500 mt-1">Logical partitions of this directory with separate user/group base DNs and objectclass configuration</p>
      </div>
      <button @click="openCreate" class="btn-primary">+ New Realm</button>
    </div>

    <div class="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <div v-if="loading" class="p-8 text-center text-gray-500 text-sm">Loading…</div>
      <div v-else-if="realms.length === 0" class="p-8 text-center text-gray-400 text-sm">
        No realms configured. Create one to define user/group scopes within this directory.
      </div>
      <table v-else class="w-full text-sm">
        <thead class="bg-gray-50 border-b border-gray-100">
          <tr>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Name</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">User Base DN</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Group Base DN</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Primary Objectclass</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Aux Classes</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-50">
          <tr v-for="r in realms" :key="r.id" class="hover:bg-gray-50">
            <td class="px-4 py-3 font-medium text-gray-900">{{ r.name }}</td>
            <td class="px-4 py-3 text-gray-600 font-mono text-xs">{{ r.userBaseDn }}</td>
            <td class="px-4 py-3 text-gray-600 font-mono text-xs">{{ r.groupBaseDn }}</td>
            <td class="px-4 py-3 text-gray-600 font-mono text-xs">{{ r.primaryUserObjectclass }}</td>
            <td class="px-4 py-3">
              <div class="flex flex-wrap gap-1">
                <span
                  v-for="aux in r.auxiliaryObjectclasses"
                  :key="aux.id"
                  class="text-xs bg-blue-50 text-blue-700 rounded px-1.5 py-0.5"
                >{{ aux.objectclassName }}</span>
              </div>
            </td>
            <td class="px-4 py-3 text-right whitespace-nowrap">
              <button @click="openEdit(r)" class="text-blue-600 hover:text-blue-800 text-xs font-medium mr-3">Edit</button>
              <button @click="confirmDelete(r)" class="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Create/Edit modal -->
    <AppModal v-model="showModal" :title="editing ? 'Edit Realm' : 'New Realm'" size="lg">
      <form @submit.prevent="save" class="space-y-4">
        <FormField label="Name" v-model="form.name" required placeholder="e.g. People, Service Accounts" />
        <div class="grid grid-cols-2 gap-4">
          <FormField label="User Base DN" v-model="form.userBaseDn" required placeholder="ou=people,dc=example,dc=com" />
          <FormField label="Group Base DN" v-model="form.groupBaseDn" required placeholder="ou=groups,dc=example,dc=com" />
        </div>
        <div class="grid grid-cols-2 gap-4">
          <FormField label="Primary User Objectclass" v-model="form.primaryUserObjectclass" required placeholder="inetOrgPerson" />
          <FormField label="Display Order" v-model.number="form.displayOrder" type="number" placeholder="0" />
        </div>

        <!-- Auxiliary objectclasses -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">Auxiliary Objectclasses</label>
          <div v-for="(aux, idx) in form.auxiliaryObjectclasses" :key="idx" class="flex gap-2 mb-2">
            <input
              v-model="aux.objectclassName"
              placeholder="e.g. posixAccount"
              class="input flex-1"
            />
            <input
              v-model.number="aux.displayOrder"
              type="number"
              placeholder="Order"
              class="input w-20"
            />
            <button type="button" @click="form.auxiliaryObjectclasses.splice(idx, 1)" class="text-red-500 hover:text-red-700 text-sm px-2">Remove</button>
          </div>
          <button
            type="button"
            @click="form.auxiliaryObjectclasses.push({ objectclassName: '', displayOrder: form.auxiliaryObjectclasses.length })"
            class="text-blue-600 hover:text-blue-800 text-xs font-medium"
          >+ Add auxiliary objectclass</button>
        </div>

        <div class="flex justify-end gap-2 pt-2">
          <button type="button" @click="showModal = false" class="btn-secondary">Cancel</button>
          <button type="submit" :disabled="saving" class="btn-primary">{{ saving ? 'Saving…' : 'Save' }}</button>
        </div>
      </form>
    </AppModal>

    <!-- Delete confirm -->
    <ConfirmDialog
      v-if="deleteTarget"
      :message="`Delete realm '${deleteTarget.name}'? This will remove all associated objectclass configuration.`"
      @confirm="doDelete"
      @cancel="deleteTarget = null"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import { listRealms, createRealm, updateRealm, deleteRealm } from '@/api/realms'
import FormField from '@/components/FormField.vue'
import AppModal from '@/components/AppModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const route = useRoute()
const notif = useNotificationStore()

const loading      = ref(false)
const saving       = ref(false)
const realms       = ref([])
const showModal    = ref(false)
const editing      = ref(null)
const deleteTarget = ref(null)

const form = ref(emptyForm())

function emptyForm() {
  return {
    name: '',
    userBaseDn: '',
    groupBaseDn: '',
    primaryUserObjectclass: 'inetOrgPerson',
    displayOrder: 0,
    auxiliaryObjectclasses: [],
  }
}

const dirId = () => route.params.dirId

async function load() {
  loading.value = true
  try {
    const { data } = await listRealms(dirId())
    realms.value = data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => route.params.dirId, load)

function openCreate() {
  editing.value = null
  form.value = emptyForm()
  showModal.value = true
}

function openEdit(r) {
  editing.value = r.id
  form.value = {
    name: r.name,
    userBaseDn: r.userBaseDn,
    groupBaseDn: r.groupBaseDn,
    primaryUserObjectclass: r.primaryUserObjectclass,
    displayOrder: r.displayOrder,
    auxiliaryObjectclasses: (r.auxiliaryObjectclasses || []).map(a => ({
      objectclassName: a.objectclassName,
      displayOrder: a.displayOrder,
    })),
  }
  showModal.value = true
}

async function save() {
  saving.value = true
  try {
    if (editing.value) {
      await updateRealm(dirId(), editing.value, form.value)
      notif.success('Realm updated')
    } else {
      await createRealm(dirId(), form.value)
      notif.success('Realm created')
    }
    showModal.value = false
    await load()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    saving.value = false
  }
}

function confirmDelete(r) { deleteTarget.value = r }

async function doDelete() {
  try {
    await deleteRealm(dirId(), deleteTarget.value.id)
    notif.success('Realm deleted')
    deleteTarget.value = null
    await load()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
    deleteTarget.value = null
  }
}
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary   { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
.btn-secondary { @apply px-4 py-2 bg-white border border-gray-300 rounded-lg text-sm font-medium hover:bg-gray-50; }
.input         { @apply border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
</style>
