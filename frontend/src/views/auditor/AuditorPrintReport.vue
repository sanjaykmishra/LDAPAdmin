<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  getPortalMetadata, getPortalCampaigns, getPortalCampaignDetail,
  getPortalSod, getPortalEntitlements, getPortalAuditEvents, getPortalApprovals,
} from '@/api/auditorPortal'

const route = useRoute()
const token = route.params.token

const loading = ref(true)
const error = ref(null)

const metadata = ref(null)
const campaigns = ref([])
const campaignDetails = ref([])
const sodData = ref({ policies: [], violations: [] })
const entitlements = ref([])
const auditEvents = ref([])
const approvals = ref([])

// ── Computed ──────────────────────────────────────────────────────────────
const scope = computed(() => metadata.value?.scope || {})
const directoryName = computed(() => metadata.value?.directoryName || 'Directory')

const metrics = computed(() => {
  const totalDecisions = campaigns.value.reduce((s, c) => s + (c.totalDecisions || 0), 0)
  const completedDecisions = campaigns.value.reduce((s, c) => s + (c.completedDecisions || 0), 0)
  const violations = sodData.value.violations || []
  return {
    campaignCount: campaigns.value.length,
    totalDecisions,
    completedDecisions,
    completionPct: totalDecisions > 0 ? Math.round((completedDecisions / totalDecisions) * 100) : 0,
    sodViolations: violations.length,
    sodOpen: violations.filter(v => v.status === 'OPEN').length,
    sodResolved: violations.filter(v => v.status === 'RESOLVED').length,
    sodExempted: violations.filter(v => v.status === 'EXEMPTED').length,
    approvalCount: approvals.value.length,
    entitlementCount: entitlements.value.length,
    auditEventCount: auditEvents.value.length,
  }
})

// ── Helpers ───────────────────────────────────────────────────────────────
function fmtDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
}

function fmtDateTime(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

function humanize(str) {
  if (!str) return ''
  return str.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
}

function statusClass(status) {
  switch (status) {
    case 'APPROVED': case 'RESOLVED': case 'CLOSED': return 'text-green-700'
    case 'REJECTED': case 'OPEN': case 'CANCELLED': return 'text-red-700'
    case 'PENDING': case 'ACTIVE': return 'text-amber-700'
    case 'EXEMPTED': return 'text-yellow-700'
    default: return 'text-gray-700'
  }
}

function handlePrint() {
  window.print()
}

// ── Data loading ──────────────────────────────────────────────────────────
onMounted(async () => {
  try {
    const { data: meta } = await getPortalMetadata(token)
    metadata.value = meta

    const promises = []

    promises.push(
      getPortalCampaigns(token)
        .then(async (r) => {
          campaigns.value = r.data
          // Fetch detail for each campaign (decision-level data)
          const details = await Promise.all(
            r.data.map(c => getPortalCampaignDetail(token, c.id).then(d => d.data).catch(() => null))
          )
          campaignDetails.value = details.filter(Boolean)
        })
        .catch(() => {})
    )

    if (meta.scope?.includeSod) {
      promises.push(getPortalSod(token).then(r => { sodData.value = r.data }).catch(() => {}))
    }
    if (meta.scope?.includeEntitlements) {
      promises.push(getPortalEntitlements(token).then(r => { entitlements.value = r.data }).catch(() => {}))
    }
    if (meta.scope?.includeAuditEvents) {
      promises.push(getPortalAuditEvents(token).then(r => { auditEvents.value = r.data }).catch(() => {}))
    }
    promises.push(getPortalApprovals(token).then(r => { approvals.value = r.data }).catch(() => {}))

    await Promise.all(promises)
  } catch (e) {
    error.value = e.response?.data?.detail || e.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="print-report">
    <!-- Screen-only toolbar -->
    <div class="print-toolbar no-print">
      <div class="max-w-4xl mx-auto px-6 py-3 flex items-center justify-between">
        <div class="flex items-center gap-3">
          <RouterLink :to="`/auditor/${token}`" class="text-sm text-blue-600 hover:text-blue-800">
            &larr; Back to portal
          </RouterLink>
        </div>
        <button @click="handlePrint"
                class="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors">
          Print / Save as PDF
        </button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="text-center py-20 text-gray-500">
      <p class="text-lg">Preparing print report...</p>
      <p class="text-sm mt-2">Loading all evidence sections</p>
    </div>

    <div v-else-if="error" class="text-center py-20 text-red-600">
      <p class="text-lg">Failed to load report</p>
      <p class="text-sm mt-2">{{ error }}</p>
    </div>

    <div v-else class="report-content max-w-4xl mx-auto px-6 py-8">

      <!-- ═══════════════════════════════════════════════════════════════════ -->
      <!-- Cover / Header -->
      <!-- ═══════════════════════════════════════════════════════════════════ -->
      <div class="cover-section mb-10 pb-8 border-b-2 border-gray-300">
        <h1 class="text-3xl font-bold text-gray-900">Evidence Report</h1>
        <p class="text-lg text-gray-600 mt-1">{{ directoryName }}</p>

        <div class="mt-6 grid grid-cols-2 gap-x-8 gap-y-2 text-sm">
          <div><span class="text-gray-500">Generated:</span> <span class="font-medium">{{ fmtDateTime(new Date().toISOString()) }}</span></div>
          <div><span class="text-gray-500">Link Expires:</span> <span class="font-medium">{{ fmtDate(metadata.expiresAt) }}</span></div>
          <div v-if="scope.dataFrom || scope.dataTo" class="col-span-2">
            <span class="text-gray-500">Evidence Window:</span>
            <span class="font-medium">{{ fmtDate(scope.dataFrom) || 'Earliest' }} — {{ fmtDate(scope.dataTo) || 'Latest' }}</span>
          </div>
        </div>

        <!-- Scope pills -->
        <div class="mt-4 flex flex-wrap gap-2 text-xs">
          <span class="px-2 py-1 rounded-full bg-gray-100 text-gray-600">{{ metrics.campaignCount }} Access Review Campaigns</span>
          <span v-if="scope.includeSod" class="px-2 py-1 rounded-full bg-gray-100 text-gray-600">SoD Analysis</span>
          <span v-if="scope.includeEntitlements" class="px-2 py-1 rounded-full bg-gray-100 text-gray-600">{{ metrics.entitlementCount }} Entitlements</span>
          <span v-if="scope.includeAuditEvents" class="px-2 py-1 rounded-full bg-gray-100 text-gray-600">{{ metrics.auditEventCount }} Audit Events</span>
          <span class="px-2 py-1 rounded-full bg-gray-100 text-gray-600">{{ metrics.approvalCount }} Approvals</span>
        </div>
      </div>

      <!-- ═══════════════════════════════════════════════════════════════════ -->
      <!-- Executive Summary -->
      <!-- ═══════════════════════════════════════════════════════════════════ -->
      <section class="report-section">
        <h2 class="section-title">Executive Summary</h2>
        <table class="summary-table">
          <tbody>
            <tr>
              <td class="label-cell">Access Review Campaigns</td>
              <td class="value-cell">{{ metrics.campaignCount }}</td>
            </tr>
            <tr>
              <td class="label-cell">Decisions Completed</td>
              <td class="value-cell">{{ metrics.completedDecisions }} / {{ metrics.totalDecisions }} ({{ metrics.completionPct }}%)</td>
            </tr>
            <tr v-if="scope.includeSod">
              <td class="label-cell">SoD Violations</td>
              <td class="value-cell">
                {{ metrics.sodViolations }} total
                <span v-if="metrics.sodViolations > 0"> — {{ metrics.sodOpen }} open, {{ metrics.sodResolved }} resolved, {{ metrics.sodExempted }} exempted</span>
              </td>
            </tr>
            <tr v-if="scope.includeEntitlements">
              <td class="label-cell">Users with Entitlements</td>
              <td class="value-cell">{{ metrics.entitlementCount }}</td>
            </tr>
            <tr v-if="scope.includeAuditEvents">
              <td class="label-cell">Audit Events</td>
              <td class="value-cell">{{ metrics.auditEventCount }}</td>
            </tr>
            <tr>
              <td class="label-cell">Approval Records</td>
              <td class="value-cell">{{ metrics.approvalCount }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- ═══════════════════════════════════════════════════════════════════ -->
      <!-- Access Review Campaigns -->
      <!-- ═══════════════════════════════════════════════════════════════════ -->
      <section class="report-section page-break">
        <h2 class="section-title">Access Review Campaigns</h2>

        <div v-for="(campaign, ci) in campaigns" :key="campaign.id" class="mb-6">
          <h3 class="text-base font-semibold text-gray-800 mb-2">{{ ci + 1 }}. {{ campaign.name }}</h3>
          <table class="summary-table mb-3">
            <tbody>
              <tr>
                <td class="label-cell">Status</td>
                <td class="value-cell" :class="statusClass(campaign.status)">{{ campaign.status }}</td>
              </tr>
              <tr>
                <td class="label-cell">Period</td>
                <td class="value-cell">{{ fmtDate(campaign.startsAt) }} — {{ fmtDate(campaign.deadline) }}</td>
              </tr>
              <tr>
                <td class="label-cell">Completion</td>
                <td class="value-cell">
                  {{ campaign.completedDecisions || 0 }} / {{ campaign.totalDecisions || 0 }}
                  ({{ campaign.totalDecisions > 0 ? Math.round((campaign.completedDecisions / campaign.totalDecisions) * 100) : 0 }}%)
                </td>
              </tr>
            </tbody>
          </table>

          <!-- Decision detail table if available -->
          <div v-if="campaignDetails[ci]?.reviewGroups?.length">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Group</th>
                  <th>Reviewer</th>
                  <th class="text-right">Decisions</th>
                  <th class="text-right">Completed</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="rg in campaignDetails[ci].reviewGroups" :key="rg.id">
                  <td>{{ rg.groupName }}</td>
                  <td>{{ rg.reviewerUsername || '—' }}</td>
                  <td class="text-right">{{ rg.totalDecisions || 0 }}</td>
                  <td class="text-right">{{ rg.completedDecisions || 0 }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <p v-if="!campaigns.length" class="text-gray-400 text-sm">No access review campaigns in scope.</p>
      </section>

      <!-- ═══════════════════════════════════════════════════════════════════ -->
      <!-- Separation of Duties -->
      <!-- ═══════════════════════════════════════════════════════════════════ -->
      <section v-if="scope.includeSod" class="report-section page-break">
        <h2 class="section-title">Separation of Duties</h2>

        <h3 class="text-sm font-semibold text-gray-700 mb-2">Policies ({{ sodData.policies?.length || 0 }})</h3>
        <table v-if="sodData.policies?.length" class="data-table mb-6">
          <thead>
            <tr>
              <th>Policy</th>
              <th>Group A</th>
              <th>Group B</th>
              <th>Severity</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in sodData.policies" :key="p.id">
              <td>{{ p.name }}</td>
              <td>{{ p.groupAName }}</td>
              <td>{{ p.groupBName }}</td>
              <td :class="p.severity === 'CRITICAL' ? 'text-red-700 font-semibold' : ''">{{ p.severity }}</td>
            </tr>
          </tbody>
        </table>

        <h3 class="text-sm font-semibold text-gray-700 mb-2">Violations ({{ (sodData.violations || []).length }})</h3>
        <table v-if="(sodData.violations || []).length" class="data-table">
          <thead>
            <tr>
              <th>User</th>
              <th>Policy</th>
              <th>Status</th>
              <th>Detected</th>
              <th>Resolved</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="v in sodData.violations" :key="v.id">
              <td>{{ v.userDisplayName || v.userDn }}</td>
              <td>{{ v.policyName }}</td>
              <td :class="statusClass(v.status)">{{ v.status }}</td>
              <td>{{ fmtDate(v.detectedAt) }}</td>
              <td>{{ fmtDate(v.resolvedAt) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="!(sodData.violations || []).length" class="text-sm text-green-700">No SoD violations detected.</p>
      </section>

      <!-- ═══════════════════════════════════════════════════════════════════ -->
      <!-- Entitlements -->
      <!-- ═══════════════════════════════════════════════════════════════════ -->
      <section v-if="scope.includeEntitlements" class="report-section page-break">
        <h2 class="section-title">User Entitlements ({{ entitlements.length }})</h2>
        <table v-if="entitlements.length" class="data-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Login</th>
              <th>Email</th>
              <th>Groups</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in entitlements" :key="u.dn">
              <td>{{ u.displayName || u.cn || '—' }}</td>
              <td>{{ u.loginName || '—' }}</td>
              <td>{{ u.mail || '—' }}</td>
              <td class="text-xs">{{ (u.groups || []).join(', ') || '—' }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="!entitlements.length" class="text-sm text-gray-400">No entitlement data available.</p>
      </section>

      <!-- ═══════════════════════════════════════════════════════════════════ -->
      <!-- Audit Events -->
      <!-- ═══════════════════════════════════════════════════════════════════ -->
      <section v-if="scope.includeAuditEvents" class="report-section page-break">
        <h2 class="section-title">Audit Events ({{ auditEvents.length }})</h2>
        <table v-if="auditEvents.length" class="data-table">
          <thead>
            <tr>
              <th>Date</th>
              <th>Actor</th>
              <th>Action</th>
              <th>Target</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="e in auditEvents" :key="e.id">
              <td class="whitespace-nowrap">{{ fmtDateTime(e.occurredAt) }}</td>
              <td>{{ e.actorUsername || 'system' }}</td>
              <td>{{ humanize(e.action) }}</td>
              <td class="text-xs break-all">{{ e.targetDn || '—' }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="!auditEvents.length" class="text-sm text-gray-400">No audit events in scope.</p>
      </section>

      <!-- ═══════════════════════════════════════════════════════════════════ -->
      <!-- Approvals -->
      <!-- ═══════════════════════════════════════════════════════════════════ -->
      <section class="report-section page-break">
        <h2 class="section-title">Approval History ({{ approvals.length }})</h2>
        <table v-if="approvals.length" class="data-table">
          <thead>
            <tr>
              <th>Type</th>
              <th>Requester</th>
              <th>Reviewer</th>
              <th>Status</th>
              <th>Submitted</th>
              <th>Reviewed</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in approvals" :key="a.id">
              <td>{{ humanize(a.requestType) }}</td>
              <td>{{ a.requestedBy || '—' }}</td>
              <td>{{ a.reviewedBy || '—' }}</td>
              <td :class="statusClass(a.status)">{{ a.status }}</td>
              <td>{{ fmtDate(a.createdAt) }}</td>
              <td>{{ fmtDate(a.reviewedAt) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="!approvals.length" class="text-sm text-gray-400">No approval records in scope.</p>
      </section>

      <!-- Footer -->
      <div class="mt-10 pt-4 border-t border-gray-300 text-xs text-gray-400 text-center">
        Generated {{ fmtDateTime(new Date().toISOString()) }} &middot; {{ directoryName }} &middot; LDAPAdmin Evidence Report
      </div>
    </div>
  </div>
</template>

<style scoped>
@reference "tailwindcss";

/* ── Print-specific styles ────────────────────────────────────────────── */
@media print {
  .no-print { display: none !important; }
  .report-content { max-width: 100%; padding: 0; }
  .page-break { page-break-before: always; }
  .cover-section { page-break-after: always; }

  body { font-size: 10pt; }
  .section-title { font-size: 14pt; }
  .data-table { font-size: 8pt; }
  .data-table th, .data-table td { padding: 3px 6px; }
}

/* ── Screen toolbar ───────────────────────────────────────────────────── */
.print-toolbar {
  position: sticky;
  top: 0;
  z-index: 20;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

/* ── Report typography ────────────────────────────────────────────────── */
.section-title {
  font-size: 1.125rem;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 0.75rem;
  padding-bottom: 0.5rem;
  border-bottom: 2px solid #e2e8f0;
}

.report-section {
  margin-bottom: 2rem;
}

/* ── Summary table (key-value pairs) ──────────────────────────────────── */
.summary-table {
  width: 100%;
  border-collapse: collapse;
}
.summary-table td {
  padding: 0.375rem 0.75rem;
  font-size: 0.875rem;
  border-bottom: 1px solid #f1f5f9;
}
.label-cell {
  color: #64748b;
  width: 200px;
  font-weight: 500;
}
.value-cell {
  color: #1e293b;
  font-weight: 600;
}

/* ── Data table (full-width with header) ──────────────────────────────── */
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.8125rem;
}
.data-table thead th {
  text-align: left;
  padding: 0.5rem 0.75rem;
  font-size: 0.6875rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #64748b;
  border-bottom: 2px solid #e2e8f0;
  background-color: #f8fafc;
}
.data-table tbody td {
  padding: 0.375rem 0.75rem;
  border-bottom: 1px solid #f1f5f9;
  color: #334155;
}
.data-table tbody tr:last-child td {
  border-bottom: none;
}
</style>
