<template>
  <div class="p-6 max-w-5xl">
    <h1 class="text-2xl font-bold text-gray-900 mb-6">HR Integration</h1>
    <p class="text-sm text-gray-500 mt-1">Connect to your HR system for identity lifecycle management</p>

    <!-- Directory picker -->
    <div v-if="showPicker" class="mb-6">
      <label class="block text-sm font-medium text-gray-700 mb-1">Directory</label>
      <select v-model="selectedDir" class="input w-64">
        <option value="" disabled>{{ loadingDirs ? 'Loading...' : '-- Select directory --' }}</option>
        <option v-for="d in directories" :key="d.id" :value="d.id">{{ d.displayName }}</option>
      </select>
    </div>

    <!-- Error -->
    <div v-if="error" class="bg-red-50 border border-red-200 text-red-800 px-4 py-3 rounded-xl mb-6 text-sm flex items-center gap-2">
      <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
      </svg>
      {{ error }}
    </div>

    <!-- Loading skeleton -->
    <div v-if="loading" class="space-y-4">
      <div class="grid grid-cols-3 gap-5">
        <div v-for="i in 3" :key="i" class="bg-white border border-gray-200 rounded-xl p-5 animate-pulse">
          <div class="h-3 bg-gray-200 rounded w-1/2 mb-3" />
          <div class="h-7 bg-gray-200 rounded w-1/3" />
        </div>
      </div>
      <div class="bg-white border border-gray-200 rounded-xl p-6 animate-pulse space-y-3">
        <div class="h-5 bg-gray-200 rounded w-1/3" />
        <div class="h-4 bg-gray-100 rounded w-2/3" />
        <div class="h-4 bg-gray-100 rounded w-1/2" />
      </div>
    </div>

    <!-- No connection configured -->
    <div v-if="!loading && !connection && dirId" class="bg-white border border-gray-200 rounded-xl p-12 text-center">
      <div class="w-16 h-16 mx-auto mb-4 bg-blue-50 rounded-full flex items-center justify-center">
        <svg class="w-8 h-8 text-blue-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
          <circle cx="7" cy="5" r="2.5" /><path d="M2 14c0-2.76 2.24-5 5-5s5 2.24 5 5" /><path d="M14 6h4M14 9h3M14 12h2" />
        </svg>
      </div>
      <h2 class="text-lg font-semibold text-gray-900 mb-2">Connect Your HR System</h2>
      <p class="text-sm text-gray-500 mb-6 max-w-md mx-auto">
        Link your BambooHR account to automatically sync employee data, detect orphaned accounts, and streamline onboarding/offboarding.
      </p>
      <button @click="showSetup = true" class="btn-primary">Connect BambooHR</button>
    </div>

    <!-- Setup / Edit form -->
    <div v-if="showSetup || editing" class="bg-white border border-gray-200 rounded-xl p-6 mb-6">
      <h2 class="text-lg font-semibold text-gray-900 mb-5">{{ editing ? 'Edit' : 'Setup' }} BambooHR Connection</h2>
      <form @submit.prevent="saveConnection" class="space-y-6">
        <!-- Connection settings -->
        <fieldset>
          <legend class="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Connection Settings</legend>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Display Name</label>
              <input v-model="form.displayName" required class="input w-full" placeholder="e.g. BambooHR Production" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Subdomain</label>
              <input v-model="form.subdomain" required autocomplete="off" class="input w-full" placeholder="e.g. acme" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">API Key</label>
              <input v-model="form.apiKey" :required="!editing" type="password" autocomplete="new-password" class="input w-full" :placeholder="editing ? '(unchanged)' : 'Enter API key'" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Sync Schedule (Cron)</label>
              <input v-model="form.syncCron" class="input w-full" placeholder="0 0 * * * ?" />
            </div>
          </div>
        </fieldset>

        <!-- Matching rules -->
        <fieldset>
          <legend class="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Matching Rules</legend>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">LDAP Match Attribute</label>
              <select v-model="form.matchAttribute" class="input w-full">
                <option value="mail">mail</option>
                <option value="uid">uid</option>
                <option value="employeeNumber">employeeNumber</option>
              </select>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">HR Match Field</label>
              <select v-model="form.matchField" class="input w-full">
                <option value="workEmail">Work Email</option>
                <option value="employeeId">Employee ID</option>
              </select>
            </div>
          </div>
          <p class="text-xs text-gray-400 mt-2">Employees are matched to LDAP users by comparing the HR field to the LDAP attribute.</p>
        </fieldset>

        <div class="flex gap-3 pt-2">
          <button type="submit" :disabled="saving" class="btn-primary">
            {{ saving ? 'Saving...' : (editing ? 'Save Changes' : 'Create Connection') }}
          </button>
          <button type="button" @click="testConn" :disabled="testing" class="btn-secondary">
            {{ testing ? 'Testing...' : 'Test Connection' }}
          </button>
          <button type="button" @click="cancelEdit" class="btn-neutral">Cancel</button>
        </div>
        <div v-if="testResult" class="text-sm mt-2 flex items-center gap-2" :class="testResult.success ? 'text-green-700' : 'text-red-700'">
          <svg v-if="testResult.success" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <svg v-else class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
          </svg>
          {{ testResult.message }}
        </div>
      </form>
    </div>

    <!-- Connection details + summary -->
    <template v-if="connection && !editing && !showSetup">
      <!-- Summary cards -->
      <div v-if="summary" class="grid grid-cols-3 gap-5 mb-6">
        <div class="bg-white border border-gray-200 rounded-xl p-5">
          <div class="flex items-center justify-between mb-2">
            <p class="text-xs font-medium text-gray-500 uppercase tracking-wider">Total Employees</p>
            <div class="p-1.5 bg-blue-50 rounded-lg">
              <svg class="w-4 h-4 text-blue-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
                <path stroke-linecap="round" stroke-linejoin="round" d="M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 018.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0111.964-3.07M12 6.375a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zm8.25 2.25a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z" />
              </svg>
            </div>
          </div>
          <p class="text-2xl font-bold text-gray-900">{{ summary.totalEmployees }}</p>
        </div>
        <div class="bg-white border border-gray-200 rounded-xl p-5">
          <div class="flex items-center justify-between mb-2">
            <p class="text-xs font-medium text-gray-500 uppercase tracking-wider">Matched to LDAP</p>
            <div class="p-1.5 bg-green-50 rounded-lg">
              <svg class="w-4 h-4 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
                <path stroke-linecap="round" stroke-linejoin="round" d="M13.19 8.688a4.5 4.5 0 011.242 7.244l-4.5 4.5a4.5 4.5 0 01-6.364-6.364l1.757-1.757" />
              </svg>
            </div>
          </div>
          <p class="text-2xl font-bold text-green-600">{{ summary.matchedCount }}</p>
        </div>
        <div class="bg-white border border-gray-200 rounded-xl p-5">
          <div class="flex items-center justify-between mb-2">
            <p class="text-xs font-medium text-gray-500 uppercase tracking-wider">Orphaned Accounts</p>
            <div class="p-1.5 rounded-lg" :class="summary.orphanedCount > 0 ? 'bg-red-50' : 'bg-gray-100'">
              <svg class="w-4 h-4" :class="summary.orphanedCount > 0 ? 'text-red-500' : 'text-gray-400'" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126z" />
              </svg>
            </div>
          </div>
          <p class="text-2xl font-bold" :class="summary.orphanedCount > 0 ? 'text-red-600' : 'text-gray-900'">{{ summary.orphanedCount }}</p>
        </div>
      </div>

      <!-- Connection info card -->
      <div class="bg-white border border-gray-200 rounded-xl p-6 mb-6">
        <div class="flex items-center justify-between mb-5">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 bg-blue-50 rounded-lg flex items-center justify-center">
              <svg class="w-5 h-5 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
                <circle cx="7" cy="5" r="2.5" /><path d="M2 14c0-2.76 2.24-5 5-5s5 2.24 5 5" /><path d="M14 6h4M14 9h3M14 12h2" />
              </svg>
            </div>
            <div>
              <h2 class="text-base font-semibold text-gray-900">{{ connection.displayName }}</h2>
              <p class="text-xs text-gray-500">{{ connection.subdomain }}.bamboohr.com</p>
            </div>
          </div>
          <span :class="connection.enabled ? 'badge-green' : 'badge-gray'">
            {{ connection.enabled ? 'Enabled' : 'Disabled' }}
          </span>
        </div>

        <div class="grid grid-cols-3 gap-5 text-sm mb-5 pb-5 border-b border-gray-100">
          <div>
            <p class="text-xs text-gray-400 mb-0.5">Match Rule</p>
            <p class="text-gray-700 font-medium">{{ connection.matchField }} <span class="text-gray-400">&rarr;</span> {{ connection.matchAttribute }}</p>
          </div>
          <div>
            <p class="text-xs text-gray-400 mb-0.5">Sync Schedule</p>
            <p class="text-gray-700 font-medium font-mono text-xs">{{ connection.syncCron }}</p>
          </div>
          <div>
            <p class="text-xs text-gray-400 mb-0.5">Last Sync</p>
            <p v-if="connection.lastSyncAt" class="text-gray-700">
              {{ new Date(connection.lastSyncAt).toLocaleString() }}
              <span :class="connection.lastSyncStatus === 'SUCCESS' ? 'text-green-600' : 'text-red-600'" class="font-medium ml-1">{{ connection.lastSyncStatus }}</span>
              <span v-if="connection.lastSyncEmployeeCount" class="text-gray-400 ml-1">({{ connection.lastSyncEmployeeCount }})</span>
            </p>
            <p v-else class="text-gray-400">Never</p>
          </div>
        </div>

        <div class="flex gap-3">
          <button @click="triggerSync" :disabled="syncing" class="btn-primary">
            {{ syncing ? 'Syncing...' : 'Sync Now' }}
          </button>
          <button @click="startEdit" class="btn-secondary">Edit</button>
          <button @click="toggleEnabled" class="btn-secondary">
            {{ connection.enabled ? 'Disable' : 'Enable' }}
          </button>
          <RouterLink :to="{ path: `/directories/${dirId}/hr/employees` }" class="btn-secondary inline-flex items-center gap-1.5">
            View Employees
            <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5L21 12m0 0l-7.5 7.5M21 12H3" />
            </svg>
          </RouterLink>
        </div>
      </div>

      <!-- Sync history -->
      <div class="bg-white border border-gray-200 rounded-xl overflow-hidden">
        <div class="px-6 py-4 border-b border-gray-200">
          <h2 class="text-sm font-semibold text-gray-700">Sync History</h2>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead class="bg-gray-50">
              <tr>
                <th class="text-left px-4 py-2.5 text-xs font-semibold text-gray-500 uppercase tracking-wider">Started</th>
                <th class="text-left px-4 py-2.5 text-xs font-semibold text-gray-500 uppercase tracking-wider">Status</th>
                <th class="text-left px-4 py-2.5 text-xs font-semibold text-gray-500 uppercase tracking-wider">Trigger</th>
                <th class="text-left px-4 py-2.5 text-xs font-semibold text-gray-500 uppercase tracking-wider">Total</th>
                <th class="text-left px-4 py-2.5 text-xs font-semibold text-gray-500 uppercase tracking-wider">New</th>
                <th class="text-left px-4 py-2.5 text-xs font-semibold text-gray-500 uppercase tracking-wider">Matched</th>
                <th class="text-left px-4 py-2.5 text-xs font-semibold text-gray-500 uppercase tracking-wider">Orphaned</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
              <tr v-for="run in syncHistory" :key="run.id" class="hover:bg-gray-50">
                <td class="px-4 py-2.5 text-gray-600 font-mono text-xs">{{ new Date(run.startedAt).toLocaleString() }}</td>
                <td class="px-4 py-2.5">
                  <span :class="syncStatusBadge(run.status)">{{ run.status }}</span>
                </td>
                <td class="px-4 py-2.5 text-gray-600">{{ run.triggeredBy }}</td>
                <td class="px-4 py-2.5 text-gray-700 font-medium">{{ run.totalEmployees ?? '-' }}</td>
                <td class="px-4 py-2.5 text-gray-600">{{ run.newEmployees }}</td>
                <td class="px-4 py-2.5 text-green-600 font-medium">{{ run.matchedCount }}</td>
                <td class="px-4 py-2.5" :class="run.orphanedCount > 0 ? 'text-red-600 font-medium' : 'text-gray-600'">{{ run.orphanedCount }}</td>
              </tr>
              <tr v-if="!syncHistory.length">
                <td colspan="7" class="px-4 py-8 text-center text-gray-400 text-sm">No sync runs yet. Click "Sync Now" to run the first sync.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { useDirectoryPicker } from '@/composables/useDirectoryPicker'
import {
  getHrConnection, createHrConnection, updateHrConnection,
  testHrConnection, triggerHrSync as apiTriggerSync,
  getHrSyncHistory, getHrSummary
} from '@/api/hrIntegration'

const { dirId, directories, selectedDir, loadingDirs, showPicker } = useDirectoryPicker()

const loading = ref(true)
const error = ref('')
const connection = ref(null)
const summary = ref(null)
const syncHistory = ref([])
const showSetup = ref(false)
const editing = ref(false)
const saving = ref(false)
const testing = ref(false)
const syncing = ref(false)
const testResult = ref(null)

const form = ref({
  displayName: '',
  subdomain: '',
  apiKey: '',
  matchAttribute: 'mail',
  matchField: 'workEmail',
  syncCron: '0 0 * * * ?',
})

function syncStatusBadge(status) {
  switch (status) {
    case 'SUCCESS': return 'badge-green'
    case 'FAILED':  return 'badge-red'
    default:        return 'badge-yellow'
  }
}

async function initLoad() {
  if (!dirId.value) { loading.value = false; return }
  await loadConnection()
  loading.value = false
}
watch(dirId, () => { loading.value = true; connection.value = null; initLoad() })
onMounted(initLoad)

async function loadConnection() {
  try {
    const { data } = await getHrConnection(dirId.value)
    connection.value = data
    await Promise.all([loadSummary(), loadHistory()])
  } catch (e) {
    if (e.response?.status !== 404) {
      error.value = e.response?.data?.detail || 'Failed to load HR connection'
    }
    connection.value = null
  }
}

async function loadSummary() {
  try {
    const { data } = await getHrSummary(dirId.value)
    summary.value = data
  } catch { /* ignore */ }
}

async function loadHistory() {
  try {
    const { data } = await getHrSyncHistory(dirId.value, { page: 0, size: 10 })
    syncHistory.value = data.content || []
  } catch { /* ignore */ }
}

async function saveConnection() {
  saving.value = true
  error.value = ''
  try {
    if (editing.value) {
      const payload = { ...form.value }
      if (!payload.apiKey) delete payload.apiKey
      await updateHrConnection(dirId.value, payload)
    } else {
      await createHrConnection(dirId.value, form.value)
    }
    showSetup.value = false
    editing.value = false
    await loadConnection()
  } catch (e) {
    error.value = e.response?.data?.detail || 'Failed to save connection'
  } finally {
    saving.value = false
  }
}

async function testConn() {
  testing.value = true
  testResult.value = null
  try {
    const { data } = await testHrConnection(dirId.value, {
      displayName: form.value.displayName || 'Test',
      subdomain: form.value.subdomain,
      apiKey: form.value.apiKey,
    })
    testResult.value = data
  } catch (e) {
    testResult.value = { success: false, message: e.response?.data?.detail || 'Test failed' }
  } finally {
    testing.value = false
  }
}

async function triggerSync() {
  syncing.value = true
  try {
    await apiTriggerSync(dirId.value)
    await loadConnection()
  } catch (e) {
    error.value = e.response?.data?.detail || 'Sync failed'
  } finally {
    syncing.value = false
  }
}

function startEdit() {
  form.value = {
    displayName: connection.value.displayName,
    subdomain: connection.value.subdomain,
    apiKey: '',
    matchAttribute: connection.value.matchAttribute,
    matchField: connection.value.matchField,
    syncCron: connection.value.syncCron,
  }
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  showSetup.value = false
  testResult.value = null
}

async function toggleEnabled() {
  try {
    await updateHrConnection(dirId.value, { enabled: !connection.value.enabled })
    await loadConnection()
  } catch (e) {
    error.value = e.response?.data?.detail || 'Failed to update'
  }
}
</script>
