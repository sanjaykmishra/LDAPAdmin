<template>
  <div>
    <h1 class="text-xl font-bold text-slate-900 mb-4">Separation of Duties</h1>

    <div v-if="loading" class="text-sm text-slate-500">Loading SoD data...</div>

    <template v-else>
      <!-- Zero-state: no violations -->
      <div v-if="policies.length > 0 && violations.length === 0"
           class="bg-green-50 border border-green-200 rounded-xl p-5 mb-6 flex items-center gap-3">
        <svg class="w-6 h-6 text-green-500 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <div>
          <div class="text-sm font-medium text-green-800">No Separation of Duties violations detected</div>
          <div class="text-xs text-green-600">All {{ policies.length }} {{ policies.length === 1 ? 'policy' : 'policies' }} passed without violations</div>
        </div>
      </div>

      <!-- Policies table -->
      <section class="bg-white border border-slate-200 rounded-xl overflow-hidden mb-6">
        <div class="px-4 py-3 border-b border-slate-200">
          <h2 class="text-sm font-semibold text-slate-700">Policies ({{ policies.length }})</h2>
        </div>
        <div v-if="policies.length === 0" class="p-6 text-center text-sm text-slate-400">No policies configured.</div>
        <div v-else class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-slate-200 bg-slate-50">
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Name</th>
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Group A</th>
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Group B</th>
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Severity</th>
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Action</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in policies" :key="p.id" class="border-b border-slate-100 hover:bg-slate-50/50">
                <td class="py-2 px-4">
                  <div class="text-xs font-medium text-slate-800">{{ p.name }}</div>
                  <div v-if="p.description" class="text-[10px] text-slate-400">{{ p.description }}</div>
                </td>
                <td class="py-2 px-4 text-xs text-slate-600">{{ p.groupAName }}</td>
                <td class="py-2 px-4 text-xs text-slate-600">{{ p.groupBName }}</td>
                <td class="py-2 px-4">
                  <span class="text-xs font-medium px-2 py-0.5 rounded-full" :class="severityClass(p.severity)">{{ p.severity }}</span>
                </td>
                <td class="py-2 px-4 text-xs text-slate-600">{{ p.action }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- Violations table -->
      <section v-if="violations.length > 0" class="bg-white border border-slate-200 rounded-xl overflow-hidden">
        <div class="px-4 py-3 border-b border-slate-200 flex items-center justify-between">
          <h2 class="text-sm font-semibold text-slate-700">Violations ({{ filteredViolations.length }})</h2>
          <select v-model="statusFilter" class="border border-slate-200 rounded-lg px-2 py-1 text-xs">
            <option value="">All Statuses</option>
            <option value="OPEN">Open</option>
            <option value="RESOLVED">Resolved</option>
            <option value="EXEMPTED">Exempted</option>
          </select>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-slate-200 bg-slate-50">
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">User</th>
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Policy</th>
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Status</th>
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Detected</th>
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Resolved</th>
                <th class="w-8"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="v in filteredViolations" :key="v.id"
                  :id="`violation-${v.id}`"
                  class="border-b border-slate-100 hover:bg-slate-50/50">
                <td class="py-2 px-4">
                  <div class="text-xs font-medium text-slate-800">{{ v.userDisplayName || '—' }}</div>
                  <div class="text-[10px] text-slate-400 font-mono truncate max-w-xs" :title="v.userDn">{{ v.userDn }}</div>
                </td>
                <td class="py-2 px-4 text-xs text-slate-600">{{ v.policyName }}</td>
                <td class="py-2 px-4">
                  <span class="text-xs font-medium px-2 py-0.5 rounded-full" :class="violationStatusClass(v.status)">{{ v.status }}</span>
                </td>
                <td class="py-2 px-4 text-xs text-slate-500 font-mono">{{ formatDate(v.detectedAt) }}</td>
                <td class="py-2 px-4 text-xs text-slate-500 font-mono">{{ formatDate(v.resolvedAt) }}</td>
                <td class="py-2 px-1"><CopyLinkButton :anchor="`violation-${v.id}`" /></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getPortalSod } from '@/api/auditorPortal'
import CopyLinkButton from './components/CopyLinkButton.vue'

const props = defineProps({ token: String, metadata: Object, scope: Object })

const loading = ref(true)
const policies = ref([])
const violations = ref([])
const statusFilter = ref('')

const filteredViolations = computed(() => {
  if (!statusFilter.value) return violations.value
  return violations.value.filter(v => v.status === statusFilter.value)
})

function severityClass(sev) {
  switch (sev) {
    case 'CRITICAL': return 'bg-red-100 text-red-700'
    case 'HIGH':     return 'bg-orange-100 text-orange-700'
    case 'MEDIUM':   return 'bg-amber-100 text-amber-700'
    case 'LOW':      return 'bg-slate-100 text-slate-600'
    default:         return 'bg-slate-100 text-slate-600'
  }
}

function violationStatusClass(status) {
  switch (status) {
    case 'OPEN':     return 'bg-red-100 text-red-700'
    case 'RESOLVED': return 'bg-green-100 text-green-700'
    case 'EXEMPTED': return 'bg-amber-100 text-amber-700'
    default:         return 'bg-slate-100 text-slate-600'
  }
}

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
}

onMounted(async () => {
  try {
    const { data } = await getPortalSod(props.token)
    policies.value = data.policies || []
    violations.value = data.violations || []
  } catch { /* handled by layout */ }
  loading.value = false

  if (window.location.hash) {
    const el = document.getElementById(window.location.hash.slice(1))
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
})
</script>
