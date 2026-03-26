<template>
  <div>
    <h1 class="text-xl font-bold text-slate-900 mb-4">Approval History</h1>

    <SkeletonLoader v-if="loading" :rows="3" />
    <ErrorCard v-else-if="error" title="Failed to load approvals" @retry="load" />

    <template v-else>
      <!-- Filters -->
      <div class="mb-4 flex flex-wrap gap-2">
        <input v-model="search" type="text" placeholder="Search requester, reviewer..."
               class="input-sm w-full sm:w-64" />
        <select v-model="statusFilter" class="input-sm">
          <option value="">All Statuses</option>
          <option value="APPROVED">Approved</option>
          <option value="REJECTED">Rejected</option>
          <option value="PENDING">Pending</option>
        </select>
      </div>

      <div v-if="filtered.length === 0" class="bg-slate-50 border border-slate-200 rounded-xl p-8 text-center">
        <p class="text-sm text-slate-500">
          {{ approvals.length === 0 ? 'No approval history available.' : 'No approvals match the current filters.' }}
        </p>
      </div>

      <!-- Desktop table -->
      <section v-if="filtered.length > 0" class="hidden sm:block bg-white border border-slate-200 rounded-xl overflow-hidden">
        <div class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-slate-200 bg-slate-50">
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Type</th>
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Status</th>
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Requested By</th>
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Reviewed By</th>
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Submitted</th>
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Reviewed</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="a in paged" :key="a.id" class="border-b border-slate-100 hover:bg-slate-50/50">
                <td class="py-2 px-4 text-xs text-slate-700">{{ humanize(a.requestType) }}</td>
                <td class="py-2 px-4">
                  <span class="text-xs font-medium px-2 py-0.5 rounded-full" :class="statusClass(a.status)">{{ a.status }}</span>
                </td>
                <td class="py-2 px-4 text-xs text-slate-600">{{ a.requestedBy || '—' }}</td>
                <td class="py-2 px-4 text-xs text-slate-600">{{ a.reviewedBy || '—' }}</td>
                <td class="py-2 px-4 text-xs text-slate-500 font-mono">{{ formatDate(a.createdAt) }}</td>
                <td class="py-2 px-4 text-xs text-slate-500 font-mono">{{ formatDate(a.reviewedAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="totalPages > 1" class="px-4 py-3 border-t border-slate-200 flex items-center justify-between">
          <span class="text-xs text-slate-500">Page {{ page + 1 }} of {{ totalPages }} ({{ filtered.length }} approvals)</span>
          <div class="flex gap-1">
            <button @click="page = Math.max(0, page - 1)" :disabled="page === 0" class="btn-sm">Prev</button>
            <button @click="page = Math.min(totalPages - 1, page + 1)" :disabled="page >= totalPages - 1" class="btn-sm">Next</button>
          </div>
        </div>
      </section>

      <!-- Mobile card layout -->
      <div class="sm:hidden space-y-2" v-if="filtered.length > 0">
        <div v-for="a in paged" :key="a.id + '-m'" class="bg-white border border-slate-200 rounded-xl p-3">
          <div class="flex items-center justify-between mb-1">
            <span class="text-xs font-medium text-slate-800">{{ humanize(a.requestType) }}</span>
            <span class="text-xs font-medium px-2 py-0.5 rounded-full" :class="statusClass(a.status)">{{ a.status }}</span>
          </div>
          <div class="text-[10px] text-slate-500">
            <span v-if="a.requestedBy">By {{ a.requestedBy }}</span>
            <span v-if="a.reviewedBy"> &middot; Reviewed by {{ a.reviewedBy }}</span>
          </div>
          <div class="text-[10px] text-slate-400 font-mono mt-0.5">{{ formatDate(a.createdAt) }}</div>
        </div>
        <div v-if="totalPages > 1" class="flex items-center justify-between pt-2">
          <span class="text-xs text-slate-500">{{ page + 1 }}/{{ totalPages }}</span>
          <div class="flex gap-1">
            <button @click="page = Math.max(0, page - 1)" :disabled="page === 0" class="btn-sm">Prev</button>
            <button @click="page = Math.min(totalPages - 1, page + 1)" :disabled="page >= totalPages - 1" class="btn-sm">Next</button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { getPortalApprovals } from '@/api/auditorPortal'
import SkeletonLoader from './components/SkeletonLoader.vue'
import ErrorCard from './components/ErrorCard.vue'

const props = defineProps({ token: String, metadata: Object, scope: Object })

const loading = ref(true)
const approvals = ref([])
const search = ref('')
const statusFilter = ref('')
const page = ref(0)
const PAGE_SIZE = 50
const error = ref(false)

const filtered = computed(() => {
  let result = approvals.value
  const q = search.value.toLowerCase()
  if (q) {
    result = result.filter(a =>
      (a.requestedBy || '').toLowerCase().includes(q) ||
      (a.reviewedBy || '').toLowerCase().includes(q) ||
      (a.requestType || '').toLowerCase().includes(q)
    )
  }
  if (statusFilter.value) {
    result = result.filter(a => a.status === statusFilter.value)
  }
  return result
})

const totalPages = computed(() => Math.ceil(filtered.value.length / PAGE_SIZE))
const paged = computed(() => filtered.value.slice(page.value * PAGE_SIZE, (page.value + 1) * PAGE_SIZE))

watch([search, statusFilter], () => { page.value = 0 })

function statusClass(status) {
  switch (status) {
    case 'APPROVED':      return 'bg-green-100 text-green-700'
    case 'REJECTED':      return 'bg-red-100 text-red-700'
    case 'PENDING':       return 'bg-amber-100 text-amber-700'
    default:              return 'bg-slate-100 text-slate-600'
  }
}

function humanize(val) {
  if (!val) return '—'
  return val.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
}

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
}

async function load() {
  loading.value = true
  error.value = false
  try {
    const { data } = await getPortalApprovals(props.token)
    approvals.value = data
  } catch { error.value = true }
  loading.value = false
}
onMounted(load)
</script>

<style scoped>
@reference "tailwindcss";
.input-sm { @apply border border-slate-200 rounded-lg px-3 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-slate-300; }
.btn-sm { @apply px-3 py-1 border border-slate-200 text-slate-600 rounded-lg text-xs hover:bg-slate-50 disabled:opacity-50; }
</style>
