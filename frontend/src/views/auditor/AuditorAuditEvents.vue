<template>
  <div>
    <div class="flex items-center justify-between mb-4">
      <h1 class="text-xl font-bold text-slate-900">Audit Log</h1>
      <div class="flex items-center gap-2">
        <ExportDropdown v-if="!loading" :options="exportOptions" />
        <button @click="viewMode = 'table'" class="view-btn" :class="{ active: viewMode === 'table' }">Table</button>
        <button @click="viewMode = 'timeline'" class="view-btn" :class="{ active: viewMode === 'timeline' }">Timeline</button>
      </div>
    </div>

    <SkeletonLoader v-if="loading" :rows="5" />
    <ErrorCard v-else-if="error" title="Failed to load audit events" @retry="load" />

    <template v-else>
      <!-- Filters -->
      <div class="bg-white border border-slate-200 rounded-xl p-4 mb-4 grid grid-cols-1 sm:grid-cols-3 gap-3">
        <input v-model="search" type="text" placeholder="Search actor, target..."
               class="input-sm" />
        <select v-model="actionFilter" class="input-sm">
          <option value="">All Actions</option>
          <option v-for="a in uniqueActions" :key="a" :value="a">{{ humanize(a) }}</option>
        </select>
        <input v-model="dateFilter" type="date" class="input-sm" />
      </div>

      <div v-if="filtered.length === 0" class="bg-slate-50 border border-slate-200 rounded-xl p-8 text-center">
        <p class="text-sm text-slate-500">
          {{ events.length === 0 ? 'No audit events available.' : 'No audit events match the current filters.' }}
        </p>
      </div>

      <!-- Table view -->
      <section v-if="viewMode === 'table' && filtered.length > 0"
               class="bg-white border border-slate-200 rounded-xl overflow-hidden">
        <div class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-slate-200 bg-slate-50">
                <th @click="toggleSort('occurredAt')" class="th-sort">When <span v-if="sortCol === 'occurredAt'" class="ml-0.5 text-[10px]">{{ sortAsc ? '▲' : '▼' }}</span></th>
                <th @click="toggleSort('actorUsername')" class="th-sort">Actor <span v-if="sortCol === 'actorUsername'" class="ml-0.5 text-[10px]">{{ sortAsc ? '▲' : '▼' }}</span></th>
                <th @click="toggleSort('action')" class="th-sort">Action <span v-if="sortCol === 'action'" class="ml-0.5 text-[10px]">{{ sortAsc ? '▲' : '▼' }}</span></th>
                <th class="th-sort">Target</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="e in paged" :key="e.id" class="border-b border-slate-100 hover:bg-slate-50/50">
                <td class="py-2 px-4 text-xs text-slate-500 font-mono whitespace-nowrap">{{ formatDateTime(e.occurredAt) }}</td>
                <td class="py-2 px-4 text-xs text-slate-700">{{ e.actorUsername || '—' }}</td>
                <td class="py-2 px-4">
                  <span class="text-xs font-medium px-2 py-0.5 rounded-full" :class="actionClass(e.action)">{{ humanize(e.action) }}</span>
                </td>
                <td class="py-2 px-4 text-xs text-slate-500 font-mono truncate max-w-xs" :title="e.targetDn">{{ e.targetDn || '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <!-- Pagination -->
        <div v-if="totalPages > 1" class="px-4 py-3 border-t border-slate-200 flex items-center justify-between">
          <span class="text-xs text-slate-500">Page {{ page + 1 }} of {{ totalPages }} ({{ filtered.length }} events)</span>
          <div class="flex gap-1">
            <button @click="page = Math.max(0, page - 1)" :disabled="page === 0" class="btn-sm">Prev</button>
            <button @click="page = Math.min(totalPages - 1, page + 1)" :disabled="page >= totalPages - 1" class="btn-sm">Next</button>
          </div>
        </div>
      </section>

      <!-- Timeline view -->
      <section v-if="viewMode === 'timeline' && filtered.length > 0"
               class="bg-white border border-slate-200 rounded-xl p-5">
        <div class="relative pl-6 border-l-2 border-slate-200 space-y-4">
          <div v-for="e in paged" :key="e.id" class="relative">
            <div class="absolute -left-[25px] w-3 h-3 rounded-full border-2"
                 :class="timelineDotClass(e.action)" />
            <div class="flex items-baseline gap-2">
              <span class="text-xs font-medium px-2 py-0.5 rounded-full" :class="actionClass(e.action)">{{ humanize(e.action) }}</span>
              <span v-if="e.actorUsername" class="text-xs text-slate-500">by {{ e.actorUsername }}</span>
            </div>
            <div v-if="e.targetDn" class="text-[10px] text-slate-400 font-mono truncate" :title="e.targetDn">{{ e.targetDn }}</div>
            <div class="text-[10px] text-slate-400 font-mono mt-0.5">{{ formatDateTime(e.occurredAt) }}</div>
          </div>
        </div>
        <!-- Timeline pagination -->
        <div v-if="totalPages > 1" class="mt-4 flex items-center justify-between">
          <span class="text-xs text-slate-500">Page {{ page + 1 }} of {{ totalPages }}</span>
          <div class="flex gap-1">
            <button @click="page = Math.max(0, page - 1)" :disabled="page === 0" class="btn-sm">Prev</button>
            <button @click="page = Math.min(totalPages - 1, page + 1)" :disabled="page >= totalPages - 1" class="btn-sm">Next</button>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { getPortalAuditEvents, exportAuditEventsCsv, exportAuditEventsPdf } from '@/api/auditorPortal'
import ExportDropdown from './components/ExportDropdown.vue'
import SkeletonLoader from './components/SkeletonLoader.vue'
import ErrorCard from './components/ErrorCard.vue'

const props = defineProps({ token: String, metadata: Object, scope: Object })

const loading = ref(true)
const events = ref([])
const viewMode = ref('table')
const search = ref('')
const actionFilter = ref('')
const dateFilter = ref('')
const sortCol = ref('occurredAt')
const sortAsc = ref(false)
const page = ref(0)
const PAGE_SIZE = 50
const error = ref(false)

const exportOptions = [
  { label: 'Export CSV', filename: 'audit-events.csv', fn: () => exportAuditEventsCsv(props.token) },
  { label: 'Export PDF', filename: 'audit-events.pdf', fn: () => exportAuditEventsPdf(props.token) },
]

const uniqueActions = computed(() => [...new Set(events.value.map(e => e.action).filter(Boolean))].sort())

const filtered = computed(() => {
  let result = events.value
  const q = search.value.toLowerCase()
  if (q) {
    result = result.filter(e =>
      (e.actorUsername || '').toLowerCase().includes(q) ||
      (e.targetDn || '').toLowerCase().includes(q) ||
      (e.action || '').toLowerCase().includes(q)
    )
  }
  if (actionFilter.value) {
    result = result.filter(e => e.action === actionFilter.value)
  }
  if (dateFilter.value) {
    const day = dateFilter.value
    result = result.filter(e => e.occurredAt && e.occurredAt.startsWith(day))
  }
  return result
})

const sorted = computed(() => {
  if (!sortCol.value) return filtered.value
  const col = sortCol.value
  const dir = sortAsc.value ? 1 : -1
  return [...filtered.value].sort((a, b) => {
    const va = a[col] || ''
    const vb = b[col] || ''
    return va < vb ? -dir : va > vb ? dir : 0
  })
})

const totalPages = computed(() => Math.ceil(sorted.value.length / PAGE_SIZE))
const paged = computed(() => sorted.value.slice(page.value * PAGE_SIZE, (page.value + 1) * PAGE_SIZE))

watch([search, actionFilter, dateFilter], () => { page.value = 0 })

function toggleSort(col) {
  if (sortCol.value === col) { sortAsc.value = !sortAsc.value }
  else { sortCol.value = col; sortAsc.value = true }
  page.value = 0
}

function humanize(action) {
  if (!action) return '—'
  return action.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
}

function formatDateTime(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

function actionClass(action) {
  if (!action) return 'bg-slate-100 text-slate-600'
  if (action.startsWith('USER_CREATE') || action.startsWith('GROUP_CREATE')) return 'bg-green-100 text-green-700'
  if (action.includes('DELETE') || action.includes('REVOKE')) return 'bg-red-100 text-red-700'
  if (action.includes('UPDATE') || action.includes('EDIT')) return 'bg-blue-100 text-blue-700'
  if (action.includes('APPROVE')) return 'bg-green-100 text-green-700'
  if (action.includes('REJECT')) return 'bg-red-100 text-red-700'
  if (action.includes('SOD') || action.includes('VIOLATION')) return 'bg-amber-100 text-amber-700'
  return 'bg-slate-100 text-slate-600'
}

function timelineDotClass(action) {
  if (!action) return 'border-slate-300 bg-white'
  if (action.includes('DELETE') || action.includes('REVOKE') || action.includes('REJECT')) return 'border-red-400 bg-red-50'
  if (action.includes('CREATE') || action.includes('APPROVE')) return 'border-green-400 bg-green-50'
  if (action.includes('SOD') || action.includes('VIOLATION')) return 'border-amber-400 bg-amber-50'
  return 'border-slate-300 bg-white'
}

async function load() {
  loading.value = true
  error.value = false
  try {
    const { data } = await getPortalAuditEvents(props.token)
    events.value = data
  } catch { error.value = true }
  loading.value = false
}
onMounted(load)
</script>

<style scoped>
@reference "tailwindcss";
.view-btn { @apply px-3 py-1.5 text-xs rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-50 transition-colors; }
.view-btn.active { @apply bg-slate-100 text-slate-900 font-medium; }
.th-sort { @apply text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider cursor-pointer hover:text-slate-700 select-none whitespace-nowrap; }
</style>
