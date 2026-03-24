<template>
  <div class="p-6">
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Pending Approvals</h1>

    <DataTable :columns="cols" :rows="approvals" :loading="loading" row-key="id"
      empty-text="No pending approvals" empty-icon="shield">
      <template #cell-status="{ value }">
        <span :class="statusClass(value)">{{ value }}</span>
      </template>
      <template #cell-requestType="{ value }">
        <span class="badge-gray">{{ formatType(value) }}</span>
      </template>
      <template #cell-createdAt="{ value }"><RelativeTime :value="value" /></template>
      <template #cell-actions="{ row }">
        <div class="flex gap-2" v-if="row.status === 'PENDING'">
          <button @click="openDetail(row)" class="btn-secondary text-xs">View</button>
          <button v-if="!isOwnRequest(row)" @click="handleApprove(row)" class="text-xs px-3 py-1 rounded-lg text-green-600 bg-green-50 hover:bg-green-100 font-medium">Approve</button>
          <button v-if="!isOwnRequest(row)" @click="openReject(row)" class="text-xs px-3 py-1 rounded-lg text-red-600 bg-red-50 hover:bg-red-100 font-medium">Reject</button>
          <span v-if="isOwnRequest(row)" class="text-xs text-gray-400 italic self-center">Own request</span>
        </div>
        <div v-else>
          <button @click="openDetail(row)" class="btn-secondary text-xs">View</button>
        </div>
      </template>
    </DataTable>

    <!-- Detail Modal -->
    <AppModal v-model="detailModal" title="Approval Details" size="lg">
      <div v-if="selectedApproval" class="space-y-3">
        <div><strong>Request Type:</strong> {{ formatType(selectedApproval.requestType) }}</div>
        <div><strong>Requester:</strong> {{ selectedApproval.requesterUsername }}</div>
        <div><strong>Status:</strong> <span :class="statusClass(selectedApproval.status)">{{ selectedApproval.status }}</span></div>
        <div><strong>Submitted:</strong> <RelativeTime :value="selectedApproval.createdAt" /></div>
        <div v-if="selectedApproval.reviewerUsername">
          <strong>Reviewed by:</strong> {{ selectedApproval.reviewerUsername }}
        </div>
        <div v-if="selectedApproval.reviewedAt">
          <strong>Reviewed at:</strong> <RelativeTime :value="selectedApproval.reviewedAt" />
        </div>
        <div v-if="selectedApproval.rejectReason">
          <strong>Reject Reason:</strong> {{ selectedApproval.rejectReason }}
        </div>

        <!-- Provisioning Error -->
        <div v-if="selectedApproval.provisionError"
          class="bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-sm text-red-700">
          <strong>Provisioning Error:</strong> {{ selectedApproval.provisionError }}
          <p class="text-xs text-red-500 mt-1">Edit the request attributes below to fix the issue, then approve again.</p>
        </div>

        <!-- Edit mode for attributes -->
        <div v-if="editMode && isEditablePayload(selectedApproval)" class="space-y-3 border rounded-lg p-3 bg-gray-50">
          <div class="text-sm font-semibold text-gray-700">Edit Request Attributes</div>
          <div>
            <label class="block text-xs text-gray-500 mb-1">DN</label>
            <input v-model="editPayload.dn" class="input w-full text-sm" />
          </div>
          <div v-for="(values, attrName) in editPayload.attributes" :key="attrName">
            <label class="block text-xs text-gray-500 mb-1">{{ attrName }}</label>
            <input v-model="editPayload.attributes[attrName][0]" class="input w-full text-sm" />
          </div>
          <div class="flex gap-2">
            <button @click="savePayload" :disabled="savingPayload"
              class="btn-primary text-sm">{{ savingPayload ? 'Saving...' : 'Save Changes' }}</button>
            <button @click="editMode = false" class="btn-secondary text-sm">Cancel</button>
          </div>
        </div>

        <!-- Read-only payload -->
        <details v-if="!editMode" class="mt-4">
          <summary class="cursor-pointer text-sm text-blue-600 hover:text-blue-800">Show Payload</summary>
          <pre class="mt-2 bg-gray-50 border rounded p-3 text-xs overflow-auto max-h-64">{{ formatPayload(selectedApproval.payload) }}</pre>
        </details>

        <div v-if="selectedApproval.status === 'PENDING'" class="flex gap-2 mt-4 pt-4 border-t">
          <template v-if="!isOwnRequest(selectedApproval)">
            <button v-if="!editMode && isEditablePayload(selectedApproval)"
              @click="startEdit(selectedApproval)" class="btn-secondary">Edit</button>
            <button v-if="!editMode" @click="handleApprove(selectedApproval); detailModal = false" class="px-4 py-2 rounded-lg text-green-600 bg-green-50 hover:bg-green-100 font-medium">Approve</button>
            <button v-if="!editMode" @click="detailModal = false; openReject(selectedApproval)" class="px-4 py-2 rounded-lg text-red-600 bg-red-50 hover:bg-red-100 font-medium">Reject</button>
          </template>
          <span v-else class="text-sm text-gray-400 italic">You cannot approve or reject your own request</span>
        </div>
      </div>
    </AppModal>

    <!-- Reject Modal -->
    <AppModal v-model="rejectModal" title="Reject Request">
      <div class="space-y-4">
        <p class="text-sm text-gray-600">Please provide a reason for rejecting this request.</p>
        <textarea v-model="rejectReason" rows="3"
          class="w-full border border-gray-300 rounded-lg p-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
          placeholder="Enter rejection reason..."></textarea>
        <div class="flex gap-2 justify-end">
          <button @click="rejectModal = false" class="btn-secondary">Cancel</button>
          <button @click="handleReject" :disabled="!rejectReason.trim()"
            class="px-4 py-2 rounded-lg text-red-600 bg-red-50 hover:bg-red-100 font-medium disabled:opacity-50">
            Reject
          </button>
        </div>
      </div>
    </AppModal>

    <!-- Confirm Approve -->
    <ConfirmDialog
      v-model="confirmApprove"
      title="Approve Request"
      message="Are you sure you want to approve this request? The LDAP entry will be created immediately."
      confirm-label="Approve"
      confirm-class="bg-green-600 hover:bg-green-700"
      @confirm="doApprove" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notifications'
import { useApi } from '@/composables/useApi'
import { listPendingApprovals, approveRequest, rejectRequest, updateApprovalPayload } from '@/api/approvals'
import DataTable from '@/components/DataTable.vue'
import AppModal from '@/components/AppModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import RelativeTime from '@/components/RelativeTime.vue'

const route = useRoute()
const auth = useAuthStore()
const { loading, call } = useApi()
const notif = useNotificationStore()
const dirId = route.params.dirId

const approvals = ref([])
const selectedApproval = ref(null)
const detailModal = ref(false)
const rejectModal = ref(false)
const rejectReason = ref('')
const confirmApprove = ref(false)
const approvalToAction = ref(null)
const editMode = ref(false)
const editPayload = reactive({ dn: '', attributes: {} })
const savingPayload = ref(false)

const cols = [
  { key: 'requestType', label: 'Type' },
  { key: 'requesterUsername', label: 'Requester' },
  { key: 'status', label: 'Status' },
  { key: 'createdAt', label: 'Submitted' },
  { key: 'actions', label: '' }
]

function fmtDate(val) {
  if (!val) return ''
  return new Date(val).toLocaleString()
}

function formatType(type) {
  const labels = {
    USER_CREATE: 'User Create',
    BULK_IMPORT: 'Bulk Import',
    USER_MOVE: 'User Move',
    GROUP_MEMBER_ADD: 'Group Member Add',
    SELF_REGISTRATION: 'Self-Registration',
  }
  return labels[type] || type
}

function statusClass(status) {
  const base = 'px-2 py-0.5 rounded-full text-xs font-medium'
  switch (status) {
    case 'PENDING': return base + ' bg-yellow-100 text-yellow-800'
    case 'APPROVED': return base + ' bg-green-100 text-green-800'
    case 'REJECTED': return base + ' bg-red-100 text-red-800'
    default: return base + ' bg-gray-100 text-gray-800'
  }
}

function formatPayload(payload) {
  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch {
    return payload
  }
}

function isOwnRequest(approval) {
  return auth.principal?.id === approval.requestedBy
}

function openDetail(approval) {
  selectedApproval.value = approval
  editMode.value = false
  detailModal.value = true
}

function isEditablePayload(approval) {
  return ['USER_CREATE', 'SELF_REGISTRATION'].includes(approval.requestType)
}

function startEdit(approval) {
  try {
    const parsed = JSON.parse(approval.payload)
    editPayload.dn = parsed.dn || ''
    // Deep copy attributes so edits don't mutate the original
    const attrs = {}
    for (const [key, val] of Object.entries(parsed.attributes || {})) {
      attrs[key] = Array.isArray(val) ? [...val] : [val]
    }
    editPayload.attributes = attrs
    editMode.value = true
  } catch {
    editPayload.dn = ''
    editPayload.attributes = {}
  }
}

async function savePayload() {
  savingPayload.value = true
  try {
    // Remove attributes with no value
    const cleanAttrs = {}
    for (const [key, vals] of Object.entries(editPayload.attributes)) {
      const filtered = vals.filter(v => v != null && v !== '')
      if (filtered.length > 0) cleanAttrs[key] = filtered
    }
    const newPayload = JSON.stringify({
      dn: editPayload.dn,
      attributes: cleanAttrs
    })
    const { data } = await updateApprovalPayload(dirId, selectedApproval.value.id, newPayload)
    // Update the local approval data
    selectedApproval.value = data
    const idx = approvals.value.findIndex(a => a.id === data.id)
    if (idx >= 0) approvals.value[idx] = data
    editMode.value = false
  } catch (e) {
    alert(e.response?.data?.detail || 'Failed to save changes')
  } finally {
    savingPayload.value = false
  }
}

function openReject(approval) {
  approvalToAction.value = approval
  rejectReason.value = ''
  rejectModal.value = true
}

function handleApprove(approval) {
  approvalToAction.value = approval
  confirmApprove.value = true
}

async function doApprove() {
  confirmApprove.value = false
  const res = await call(() => approveRequest(dirId, approvalToAction.value.id))
  if (res?.data?.provisionError) {
    // Provisioning failed — reload list and open detail to show the error
    await loadApprovals()
    const updated = approvals.value.find(a => a.id === res.data.id)
    if (updated) openDetail(updated)
    notif.error('Provisioning failed: ' + res.data.provisionError)
  } else {
    notif.success('Request approved')
    await loadApprovals()
  }
}

async function handleReject() {
  rejectModal.value = false
  await call(() => rejectRequest(dirId, approvalToAction.value.id, rejectReason.value), { successMsg: 'Request rejected' })
  await loadApprovals()
}

async function loadApprovals() {
  const res = await call(() => listPendingApprovals(dirId))
  approvals.value = res.data
}

onMounted(loadApprovals)
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
.btn-secondary { @apply px-3 py-1.5 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50; }
.input { @apply border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
</style>
