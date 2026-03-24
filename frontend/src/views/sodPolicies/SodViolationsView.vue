<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">SoD Violations</h1>
      <div class="flex gap-2 items-center">
        <label class="text-sm text-gray-600">Filter:</label>
        <select v-model="statusFilter" @change="loadViolations"
          class="border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500">
          <option value="">All</option>
          <option value="OPEN">Open</option>
          <option value="RESOLVED">Resolved</option>
          <option value="EXEMPTED">Exempted</option>
        </select>
      </div>
    </div>

    <div v-if="loading && !violations.length" class="text-gray-500">Loading...</div>

    <div v-if="!loading && !violations.length" class="text-gray-500 bg-white rounded-lg border p-8 text-center">
      No violations found{{ statusFilter ? ' with status ' + statusFilter : '' }}.
    </div>

    <div class="space-y-2">
      <div v-for="v in violations" :key="v.id"
        class="bg-white rounded-lg border p-4 flex items-center gap-4">
        <span :class="statusBadge(v.status)">{{ v.status }}</span>
        <div class="flex-1 min-w-0">
          <div class="font-medium text-gray-900 text-sm truncate">{{ v.userDisplayName || v.userDn }}</div>
          <div class="text-xs text-gray-500 truncate">
            Policy: <strong>{{ v.policyName }}</strong> &mdash; {{ v.userDn }}
          </div>
          <div class="text-xs text-gray-400">
            Detected: {{ fmtDate(v.detectedAt) }}
            <template v-if="v.resolvedAt"> &mdash; Resolved: {{ fmtDate(v.resolvedAt) }}</template>
          </div>
          <div v-if="v.exemptionReason" class="text-xs text-gray-500 mt-1">
            Exempted by {{ v.exemptedByUsername }}: {{ v.exemptionReason }}
          </div>
        </div>
        <button v-if="v.status === 'OPEN'" @click="openExempt(v)" class="btn-secondary text-xs shrink-0">
          Exempt
        </button>
      </div>
    </div>

    <!-- Exempt dialog -->
    <div v-if="exemptDialog" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50" @click.self="exemptDialog = null">
      <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-md">
        <h3 class="font-semibold text-gray-900 mb-3">Exempt Violation</h3>
        <p class="text-sm text-gray-600 mb-3">
          User: <strong>{{ exemptDialog.userDisplayName || exemptDialog.userDn }}</strong><br/>
          Policy: <strong>{{ exemptDialog.policyName }}</strong>
        </p>
        <div class="mb-4">
          <label class="block text-sm font-medium text-gray-700 mb-1">Reason *</label>
          <textarea v-model="exemptReason" rows="3" required
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            placeholder="Business justification for this exemption..."></textarea>
        </div>
        <div class="flex gap-2 justify-end">
          <button @click="exemptDialog = null" class="btn-secondary text-sm">Cancel</button>
          <button @click="submitExempt" :disabled="!exemptReason.trim() || loading" class="btn-primary text-sm">
            {{ loading ? 'Saving...' : 'Confirm Exemption' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useApi } from '@/composables/useApi'
import { listViolations, exemptViolation } from '@/api/sodPolicies'

const route = useRoute()
const { loading, call } = useApi()
const dirId = route.params.dirId

const violations = ref([])
const statusFilter = ref('OPEN')
const exemptDialog = ref(null)
const exemptReason = ref('')

function statusBadge(s) {
  const base = 'px-2 py-0.5 rounded-full text-xs font-medium shrink-0'
  switch (s) {
    case 'OPEN': return base + ' bg-red-100 text-red-800'
    case 'RESOLVED': return base + ' bg-green-100 text-green-800'
    case 'EXEMPTED': return base + ' bg-yellow-100 text-yellow-800'
    default: return base + ' bg-gray-100 text-gray-800'
  }
}

function fmtDate(val) {
  if (!val) return ''
  return new Date(val).toLocaleString()
}

function openExempt(v) {
  exemptDialog.value = v
  exemptReason.value = ''
}

async function submitExempt() {
  try {
    await call(() => exemptViolation(dirId, exemptDialog.value.id, { reason: exemptReason.value }),
      { successMsg: 'Violation exempted' })
    exemptDialog.value = null
    await loadViolations()
  } catch { /* handled */ }
}

async function loadViolations() {
  try {
    const params = statusFilter.value ? { status: statusFilter.value } : {}
    const res = await call(() => listViolations(dirId, params))
    violations.value = res.data
  } catch { /* handled */ }
}

onMounted(loadViolations)
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary   { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
.btn-secondary { @apply px-3 py-1.5 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50; }
</style>
