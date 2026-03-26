<template>
  <div>
    <h1 class="text-xl font-bold text-slate-900 mb-4">Access Review Campaigns</h1>

    <div v-if="loading" class="text-sm text-slate-500">Loading campaigns...</div>

    <div v-else-if="campaigns.length === 0" class="bg-slate-50 border border-slate-200 rounded-xl p-8 text-center">
      <p class="text-sm text-slate-500">No campaigns included in this evidence package.</p>
    </div>

    <div v-else class="space-y-3">
      <RouterLink v-for="c in campaigns" :key="c.id"
                  :to="`/auditor/${token}/campaigns/${c.id}`"
                  class="block bg-white border border-slate-200 rounded-xl p-4 hover:border-slate-300 hover:shadow-sm transition-all">
        <div class="flex items-center justify-between mb-2">
          <h3 class="text-sm font-semibold text-slate-800">{{ c.name }}</h3>
          <span class="text-xs font-medium px-2 py-0.5 rounded-full" :class="statusClass(c.status)">{{ c.status }}</span>
        </div>
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs text-slate-500">
          <div>
            <span class="text-slate-400">Started</span>
            <div class="text-slate-700 font-medium">{{ formatDate(c.startsAt) || '—' }}</div>
          </div>
          <div>
            <span class="text-slate-400">Deadline</span>
            <div class="text-slate-700 font-medium">{{ formatDate(c.deadline) || '—' }}</div>
          </div>
          <div>
            <span class="text-slate-400">Decisions</span>
            <div class="text-slate-700 font-medium">{{ c.completedDecisions }} / {{ c.totalDecisions }}</div>
          </div>
          <div>
            <span class="text-slate-400">Completion</span>
            <div class="font-medium" :class="c.totalDecisions > 0 && c.completedDecisions === c.totalDecisions ? 'text-green-600' : 'text-slate-700'">
              {{ c.totalDecisions > 0 ? Math.round((c.completedDecisions / c.totalDecisions) * 100) : 0 }}%
            </div>
          </div>
        </div>
      </RouterLink>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { getPortalCampaigns } from '@/api/auditorPortal'

const props = defineProps({ token: String, metadata: Object, scope: Object })

const loading = ref(true)
const campaigns = ref([])

function statusClass(status) {
  switch (status) {
    case 'CLOSED':    return 'bg-green-100 text-green-700'
    case 'ACTIVE':    return 'bg-blue-100 text-blue-700'
    case 'UPCOMING':  return 'bg-slate-100 text-slate-600'
    case 'CANCELLED': return 'bg-red-100 text-red-700'
    case 'EXPIRED':   return 'bg-amber-100 text-amber-700'
    default:          return 'bg-slate-100 text-slate-600'
  }
}

function formatDate(iso) {
  if (!iso) return null
  return new Date(iso).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
}

onMounted(async () => {
  try {
    const { data } = await getPortalCampaigns(props.token)
    campaigns.value = data
  } catch { /* handled by layout */ }
  loading.value = false
})
</script>
