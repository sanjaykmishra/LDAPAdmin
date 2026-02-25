<template>
  <div class="p-6 max-w-4xl">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Attribute Profiles</h1>
      <button @click="openCreate" class="btn-primary">+ New Profile</button>
    </div>

    <div class="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <div v-if="loading" class="p-8 text-center text-gray-500 text-sm">Loading…</div>
      <div v-else-if="profiles.length === 0" class="p-8 text-center text-gray-400 text-sm">
        No attribute profiles yet. Create one to customise user create/edit forms.
      </div>
      <table v-else class="w-full text-sm">
        <thead class="bg-gray-50 border-b border-gray-100">
          <tr>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Name</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Branch DN</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Fields</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Default</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-50">
          <tr v-for="p in profiles" :key="p.id" class="hover:bg-gray-50">
            <td class="px-4 py-3 font-medium text-gray-900">{{ p.displayName || '(unnamed)' }}</td>
            <td class="px-4 py-3 text-gray-600 font-mono text-xs">{{ p.branchDn }}</td>
            <td class="px-4 py-3 text-gray-600">{{ p.entries?.length ?? 0 }}</td>
            <td class="px-4 py-3">
              <span v-if="p.isDefault" class="text-xs bg-green-50 text-green-700 rounded px-2 py-0.5 font-medium">Default</span>
            </td>
            <td class="px-4 py-3 text-right">
              <button @click="openEdit(p)" class="text-blue-600 hover:text-blue-800 text-xs font-medium mr-3">Edit</button>
              <button @click="confirmDelete(p)" class="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Create/Edit modal -->
    <AppModal v-if="showModal" :title="editProfile ? 'Edit Profile' : 'New Profile'" size="xl" @close="showModal = false">
      <form @submit.prevent="saveProfile" class="space-y-5">
        <div class="grid grid-cols-2 gap-4">
          <FormField label="Display Name" v-model="form.displayName" placeholder="Default Profile" />
          <FormField label="Branch DN" v-model="form.branchDn" placeholder="* (all) or ou=people,dc=example,dc=com" required />
        </div>
        <div class="flex items-center gap-2">
          <input type="checkbox" id="isDefault" v-model="form.isDefault" class="rounded" />
          <label for="isDefault" class="text-sm text-gray-700">Set as default profile for this directory</label>
        </div>

        <!-- Entries -->
        <div>
          <div class="flex items-center justify-between mb-3">
            <h3 class="text-sm font-semibold text-gray-700">Attribute Fields</h3>
            <button type="button" @click="addEntry" class="text-xs text-blue-600 hover:text-blue-800 font-medium">+ Add Field</button>
          </div>

          <div v-if="form.entries.length === 0" class="text-xs text-gray-400 text-center py-4 border border-dashed border-gray-200 rounded-lg">
            No fields. Click "+ Add Field" to start.
          </div>

          <div class="space-y-2 max-h-72 overflow-y-auto pr-1">
            <div
              v-for="(entry, i) in form.entries"
              :key="i"
              class="grid grid-cols-12 gap-2 p-2 bg-gray-50 rounded-lg items-start text-xs"
            >
              <!-- Attribute name -->
              <div class="col-span-3">
                <input v-model="entry.attributeName" placeholder="uid" class="input w-full text-xs" required />
                <p class="text-gray-400 mt-0.5">Attribute</p>
              </div>
              <!-- Custom label -->
              <div class="col-span-2">
                <input v-model="entry.customLabel" placeholder="Username" class="input w-full text-xs" />
                <p class="text-gray-400 mt-0.5">Label</p>
              </div>
              <!-- Input type -->
              <div class="col-span-2">
                <select v-model="entry.inputType" class="input w-full text-xs">
                  <option v-for="t in inputTypes" :key="t" :value="t">{{ t }}</option>
                </select>
                <p class="text-gray-400 mt-0.5">Type</p>
              </div>
              <!-- Flags -->
              <div class="col-span-4 flex flex-col gap-1 pt-1">
                <label class="flex items-center gap-1">
                  <input type="checkbox" v-model="entry.requiredOnCreate" class="rounded" />
                  Required
                </label>
                <label class="flex items-center gap-1">
                  <input type="checkbox" v-model="entry.enabledOnEdit" class="rounded" />
                  Editable
                </label>
                <label class="flex items-center gap-1">
                  <input type="checkbox" v-model="entry.visibleInListView" class="rounded" />
                  In list
                </label>
              </div>
              <!-- Remove -->
              <div class="col-span-1 pt-1">
                <button type="button" @click="removeEntry(i)" class="text-red-400 hover:text-red-600 text-lg leading-none">×</button>
              </div>
            </div>
          </div>
        </div>

        <div class="flex justify-end gap-2 pt-2">
          <button type="button" @click="showModal = false" class="btn-secondary">Cancel</button>
          <button type="submit" :disabled="saving" class="btn-primary">{{ saving ? 'Saving…' : 'Save' }}</button>
        </div>
      </form>
    </AppModal>

    <ConfirmDialog
      v-if="deleteTarget"
      :message="`Delete profile '${deleteTarget.displayName || deleteTarget.branchDn}'?`"
      @confirm="doDelete"
      @cancel="deleteTarget = null"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import { listProfiles, createProfile, updateProfile, deleteProfile } from '@/api/attributeProfiles'
import FormField from '@/components/FormField.vue'
import AppModal from '@/components/AppModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const route = useRoute()
const notif = useNotificationStore()
const dirId = route.params.dirId

const loading      = ref(false)
const saving       = ref(false)
const profiles     = ref([])
const showModal    = ref(false)
const editProfile  = ref(null)
const deleteTarget = ref(null)

const inputTypes = ['TEXT', 'TEXTAREA', 'PASSWORD', 'BOOLEAN', 'DATE', 'DATETIME', 'MULTI_VALUE', 'DN_LOOKUP']

const form = ref({ displayName: '', branchDn: '*', isDefault: false, entries: [] })

function blankEntry() {
  return { attributeName: '', customLabel: '', inputType: 'TEXT', requiredOnCreate: false, enabledOnEdit: true, visibleInListView: false }
}

function addEntry()      { form.value.entries.push(blankEntry()) }
function removeEntry(i)  { form.value.entries.splice(i, 1) }

async function loadProfiles() {
  loading.value = true
  try {
    const { data } = await listProfiles(dirId)
    profiles.value = data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    loading.value = false
  }
}

onMounted(loadProfiles)

function openCreate() {
  editProfile.value = null
  form.value = { displayName: '', branchDn: '*', isDefault: false, entries: [] }
  showModal.value = true
}

function openEdit(p) {
  editProfile.value = p
  form.value = {
    displayName: p.displayName ?? '',
    branchDn:    p.branchDn,
    isDefault:   p.isDefault,
    entries: (p.entries ?? []).map(e => ({
      attributeName:    e.attributeName,
      customLabel:      e.customLabel ?? '',
      inputType:        e.inputType ?? 'TEXT',
      requiredOnCreate: e.requiredOnCreate,
      enabledOnEdit:    e.enabledOnEdit,
      visibleInListView: e.visibleInListView,
    })),
  }
  showModal.value = true
}

async function saveProfile() {
  saving.value = true
  try {
    const payload = {
      ...form.value,
      entries: form.value.entries.map((e, i) => ({ ...e, displayOrder: i })),
    }
    if (editProfile.value) {
      await updateProfile(dirId, editProfile.value.id, payload)
      notif.success('Profile updated')
    } else {
      await createProfile(dirId, payload)
      notif.success('Profile created')
    }
    showModal.value = false
    await loadProfiles()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    saving.value = false
  }
}

function confirmDelete(p) { deleteTarget.value = p }

async function doDelete() {
  try {
    await deleteProfile(dirId, deleteTarget.value.id)
    notif.success('Profile deleted')
    deleteTarget.value = null
    await loadProfiles()
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
.input         { @apply border border-gray-300 rounded-lg px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
</style>
