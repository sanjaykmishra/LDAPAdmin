<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Manage Accounts</h1>
        <p class="text-sm text-gray-500 mt-1">Create and manage superadmin and admin accounts</p>
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
      <template #cell-role="{ value }">
        <span :class="value === 'SUPERADMIN' ? 'badge-blue' : 'badge-gray'">{{ value }}</span>
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
          <button v-if="row.role === 'ADMIN'" @click="openPermissions(row)" class="btn-sm btn-secondary">Permissions</button>
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
        <FormField label="Role" v-model="form.role" type="select" required
          :options="[{ value: 'ADMIN', label: 'Admin' }, { value: 'SUPERADMIN', label: 'Superadmin' }]"
          hint="Superadmins have full platform access. Admins have realm-scoped permissions." />
        <FormField label="Auth type" v-model="form.authType" type="select" required
          :options="[{ value: 'LOCAL', label: 'Local' }, { value: 'LDAP', label: 'LDAP' }]"
          hint="LOCAL uses a portal password. LDAP authenticates against the configured LDAP directory." />
        <FormField v-if="form.authType === 'LOCAL'" label="Password" v-model="form.password" type="password"
          :placeholder="editing ? 'Leave blank to keep current' : 'Enter password'"
          :hint="editing ? 'Only fill in to change the password.' : 'Set the initial password for this account.'" />
        <FormField v-if="form.authType === 'LDAP'" label="LDAP DN" v-model="form.ldapDn"
          placeholder="e.g. uid=jdoe,ou=People,dc=example,dc=com"
          hint="Distinguished name used to bind against the LDAP auth directory." />
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
          <div class="flex items-center justify-between mb-2">
            <h3 class="font-semibold text-gray-700">Realm roles</h3>
          </div>
          <div v-if="perms.realmRoles.length === 0" class="text-gray-400 mb-2">None assigned.</div>
          <table v-else class="w-full text-xs border border-gray-100 rounded-lg overflow-hidden mb-2">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-3 py-2 text-left text-gray-500 font-medium">Realm</th>
                <th class="px-3 py-2 text-left text-gray-500 font-medium">Role</th>
                <th class="px-3 py-2"></th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-50">
              <tr v-for="r in perms.realmRoles" :key="r.realmId" class="hover:bg-gray-50">
                <td class="px-3 py-2 text-gray-700">{{ r.realmName }}</td>
                <td class="px-3 py-2">
                  <select :value="r.baseRole" @change="changeRealmRole(r.realmId, $event.target.value)" class="input text-xs py-1">
                    <option value="ADMIN">ADMIN</option>
                    <option value="READ_ONLY">READ_ONLY</option>
                  </select>
                </td>
                <td class="px-3 py-2 text-right">
                  <button @click="doRemoveRealmRole(r.realmId)" class="text-red-500 hover:text-red-700 text-xs font-medium">Remove</button>
                </td>
              </tr>
            </tbody>
          </table>
          <!-- Add realm role -->
          <div class="flex items-center gap-2">
            <select v-model="newRealmId" class="input text-xs py-1 flex-1">
              <option value="" disabled>— Add realm —</option>
              <option v-for="r in availableRealms" :key="r.id" :value="r.id">{{ r.name }}</option>
            </select>
            <select v-model="newRealmRole" class="input text-xs py-1">
              <option value="ADMIN">ADMIN</option>
              <option value="READ_ONLY">READ_ONLY</option>
            </select>
            <button @click="doAddRealmRole" :disabled="!newRealmId" class="btn-primary btn-sm px-3 py-1 disabled:opacity-50">Add</button>
          </div>
        </section>

        <!-- Feature overrides -->
        <section>
          <h3 class="font-semibold text-gray-700 mb-2">Feature permission overrides</h3>
          <p class="text-xs text-gray-400 mb-3">Override the default feature permissions for this admin. Leave as "Default" to use the role-based default.</p>
          <div class="grid grid-cols-2 gap-x-6 gap-y-2">
            <div v-for="fk in allFeatureKeys" :key="fk" class="flex items-center justify-between">
              <span class="text-xs text-gray-700 font-mono">{{ fk }}</span>
              <select :value="featureState(fk)" @change="changeFeature(fk, $event.target.value)" class="input text-xs py-0.5 w-28">
                <option value="default">Default</option>
                <option value="enabled">Enabled</option>
                <option value="disabled">Disabled</option>
              </select>
            </div>
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
import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notifications'
import { listAdmins, createAdmin, updateAdmin, deleteAdmin, getPermissions } from '@/api/adminManagement'
import { setRealmRole, removeRealmRole, setFeaturePermissions, clearFeaturePermission } from '@/api/adminPermissions'
import { myRealms } from '@/api/auth'
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
  { key: 'role',        label: 'Role' },
  { key: 'authType',    label: 'Auth type' },
  { key: 'active',      label: 'Status' },
  { key: 'lastLoginAt', label: 'Last login' },
]

function emptyForm() {
  return { username: '', displayName: '', email: '', role: 'ADMIN', authType: 'LOCAL', password: '', ldapDn: '', active: true }
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
  form.value = { username: row.username, displayName: row.displayName || '', email: row.email || '', role: row.role || 'ADMIN', authType: row.authType || 'LOCAL', password: '', ldapDn: row.ldapDn || '', active: row.active }
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

// ── All realms (for the realm picker in permissions dialog) ──────────────────
const allRealms = ref([])

onMounted(async () => {
  try {
    const { data } = await myRealms()
    allRealms.value = data
  } catch { /* ignore */ }
})

// Realms not already assigned to this admin
const availableRealms = computed(() => {
  if (!perms.value) return allRealms.value
  const assigned = new Set(perms.value.realmRoles.map(r => r.realmId))
  return allRealms.value.filter(r => !assigned.has(r.id))
})

const newRealmId   = ref('')
const newRealmRole = ref('ADMIN')
const allFeatureKeys = [
  'USER_CREATE', 'USER_EDIT', 'USER_DELETE', 'USER_ENABLE_DISABLE', 'USER_MOVE',
  'GROUP_MANAGE_MEMBERS', 'GROUP_CREATE_DELETE',
  'BULK_IMPORT', 'BULK_EXPORT',
  'REPORTS_RUN', 'REPORTS_EXPORT', 'REPORTS_SCHEDULE',
]

function featureState(fk) {
  const f = perms.value?.featurePermissions?.find(p => p.featureKey === fk)
  if (!f) return 'default'
  return f.enabled ? 'enabled' : 'disabled'
}

async function openPermissions(row) {
  permsTarget.value = row
  perms.value = null
  newRealmId.value = ''
  newRealmRole.value = 'ADMIN'
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

async function reloadPerms() {
  try {
    const { data } = await getPermissions(permsTarget.value.id)
    perms.value = data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  }
}

async function doAddRealmRole() {
  if (!newRealmId.value) return
  try {
    await setRealmRole(permsTarget.value.id, { realmId: newRealmId.value, baseRole: newRealmRole.value })
    newRealmId.value = ''
    await reloadPerms()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  }
}

async function changeRealmRole(realmId, baseRole) {
  try {
    await setRealmRole(permsTarget.value.id, { realmId, baseRole })
    await reloadPerms()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  }
}

async function doRemoveRealmRole(realmId) {
  try {
    await removeRealmRole(permsTarget.value.id, realmId)
    await reloadPerms()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  }
}

async function changeFeature(featureKey, state) {
  try {
    if (state === 'default') {
      await clearFeaturePermission(permsTarget.value.id, featureKey)
    } else {
      await setFeaturePermissions(permsTarget.value.id, [{ featureKey, enabled: state === 'enabled' }])
    }
    await reloadPerms()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
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
.badge-blue    { @apply inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-blue-100 text-blue-800; }
.badge-red     { @apply inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-red-100 text-red-700; }
.input         { @apply border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
</style>
