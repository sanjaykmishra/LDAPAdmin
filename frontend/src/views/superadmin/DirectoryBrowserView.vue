<template>
  <div class="p-6 h-full flex flex-col">
    <h1 class="text-2xl font-bold text-gray-900 mb-4">Directory Browser</h1>

    <!-- Directory picker -->
    <div class="mb-4">
      <label class="block text-sm font-medium text-gray-700 mb-1">Directory</label>
      <select v-model="selectedDirId" class="input w-64">
        <option value="" disabled>{{ loadingDirs ? 'Loading…' : '— Select directory —' }}</option>
        <option v-for="d in directories" :key="d.id" :value="d.id">{{ d.displayName }}</option>
      </select>
    </div>

    <!-- Two-panel browser -->
    <div class="flex gap-4 flex-1 min-h-0">
      <!-- Left panel: DIT tree (1/3) -->
      <div class="w-1/3 shrink-0 bg-white border border-gray-200 rounded-xl overflow-y-auto p-3">
        <div v-if="!selectedDirId" class="text-sm text-gray-400 text-center mt-8">
          Select a directory to browse.
        </div>
        <div v-else-if="treeLoading" class="text-sm text-gray-400 text-center mt-8">Loading…</div>
        <div v-else-if="rootNodes.length === 0" class="text-sm text-gray-400 text-center mt-8">
          No entries found.
        </div>
        <DnTree
          v-else
          ref="treeRef"
          :nodes="rootNodes"
          :selected-dn="selectedDn"
          :load-children="loadChildren"
          @select="selectEntry"
        />
      </div>

      <!-- Right panel: Entry details or create form (2/3) -->
      <div class="w-2/3 bg-white border border-gray-200 rounded-xl overflow-y-auto p-5">
        <!-- Create form mode -->
        <CreateEntryForm
          v-if="creatingEntry && selectedDirId && selectedDn"
          :directory-id="selectedDirId"
          :parent-dn="selectedDn"
          @created="onEntryCreated"
          @cancel="creatingEntry = false"
        />

        <!-- Edit form mode -->
        <EditEntryForm
          v-else-if="editingEntry && selectedDirId && entryDetail"
          :directory-id="selectedDirId"
          :dn="entryDetail.dn"
          :attributes="entryDetail.attributes"
          @updated="onEntryUpdated"
          @cancel="editingEntry = false"
        />

        <!-- Normal browse mode -->
        <template v-else>
          <div v-if="!selectedDn" class="text-sm text-gray-400 text-center mt-8">
            Select an entry from the tree to view its attributes.
          </div>
          <div v-else-if="detailLoading" class="text-sm text-gray-400 text-center mt-8">Loading…</div>
          <template v-else-if="entryDetail">
            <div class="flex items-end justify-between mb-4">
              <div class="flex-1 min-w-0">
                <p class="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1">Distinguished Name</p>
                <p class="text-sm font-mono text-gray-900 bg-gray-50 px-3 py-2 rounded-lg break-all">{{ entryDetail.dn }}</p>
              </div>
              <div class="ml-3 shrink-0 relative" ref="menuRef">
                <button @click="showActionsMenu = !showActionsMenu"
                        class="px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors inline-flex items-center gap-1">
                  Actions
                  <svg class="w-4 h-4" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" clip-rule="evenodd"/></svg>
                </button>
                <div v-if="showActionsMenu"
                     class="absolute right-0 mt-1 w-40 bg-white border border-gray-200 rounded-lg shadow-lg z-10 py-1">
                  <button @click="creatingEntry = true; showActionsMenu = false"
                          class="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-100">
                    New Entry
                  </button>
                  <button @click="editingEntry = true; showActionsMenu = false"
                          class="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-100">
                    Edit Entry
                  </button>
                  <button disabled
                          class="w-full text-left px-4 py-2 text-sm text-gray-400 cursor-not-allowed">
                    Delete Entry
                  </button>
                </div>
              </div>
            </div>

            <div v-if="Object.keys(entryDetail.attributes).length === 0" class="text-sm text-gray-400">
              No attributes returned for this entry.
            </div>
            <table v-else class="w-full text-sm">
              <thead>
                <tr class="border-b border-gray-200">
                  <th class="text-left py-2 pr-4 text-xs font-semibold text-gray-500 uppercase tracking-wider w-48">Attribute</th>
                  <th class="text-left py-2 text-xs font-semibold text-gray-500 uppercase tracking-wider">Value</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-50">
                <tr v-for="[attr, values] in sortedAttributes" :key="attr">
                  <td class="py-2 pr-4 text-gray-600 font-mono text-xs align-top">{{ attr }}</td>
                  <td class="py-2 text-gray-900 font-mono text-xs">
                    <div v-for="(val, i) in values" :key="i" class="break-all">{{ formatValue(val) }}</div>
                  </td>
                </tr>
              </tbody>
            </table>
          </template>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useNotificationStore } from '@/stores/notifications'
import { listDirectories } from '@/api/directories'
import { browse } from '@/api/browse'
import DnTree from '@/components/DnTree.vue'
import CreateEntryForm from '@/components/CreateEntryForm.vue'
import EditEntryForm from '@/components/EditEntryForm.vue'

const notif = useNotificationStore()

const directories   = ref([])
const loadingDirs   = ref(false)
const selectedDirId = ref('')

const treeLoading   = ref(false)
const rootNodes     = ref([])
const selectedDn    = ref('')
const detailLoading = ref(false)
const entryDetail   = ref(null)
const creatingEntry   = ref(false)
const editingEntry    = ref(false)
const treeRef         = ref(null)
const showActionsMenu = ref(false)
const menuRef         = ref(null)

function onClickOutside(e) {
  if (menuRef.value && !menuRef.value.contains(e.target)) {
    showActionsMenu.value = false
  }
}

onMounted(() => document.addEventListener('click', onClickOutside))
onUnmounted(() => document.removeEventListener('click', onClickOutside))

const sortedAttributes = computed(() => {
  if (!entryDetail.value?.attributes) return []
  return Object.entries(entryDetail.value.attributes)
    .sort(([a], [b]) => a.localeCompare(b, undefined, { sensitivity: 'base' }))
})

// Load the tree root when directory changes
watch(selectedDirId, async (dirId) => {
  rootNodes.value = []
  selectedDn.value = ''
  entryDetail.value = null
  if (!dirId) return

  treeLoading.value = true
  try {
    const { data } = await browse(dirId)
    // Root node is the directory base DN itself
    rootNodes.value = [{
      dn: data.dn,
      rdn: data.dn,
      hasChildren: data.children.length > 0,
      _preloaded: data.children,
    }]
    // Auto-select root
    entryDetail.value = data
    selectedDn.value = data.dn
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    treeLoading.value = false
  }
})

async function loadChildren(dn) {
  // If children were preloaded (for root node), use them
  const rootNode = rootNodes.value.find(n => n.dn === dn)
  if (rootNode?._preloaded) {
    const children = rootNode._preloaded
    delete rootNode._preloaded
    return children
  }

  const { data } = await browse(selectedDirId.value, dn)
  return data.children
}

async function selectEntry(dn) {
  selectedDn.value = dn
  detailLoading.value = true
  try {
    const { data } = await browse(selectedDirId.value, dn)
    entryDetail.value = { dn: data.dn, attributes: data.attributes }
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
    entryDetail.value = null
  } finally {
    detailLoading.value = false
  }
}

async function onEntryUpdated(browseResult) {
  editingEntry.value = false
  // Refresh entry detail from the returned browse result
  entryDetail.value = { dn: browseResult.dn, attributes: browseResult.attributes }
  notif.success('Entry updated successfully')
}

async function onEntryCreated(browseResult) {
  creatingEntry.value = false
  // The server returned the parent's browse result — use it to refresh the tree
  if (treeRef.value) {
    treeRef.value.refreshNode(selectedDn.value, browseResult.children)
  }
  // Reload the current entry detail
  await selectEntry(selectedDn.value)
  notif.success('Entry created successfully')
}

function formatValue(val) {
  // Detect likely binary data (contains non-printable characters)
  if (val && /[\x00-\x08\x0E-\x1F]/.test(val)) {
    return `[binary data, ${val.length} bytes]`
  }
  return val
}

onMounted(async () => {
  loadingDirs.value = true
  try {
    const { data } = await listDirectories()
    directories.value = data
    if (data.length) selectedDirId.value = data[0].id
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
