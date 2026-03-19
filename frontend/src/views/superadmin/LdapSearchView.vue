<template>
  <div class="p-6">
    <h1 class="text-2xl font-bold text-gray-900 mb-6">LDAP Search</h1>

    <!-- Search form -->
    <div class="bg-white border border-gray-200 rounded-xl p-5 mb-6">
      <div class="grid grid-cols-4 gap-4 mb-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Directory</label>
          <select v-model="form.directoryId" class="input w-full">
            <option value="" disabled>{{ loadingDirs ? 'Loading…' : '— Select —' }}</option>
            <option v-for="d in directories" :key="d.id" :value="d.id">{{ d.displayName }}</option>
          </select>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Scope</label>
          <select v-model="form.scope" class="input w-full">
            <option value="sub">Subtree</option>
            <option value="one">One Level</option>
            <option value="base">Base</option>
          </select>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Base DN</label>
          <DnPicker v-model="form.baseDn" :directory-id="form.directoryId" placeholder="dc=example,dc=com (optional)" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Size Limit</label>
          <input v-model.number="form.limit" type="number" min="1" max="1000" class="input w-full" />
        </div>
      </div>

      <div class="grid grid-cols-2 gap-4 mb-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">LDAP Filter</label>
          <input v-model="form.filter" class="input w-full" placeholder="(objectClass=inetOrgPerson)" @keyup.enter="doSearch" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Attributes <span class="text-gray-400 font-normal">(comma-separated, optional)</span></label>
          <input v-model="form.attributes" class="input w-full" placeholder="cn,mail,uid" />
        </div>
      </div>

      <div class="flex items-center gap-3">
        <button @click="doSearch" :disabled="!form.directoryId || searching" class="btn-primary">
          {{ searching ? 'Searching…' : 'Search' }}
        </button>
        <button @click="clearForm" class="btn-secondary">Clear</button>
      </div>
    </div>

    <!-- Search history -->
    <div v-if="history.length" class="mb-4">
      <div class="flex items-center gap-2 mb-2">
        <span class="text-xs font-medium text-gray-500 uppercase tracking-wider">Recent Searches</span>
        <button @click="clearHistory" class="text-xs text-gray-400 hover:text-gray-600">Clear</button>
      </div>
      <div class="flex flex-wrap gap-2">
        <button
          v-for="(h, i) in history" :key="i"
          @click="loadFromHistory(h)"
          class="text-xs bg-gray-100 hover:bg-gray-200 text-gray-700 px-3 py-1.5 rounded-full truncate max-w-xs transition-colors"
          :title="h.filter"
        >
          {{ h.filter || '(objectClass=*)' }}
        </button>
      </div>
    </div>

    <!-- Results -->
    <div v-if="hasSearched" class="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <div class="px-5 py-3 border-b border-gray-200 flex items-center justify-between">
        <span class="text-sm text-gray-600">{{ results.length }} result{{ results.length !== 1 ? 's' : '' }}</span>
        <button
          v-if="results.length"
          @click="doExportResults"
          class="text-sm text-blue-600 hover:text-blue-800 font-medium"
        >Export LDIF</button>
      </div>

      <div v-if="results.length === 0" class="p-8 text-center text-sm text-gray-400">
        No entries found.
      </div>

      <table v-else class="w-full text-sm">
        <thead>
          <tr class="border-b border-gray-200 bg-gray-50">
            <th class="text-left py-2 px-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">DN</th>
            <th v-for="col in resultColumns" :key="col"
                class="text-left py-2 px-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">{{ col }}</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-50">
          <tr v-for="entry in results" :key="entry.dn" class="hover:bg-blue-50 cursor-pointer" @click="goToBrowser(entry.dn)">
            <td class="py-2 px-4 font-mono text-xs text-blue-600 break-all max-w-md">{{ entry.dn }}</td>
            <td v-for="col in resultColumns" :key="col" class="py-2 px-4 font-mono text-xs text-gray-700 break-all">
              {{ (entry.attributes[col] || []).join(', ') }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import { listDirectories } from '@/api/directories'
import { searchEntries, exportLdif } from '@/api/browse'
import DnPicker from '@/components/DnPicker.vue'

const HISTORY_KEY = 'ldap-search-history'
const MAX_HISTORY = 10

const router = useRouter()
const notif  = useNotificationStore()

const directories = ref([])
const loadingDirs = ref(false)
const searching   = ref(false)
const hasSearched = ref(false)
const results     = ref([])

const form = ref({
  directoryId: '',
  baseDn: '',
  scope: 'sub',
  filter: '',
  attributes: '',
  limit: 100,
})

const history = ref(loadHistory())

const resultColumns = computed(() => {
  if (!results.value.length) return []
  const cols = new Set()
  for (const entry of results.value) {
    for (const key of Object.keys(entry.attributes || {})) {
      cols.add(key)
    }
  }
  // Show a reasonable number of columns
  return [...cols].sort().slice(0, 8)
})

async function doSearch() {
  if (!form.value.directoryId) return
  searching.value = true
  hasSearched.value = false
  try {
    const params = {
      baseDn: form.value.baseDn || undefined,
      scope: form.value.scope,
      filter: form.value.filter || undefined,
      attributes: form.value.attributes || undefined,
      limit: form.value.limit,
    }
    const { data } = await searchEntries(form.value.directoryId, params)
    results.value = Array.isArray(data) ? data : []
    hasSearched.value = true
    saveToHistory(form.value)
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    searching.value = false
  }
}

async function doExportResults() {
  try {
    const { data } = await exportLdif(
      form.value.directoryId,
      form.value.baseDn || '',
      form.value.scope
    )
    const url = URL.createObjectURL(data)
    const a = document.createElement('a')
    a.href = url
    a.download = 'search-export.ldif'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  }
}

function goToBrowser(dn) {
  router.push({ path: '/superadmin/browser', query: { dn } })
}

function clearForm() {
  form.value = { ...form.value, baseDn: '', scope: 'sub', filter: '', attributes: '', limit: 100 }
  results.value = []
  hasSearched.value = false
}

function saveToHistory(f) {
  const entry = { directoryId: f.directoryId, baseDn: f.baseDn, scope: f.scope, filter: f.filter, attributes: f.attributes, limit: f.limit }
  const existing = history.value.filter(h => h.filter !== entry.filter || h.baseDn !== entry.baseDn)
  history.value = [entry, ...existing].slice(0, MAX_HISTORY)
  try { localStorage.setItem(HISTORY_KEY, JSON.stringify(history.value)) } catch {}
}

function loadHistory() {
  try { return JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]') } catch { return [] }
}

function loadFromHistory(h) {
  form.value = { ...form.value, ...h }
  doSearch()
}

function clearHistory() {
  history.value = []
  try { localStorage.removeItem(HISTORY_KEY) } catch {}
}

onMounted(async () => {
  loadingDirs.value = true
  try {
    const { data } = await listDirectories()
    directories.value = data
    if (data.length) form.value.directoryId = data[0].id
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    loadingDirs.value = false
  }
})
</script>

<style scoped>
@reference "tailwindcss";
.input { @apply border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
.btn-primary { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
.btn-secondary { @apply px-3 py-1.5 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50; }
</style>
