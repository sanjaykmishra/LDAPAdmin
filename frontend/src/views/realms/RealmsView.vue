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
            <th class="px-4 py-3 text-left font-medium text-gray-500">User Templates</th>
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
                  v-for="ut in r.userTemplates"
                  :key="ut.id"
                  class="text-xs bg-blue-50 text-blue-700 rounded px-1.5 py-0.5"
                >{{ ut.templateName }}{{ ut.objectClassNames?.length ? ` (${ut.objectClassNames.join(', ')})` : '' }}</span>
                <span v-if="!r.userTemplates?.length" class="text-xs text-gray-400">—</span>
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
        <!-- User Templates multi-select -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">User Templates</label>
          <div class="border border-gray-300 rounded-lg p-2 max-h-48 overflow-y-auto space-y-1">
            <label
              v-for="ut in userTemplates"
              :key="ut.id"
              class="flex items-center gap-2 px-2 py-1.5 rounded hover:bg-gray-50 cursor-pointer"
            >
              <input
                type="checkbox"
                :value="ut.id"
                v-model="form.userTemplateIds"
                class="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
              />
              <span class="text-sm text-gray-800">{{ ut.templateName }}</span>
              <span v-if="ut.objectClassNames?.length" class="text-xs text-gray-400">({{ ut.objectClassNames.join(', ') }})</span>
            </label>
            <p v-if="userTemplates.length === 0" class="text-xs text-gray-400 px-2 py-1">No user templates available. Create one first.</p>
          </div>
          <p class="text-xs text-gray-400 mt-1">Select the user templates available for creating users in this realm</p>
        </div>

        <!-- Approval Workflow (only shown when editing) -->
        <div v-if="editing" class="border-t border-gray-200 pt-4 mt-4">
          <h3 class="text-sm font-semibold text-gray-800 mb-3">Approval Workflow</h3>
          <label class="flex items-center gap-2 mb-2">
            <input type="checkbox" v-model="approvalEnabled"
              class="rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
            <span class="text-sm text-gray-700">Require approval for user creation</span>
          </label>
          <label class="flex items-center gap-2 mb-2">
            <input type="checkbox" v-model="moveApprovalEnabled"
              class="rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
            <span class="text-sm text-gray-700">Require approval for user move</span>
          </label>
          <label class="flex items-center gap-2 mb-3">
            <input type="checkbox" v-model="groupAddApprovalEnabled"
              class="rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
            <span class="text-sm text-gray-700">Require approval for group member addition</span>
          </label>

          <div v-if="approvalEnabled || moveApprovalEnabled || groupAddApprovalEnabled">
            <div v-if="ldapAuthEnabled" class="space-y-2">
              <FormField label="Approver Group DN" v-model="approverGroupDn"
                placeholder="cn=ldap-approvers,ou=groups,dc=example,dc=com" />
              <p class="text-xs text-gray-400">Members of this LDAP group will be able to approve user creation requests</p>
            </div>
            <div v-else class="space-y-2">
              <label class="block text-sm font-medium text-gray-700 mb-1">Approvers</label>
              <div class="border border-gray-300 rounded-lg p-2 max-h-48 overflow-y-auto space-y-1">
                <label v-for="admin in availableAdmins" :key="admin.id"
                  class="flex items-center gap-2 px-2 py-1.5 rounded hover:bg-gray-50 cursor-pointer">
                  <input type="checkbox" :value="admin.id" v-model="selectedApproverIds"
                    class="rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
                  <span class="text-sm text-gray-800">{{ admin.username }}</span>
                  <span v-if="admin.email" class="text-xs text-gray-400">{{ admin.email }}</span>
                </label>
                <p v-if="availableAdmins.length === 0" class="text-xs text-gray-400 px-2">No admin accounts available</p>
              </div>
              <p class="text-xs text-gray-400">Select admin accounts that can approve user creation in this realm</p>
            </div>
          </div>
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
import { listUserTemplates } from '@/api/userTemplates'
import { listDirectories } from '@/api/directories'
import { getRealmSettings, updateRealmSettings, getRealmApprovers, setRealmApprovers, getApprovalConfig } from '@/api/approvals'
import { listAdmins } from '@/api/adminManagement'
import FormField from '@/components/FormField.vue'
import AppModal from '@/components/AppModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const notif = useNotificationStore()

const loading        = ref(false)
const saving         = ref(false)
const realms         = ref([])
const userTemplates  = ref([])
const directories    = ref([])
const showModal      = ref(false)
const editing        = ref(null)
const deleteTarget   = ref(null)

const form = ref(emptyForm())

// Approval workflow state
const approvalEnabled = ref(false)
const moveApprovalEnabled = ref(false)
const groupAddApprovalEnabled = ref(false)
const approverGroupDn = ref('')
const ldapAuthEnabled = ref(false)
const selectedApproverIds = ref([])
const availableAdmins = ref([])

function emptyForm() {
  return {
    directoryId: '',
    name: '',
    userBaseDn: '',
    groupBaseDn: '',
    userTemplateIds: [],
  }
}

async function load() {
  loading.value = true
  try {
    const [realmsRes, templatesRes, dirsRes] = await Promise.all([
      listAllRealms(),
      listUserTemplates(),
      listDirectories(),
    ])
    realms.value = realmsRes.data
    userTemplates.value = templatesRes.data
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

async function openEdit(r) {
  editing.value = r.id
  form.value = {
    directoryId: r.directoryId,
    name: r.name,
    userBaseDn: r.userBaseDn,
    groupBaseDn: r.groupBaseDn,
    userTemplateIds: (r.userTemplates || []).map(ut => ut.id),
  }
  showModal.value = true

  // Load approval config
  try {
    const [settingsRes, configRes] = await Promise.all([
      getRealmSettings(r.directoryId, r.id),
      getApprovalConfig(r.directoryId, r.id),
    ])
    const settings = settingsRes.data.settings || {}
    approvalEnabled.value = settings['approval.user_create.enabled'] === 'true'
    moveApprovalEnabled.value = settings['approval.user_move.enabled'] === 'true'
    groupAddApprovalEnabled.value = settings['approval.group_member_add.enabled'] === 'true'
    approverGroupDn.value = settings['approval.approver_group_dn'] || ''
    ldapAuthEnabled.value = configRes.data.ldapAuthEnabled || false

    if (ldapAuthEnabled.value) {
      selectedApproverIds.value = []
    } else {
      const [approversRes, adminsRes] = await Promise.all([
        getRealmApprovers(r.directoryId, r.id),
        listAdmins(),
      ])
      selectedApproverIds.value = (approversRes.data || []).map(a => a.accountId)
      availableAdmins.value = adminsRes.data || []
    }
  } catch (e) {
    console.warn('Failed to load approval config:', e)
    approvalEnabled.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const targetDirId = form.value.directoryId
    const payload = {
      name: form.value.name,
      userBaseDn: form.value.userBaseDn,
      groupBaseDn: form.value.groupBaseDn,
      userTemplateIds: form.value.userTemplateIds,
    }
    if (editing.value) {
      await updateRealm(targetDirId, editing.value, payload)

      // Save approval settings
      const approvalSettings = {
        'approval.user_create.enabled': approvalEnabled.value ? 'true' : 'false',
        'approval.user_move.enabled': moveApprovalEnabled.value ? 'true' : 'false',
        'approval.group_member_add.enabled': groupAddApprovalEnabled.value ? 'true' : 'false',
      }
      const anyApprovalEnabled = approvalEnabled.value || moveApprovalEnabled.value || groupAddApprovalEnabled.value
      if (ldapAuthEnabled.value && anyApprovalEnabled) {
        approvalSettings['approval.approver_group_dn'] = approverGroupDn.value
      }
      await updateRealmSettings(targetDirId, editing.value, approvalSettings)

      // Save approvers if not LDAP auth mode
      if (!ldapAuthEnabled.value && anyApprovalEnabled) {
        try {
          await setRealmApprovers(targetDirId, editing.value, selectedApproverIds.value)
        } catch (e) {
          console.warn('Failed to save approvers:', e)
        }
      }

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
