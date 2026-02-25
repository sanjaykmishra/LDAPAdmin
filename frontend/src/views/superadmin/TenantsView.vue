<template>
  <div class="p-6 max-w-5xl">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Tenants</h1>
      <button @click="openCreateTenant" class="btn-primary">+ New Tenant</button>
    </div>

    <!-- Tenants list -->
    <div class="bg-white border border-gray-200 rounded-xl overflow-hidden mb-8">
      <div v-if="loading" class="p-8 text-center text-gray-500 text-sm">Loading…</div>
      <div v-else-if="tenants.length === 0" class="p-8 text-center text-gray-400 text-sm">No tenants yet.</div>
      <table v-else class="w-full text-sm">
        <thead class="bg-gray-50 border-b border-gray-100">
          <tr>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Name</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Slug</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Max Directories</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Features</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-50">
          <tr
            v-for="t in tenants"
            :key="t.id"
            class="hover:bg-gray-50 cursor-pointer"
            @click="selectTenant(t)"
            :class="{ 'ring-2 ring-inset ring-blue-500': selectedTenant?.id === t.id }"
          >
            <td class="px-4 py-3 font-medium text-gray-900">{{ t.name }}</td>
            <td class="px-4 py-3 text-gray-600 font-mono text-xs">{{ t.slug }}</td>
            <td class="px-4 py-3 text-gray-600">{{ t.maxDirectories ?? '—' }}</td>
            <td class="px-4 py-3">
              <div class="flex flex-wrap gap-1">
                <span v-for="f in (t.enabledFeatures ?? [])" :key="f" class="text-xs bg-blue-50 text-blue-700 rounded px-1.5 py-0.5">{{ f }}</span>
              </div>
            </td>
            <td class="px-4 py-3 text-right">
              <button @click.stop="openEditTenant(t)" class="text-blue-600 hover:text-blue-800 text-xs font-medium mr-3">Edit</button>
              <button @click.stop="confirmDeleteTenant(t)" class="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Admins panel (shown when a tenant is selected) -->
    <div v-if="selectedTenant" class="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <div class="flex items-center justify-between px-6 py-4 border-b border-gray-100">
        <h2 class="text-base font-semibold text-gray-900">Admins — {{ selectedTenant.name }}</h2>
        <button @click="openCreateAdmin" class="btn-sm-primary">+ Add Admin</button>
      </div>
      <div v-if="adminsLoading" class="p-6 text-center text-gray-500 text-sm">Loading…</div>
      <div v-else-if="admins.length === 0" class="p-6 text-center text-gray-400 text-sm">No admins for this tenant.</div>
      <table v-else class="w-full text-sm">
        <thead class="bg-gray-50 border-b border-gray-100">
          <tr>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Username</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Email</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Role</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-50">
          <tr v-for="a in admins" :key="a.id" class="hover:bg-gray-50">
            <td class="px-4 py-3 font-medium text-gray-900">{{ a.username }}</td>
            <td class="px-4 py-3 text-gray-600">{{ a.email ?? '—' }}</td>
            <td class="px-4 py-3 text-gray-600">{{ a.role ?? 'ADMIN' }}</td>
            <td class="px-4 py-3 text-right">
              <button @click="openEditAdmin(a)" class="text-blue-600 hover:text-blue-800 text-xs font-medium mr-3">Edit</button>
              <button @click="confirmDeleteAdmin(a)" class="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Tenant create/edit modal -->
    <AppModal v-if="showTenantModal" :title="editTenant ? 'Edit Tenant' : 'New Tenant'" size="lg" @close="showTenantModal = false">
      <form @submit.prevent="saveTenant" class="space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <FormField label="Name" v-model="tenantForm.name" required />
          <FormField label="Slug" v-model="tenantForm.slug" placeholder="acme" required />
          <FormField label="Max Directories" v-model.number="tenantForm.maxDirectories" type="number" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">Enabled Features</label>
          <div class="flex flex-wrap gap-2">
            <label v-for="f in allFeatures" :key="f" class="flex items-center gap-1 text-sm">
              <input type="checkbox" :value="f" v-model="tenantForm.enabledFeatures" class="rounded" />
              {{ f }}
            </label>
          </div>
        </div>
        <div class="flex justify-end gap-2 pt-2">
          <button type="button" @click="showTenantModal = false" class="btn-secondary">Cancel</button>
          <button type="submit" :disabled="tenantSaving" class="btn-primary">{{ tenantSaving ? 'Saving…' : 'Save' }}</button>
        </div>
      </form>
    </AppModal>

    <!-- Admin create/edit modal -->
    <AppModal v-if="showAdminModal" :title="editAdmin ? 'Edit Admin' : 'New Admin'" size="sm" @close="showAdminModal = false">
      <form @submit.prevent="saveAdmin" class="space-y-4">
        <FormField label="Username" v-model="adminForm.username" required />
        <FormField label="Email" v-model="adminForm.email" />
        <FormField v-if="!editAdmin" label="Password" v-model="adminForm.password" type="password" required />
        <FormField v-else label="New Password" v-model="adminForm.password" type="password" placeholder="Leave blank to keep" />
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Role</label>
          <select v-model="adminForm.role" class="input w-full">
            <option value="ADMIN">Admin</option>
            <option value="READ_ONLY">Read Only</option>
          </select>
        </div>
        <div class="flex justify-end gap-2 pt-2">
          <button type="button" @click="showAdminModal = false" class="btn-secondary">Cancel</button>
          <button type="submit" :disabled="adminSaving" class="btn-primary">{{ adminSaving ? 'Saving…' : 'Save' }}</button>
        </div>
      </form>
    </AppModal>

    <!-- Delete confirms -->
    <ConfirmDialog
      v-if="deleteTenantTarget"
      :message="`Delete tenant '${deleteTenantTarget.name}'? This is irreversible.`"
      @confirm="doDeleteTenant"
      @cancel="deleteTenantTarget = null"
    />
    <ConfirmDialog
      v-if="deleteAdminTarget"
      :message="`Remove admin '${deleteAdminTarget.username}'?`"
      @confirm="doDeleteAdmin"
      @cancel="deleteAdminTarget = null"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useNotificationStore } from '@/stores/notifications'
import {
  listTenants, createTenant, updateTenant, deleteTenant,
  listAdmins, createAdmin, updateAdmin, deleteAdmin,
} from '@/api/superadmin'
import FormField from '@/components/FormField.vue'
import AppModal from '@/components/AppModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const notif = useNotificationStore()

const loading       = ref(false)
const tenants       = ref([])
const selectedTenant = ref(null)

const adminsLoading = ref(false)
const admins        = ref([])

const showTenantModal  = ref(false)
const editTenant       = ref(null)
const tenantSaving     = ref(false)
const deleteTenantTarget = ref(null)

const showAdminModal  = ref(false)
const editAdmin       = ref(null)
const adminSaving     = ref(false)
const deleteAdminTarget = ref(null)

const allFeatures = [
  'REPORTS_RUN', 'REPORTS_EXPORT', 'REPORTS_SCHEDULE',
  'AUDIT_LOG', 'BULK_IMPORT', 'ATTRIBUTE_PROFILES',
]

const tenantForm = ref({ name: '', slug: '', maxDirectories: null, enabledFeatures: [] })
const adminForm  = ref({ username: '', email: '', password: '', role: 'ADMIN' })

// ── Tenants ───────────────────────────────────────────────────────────────────

async function loadTenants() {
  loading.value = true
  try {
    const { data } = await listTenants()
    tenants.value = data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    loading.value = false
  }
}

onMounted(loadTenants)

function openCreateTenant() {
  editTenant.value = null
  tenantForm.value = { name: '', slug: '', maxDirectories: null, enabledFeatures: [] }
  showTenantModal.value = true
}

function openEditTenant(t) {
  editTenant.value = t
  tenantForm.value = {
    name:             t.name,
    slug:             t.slug,
    maxDirectories:   t.maxDirectories ?? null,
    enabledFeatures:  [...(t.enabledFeatures ?? [])],
  }
  showTenantModal.value = true
}

async function saveTenant() {
  tenantSaving.value = true
  try {
    if (editTenant.value) {
      await updateTenant(editTenant.value.id, tenantForm.value)
      notif.success('Tenant updated')
    } else {
      await createTenant(tenantForm.value)
      notif.success('Tenant created')
    }
    showTenantModal.value = false
    await loadTenants()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    tenantSaving.value = false
  }
}

function confirmDeleteTenant(t) { deleteTenantTarget.value = t }

async function doDeleteTenant() {
  try {
    await deleteTenant(deleteTenantTarget.value.id)
    notif.success('Tenant deleted')
    if (selectedTenant.value?.id === deleteTenantTarget.value.id) {
      selectedTenant.value = null
      admins.value = []
    }
    deleteTenantTarget.value = null
    await loadTenants()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
    deleteTenantTarget.value = null
  }
}

// ── Admins ────────────────────────────────────────────────────────────────────

async function selectTenant(t) {
  if (selectedTenant.value?.id === t.id) {
    selectedTenant.value = null
    admins.value = []
    return
  }
  selectedTenant.value = t
  await loadAdmins()
}

async function loadAdmins() {
  if (!selectedTenant.value) return
  adminsLoading.value = true
  try {
    const { data } = await listAdmins(selectedTenant.value.id)
    admins.value = data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    adminsLoading.value = false
  }
}

function openCreateAdmin() {
  editAdmin.value = null
  adminForm.value = { username: '', email: '', password: '', role: 'ADMIN' }
  showAdminModal.value = true
}

function openEditAdmin(a) {
  editAdmin.value = a
  adminForm.value = { username: a.username, email: a.email ?? '', password: '', role: a.role ?? 'ADMIN' }
  showAdminModal.value = true
}

async function saveAdmin() {
  adminSaving.value = true
  try {
    const payload = { ...adminForm.value }
    if (editAdmin.value && !payload.password) delete payload.password
    if (editAdmin.value) {
      await updateAdmin(selectedTenant.value.id, editAdmin.value.id, payload)
      notif.success('Admin updated')
    } else {
      await createAdmin(selectedTenant.value.id, payload)
      notif.success('Admin created')
    }
    showAdminModal.value = false
    await loadAdmins()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    adminSaving.value = false
  }
}

function confirmDeleteAdmin(a) { deleteAdminTarget.value = a }

async function doDeleteAdmin() {
  try {
    await deleteAdmin(selectedTenant.value.id, deleteAdminTarget.value.id)
    notif.success('Admin removed')
    deleteAdminTarget.value = null
    await loadAdmins()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
    deleteAdminTarget.value = null
  }
}
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary    { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
.btn-secondary  { @apply px-4 py-2 bg-white border border-gray-300 rounded-lg text-sm font-medium hover:bg-gray-50; }
.btn-sm-primary { @apply px-3 py-1.5 bg-blue-600 text-white rounded-lg text-xs font-medium hover:bg-blue-700; }
.input          { @apply border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
</style>
