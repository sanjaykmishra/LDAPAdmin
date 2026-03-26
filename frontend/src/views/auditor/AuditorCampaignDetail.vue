<template>
  <div>
    <!-- Back link -->
    <RouterLink :to="`/auditor/${token}/campaigns`" class="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700 mb-4">
      <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
        <path stroke-linecap="round" stroke-linejoin="round" d="M10.5 19.5L3 12m0 0l7.5-7.5M3 12h18" />
      </svg>
      All Campaigns
    </RouterLink>

    <div v-if="loading" class="text-sm text-slate-500">Loading campaign...</div>
    <div v-else-if="!campaign" class="text-sm text-slate-500">Campaign not found.</div>

    <template v-else>
      <!-- Header -->
      <div class="flex items-center justify-between mb-6">
        <div>
          <h1 class="text-xl font-bold text-slate-900">{{ campaign.name }}</h1>
          <span class="text-xs font-medium px-2 py-0.5 rounded-full" :class="statusClass(campaign.status)">{{ campaign.status }}</span>
        </div>
      </div>

      <!-- Decisions table (desktop) -->
      <section class="hidden sm:block bg-white border border-slate-200 rounded-xl overflow-hidden mb-6">
        <div class="px-4 py-3 border-b border-slate-200 flex items-center justify-between">
          <h2 class="text-sm font-semibold text-slate-700">Decisions ({{ filteredDecisions.length }})</h2>
          <div class="flex items-center gap-2">
            <input v-model="search" type="text" placeholder="Search members..."
                   class="border border-slate-200 rounded-lg px-3 py-1.5 text-xs w-48 focus:outline-none focus:ring-2 focus:ring-slate-300" />
            <ExportDropdown :options="exportOptions" />
          </div>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-slate-200 bg-slate-50">
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Member</th>
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Decision</th>
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Decided By</th>
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Date</th>
                <th class="w-8"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="d in pagedDecisions" :key="d.id"
                  :id="`decision-${d.id}`"
                  class="border-b border-slate-100 hover:bg-slate-50/50">
                <td class="py-2 px-4">
                  <div class="text-xs font-medium text-slate-800">{{ d.memberDisplayName || '—' }}</div>
                  <div class="text-[10px] text-slate-400 font-mono truncate max-w-xs" :title="d.memberDn">{{ d.memberDn }}</div>
                </td>
                <td class="py-2 px-4">
                  <span class="text-xs font-medium px-2 py-0.5 rounded-full" :class="decisionClass(d.decision)">{{ d.decision }}</span>
                </td>
                <td class="py-2 px-4 text-xs text-slate-600">{{ d.decidedBy || '—' }}</td>
                <td class="py-2 px-4 text-xs text-slate-500 font-mono">{{ formatDate(d.decidedAt) }}</td>
                <td class="py-2 px-1"><CopyLinkButton :anchor="`decision-${d.id}`" /></td>
              </tr>
            </tbody>
          </table>
        </div>
        <!-- Pagination -->
        <div v-if="totalPages > 1" class="px-4 py-3 border-t border-slate-200 flex items-center justify-between">
          <span class="text-xs text-slate-500">Page {{ page + 1 }} of {{ totalPages }}</span>
          <div class="flex gap-1">
            <button @click="page = Math.max(0, page - 1)" :disabled="page === 0" class="btn-sm">Prev</button>
            <button @click="page = Math.min(totalPages - 1, page + 1)" :disabled="page >= totalPages - 1" class="btn-sm">Next</button>
          </div>
        </div>
      </section>

      <!-- Campaign history timeline -->
      <section v-if="campaign.history && campaign.history.length" class="bg-white border border-slate-200 rounded-xl p-4">
        <h2 class="text-sm font-semibold text-slate-700 mb-4">Campaign History</h2>
        <div class="relative pl-6 border-l-2 border-slate-200 space-y-4">
          <div v-for="(h, i) in campaign.history" :key="i" class="relative">
            <div class="absolute -left-[25px] w-3 h-3 rounded-full border-2 border-slate-300 bg-white" />
            <div class="text-xs">
              <span class="font-medium text-slate-800">{{ h.newStatus }}</span>
              <span v-if="h.oldStatus" class="text-slate-400"> (from {{ h.oldStatus }})</span>
              <span v-if="h.changedBy" class="text-slate-500"> by {{ h.changedBy }}</span>
            </div>
            <div class="text-[10px] text-slate-400 font-mono">{{ formatDate(h.changedAt) }}</div>
            <div v-if="h.note" class="text-xs text-slate-500 mt-0.5 italic">{{ h.note }}</div>
          </div>
        </div>
      </section>
    </template>

    <!-- Mobile: card layout for decisions -->
    <div class="sm:hidden space-y-2 mb-6" v-if="campaign">
      <div v-for="d in pagedDecisions" :key="d.id + '-card'"
           :id="`decision-${d.id}-m`"
           class="bg-white border border-slate-200 rounded-xl p-3">
        <div class="flex items-center justify-between mb-1">
          <span class="text-xs font-medium text-slate-800">{{ d.memberDisplayName || d.memberDn }}</span>
          <span class="text-xs font-medium px-2 py-0.5 rounded-full" :class="decisionClass(d.decision)">{{ d.decision }}</span>
        </div>
        <div class="text-[10px] text-slate-400">{{ d.decidedBy || '—' }} &middot; {{ formatDate(d.decidedAt) }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'

import { RouterLink, useRoute } from 'vue-router'
import { getPortalCampaignDetail, exportCampaignCsv, exportCampaignPdf } from '@/api/auditorPortal'
import CopyLinkButton from './components/CopyLinkButton.vue'
import ExportDropdown from './components/ExportDropdown.vue'

const props = defineProps({ token: String, metadata: Object, scope: Object })
const route = useRoute()

const loading = ref(true)
const campaign = ref(null)
const search = ref('')
const page = ref(0)
const PAGE_SIZE = 50
const campaignId = computed(() => route.params.campaignId)

const exportOptions = computed(() => [
  { label: 'Export CSV', filename: 'campaign-decisions.csv', fn: () => exportCampaignCsv(props.token, campaignId.value) },
  { label: 'Export PDF', filename: 'campaign-decisions.pdf', fn: () => exportCampaignPdf(props.token, campaignId.value) },
])

const filteredDecisions = computed(() => {
  if (!campaign.value?.decisions) return []
  const q = search.value.toLowerCase()
  if (!q) return campaign.value.decisions
  return campaign.value.decisions.filter(d =>
    (d.memberDisplayName || '').toLowerCase().includes(q) ||
    (d.memberDn || '').toLowerCase().includes(q) ||
    (d.decision || '').toLowerCase().includes(q) ||
    (d.decidedBy || '').toLowerCase().includes(q)
  )
})

watch(search, () => { page.value = 0 })

const totalPages = computed(() => Math.ceil(filteredDecisions.value.length / PAGE_SIZE))
const pagedDecisions = computed(() =>
  filteredDecisions.value.slice(page.value * PAGE_SIZE, (page.value + 1) * PAGE_SIZE)
)

function statusClass(status) {
  switch (status) {
    case 'CLOSED':   return 'bg-green-100 text-green-700'
    case 'ACTIVE':   return 'bg-blue-100 text-blue-700'
    case 'UPCOMING': return 'bg-slate-100 text-slate-600'
    default:         return 'bg-slate-100 text-slate-600'
  }
}

function decisionClass(decision) {
  switch (decision) {
    case 'CONFIRM': return 'bg-green-100 text-green-700'
    case 'REVOKE':  return 'bg-red-100 text-red-700'
    case 'PENDING': return 'bg-amber-100 text-amber-700'
    default:        return 'bg-slate-100 text-slate-600'
  }
}

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
}

onMounted(async () => {
  try {
    const { data } = await getPortalCampaignDetail(props.token, campaignId.value)
    campaign.value = data
  } catch { /* handled by layout */ }
  loading.value = false

  // Scroll to anchor if present (after DOM renders)
  if (window.location.hash) {
    await nextTick()
    const el = document.getElementById(window.location.hash.slice(1))
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
})
</script>

<style scoped>
@reference "tailwindcss";
.btn-sm { @apply px-3 py-1 border border-slate-200 text-slate-600 rounded-lg text-xs hover:bg-slate-50 disabled:opacity-50; }
</style>
