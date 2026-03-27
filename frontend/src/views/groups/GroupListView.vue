<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Groups</h1>
        <p class="text-sm text-gray-500 mt-1">Manage groups in this directory</p>
      </div>
      <div class="flex items-center gap-2">
        <div v-if="allProfiles.length > 1" class="flex items-center gap-2">
          <label class="text-sm text-gray-600 font-medium">Profile:</label>
          <select v-model="selectedProfileId" @change="onProfileChange"
            class="rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
            <option value="">All</option>
            <option v-for="p in allProfiles" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
        </div>
        <button @click="openCreate" class="btn-primary">+ New Group</button>
      </div>
    </div>

    <!-- Search -->
    <div class="flex gap-2 mb-2">
      <input v-model="filterText" placeholder="Filter (e.g. cn=staff*)" @keyup.enter="load"
        class="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
      <button @click="load" class="btn-primary">Search</button>
    </div>

    <DataTable :columns="cols" :rows="groups" :loading="loading" row-key="dn">
      <template #cell-dn="{ value }">
        <span class="inline-flex items-center gap-1">
          <span class="text-xs truncate max-w-xs" :title="value">{{ value }}</span>
          <CopyButton :text="value" />
        </span>
      </template>
      <template #cell-description="{ value }">
        <span class="text-gray-600 text-xs">{{ value }}</span>
      </template>
      <template #actions="{ row }">
        <div class="flex gap-2 justify-end whitespace-nowrap">
          <button @click="openEdit(row)" class="text-blue-600 hover:text-blue-800 text-xs font-medium">Edit</button>
          <button @click="openMembers(row)" class="text-blue-600 hover:text-blue-800 text-xs font-medium">Members</button>
          <button @click="confirmDelete(row)" class="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
        </div>
      </template>
    </DataTable>

    <!-- Create group -->
    <AppModal v-model="showCreate" title="New Group" size="lg">
      <div class="grid grid-cols-3 gap-2">
        <FormField label="Group Name (cn) (RDN)" v-model="createForm.cn" required />
        <div class="col-span-2">
          <FormField label="DN" :model-value="computedGroupDn" required disabled />
        </div>
      </div>
      <FormField label="Object Class" v-model="createForm.objectClass" />
      <FormField label="Owner" v-model="createForm.owner" placeholder="DN of the group owner" />
      <FormField label="Description" v-model="createForm.description" placeholder="Group description" />
      <template #footer>
        <button @click="showCreate = false" class="btn-neutral">Cancel</button>
        <button @click="doCreate" :disabled="saving" class="btn-primary">Create</button>
      </template>
    </AppModal>

    <!-- Edit group -->
    <AppModal v-model="showEdit" title="Edit Group" size="md">
      <FormField label="Owner" v-model="editForm.owner" placeholder="DN of the group owner" />
      <FormField label="Description" v-model="editForm.description" placeholder="Group description" />
      <template #footer>
        <button @click="showEdit = false" class="btn-neutral">Cancel</button>
        <button @click="doEdit" :disabled="saving" class="btn-primary">{{ saving ? 'Saving…' : 'Save' }}</button>
      </template>
    </AppModal>

    <!-- Members drawer -->
    <AppModal v-model="showMembers" :title="`Members — ${selectedGroup?.cn || ''}`" size="lg">
      <div class="mb-3 flex gap-2">
        <DnPicker v-model="newMemberDn" :directory-id="dirId" class="flex-1" />
        <button @click="addMember" class="btn-primary">Add</button>
        <button @click="showBulkAdd = !showBulkAdd" class="btn-secondary">Bulk Add</button>
      </div>
      <div v-if="showBulkAdd" class="mb-3 p-3 bg-gray-50 rounded-lg border border-gray-200">
        <label class="block text-xs font-medium text-gray-600 mb-1">Add multiple members (one DN per line)</label>
        <textarea v-model="bulkMemberDns" rows="4" placeholder="cn=Alice,ou=Users,dc=example,dc=com&#10;cn=Bob,ou=Users,dc=example,dc=com"
          class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500 mb-2"></textarea>
        <div v-if="bulkResult" class="mb-2 p-2 rounded-lg text-xs" :class="bulkResult.failed ? 'bg-amber-50 border border-amber-200 text-amber-800' : 'bg-green-50 border border-green-200 text-green-800'">
          Added {{ bulkResult.added }}, failed {{ bulkResult.failed }}.
          <ul v-if="bulkResult.errors?.length" class="mt-1 list-disc pl-4">
            <li v-for="(e, i) in bulkResult.errors" :key="i">{{ e.memberValue }}: {{ e.error }}</li>
          </ul>
        </div>
        <div class="flex justify-end">
          <button @click="doBulkAdd" :disabled="bulkAdding || !bulkMemberDns.trim()" class="btn-primary text-xs">
            {{ bulkAdding ? 'Adding…' : 'Add All' }}
          </button>
        </div>
      </div>
      <ul class="divide-y divide-gray-100 max-h-80 overflow-y-auto">
        <li v-for="dn in members" :key="dn" class="flex items-center justify-between py-2 text-sm">
          <span class="text-xs text-gray-700 truncate" :title="dn">{{ dn }}</span>
          <button @click="removeMember(dn)" class="text-red-500 hover:text-red-700 text-xs">Remove</button>
        </li>
        <li v-if="!members.length" class="py-4 text-center text-gray-400 text-sm">No members</li>
      </ul>
    </AppModal>

    <ConfirmDialog v-model="showDelete" title="Delete Group" :message="`Delete '${deleteTarget?.dn}'?`" confirm-label="Delete" danger @confirm="doDelete" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import { useApi } from '@/composables/useApi'
import * as groupsApi from '@/api/groups'
import { listProfiles } from '@/api/profiles'
import DataTable from '@/components/DataTable.vue'
import AppModal from '@/components/AppModal.vue'
import FormField from '@/components/FormField.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import DnPicker from '@/components/DnPicker.vue'
import CopyButton from '@/components/CopyButton.vue'

const route = useRoute()
const notif = useNotificationStore()
const { loading, call } = useApi()

const dirId         = route.params.dirId
const groups        = ref([])
const filterText    = ref('')
const showCreate    = ref(false)
const showEdit      = ref(false)
const showMembers   = ref(false)
const showDelete    = ref(false)
const selectedGroup = ref(null)
const deleteTarget  = ref(null)
const members       = ref([])
const newMemberDn   = ref('')
const saving        = ref(false)
const allProfiles     = ref([])
const selectedProfileId = ref('')
const profileData     = ref(null)
const showBulkAdd   = ref(false)
const bulkMemberDns = ref('')
const bulkAdding    = ref(false)
const bulkResult    = ref(null)
const createForm    = ref({ parentDn: '', cn: '', objectClass: 'groupOfNames', owner: '', description: '' })
const editForm      = ref({ owner: '', description: '' })
const editingDn     = ref(null)

const computedGroupDn = computed(() => {
  const cn = createForm.value.cn?.trim()
  const base = createForm.value.parentDn
  if (!cn || !base) return ''
  return `cn=${cn},${base}`
})

const cols = [
  { key: 'dn',          label: 'DN' },
  { key: 'cn',          label: 'Name' },
  { key: 'description', label: 'Description' },
]

async function load() {
  await call(async () => {
    const { data } = await groupsApi.searchGroups(dirId, {
      filter: filterText.value || undefined,
      baseDn: profileData.value?.targetOuDn || undefined,
    })
    const entries = Array.isArray(data) ? data : (data?.entries || [])
    groups.value = entries.map(e => ({
      dn:          e.dn,
      cn:          e.attributes?.cn?.[0] || '—',
      description: e.attributes?.description?.[0] || '—',
      _owner:      e.attributes?.owner?.[0] || '',
      _members:    e.attributes?.member || e.attributes?.uniqueMember || [],
      _memberAttr: e.attributes?.member ? 'member' : e.attributes?.uniqueMember ? 'uniqueMember' : 'member',
    }))
  })
}

async function doCreate() {
  saving.value = true
  try {
    const f = createForm.value
    const dn = computedGroupDn.value
    const attributes = {
      cn: [f.cn],
      objectClass: [f.objectClass],
    }
    if (f.owner?.trim()) attributes.owner = [f.owner.trim()]
    if (f.description?.trim()) attributes.description = [f.description.trim()]
    await groupsApi.createGroup(dirId, { dn, attributes })
    notif.success('Group created')
    showCreate.value = false
    await load()
  } catch (e) { notif.error(e.response?.data?.detail || e.message) }
  finally { saving.value = false }
}

function openCreate() {
  createForm.value = {
    parentDn: profileData.value?.targetOuDn || '',
    cn: '',
    objectClass: 'groupOfNames',
    owner: '',
    description: '',
  }
  showCreate.value = true
}

function openEdit(row) {
  editingDn.value = row.dn
  editForm.value = {
    owner: row._owner,
    description: row.description === '—' ? '' : row.description,
  }
  showEdit.value = true
}

async function doEdit() {
  saving.value = true
  try {
    const mods = [
      { operation: 'REPLACE', attribute: 'owner', values: editForm.value.owner?.trim() ? [editForm.value.owner.trim()] : [] },
      { operation: 'REPLACE', attribute: 'description', values: editForm.value.description?.trim() ? [editForm.value.description.trim()] : [] },
    ]
    await groupsApi.updateGroup(dirId, editingDn.value, { modifications: mods })
    notif.success('Group updated')
    showEdit.value = false
    await load()
  } catch (e) { notif.error(e.response?.data?.detail || e.message) }
  finally { saving.value = false }
}

function openMembers(row) {
  selectedGroup.value = row
  members.value       = [...row._members]
  newMemberDn.value   = ''
  showBulkAdd.value   = false
  bulkMemberDns.value = ''
  bulkResult.value    = null
  showMembers.value   = true
}

async function addMember() {
  if (!newMemberDn.value.trim()) return
  const res = await call(
    () => groupsApi.addGroupMember(dirId, selectedGroup.value.dn, { memberAttribute: selectedGroup.value._memberAttr, memberValue: newMemberDn.value }),
  )
  if (res?.status === 202) {
    notif.success('Group member addition submitted for approval')
  } else {
    notif.success('Member added')
    members.value.push(newMemberDn.value)
  }
  newMemberDn.value = ''
}

async function doBulkAdd() {
  const dns = bulkMemberDns.value.split('\n').map(s => s.trim()).filter(Boolean)
  if (!dns.length) return
  bulkAdding.value = true
  bulkResult.value = null
  try {
    const { data } = await groupsApi.addGroupMembersBulk(dirId, selectedGroup.value.dn, {
      memberAttribute: selectedGroup.value._memberAttr,
      memberValues: dns,
    })
    bulkResult.value = data
    // Refresh members list
    for (const d of dns) {
      if (!members.value.includes(d)) members.value.push(d)
    }
    if (data.added > 0) bulkMemberDns.value = ''
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    bulkAdding.value = false
  }
}

async function removeMember(dn) {
  await call(
    () => groupsApi.removeGroupMember(dirId, selectedGroup.value.dn, { memberAttribute: selectedGroup.value._memberAttr, memberValue: dn }),
    { successMsg: 'Member removed' }
  )
  members.value = members.value.filter(m => m !== dn)
}

function confirmDelete(row) { deleteTarget.value = row; showDelete.value = true }
async function doDelete() {
  await call(() => groupsApi.deleteGroup(dirId, deleteTarget.value.dn), { successMsg: 'Group deleted' })
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
  } catch (e) { console.warn('Failed to load profiles:', e) }
}

function onProfileChange() {
  const p = allProfiles.value.find(p => p.id === selectedProfileId.value)
  profileData.value = p || null
  load()
}

onMounted(async () => {
  await loadProfiles()
  load()
})
</script>

<style scoped>
@reference "tailwindcss";
</style>
