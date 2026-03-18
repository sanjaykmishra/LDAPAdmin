<template>
  <div class="p-6 max-w-5xl">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Realms</h1>
        <p class="text-sm text-gray-500 mt-1">Manage realms across all directories</p>
      </div>
      <button @click="openCreate" class="btn-primary">+ New Realm</button>
    </div>

    <div class="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <div v-if="loading" class="p-8 text-center text-gray-500 text-sm">Loading…</div>
      <div v-else-if="realms.length === 0" class="p-8 text-center text-gray-400 text-sm">
        No realms configured. Create one to define user/group scopes within a directory.
      </div>
      <table v-else class="w-full text-sm">
        <thead class="bg-gray-50 border-b border-gray-100">
          <tr>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Directory</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Name</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">User Base DN</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Group Base DN</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">User Forms</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-50">
          <tr v-for="r in realms" :key="r.id" class="hover:bg-gray-50">
            <td class="px-4 py-3 text-gray-600 text-xs">{{ r.directoryName }}</td>
            <td class="px-4 py-3 font-medium text-gray-900">{{ r.name }}</td>
            <td class="px-4 py-3 text-gray-600 font-mono text-xs">{{ r.userBaseDn }}</td>
            <td class="px-4 py-3 text-gray-600 font-mono text-xs">{{ r.groupBaseDn }}</td>
            <td class="px-4 py-3">
              <div class="flex flex-wrap gap-1">
                <span
                  v-for="uf in r.userForms"
                  :key="uf.id"
                  class="text-xs bg-blue-50 text-blue-700 rounded px-1.5 py-0.5"
                >{{ uf.formName }} ({{ uf.objectClassName }})</span>
                <span v-if="!r.userForms?.length" class="text-xs text-gray-400">—</span>
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
        <!-- Directory picker -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Directory</label>
          <select v-model="form.directoryId" class="input w-full" required>
            <option value="" disabled>— Select directory —</option>
            <option v-for="d in directories" :key="d.id" :value="d.id">
              {{ d.displayName }}
            </option>
          </select>
          <p class="text-xs text-gray-400 mt-1">The directory connection this realm belongs to</p>
        </div>

        <FormField label="Name" v-model="form.name" required placeholder="e.g. People, Service Accounts" />
        <div class="grid grid-cols-2 gap-4">
          <FormField label="User Base DN" v-model="form.userBaseDn" required placeholder="ou=people,dc=example,dc=com" />
          <FormField label="Group Base DN" v-model="form.groupBaseDn" required placeholder="ou=groups,dc=example,dc=com" />
        </div>
        <FormField label="Display Order" v-model.number="form.displayOrder" type="number" placeholder="0" />

        <!-- User Forms multi-select -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">User Forms</label>
          <div class="border border-gray-300 rounded-lg p-2 max-h-48 overflow-y-auto space-y-1">
            <label
              v-for="uf in userForms"
              :key="uf.id"
              class="flex items-center gap-2 px-2 py-1.5 rounded hover:bg-gray-50 cursor-pointer"
            >
              <input
                type="checkbox"
                :value="uf.id"
                v-model="form.userFormIds"
                class="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
              />
              <span class="text-sm text-gray-800">{{ uf.formName }}</span>
              <span class="text-xs text-gray-400">({{ uf.objectClassName }})</span>
            </label>
            <p v-if="userForms.length === 0" class="text-xs text-gray-400 px-2 py-1">No user forms available. Create one first.</p>
          </div>
          <p class="text-xs text-gray-400 mt-1">Select the user forms available for creating users in this realm</p>
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
      :message="`Delete realm '${deleteTarget.name}'? This will remove all associated configuration.`"
      @confirm="doDelete"
      @cancel="deleteTarget = null"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useNotificationStore } from '@/stores/notifications'
import { listAllRealms, createRealm, updateRealm, deleteRealm } from '@/api/realms'
import { listUserForms } from '@/api/userForms'
import { listDirectories } from '@/api/directories'
import FormField from '@/components/FormField.vue'
import AppModal from '@/components/AppModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const notif = useNotificationStore()

const loading      = ref(false)
const saving       = ref(false)
const realms       = ref([])
const userForms    = ref([])
const directories  = ref([])
const showModal    = ref(false)
const editing      = ref(null)
const deleteTarget = ref(null)

const form = ref(emptyForm())

function emptyForm() {
  return {
    directoryId: '',
    name: '',
    userBaseDn: '',
    groupBaseDn: '',
    displayOrder: 0,
    userFormIds: [],
  }
}

async function load() {
  loading.value = true
  try {
    const [realmsRes, formsRes, dirsRes] = await Promise.all([
      listAllRealms(),
      listUserForms(),
      listDirectories(),
    ])
    realms.value = realmsRes.data
    userForms.value = formsRes.data
    directories.value = dirsRes.data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate() {
  editing.value = null
  form.value = emptyForm()
  showModal.value = true
}

function openEdit(r) {
  editing.value = r.id
  form.value = {
    directoryId: r.directoryId,
    name: r.name,
    userBaseDn: r.userBaseDn,
    groupBaseDn: r.groupBaseDn,
    displayOrder: r.displayOrder,
    userFormIds: (r.userForms || []).map(uf => uf.id),
  }
  showModal.value = true
}

async function save() {
  saving.value = true
  try {
    const targetDirId = form.value.directoryId
    const payload = {
      name: form.value.name,
      userBaseDn: form.value.userBaseDn,
      groupBaseDn: form.value.groupBaseDn,
      displayOrder: form.value.displayOrder,
      userFormIds: form.value.userFormIds,
    }
    if (editing.value) {
      await updateRealm(targetDirId, editing.value, payload)
      notif.success('Realm updated')
    } else {
      await createRealm(targetDirId, payload)
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
    await deleteRealm(deleteTarget.value.directoryId, deleteTarget.value.id)
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
