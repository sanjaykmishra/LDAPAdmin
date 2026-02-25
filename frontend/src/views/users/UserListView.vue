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
        <div class="flex gap-2 justify-end">
          <button @click="openEdit(row)" class="btn-sm btn-secondary">Edit</button>
          <button
            @click="toggleEnabled(row)"
            class="btn-sm"
            :class="row.enabled !== false ? 'btn-secondary' : 'btn-primary'"
          >{{ row.enabled !== false ? 'Disable' : 'Enable' }}</button>
          <button @click="openMove(row)" class="btn-sm btn-secondary">Move</button>
          <button @click="confirmDelete(row)" class="btn-sm btn-danger">Delete</button>
        </div>
      </template>
    </DataTable>

    <!-- Pagination -->
    <div v-if="users.length >= limit" class="mt-4 flex justify-center">
      <button @click="loadMore" :disabled="loading" class="btn-secondary">Load more</button>
    </div>

    <!-- Create/Edit modal -->
    <AppModal v-model="showModal" :title="editingDn ? 'Edit User' : 'New User'" size="lg">
      <UserForm :data="form" :is-edit="!!editingDn" @update="v => form = v" />
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

const emptyForm = () => ({ parentDn: '', rdnAttribute: 'uid', rdnValue: '', attributes: {} })
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
    users.value = (data.entries || data).map(e => ({
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
        attribute: attr, values: val.split('\n').map(v => v.trim()).filter(v => v.length > 0),
      }))
      await usersApi.updateUser(dirId, editingDn.value, { modifications: mods })
      notif.success('User updated')
    } else {
      await usersApi.createUser(dirId, form.value)
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

onMounted(load)
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
