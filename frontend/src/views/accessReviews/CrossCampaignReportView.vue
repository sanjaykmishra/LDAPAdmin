<template>
  <div class="p-6">
    <h1 class="text-2xl font-bold text-gray-900">Cross-Campaign Report</h1>
    <p class="text-sm text-gray-500 mt-1 mb-6">Compare decisions across multiple access review campaigns</p>

    <!-- Filters -->
    <div class="flex flex-wrap items-end gap-4 mb-6 p-4 bg-gray-50 rounded-lg border border-gray-200">
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">From</label>
        <input type="date" v-model="fromDate" class="input" />
      </div>
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">To</label>
        <input type="date" v-model="toDate" class="input" />
      </div>
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">Status</label>
        <select v-model="statusFilter" class="input">
          <option value="">All statuses</option>
          <option v-for="s in statuses" :key="s" :value="s">{{ s }}</option>
        </select>
      </div>
      <button @click="loadReport" :disabled="loading" class="btn-primary">
        {{ loading ? 'Loading...' : 'Generate Report' }}
      </button>
      <div v-if="report" class="flex gap-2 ml-auto">
        <button @click="doExport('csv')" :disabled="exporting" class="btn-secondary">Export CSV</button>
        <button @click="doExport('pdf')" :disabled="exporting" class="btn-secondary">Export PDF</button>
      </div>
    </div>

    <!-- Error -->
    <div v-if="error" class="mb-4 p-3 bg-red-50 text-red-700 rounded-lg border border-red-200 text-sm">
      {{ error }}
    </div>

    <div v-if="report">
      <!-- Summary cards -->
      <div class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-7 gap-4 mb-6">
        <div class="stat-card">
          <div class="stat-label">Campaigns</div>
          <div class="stat-value">{{ report.totalCampaigns }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Total Decisions</div>
          <div class="stat-value">{{ report.totalDecisions }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Confirmed</div>
          <div class="stat-value text-green-700">{{ report.totalConfirmed }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Revoked</div>
          <div class="stat-value text-red-700">{{ report.totalRevoked }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Pending</div>
          <div class="stat-value text-yellow-700">{{ report.totalPending }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Revocation Rate</div>
          <div class="stat-value">{{ report.overallRevocationRate.toFixed(1) }}%</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Avg Completion</div>
          <div class="stat-value">{{ report.avgCompletionDays != null ? report.avgCompletionDays.toFixed(1) + 'd' : '—' }}</div>
        </div>
      </div>

      <!-- Campaigns table -->
      <h2 class="text-lg font-semibold text-gray-800 mb-3">Campaigns</h2>
      <div class="overflow-x-auto mb-8">
        <table class="w-full text-sm border border-gray-200 rounded-lg">
          <thead class="bg-gray-100">
            <tr>
              <th class="th">Name</th>
              <th class="th">Status</th>
              <th class="th text-right">Total</th>
              <th class="th text-right">Confirmed</th>
              <th class="th text-right">Revoked</th>
              <th class="th text-right">Pending</th>
              <th class="th text-right">Complete</th>
              <th class="th text-right">Duration</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(c, i) in report.campaigns" :key="c.id"
                :class="i % 2 === 1 ? 'bg-gray-50' : ''"
                class="hover:bg-blue-50 cursor-pointer"
                @click="goToCampaign(c.id)">
              <td class="td font-medium">{{ c.name }}</td>
              <td class="td"><span :class="statusClass(c.status)">{{ c.status }}</span></td>
              <td class="td text-right">{{ c.total }}</td>
              <td class="td text-right text-green-700">{{ c.confirmed }}</td>
              <td class="td text-right text-red-700">{{ c.revoked }}</td>
              <td class="td text-right text-yellow-700">{{ c.pending }}</td>
              <td class="td text-right">{{ c.percentComplete.toFixed(1) }}%</td>
              <td class="td text-right">{{ c.durationDays != null ? c.durationDays + 'd' : '—' }}</td>
            </tr>
            <tr v-if="!report.campaigns.length">
              <td colspan="8" class="td text-center text-gray-400">No campaigns in this date range</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Reviewers table -->
      <h2 class="text-lg font-semibold text-gray-800 mb-3">Reviewers</h2>
      <div class="overflow-x-auto">
        <table class="w-full text-sm border border-gray-200 rounded-lg">
          <thead class="bg-gray-100">
            <tr>
              <th class="th">Reviewer</th>
              <th class="th text-right">Decisions</th>
              <th class="th text-right">Confirmed</th>
              <th class="th text-right">Revoked</th>
              <th class="th text-right">Revocation Rate</th>
              <th class="th text-right">Avg Response</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in report.reviewers" :key="r.reviewerId"
                :class="i % 2 === 1 ? 'bg-gray-50' : ''">
              <td class="td font-medium">{{ r.username }}</td>
              <td class="td text-right">{{ r.totalDecisions }}</td>
              <td class="td text-right text-green-700">{{ r.confirmed }}</td>
              <td class="td text-right text-red-700">{{ r.revoked }}</td>
              <td class="td text-right">{{ r.revocationRate.toFixed(1) }}%</td>
              <td class="td text-right">{{ r.avgResponseHours != null ? r.avgResponseHours.toFixed(1) + 'h' : '—' }}</td>
            </tr>
            <tr v-if="!report.reviewers.length">
              <td colspan="6" class="td text-center text-gray-400">No reviewer data available</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-else-if="!loading" class="text-center text-gray-400 py-16">
      Select a date range and click "Generate Report" to view cross-campaign metrics.
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useApi, downloadBlob } from '@/composables/useApi'
import { getCrossCampaignReport, exportCrossCampaignReport } from '@/api/crossCampaignReport'

const route = useRoute()
const router = useRouter()
const { loading, error, call } = useApi()
const dirId = route.params.dirId

const report = ref(null)
const exporting = ref(false)

// Default: last 12 months
const now = new Date()
const yearAgo = new Date(now)
yearAgo.setFullYear(yearAgo.getFullYear() - 1)
const fromDate = ref(yearAgo.toISOString().slice(0, 10))
const toDate = ref(now.toISOString().slice(0, 10))
const statusFilter = ref('')

const statuses = ['UPCOMING', 'ACTIVE', 'CLOSED', 'CANCELLED', 'EXPIRED']

function buildParams() {
  const params = {
    from: new Date(fromDate.value).toISOString(),
    to: new Date(toDate.value + 'T23:59:59Z').toISOString(),
  }
  if (statusFilter.value) params.status = statusFilter.value
  return params
}

async function loadReport() {
  try {
    const res = await call(() => getCrossCampaignReport(dirId, buildParams()))
    report.value = res.data
  } catch { /* handled by useApi */ }
}

async function doExport(format) {
  exporting.value = true
  try {
    const res = await exportCrossCampaignReport(dirId, { ...buildParams(), format })
    const ext = format === 'pdf' ? 'pdf' : 'csv'
    downloadBlob(res.data, `cross-campaign-report.${ext}`)
  } catch (err) {
    error.value = 'Export failed: ' + (err.message || 'Unknown error')
  } finally {
    exporting.value = false
  }
}

function goToCampaign(campaignId) {
  router.push({ name: 'accessReviewDetail', params: { dirId, campaignId } })
}

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
</script>

<style scoped>
@reference "tailwindcss";
.stat-card    { @apply p-4 bg-white border border-gray-200 rounded-lg; }
.stat-label   { @apply text-xs text-gray-500 mb-1; }
.stat-value   { @apply text-xl font-bold text-gray-900; }
.th           { @apply px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider; }
.td           { @apply px-3 py-2 text-gray-700; }
</style>
