<template>
  <div class="p-6">
    <!-- Header -->
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Users</h1>
        <p class="text-sm text-gray-500 mt-1">Directory: <code class="text-xs bg-gray-100 px-1 rounded">{{ dirId }}</code></p>
      </div>
      <button @click="openCreate" class="btn-primary">+ New User</button>
    </div>

    <!-- Search bar -->
    <div class="flex gap-3 mb-4">
      <input
        v-model="filterText"
        placeholder="LDAP filter, e.g. (cn=john*)"
        class="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        @keyup.enter="search"
      />
      <input
        v-model="baseDnOverride"
        placeholder="Base DN (optional)"
        class="w-64 rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
      <button @click="search" class="btn-primary">Search</button>
    </div>

    <DataTable :columns="cols" :rows="users" :loading="loading" row-key="dn">
      <template #cell-dn="{ value }">
        <code class="text-xs">{{ value }}</code>
      </template>
      <template #cell-enabled="{ value }">
        <span :class="value !== false ? 'badge-green' : 'badge-red'">
          {{ value !== false ? 'Active' : 'Disabled' }}
        </span>
      </template>
      <template #actions="{ row }">
        <div class="flex gap-3 justify-end whitespace-nowrap">
          <button @click="openEdit(row)" class="text-blue-600 hover:text-blue-800 text-xs font-medium">Edit</button>
          <button @click="toggleEnabled(row)" class="text-xs font-medium" :class="row.enabled !== false ? 'text-amber-600 hover:text-amber-800' : 'text-green-600 hover:text-green-800'">{{ row.enabled !== false ? 'Disable' : 'Enable' }}</button>
          <button @click="openMove(row)" class="text-blue-600 hover:text-blue-800 text-xs font-medium">Move</button>
          <button @click="confirmDelete(row)" class="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
        </div>
      </template>
    </DataTable>

    <!-- Pagination -->
    <div v-if="users.length >= limit" class="mt-4 flex justify-center">
      <button @click="loadMore" :disabled="loading" class="btn-secondary">Load more</button>
    </div>

    <!-- Form picker modal (step 1 of create) -->
    <AppModal v-model="showFormPicker" title="Choose User Form" size="sm">
      <div class="space-y-2">
        <p class="text-sm text-gray-600 mb-3">Select a user form to define which attributes are available for the new user.</p>
        <div v-if="availableForms.length === 0" class="text-sm text-gray-400 py-4 text-center">
          No user forms are linked to this realm. Configure user forms in the Realms settings first.
        </div>
        <button
          v-for="uf in availableForms"
          :key="uf.id"
          @click="selectFormAndCreate(uf)"
          class="w-full text-left px-4 py-3 border border-gray-200 rounded-lg hover:bg-blue-50 hover:border-blue-300 transition-colors"
        >
          <span class="font-medium text-gray-900 text-sm">{{ uf.formName }}</span>
          <span class="text-xs text-gray-500 ml-2">({{ (uf.objectClassNames || []).join(', ') }})</span>
        </button>
      </div>
      <template #footer>
        <button @click="showFormPicker = false" class="btn-secondary">Cancel</button>
      </template>
    </AppModal>

    <!-- Create/Edit modal (step 2 of create, or edit) -->
    <AppModal v-model="showModal" :title="editingDn ? 'Edit User' : 'New User'" size="lg">
      <UserForm :data="form" :is-edit="!!editingDn" :user-form-config="userFormConfig" :dir-id="dirId" @update="v => form = v" />
      <template #footer>
        <button @click="showModal = false" class="btn-secondary">Cancel</button>
        <button @click="save" :disabled="saving" class="btn-primary">{{ saving ? 'Saving…' : 'Save' }}</button>
      </template>
    </AppModal>

    <!-- Move modal -->
    <AppModal v-model="showMove" title="Move User" size="sm">
      <FormField label="New Parent DN" v-model="newParentDn" placeholder="ou=people,dc=example,dc=com" required />
      <template #footer>
        <button @click="showMove = false" class="btn-secondary">Cancel</button>
        <button @click="doMove" :disabled="saving" class="btn-primary">Move</button>
      </template>
    </AppModal>

    <ConfirmDialog v-model="showDelete" title="Delete User" :message="`Delete '${deleteTarget?.dn}'?`" confirm-label="Delete" danger @confirm="doDelete" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import { useApi } from '@/composables/useApi'
import * as usersApi from '@/api/users'
import { listRealms } from '@/api/realms'
import { getUserForm } from '@/api/userForms'
import DataTable from '@/components/DataTable.vue'
import AppModal from '@/components/AppModal.vue'
import FormField from '@/components/FormField.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import UserForm from './UserForm.vue'

const PAGE_SIZE = 50

const route  = useRoute()
const notif  = useNotificationStore()
const { loading, call } = useApi()

const dirId          = route.params.dirId
const users          = ref([])
const filterText     = ref('')
const baseDnOverride = ref('')
const limit          = ref(PAGE_SIZE)
const showFormPicker = ref(false)
const showModal      = ref(false)
const showMove       = ref(false)
const showDelete     = ref(false)
const editingDn      = ref(null)
const deleteTarget   = ref(null)
const moveTarget     = ref(null)
const newParentDn    = ref('')
const saving         = ref(false)

const cols = [
  { key: 'dn',   label: 'DN' },
  { key: 'cn',   label: 'CN' },
  { key: 'mail', label: 'Email' },
  { key: 'enabled', label: 'Status' },
]

const realmData       = ref(null)
const availableForms  = ref([])
const userFormConfig  = ref(null)

const emptyForm = () => {
  const rdnConfig = userFormConfig.value?.attributeConfigs?.find(a => a.rdn)
  return {
    parentDn: realmData.value?.userBaseDn || '',
    rdnAttribute: rdnConfig?.attributeName || 'uid',
    rdnValue: '',
    attributes: {},
  }
}
const form = ref(emptyForm())

function search() { limit.value = PAGE_SIZE; load() }

async function load() {
  await call(async () => {
    const params = {
      filter: filterText.value || undefined,
      baseDn: baseDnOverride.value || undefined,
      limit:  limit.value,
    }
    const { data } = await usersApi.searchUsers(dirId, params)
    const entries = Array.isArray(data) ? data : (data.entries || [])
    users.value = entries.map(e => ({
      dn:      e.dn,
      cn:      e.attributes?.cn?.[0] || e.attributes?.CN?.[0] || '—',
      mail:    e.attributes?.mail?.[0] || '—',
      enabled: e.attributes?.enabled,
      _raw:    e,
    }))
  })
}

function loadMore() { limit.value += 50; load() }

function openCreate() {
  editingDn.value = null
  userFormConfig.value = null
  if (availableForms.value.length === 1) {
    // Only one form available — skip the picker
    selectFormAndCreate(availableForms.value[0])
  } else if (availableForms.value.length > 1) {
    showFormPicker.value = true
  } else {
    // No forms linked — open create dialog with fallback fields
    form.value = emptyForm()
    showModal.value = true
  }
}

async function selectFormAndCreate(uf) {
  showFormPicker.value = false
  try {
    const { data } = await getUserForm(uf.id)
    userFormConfig.value = data
  } catch {
    userFormConfig.value = null
  }
  form.value = emptyForm()
  showModal.value = true
}

function openEdit(row) {
  editingDn.value = row.dn
  const attrs = row._raw?.attributes || {}
  form.value = { dn: row.dn, attributes: Object.fromEntries(
    Object.entries(attrs).map(([k, v]) => [k, Array.isArray(v) ? v.join('\n') : v])
  )}
  showModal.value = true
}

async function save() {
  saving.value = true
  try {
    if (editingDn.value) {
      const mods = Object.entries(form.value.attributes || {}).map(([attr, val]) => ({
        operation: 'REPLACE',
        attribute: attr,
        values: val.split('\n').map(v => v.trim()).filter(v => v.length > 0),
      }))
      await usersApi.updateUser(dirId, editingDn.value, { modifications: mods })
      notif.success('User updated')
    } else {
      const f = form.value
      const dn = `${f.rdnAttribute}=${f.rdnValue},${f.parentDn}`
      const attributes = {}
      for (const [k, v] of Object.entries(f.attributes || {})) {
        if (v) attributes[k] = [v]
      }
      // Include the RDN attribute in the attributes map
      attributes[f.rdnAttribute] = [f.rdnValue]
      // Include objectClasses from the selected user form
      if (userFormConfig.value?.objectClassNames?.length) {
        attributes.objectClass = userFormConfig.value.objectClassNames
      }
      await usersApi.createUser(dirId, { dn, attributes })
      notif.success('User created')
    }
    showModal.value = false
    await load()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(row) {
  const fn = row.enabled !== false ? usersApi.disableUser : usersApi.enableUser
  await call(() => fn(dirId, row.dn), { successMsg: 'Status updated' })
  await load()
}

function openMove(row) {
  moveTarget.value  = row
  newParentDn.value = ''
  showMove.value    = true
}

async function doMove() {
  saving.value = true
  try {
    await usersApi.moveUser(dirId, moveTarget.value.dn, { newParentDn: newParentDn.value })
    notif.success('User moved')
    showMove.value = false
    await load()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally { saving.value = false }
}

function confirmDelete(row) { deleteTarget.value = row; showDelete.value = true }

async function doDelete() {
  await call(() => usersApi.deleteUser(dirId, deleteTarget.value.dn), { successMsg: 'User deleted' })
  await load()
}

async function loadRealmAndForms() {
  try {
    const { data: realms } = await listRealms(dirId)
    if (realms.length) {
      realmData.value = realms[0]
      // Collect available user forms from the realm
      availableForms.value = realms[0].userForms || []
    }
  } catch { /* realm loading is best-effort */ }
}

onMounted(() => {
  load()
  loadRealmAndForms()
})
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary   { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700; }
.btn-secondary { @apply px-3 py-1.5 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50; }
.btn-danger    { @apply px-3 py-1.5 bg-red-600 text-white rounded-lg text-sm hover:bg-red-700; }
.btn-sm        { @apply text-xs; }
.badge-green   { @apply inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800; }
.badge-red     { @apply inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-800; }
</style>
