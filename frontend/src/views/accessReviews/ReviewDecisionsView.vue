<template>
  <div class="p-6">
    <div class="flex items-center gap-3 mb-6">
      <button @click="$router.back()" class="text-gray-400 hover:text-gray-600">
        <svg class="w-5 h-5" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M12.707 5.293a1 1 0 010 1.414L9.414 10l3.293 3.293a1 1 0 01-1.414 1.414l-4-4a1 1 0 010-1.414l4-4a1 1 0 011.414 0z" clip-rule="evenodd"/></svg>
      </button>
      <h1 class="text-2xl font-bold text-gray-900">Review Decisions</h1>
    </div>

    <!-- Progress summary -->
    <div v-if="decisions.length > 0" class="grid grid-cols-4 gap-3 mb-5">
      <div class="bg-white border border-gray-200 rounded-lg p-3 text-center">
        <div class="text-xl font-bold text-gray-900">{{ decisions.length }}</div>
        <div class="text-xs text-gray-500">Total</div>
      </div>
      <div class="bg-white border border-gray-200 rounded-lg p-3 text-center">
        <div class="text-xl font-bold text-yellow-600">{{ pendingDecisions.length }}</div>
        <div class="text-xs text-gray-500">Pending</div>
      </div>
      <div class="bg-white border border-gray-200 rounded-lg p-3 text-center">
        <div class="text-xl font-bold text-green-600">{{ confirmedCount }}</div>
        <div class="text-xs text-gray-500">Confirmed</div>
      </div>
      <div class="bg-white border border-gray-200 rounded-lg p-3 text-center">
        <div class="text-xl font-bold text-red-600">{{ revokedCount }}</div>
        <div class="text-xs text-gray-500">Revoked</div>
      </div>
    </div>

    <!-- Filter tabs + search + bulk actions -->
    <div class="flex flex-wrap items-center gap-3 mb-4">
      <div class="flex gap-1">
        <button v-for="tab in filterTabs" :key="tab.value"
          @click="activeFilter = tab.value; page = 0"
          :class="['px-3 py-1.5 text-xs rounded-lg border transition-colors',
            activeFilter === tab.value ? 'bg-blue-50 border-blue-300 text-blue-700 font-medium' : 'border-gray-200 text-gray-600 hover:bg-gray-50']">
          {{ tab.label }}
          <span class="ml-1 opacity-70">({{ tab.count }})</span>
        </button>
      </div>
      <input v-model="search" type="text" placeholder="Search members..."
             class="border border-gray-300 rounded-lg px-3 py-1.5 text-xs w-56 focus:outline-none focus:ring-2 focus:ring-blue-500" />
      <div class="ml-auto flex gap-2">
        <button v-if="selected.size > 0" @click="bulkConfirmSelected" :disabled="loading"
          class="text-xs px-3 py-1.5 rounded-lg bg-green-600 text-white hover:bg-green-700 disabled:opacity-50">
          Confirm Selected ({{ selected.size }})
        </button>
        <button v-if="selected.size > 0" @click="bulkRevokeSelected" :disabled="loading"
          class="text-xs px-3 py-1.5 rounded-lg bg-red-600 text-white hover:bg-red-700 disabled:opacity-50">
          Revoke Selected ({{ selected.size }})
        </button>
        <button @click="bulkConfirmRemaining" :disabled="loading || pendingDecisions.length === 0"
          class="text-xs px-3 py-1.5 rounded-lg bg-green-600 text-white hover:bg-green-700 disabled:opacity-50">
          Confirm All Remaining ({{ pendingDecisions.length }})
        </button>
      </div>
    </div>

    <!-- Table -->
    <div class="bg-white border border-gray-200 rounded-lg overflow-hidden">
      <div class="overflow-x-auto">
        <table class="min-w-full text-sm">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-3 py-3 w-10">
                <input type="checkbox" :checked="allPageSelected" :indeterminate="somePageSelected && !allPageSelected"
                  @change="toggleSelectAll" class="rounded text-blue-600 focus:ring-blue-500" />
              </th>
              <th v-for="col in cols" :key="col.key"
                  @click="col.sortable !== false && toggleSort(col.key)"
                  :class="['px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider',
                    col.sortable !== false ? 'cursor-pointer hover:text-gray-700 select-none' : '']">
                {{ col.label }}
                <span v-if="sortCol === col.key" class="ml-0.5 text-[10px]">{{ sortAsc ? '▲' : '▼' }}</span>
              </th>
              <th class="px-4 py-3 text-right text-xs font-semibold text-gray-500 uppercase tracking-wider">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr v-if="loading">
              <td :colspan="cols.length + 2" class="px-4 py-8 text-center text-gray-400">Loading...</td>
            </tr>
            <tr v-else-if="sorted.length === 0">
              <td :colspan="cols.length + 2" class="px-4 py-8 text-center text-gray-400">No decisions match the current filter.</td>
            </tr>
            <tr v-for="row in paged" :key="row.id" class="hover:bg-gray-50 transition-colors"
                :class="{ 'bg-blue-50/50': selected.has(row.id) }">
              <td class="px-3 py-3 w-10">
                <input v-if="!row.decision" type="checkbox" :checked="selected.has(row.id)"
                  @change="toggleSelect(row.id)" class="rounded text-blue-600 focus:ring-blue-500" />
              </td>
              <td class="px-4 py-3">
                <div class="font-medium text-gray-900">{{ row.memberDisplay || '—' }}</div>
              </td>
              <td class="px-4 py-3 text-gray-500 max-w-xs truncate" :title="row.memberDn">{{ row.memberDn }}</td>
              <td class="px-4 py-3">
                <span v-if="row.decision" :class="decisionBadge(row.decision)">{{ row.decision }}</span>
                <span v-else class="px-2 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800">PENDING</span>
              </td>
              <td class="px-4 py-3 text-gray-600">{{ row.decidedByUsername || '—' }}</td>
              <td class="px-4 py-3 text-gray-500 whitespace-nowrap">{{ fmtDateTime(row.decidedAt) }}</td>
              <td class="px-4 py-3 text-right">
                <div v-if="!row.decision" class="flex gap-1 justify-end">
                  <button @click="handleDecision(row, 'CONFIRM')" :disabled="loading"
                    class="text-xs px-2.5 py-1 rounded bg-green-600 text-white hover:bg-green-700 disabled:opacity-50">Confirm</button>
                  <button @click="openRevoke(row)" :disabled="loading"
                    class="text-xs px-2.5 py-1 rounded bg-red-600 text-white hover:bg-red-700 disabled:opacity-50">Revoke</button>
                </div>
                <span v-else class="text-xs text-gray-400">Decided</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="px-4 py-3 border-t border-gray-200 flex items-center justify-between">
        <span class="text-xs text-gray-500">Page {{ page + 1 }} of {{ totalPages }} ({{ sorted.length }} results)</span>
        <div class="flex gap-1">
          <button @click="page = Math.max(0, page - 1)" :disabled="page === 0" class="btn-sm">Prev</button>
          <button @click="page = Math.min(totalPages - 1, page + 1)" :disabled="page >= totalPages - 1" class="btn-sm">Next</button>
        </div>
      </div>
    </div>

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
          <button @click="revokeModal = false" class="btn-neutral">Cancel</button>
          <button @click="doRevoke"
            class="px-4 py-2 rounded-lg bg-red-600 text-white hover:bg-red-700">Revoke</button>
        </div>
      </div>
    </AppModal>

    <!-- Bulk revoke modal -->
    <AppModal v-model="bulkRevokeModal" title="Bulk Revoke">
      <div class="space-y-4">
        <p class="text-sm text-gray-600">
          Revoke <strong>{{ selected.size }}</strong> selected member{{ selected.size !== 1 ? 's' : '' }} from this group?
        </p>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Comment (optional)</label>
          <textarea v-model="bulkRevokeComment" rows="2"
            class="w-full border border-gray-300 rounded-lg p-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            placeholder="Reason for revocation..."></textarea>
        </div>
        <div class="flex gap-2 justify-end">
          <button @click="bulkRevokeModal = false" class="btn-neutral">Cancel</button>
          <button @click="doBulkRevoke"
            class="px-4 py-2 rounded-lg bg-red-600 text-white hover:bg-red-700">Revoke {{ selected.size }}</button>
        </div>
      </div>
    </AppModal>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useApi } from '@/composables/useApi'
import { listDecisions, submitDecision, bulkDecide } from '@/api/accessReviews'
import AppModal from '@/components/AppModal.vue'

const route = useRoute()
const { loading, call } = useApi()
const dirId = route.params.dirId
const campaignId = route.params.campaignId
const groupId = route.params.groupId

const decisions = ref([])
const activeFilter = ref('ALL')
const search = ref('')
const sortCol = ref('memberDisplay')
const sortAsc = ref(true)
const page = ref(0)
const PAGE_SIZE = 50
const selected = ref(new Set())

const revokeModal = ref(false)
const revokeTarget = ref(null)
const revokeComment = ref('')
const bulkRevokeModal = ref(false)
const bulkRevokeComment = ref('')

const cols = [
  { key: 'memberDisplay', label: 'Member' },
  { key: 'memberDn', label: 'DN' },
  { key: 'decision', label: 'Decision' },
  { key: 'decidedByUsername', label: 'Decided By' },
  { key: 'decidedAt', label: 'Decided At' },
]

const pendingDecisions = computed(() => decisions.value.filter(d => !d.decision))
const confirmedCount = computed(() => decisions.value.filter(d => d.decision === 'CONFIRM').length)
const revokedCount = computed(() => decisions.value.filter(d => d.decision === 'REVOKE').length)

const filterTabs = computed(() => [
  { label: 'All', value: 'ALL', count: decisions.value.length },
  { label: 'Pending', value: 'PENDING', count: pendingDecisions.value.length },
  { label: 'Confirmed', value: 'CONFIRM', count: confirmedCount.value },
  { label: 'Revoked', value: 'REVOKE', count: revokedCount.value },
])

const filtered = computed(() => {
  let result = decisions.value
  if (activeFilter.value === 'PENDING') result = result.filter(d => !d.decision)
  else if (activeFilter.value !== 'ALL') result = result.filter(d => d.decision === activeFilter.value)
  const q = search.value.toLowerCase()
  if (q) {
    result = result.filter(d =>
      (d.memberDisplay || '').toLowerCase().includes(q) ||
      (d.memberDn || '').toLowerCase().includes(q) ||
      (d.decidedByUsername || '').toLowerCase().includes(q)
    )
  }
  return result
})

const sorted = computed(() => {
  if (!sortCol.value) return filtered.value
  const col = sortCol.value
  const dir = sortAsc.value ? 1 : -1
  return [...filtered.value].sort((a, b) => {
    const va = (a[col] || '').toString().toLowerCase()
    const vb = (b[col] || '').toString().toLowerCase()
    // Sort pending (null decision) before decided when sorting by decision
    if (col === 'decision') {
      if (!a.decision && b.decision) return -1 * dir
      if (a.decision && !b.decision) return 1 * dir
    }
    return va < vb ? -dir : va > vb ? dir : 0
  })
})

const totalPages = computed(() => Math.ceil(sorted.value.length / PAGE_SIZE))
const paged = computed(() => sorted.value.slice(page.value * PAGE_SIZE, (page.value + 1) * PAGE_SIZE))

const allPageSelected = computed(() => {
  const pending = paged.value.filter(r => !r.decision)
  return pending.length > 0 && pending.every(r => selected.value.has(r.id))
})
const somePageSelected = computed(() => paged.value.some(r => !r.decision && selected.value.has(r.id)))

watch([search, activeFilter], () => { page.value = 0 })

function toggleSort(col) {
  if (sortCol.value === col) sortAsc.value = !sortAsc.value
  else { sortCol.value = col; sortAsc.value = true }
  page.value = 0
}

function toggleSelect(id) {
  const s = new Set(selected.value)
  if (s.has(id)) s.delete(id); else s.add(id)
  selected.value = s
}

function toggleSelectAll() {
  const s = new Set(selected.value)
  const pending = paged.value.filter(r => !r.decision)
  if (allPageSelected.value) {
    pending.forEach(r => s.delete(r.id))
  } else {
    pending.forEach(r => s.add(r.id))
  }
  selected.value = s
}

function decisionBadge(decision) {
  const base = 'px-2 py-0.5 rounded-full text-xs font-medium'
  return decision === 'CONFIRM'
    ? base + ' bg-green-100 text-green-800'
    : base + ' bg-red-100 text-red-800'
}

function fmtDateTime(val) {
  if (!val) return '—'
  return new Date(val).toLocaleString()
}

async function handleDecision(row, decision) {
  try {
    await call(() => submitDecision(dirId, campaignId, groupId, row.id, { decision, comment: null }),
      { successMsg: `Member ${decision.toLowerCase()}ed` })
    selected.value = new Set([...selected.value].filter(id => id !== row.id))
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
    selected.value = new Set([...selected.value].filter(id => id !== revokeTarget.value.id))
    await loadDecisions()
  } catch { /* handled */ }
}

async function bulkConfirmRemaining() {
  const items = pendingDecisions.value.map(d => ({
    decisionId: d.id, decision: 'CONFIRM', comment: null,
  }))
  try {
    await call(() => bulkDecide(dirId, campaignId, groupId, items),
      { successMsg: `${items.length} members confirmed` })
    selected.value = new Set()
    await loadDecisions()
  } catch { /* handled */ }
}

async function bulkConfirmSelected() {
  const items = [...selected.value].map(id => ({
    decisionId: id, decision: 'CONFIRM', comment: null,
  }))
  try {
    await call(() => bulkDecide(dirId, campaignId, groupId, items),
      { successMsg: `${items.length} members confirmed` })
    selected.value = new Set()
    await loadDecisions()
  } catch { /* handled */ }
}

function bulkRevokeSelected() {
  bulkRevokeComment.value = ''
  bulkRevokeModal.value = true
}

async function doBulkRevoke() {
  bulkRevokeModal.value = false
  const items = [...selected.value].map(id => ({
    decisionId: id, decision: 'REVOKE', comment: bulkRevokeComment.value || null,
  }))
  try {
    await call(() => bulkDecide(dirId, campaignId, groupId, items),
      { successMsg: `${items.length} members revoked` })
    selected.value = new Set()
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

<style scoped>
@reference "tailwindcss";
</style>
