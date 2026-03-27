<template>
  <div>
    <h1 class="text-xl font-bold text-slate-900 mb-4">Access Review Campaigns</h1>

    <SkeletonLoader v-if="loading" :rows="3" />
    <ErrorCard v-else-if="error" title="Failed to load campaigns" @retry="load" />

    <div v-else-if="campaigns.length === 0" class="bg-green-50 border border-green-200 rounded-xl p-6 text-center">
      <svg class="w-8 h-8 mx-auto mb-2 text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
        <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <p class="text-sm text-green-800 font-medium">No access review campaigns included in this evidence package</p>
    </div>

    <FadeIn :show="!loading && campaigns.length > 0">
    <div class="space-y-3">
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
    </FadeIn>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { getPortalCampaigns } from '@/api/auditorPortal'
import SkeletonLoader from './components/SkeletonLoader.vue'
import ErrorCard from './components/ErrorCard.vue'
import FadeIn from './components/FadeIn.vue'

const props = defineProps({ token: String, metadata: Object, scope: Object })

const loading = ref(true)
const error = ref(false)
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

async function load() {
  loading.value = true
  error.value = false
  try {
    const { data } = await getPortalCampaigns(props.token)
    campaigns.value = data
  } catch { error.value = true }
  loading.value = false
}
onMounted(load)
</script>
