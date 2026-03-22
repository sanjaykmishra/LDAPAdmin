<template>
  <div class="p-6">
    <!-- Header -->
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Users</h1>
        <p class="text-sm text-gray-500 mt-1">Directory: <code class="text-xs bg-gray-100 px-1 rounded">{{ dirId }}</code></p>
      </div>
      <div class="flex items-center gap-3">
        <div v-if="allProfiles.length > 1" class="flex items-center gap-2">
          <label class="text-sm text-gray-600 font-medium">Profile:</label>
          <select v-model="selectedProfileId" @change="onProfileChange"
            class="rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
            <option value="">All</option>
            <option v-for="p in allProfiles" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
        </div>
        <button v-if="selectedDns.size > 0" @click="openBulkUpdate" class="btn-secondary">
          Bulk Update ({{ selectedDns.size }})
        </button>
        <button @click="openCreate" class="btn-primary">+ New User</button>
      </div>
    </div>

    <!-- Search bar -->
    <div class="flex gap-3 mb-4">
      <input
        v-model="filterText"
        placeholder="LDAP filter, e.g. (cn=john*)"
        class="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        @keyup.enter="search"
      />
      <button @click="search" class="btn-primary">Search</button>
    </div>

    <DataTable :columns="cols" :rows="users" :loading="loading" row-key="dn"
      selectable v-model:selectedKeys="selectedDns"
      empty-text="No users found" empty-icon="users">
      <template #cell-dn="{ value }">
        <span class="inline-flex items-center gap-1">
          <code class="text-xs">{{ value }}</code>
          <CopyButton :text="value" />
        </span>
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
          <button @click="openResetPassword(row)" class="text-purple-600 hover:text-purple-800 text-xs font-medium">Password</button>
          <button @click="openMove(row)" class="text-blue-600 hover:text-blue-800 text-xs font-medium">Move</button>
          <button @click="timelineTarget = row; showTimeline = true" class="text-gray-500 hover:text-gray-700 text-xs font-medium">History</button>
          <button @click="confirmDelete(row)" class="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
        </div>
      </template>
    </DataTable>

    <!-- Pagination -->
    <div v-if="users.length >= limit" class="mt-4 flex justify-center">
      <button @click="loadMore" :disabled="loading" class="btn-secondary">Load more</button>
    </div>

    <!-- Profile picker modal (step 1 of create) -->
    <AppModal v-model="showTemplatePicker" title="Choose Profile" size="sm">
      <div class="space-y-2">
        <p class="text-sm text-gray-600 mb-3">Select a provisioning profile to define which attributes are available for the new user.</p>
        <div v-if="allProfiles.length === 0" class="text-sm text-gray-400 py-4 text-center">
          No provisioning profiles are configured for this directory. Create a profile in the Profiles settings first.
        </div>
        <button
          v-for="p in allProfiles"
          :key="p.id"
          @click="selectProfileAndCreate(p)"
          class="w-full text-left px-4 py-3 border border-gray-200 rounded-lg hover:bg-blue-50 hover:border-blue-300 transition-colors"
        >
          <span class="font-medium text-gray-900 text-sm">{{ p.name }}</span>
          <span class="text-xs text-gray-500 ml-2">({{ (p.objectClassNames || []).join(', ') }})</span>
        </button>
      </div>
      <template #footer>
        <button @click="showTemplatePicker = false" class="btn-secondary">Cancel</button>
      </template>
    </AppModal>

    <!-- Create/Edit modal (step 2 of create, or edit) -->
    <AppModal v-model="showModal" :title="editingDn ? 'Edit User' : 'New User'" size="lg">
      <UserForm :data="form" :is-edit="!!editingDn" :user-template-config="profileConfig" :dir-id="dirId" @update="v => form = v" />
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

    <!-- Reset Password modal -->
    <AppModal v-model="showResetPassword" title="Reset Password" size="sm">
      <div class="space-y-4">
        <p class="text-sm text-gray-600">Reset password for:</p>
        <p class="text-sm font-mono text-gray-900 bg-gray-50 px-3 py-2 rounded-lg break-all">{{ resetPwTarget?.dn }}</p>
        <PasswordPolicyStatus v-if="resetPwTarget" :directory-id="dirId" :user-dn="resetPwTarget.dn" />
        <FormField label="New Password" v-model="resetPwNew" type="password" required />
        <FormField label="Confirm Password" v-model="resetPwConfirm" type="password" required />
        <div v-if="resetPwNew" class="flex items-center gap-2">
          <div class="flex-1 h-1.5 bg-gray-200 rounded-full overflow-hidden">
            <div class="h-full rounded-full transition-all" :class="pwStrengthColor" :style="{ width: pwStrengthPct + '%' }"></div>
          </div>
          <span class="text-xs font-medium" :class="pwStrengthTextColor">{{ pwStrengthLabel }}</span>
        </div>
        <p v-if="resetPwConfirm && resetPwNew !== resetPwConfirm" class="text-xs text-red-600">Passwords do not match.</p>
        <p v-if="resetPwError" class="text-sm text-red-600">{{ resetPwError }}</p>
      </div>
      <template #footer>
        <button @click="showResetPassword = false" class="btn-secondary">Cancel</button>
        <button @click="doResetPassword" :disabled="saving || !resetPwNew || resetPwNew !== resetPwConfirm" class="btn-primary">
          {{ saving ? 'Resetting…' : 'Reset Password' }}
        </button>
      </template>
    </AppModal>

    <!-- Bulk Attribute Update modal -->
    <AppModal v-model="showBulkUpdate" title="Bulk Attribute Update" size="lg">
      <div class="space-y-4">
        <p class="text-sm text-gray-600">
          Apply attribute changes to <strong>{{ selectedDns.size }}</strong> selected user(s).
        </p>
        <div v-for="(mod, i) in bulkMods" :key="i" class="flex gap-2 items-end">
          <div class="flex-1">
            <label v-if="i === 0" class="block text-xs font-medium text-gray-600 mb-1">Operation</label>
            <select v-model="mod.operation" class="input w-full">
              <option value="REPLACE">Set (replace)</option>
              <option value="ADD">Add value</option>
              <option value="DELETE">Remove value</option>
            </select>
          </div>
          <div class="flex-1">
            <label v-if="i === 0" class="block text-xs font-medium text-gray-600 mb-1">Attribute</label>
            <input v-model="mod.attribute" placeholder="e.g. department" class="input w-full" />
          </div>
          <div class="flex-[2]">
            <label v-if="i === 0" class="block text-xs font-medium text-gray-600 mb-1">Value(s)</label>
            <input v-model="mod.value" :placeholder="mod.operation === 'DELETE' ? '(leave empty to remove all)' : 'e.g. Engineering'" class="input w-full" />
          </div>
          <button @click="bulkMods.splice(i, 1)" class="text-red-400 hover:text-red-600 text-lg leading-none pb-2"
            :class="{ 'invisible': bulkMods.length <= 1 }">&times;</button>
        </div>
        <button @click="addBulkMod" class="text-sm text-blue-600 hover:text-blue-800">+ Add another modification</button>
        <div v-if="bulkResult" class="p-3 rounded-lg text-sm" :class="bulkResult.errors ? 'bg-amber-50 border border-amber-200' : 'bg-green-50 border border-green-200'">
          <p><strong>{{ bulkResult.updated }}</strong> updated, <strong>{{ bulkResult.errors }}</strong> errors</p>
          <ul v-if="bulkResult.failures?.length" class="mt-1 space-y-0.5">
            <li v-for="f in bulkResult.failures" :key="f.dn" class="text-xs text-red-600">{{ f.dn }}: {{ f.message }}</li>
          </ul>
        </div>
      </div>
      <template #footer>
        <button @click="showBulkUpdate = false" class="btn-secondary">Close</button>
        <button @click="doBulkUpdate" :disabled="bulkUpdating || !canBulkUpdate" class="btn-primary">
          {{ bulkUpdating ? 'Updating…' : 'Apply Changes' }}
        </button>
      </template>
    </AppModal>

    <!-- Activity Timeline modal -->
    <AppModal v-model="showTimeline" title="Activity History" size="lg">
      <div v-if="timelineTarget" class="mb-3">
        <p class="text-xs font-mono text-gray-500 break-all">{{ timelineTarget.dn }}</p>
      </div>
      <EntryTimeline v-if="timelineTarget" :directory-id="dirId" :target-dn="timelineTarget.dn" />
      <template #footer>
        <button @click="showTimeline = false" class="btn-secondary">Close</button>
      </template>
    </AppModal>

    <ConfirmDialog v-model="showDelete" title="Delete User" :message="`Delete '${deleteTarget?.dn}'?`" confirm-label="Delete" danger @confirm="doDelete" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import { useApi } from '@/composables/useApi'
import * as usersApi from '@/api/users'
import * as groupsApi from '@/api/groups'
import { listProfiles, getProfile } from '@/api/profiles'
import DataTable from '@/components/DataTable.vue'
import AppModal from '@/components/AppModal.vue'
import FormField from '@/components/FormField.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import UserForm from './UserForm.vue'
import CopyButton from '@/components/CopyButton.vue'
import EntryTimeline from '@/components/EntryTimeline.vue'
import PasswordPolicyStatus from '@/components/PasswordPolicyStatus.vue'

const PAGE_SIZE = 50

const route  = useRoute()
const notif  = useNotificationStore()
const { loading, call } = useApi()

const dirId          = route.params.dirId
const users          = ref([])
const filterText     = ref('')
const limit          = ref(PAGE_SIZE)
const selectedDns    = ref(new Set())
const showTemplatePicker = ref(false)
const showModal      = ref(false)
const showMove       = ref(false)
const showDelete     = ref(false)
const editingDn      = ref(null)
const deleteTarget   = ref(null)
const moveTarget     = ref(null)
const newParentDn    = ref('')
const showTimeline   = ref(false)
const timelineTarget = ref(null)
const saving             = ref(false)
const showResetPassword  = ref(false)
const resetPwTarget      = ref(null)
const resetPwNew         = ref('')
const resetPwConfirm     = ref('')
const resetPwError       = ref('')

function computePasswordStrength(pw) {
  if (!pw) return 0
  let score = 0
  if (pw.length >= 8) score++
  if (pw.length >= 12) score++
  if (/[a-z]/.test(pw) && /[A-Z]/.test(pw)) score++
  if (/\d/.test(pw)) score++
  if (/[^a-zA-Z0-9]/.test(pw)) score++
  return score
}

const pwStrength = computed(() => computePasswordStrength(resetPwNew.value))
const pwStrengthPct = computed(() => Math.min(100, pwStrength.value * 20))
const pwStrengthLabel = computed(() => {
  const s = pwStrength.value
  if (s <= 1) return 'Weak'
  if (s <= 2) return 'Fair'
  if (s <= 3) return 'Good'
  return 'Strong'
})
const pwStrengthColor = computed(() => {
  const s = pwStrength.value
  if (s <= 1) return 'bg-red-500'
  if (s <= 2) return 'bg-amber-500'
  if (s <= 3) return 'bg-blue-500'
  return 'bg-green-500'
})
const pwStrengthTextColor = computed(() => {
  const s = pwStrength.value
  if (s <= 1) return 'text-red-600'
  if (s <= 2) return 'text-amber-600'
  if (s <= 3) return 'text-blue-600'
  return 'text-green-600'
})

const cols = [
  { key: 'dn',   label: 'DN' },
  { key: 'cn',   label: 'CN' },
  { key: 'mail', label: 'Email' },
  { key: 'enabled', label: 'Status' },
]

const allProfiles       = ref([])
const selectedProfileId = ref('')
const profileData       = ref(null)
const profileConfig     = ref(null)

const emptyForm = () => {
  return {
    parentDn: profileData.value?.targetOuDn || '',
    rdnAttribute: profileData.value?.rdnAttribute || 'uid',
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
      baseDn: profileData.value?.targetOuDn || undefined,
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

async function openCreate() {
  editingDn.value = null
  profileConfig.value = null
  if (allProfiles.value.length === 1) {
    await selectProfileAndCreate(allProfiles.value[0])
  } else if (allProfiles.value.length > 1) {
    showTemplatePicker.value = true
  } else {
    form.value = emptyForm()
    showModal.value = true
  }
}

async function selectProfileAndCreate(p) {
  showTemplatePicker.value = false
  try {
    const { data } = await getProfile(dirId, p.id)
    profileData.value = data
    profileConfig.value = data
  } catch (e) {
    console.warn('Failed to load profile:', e)
    profileConfig.value = null
  }
  form.value = emptyForm()
  showModal.value = true
}

async function openEdit(row) {
  editingDn.value = row.dn
  const attrs = row._raw?.attributes || {}

  // Try to resolve a matching profile from the available profiles
  profileConfig.value = null
  const userOCs = (attrs.objectClass || attrs.objectclass || []).map(s => s.toLowerCase())
  if (userOCs.length && allProfiles.value.length) {
    const match = allProfiles.value.find(p =>
      (p.objectClassNames || []).every(oc => userOCs.includes(oc.toLowerCase()))
    )
    if (match) {
      try {
        const { data } = await getProfile(dirId, match.id)
        profileConfig.value = data
      } catch (e) { console.warn('Failed to load profile for edit:', e) }
    }
  }

  form.value = { dn: row.dn, attributes: Object.fromEntries(
    Object.entries(attrs).map(([k, v]) => [k, Array.isArray(v) ? v.join('\n') : v])
  )}
  showModal.value = true
}

async function save() {
  saving.value = true
  try {
    if (editingDn.value) {
      const mods = Object.entries(form.value.attributes || {})
        .filter(([attr]) => attr.toLowerCase() !== 'objectclass')
        .map(([attr, val]) => ({
          operation: 'REPLACE',
          attribute: attr,
          values: typeof val === 'string'
            ? val.split('\n').map(v => v.trim()).filter(v => v.length > 0)
            : [String(val)],
        }))
        .filter(m => m.values.length > 0)
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
      // Include objectClasses from the selected user template
      if (profileConfig.value?.objectClassNames?.length) {
        attributes.objectClass = profileConfig.value.objectClassNames
      }
      const createRes = await usersApi.createUser(dirId, { dn, attributes })
      if (createRes.status === 202) {
        // Approval workflow intercepted — user creation is pending approval
        notif.success('User creation submitted for approval')
      } else {
        // Add user to any groups selected during creation
        const pending = f._pendingGroups || []
        let pendingApprovalCount = 0
        for (const pg of pending) {
          try {
            const groupRes = await groupsApi.addGroupMember(dirId, pg.dn, {
              memberAttribute: pg.memberAttr,
              memberValue: dn,
            })
            if (groupRes.status === 202) pendingApprovalCount++
          } catch (e) {
              console.warn('Failed to add user to group', pg.dn, e)
              notif.error(`Failed to add user to group: ${pg.dn}`)
            }
        }
        if (pendingApprovalCount > 0) {
          notif.success(`User created. ${pendingApprovalCount} group membership(s) submitted for approval`)
        } else {
          notif.success(pending.length ? `User created and added to ${pending.length} group(s)` : 'User created')
        }
      }
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
    const res = await usersApi.moveUser(dirId, moveTarget.value.dn, { newParentDn: newParentDn.value })
    if (res.status === 202) {
      notif.success('User move submitted for approval')
    } else {
      notif.success('User moved')
    }
    showMove.value = false
    await load()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally { saving.value = false }
}

function openResetPassword(row) {
  resetPwTarget.value  = row
  resetPwNew.value     = ''
  resetPwConfirm.value = ''
  resetPwError.value   = ''
  showResetPassword.value = true
}

async function doResetPassword() {
  resetPwError.value = ''
  saving.value = true
  try {
    await usersApi.resetPassword(dirId, resetPwTarget.value.dn, resetPwNew.value)
    notif.success('Password reset successfully')
    showResetPassword.value = false
  } catch (e) {
    resetPwError.value = e.response?.data?.detail || e.message
  } finally {
    saving.value = false
  }
}

// ── Bulk Attribute Update ────────────────────────────────────────────────────

const showBulkUpdate = ref(false)
const bulkUpdating   = ref(false)
const bulkResult     = ref(null)
const bulkMods       = ref([{ operation: 'REPLACE', attribute: '', value: '' }])

const canBulkUpdate = computed(() =>
  bulkMods.value.some(m => m.attribute && m.attribute.trim())
)

function addBulkMod() {
  bulkMods.value.push({ operation: 'REPLACE', attribute: '', value: '' })
}

function openBulkUpdate() {
  bulkMods.value = [{ operation: 'REPLACE', attribute: '', value: '' }]
  bulkResult.value = null
  showBulkUpdate.value = true
}

async function doBulkUpdate() {
  bulkUpdating.value = true
  bulkResult.value = null
  try {
    const modifications = bulkMods.value
      .filter(m => m.attribute && m.attribute.trim())
      .map(m => ({
        operation: m.operation,
        attribute: m.attribute.trim(),
        values: m.value ? m.value.split('|').map(v => v.trim()).filter(Boolean) : [],
      }))
    const { data } = await usersApi.bulkUpdateAttributes(dirId, {
      dns: [...selectedDns.value],
      modifications,
    })
    bulkResult.value = data
    if (data.errors === 0) {
      notif.success(`${data.updated} user(s) updated successfully`)
      showBulkUpdate.value = false
      selectedDns.value = new Set()
      await load()
    } else {
      notif.warn(`${data.updated} updated, ${data.errors} failed`)
      await load()
    }
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    bulkUpdating.value = false
  }
}

function confirmDelete(row) { deleteTarget.value = row; showDelete.value = true }

async function doDelete() {
  await call(() => usersApi.deleteUser(dirId, deleteTarget.value.dn), { successMsg: 'User deleted' })
  await load()
}

async function loadProfiles() {
  try {
    const { data: profiles } = await listProfiles(dirId)
    allProfiles.value = profiles
    if (profiles.length === 1) {
      selectedProfileId.value = profiles[0].id
      profileData.value = profiles[0]
    }
  } catch (e) {
    console.warn('Failed to load profiles:', e)
  }
}

function onProfileChange() {
  const p = allProfiles.value.find(p => p.id === selectedProfileId.value)
  profileData.value = p || null
  limit.value = PAGE_SIZE
  load()
}

onMounted(async () => {
  await loadProfiles()
  load()
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
