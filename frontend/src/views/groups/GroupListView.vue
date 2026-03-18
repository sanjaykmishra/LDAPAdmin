<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Groups</h1>
      <div class="flex items-center gap-3">
        <div v-if="allRealms.length > 1" class="flex items-center gap-2">
          <label class="text-sm text-gray-600 font-medium">Realm:</label>
          <select v-model="selectedRealmId" @change="onRealmChange"
            class="rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
            <option v-for="r in allRealms" :key="r.id" :value="r.id">{{ r.name }}</option>
          </select>
        </div>
        <button @click="openCreate" class="btn-primary">+ New Group</button>
      </div>
    </div>

    <!-- Search -->
    <div class="flex gap-3 mb-4">
      <input v-model="filterText" placeholder="Filter (e.g. cn=staff*)" @keyup.enter="load"
        class="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
      <button @click="load" class="btn-primary">Search</button>
    </div>

    <DataTable :columns="cols" :rows="groups" :loading="loading" row-key="dn">
      <template #cell-dn="{ value }"><code class="text-xs">{{ value }}</code></template>
      <template #cell-description="{ value }">
        <span class="text-gray-600 text-xs">{{ value }}</span>
      </template>
      <template #actions="{ row }">
        <div class="flex gap-3 justify-end whitespace-nowrap">
          <button @click="openEdit(row)" class="text-blue-600 hover:text-blue-800 text-xs font-medium">Edit</button>
          <button @click="openMembers(row)" class="text-blue-600 hover:text-blue-800 text-xs font-medium">Members</button>
          <button @click="confirmDelete(row)" class="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
        </div>
      </template>
    </DataTable>

    <!-- Create group -->
    <AppModal v-model="showCreate" title="New Group" size="md">
      <FormField label="Group Name (cn) (RDN)" v-model="createForm.cn" required />
      <FormField label="DN" :model-value="computedGroupDn" required disabled />
      <FormField label="Object Class" v-model="createForm.objectClass" />
      <FormField label="Owner" v-model="createForm.owner" placeholder="DN of the group owner" />
      <FormField label="Description" v-model="createForm.description" placeholder="Group description" />
      <template #footer>
        <button @click="showCreate = false" class="btn-secondary">Cancel</button>
        <button @click="doCreate" :disabled="saving" class="btn-primary">Create</button>
      </template>
    </AppModal>

    <!-- Edit group -->
    <AppModal v-model="showEdit" title="Edit Group" size="md">
      <FormField label="Owner" v-model="editForm.owner" placeholder="DN of the group owner" />
      <FormField label="Description" v-model="editForm.description" placeholder="Group description" />
      <template #footer>
        <button @click="showEdit = false" class="btn-secondary">Cancel</button>
        <button @click="doEdit" :disabled="saving" class="btn-primary">{{ saving ? 'Saving…' : 'Save' }}</button>
      </template>
    </AppModal>

    <!-- Members drawer -->
    <AppModal v-model="showMembers" :title="`Members — ${selectedGroup?.cn || ''}`" size="lg">
      <div class="mb-3 flex gap-2">
        <input v-model="newMemberDn" placeholder="member DN to add" @keyup.enter="addMember"
          class="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
        <button @click="addMember" class="btn-primary">Add</button>
      </div>
      <ul class="divide-y divide-gray-100 max-h-80 overflow-y-auto">
        <li v-for="dn in members" :key="dn" class="flex items-center justify-between py-2 text-sm">
          <code class="text-xs">{{ dn }}</code>
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
import { listRealms } from '@/api/realms'
import DataTable from '@/components/DataTable.vue'
import AppModal from '@/components/AppModal.vue'
import FormField from '@/components/FormField.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

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
const allRealms     = ref([])
const selectedRealmId = ref(route.query.realmId || '')
const realmData     = ref(null)
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
      baseDn: realmData.value?.groupBaseDn || undefined,
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
    parentDn: realmData.value?.groupBaseDn || '',
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
  showMembers.value   = true
}

async function addMember() {
  if (!newMemberDn.value.trim()) return
  await call(
    () => groupsApi.addGroupMember(dirId, selectedGroup.value.dn, { memberAttribute: selectedGroup.value._memberAttr, memberValue: newMemberDn.value }),
    { successMsg: 'Member added' }
  )
  members.value.push(newMemberDn.value)
  newMemberDn.value = ''
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

function selectRealm(realms) {
  const match = selectedRealmId.value
    ? realms.find(r => r.id === selectedRealmId.value)
    : null
  const selected = match || realms[0]
  selectedRealmId.value = selected.id
  realmData.value = selected
}

async function loadRealm() {
  try {
    const { data: realms } = await listRealms(dirId)
    allRealms.value = realms
    if (realms.length) selectRealm(realms)
  } catch { /* best-effort */ }
}

function onRealmChange() {
  const realm = allRealms.value.find(r => r.id === selectedRealmId.value)
  if (realm) realmData.value = realm
  load()
}

onMounted(async () => {
  await loadRealm()
  load()
})
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary   { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700; }
.btn-secondary { @apply px-3 py-1.5 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50; }
.btn-danger    { @apply px-3 py-1.5 bg-red-600 text-white rounded-lg text-sm hover:bg-red-700; }
.btn-sm        { @apply text-xs; }
.badge-blue    { @apply inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800; }
</style>
