<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Operational Reports</h1>
      <button @click="openSchedules" class="btn-secondary flex items-center gap-1.5">
        <svg class="w-4 h-4" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="10" cy="10" r="7"/><path d="M10 6v4l2.5 2.5"/></svg>
        Scheduled Jobs
      </button>
    </div>

    <!-- Report runner -->
    <section class="bg-white border border-gray-200 rounded-xl p-5 mb-6">
      <div class="grid grid-cols-3 gap-3 mb-3">
        <!-- Directory picker (superadmin only — when no dirId from route) -->
        <div v-if="!routeDirId">
          <label class="block text-sm font-medium text-gray-700 mb-1">Directory</label>
          <select v-model="selectedDir" class="input w-full">
            <option value="" disabled>{{ loadingDirs ? 'Loading…' : '— Select —' }}</option>
            <option v-for="d in directories" :key="d.id" :value="d.id">{{ d.displayName }}</option>
          </select>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Report Type</label>
          <select v-model="runForm.reportType" class="input w-full">
            <option v-for="t in reportTypes" :key="t.value" :value="t.value">{{ t.label }}</option>
          </select>
        </div>
        <div v-if="needsParam">
          <label class="block text-sm font-medium text-gray-700 mb-1">{{ paramLabel }}</label>
          <GroupDnPicker v-if="currentRunType?.param === 'groupDn'" v-model="runForm.paramValue" :directory-id="dirId" />
          <DnPicker v-else-if="currentRunType?.param === 'branchDn'" v-model="runForm.paramValue" :directory-id="dirId" />
          <input v-else v-model="runForm.paramValue" type="text" :placeholder="paramPlaceholder" class="input w-full" />
        </div>
        <div v-if="needsLookback">
          <label class="block text-sm font-medium text-gray-700 mb-1">Lookback Days</label>
          <input v-model.number="runForm.lookbackDays" type="number" min="1" class="input w-full" placeholder="30" />
        </div>
        <!-- Integrity check options -->
        <div v-if="isIntegrityCheck">
          <label class="block text-sm font-medium text-gray-700 mb-1">Checks to Run</label>
          <div class="flex flex-col gap-1.5 mt-1">
            <label v-for="c in integrityChecks" :key="c.value" class="flex items-center gap-2 text-sm text-gray-700">
              <input type="checkbox" v-model="runForm.integrityChecks" :value="c.value" class="rounded border-gray-300" />
              {{ c.label }}
            </label>
          </div>
        </div>
      </div>
      <button @click="doRun" :disabled="running || !dirId" class="btn-primary">
        {{ running ? 'Running…' : 'Run Report' }}
      </button>
    </section>

    <!-- Results -->
    <section v-if="hasResults" class="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <div class="px-5 py-3 border-b border-gray-200 flex items-center justify-between">
        <span class="text-sm text-gray-600">{{ resultRows.length }} result{{ resultRows.length !== 1 ? 's' : '' }}</span>
        <div class="flex gap-2">
          <button @click="doExport('CSV')" :disabled="exporting" class="btn-secondary text-xs">Export CSV</button>
          <button @click="doExport('PDF')" :disabled="exporting || isIntegrityCheck" class="btn-secondary text-xs">Export PDF</button>
        </div>
      </div>

      <div v-if="resultRows.length === 0" class="p-8 text-center text-sm text-gray-400">
        No entries found for this report.
      </div>

      <div v-else class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-gray-200 bg-gray-50">
              <th v-for="col in visibleColumns" :key="col"
                @click="toggleSort(col)"
                class="text-left py-2 px-4 text-xs font-semibold text-gray-500 uppercase tracking-wider cursor-pointer hover:text-gray-700 select-none whitespace-nowrap">
                {{ col }}
                <span v-if="sortCol === col" class="ml-0.5">{{ sortAsc ? '▲' : '▼' }}</span>
              </th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-50">
            <tr v-for="(row, i) in pagedRows" :key="i" class="hover:bg-blue-50/30">
              <td v-for="col in visibleColumns" :key="col"
                class="py-2 px-4 font-mono text-xs text-gray-700 break-all max-w-xs truncate" :title="row[col]">
                {{ row[col] }}
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

    <!-- Scheduled Jobs Modal -->
    <AppModal v-model="showSchedules" title="Scheduled Jobs" size="xl">
      <div class="space-y-4">
        <!-- Jobs list -->
        <div v-if="loadingJobs" class="text-gray-500 text-sm text-center py-4">Loading…</div>
        <div v-else-if="jobs.length === 0" class="text-gray-400 text-sm text-center py-4">No scheduled jobs.</div>
        <table v-else class="w-full text-sm">
          <thead class="bg-gray-50 border-b border-gray-100">
            <tr>
              <th class="px-3 py-2 text-left font-medium text-gray-500">Name</th>
              <th class="px-3 py-2 text-left font-medium text-gray-500">Type</th>
              <th class="px-3 py-2 text-left font-medium text-gray-500">Schedule</th>
              <th class="px-3 py-2 text-left font-medium text-gray-500">Format</th>
              <th class="px-3 py-2 text-left font-medium text-gray-500">Delivery</th>
              <th class="px-3 py-2 text-left font-medium text-gray-500">Last Run</th>
              <th class="px-3 py-2 text-left font-medium text-gray-500">On</th>
              <th class="px-3 py-2"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-50">
            <tr v-for="job in jobs" :key="job.id" class="hover:bg-gray-50">
              <td class="px-3 py-2 font-medium text-gray-900">{{ job.name }}</td>
              <td class="px-3 py-2 text-gray-600 text-xs">{{ labelFor(job.reportType) }}</td>
              <td class="px-3 py-2 text-gray-600 font-mono text-xs">{{ job.cronExpression }}</td>
              <td class="px-3 py-2 text-gray-600 text-xs">{{ job.outputFormat || 'CSV' }}</td>
              <td class="px-3 py-2 text-gray-600 text-xs">{{ job.deliveryMethod }}</td>
              <td class="px-3 py-2 text-gray-500 text-xs">
                <span v-if="job.lastRunAt">{{ fmtDate(job.lastRunAt) }}</span>
                <span v-else class="text-gray-300">—</span>
                <span v-if="job.lastRunStatus" :class="job.lastRunStatus === 'SUCCESS' ? 'text-green-600' : 'text-red-500'" class="ml-1 text-xs">({{ job.lastRunStatus }})</span>
              </td>
              <td class="px-3 py-2">
                <button @click="toggleEnabled(job)" :class="job.enabled ? 'bg-green-500' : 'bg-gray-300'" class="relative inline-flex h-4 w-7 rounded-full transition-colors">
                  <span :class="job.enabled ? 'translate-x-3' : 'translate-x-0'" class="inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform scale-75"></span>
                </button>
              </td>
              <td class="px-3 py-2 text-right whitespace-nowrap">
                <button @click="openEditJob(job)" class="text-blue-600 hover:text-blue-800 text-xs font-medium mr-1">Edit</button>
                <button @click="confirmDelete(job)" class="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>

        <!-- Add/Edit form -->
        <details :open="showJobForm" class="border border-gray-200 rounded-lg">
          <summary @click.prevent="showJobForm = !showJobForm" class="px-4 py-2 text-sm font-medium text-gray-700 cursor-pointer">
            {{ editJob ? 'Edit Job' : '+ Add Scheduled Job' }}
          </summary>
          <form v-if="showJobForm" @submit.prevent="saveJob" class="px-4 pb-4 pt-2 space-y-3">
            <div class="grid grid-cols-2 gap-3">
              <FormField label="Name" v-model="jobForm.name" required />
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Report Type</label>
                <select v-model="jobForm.reportType" class="input w-full" required>
                  <option v-for="t in schedulableTypes" :key="t.value" :value="t.value">{{ t.label }}</option>
                </select>
              </div>
            </div>
            <div class="grid grid-cols-3 gap-3">
              <FormField label="Cron Expression" v-model="jobForm.cronExpression" placeholder="0 8 * * 1" required />
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Output Format</label>
                <select v-model="jobForm.outputFormat" class="input w-full">
                  <option value="CSV">CSV</option>
                  <option value="PDF">PDF</option>
                </select>
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Delivery</label>
                <select v-model="jobForm.deliveryMethod" class="input w-full">
                  <option value="EMAIL">Email</option>
                  <option value="S3">S3</option>
                </select>
              </div>
            </div>
            <div v-if="jobForm.deliveryMethod === 'EMAIL'" class="grid grid-cols-2 gap-3">
              <FormField label="Recipient Email" v-model="jobForm.recipientEmail" placeholder="user@example.com" />
              <FormField label="Email Subject" v-model="jobForm.emailSubject" placeholder="Scheduled report" />
            </div>
            <div v-if="jobForm.deliveryMethod === 'S3'">
              <FormField label="S3 Key Prefix" v-model="jobForm.s3KeyPrefix" placeholder="reports/" />
            </div>
            <div v-if="jobFormNeedsParam">
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ jobFormParamLabel }}</label>
              <GroupDnPicker v-if="currentJobFormType?.param === 'groupDn'" v-model="jobForm.paramValue" :directory-id="dirId" />
              <DnPicker v-else-if="currentJobFormType?.param === 'branchDn'" v-model="jobForm.paramValue" :directory-id="dirId" />
              <FormField v-else :label="jobFormParamLabel" v-model="jobForm.paramValue" />
            </div>
            <div v-if="jobFormNeedsLookback">
              <FormField label="Lookback Days" v-model.number="jobForm.lookbackDays" type="number" placeholder="30" />
            </div>
            <div class="flex items-center gap-2">
              <input type="checkbox" id="jobEnabled" v-model="jobForm.enabled" class="rounded" />
              <label for="jobEnabled" class="text-sm text-gray-700">Enabled</label>
            </div>
            <div class="flex gap-2 justify-end">
              <button type="button" @click="cancelJobForm" class="btn-secondary text-sm">Cancel</button>
              <button type="submit" :disabled="savingJob" class="btn-primary text-sm">{{ savingJob ? 'Saving…' : 'Save' }}</button>
            </div>
          </form>
        </details>
      </div>
    </AppModal>

    <!-- Delete confirm -->
    <ConfirmDialog v-if="deleteTarget" :message="`Delete job '${deleteTarget.name}'?`" @confirm="doDelete" @cancel="deleteTarget = null" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import {
  listReportJobs, createReportJob, updateReportJob,
  deleteReportJob, setReportJobEnabled, runReport, runReportData,
} from '@/api/reports'
import { listDirectories } from '@/api/directories'
import { checkIntegrity } from '@/api/browse'
import { downloadBlob } from '@/composables/useApi'
import FormField from '@/components/FormField.vue'
import AppModal from '@/components/AppModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import DnPicker from '@/components/DnPicker.vue'
import GroupDnPicker from '@/components/GroupDnPicker.vue'

const route = useRoute()
const notif = useNotificationStore()
const routeDirId = route.params.dirId

// Directory picker for superadmin (when no dirId from route)
const directories = ref([])
const loadingDirs = ref(false)
const selectedDir = ref('')

const dirId = computed(() => routeDirId || selectedDir.value)

const PAGE_SIZE = 50

const reportTypes = [
  { value: 'USERS_IN_GROUP',       label: 'Users in Group',         param: 'groupDn',  paramLabel: 'Group DN',  paramPlaceholder: 'cn=admins,dc=example,dc=com', lookback: false },
  { value: 'USERS_IN_BRANCH',      label: 'Users in Branch',        param: 'branchDn', paramLabel: 'Branch DN', paramPlaceholder: 'ou=people,dc=example,dc=com', lookback: false },
  { value: 'USERS_WITH_NO_GROUP',  label: 'Users with No Group',    param: null, lookback: false },
  { value: 'RECENTLY_ADDED',       label: 'Recently Added',         param: null, lookback: true },
  { value: 'RECENTLY_MODIFIED',    label: 'Recently Modified',      param: null, lookback: true },
  { value: 'RECENTLY_DELETED',     label: 'Recently Deleted',       param: null, lookback: true },
  { value: 'DISABLED_ACCOUNTS',    label: 'Disabled Accounts',      param: null, lookback: false },
  { value: 'MISSING_PROFILE_GROUPS', label: 'Missing Profile Groups', param: null, lookback: false },
  { value: 'INTEGRITY_CHECK',      label: 'Integrity Check',        param: null, lookback: false },
]

// Scheduled jobs can't use INTEGRITY_CHECK (it's not a backend report type)
const schedulableTypes = computed(() => reportTypes.filter(t => t.value !== 'INTEGRITY_CHECK'))

const integrityChecks = [
  { value: 'BROKEN_MEMBER',  label: 'Broken Member References' },
  { value: 'ORPHANED_ENTRY', label: 'Orphaned Entries' },
  { value: 'EMPTY_GROUP',    label: 'Empty Groups' },
]

function labelFor(type) { return reportTypes.find(t => t.value === type)?.label ?? type }
function fmtDate(iso) { return new Date(iso).toLocaleString() }

// ── Report runner ─────────────────────────────────────────────────────────────

const runForm = ref({
  reportType: 'RECENTLY_ADDED', paramValue: '', lookbackDays: 30,
  integrityChecks: ['BROKEN_MEMBER', 'ORPHANED_ENTRY', 'EMPTY_GROUP'],
})
const running = ref(false)
const exporting = ref(false)
const hasResults = ref(false)
const resultColumns = ref([])
const resultRows = ref([])
const sortCol = ref('')
const sortAsc = ref(true)
const page = ref(0)

const currentRunType   = computed(() => reportTypes.find(t => t.value === runForm.value.reportType))
const needsParam       = computed(() => !!currentRunType.value?.param)
const paramLabel       = computed(() => currentRunType.value?.paramLabel ?? '')
const paramPlaceholder = computed(() => currentRunType.value?.paramPlaceholder ?? '')
const needsLookback    = computed(() => !!currentRunType.value?.lookback)
const isIntegrityCheck = computed(() => runForm.value.reportType === 'INTEGRITY_CHECK')

// Show max 10 columns in table (dn + 9 attributes)
const visibleColumns = computed(() => resultColumns.value.slice(0, 10))

const sortedRows = computed(() => {
  if (!sortCol.value) return resultRows.value
  const col = sortCol.value
  const dir = sortAsc.value ? 1 : -1
  return [...resultRows.value].sort((a, b) => (a[col] || '').localeCompare(b[col] || '') * dir)
})

const totalPages = computed(() => Math.ceil(sortedRows.value.length / PAGE_SIZE))
const pagedRows = computed(() => sortedRows.value.slice(page.value * PAGE_SIZE, (page.value + 1) * PAGE_SIZE))

function toggleSort(col) {
  if (sortCol.value === col) { sortAsc.value = !sortAsc.value }
  else { sortCol.value = col; sortAsc.value = true }
  page.value = 0
}

function buildReportParams() {
  const params = { lookbackDays: runForm.value.lookbackDays || 30 }
  if (currentRunType.value?.param) params[currentRunType.value.param] = runForm.value.paramValue
  return params
}

async function doRun() {
  if (!dirId.value) { notif.error('Please select a directory.'); return }
  running.value = true
  hasResults.value = false
  try {
    if (isIntegrityCheck.value) {
      await runIntegrityCheck()
    } else {
      const { data } = await runReportData(dirId.value, {
        reportType: runForm.value.reportType,
        reportParams: buildReportParams(),
      })
      resultColumns.value = data.columns || []
      resultRows.value = data.rows || []
    }
    hasResults.value = true
    sortCol.value = ''
    page.value = 0
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    running.value = false
  }
}

async function runIntegrityCheck() {
  const checks = runForm.value.integrityChecks
  if (!checks.length) { notif.error('Select at least one check.'); return }
  const { data } = await checkIntegrity(dirId.value, '', checks)
  const issues = data.issues || []
  resultColumns.value = ['type', 'dn', 'description']
  resultRows.value = issues.map(i => ({ type: i.type, dn: i.dn, description: i.description }))
}

async function doExport(format) {
  if (!dirId.value) return
  if (isIntegrityCheck.value) {
    // Export integrity results as CSV from current data
    const header = resultColumns.value.join(',')
    const rows = resultRows.value.map(r => resultColumns.value.map(c => `"${(r[c] || '').replace(/"/g, '""')}"`).join(','))
    const csv = [header, ...rows].join('\n')
    const blob = new Blob([csv], { type: 'text/csv' })
    downloadBlob(blob, 'integrity_check.csv')
    return
  }
  exporting.value = true
  try {
    const { data } = await runReport(dirId.value, {
      reportType: runForm.value.reportType,
      reportParams: buildReportParams(),
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

// ── Scheduled jobs ────────────────────────────────────────────────────────────

const showSchedules = ref(false)
const loadingJobs = ref(false)
const savingJob = ref(false)
const jobs = ref([])
const editJob = ref(null)
const deleteTarget = ref(null)
const showJobForm = ref(false)
const jobForm = ref(blankJobForm())

function blankJobForm() {
  return {
    name: '', reportType: 'RECENTLY_ADDED', cronExpression: '0 8 * * 1',
    outputFormat: 'CSV', deliveryMethod: 'EMAIL', recipientEmail: '',
    emailSubject: '', s3KeyPrefix: '', paramValue: '', lookbackDays: 30, enabled: true,
  }
}

const currentJobFormType   = computed(() => reportTypes.find(t => t.value === jobForm.value.reportType))
const jobFormNeedsParam    = computed(() => !!currentJobFormType.value?.param)
const jobFormParamLabel    = computed(() => currentJobFormType.value?.paramLabel ?? '')
const jobFormNeedsLookback = computed(() => !!currentJobFormType.value?.lookback)

async function openSchedules() {
  if (!dirId.value) { notif.error('Please select a directory first.'); return }
  showSchedules.value = true
  loadingJobs.value = true
  try {
    const { data } = await listReportJobs(dirId.value, { size: 50 })
    jobs.value = data.content ?? data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    loadingJobs.value = false
  }
}

function openEditJob(job) {
  editJob.value = job
  const typeInfo = reportTypes.find(t => t.value === job.reportType)
  jobForm.value = {
    name: job.name, reportType: job.reportType,
    cronExpression: job.cronExpression,
    outputFormat: job.outputFormat || 'CSV',
    deliveryMethod: job.deliveryMethod ?? 'EMAIL',
    recipientEmail: job.recipientEmail ?? '',
    emailSubject: job.emailSubject ?? '',
    s3KeyPrefix: job.s3KeyPrefix ?? '',
    paramValue: typeInfo?.param ? (job.reportParams?.[typeInfo.param] ?? '') : '',
    lookbackDays: job.reportParams?.lookbackDays ?? 30,
    enabled: job.enabled,
  }
  showJobForm.value = true
}

function cancelJobForm() {
  showJobForm.value = false
  editJob.value = null
  jobForm.value = blankJobForm()
}

function buildJobPayload() {
  const params = { lookbackDays: jobForm.value.lookbackDays || 30 }
  if (currentJobFormType.value?.param) params[currentJobFormType.value.param] = jobForm.value.paramValue
  return {
    name: jobForm.value.name,
    reportType: jobForm.value.reportType,
    reportParams: params,
    cronExpression: jobForm.value.cronExpression,
    outputFormat: jobForm.value.outputFormat,
    deliveryMethod: jobForm.value.deliveryMethod,
    recipientEmail: jobForm.value.deliveryMethod === 'EMAIL' ? jobForm.value.recipientEmail : null,
    emailSubject: jobForm.value.deliveryMethod === 'EMAIL' ? jobForm.value.emailSubject : null,
    s3KeyPrefix: jobForm.value.deliveryMethod === 'S3' ? jobForm.value.s3KeyPrefix : null,
    enabled: jobForm.value.enabled,
  }
}

async function saveJob() {
  savingJob.value = true
  try {
    const payload = buildJobPayload()
    if (editJob.value) {
      await updateReportJob(dirId.value, editJob.value.id, payload)
      notif.success('Job updated')
    } else {
      await createReportJob(dirId.value, payload)
      notif.success('Job created')
    }
    cancelJobForm()
    const { data } = await listReportJobs(dirId.value, { size: 50 })
    jobs.value = data.content ?? data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    savingJob.value = false
  }
}

async function toggleEnabled(job) {
  try {
    const { data } = await setReportJobEnabled(dirId.value, job.id, !job.enabled)
    job.enabled = data.enabled
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  }
}

function confirmDelete(job) { deleteTarget.value = job }

async function doDelete() {
  try {
    await deleteReportJob(dirId.value, deleteTarget.value.id)
    notif.success('Job deleted')
    deleteTarget.value = null
    const { data } = await listReportJobs(dirId.value, { size: 50 })
    jobs.value = data.content ?? data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
    deleteTarget.value = null
  }
}

// ── Init ──────────────────────────────────────────────────────────────────────

onMounted(async () => {
  if (!routeDirId) {
    // Superadmin mode — load directory list
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
})
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary   { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
.btn-secondary { @apply px-3 py-1.5 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50 disabled:opacity-50; }
.input         { @apply border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
</style>
