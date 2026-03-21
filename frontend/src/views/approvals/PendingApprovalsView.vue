<template>
  <div class="p-6">
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Pending Approvals</h1>

    <DataTable :columns="cols" :rows="approvals" :loading="loading" row-key="id">
      <template #cell-status="{ value }">
        <span :class="statusClass(value)">{{ value }}</span>
      </template>
      <template #cell-requestType="{ value }">
        <span class="badge-gray">{{ formatType(value) }}</span>
      </template>
      <template #cell-createdAt="{ value }">{{ fmtDate(value) }}</template>
      <template #cell-actions="{ row }">
        <div class="flex gap-2" v-if="row.status === 'PENDING'">
          <button @click="openDetail(row)" class="btn-secondary text-xs">View</button>
          <button @click="handleApprove(row)" class="btn-primary text-xs">Approve</button>
          <button @click="openReject(row)" class="text-xs px-3 py-1 rounded-lg bg-red-600 text-white hover:bg-red-700">Reject</button>
        </div>
        <div v-else>
          <button @click="openDetail(row)" class="btn-secondary text-xs">View</button>
        </div>
      </template>
    </DataTable>

    <p v-if="!loading && approvals.length === 0" class="text-gray-500 text-sm mt-4">
      No pending approvals.
    </p>

    <!-- Detail Modal -->
    <AppModal v-model="detailModal" title="Approval Details">
      <div v-if="selectedApproval" class="space-y-3">
        <div><strong>Request Type:</strong> {{ formatType(selectedApproval.requestType) }}</div>
        <div><strong>Requester:</strong> {{ selectedApproval.requesterUsername }}</div>
        <div><strong>Status:</strong> <span :class="statusClass(selectedApproval.status)">{{ selectedApproval.status }}</span></div>
        <div><strong>Submitted:</strong> {{ fmtDate(selectedApproval.createdAt) }}</div>
        <div v-if="selectedApproval.reviewerUsername">
          <strong>Reviewed by:</strong> {{ selectedApproval.reviewerUsername }}
        </div>
        <div v-if="selectedApproval.reviewedAt">
          <strong>Reviewed at:</strong> {{ fmtDate(selectedApproval.reviewedAt) }}
        </div>
        <div v-if="selectedApproval.rejectReason">
          <strong>Reject Reason:</strong> {{ selectedApproval.rejectReason }}
        </div>

        <details class="mt-4">
          <summary class="cursor-pointer text-sm text-blue-600 hover:text-blue-800">Show Payload</summary>
          <pre class="mt-2 bg-gray-50 border rounded p-3 text-xs overflow-auto max-h-64">{{ formatPayload(selectedApproval.payload) }}</pre>
        </details>

        <div v-if="selectedApproval.status === 'PENDING'" class="flex gap-2 mt-4 pt-4 border-t">
          <button @click="handleApprove(selectedApproval); detailModal = false" class="btn-primary">Approve</button>
          <button @click="detailModal = false; openReject(selectedApproval)" class="px-4 py-2 rounded-lg bg-red-600 text-white hover:bg-red-700">Reject</button>
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
            class="px-4 py-2 rounded-lg bg-red-600 text-white hover:bg-red-700 disabled:opacity-50">
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
      @confirm="doApprove" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useApi } from '@/composables/useApi'
import { listPendingApprovals, approveRequest, rejectRequest } from '@/api/approvals'
import DataTable from '@/components/DataTable.vue'
import AppModal from '@/components/AppModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const route = useRoute()
const { loading, call } = useApi()
const dirId = route.params.dirId

const approvals = ref([])
const selectedApproval = ref(null)
const detailModal = ref(false)
const rejectModal = ref(false)
const rejectReason = ref('')
const confirmApprove = ref(false)
const approvalToAction = ref(null)

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
  return type === 'USER_CREATE' ? 'User Create' : type === 'BULK_IMPORT' ? 'Bulk Import' : type
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

function openDetail(approval) {
  selectedApproval.value = approval
  detailModal.value = true
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
  await call(() => approveRequest(dirId, approvalToAction.value.id), { successMsg: 'Request approved' })
  await loadApprovals()
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
