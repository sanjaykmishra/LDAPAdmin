<template>
  <div class="relative">
    <!-- Input with browse button -->
    <div class="flex gap-1">
      <input
        :value="modelValue"
        @input="$emit('update:modelValue', $event.target.value)"
        type="text"
        class="flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
        :placeholder="placeholder"
      />
      <button
        @click="openPicker"
        :disabled="!directoryId"
        type="button"
        class="px-2.5 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed shrink-0"
        title="Browse directory tree"
      >
        <svg class="w-4 h-4 text-gray-500" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M3 4h5l2 2h7a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z"/>
        </svg>
      </button>
    </div>

    <!-- Tree picker modal -->
    <Teleport to="body">
      <div v-if="showPicker" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @mousedown.self="showPicker = false">
        <div class="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 flex flex-col" style="max-height: 70vh;">
          <div class="px-5 py-3 border-b border-gray-200 flex items-center justify-between shrink-0">
            <h3 class="text-sm font-semibold text-gray-900">Select DN</h3>
            <button @click="showPicker = false" class="text-gray-400 hover:text-gray-600">
              <svg class="w-5 h-5" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/></svg>
            </button>
          </div>

          <div class="flex-1 overflow-y-auto p-3 min-h-0">
            <div v-if="treeLoading" class="text-sm text-gray-400 text-center py-8">Loading...</div>
            <div v-else-if="treeNodes.length === 0" class="text-sm text-gray-400 text-center py-8">No entries found.</div>
            <DnTree
              v-else
              :nodes="treeNodes"
              :selected-dn="pickerSelectedDn"
              :load-children="loadChildren"
              @select="onNodeSelect"
            />
          </div>

          <div class="px-5 py-3 border-t border-gray-200 shrink-0">
            <div v-if="pickerSelectedDn" class="text-xs font-mono text-gray-600 mb-2 break-all">{{ pickerSelectedDn }}</div>
            <div class="flex justify-end gap-2">
              <button @click="showPicker = false" class="btn-neutral">Cancel</button>
              <button
                @click="confirmSelection"
                :disabled="!pickerSelectedDn"
                class="btn-primary"
              >Select</button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { browse, directoryBrowse } from '@/api/browse'
import DnTree from '@/components/DnTree.vue'

const props = defineProps({
  modelValue:  { type: String, default: '' },
  directoryId: { type: String, default: '' },
  placeholder: { type: String, default: 'dc=example,dc=com' },
  superadmin:  { type: Boolean, default: true },
})

const emit = defineEmits(['update:modelValue'])

const showPicker       = ref(false)
const treeLoading      = ref(false)
const treeNodes        = ref([])
const pickerSelectedDn = ref('')

async function openPicker() {
  if (!props.directoryId) return
  showPicker.value = true
  pickerSelectedDn.value = props.modelValue || ''

  const browseFn = props.superadmin ? browse : directoryBrowse
  treeLoading.value = true
  try {
    const { data } = await browseFn(props.directoryId)
    treeNodes.value = [{
      dn: data.dn,
      rdn: data.dn,
      hasChildren: data.children.length > 0,
      _preloaded: data.children,
    }]
  } catch (e) {
    console.warn('Failed to load directory tree:', e)
    treeNodes.value = []
  } finally {
    treeLoading.value = false
  }
}

async function loadChildren(dn) {
  const rootNode = treeNodes.value.find(n => n.dn === dn)
  if (rootNode?._preloaded) {
    const children = rootNode._preloaded
    delete rootNode._preloaded
    return children
  }
  const browseFn = props.superadmin ? browse : directoryBrowse
  try {
    const { data } = await browseFn(props.directoryId, dn)
    return data.children || []
  } catch (e) {
    console.warn('Failed to load children for', dn, e)
    return []
  }
}

function onNodeSelect(dn) {
  pickerSelectedDn.value = dn
}

function confirmSelection() {
  emit('update:modelValue', pickerSelectedDn.value)
  showPicker.value = false
}
</script>
