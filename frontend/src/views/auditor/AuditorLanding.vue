<template>
  <div>
    <!-- Scope summary -->
    <div class="bg-white border border-slate-200 rounded-xl p-5 mb-6">
      <h2 class="text-sm font-semibold text-slate-500 uppercase tracking-wider mb-3">Evidence Package Scope</h2>
      <div class="flex flex-wrap gap-3">
        <div v-for="item in scopeItems" :key="item.label"
             class="flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm"
             :class="item.included ? 'bg-green-50 text-green-700' : 'bg-slate-50 text-slate-400'">
          <svg v-if="item.included" class="w-4 h-4 text-green-500" fill="currentColor" viewBox="0 0 20 20">
            <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd" />
          </svg>
          <svg v-else class="w-4 h-4" fill="none" viewBox="0 0 20 20" stroke="currentColor" stroke-width="1.5">
            <circle cx="10" cy="10" r="7" />
          </svg>
          {{ item.label }}
        </div>
      </div>
      <div v-if="scope.dataFrom || scope.dataTo" class="mt-3 text-xs text-slate-500">
        Evidence window:
        <span class="font-medium text-slate-700">{{ formatDate(scope.dataFrom) || 'Earliest' }}</span>
        to
        <span class="font-medium text-slate-700">{{ formatDate(scope.dataTo) || 'Latest' }}</span>
      </div>
    </div>

    <!-- Metrics cards -->
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
      <MetricCard label="Campaigns" :value="metrics.campaignCount" icon="campaigns" />
      <MetricCard label="Decisions Complete" :value="metrics.completionPct + '%'" :sub="metrics.completedDecisions + ' / ' + metrics.totalDecisions" icon="decisions" />
      <MetricCard label="SoD Violations" :value="metrics.sodViolations" :sub="metrics.sodOpen + ' open'" icon="sod"
                  :alert="metrics.sodOpen > 0" />
      <MetricCard label="Approvals" :value="metrics.approvalCount" icon="approvals" />
    </div>

    <!-- Decision breakdown + SoD status (CSS donut charts) -->
    <div class="grid md:grid-cols-2 gap-4 mb-6" v-if="metrics.totalDecisions > 0 || metrics.sodViolations > 0">
      <!-- Decision donut -->
      <div v-if="metrics.totalDecisions > 0" class="bg-white border border-slate-200 rounded-xl p-5">
        <h3 class="text-sm font-semibold text-slate-700 mb-4">Decision Breakdown</h3>
        <div class="flex items-center gap-6">
          <DonutChart :segments="decisionSegments" :size="120" />
          <div class="space-y-2">
            <div v-for="s in decisionSegments" :key="s.label" class="flex items-center gap-2 text-sm">
              <span class="w-3 h-3 rounded-full" :style="{ backgroundColor: s.color }" />
              <span class="text-slate-600">{{ s.label }}</span>
              <span class="font-semibold text-slate-800 ml-auto">{{ s.value }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- SoD status donut -->
      <div v-if="metrics.sodViolations > 0" class="bg-white border border-slate-200 rounded-xl p-5">
        <h3 class="text-sm font-semibold text-slate-700 mb-4">SoD Violation Status</h3>
        <div class="flex items-center gap-6">
          <DonutChart :segments="sodSegments" :size="120" />
          <div class="space-y-2">
            <div v-for="s in sodSegments" :key="s.label" class="flex items-center gap-2 text-sm">
              <span class="w-3 h-3 rounded-full" :style="{ backgroundColor: s.color }" />
              <span class="text-slate-600">{{ s.label }}</span>
              <span class="font-semibold text-slate-800 ml-auto">{{ s.value }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- SoD zero state -->
    <div v-if="scope.includeSod && metrics.sodPolicies > 0 && metrics.sodViolations === 0 && !loadingData"
         class="bg-green-50 border border-green-200 rounded-xl p-5 mb-6 flex items-center gap-3">
      <svg class="w-6 h-6 text-green-500 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <div>
        <div class="text-sm font-medium text-green-800">No Separation of Duties violations detected</div>
        <div class="text-xs text-green-600">All {{ metrics.sodPolicies }} {{ metrics.sodPolicies === 1 ? 'policy' : 'policies' }} passed without violations</div>
      </div>
    </div>

    <!-- Navigation cards to sections -->
    <h2 class="text-sm font-semibold text-slate-500 uppercase tracking-wider mb-3">Evidence Sections</h2>
    <div class="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
      <RouterLink v-for="section in sections" :key="section.to"
                  :to="section.to"
                  class="bg-white border border-slate-200 rounded-xl p-4 hover:border-slate-300 hover:shadow-sm transition-all group">
        <div class="text-sm font-medium text-slate-800 group-hover:text-slate-900">{{ section.label }}</div>
        <div class="text-xs text-slate-500 mt-0.5">{{ section.desc }}</div>
      </RouterLink>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { getPortalCampaigns, getPortalSod, getPortalApprovals } from '@/api/auditorPortal'
import DonutChart from './components/DonutChart.vue'
import MetricCard from './components/MetricCard.vue'

const props = defineProps({
  token: String,
  metadata: Object,
  scope: Object,
})

const loadingData = ref(true)
const campaigns = ref([])
const sodData = ref({ policies: [], violations: [] })
const approvals = ref([])

const metrics = computed(() => {
  const totalDecisions = campaigns.value.reduce((s, c) => s + (c.totalDecisions || 0), 0)
  const completedDecisions = campaigns.value.reduce((s, c) => s + (c.completedDecisions || 0), 0)
  const violations = sodData.value.violations || []

  return {
    campaignCount: campaigns.value.length,
    totalDecisions,
    completedDecisions,
    completionPct: totalDecisions > 0 ? Math.round((completedDecisions / totalDecisions) * 100) : 0,
    sodPolicies: (sodData.value.policies || []).length,
    sodViolations: violations.length,
    sodOpen: violations.filter(v => v.status === 'OPEN').length,
    sodResolved: violations.filter(v => v.status === 'RESOLVED').length,
    sodExempted: violations.filter(v => v.status === 'EXEMPTED').length,
    approvalCount: approvals.value.length,
  }
})

const scopeItems = computed(() => [
  { label: `${campaigns.value.length} Campaign${campaigns.value.length !== 1 ? 's' : ''}`, included: (props.scope.campaignIds || []).length > 0 },
  { label: 'SoD Analysis', included: !!props.scope.includeSod },
  { label: 'Entitlements', included: !!props.scope.includeEntitlements },
  { label: 'Audit Log', included: !!props.scope.includeAuditEvents },
  { label: 'Approvals', included: true },
])

const decisionSegments = computed(() => {
  const completed = metrics.value.completedDecisions
  const pending = metrics.value.totalDecisions - completed
  return [
    { label: 'Completed', value: completed, color: '#22c55e' },
    { label: 'Pending', value: pending, color: '#e2e8f0' },
  ].filter(s => s.value > 0)
})

const sodSegments = computed(() => {
  const m = metrics.value
  return [
    { label: 'Open', value: m.sodOpen, color: '#ef4444' },
    { label: 'Resolved', value: m.sodResolved, color: '#22c55e' },
    { label: 'Exempted', value: m.sodExempted, color: '#f59e0b' },
  ].filter(s => s.value > 0)
})

const sections = computed(() => {
  const base = `/auditor/${props.token}`
  const items = [
    { to: `${base}/campaigns`, label: 'Access Review Campaigns', desc: `${campaigns.value.length} campaigns with decision data` },
  ]
  if (props.scope.includeSod) {
    items.push({ to: `${base}/sod`, label: 'Separation of Duties', desc: `${metrics.value.sodPolicies} policies, ${metrics.value.sodViolations} violations` })
  }
  if (props.scope.includeEntitlements) {
    items.push({ to: `${base}/entitlements`, label: 'User Entitlements', desc: 'Point-in-time LDAP group memberships' })
  }
  if (props.scope.includeAuditEvents) {
    items.push({ to: `${base}/audit-events`, label: 'Audit Log', desc: 'Directory change events within evidence window' })
  }
  items.push({ to: `${base}/approvals`, label: 'Approval History', desc: `${approvals.value.length} workflow approvals` })
  return items
})

function formatDate(iso) {
  if (!iso) return null
  return new Date(iso).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
}

onMounted(async () => {
  const promises = []

  promises.push(
    getPortalCampaigns(props.token)
      .then(r => { campaigns.value = r.data })
      .catch(() => {})
  )

  if (props.scope.includeSod) {
    promises.push(
      getPortalSod(props.token)
        .then(r => { sodData.value = r.data })
        .catch(() => {})
    )
  }

  promises.push(
    getPortalApprovals(props.token)
      .then(r => { approvals.value = r.data })
      .catch(() => {})
  )

  await Promise.all(promises)
  loadingData.value = false
})
</script>
