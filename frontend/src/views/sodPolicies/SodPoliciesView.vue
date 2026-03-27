<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Separation of Duties Policies</h1>
        <p class="text-sm text-gray-500 mt-1">Define conflicting group pairs to detect segregation of duties violations</p>
      </div>
      <router-link :to="{ name: 'sodPolicyCreate', params: { dirId } }" v-if="dirId" class="btn-primary text-sm">
          New Policy
        </router-link>
    </div>

    <!-- Directory picker + Run Scan -->
    <div v-if="showPicker" class="mb-4 flex items-end gap-3">
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">Directory</label>
        <select v-model="selectedDir" class="w-64 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="" disabled>{{ loadingDirs ? 'Loading…' : '— Select directory —' }}</option>
          <option v-for="d in directories" :key="d.id" :value="d.id">{{ d.displayName }}</option>
        </select>
      </div>
      <button @click="handleScan" :disabled="scanning || !dirId" class="btn-secondary text-sm h-[38px]">
        {{ scanning ? 'Scanning...' : 'Run Scan' }}
      </button>
    </div>
    <div v-else class="mb-4">
      <button @click="handleScan" :disabled="scanning || !dirId" class="btn-secondary text-sm">
        {{ scanning ? 'Scanning...' : 'Run Scan' }}
      </button>
    </div>

    <!-- Scan result banner -->
    <div v-if="scanResult" class="mb-4 bg-blue-50 border border-blue-200 rounded-lg p-3 text-sm flex justify-between items-center">
      <span>
        Scan complete: {{ scanResult.policiesScanned }} policies scanned,
        {{ scanResult.violationsFound }} violations found
        ({{ scanResult.newViolations }} new, {{ scanResult.resolvedViolations }} resolved)
      </span>
      <button @click="scanResult = null" class="text-blue-400 hover:text-blue-600">Dismiss</button>
    </div>

    <div v-if="loading && !policies.length" class="text-gray-500">Loading...</div>

    <div v-if="!loading && !policies.length" class="text-gray-500 bg-white rounded-lg border p-8 text-center">
      No SoD policies defined yet. Create one to start enforcing separation of duties.
    </div>

    <div class="space-y-3">
      <div v-for="p in policies" :key="p.id"
        class="bg-white rounded-lg border p-4 hover:shadow-sm transition-shadow cursor-pointer"
        @click="$router.push({ name: 'sodPolicyEdit', params: { dirId, policyId: p.id } })">
        <div class="flex items-start justify-between">
          <div>
            <div class="flex items-center gap-2 mb-1">
              <h3 class="font-semibold text-gray-900">{{ p.name }}</h3>
              <span :class="severityClass(p.severity)">{{ p.severity }}</span>
              <span :class="actionClass(p.action)">{{ p.action }}</span>
              <span v-if="!p.enabled" class="px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-500">DISABLED</span>
            </div>
            <p v-if="p.description" class="text-sm text-gray-500 mb-2">{{ p.description }}</p>
            <div class="flex gap-4 text-xs text-gray-500">
              <span>Group A: <strong>{{ p.groupAName || p.groupADn }}</strong></span>
              <span>Group B: <strong>{{ p.groupBName || p.groupBDn }}</strong></span>
            </div>
          </div>
          <div class="text-right">
            <div v-if="p.openViolationCount > 0"
              class="text-red-600 font-bold text-lg">{{ p.openViolationCount }}</div>
            <div v-else class="text-green-600 font-bold text-lg">0</div>
            <div class="text-xs text-gray-500">violations</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Violations link -->
    <div class="mt-6">
      <router-link :to="{ name: 'sodViolations', params: { dirId } }" class="text-sm text-blue-600 hover:underline">
        View all violations &rarr;
      </router-link>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useApi } from '@/composables/useApi'
import { useDirectoryPicker } from '@/composables/useDirectoryPicker'
import { listPolicies, scanDirectory } from '@/api/sodPolicies'

const { dirId, directories, selectedDir, loadingDirs, showPicker } = useDirectoryPicker()
const { loading, call } = useApi()

const policies = ref([])
const scanning = ref(false)
const scanResult = ref(null)

function severityClass(s) {
  const base = 'px-2 py-0.5 rounded-full text-xs font-medium'
  switch (s) {
    case 'CRITICAL': return base + ' bg-red-200 text-red-900'
    case 'HIGH': return base + ' bg-red-100 text-red-800'
    case 'MEDIUM': return base + ' bg-yellow-100 text-yellow-800'
    case 'LOW': return base + ' bg-blue-100 text-blue-800'
    default: return base + ' bg-gray-100 text-gray-800'
  }
}

function actionClass(a) {
  const base = 'px-2 py-0.5 rounded-full text-xs font-medium'
  return a === 'BLOCK'
    ? base + ' bg-red-100 text-red-800'
    : base + ' bg-yellow-100 text-yellow-800'
}

async function handleScan() {
  scanning.value = true
  try {
    const res = await call(() => scanDirectory(dirId.value), { successMsg: 'Scan complete' })
    scanResult.value = res.data
    await loadPolicies()
  } catch { /* handled */ }
  scanning.value = false
}

async function loadPolicies() {
  try {
    const res = await call(() => listPolicies(dirId.value))
    policies.value = res.data
  } catch { /* handled */ }
}

watch(dirId, (v) => { if (v) loadPolicies() })
onMounted(() => { if (dirId.value) loadPolicies() })
</script>

<style scoped>
@reference "tailwindcss";
</style>
