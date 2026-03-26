<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Audit Reports</h1>
      <button @click="openEvidencePackage" :disabled="!dirId" class="btn-secondary flex items-center gap-1.5">
        <svg class="w-4 h-4" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M10 2v12M10 2l4 4M10 2 6 6"/><path d="M3 13v3a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-3"/></svg>
        Evidence Package
      </button>
    </div>

    <!-- Report runner -->
    <section class="bg-white border border-gray-200 rounded-xl p-5 mb-6">
      <div class="grid grid-cols-3 gap-3 mb-3">
        <!-- Directory picker (superadmin — when no dirId from route) -->
        <div v-if="!routeDirId">
          <label class="block text-sm font-medium text-gray-700 mb-1">Directory</label>
          <select v-model="selectedDir" class="input w-full">
            <option value="" disabled>{{ loadingDirs ? 'Loading...' : '-- Select --' }}</option>
            <option v-for="d in directories" :key="d.id" :value="d.id">{{ d.displayName }}</option>
          </select>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Report Type</label>
          <select v-model="runForm.reportType" class="input w-full">
            <option v-for="t in reportTypes" :key="t.value" :value="t.value">{{ t.label }}</option>
          </select>
        </div>
        <div v-if="needsGroupDn">
          <label class="block text-sm font-medium text-gray-700 mb-1">Group DN (optional filter)</label>
          <GroupDnPicker v-model="runForm.groupDn" :directory-id="dirId" />
        </div>
        <div v-if="needsStatusFilter">
          <label class="block text-sm font-medium text-gray-700 mb-1">Status</label>
          <select v-model="runForm.statusFilter" class="input w-full">
            <option value="">All</option>
            <option value="OPEN">Open</option>
            <option value="RESOLVED">Resolved</option>
            <option value="EXEMPTED">Exempted</option>
          </select>
        </div>
        <div v-if="needsPolicyFilter">
          <label class="block text-sm font-medium text-gray-700 mb-1">SoD Policy</label>
          <select v-model="runForm.policyId" class="input w-full">
            <option value="">All Policies</option>
            <option v-for="p in sodPolicies" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
        </div>
        <div v-if="needsLookback">
          <label class="block text-sm font-medium text-gray-700 mb-1">Lookback Days</label>
          <input v-model.number="runForm.lookbackDays" type="number" min="1" class="input w-full" placeholder="30" />
        </div>
        <div v-if="needsCampaign">
          <label class="block text-sm font-medium text-gray-700 mb-1">Campaign</label>
          <select v-model="runForm.campaignId" class="input w-full">
            <option value="">-- select campaign --</option>
            <option v-for="c in campaigns" :key="c.id" :value="c.id">{{ c.name }} ({{ c.status }})</option>
          </select>
        </div>
      </div>
      <button @click="doRun" :disabled="running || !dirId" class="btn-primary">
        {{ running ? 'Running...' : 'Run Report' }}
      </button>
    </section>

    <!-- Results -->
    <section v-if="hasResults" class="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <div class="px-5 py-3 border-b border-gray-200 flex items-center justify-between">
        <span class="text-sm text-gray-600">{{ resultRows.length }} result{{ resultRows.length !== 1 ? 's' : '' }}</span>
        <div class="flex gap-2">
          <!-- SoD bulk actions -->
          <template v-if="isSodReport && selectedIds.size > 0">
            <button @click="bulkResolve" :disabled="actioning" class="bg-green-600 text-white text-xs font-medium px-3 py-1.5 rounded-lg hover:bg-green-700 disabled:opacity-50">
              Resolve ({{ selectedIds.size }})
            </button>
            <button @click="showExemptModal = true" :disabled="actioning" class="bg-amber-500 text-white text-xs font-medium px-3 py-1.5 rounded-lg hover:bg-amber-600 disabled:opacity-50">
              Exempt ({{ selectedIds.size }})
            </button>
          </template>
          <button @click="doExport('CSV')" :disabled="exporting" class="btn-secondary text-xs">Export CSV</button>
          <button @click="doExport('PDF')" :disabled="exporting" class="btn-secondary text-xs">Export PDF</button>
        </div>
      </div>

      <div v-if="resultRows.length === 0" class="p-8 text-center text-sm text-gray-400">
        No entries found for this report.
      </div>

      <div v-else class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-gray-200 bg-gray-50">
              <th v-if="isSodReport" class="py-2 px-3 w-8">
                <input type="checkbox" :checked="allPageSelected" @change="toggleSelectAll" class="rounded border-gray-300" />
              </th>
              <th v-for="col in visibleColumns" :key="col"
                @click="toggleSort(col)"
                class="text-left py-2 px-4 text-xs font-semibold text-gray-500 uppercase tracking-wider cursor-pointer hover:text-gray-700 select-none whitespace-nowrap">
                {{ col }}
                <span v-if="sortCol === col" class="ml-0.5">{{ sortAsc ? '&#9650;' : '&#9660;' }}</span>
              </th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-50">
            <tr v-for="(row, i) in pagedRows" :key="i" class="hover:bg-blue-50/30">
              <td v-if="isSodReport" class="py-2 px-3 w-8">
                <input type="checkbox" :checked="selectedIds.has(row['id'])" @change="toggleSelect(row['id'])" class="rounded border-gray-300" />
              </td>
              <td v-for="col in visibleColumns" :key="col"
                class="py-2 px-4 text-xs text-gray-700 break-all max-w-xs truncate" :title="row[col]">
                <span v-if="col === 'Status'" :class="statusBadgeClass(row[col])" class="inline-block px-2 py-0.5 rounded-full text-xs font-medium">
                  {{ row[col] }}
                </span>
                <span v-else-if="col === 'Severity'" :class="severityBadgeClass(row[col])" class="inline-block px-2 py-0.5 rounded-full text-xs font-medium">
                  {{ row[col] }}
                </span>
                <span v-else class="font-mono">{{ row[col] }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="px-5 py-3 border-t border-gray-200 flex items-center justify-between">
        <span class="text-xs text-gray-500">Page {{ page + 1 }} of {{ totalPages }}</span>
        <div class="flex gap-1">
          <button @click="page = Math.max(0, page - 1)" :disabled="page === 0" class="btn-secondary text-xs">Prev</button>
          <button @click="page = Math.min(totalPages - 1, page + 1)" :disabled="page >= totalPages - 1" class="btn-secondary text-xs">Next</button>
        </div>
      </div>
    </section>

    <!-- Exempt Modal -->
    <AppModal v-model="showExemptModal" title="Exempt Violations" size="md">
      <p class="text-sm text-gray-600 mb-4">Exempting {{ selectedIds.size }} violation{{ selectedIds.size !== 1 ? 's' : '' }}.</p>
      <div class="space-y-3">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Reason <span class="text-red-500">*</span></label>
          <textarea v-model="exemptReason" rows="3" class="input w-full" placeholder="Provide a reason for the exemption..."></textarea>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Expiration Date (optional)</label>
          <input v-model="exemptExpires" type="date" class="input w-full" />
        </div>
      </div>
      <div class="flex justify-end gap-2 mt-5">
        <button @click="showExemptModal = false" class="btn-secondary text-sm">Cancel</button>
        <button @click="bulkExempt" :disabled="!exemptReason.trim() || actioning" class="bg-amber-500 text-white text-sm font-medium px-4 py-2 rounded-lg hover:bg-amber-600 disabled:opacity-50">
          {{ actioning ? 'Processing...' : 'Exempt' }}
        </button>
      </div>
    </AppModal>

    <!-- Evidence Package Modal -->
    <AppModal v-model="showEvidence" title="Evidence Package" size="xl">
      <p class="text-sm text-gray-500 mb-5">
        Generate a comprehensive ZIP package containing all compliance artifacts: PDF reports,
        campaign decisions, SoD data, approval history, and user entitlements.
      </p>

      <div class="grid gap-6 md:grid-cols-2">
        <!-- Campaign selection -->
        <div>
          <label class="block text-xs font-semibold text-gray-600 uppercase tracking-wider mb-2">
            Include Campaigns
          </label>
          <div v-if="!campaigns.length" class="text-sm text-gray-400">No campaigns available.</div>
          <div v-else class="max-h-48 overflow-y-auto border border-gray-200 rounded-lg divide-y divide-gray-100">
            <label v-for="c in campaigns" :key="c.id"
                   class="flex items-center gap-3 px-3 py-2 hover:bg-gray-50 cursor-pointer">
              <input type="checkbox" :value="c.id" v-model="evidencePackageCampaignIds"
                     class="rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
              <div class="min-w-0">
                <span class="text-sm font-medium text-gray-900 block truncate">{{ c.name }}</span>
                <span class="text-xs text-gray-400">{{ c.status }}</span>
              </div>
            </label>
          </div>
        </div>

        <!-- Options -->
        <div class="space-y-4">
          <label class="block text-xs font-semibold text-gray-600 uppercase tracking-wider mb-2">
            Options
          </label>
          <label class="flex items-start gap-3 cursor-pointer">
            <input type="checkbox" v-model="evidenceIncludeSod"
                   class="mt-0.5 rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
            <div>
              <span class="text-sm font-medium text-gray-900">Include SoD Policies &amp; Violations</span>
              <p class="text-xs text-gray-500">Exports all separation-of-duties policy definitions and open violations.</p>
            </div>
          </label>
          <label class="flex items-start gap-3 cursor-pointer">
            <input type="checkbox" v-model="evidenceIncludeEntitlements"
                   class="mt-0.5 rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
            <div>
              <span class="text-sm font-medium text-gray-900">Include Entitlement Snapshot</span>
              <p class="text-xs text-gray-500">Exports a point-in-time snapshot of all users and their group memberships from LDAP.</p>
            </div>
          </label>

          <button @click="downloadEvidencePackage"
                  :disabled="loadingEvidence || evidencePackageCampaignIds.length === 0"
                  class="w-full bg-green-600 text-white text-sm font-medium rounded-lg px-4 py-2.5 hover:bg-green-700 disabled:opacity-50 flex items-center justify-center gap-2">
            <svg v-if="loadingEvidence" class="animate-spin h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
            </svg>
            {{ loadingEvidence ? 'Generating package...' : 'Download Evidence Package (ZIP)' }}
          </button>
        </div>
      </div>

      <!-- Success toast -->
      <div v-if="evidenceSuccess" class="mt-4 bg-green-50 border border-green-200 text-green-700 rounded-lg px-4 py-3 text-sm flex items-center gap-2">
        <svg class="w-4 h-4 shrink-0" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"></path></svg>
        Evidence package downloaded successfully ({{ evidenceFileSize }}).
      </div>
    </AppModal>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import { runReport, runReportData } from '@/api/reports'
import { listDirectories } from '@/api/directories'
import { listCampaigns } from '@/api/accessReviews'
import { listPolicies, exemptViolation, resolveViolation } from '@/api/sodPolicies'
import { generateEvidencePackage } from '@/api/complianceReports'
import { downloadBlob } from '@/composables/useApi'
import GroupDnPicker from '@/components/GroupDnPicker.vue'
import AppModal from '@/components/AppModal.vue'

const route = useRoute()
const notif = useNotificationStore()
const routeDirId = route.params.dirId

const directories = ref([])
const loadingDirs = ref(false)
const selectedDir = ref('')
const dirId = computed(() => routeDirId || selectedDir.value)

const PAGE_SIZE = 50

const reportTypes = [
  { value: 'USER_ACCESS_REPORT',          label: 'User Access Report',           lookback: false, statusFilter: false, groupDn: true,  campaign: false, policyFilter: false },
  { value: 'ACCESS_REVIEW_SUMMARY',       label: 'Access Review Summary',        lookback: false, statusFilter: false, groupDn: false, campaign: true,  policyFilter: false },
  { value: 'PRIVILEGED_ACCOUNT_INVENTORY', label: 'Privileged Account Inventory', lookback: false, statusFilter: false, groupDn: false, campaign: false, policyFilter: false },
  { value: 'ACCESS_DRIFT_REPORT',         label: 'Access Drift',                 lookback: false, statusFilter: false, groupDn: false, campaign: false, policyFilter: false },
  { value: 'SOD_VIOLATIONS',              label: 'SoD Violations',               lookback: false, statusFilter: true,  groupDn: false, campaign: false, policyFilter: true },
  { value: 'AUDIT_LOG_REPORT',            label: 'Audit Log',                    lookback: true,  statusFilter: false, groupDn: false, campaign: false, policyFilter: false },
]

const runForm = ref({
  reportType: 'USER_ACCESS_REPORT', groupDn: '', statusFilter: '', lookbackDays: 30, campaignId: '', policyId: '',
})
const running = ref(false)
const exporting = ref(false)
const hasResults = ref(false)
const resultColumns = ref([])
const resultRows = ref([])
const sortCol = ref('')
const sortAsc = ref(true)
const page = ref(0)

const currentType       = computed(() => reportTypes.find(t => t.value === runForm.value.reportType))
const needsLookback     = computed(() => !!currentType.value?.lookback)
const needsStatusFilter = computed(() => !!currentType.value?.statusFilter)
const needsGroupDn      = computed(() => !!currentType.value?.groupDn)
const needsCampaign     = computed(() => !!currentType.value?.campaign)
const needsPolicyFilter = computed(() => !!currentType.value?.policyFilter)
const isSodReport       = computed(() => runForm.value.reportType === 'SOD_VIOLATIONS' && hasResults.value)

// Hide the 'id' column from visible display (used internally for selection)
const visibleColumns = computed(() => resultColumns.value.filter(c => c !== 'id').slice(0, 10))

const sortedRows = computed(() => {
  if (!sortCol.value) return resultRows.value
  const col = sortCol.value
  const dir = sortAsc.value ? 1 : -1
  return [...resultRows.value].sort((a, b) => (a[col] || '').localeCompare(b[col] || '') * dir)
})

const totalPages = computed(() => Math.ceil(sortedRows.value.length / PAGE_SIZE))
const pagedRows = computed(() => sortedRows.value.slice(page.value * PAGE_SIZE, (page.value + 1) * PAGE_SIZE))

// SoD policies for filter
const sodPolicies = ref([])

// Campaigns (for Access Review Summary + Evidence Package)
const campaigns = ref([])

// ── Selection state (SoD) ─────────────────────────────────────────────────────
const selectedIds = ref(new Set())

const allPageSelected = computed(() => {
  if (!pagedRows.value.length) return false
  return pagedRows.value.every(r => selectedIds.value.has(r['id']))
})

function toggleSelect(id) {
  const s = new Set(selectedIds.value)
  if (s.has(id)) s.delete(id); else s.add(id)
  selectedIds.value = s
}

function toggleSelectAll() {
  const s = new Set(selectedIds.value)
  if (allPageSelected.value) {
    pagedRows.value.forEach(r => s.delete(r['id']))
  } else {
    pagedRows.value.forEach(r => s.add(r['id']))
  }
  selectedIds.value = s
}

// ── Bulk actions ──────────────────────────────────────────────────────────────
const actioning = ref(false)
const showExemptModal = ref(false)
const exemptReason = ref('')
const exemptExpires = ref('')

async function bulkResolve() {
  if (!selectedIds.value.size) return
  actioning.value = true
  let ok = 0, fail = 0
  for (const vid of selectedIds.value) {
    try {
      await resolveViolation(dirId.value, vid)
      ok++
    } catch { fail++ }
  }
  actioning.value = false
  notif.success(`Resolved ${ok} violation${ok !== 1 ? 's' : ''}${fail ? `, ${fail} failed` : ''}`)
  selectedIds.value = new Set()
  await doRun()
}

async function bulkExempt() {
  if (!selectedIds.value.size || !exemptReason.value.trim()) return
  actioning.value = true
  const body = {
    reason: exemptReason.value.trim(),
    expiresAt: exemptExpires.value ? new Date(exemptExpires.value).toISOString() : null,
  }
  let ok = 0, fail = 0
  for (const vid of selectedIds.value) {
    try {
      await exemptViolation(dirId.value, vid, body)
      ok++
    } catch { fail++ }
  }
  actioning.value = false
  showExemptModal.value = false
  exemptReason.value = ''
  exemptExpires.value = ''
  notif.success(`Exempted ${ok} violation${ok !== 1 ? 's' : ''}${fail ? `, ${fail} failed` : ''}`)
  selectedIds.value = new Set()
  await doRun()
}

// ── Status / Severity badges ──────────────────────────────────────────────────
function statusBadgeClass(status) {
  switch (status) {
    case 'OPEN':     return 'bg-red-100 text-red-700'
    case 'RESOLVED': return 'bg-green-100 text-green-700'
    case 'EXEMPTED': return 'bg-amber-100 text-amber-700'
    default:         return 'bg-gray-100 text-gray-700'
  }
}

function severityBadgeClass(severity) {
  switch (severity) {
    case 'CRITICAL': return 'bg-red-100 text-red-700'
    case 'HIGH':     return 'bg-orange-100 text-orange-700'
    case 'MEDIUM':   return 'bg-yellow-100 text-yellow-700'
    case 'LOW':      return 'bg-blue-100 text-blue-700'
    default:         return 'bg-gray-100 text-gray-700'
  }
}

// ── Sorting / params ──────────────────────────────────────────────────────────

function toggleSort(col) {
  if (sortCol.value === col) { sortAsc.value = !sortAsc.value }
  else { sortCol.value = col; sortAsc.value = true }
  page.value = 0
}

function buildParams() {
  const params = {}
  if (needsLookback.value) params.lookbackDays = runForm.value.lookbackDays || 30
  if (needsGroupDn.value && runForm.value.groupDn) params.groupDn = runForm.value.groupDn
  if (needsStatusFilter.value && runForm.value.statusFilter) params.status = runForm.value.statusFilter
  if (needsCampaign.value && runForm.value.campaignId) params.campaignId = runForm.value.campaignId
  if (needsPolicyFilter.value && runForm.value.policyId) params.policyId = runForm.value.policyId
  return params
}

async function doRun() {
  if (!dirId.value) { notif.error('Please select a directory.'); return }
  running.value = true
  hasResults.value = false
  selectedIds.value = new Set()
  try {
    const { data } = await runReportData(dirId.value, {
      reportType: runForm.value.reportType,
      reportParams: buildParams(),
    })
    resultColumns.value = data.columns || []
    resultRows.value = data.rows || []
    hasResults.value = true
    sortCol.value = ''
    page.value = 0
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    running.value = false
  }
}

async function doExport(format) {
  if (!dirId.value) return
  exporting.value = true
  try {
    const { data } = await runReport(dirId.value, {
      reportType: runForm.value.reportType,
      reportParams: buildParams(),
      outputFormat: format,
    })
    const ext = format === 'PDF' ? '.pdf' : '.csv'
    downloadBlob(data, `${runForm.value.reportType.toLowerCase()}${ext}`)
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    exporting.value = false
  }
}

// ── Evidence Package ─────────────────────────────────────────────────────────

const showEvidence = ref(false)
const loadingEvidence = ref(false)
const evidencePackageCampaignIds = ref([])
const evidenceIncludeSod = ref(true)
const evidenceIncludeEntitlements = ref(false)
const evidenceSuccess = ref(false)
const evidenceFileSize = ref('')

function openEvidencePackage() {
  showEvidence.value = true
}

function formatBytes(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function triggerDownload(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

async function downloadEvidencePackage() {
  loadingEvidence.value = true
  evidenceSuccess.value = false
  try {
    const body = {
      campaignIds: evidencePackageCampaignIds.value,
      includeSod: evidenceIncludeSod.value,
      includeEntitlements: evidenceIncludeEntitlements.value,
    }
    const { data } = await generateEvidencePackage(dirId.value, body)
    const today = new Date().toISOString().slice(0, 10)
    triggerDownload(data, `evidence-package-${today}.zip`)
    evidenceFileSize.value = formatBytes(data.size)
    evidenceSuccess.value = true
    setTimeout(() => { evidenceSuccess.value = false }, 10000)
  } catch (e) {
    if (e.response?.status === 429) {
      notif.error('An evidence package is already being generated. Please wait and try again.')
    } else {
      notif.error('Failed to generate Evidence Package: ' + (e.response?.data?.message || e.message))
    }
  } finally {
    loadingEvidence.value = false
  }
}

// ── Init ──────────────────────────────────────────────────────────────────────

async function loadCampaigns() {
  if (!dirId.value) return
  try {
    const { data } = await listCampaigns(dirId.value, { size: 100 })
    campaigns.value = data.content || data
  } catch (e) {
    console.warn('Failed to load campaigns:', e)
  }
}

async function loadSodPolicies() {
  if (!dirId.value) return
  try {
    const { data } = await listPolicies(dirId.value)
    sodPolicies.value = data.content || data
  } catch (e) {
    console.warn('Failed to load SoD policies:', e)
  }
}

// Reload policies/campaigns when directory changes
watch(dirId, () => {
  loadCampaigns()
  loadSodPolicies()
})

onMounted(async () => {
  if (!routeDirId) {
    loadingDirs.value = true
    try {
      const { data } = await listDirectories()
      directories.value = data
      if (data.length === 1) selectedDir.value = data[0].id
    } catch (e) {
      notif.error(e.response?.data?.detail || e.message)
    } finally {
      loadingDirs.value = false
    }
  }
  await Promise.all([loadCampaigns(), loadSodPolicies()])
})
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary   { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
.btn-secondary { @apply px-3 py-1.5 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50 disabled:opacity-50; }
.input         { @apply border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
</style>
