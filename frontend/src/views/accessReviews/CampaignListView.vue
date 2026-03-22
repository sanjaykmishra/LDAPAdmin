<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Access Reviews</h1>
      <button @click="$router.push({ name: 'accessReviewCreate', params: { dirId } })" class="btn-primary">
        New Campaign
      </button>
    </div>

    <!-- Status filter tabs -->
    <div class="flex gap-2 mb-4">
      <button v-for="tab in tabs" :key="tab.value"
        @click="activeTab = tab.value"
        :class="['px-3 py-1.5 text-sm rounded-lg border transition-colors',
          activeTab === tab.value ? 'bg-blue-50 border-blue-300 text-blue-700' : 'border-gray-200 text-gray-600 hover:bg-gray-50']">
        {{ tab.label }}
      </button>
    </div>

    <DataTable :columns="cols" :rows="filteredCampaigns" :loading="loading" row-key="id">
      <template #cell-status="{ value }">
        <span :class="statusClass(value)">{{ value }}</span>
      </template>
      <template #cell-deadline="{ value }">{{ fmtDate(value) }}</template>
      <template #cell-createdAt="{ value }">{{ fmtDate(value) }}</template>
      <template #cell-progress="{ row }">
        <div v-if="row.progress" class="flex items-center gap-2">
          <div class="w-24 bg-gray-200 rounded-full h-2">
            <div class="bg-blue-600 h-2 rounded-full" :style="{ width: row.progress.percentComplete + '%' }"></div>
          </div>
          <span class="text-xs text-gray-500">{{ Math.round(row.progress.percentComplete) }}%</span>
        </div>
      </template>
      <template #cell-actions="{ row }">
        <button @click="$router.push({ name: 'accessReviewDetail', params: { dirId, campaignId: row.id } })"
          class="btn-secondary text-xs">View</button>
      </template>
    </DataTable>

    <p v-if="!loading && filteredCampaigns.length === 0" class="text-gray-500 text-sm mt-4">
      No campaigns found.
    </p>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useApi } from '@/composables/useApi'
import { listCampaigns } from '@/api/accessReviews'
import DataTable from '@/components/DataTable.vue'

const route = useRoute()
const { loading, call } = useApi()
const dirId = route.params.dirId

const campaigns = ref([])
const activeTab = ref('ALL')

const tabs = [
  { label: 'All', value: 'ALL' },
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Closed', value: 'CLOSED' },
]

const cols = [
  { key: 'name', label: 'Name' },
  { key: 'status', label: 'Status' },
  { key: 'deadline', label: 'Deadline' },
  { key: 'progress', label: 'Progress' },
  { key: 'createdByUsername', label: 'Created By' },
  { key: 'createdAt', label: 'Created' },
  { key: 'actions', label: '' },
]

const filteredCampaigns = computed(() => {
  if (activeTab.value === 'ALL') return campaigns.value
  return campaigns.value.filter(c => c.status === activeTab.value)
})

function statusClass(status) {
  const base = 'px-2 py-0.5 rounded-full text-xs font-medium'
  switch (status) {
    case 'DRAFT': return base + ' bg-gray-100 text-gray-800'
    case 'ACTIVE': return base + ' bg-blue-100 text-blue-800'
    case 'CLOSED': return base + ' bg-green-100 text-green-800'
    case 'CANCELLED': return base + ' bg-red-100 text-red-800'
    case 'EXPIRED': return base + ' bg-yellow-100 text-yellow-800'
    default: return base + ' bg-gray-100 text-gray-800'
  }
}

function fmtDate(val) {
  if (!val) return ''
  return new Date(val).toLocaleDateString()
}

async function loadCampaigns() {
  try {
    const res = await call(() => listCampaigns(dirId, { page: 0, size: 100 }))
    campaigns.value = res.data.content || []
  } catch { /* handled by useApi */ }
}

onMounted(loadCampaigns)
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary   { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700; }
.btn-secondary { @apply px-3 py-1.5 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50; }
</style>
