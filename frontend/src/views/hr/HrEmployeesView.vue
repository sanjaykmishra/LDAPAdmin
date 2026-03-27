<template>
  <div class="p-6 max-w-6xl mx-auto">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">HR Employees</h1>
        <p class="text-sm text-gray-500 mt-1">HR employee records and LDAP account matching</p>
      </div>
      <RouterLink :to="{ path: `/directories/${dirId}/hr` }" class="text-sm text-blue-600 hover:text-blue-800">
        ← Back to HR Connection
      </RouterLink>
    </div>

    <!-- Error -->
    <div v-if="error" class="bg-red-50 border border-red-200 text-red-800 px-4 py-3 rounded-lg mb-6 text-sm">{{ error }}</div>

    <!-- Tab filters -->
    <div class="flex gap-2 mb-4">
      <button v-for="tab in tabs" :key="tab.value"
        @click="activeTab = tab.value"
        :class="activeTab === tab.value ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'"
        class="px-3 py-1.5 rounded-lg text-sm font-medium transition-colors">
        {{ tab.label }}
      </button>
    </div>

    <!-- Employees table -->
    <div class="bg-white rounded-xl shadow overflow-hidden">
      <table class="w-full text-sm">
        <thead>
          <tr class="text-left text-gray-500 border-b bg-gray-50">
            <th class="px-4 py-3">Name</th>
            <th class="px-4 py-3">Email</th>
            <th class="px-4 py-3">Department</th>
            <th class="px-4 py-3">Status</th>
            <th class="px-4 py-3">Hire Date</th>
            <th class="px-4 py-3">LDAP Match</th>
            <th class="px-4 py-3">Confidence</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(emp, i) in employees" :key="emp.id" :class="i % 2 === 1 ? 'bg-gray-50' : ''">
            <td class="px-4 py-3 font-medium text-gray-900">{{ emp.displayName || `${emp.firstName} ${emp.lastName}` }}</td>
            <td class="px-4 py-3 text-gray-600">{{ emp.workEmail }}</td>
            <td class="px-4 py-3 text-gray-600">{{ emp.department }}</td>
            <td class="px-4 py-3">
              <span :class="statusClass(emp.status)" class="px-2 py-0.5 rounded text-xs font-medium">
                {{ emp.status }}
              </span>
            </td>
            <td class="px-4 py-3 text-gray-600">{{ emp.hireDate }}</td>
            <td class="px-4 py-3 text-gray-600">{{ emp.matchedLdapDn || '—' }}</td>
            <td class="px-4 py-3">
              <span v-if="emp.matchConfidence" :class="confidenceClass(emp.matchConfidence)" class="px-2 py-0.5 rounded text-xs font-medium">
                {{ emp.matchConfidence }}
              </span>
              <span v-else class="text-gray-400 text-xs">—</span>
            </td>
          </tr>
          <tr v-if="!employees.length && !loading">
            <td colspan="7" class="px-4 py-8 text-center text-gray-400">No employees found</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Pagination -->
    <div v-if="totalPages > 1" class="flex justify-center gap-2 mt-4">
      <button @click="page--; loadEmployees()" :disabled="page === 0" class="px-3 py-1.5 bg-gray-100 rounded text-sm disabled:opacity-50">Previous</button>
      <span class="px-3 py-1.5 text-sm text-gray-600">Page {{ page + 1 }} of {{ totalPages }}</span>
      <button @click="page++; loadEmployees()" :disabled="page >= totalPages - 1" class="px-3 py-1.5 bg-gray-100 rounded text-sm disabled:opacity-50">Next</button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { listHrEmployees, listOrphanedAccounts } from '@/api/hrIntegration'

const route = useRoute()
const dirId = route.params.dirId

const tabs = [
  { label: 'All', value: null },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Terminated', value: 'TERMINATED' },
  { label: 'Orphaned', value: 'ORPHANED' },
]

const activeTab = ref(null)
const employees = ref([])
const loading = ref(true)
const error = ref('')
const page = ref(0)
const totalPages = ref(0)

onMounted(() => loadEmployees())

watch(activeTab, () => {
  page.value = 0
  loadEmployees()
})

async function loadEmployees() {
  loading.value = true
  error.value = ''
  try {
    if (activeTab.value === 'ORPHANED') {
      const { data } = await listOrphanedAccounts(dirId)
      employees.value = data
      totalPages.value = 1
    } else {
      const params = { page: page.value, size: 50 }
      if (activeTab.value) params.status = activeTab.value
      const { data } = await listHrEmployees(dirId, params)
      employees.value = data.content || []
      totalPages.value = data.totalPages || 1
    }
  } catch (e) {
    error.value = e.response?.data?.detail || 'Failed to load employees'
  } finally {
    loading.value = false
  }
}

function statusClass(status) {
  switch (status) {
    case 'ACTIVE': return 'bg-green-100 text-green-800'
    case 'TERMINATED': return 'bg-red-100 text-red-800'
    case 'ON_LEAVE': return 'bg-yellow-100 text-yellow-800'
    default: return 'bg-gray-100 text-gray-600'
  }
}

function confidenceClass(confidence) {
  switch (confidence) {
    case 'EXACT': return 'bg-green-100 text-green-800'
    case 'FUZZY': return 'bg-yellow-100 text-yellow-800'
    case 'NONE': return 'bg-red-100 text-red-800'
    default: return 'bg-gray-100 text-gray-600'
  }
}
</script>
