<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { RouterLink } from 'vue-router'
import { listAlerts, getAlertSummary, acknowledgeAlert, dismissAlert, resolveAlert } from '@/api/alerts'
import { listDirectories } from '@/api/directories'
import { useNotificationStore } from '@/stores/notifications'
import RelativeTime from '@/components/RelativeTime.vue'

const notif = useNotificationStore()
const loading = ref(true)
const summary = ref({ openCount: 0, acknowledgedCount: 0, criticalCount: 0, highCount: 0, mediumCount: 0, lowCount: 0 })
const alerts = ref([])
const directories = ref([])
const dirFilter = ref('')
const page = ref(0)
const totalPages = ref(1)
const statusFilter = ref('OPEN')
const selectedAlert = ref(null)
let refreshTimer = null

const severityIcon = {
  CRITICAL: { color: 'text-red-600 bg-red-50', label: 'Critical' },
  HIGH: { color: 'text-orange-600 bg-orange-50', label: 'High' },
  MEDIUM: { color: 'text-amber-600 bg-amber-50', label: 'Medium' },
  LOW: { color: 'text-gray-600 bg-gray-100', label: 'Low' },
}

function humanizeRuleType(type) {
  return (type || '').replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
}

async function loadData() {
  try {
    const [summaryRes, alertsRes] = await Promise.all([
      getAlertSummary(),
      listAlerts({
        status: statusFilter.value || undefined,
        directoryId: dirFilter.value || undefined,
        page: page.value,
        size: 20,
      }),
    ])
    summary.value = summaryRes.data
    alerts.value = alertsRes.data.content || []
    totalPages.value = alertsRes.data.totalPages || 1
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    loading.value = false
  }
}

async function doAction(action, alert) {
  try {
    if (action === 'acknowledge') await acknowledgeAlert(alert.id)
    else if (action === 'dismiss') await dismissAlert(alert.id)
    else if (action === 'resolve') await resolveAlert(alert.id)
    notif.success('Alert ' + action + 'd')
    await loadData()
    selectedAlert.value = null
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  }
}

function changeFilter(status) {
  statusFilter.value = status
  page.value = 0
  loadData()
}

function changeDirFilter(val) {
  dirFilter.value = val
  page.value = 0
  loadData()
}

onMounted(async () => {
  try { const { data } = await listDirectories(); directories.value = data } catch {}
  loadData()
  refreshTimer = setInterval(loadData, 30000)
})
onUnmounted(() => { if (refreshTimer) clearInterval(refreshTimer) })
</script>

<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Alerts</h1>
        <p class="text-sm text-gray-500 mt-1">Continuous access monitoring alerts and findings</p>
      </div>
      <RouterLink to="/superadmin/alert-rules" class="btn-secondary btn-sm">Configure Rules</RouterLink>
    </div>

    <!-- Summary cards -->
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
      <div class="bg-white border-2 rounded-xl p-5 shadow-sm cursor-pointer transition-shadow hover:shadow-md"
           :class="summary.criticalCount > 0 ? 'border-red-200 bg-red-50/30' : 'border-gray-200'"
           @click="changeFilter('OPEN')">
        <p class="text-sm font-medium text-gray-500">Critical</p>
        <p class="text-3xl font-bold" :class="summary.criticalCount > 0 ? 'text-red-600' : 'text-gray-300'">{{ summary.criticalCount }}</p>
      </div>
      <div class="bg-white border-2 rounded-xl p-5 shadow-sm cursor-pointer transition-shadow hover:shadow-md"
           :class="summary.highCount > 0 ? 'border-orange-200 bg-orange-50/30' : 'border-gray-200'"
           @click="changeFilter('OPEN')">
        <p class="text-sm font-medium text-gray-500">High</p>
        <p class="text-3xl font-bold" :class="summary.highCount > 0 ? 'text-orange-600' : 'text-gray-300'">{{ summary.highCount }}</p>
      </div>
      <div class="bg-white border-2 rounded-xl p-5 shadow-sm cursor-pointer transition-shadow hover:shadow-md"
           :class="summary.mediumCount > 0 ? 'border-amber-200 bg-amber-50/30' : 'border-gray-200'"
           @click="changeFilter('OPEN')">
        <p class="text-sm font-medium text-gray-500">Medium</p>
        <p class="text-3xl font-bold" :class="summary.mediumCount > 0 ? 'text-amber-600' : 'text-gray-300'">{{ summary.mediumCount }}</p>
      </div>
      <div class="bg-white border-2 rounded-xl p-5 shadow-sm cursor-pointer transition-shadow hover:shadow-md border-gray-200"
           @click="changeFilter('OPEN')">
        <p class="text-sm font-medium text-gray-500">Low</p>
        <p class="text-3xl font-bold" :class="summary.lowCount > 0 ? 'text-gray-600' : 'text-gray-300'">{{ summary.lowCount }}</p>
      </div>
    </div>

    <!-- Filters -->
    <div class="flex items-center justify-between mb-4 border-b">
      <div class="flex gap-1">
        <button v-for="s in ['OPEN', 'ACKNOWLEDGED', null]" :key="s || 'all'"
                :class="['px-4 py-2 text-sm font-medium border-b-2 -mb-px',
                  statusFilter === s ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700']"
                @click="changeFilter(s)">
          {{ s === 'OPEN' ? 'Open (' + summary.openCount + ')' : s === 'ACKNOWLEDGED' ? 'Acknowledged (' + summary.acknowledgedCount + ')' : 'All' }}
        </button>
      </div>
      <div class="flex items-center gap-2 pb-2">
        <label class="text-xs text-gray-500">Directory:</label>
        <select :value="dirFilter" @change="changeDirFilter($event.target.value)" class="input input-sm text-xs">
          <option value="">All</option>
          <option v-for="d in directories" :key="d.id" :value="d.id">{{ d.displayName || d.name }}</option>
        </select>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="text-center py-12 text-gray-400">Loading alerts...</div>

    <!-- Empty state -->
    <div v-else-if="alerts.length === 0" class="text-center py-12 text-gray-400">
      <p class="text-lg">No alerts</p>
      <p class="text-sm mt-1">All monitored conditions are within acceptable thresholds.</p>
    </div>

    <!-- Alert list -->
    <div v-else class="space-y-2">
      <div v-for="a in alerts" :key="a.id"
           class="bg-white border border-gray-200 rounded-xl p-4 flex items-start gap-4 hover:shadow-sm transition-shadow cursor-pointer"
           @click="selectedAlert = a">
        <!-- Severity badge -->
        <span class="shrink-0 px-2 py-0.5 rounded-full text-xs font-bold"
              :class="severityIcon[a.severity]?.color || 'text-gray-600 bg-gray-100'">
          {{ a.severity }}
        </span>

        <!-- Content -->
        <div class="flex-1 min-w-0">
          <p class="text-sm font-medium text-gray-900">{{ a.title }}</p>
          <div class="flex items-center gap-3 mt-1 text-xs text-gray-400">
            <span v-if="a.directoryName">{{ a.directoryName }}</span>
            <span class="badge-gray text-xs">{{ humanizeRuleType(a.ruleType) }}</span>
            <RelativeTime :value="a.createdAt" />
          </div>
        </div>

        <!-- Status -->
        <span :class="['shrink-0 px-2 py-0.5 rounded-full text-xs font-medium',
          a.status === 'OPEN' ? 'bg-red-100 text-red-700' :
          a.status === 'ACKNOWLEDGED' ? 'bg-blue-100 text-blue-700' :
          a.status === 'RESOLVED' ? 'bg-green-100 text-green-700' :
          'bg-gray-100 text-gray-600']">
          {{ a.status }}
        </span>
      </div>
    </div>

    <!-- Pagination -->
    <div v-if="totalPages > 1" class="flex items-center justify-between mt-4">
      <button :disabled="page === 0" @click="page--; loadData()" class="btn-secondary">Prev</button>
      <span class="text-sm text-gray-500">Page {{ page + 1 }} of {{ totalPages }}</span>
      <button :disabled="page >= totalPages - 1" @click="page++; loadData()" class="btn-secondary">Next</button>
    </div>

    <!-- Detail panel -->
    <Teleport to="body">
      <div v-if="selectedAlert" class="fixed inset-0 z-40 flex justify-end bg-black/30" @click.self="selectedAlert = null">
        <div class="w-full max-w-lg bg-white shadow-xl h-full overflow-y-auto">
          <div class="p-6 border-b border-gray-100 flex items-center justify-between">
            <h2 class="text-lg font-semibold text-gray-900">Alert Detail</h2>
            <button @click="selectedAlert = null" class="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
          </div>
          <div class="p-6 space-y-4">
            <div>
              <span class="px-2 py-0.5 rounded-full text-xs font-bold"
                    :class="severityIcon[selectedAlert.severity]?.color">{{ selectedAlert.severity }}</span>
              <span class="ml-2 px-2 py-0.5 rounded-full text-xs font-medium"
                    :class="selectedAlert.status === 'OPEN' ? 'bg-red-100 text-red-700' : 'bg-gray-100 text-gray-600'">{{ selectedAlert.status }}</span>
            </div>
            <h3 class="text-base font-medium text-gray-900">{{ selectedAlert.title }}</h3>
            <div class="text-sm text-gray-600 whitespace-pre-wrap bg-gray-50 rounded-lg p-4">{{ selectedAlert.detail || 'No additional detail.' }}</div>

            <div class="grid grid-cols-2 gap-3 text-sm">
              <div><span class="text-gray-500">Rule Type:</span><br><span class="font-medium">{{ humanizeRuleType(selectedAlert.ruleType) }}</span></div>
              <div><span class="text-gray-500">Directory:</span><br><span class="font-medium">{{ selectedAlert.directoryName || '—' }}</span></div>
              <div><span class="text-gray-500">Created:</span><br><span class="font-medium"><RelativeTime :value="selectedAlert.createdAt" /></span></div>
              <div v-if="selectedAlert.acknowledgedAt"><span class="text-gray-500">Acknowledged:</span><br><span class="font-medium"><RelativeTime :value="selectedAlert.acknowledgedAt" /></span></div>
            </div>

            <div v-if="selectedAlert.status === 'OPEN' || selectedAlert.status === 'ACKNOWLEDGED'"
                 class="flex gap-2 pt-4 border-t border-gray-100">
              <button v-if="selectedAlert.status === 'OPEN'" @click="doAction('acknowledge', selectedAlert)" class="btn-primary btn-sm">Acknowledge</button>
              <button @click="doAction('resolve', selectedAlert)" class="btn-secondary btn-sm">Resolve</button>
              <button @click="doAction('dismiss', selectedAlert)" class="btn-neutral btn-sm">Dismiss</button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
@reference "tailwindcss";
</style>
