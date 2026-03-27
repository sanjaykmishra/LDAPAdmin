<template>
  <div class="p-6">
    <h1 class="text-2xl font-bold text-gray-900 mb-4">Directory Search</h1>

    <!-- Directory picker -->
    <div class="mb-4">
      <label class="block text-sm font-medium text-gray-700 mb-1">Directory</label>
      <select v-model="form.directoryId" class="input w-64">
        <option value="" disabled>{{ loadingDirs ? 'Loading…' : '— Select directory —' }}</option>
        <option v-for="d in directories" :key="d.id" :value="d.id">{{ d.displayName }}</option>
      </select>
    </div>

    <!-- Search form -->
    <div class="bg-white border border-gray-200 rounded-xl p-5 mb-6" >
      <div class="grid grid-cols-3 gap-4 mb-4" style="grid-template-columns: 1fr 2.5fr .5fr ;">
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
                  <div v-if="attr === 'userAccountControl' && values.length" class="mt-1 flex flex-wrap gap-1">
                    <span v-for="flag in decodeUAC(values[0])" :key="flag"
                      :class="['px-1.5 py-0.5 rounded text-xs font-medium',
                        flag === 'DISABLED' || flag === 'LOCKED_OUT' ? 'bg-red-100 text-red-800' :
                        flag === 'PASSWORD_EXPIRED' || flag === 'PASSWORD_NEVER_EXPIRES' ? 'bg-yellow-100 text-yellow-800' :
                        'bg-gray-100 text-gray-700']">
                      {{ flag }}
                    </span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-if="!Object.keys(selectedEntry.attributes || {}).length" class="text-sm text-gray-400 text-center py-4">No attributes returned.</p>

          <!-- Group membership lookup -->
          <div class="mt-4 border-t border-gray-200 pt-4">
            <button @click="loadGroups(selectedEntry.dn)" :disabled="loadingGroups" class="btn-secondary text-sm">
              {{ loadingGroups ? 'Loading…' : (entryGroups !== null ? 'Refresh Groups' : 'View Groups') }}
            </button>

            <div v-if="entryGroups !== null" class="mt-3">
              <p class="text-xs font-medium text-gray-500 uppercase tracking-wider mb-2">
                Group Memberships ({{ entryGroups.length }})
              </p>
              <div v-if="entryGroups.length === 0" class="text-sm text-gray-400">Not a member of any groups.</div>
              <div v-else class="space-y-1 max-h-48 overflow-y-auto">
                <div v-for="g in entryGroups" :key="g.dn"
                  class="font-mono text-xs text-gray-700 bg-gray-50 rounded px-3 py-1.5 break-all">
                  <span class="font-medium text-gray-900">{{ g.cn || '' }}</span>
                  <span v-if="g.cn" class="text-gray-400 ml-1">—</span>
                  {{ g.dn }}
                </div>
              </div>
            </div>
          </div>
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
const entryGroups   = ref(null)
const loadingGroups = ref(false)

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

function decodeUAC(value) {
  const uac = parseInt(value, 10)
  if (isNaN(uac)) return []
  const flags = []
  if (uac & 0x0002) flags.push('DISABLED')
  if (uac & 0x0010) flags.push('LOCKED_OUT')
  if (uac & 0x0020) flags.push('PASSWORD_NOT_REQUIRED')
  if (uac & 0x0200) flags.push('NORMAL_ACCOUNT')
  if (uac & 0x10000) flags.push('PASSWORD_NEVER_EXPIRES')
  if (uac & 0x800000) flags.push('PASSWORD_EXPIRED')
  if (uac & 0x40000) flags.push('SMARTCARD_REQUIRED')
  return flags
}

function showEntryDetail(entry) {
  selectedEntry.value = entry
  entryGroups.value = null
}

async function loadGroups(dn) {
  if (!form.value.directoryId || !dn) return
  loadingGroups.value = true
  try {
    const escapedDn = dn.replace(/([\\*()])/g, '\\$1')
    const { data } = await searchEntries(form.value.directoryId, {
      scope: 'sub',
      filter: `(|(member=${escapedDn})(uniqueMember=${escapedDn})(memberUid=${dn.split(',')[0].split('=')[1] || dn}))`,
      attributes: 'cn,dn',
      limit: 200,
    })
    entryGroups.value = (Array.isArray(data) ? data : []).map(e => ({
      dn: e.dn,
      cn: (e.attributes?.cn || [])[0] || '',
    })).sort((a, b) => (a.cn || a.dn).localeCompare(b.cn || b.dn))
  } catch (e) {
    notif.error('Failed to load groups: ' + (e.response?.data?.detail || e.message))
    entryGroups.value = []
  } finally {
    loadingGroups.value = false
  }
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
</style>
