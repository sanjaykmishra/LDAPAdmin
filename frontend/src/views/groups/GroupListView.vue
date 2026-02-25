<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Groups</h1>
      <button @click="openCreate" class="btn-primary">+ New Group</button>
    </div>

    <!-- Search -->
    <div class="flex gap-3 mb-4">
      <input v-model="filterText" placeholder="Filter (e.g. cn=staff*)" @keyup.enter="load"
        class="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
      <button @click="load" class="btn-primary">Search</button>
    </div>

    <DataTable :columns="cols" :rows="groups" :loading="loading" row-key="dn">
      <template #cell-dn="{ value }"><code class="text-xs">{{ value }}</code></template>
      <template #cell-memberCount="{ value }">
        <span class="badge-blue">{{ value }} members</span>
      </template>
      <template #actions="{ row }">
        <div class="flex gap-2 justify-end">
          <button @click="openMembers(row)" class="btn-sm btn-secondary">Members</button>
          <button @click="confirmDelete(row)" class="btn-sm btn-danger">Delete</button>
        </div>
      </template>
    </DataTable>

    <!-- Create group -->
    <AppModal v-model="showCreate" title="New Group" size="md">
      <FormField label="Parent DN" v-model="createForm.parentDn" required />
      <FormField label="Group Name (cn)" v-model="createForm.cn" required />
      <FormField label="Object Class" v-model="createForm.objectClass" />
      <template #footer>
        <button @click="showCreate = false" class="btn-secondary">Cancel</button>
        <button @click="doCreate" :disabled="saving" class="btn-primary">Create</button>
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
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import { useApi } from '@/composables/useApi'
import * as groupsApi from '@/api/groups'
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
const showMembers   = ref(false)
const showDelete    = ref(false)
const selectedGroup = ref(null)
const deleteTarget  = ref(null)
const members       = ref([])
const newMemberDn   = ref('')
const saving        = ref(false)
const createForm    = ref({ parentDn: '', cn: '', objectClass: 'groupOfNames' })

const cols = [
  { key: 'dn',          label: 'DN' },
  { key: 'cn',          label: 'Name' },
  { key: 'memberCount', label: 'Members' },
]

async function load() {
  await call(async () => {
    const { data } = await groupsApi.searchGroups(dirId, { filter: filterText.value || undefined })
    groups.value = (data.entries || data).map(e => ({
      dn:          e.dn,
      cn:          e.attributes?.cn?.[0] || '—',
      memberCount: (e.attributes?.member || e.attributes?.uniqueMember || []).length,
      _members:    e.attributes?.member || e.attributes?.uniqueMember || [],
    }))
  })
}

async function doCreate() {
  saving.value = true
  try {
    await groupsApi.createGroup(dirId, createForm.value)
    notif.success('Group created')
    showCreate.value = false
    await load()
  } catch (e) { notif.error(e.response?.data?.detail || e.message) }
  finally { saving.value = false }
}

function openCreate() {
  createForm.value = { parentDn: '', cn: '', objectClass: 'groupOfNames' }
  showCreate.value = true
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
    () => groupsApi.addGroupMember(dirId, selectedGroup.value.dn, { memberDn: newMemberDn.value }),
    { successMsg: 'Member added' }
  )
  members.value.push(newMemberDn.value)
  newMemberDn.value = ''
}

async function removeMember(dn) {
  await call(
    () => groupsApi.removeGroupMember(dirId, selectedGroup.value.dn, dn),
    { successMsg: 'Member removed' }
  )
  members.value = members.value.filter(m => m !== dn)
}

function confirmDelete(row) { deleteTarget.value = row; showDelete.value = true }
async function doDelete() {
  await call(() => groupsApi.deleteGroup(dirId, deleteTarget.value.dn), { successMsg: 'Group deleted' })
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
.badge-blue    { @apply inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800; }
</style>
