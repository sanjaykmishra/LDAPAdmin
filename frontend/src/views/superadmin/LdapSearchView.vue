<template>
  <div class="p-6">
    <h1 class="text-2xl font-bold text-gray-900 mb-6">LDAP Search</h1>

    <!-- Search form -->
    <div class="bg-white border border-gray-200 rounded-xl p-5 mb-6" > 
      <div class="grid grid-cols-4 gap-4 mb-4" style="grid-template-columns: 1fr 1fr 2.5fr .5fr ;">
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
        <button @click="promptSaveSearch" class="btn-secondary">Save Search</button>
        <select v-if="savedSearches.length" @change="loadSavedSearch($event)" class="input w-48">
          <option value="">-- Saved Searches --</option>
          <option v-for="(s, i) in savedSearches" :key="i" :value="i">{{ s.name }}</option>
        </select>
        <button v-if="savedSearches.length" @click="clearSavedSearches" class="text-xs text-gray-400 hover:text-gray-600" title="Clear all saved searches">Clear saved</button>
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
          class="btn-secondary"
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
          <tr v-for="entry in results" :key="entry.dn" class="hover:bg-blue-50 cursor-pointer" @click="showEntryDetail(entry)">
            <td class="py-2 px-4 font-mono text-xs text-blue-600 break-all max-w-md">{{ entry.dn }}</td>
            <td v-for="col in resultColumns" :key="col" class="py-2 px-4 font-mono text-xs text-gray-700 break-all">
              {{ (entry.attributes[col] || []).join(', ') }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Entry detail dialog -->
    <div v-if="selectedEntry" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="selectedEntry = null">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-2xl max-h-[80vh] flex flex-col">
        <div class="flex items-center justify-between px-5 py-3 border-b border-gray-200">
          <h3 class="text-sm font-semibold text-gray-900 truncate">{{ selectedEntry.dn }}</h3>
          <button @click="selectedEntry = null" class="text-gray-400 hover:text-gray-600 text-lg leading-none">&times;</button>
        </div>
        <div class="overflow-y-auto p-5">
          <table class="w-full text-sm">
            <tbody>
              <tr v-for="(values, attr) in selectedEntry.attributes" :key="attr" class="border-b border-gray-100">
                <td class="py-2 pr-4 font-medium text-gray-600 align-top whitespace-nowrap">{{ attr }}</td>
                <td class="py-2 font-mono text-xs text-gray-800 break-all">
                  <div v-for="(v, i) in values" :key="i">{{ v }}</div>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-if="!Object.keys(selectedEntry.attributes || {}).length" class="text-sm text-gray-400 text-center py-4">No attributes returned.</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useNotificationStore } from '@/stores/notifications'
import { listDirectories } from '@/api/directories'
import { searchEntries } from '@/api/browse'
import DnPicker from '@/components/DnPicker.vue'

const HISTORY_KEY = 'ldap-search-history'
const SAVED_KEY   = 'ldap-saved-searches'
const MAX_HISTORY = 10

const notif  = useNotificationStore()

const directories = ref([])
const loadingDirs = ref(false)
const searching   = ref(false)
const hasSearched = ref(false)
const results      = ref([])
const selectedEntry = ref(null)

const form = ref({
  directoryId: '',
  baseDn: '',
  scope: 'sub',
  filter: '',
  attributes: '',
  limit: 100,
})

const history = ref(loadHistory())
const savedSearches = ref(loadSavedSearches())

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

function doExportResults() {
  const lines = []
  for (const entry of results.value) {
    lines.push(`dn: ${entry.dn}`)
    for (const [attr, values] of Object.entries(entry.attributes || {})) {
      for (const v of values) {
        lines.push(`${attr}: ${v}`)
      }
    }
    lines.push('')
  }
  const blob = new Blob([lines.join('\n')], { type: 'application/ldif' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'search-export.ldif'
  a.click()
  URL.revokeObjectURL(url)
}

function showEntryDetail(entry) {
  selectedEntry.value = entry
}

function clearForm() {
  form.value = { ...form.value, baseDn: '', scope: 'sub', filter: '', attributes: '', limit: 100 }
  results.value = []
  hasSearched.value = false
}

function saveToHistory(f) {
  const entry = { directoryId: f.directoryId, baseDn: f.baseDn, scope: f.scope, filter: f.filter, attributes: f.attributes, limit: f.limit }
  const same = (a, b) => a.directoryId === b.directoryId && a.baseDn === b.baseDn
      && a.scope === b.scope && a.filter === b.filter && a.attributes === b.attributes && a.limit === b.limit
  if (history.value.some(h => same(h, entry))) return
  history.value = [entry, ...history.value].slice(0, MAX_HISTORY)
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

function loadSavedSearches() {
  try { return JSON.parse(localStorage.getItem(SAVED_KEY) || '[]') } catch { return [] }
}

function promptSaveSearch() {
  const name = prompt('Name for this search:')
  if (!name?.trim()) return
  const entry = { name: name.trim(), baseDn: form.value.baseDn, scope: form.value.scope, filter: form.value.filter, attributes: form.value.attributes, limit: form.value.limit }
  savedSearches.value = [...savedSearches.value.filter(s => s.name !== entry.name), entry]
  try { localStorage.setItem(SAVED_KEY, JSON.stringify(savedSearches.value)) } catch {}
}

function loadSavedSearch(event) {
  const idx = event.target.value
  if (idx === '') return
  const s = savedSearches.value[idx]
  if (s) {
    form.value = { ...form.value, ...s }
    doSearch()
  }
  event.target.value = ''
}

function clearSavedSearches() {
  savedSearches.value = []
  try { localStorage.removeItem(SAVED_KEY) } catch {}
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
