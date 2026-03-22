<script setup>
import { ref, onMounted } from 'vue'
import { getDashboard } from '@/api/dashboard'
import RelativeTime from '@/components/RelativeTime.vue'

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
  'ENTRY_CREATE': 'Entry created',
  'ENTRY_UPDATE': 'Entry updated',
  'ENTRY_DELETE': 'Entry deleted',
  'ENTRY_MOVE': 'Entry moved',
  'ENTRY_RENAME': 'Entry renamed',
  'APPROVAL_SUBMITTED': 'Approval submitted',
  'APPROVAL_APPROVED': 'Request approved',
  'APPROVAL_REJECTED': 'Request rejected',
  'LDAP_CHANGE': 'LDAP change',
}

function actionLabel(action) {
  return ACTION_LABELS[action] || action
}

function actionColor(action) {
  if (action?.startsWith('USER_DELETE') || action?.startsWith('GROUP_DELETE') || action?.startsWith('ENTRY_DELETE'))
    return 'text-red-600 bg-red-50'
  if (action?.includes('CREATE')) return 'text-green-600 bg-green-50'
  if (action?.includes('DISABLE') || action?.includes('REJECTED')) return 'text-amber-600 bg-amber-50'
  return 'text-blue-600 bg-blue-50'
}

function shortDn(dn) {
  if (!dn) return '—'
  const first = dn.split(',')[0]
  return first || dn
}

onMounted(async () => {
  try {
    const { data: d } = await getDashboard()
    data.value = d
  } catch (e) {
    error.value = e.response?.data?.detail || e.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="p-6">
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Dashboard</h1>

    <div v-if="loading" class="text-gray-400">Loading...</div>
    <div v-else-if="error" class="text-red-500">{{ error }}</div>
    <template v-else-if="data">
      <!-- Summary cards -->
      <div class="grid grid-cols-3 gap-4 mb-8">
        <div class="bg-white border border-gray-200 rounded-xl p-5">
          <p class="text-sm text-gray-500 mb-1">Total Users</p>
          <p class="text-3xl font-bold text-gray-900">{{ data.totalUsers.toLocaleString() }}</p>
        </div>
        <div class="bg-white border border-gray-200 rounded-xl p-5">
          <p class="text-sm text-gray-500 mb-1">Total Groups</p>
          <p class="text-3xl font-bold text-gray-900">{{ data.totalGroups.toLocaleString() }}</p>
        </div>
        <div class="bg-white border border-gray-200 rounded-xl p-5">
          <p class="text-sm text-gray-500 mb-1">Pending Approvals</p>
          <p class="text-3xl font-bold" :class="data.totalPendingApprovals > 0 ? 'text-amber-600' : 'text-gray-900'">
            {{ data.totalPendingApprovals }}
          </p>
        </div>
      </div>

      <!-- Directory breakdown -->
      <div class="grid grid-cols-2 gap-6">
        <div class="bg-white border border-gray-200 rounded-xl overflow-hidden">
          <div class="px-5 py-3 border-b border-gray-100">
            <h2 class="text-sm font-semibold text-gray-700">Directories</h2>
          </div>
          <table class="w-full text-sm">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Name</th>
                <th class="px-4 py-2.5 text-right text-xs font-semibold text-gray-500 uppercase tracking-wider">Users</th>
                <th class="px-4 py-2.5 text-right text-xs font-semibold text-gray-500 uppercase tracking-wider">Groups</th>
                <th class="px-4 py-2.5 text-right text-xs font-semibold text-gray-500 uppercase tracking-wider">Pending</th>
                <th class="px-4 py-2.5 text-right text-xs font-semibold text-gray-500 uppercase tracking-wider">Reviews</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-50">
              <tr v-for="dir in data.directories" :key="dir.id" class="hover:bg-gray-50">
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
                  <span v-if="dir.activeCampaigns > 0" class="text-blue-600 font-medium">{{ dir.activeCampaigns }}</span>
                  <span v-else class="text-gray-400">0</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Recent activity -->
        <div class="bg-white border border-gray-200 rounded-xl overflow-hidden">
          <div class="px-5 py-3 border-b border-gray-100">
            <h2 class="text-sm font-semibold text-gray-700">Recent Activity</h2>
          </div>
          <div v-if="!data.recentAudit.length" class="px-5 py-8 text-center text-sm text-gray-400">No recent events.</div>
          <ul v-else class="divide-y divide-gray-50">
            <li v-for="evt in data.recentAudit" :key="evt.id" class="px-4 py-2.5 flex items-start gap-3">
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
    </template>
  </div>
</template>
