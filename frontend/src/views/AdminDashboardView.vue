<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAdminDashboard } from '@/api/dashboard'
import RelativeTime from '@/components/RelativeTime.vue'

const router = useRouter()
const loading = ref(true)
const data = ref(null)
const error = ref(null)

const ACTION_LABELS = {
  'USER_CREATE': 'User created',
  'USER_UPDATE': 'User updated',
  'USER_DELETE': 'User deleted',
  'USER_ENABLE': 'User enabled',
  'USER_DISABLE': 'User disabled',
  'USER_MOVE': 'User moved',
  'PASSWORD_RESET': 'Password reset',
  'GROUP_CREATE': 'Group created',
  'GROUP_UPDATE': 'Group updated',
  'GROUP_DELETE': 'Group deleted',
  'GROUP_MEMBER_ADD': 'Member added',
  'GROUP_MEMBER_REMOVE': 'Member removed',
  'APPROVAL_SUBMITTED': 'Approval submitted',
  'APPROVAL_APPROVED': 'Request approved',
  'APPROVAL_AUTO_APPROVED': 'Auto-approved',
  'APPROVAL_REJECTED': 'Request rejected',
  'CAMPAIGN_CREATED': 'Campaign created',
  'CAMPAIGN_ACTIVATED': 'Campaign activated',
  'CAMPAIGN_CLOSED': 'Campaign closed',
  'REVIEW_CONFIRMED': 'Review confirmed',
  'REVIEW_REVOKED': 'Review revoked',
  'PLAYBOOK_EXECUTED': 'Playbook executed',
  'SOD_VIOLATION_DETECTED': 'SoD violation',
  'SOD_VIOLATION_RESOLVED': 'SoD resolved',
  'HR_SYNC_COMPLETED': 'HR sync completed',
}

function actionLabel(action) {
  return ACTION_LABELS[action] || action
}

function actionColor(action) {
  if (action?.includes('DELETE') || action?.includes('REVOKED') || action?.includes('BLOCKED'))
    return 'text-red-600 bg-red-50'
  if (action?.includes('CREATE') || action?.includes('APPROVED') || action?.includes('CONFIRMED'))
    return 'text-green-600 bg-green-50'
  if (action?.includes('DISABLE') || action?.includes('REJECTED') || action?.includes('DETECTED'))
    return 'text-amber-600 bg-amber-50'
  return 'text-blue-600 bg-blue-50'
}

function shortDn(dn) {
  if (!dn) return '—'
  const first = dn.split(',')[0]
  return first || dn
}

// ── Severity helpers ──────────────────────────────────────────────────────
function cardBorder(severity) {
  switch (severity) {
    case 'green': return 'border-green-200 bg-green-50/30'
    case 'yellow': return 'border-amber-200 bg-amber-50/30'
    case 'red': return 'border-red-200 bg-red-50/30'
    default: return 'border-gray-200'
  }
}

function cardValueColor(severity) {
  switch (severity) {
    case 'green': return 'text-green-700'
    case 'yellow': return 'text-amber-600'
    case 'red': return 'text-red-600'
    default: return 'text-gray-900'
  }
}

const pendingSeverity = computed(() => {
  if (!data.value) return 'gray'
  const v = data.value.totalPendingApprovals
  return v === 0 ? 'green' : v <= 5 ? 'yellow' : 'red'
})

const sodSeverity = computed(() => {
  if (!data.value) return 'gray'
  return data.value.openSodViolations === 0 ? 'green' : data.value.openSodViolations <= 5 ? 'yellow' : 'red'
})

const campaignSeverity = computed(() => {
  if (!data.value || data.value.campaignCompletionPercent == null) return 'gray'
  return data.value.campaignCompletionPercent >= 90 ? 'green' : data.value.campaignCompletionPercent >= 50 ? 'yellow' : 'red'
})

const overdueSeverity = computed(() => {
  if (!data.value) return 'gray'
  return data.value.overdueCampaigns === 0 ? 'green' : 'red'
})

const agingTotal = computed(() => data.value?.approvalAging?.total ?? 0)

function agingBarWidth(count) {
  if (agingTotal.value === 0) return '0%'
  return Math.max(2, (count / agingTotal.value) * 100) + '%'
}

// ── Navigation helpers ────────────────────────────────────────────────────
function dirPath(suffix) {
  const dirId = data.value?.firstDirectoryId
  if (!dirId) return '#'
  return `/directories/${dirId}/${suffix}`
}

// ── Data loading ──────────────────────────────────────────────────────────
let refreshTimer = null

async function loadDashboard() {
  try {
    const { data: d } = await getAdminDashboard()
    data.value = d
    error.value = null
  } catch (e) {
    error.value = e.response?.data?.detail || e.message
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDashboard()
  refreshTimer = setInterval(loadDashboard, 60000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Dashboard</h1>
        <p class="text-sm text-gray-500 mt-0.5">Overview of your managed directories</p>
      </div>
      <button @click="loadDashboard" :disabled="loading"
              class="text-sm text-gray-500 hover:text-gray-700 border border-gray-300 rounded-lg px-3 py-1.5 hover:bg-gray-50 disabled:opacity-50">
        {{ loading ? 'Refreshing...' : 'Refresh' }}
      </button>
    </div>

    <!-- Loading skeleton -->
    <div v-if="loading" class="space-y-6 animate-pulse">
      <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <div v-for="i in 4" :key="i" class="bg-white rounded-xl border border-gray-200 p-5">
          <div class="h-3 bg-gray-200 rounded w-1/2 mb-3" />
          <div class="h-8 bg-gray-200 rounded w-1/3" />
        </div>
      </div>
      <div class="grid grid-cols-3 gap-4">
        <div v-for="i in 3" :key="i" class="bg-white rounded-xl border border-gray-200 p-5">
          <div class="h-3 bg-gray-200 rounded w-2/3 mb-3" />
          <div class="h-6 bg-gray-200 rounded w-1/4" />
        </div>
      </div>
    </div>

    <div v-else-if="error" class="bg-red-50 border border-red-200 rounded-xl p-6 text-red-700">
      <p class="font-medium">Failed to load dashboard</p>
      <p class="text-sm mt-1">{{ error }}</p>
    </div>

    <template v-else-if="data">

      <!-- ── Top row: Key metrics ──────────────────────────────────────────── -->
      <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
        <!-- Pending Approvals -->
        <div class="rounded-xl border-2 p-5 cursor-pointer shadow-sm transition-shadow hover:shadow-md"
             :class="cardBorder(pendingSeverity)"
             @click="router.push(dirPath('approvals'))">
          <p class="text-sm font-medium text-gray-500 mb-1">Pending Approvals</p>
          <p class="text-3xl font-bold" :class="cardValueColor(pendingSeverity)">
            {{ data.totalPendingApprovals }}
          </p>
          <p class="text-xs text-gray-400 mt-1">Click to review</p>
        </div>

        <!-- Active Campaigns -->
        <div class="rounded-xl border-2 p-5 cursor-pointer shadow-sm transition-shadow hover:shadow-md"
             :class="cardBorder(campaignSeverity)"
             @click="router.push(dirPath('access-reviews'))">
          <p class="text-sm font-medium text-gray-500 mb-1">Campaign Completion</p>
          <p class="text-3xl font-bold" :class="cardValueColor(campaignSeverity)">
            {{ data.campaignCompletionPercent != null ? data.campaignCompletionPercent + '%' : 'N/A' }}
          </p>
          <p class="text-xs text-gray-400 mt-1">{{ data.activeAccessReviewCampaigns }} active campaigns</p>
        </div>

        <!-- SoD Violations -->
        <div class="rounded-xl border-2 p-5 cursor-pointer shadow-sm transition-shadow hover:shadow-md"
             :class="cardBorder(sodSeverity)"
             @click="router.push(dirPath('sod-violations'))">
          <p class="text-sm font-medium text-gray-500 mb-1">Open SoD Violations</p>
          <p class="text-3xl font-bold" :class="cardValueColor(sodSeverity)">
            {{ data.openSodViolations }}
          </p>
          <p class="text-xs text-gray-400 mt-1">Click to view</p>
        </div>

        <!-- Overdue Campaigns -->
        <div class="rounded-xl border-2 p-5 cursor-pointer shadow-sm transition-shadow hover:shadow-md"
             :class="cardBorder(overdueSeverity)"
             @click="router.push(dirPath('access-reviews'))">
          <p class="text-sm font-medium text-gray-500 mb-1">Overdue Campaigns</p>
          <p class="text-3xl font-bold" :class="cardValueColor(overdueSeverity)">
            {{ data.overdueCampaigns }}
          </p>
          <p class="text-xs text-gray-400 mt-1">Past deadline</p>
        </div>
      </div>

      <!-- ── Second row: User/Group totals ─────────────────────────────────── -->
      <div class="grid grid-cols-2 gap-4 mb-6">
        <div class="bg-white border border-gray-200 rounded-xl p-5 shadow-sm">
          <p class="text-sm text-gray-500 mb-1">Total Users</p>
          <p class="text-3xl font-bold text-gray-900">{{ data.totalUsers.toLocaleString() }}</p>
          <p class="text-xs text-gray-400 mt-1">Across {{ data.directories.length }} directories</p>
        </div>
        <div class="bg-white border border-gray-200 rounded-xl p-5 shadow-sm">
          <p class="text-sm text-gray-500 mb-1">Total Groups</p>
          <p class="text-3xl font-bold text-gray-900">{{ data.totalGroups.toLocaleString() }}</p>
          <p class="text-xs text-gray-400 mt-1">Across {{ data.directories.length }} directories</p>
        </div>
      </div>

      <!-- ── Middle row: Approval aging + Campaign progress ────────────────── -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        <!-- Approval aging -->
        <div class="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
          <div class="px-5 py-3 border-b border-gray-100">
            <h2 class="text-sm font-semibold text-gray-700">Approval Aging</h2>
          </div>
          <div class="p-5 space-y-3">
            <div v-if="agingTotal === 0" class="text-sm text-gray-400 text-center py-4">No pending approvals</div>
            <template v-else>
              <div v-for="(bucket, idx) in [
                { label: '< 24 hours', count: data.approvalAging.lessThan24h, color: 'bg-green-500' },
                { label: '1–3 days', count: data.approvalAging.oneToThreeDays, color: 'bg-yellow-500' },
                { label: '3–7 days', count: data.approvalAging.threeToSevenDays, color: 'bg-amber-500' },
                { label: '7+ days', count: data.approvalAging.moreThanSevenDays, color: 'bg-red-500' },
              ]" :key="idx" class="flex items-center gap-3">
                <span class="text-xs text-gray-500 w-20 shrink-0 text-right">{{ bucket.label }}</span>
                <div class="flex-1 h-5 bg-gray-100 rounded-full overflow-hidden">
                  <div class="h-full rounded-full transition-all duration-300"
                       :class="bucket.color"
                       :style="{ width: agingBarWidth(bucket.count) }"></div>
                </div>
                <span class="text-sm font-medium text-gray-700 w-8 text-right">{{ bucket.count }}</span>
              </div>
            </template>
          </div>
        </div>

        <!-- Campaign progress -->
        <div class="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
          <div class="px-5 py-3 border-b border-gray-100">
            <h2 class="text-sm font-semibold text-gray-700">Active Campaign Progress</h2>
          </div>
          <div v-if="!data.campaignProgress.length" class="px-5 py-8 text-center text-sm text-gray-400">
            No active access review campaigns.
          </div>
          <div v-else class="p-5 space-y-4">
            <div v-for="c in data.campaignProgress" :key="c.campaignId">
              <div class="flex items-center justify-between mb-1">
                <div class="flex items-center gap-2 min-w-0">
                  <span class="text-sm font-medium text-gray-900 truncate">{{ c.campaignName }}</span>
                  <span v-if="c.overdue" class="px-1.5 py-0.5 rounded text-[10px] font-bold bg-red-100 text-red-700">OVERDUE</span>
                </div>
                <span class="text-xs text-gray-500 shrink-0 ml-2">{{ c.decidedCount }}/{{ c.totalDecisions }}</span>
              </div>
              <div class="h-2.5 bg-gray-100 rounded-full overflow-hidden">
                <div class="h-full rounded-full transition-all duration-300"
                     :class="c.overdue ? 'bg-red-500' : c.completionPercent >= 90 ? 'bg-green-500' : c.completionPercent >= 50 ? 'bg-yellow-500' : 'bg-blue-500'"
                     :style="{ width: c.completionPercent + '%' }"></div>
              </div>
              <div class="flex justify-between mt-0.5">
                <span class="text-[10px] text-gray-400">{{ c.directoryName }}</span>
                <span class="text-[10px] font-medium"
                      :class="c.overdue ? 'text-red-600' : 'text-gray-500'">
                  {{ c.completionPercent }}%
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ── Bottom row: Directories + Recent Activity ─────────────────────── -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <!-- My Directories -->
        <div class="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
          <div class="px-5 py-3 border-b border-gray-100">
            <h2 class="text-sm font-semibold text-gray-700">My Directories</h2>
          </div>
          <table class="w-full text-sm">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Name</th>
                <th class="px-4 py-2.5 text-right text-xs font-semibold text-gray-500 uppercase tracking-wider">Users</th>
                <th class="px-4 py-2.5 text-right text-xs font-semibold text-gray-500 uppercase tracking-wider">Groups</th>
                <th class="px-4 py-2.5 text-right text-xs font-semibold text-gray-500 uppercase tracking-wider">Pending</th>
                <th class="px-4 py-2.5 text-right text-xs font-semibold text-gray-500 uppercase tracking-wider">SoD</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-50">
              <tr v-for="dir in data.directories" :key="dir.id"
                  class="hover:bg-gray-50 cursor-pointer"
                  @click="router.push(`/directories/${dir.id}/users`)">
                <td class="px-4 py-2.5">
                  <div class="flex items-center gap-2">
                    <span class="w-2 h-2 rounded-full" :class="dir.enabled ? 'bg-green-400' : 'bg-gray-300'"></span>
                    <span class="font-medium text-gray-900">{{ dir.name }}</span>
                  </div>
                </td>
                <td class="px-4 py-2.5 text-right text-gray-600">{{ dir.userCount >= 0 ? dir.userCount.toLocaleString() : '—' }}</td>
                <td class="px-4 py-2.5 text-right text-gray-600">{{ dir.groupCount >= 0 ? dir.groupCount.toLocaleString() : '—' }}</td>
                <td class="px-4 py-2.5 text-right">
                  <span v-if="dir.pendingApprovals > 0" class="text-amber-600 font-medium">{{ dir.pendingApprovals }}</span>
                  <span v-else class="text-gray-400">0</span>
                </td>
                <td class="px-4 py-2.5 text-right">
                  <span v-if="dir.openSodViolations > 0" class="text-red-600 font-medium">{{ dir.openSodViolations }}</span>
                  <span v-else class="text-green-600">0</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Recent Activity -->
        <div class="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
          <div class="px-5 py-3 border-b border-gray-100 flex items-center justify-between">
            <h2 class="text-sm font-semibold text-gray-700">Recent Activity</h2>
            <button v-if="data.firstDirectoryId"
                    @click="router.push(dirPath('audit'))"
                    class="text-xs text-blue-600 hover:text-blue-800">View all</button>
          </div>
          <div v-if="!data.recentActivity || !data.recentActivity.length" class="px-5 py-8 text-center text-sm text-gray-400">
            No recent events.
          </div>
          <ul v-else class="divide-y divide-gray-50">
            <li v-for="evt in data.recentActivity" :key="evt.id" class="px-4 py-2.5 flex items-start gap-3">
              <span class="mt-0.5 shrink-0 text-[11px] font-medium px-1.5 py-0.5 rounded" :class="actionColor(evt.action)">
                {{ actionLabel(evt.action) }}
              </span>
              <div class="min-w-0 flex-1">
                <p class="text-sm text-gray-700 truncate" :title="evt.targetDn">{{ shortDn(evt.targetDn) }}</p>
                <p class="text-xs text-gray-400">
                  {{ evt.actorUsername || 'system' }}
                  <span class="mx-1">&middot;</span>
                  <RelativeTime :value="evt.occurredAt" />
                </p>
              </div>
            </li>
          </ul>
        </div>
      </div>

      <!-- ── Quick Actions ─────────────────────────────────────────────────── -->
      <div v-if="data.firstDirectoryId" class="mt-6 bg-white border border-gray-200 rounded-xl p-5 shadow-sm">
        <h2 class="text-sm font-semibold text-gray-700 mb-3">Quick Actions</h2>
        <div class="flex flex-wrap gap-3">
          <button @click="router.push(dirPath('users'))" class="btn-primary btn-sm">
            <svg class="w-4 h-4 mr-1.5" fill="none" viewBox="0 0 20 20" stroke="currentColor" stroke-width="1.5"><circle cx="10" cy="6" r="3.25"/><path d="M3.5 17.5c0-3.59 2.91-6.5 6.5-6.5s6.5 2.91 6.5 6.5" stroke-linecap="round"/></svg>
            Browse Users
          </button>
          <button @click="router.push(dirPath('groups'))" class="btn-primary btn-sm">
            <svg class="w-4 h-4 mr-1.5" fill="none" viewBox="0 0 20 20" stroke="currentColor" stroke-width="1.5"><circle cx="7.5" cy="6" r="2.75"/><circle cx="13.5" cy="6" r="2.75"/><path d="M1.5 17c0-3.04 2.46-5.5 5.5-5.5 1.26 0 2.42.42 3.35 1.14M12 11.64A5.48 5.48 0 0 1 18.5 17" stroke-linecap="round"/></svg>
            Browse Groups
          </button>
          <button @click="router.push(dirPath('approvals'))" class="btn-neutral btn-sm">
            <svg class="w-4 h-4 mr-1.5" fill="none" viewBox="0 0 20 20" stroke="currentColor" stroke-width="1.5"><path d="M10 2l7 4v5c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6l7-4z" stroke-linecap="round" stroke-linejoin="round"/><path d="M7 10l2 2 4-4" stroke-linecap="round" stroke-linejoin="round"/></svg>
            Review Approvals
          </button>
          <button @click="router.push(dirPath('access-reviews'))" class="btn-neutral btn-sm">
            <svg class="w-4 h-4 mr-1.5" fill="none" viewBox="0 0 20 20" stroke="currentColor" stroke-width="1.5"><path d="M9 2H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9" stroke-linecap="round" stroke-linejoin="round"/><path d="M9 11l8-8M14 3h3v3" stroke-linecap="round" stroke-linejoin="round"/></svg>
            Access Reviews
          </button>
          <button @click="router.push(dirPath('reports'))" class="btn-neutral btn-sm">
            <svg class="w-4 h-4 mr-1.5" fill="none" viewBox="0 0 20 20" stroke="currentColor" stroke-width="1.5"><path d="M5 16V10M10 16V4M15 16v-4" stroke-linecap="round" stroke-linejoin="round"/></svg>
            Run Reports
          </button>
          <button @click="router.push(dirPath('audit'))" class="btn-neutral btn-sm">
            <svg class="w-4 h-4 mr-1.5" fill="none" viewBox="0 0 20 20" stroke="currentColor" stroke-width="1.5"><rect x="3" y="2" width="14" height="16" rx="2" stroke-linecap="round" stroke-linejoin="round"/><path d="M7 6h6M7 10h6M7 14h3" stroke-linecap="round" stroke-linejoin="round"/></svg>
            Audit Log
          </button>
        </div>
      </div>

    </template>
  </div>
</template>
