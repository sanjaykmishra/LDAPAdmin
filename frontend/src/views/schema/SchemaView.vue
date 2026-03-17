<template>
  <div class="p-6 max-w-4xl">
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Schema Browser</h1>

    <!-- Directory picker -->
    <div class="mb-6">
      <label class="block text-sm font-medium text-gray-700 mb-1">Directory</label>
      <select v-model="selectedDirId" class="input w-64">
        <option value="" disabled>{{ loadingDirs ? 'Loading…' : '— Select directory —' }}</option>
        <option v-for="d in directories" :key="d.id" :value="d.id">{{ d.displayName }}</option>
      </select>
    </div>

    <!-- Tabs -->
    <div class="flex gap-1 mb-6 bg-gray-100 p-1 rounded-lg w-fit">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        @click="switchTab(tab.key)"
        :class="activeTab === tab.key
          ? 'bg-white text-gray-900 shadow-sm'
          : 'text-gray-500 hover:text-gray-700'"
        class="px-4 py-1.5 rounded-md text-sm font-medium transition-colors"
      >{{ tab.label }}</button>
    </div>

    <div class="flex gap-4">
      <!-- Left panel: list -->
      <div class="w-64 shrink-0">
        <input
          v-model="search"
          type="text"
          placeholder="Filter…"
          class="input w-full mb-3"
        />
        <div v-if="listLoading" class="text-sm text-gray-400 text-center py-4">Loading…</div>
        <div v-else class="bg-white border border-gray-200 rounded-xl overflow-hidden max-h-[60vh] overflow-y-auto">
          <div v-if="filteredList.length === 0" class="p-4 text-sm text-gray-400 text-center">Nothing found.</div>
          <button
            v-for="name in filteredList"
            :key="name"
            @click="loadDetail(name)"
            :class="selected === name ? 'bg-blue-50 text-blue-700 font-medium' : 'text-gray-700 hover:bg-gray-50'"
            class="w-full text-left px-3 py-2 text-sm border-b border-gray-50 last:border-0 font-mono"
          >{{ name }}</button>
        </div>
      </div>

      <!-- Right panel: detail -->
      <div class="flex-1">
        <div v-if="!selected" class="text-sm text-gray-400 mt-8 text-center">
          Select an item from the list to see details.
        </div>
        <div v-else-if="detailLoading" class="text-sm text-gray-400 mt-8 text-center">Loading…</div>
        <div v-else-if="detail" class="bg-white border border-gray-200 rounded-xl p-5">
          <h2 class="text-lg font-semibold text-gray-900 font-mono mb-4">{{ selected }}</h2>

          <!-- Object class detail -->
          <template v-if="activeTab === 'objectClasses'">
            <div v-if="detail.required?.length" class="mb-4">
              <p class="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Required Attributes</p>
              <div class="flex flex-wrap gap-1">
                <span v-for="a in detail.required" :key="a" class="text-xs bg-red-50 text-red-700 rounded px-2 py-0.5 font-mono">{{ a }}</span>
              </div>
            </div>
            <div v-if="detail.optional?.length">
              <p class="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Optional Attributes</p>
              <div class="flex flex-wrap gap-1">
                <span v-for="a in detail.optional" :key="a" class="text-xs bg-gray-100 text-gray-700 rounded px-2 py-0.5 font-mono">{{ a }}</span>
              </div>
            </div>
            <div v-if="!detail.required?.length && !detail.optional?.length" class="text-sm text-gray-400">
              No attribute information available.
            </div>
          </template>

          <!-- Attribute type detail -->
          <template v-else>
            <table class="w-full text-sm">
              <tbody class="divide-y divide-gray-50">
                <tr v-for="[k, v] in detailRows" :key="k" class="py-2">
                  <td class="py-2 pr-4 text-xs font-semibold text-gray-500 uppercase tracking-wider w-40">{{ k }}</td>
                  <td class="py-2 text-gray-800 font-mono text-xs">{{ v }}</td>
                </tr>
              </tbody>
            </table>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useNotificationStore } from '@/stores/notifications'
import { listDirectories } from '@/api/directories'
import { listObjectClasses, getObjectClass, listAttributeTypes, getAttributeType } from '@/api/schema'

const notif = useNotificationStore()

const tabs = [
  { key: 'objectClasses',  label: 'Object Classes' },
  { key: 'attributeTypes', label: 'Attribute Types' },
]

const directories   = ref([])
const loadingDirs   = ref(false)
const selectedDirId = ref('')

const activeTab   = ref('objectClasses')
const search      = ref('')
const allItems    = ref([])
const listLoading = ref(false)
const selected    = ref(null)
const detail      = ref(null)
const detailLoading = ref(false)

const filteredList = computed(() => {
  const q = search.value.toLowerCase()
  return q ? allItems.value.filter(n => n.toLowerCase().includes(q)) : allItems.value
})

const detailRows = computed(() => {
  if (!detail.value || activeTab.value !== 'attributeTypes') return []
  const skip = new Set(['name'])
  return Object.entries(detail.value)
    .filter(([k]) => !skip.has(k))
    .map(([k, v]) => [k, v == null ? '—' : String(v)])
})

// Reload object classes / attribute types when directory changes
watch(selectedDirId, () => {
  if (selectedDirId.value) loadList()
})

async function loadList() {
  if (!selectedDirId.value) return
  listLoading.value = true
  selected.value = null
  detail.value = null
  allItems.value = []
  try {
    const fn = activeTab.value === 'objectClasses' ? listObjectClasses : listAttributeTypes
    const { data } = await fn(selectedDirId.value)
    allItems.value = Array.isArray(data) ? [...data].sort() : data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    listLoading.value = false
  }
}

async function loadDetail(name) {
  if (!selectedDirId.value) return
  selected.value = name
  detail.value = null
  detailLoading.value = true
  try {
    const fn = activeTab.value === 'objectClasses' ? getObjectClass : getAttributeType
    const { data } = await fn(selectedDirId.value, name)
    detail.value = data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    detailLoading.value = false
  }
}

function switchTab(tab) {
  activeTab.value = tab
  search.value = ''
  if (selectedDirId.value) loadList()
}

onMounted(async () => {
  loadingDirs.value = true
  try {
    const { data } = await listDirectories()
    directories.value = data
    if (data.length) {
      selectedDirId.value = data[0].id
    }
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
