<template>
  <div class="p-6">
    <div class="flex items-center gap-3 mb-6">
      <button @click="$router.back()" class="text-gray-400 hover:text-gray-600">
        <svg class="w-5 h-5" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M12.707 5.293a1 1 0 010 1.414L9.414 10l3.293 3.293a1 1 0 01-1.414 1.414l-4-4a1 1 0 010-1.414l4-4a1 1 0 011.414 0z" clip-rule="evenodd"/></svg>
      </button>
      <h1 class="text-2xl font-bold text-gray-900">Review Decisions</h1>
    </div>

    <!-- Filter tabs -->
    <div class="flex gap-2 mb-4">
      <button v-for="tab in filterTabs" :key="tab.value"
        @click="activeFilter = tab.value"
        :class="['px-3 py-1.5 text-sm rounded-lg border transition-colors',
          activeFilter === tab.value ? 'bg-blue-50 border-blue-300 text-blue-700' : 'border-gray-200 text-gray-600 hover:bg-gray-50']">
        {{ tab.label }}
        <span v-if="tab.count !== undefined" class="ml-1 text-xs opacity-70">({{ tab.count }})</span>
      </button>
    </div>

    <!-- Bulk action toolbar -->
    <div class="flex gap-2 mb-4">
      <button @click="bulkConfirmRemaining" :disabled="loading || pendingDecisions.length === 0"
        class="text-xs px-3 py-1.5 rounded-lg bg-green-600 text-white hover:bg-green-700 disabled:opacity-50">
        Confirm All Remaining ({{ pendingDecisions.length }})
      </button>
    </div>

    <DataTable :columns="cols" :rows="filteredDecisions" :loading="loading" row-key="id">
      <template #cell-decision="{ row }">
        <span v-if="row.decision" :class="decisionClass(row.decision)">{{ row.decision }}</span>
        <span v-else class="px-2 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800">PENDING</span>
      </template>
      <template #cell-decidedAt="{ value }">{{ fmtDateTime(value) }}</template>
      <template #cell-actions="{ row }">
        <div v-if="!row.decision" class="flex gap-1">
          <button @click="handleDecision(row, 'CONFIRM')" :disabled="loading"
            class="text-xs px-2 py-1 rounded bg-green-600 text-white hover:bg-green-700">Confirm</button>
          <button @click="openRevoke(row)" :disabled="loading"
            class="text-xs px-2 py-1 rounded bg-red-600 text-white hover:bg-red-700">Revoke</button>
        </div>
        <span v-else class="text-xs text-gray-400">Decided</span>
      </template>
    </DataTable>

    <!-- Revoke modal with comment -->
    <AppModal v-model="revokeModal" title="Revoke Membership">
      <div class="space-y-4">
        <p class="text-sm text-gray-600">
          Revoke <strong>{{ revokeTarget?.memberDisplay || revokeTarget?.memberDn }}</strong> from this group?
        </p>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Comment (optional)</label>
          <textarea v-model="revokeComment" rows="2"
            class="w-full border border-gray-300 rounded-lg p-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            placeholder="Reason for revocation..."></textarea>
        </div>
        <div class="flex gap-2 justify-end">
          <button @click="revokeModal = false" class="btn-secondary">Cancel</button>
          <button @click="doRevoke"
            class="px-4 py-2 rounded-lg bg-red-600 text-white hover:bg-red-700">Revoke</button>
        </div>
      </div>
    </AppModal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useApi } from '@/composables/useApi'
import { listDecisions, submitDecision, bulkDecide } from '@/api/accessReviews'
import DataTable from '@/components/DataTable.vue'
import AppModal from '@/components/AppModal.vue'

const route = useRoute()
const { loading, call } = useApi()
const dirId = route.params.dirId
const campaignId = route.params.campaignId
const groupId = route.params.groupId

const decisions = ref([])
const activeFilter = ref('ALL')
const revokeModal = ref(false)
const revokeTarget = ref(null)
const revokeComment = ref('')

const cols = [
  { key: 'memberDisplay', label: 'Member' },
  { key: 'memberDn', label: 'DN' },
  { key: 'decision', label: 'Decision' },
  { key: 'decidedByUsername', label: 'Decided By' },
  { key: 'decidedAt', label: 'Decided At' },
  { key: 'actions', label: '' },
]

const pendingDecisions = computed(() => decisions.value.filter(d => !d.decision))

const filterTabs = computed(() => [
  { label: 'All', value: 'ALL', count: decisions.value.length },
  { label: 'Pending', value: 'PENDING', count: pendingDecisions.value.length },
  { label: 'Confirmed', value: 'CONFIRM', count: decisions.value.filter(d => d.decision === 'CONFIRM').length },
  { label: 'Revoked', value: 'REVOKE', count: decisions.value.filter(d => d.decision === 'REVOKE').length },
])

const filteredDecisions = computed(() => {
  if (activeFilter.value === 'ALL') return decisions.value
  if (activeFilter.value === 'PENDING') return decisions.value.filter(d => !d.decision)
  return decisions.value.filter(d => d.decision === activeFilter.value)
})

function decisionClass(decision) {
  const base = 'px-2 py-0.5 rounded-full text-xs font-medium'
  return decision === 'CONFIRM'
    ? base + ' bg-green-100 text-green-800'
    : base + ' bg-red-100 text-red-800'
}

function fmtDateTime(val) {
  if (!val) return ''
  return new Date(val).toLocaleString()
}

async function handleDecision(row, decision) {
  try {
    await call(() => submitDecision(dirId, campaignId, groupId, row.id, { decision, comment: null }),
      { successMsg: `Member ${decision.toLowerCase()}ed` })
    await loadDecisions()
  } catch { /* handled */ }
}

function openRevoke(row) {
  revokeTarget.value = row
  revokeComment.value = ''
  revokeModal.value = true
}

async function doRevoke() {
  revokeModal.value = false
  try {
    await call(() => submitDecision(dirId, campaignId, groupId, revokeTarget.value.id,
      { decision: 'REVOKE', comment: revokeComment.value || null }),
      { successMsg: 'Member revoked' })
    await loadDecisions()
  } catch { /* handled */ }
}

async function bulkConfirmRemaining() {
  const items = pendingDecisions.value.map(d => ({
    decisionId: d.id,
    decision: 'CONFIRM',
    comment: null,
  }))
  try {
    await call(() => bulkDecide(dirId, campaignId, groupId, items),
      { successMsg: `${items.length} members confirmed` })
    await loadDecisions()
  } catch { /* handled */ }
}

async function loadDecisions() {
  try {
    const res = await call(() => listDecisions(dirId, campaignId, groupId))
    decisions.value = res.data
  } catch { /* handled */ }
}

onMounted(loadDecisions)
</script>
