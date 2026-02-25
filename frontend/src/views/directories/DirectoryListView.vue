<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Directories</h1>
        <p class="text-sm text-gray-500 mt-1">LDAP directory connections</p>
      </div>
      <button @click="openCreate" class="btn-primary">+ New Directory</button>
    </div>

    <DataTable :columns="cols" :rows="dirs" :loading="loading" row-key="id">
      <template #cell-name="{ row }">
        <div>
          <p class="font-medium text-gray-900">{{ row.name }}</p>
          <p class="text-xs text-gray-400">{{ row.host }}:{{ row.port }}</p>
        </div>
      </template>
      <template #cell-bindDn="{ value }">
        <code class="text-xs">{{ value }}</code>
      </template>
      <template #actions="{ row }">
        <div class="flex gap-2 justify-end">
          <RouterLink :to="`/directories/${row.id}/users`" class="btn-sm btn-secondary">Users</RouterLink>
          <button @click="openEdit(row)" class="btn-sm btn-secondary">Edit</button>
          <button @click="testConn(row)" class="btn-sm btn-secondary">Test</button>
          <button @click="confirmDelete(row)" class="btn-sm btn-danger">Delete</button>
        </div>
      </template>
    </DataTable>

    <!-- Create/Edit modal -->
    <AppModal v-model="showModal" :title="editing ? 'Edit Directory' : 'New Directory'" size="lg">
      <DirectoryForm :data="form" @update="v => form = v" />
      <template #footer>
        <button @click="showModal = false" class="btn-secondary">Cancel</button>
        <button @click="save" :disabled="saving" class="btn-primary">
          {{ saving ? 'Saving…' : 'Save' }}
        </button>
      </template>
    </AppModal>

    <ConfirmDialog
      v-model="showDelete"
      title="Delete Directory"
      :message="`Delete '${deleteTarget?.name}'? This cannot be undone.`"
      confirm-label="Delete"
      danger
      @confirm="doDelete"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notifications'
import { useApi } from '@/composables/useApi'
import client from '@/api/client'
import DataTable from '@/components/DataTable.vue'
import AppModal from '@/components/AppModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import DirectoryForm from './DirectoryForm.vue'

const auth  = useAuthStore()
const notif = useNotificationStore()
const { loading, call } = useApi()

const dirs        = ref([])
const showModal   = ref(false)
const showDelete  = ref(false)
const editing     = ref(null)
const saving      = ref(false)
const deleteTarget = ref(null)

const cols = [
  { key: 'name',   label: 'Name' },
  { key: 'bindDn', label: 'Bind DN' },
  { key: 'baseDn', label: 'Base DN' },
]

function tenantId() {
  return auth.principal?.tenantId
}

const emptyForm = () => ({
  name: '', host: 'localhost', port: 389, bindDn: '', bindPassword: '',
  baseDn: '', useTls: false, pagingSize: 500,
})

const form = ref(emptyForm())

async function load() {
  await call(async () => {
    const { data } = await client.get(`/admin/tenants/${tenantId()}/directories`)
    dirs.value = data
  })
}

function openCreate() {
  editing.value = null
  form.value = emptyForm()
  showModal.value = true
}

function openEdit(row) {
  editing.value = row.id
  form.value = { ...row, bindPassword: '' }
  showModal.value = true
}

async function save() {
  saving.value = true
  try {
    if (editing.value) {
      await client.put(`/admin/tenants/${tenantId()}/directories/${editing.value}`, form.value)
      notif.success('Directory updated')
    } else {
      await client.post(`/admin/tenants/${tenantId()}/directories`, form.value)
      notif.success('Directory created')
    }
    showModal.value = false
    await load()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    saving.value = false
  }
}

function confirmDelete(row) {
  deleteTarget.value = row
  showDelete.value = true
}

async function doDelete() {
  await call(
    () => client.delete(`/admin/tenants/${tenantId()}/directories/${deleteTarget.value.id}`),
    { successMsg: 'Directory deleted' }
  )
  await load()
}

async function testConn(row) {
  try {
    await client.post(`/admin/tenants/${tenantId()}/directories/${row.id}/test`)
    notif.success('Connection successful')
  } catch (e) {
    notif.error(e.response?.data?.detail || 'Connection failed')
  }
}

onMounted(load)
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary   { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors; }
.btn-secondary { @apply px-3 py-1.5 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50 transition-colors; }
.btn-danger    { @apply px-3 py-1.5 bg-red-600 text-white rounded-lg text-sm hover:bg-red-700 transition-colors; }
.btn-sm        { @apply text-xs; }
</style>
