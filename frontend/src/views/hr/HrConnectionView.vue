<template>
  <div class="p-6 max-w-5xl">
    <h1 class="text-2xl font-bold text-gray-900 mb-4">HR Integration</h1>

    <!-- Directory picker -->
    <div v-if="showPicker" class="mb-4">
      <label class="block text-sm font-medium text-gray-700 mb-1">Directory</label>
      <select v-model="selectedDir" class="w-64 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
        <option value="" disabled>{{ loadingDirs ? 'Loading…' : '— Select directory —' }}</option>
        <option v-for="d in directories" :key="d.id" :value="d.id">{{ d.displayName }}</option>
      </select>
    </div>

    <!-- Error -->
    <div v-if="error" class="bg-red-50 border border-red-200 text-red-800 px-4 py-3 rounded-lg mb-6 text-sm">{{ error }}</div>

    <!-- No connection configured -->
    <div v-if="!loading && !connection" class="bg-white rounded-xl shadow p-8 text-center">
      <p class="text-gray-500 mb-4">No HR connection configured for this directory.</p>
      <button @click="showSetup = true" class="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm hover:bg-blue-700">
        Connect BambooHR
      </button>
    </div>

    <!-- Setup / Edit form -->
    <div v-if="showSetup || editing" class="bg-white rounded-xl shadow p-6 mb-6">
      <h2 class="text-lg font-semibold text-gray-900 mb-4">{{ editing ? 'Edit' : 'Setup' }} BambooHR Connection</h2>
      <form @submit.prevent="saveConnection" class="space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Display Name</label>
            <input v-model="form.displayName" required class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" placeholder="e.g. BambooHR Production" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Subdomain</label>
            <input v-model="form.subdomain" required autocomplete="off" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" placeholder="e.g. acme" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">API Key</label>
            <input v-model="form.apiKey" :required="!editing" type="password" autocomplete="new-password" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" :placeholder="editing ? '(unchanged)' : 'Enter API key'" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Sync Schedule (Cron)</label>
            <input v-model="form.syncCron" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" placeholder="0 0 * * * ?" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">LDAP Match Attribute</label>
            <select v-model="form.matchAttribute" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
              <option value="mail">mail</option>
              <option value="uid">uid</option>
              <option value="employeeNumber">employeeNumber</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">HR Match Field</label>
            <select v-model="form.matchField" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
              <option value="workEmail">Work Email</option>
              <option value="employeeId">Employee ID</option>
            </select>
          </div>
        </div>
        <div class="flex gap-3 pt-2">
          <button type="submit" :disabled="saving" class="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm hover:bg-blue-700 disabled:opacity-50">
            {{ saving ? 'Saving...' : (editing ? 'Save Changes' : 'Create Connection') }}
          </button>
          <button type="button" @click="testConn" :disabled="testing" class="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg text-sm hover:bg-gray-200 disabled:opacity-50">
            {{ testing ? 'Testing...' : 'Test Connection' }}
          </button>
          <button type="button" @click="cancelEdit" class="px-4 py-2 text-gray-500 text-sm hover:text-gray-700">Cancel</button>
        </div>
        <div v-if="testResult" class="text-sm mt-2" :class="testResult.success ? 'text-green-700' : 'text-red-700'">
          {{ testResult.message }}
        </div>
      </form>
    </div>

    <!-- Connection details + summary -->
    <template v-if="connection && !editing && !showSetup">
      <!-- Summary cards -->
      <div v-if="summary" class="grid grid-cols-3 gap-4 mb-6">
        <div class="bg-white rounded-xl shadow p-4">
          <p class="text-sm text-gray-500">Total Employees</p>
          <p class="text-2xl font-bold text-gray-900">{{ summary.totalEmployees }}</p>
        </div>
        <div class="bg-white rounded-xl shadow p-4">
          <p class="text-sm text-gray-500">Matched to LDAP</p>
          <p class="text-2xl font-bold text-green-600">{{ summary.matchedCount }}</p>
        </div>
        <div class="bg-white rounded-xl shadow p-4">
          <p class="text-sm text-gray-500">Orphaned Accounts</p>
          <p class="text-2xl font-bold" :class="summary.orphanedCount > 0 ? 'text-red-600' : 'text-gray-900'">{{ summary.orphanedCount }}</p>
        </div>
      </div>

      <!-- Connection info -->
      <div class="bg-white rounded-xl shadow p-6 mb-6">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-lg font-semibold text-gray-900">{{ connection.displayName }}</h2>
          <div class="flex gap-2">
            <span :class="connection.enabled ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-600'" class="px-2 py-1 rounded text-xs font-medium">
              {{ connection.enabled ? 'Enabled' : 'Disabled' }}
            </span>
          </div>
        </div>
        <div class="grid grid-cols-3 gap-4 text-sm text-gray-600 mb-4">
          <div><span class="font-medium text-gray-900">Subdomain:</span> {{ connection.subdomain }}</div>
          <div><span class="font-medium text-gray-900">Match:</span> {{ connection.matchField }} → {{ connection.matchAttribute }}</div>
          <div><span class="font-medium text-gray-900">Schedule:</span> {{ connection.syncCron }}</div>
        </div>
        <div v-if="connection.lastSyncAt" class="text-sm text-gray-500 mb-4">
          Last sync: {{ new Date(connection.lastSyncAt).toLocaleString() }}
          <span :class="connection.lastSyncStatus === 'SUCCESS' ? 'text-green-600' : 'text-red-600'" class="font-medium ml-1">{{ connection.lastSyncStatus }}</span>
          <span v-if="connection.lastSyncEmployeeCount" class="ml-1">({{ connection.lastSyncEmployeeCount }} employees)</span>
        </div>
        <div class="flex gap-3">
          <button @click="triggerSync" :disabled="syncing" class="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm hover:bg-blue-700 disabled:opacity-50">
            {{ syncing ? 'Syncing...' : 'Sync Now' }}
          </button>
          <button @click="startEdit" class="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg text-sm hover:bg-gray-200">Edit</button>
          <button @click="toggleEnabled" class="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg text-sm hover:bg-gray-200">
            {{ connection.enabled ? 'Disable' : 'Enable' }}
          </button>
          <RouterLink :to="{ path: `/directories/${dirId}/hr/employees` }" class="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg text-sm hover:bg-gray-200">
            View Employees
          </RouterLink>
        </div>
      </div>

      <!-- Sync history -->
      <div class="bg-white rounded-xl shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Sync History</h2>
        <table class="w-full text-sm">
          <thead>
            <tr class="text-left text-gray-500 border-b">
              <th class="pb-2">Started</th>
              <th class="pb-2">Status</th>
              <th class="pb-2">Trigger</th>
              <th class="pb-2">Total</th>
              <th class="pb-2">New</th>
              <th class="pb-2">Matched</th>
              <th class="pb-2">Orphaned</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="run in syncHistory" :key="run.id" class="border-b border-gray-100">
              <td class="py-2">{{ new Date(run.startedAt).toLocaleString() }}</td>
              <td class="py-2">
                <span :class="run.status === 'SUCCESS' ? 'text-green-600' : run.status === 'FAILED' ? 'text-red-600' : 'text-yellow-600'" class="font-medium">{{ run.status }}</span>
              </td>
              <td class="py-2">{{ run.triggeredBy }}</td>
              <td class="py-2">{{ run.totalEmployees ?? '-' }}</td>
              <td class="py-2">{{ run.newEmployees }}</td>
              <td class="py-2">{{ run.matchedCount }}</td>
              <td class="py-2">{{ run.orphanedCount }}</td>
            </tr>
            <tr v-if="!syncHistory.length">
              <td colspan="7" class="py-4 text-center text-gray-400">No sync runs yet</td>
            </tr>
          </tbody>
        </table>
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
