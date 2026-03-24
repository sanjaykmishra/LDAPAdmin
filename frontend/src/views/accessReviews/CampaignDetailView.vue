<template>
  <div class="p-6">
    <div v-if="loading && !campaign" class="text-gray-500">Loading...</div>

    <template v-if="campaign">
      <!-- Header -->
      <div class="flex items-start justify-between mb-6">
        <div>
          <div class="flex items-center gap-3 mb-1">
            <h1 class="text-2xl font-bold text-gray-900">{{ campaign.name }}</h1>
            <span :class="statusClass(campaign.status)">{{ campaign.status }}</span>
          </div>
          <p v-if="campaign.description" class="text-sm text-gray-500 mt-1">{{ campaign.description }}</p>
          <div class="flex gap-4 text-sm text-gray-500 mt-2">
            <span>Deadline: <strong>{{ fmtDate(campaign.deadline) }}</strong>
              <span v-if="campaign.deadlineDays" class="text-gray-400">({{ campaign.deadlineDays }} days)</span>
            </span>
            <span v-if="campaign.recurrenceMonths" class="text-blue-600">Repeats every {{ campaign.recurrenceMonths }} month{{ campaign.recurrenceMonths > 1 ? 's' : '' }}</span>
            <span>Created by: {{ campaign.createdByUsername }}</span>
            <span v-if="campaign.autoRevoke" class="text-orange-600">Auto-revoke enabled</span>
          </div>
        </div>
        <div class="flex gap-2">
          <button v-if="campaign.status === 'UPCOMING'" @click="handleActivate" :disabled="loading" class="btn-primary text-sm">Activate</button>
          <button v-if="campaign.status === 'ACTIVE'" @click="handleClose(false)" :disabled="loading" class="btn-primary text-sm">Close</button>
          <button v-if="campaign.status === 'ACTIVE'" @click="handleClose(true)" :disabled="loading" class="text-sm px-3 py-1.5 rounded-lg bg-orange-600 text-white hover:bg-orange-700">Force Close</button>
          <button v-if="campaign.status === 'UPCOMING' || campaign.status === 'ACTIVE'" @click="handleCancel" :disabled="loading" class="text-sm px-3 py-1.5 rounded-lg bg-red-600 text-white hover:bg-red-700">Cancel</button>
          <button @click="handleSaveAsTemplate" :disabled="loading" class="btn-secondary text-sm">Save as Template</button>
          <button @click="handleExport" class="btn-secondary text-sm">Export CSV</button>
        </div>
      </div>

      <!-- Progress -->
      <div v-if="campaign.progress" class="bg-white rounded-lg border p-5 mb-6">
        <h2 class="text-sm font-semibold text-gray-700 mb-3">Review Progress</h2>
        <div class="flex items-center gap-6">
          <div class="flex-1">
            <div class="w-full bg-gray-200 rounded-full h-3">
              <div class="h-3 rounded-full bg-blue-600 transition-all" :style="{ width: campaign.progress.percentComplete + '%' }"></div>
            </div>
          </div>
          <span class="text-sm font-medium text-gray-700">{{ Math.round(campaign.progress.percentComplete) }}%</span>
        </div>
        <div class="grid grid-cols-4 gap-4 mt-3">
          <div class="text-center">
            <div class="text-2xl font-bold text-gray-900">{{ campaign.progress.total }}</div>
            <div class="text-xs text-gray-500">Total</div>
          </div>
          <div class="text-center">
            <div class="text-2xl font-bold text-green-600">{{ campaign.progress.confirmed }}</div>
            <div class="text-xs text-gray-500">Confirmed</div>
          </div>
          <div class="text-center">
            <div class="text-2xl font-bold text-red-600">{{ campaign.progress.revoked }}</div>
            <div class="text-xs text-gray-500">Revoked</div>
          </div>
          <div class="text-center">
            <div class="text-2xl font-bold text-yellow-600">{{ campaign.progress.pending }}</div>
            <div class="text-xs text-gray-500">Pending</div>
          </div>
        </div>
      </div>

      <!-- Review groups table -->
      <div class="bg-white rounded-lg border p-5 mb-6">
        <h2 class="text-sm font-semibold text-gray-700 mb-3">Review Groups</h2>
        <DataTable :columns="groupCols" :rows="campaign.reviewGroups || []" row-key="id">
          <template #cell-progress="{ row }">
            <div class="flex items-center gap-2">
              <div class="w-20 bg-gray-200 rounded-full h-2">
                <div class="bg-blue-600 h-2 rounded-full"
                  :style="{ width: (row.total > 0 ? ((row.confirmed + row.revoked) / row.total * 100) : 0) + '%' }"></div>
              </div>
              <span class="text-xs text-gray-500">{{ row.confirmed + row.revoked }}/{{ row.total }}</span>
            </div>
          </template>
          <template #cell-actions="{ row }">
            <button @click="$router.push({ name: 'accessReviewDecisions', params: { dirId, campaignId, groupId: row.id } })"
              class="btn-secondary text-xs">Review</button>
          </template>
        </DataTable>
      </div>

      <!-- Status history -->
      <details class="bg-white rounded-lg border p-5">
        <summary class="text-sm font-semibold text-gray-700 cursor-pointer">Status History</summary>
        <div class="mt-3 space-y-2">
          <div v-for="h in campaign.history" :key="h.id" class="flex items-start gap-3 text-sm">
            <div class="w-2 h-2 rounded-full bg-blue-500 mt-1.5 shrink-0"></div>
            <div>
              <span class="font-medium">{{ h.newStatus }}</span>
              <span v-if="h.oldStatus" class="text-gray-400"> (from {{ h.oldStatus }})</span>
              <span class="text-gray-500"> — {{ h.changedByUsername }}</span>
              <span class="text-gray-400 ml-2"><RelativeTime :value="h.changedAt" /></span>
              <p v-if="h.note" class="text-gray-500 text-xs mt-0.5">{{ h.note }}</p>
            </div>
          </div>
        </div>
      </details>
    </template>

    <!-- Confirm dialogs -->
    <ConfirmDialog v-model="confirmActivate" title="Activate Campaign"
      message="Activating will snapshot current LDAP group members and notify assigned reviewers. Continue?"
      confirm-label="Activate" @confirm="doActivate" />

    <ConfirmDialog v-model="confirmCancel" title="Cancel Campaign"
      message="Are you sure you want to cancel this campaign? This cannot be undone."
      confirm-label="Cancel Campaign" @confirm="doCancel" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useApi, downloadBlob } from '@/composables/useApi'
import { getCampaign, activateCampaign, closeCampaign, cancelCampaign, exportCampaign } from '@/api/accessReviews'
import { saveAsTemplate } from '@/api/campaignTemplates'
import DataTable from '@/components/DataTable.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import RelativeTime from '@/components/RelativeTime.vue'

const route = useRoute()
const router = useRouter()
const { loading, call } = useApi()
const dirId = route.params.dirId
const campaignId = route.params.campaignId

const campaign = ref(null)
const confirmActivate = ref(false)
const confirmCancel = ref(false)

const groupCols = [
  { key: 'groupName', label: 'Group' },
  { key: 'groupDn', label: 'DN' },
  { key: 'reviewerUsername', label: 'Reviewer' },
  { key: 'progress', label: 'Progress' },
  { key: 'actions', label: '' },
]

function statusClass(status) {
  const base = 'px-2 py-0.5 rounded-full text-xs font-medium'
  switch (status) {
    case 'UPCOMING': return base + ' bg-gray-100 text-gray-800'
    case 'ACTIVE': return base + ' bg-blue-100 text-blue-800'
    case 'CLOSED': return base + ' bg-green-100 text-green-800'
    case 'CANCELLED': return base + ' bg-red-100 text-red-800'
    case 'EXPIRED': return base + ' bg-yellow-100 text-yellow-800'
    default: return base + ' bg-gray-100 text-gray-800'
  }
}

function fmtDate(val) {
  if (!val) return ''
  return new Date(val).toLocaleDateString()
}

function fmtDateTime(val) {
  if (!val) return ''
  return new Date(val).toLocaleString()
}

function handleActivate() { confirmActivate.value = true }
function handleCancel() { confirmCancel.value = true }

async function doActivate() {
  confirmActivate.value = false
  try {
    await call(() => activateCampaign(dirId, campaignId), { successMsg: 'Campaign activated' })
    await loadCampaign()
  } catch { /* handled */ }
}

async function handleClose(force) {
  try {
    await call(() => closeCampaign(dirId, campaignId, force), { successMsg: 'Campaign closed' })
    await loadCampaign()
  } catch { /* handled */ }
}

async function doCancel() {
  confirmCancel.value = false
  try {
    await call(() => cancelCampaign(dirId, campaignId), { successMsg: 'Campaign cancelled' })
    await loadCampaign()
  } catch { /* handled */ }
}

async function handleSaveAsTemplate() {
  try {
    await call(() => saveAsTemplate(dirId, campaignId), { successMsg: 'Saved as template' })
  } catch { /* handled */ }
}

async function handleExport() {
  try {
    const res = await call(() => exportCampaign(dirId, campaignId, 'csv'))
    downloadBlob(res.data, `access-review-${campaignId}.csv`)
  } catch { /* handled */ }
}

async function loadCampaign() {
  try {
    const res = await call(() => getCampaign(dirId, campaignId))
    campaign.value = res.data
  } catch { /* handled */ }
}

onMounted(loadCampaign)
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary   { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
.btn-secondary { @apply px-3 py-1.5 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50; }
</style>
