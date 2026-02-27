<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Manage Admin Users</h1>
        <p class="text-sm text-gray-500 mt-1">Create and manage portal administrator accounts</p>
      </div>
      <button @click="openCreate" class="btn-primary">+ New Admin</button>
    </div>

    <DataTable :columns="cols" :rows="admins" :loading="loading" row-key="id" empty-text="No admin users found.">
      <template #cell-displayName="{ row }">
        <div>
          <p class="font-medium text-gray-900">{{ row.displayName || row.username }}</p>
          <p class="text-xs text-gray-400">{{ row.username }}</p>
        </div>
      </template>
      <template #cell-active="{ value }">
        <span :class="value ? 'badge-green' : 'badge-gray'">{{ value ? 'Active' : 'Inactive' }}</span>
      </template>
      <template #cell-authType="{ value }">
        <span class="text-xs text-gray-500 uppercase">{{ value }}</span>
      </template>
      <template #cell-lastLoginAt="{ value }">
        <span class="text-xs text-gray-500">{{ value ? new Date(value).toLocaleString() : '—' }}</span>
      </template>
      <template #actions="{ row }">
        <div class="flex gap-2 justify-end">
          <button @click="openEdit(row)" class="btn-sm btn-secondary">Edit</button>
          <button @click="openPermissions(row)" class="btn-sm btn-secondary">Permissions</button>
          <button
            v-if="row.id !== auth.principal?.id"
            @click="confirmDelete(row)"
            class="btn-sm btn-danger"
          >Delete</button>
        </div>
      </template>
    </DataTable>

    <!-- Create / Edit modal -->
    <AppModal v-model="showForm" :title="editing ? 'Edit Admin User' : 'New Admin User'" size="sm">
      <form @submit.prevent="save" class="space-y-1">
        <FormField label="Username" v-model="form.username" required :disabled="!!editing"
          hint="Used to log in. Cannot be changed after creation." />
        <FormField label="Display name" v-model="form.displayName" placeholder="Optional" />
        <FormField label="Email" v-model="form.email" type="email" placeholder="Optional" />
        <div class="flex items-center gap-2 py-2">
          <input id="active-toggle" type="checkbox" v-model="form.active"
            class="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
          <label for="active-toggle" class="text-sm font-medium text-gray-700">Active</label>
        </div>
      </form>
      <template #footer>
        <button @click="showForm = false" class="btn-secondary">Cancel</button>
        <button @click="save" :disabled="saving || !form.username.trim()" class="btn-primary">
          {{ saving ? 'Saving…' : (editing ? 'Save changes' : 'Create') }}
        </button>
      </template>
    </AppModal>

    <!-- Permissions panel -->
    <AppModal v-model="showPerms" :title="`Permissions — ${permsTarget?.username}`" size="lg">
      <div v-if="permsLoading" class="py-8 text-center text-sm text-gray-400">Loading…</div>
      <div v-else-if="perms" class="space-y-6 text-sm">

        <!-- Realm roles -->
        <section>
          <h3 class="font-semibold text-gray-700 mb-2">Realm roles</h3>
          <div v-if="perms.realmRoles.length === 0" class="text-gray-400">None assigned.</div>
          <table v-else class="w-full text-xs border border-gray-100 rounded-lg overflow-hidden">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-3 py-2 text-left text-gray-500 font-medium">Realm</th>
                <th class="px-3 py-2 text-left text-gray-500 font-medium">Role</th>
                <th class="px-3 py-2 text-left text-gray-500 font-medium">Write</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-50">
              <tr v-for="r in perms.realmRoles" :key="r.realmId" class="hover:bg-gray-50">
                <td class="px-3 py-2 text-gray-700">{{ r.realmName }}</td>
                <td class="px-3 py-2 text-gray-700">{{ r.role }}</td>
                <td class="px-3 py-2">
                  <span :class="r.canWrite ? 'badge-green' : 'badge-gray'">{{ r.canWrite ? 'Yes' : 'No' }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </section>

        <!-- Feature overrides -->
        <section>
          <h3 class="font-semibold text-gray-700 mb-2">Feature permission overrides</h3>
          <div v-if="perms.featurePermissions.length === 0" class="text-gray-400">No overrides — defaults apply.</div>
          <div v-else class="flex flex-wrap gap-2">
            <span
              v-for="f in perms.featurePermissions"
              :key="f.featureKey"
              :class="f.enabled ? 'badge-green' : 'badge-red'"
            >{{ f.featureKey }}</span>
          </div>
        </section>

      </div>
      <template #footer>
        <button @click="showPerms = false" class="btn-secondary">Close</button>
      </template>
    </AppModal>

    <!-- Delete confirm -->
    <ConfirmDialog
      v-model="showDelete"
      title="Delete admin user"
      :message="`Delete '${deleteTarget?.username}'? This cannot be undone.`"
      confirm-label="Delete"
      danger
      @confirm="doDelete"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notifications'
import { listAdmins, createAdmin, updateAdmin, deleteAdmin, getPermissions } from '@/api/adminManagement'
import DataTable from '@/components/DataTable.vue'
import AppModal from '@/components/AppModal.vue'
import FormField from '@/components/FormField.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const auth  = useAuthStore()
const notif = useNotificationStore()

const loading = ref(false)
const saving  = ref(false)
const admins  = ref([])

const showForm   = ref(false)
const editing    = ref(null)   // admin id when editing, null when creating
const form       = ref(emptyForm())

const showDelete   = ref(false)
const deleteTarget = ref(null)

const showPerms   = ref(false)
const permsTarget = ref(null)
const permsLoading = ref(false)
const perms       = ref(null)

const cols = [
  { key: 'displayName', label: 'Name / Username' },
  { key: 'email',       label: 'Email' },
  { key: 'authType',    label: 'Auth type' },
  { key: 'active',      label: 'Status' },
  { key: 'lastLoginAt', label: 'Last login' },
]

function emptyForm() {
  return { username: '', displayName: '', email: '', active: true }
}

async function load() {
  loading.value = true
  try {
    const { data } = await listAdmins()
    admins.value = data
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
  showForm.value = true
}

function openEdit(row) {
  editing.value = row.id
  form.value = { username: row.username, displayName: row.displayName || '', email: row.email || '', active: row.active }
  showForm.value = true
}

async function save() {
  if (!form.value.username.trim()) return
  saving.value = true
  try {
    if (editing.value) {
      await updateAdmin(editing.value, form.value)
      notif.success('Admin user updated')
    } else {
      await createAdmin(form.value)
      notif.success('Admin user created')
    }
    showForm.value = false
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
  try {
    await deleteAdmin(deleteTarget.value.id)
    notif.success('Admin user deleted')
    await load()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    deleteTarget.value = null
  }
}

async function openPermissions(row) {
  permsTarget.value = row
  perms.value = null
  showPerms.value = true
  permsLoading.value = true
  try {
    const { data } = await getPermissions(row.id)
    perms.value = data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
    showPerms.value = false
  } finally {
    permsLoading.value = false
  }
}
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary   { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50 transition-colors; }
.btn-secondary { @apply px-3 py-1.5 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50 transition-colors; }
.btn-danger    { @apply px-3 py-1.5 bg-red-600 text-white rounded-lg text-sm hover:bg-red-700 transition-colors; }
.btn-sm        { @apply text-xs; }
.badge-green   { @apply inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-green-100 text-green-800; }
.badge-gray    { @apply inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-600; }
.badge-red     { @apply inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-red-100 text-red-700; }
</style>
