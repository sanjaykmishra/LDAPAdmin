<template>
  <div class="p-6">
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Audit Reports</h1>

    <!-- Report runner -->
    <section class="bg-white border border-gray-200 rounded-xl p-5 mb-6">
      <div class="grid grid-cols-2 gap-3 mb-3">
        <!-- Directory picker (superadmin — when no dirId from route) -->
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
        <div v-if="needsLookback">
          <label class="block text-sm font-medium text-gray-700 mb-1">Lookback Days</label>
          <input v-model.number="runForm.lookbackDays" type="number" min="1" class="input w-full" placeholder="30" />
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import { runReport, runReportData } from '@/api/reports'
import { listDirectories } from '@/api/directories'
import { downloadBlob } from '@/composables/useApi'
import GroupDnPicker from '@/components/GroupDnPicker.vue'

const route = useRoute()
const notif = useNotificationStore()
const routeDirId = route.params.dirId

const directories = ref([])
const loadingDirs = ref(false)
const selectedDir = ref('')
const dirId = computed(() => routeDirId || selectedDir.value)

const PAGE_SIZE = 50

const reportTypes = [
  { value: 'ACCESS_REVIEW_SUMMARY',       label: 'Access Review Summary',        lookback: false, statusFilter: false, groupDn: false },
  { value: 'PRIVILEGED_ACCOUNT_INVENTORY', label: 'Privileged Account Inventory', lookback: false, statusFilter: false, groupDn: true },
  { value: 'ACCESS_DRIFT_REPORT',         label: 'Access Drift',                 lookback: false, statusFilter: false, groupDn: false },
  { value: 'SOD_VIOLATIONS',              label: 'SoD Violations',               lookback: false, statusFilter: true,  groupDn: false },
  { value: 'AUDIT_LOG_REPORT',            label: 'Audit Log',                    lookback: true,  statusFilter: false, groupDn: false },
]

const runForm = ref({
  reportType: 'ACCESS_REVIEW_SUMMARY', groupDn: '', statusFilter: '', lookbackDays: 30,
})
const running = ref(false)
const exporting = ref(false)
const hasResults = ref(false)
const resultColumns = ref([])
const resultRows = ref([])
const sortCol = ref('')
const sortAsc = ref(true)
const page = ref(0)

const currentType      = computed(() => reportTypes.find(t => t.value === runForm.value.reportType))
const needsLookback    = computed(() => !!currentType.value?.lookback)
const needsStatusFilter = computed(() => !!currentType.value?.statusFilter)
const needsGroupDn     = computed(() => !!currentType.value?.groupDn)

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

function buildParams() {
  const params = {}
  if (needsLookback.value) params.lookbackDays = runForm.value.lookbackDays || 30
  if (needsGroupDn.value && runForm.value.groupDn) params.groupDn = runForm.value.groupDn
  if (needsStatusFilter.value && runForm.value.statusFilter) params.status = runForm.value.statusFilter
  return params
}

async function doRun() {
  if (!dirId.value) { notif.error('Please select a directory.'); return }
  running.value = true
  hasResults.value = false
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
})
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary   { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
.btn-secondary { @apply px-3 py-1.5 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50 disabled:opacity-50; }
.input         { @apply border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
</style>
