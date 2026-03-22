<template>
  <div class="relative">
    <div class="flex gap-1">
      <input
        :value="modelValue"
        @input="$emit('update:modelValue', $event.target.value)"
        type="text"
        class="flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
        placeholder="cn=engineers,ou=groups,dc=corp"
      />
      <button
        @click="openPicker"
        :disabled="!directoryId"
        type="button"
        class="px-2.5 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed shrink-0"
        title="Browse groups"
      >
        <svg class="w-4 h-4 text-gray-500" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M3 4h5l2 2h7a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z"/>
        </svg>
      </button>
    </div>

    <Teleport to="body">
      <div v-if="showPicker" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @mousedown.self="showPicker = false">
        <div class="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 flex flex-col" style="max-height: 70vh;">
          <div class="px-5 py-3 border-b border-gray-200 flex items-center justify-between shrink-0">
            <h3 class="text-sm font-semibold text-gray-900">Select Group</h3>
            <button @click="showPicker = false" class="text-gray-400 hover:text-gray-600">
              <svg class="w-5 h-5" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/></svg>
            </button>
          </div>

          <div class="flex-1 overflow-y-auto p-3 min-h-0">
            <div v-if="treeLoading" class="text-sm text-gray-400 text-center py-8">Loading groups...</div>
            <div v-else-if="treeNodes.length === 0" class="text-sm text-gray-400 text-center py-8">No groups found.</div>
            <GroupTree
              v-else
              :nodes="treeNodes"
              :selected-dn="selectedDn"
              @select="onSelect"
            />
          </div>

          <div class="px-5 py-3 border-t border-gray-200 shrink-0">
            <div v-if="selectedDn" class="text-xs font-mono text-gray-600 mb-2 break-all">{{ selectedDn }}</div>
            <div class="flex justify-end gap-2">
              <button @click="showPicker = false" class="px-3 py-1.5 text-sm rounded-lg border border-gray-300 hover:bg-gray-50">Cancel</button>
              <button
                @click="confirmSelection"
                :disabled="!selectedDn"
                class="px-3 py-1.5 text-sm rounded-lg text-white font-medium bg-blue-600 hover:bg-blue-700 disabled:opacity-50"
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
import { searchGroups } from '@/api/groups'
import GroupTree from '@/components/GroupTree.vue'

const props = defineProps({
  modelValue:  { type: String, default: '' },
  directoryId: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue'])

const showPicker  = ref(false)
const treeLoading = ref(false)
const treeNodes   = ref([])
const selectedDn  = ref('')

/**
 * Build a tree from a flat list of group DNs.
 * Each DN is decomposed into its RDN components so parent containers
 * (OUs, DCs) appear as non-selectable structural nodes.
 */
function buildTree(groupDns) {
  const nodeMap = new Map()  // dn -> { dn, rdn, children, isGroup }

  for (const dn of groupDns) {
    // Walk from the full DN up to root, ensuring every ancestor exists
    const parts = parseDnComponents(dn)
    for (let i = 0; i < parts.length; i++) {
      const currentDn = parts.slice(i).join(',')
      if (nodeMap.has(currentDn)) continue
      nodeMap.set(currentDn, {
        dn: currentDn,
        rdn: parts[i],
        isGroup: i === 0,  // only the original DN is a group
        children: [],
      })
    }
  }

  // Link children to parents
  for (const [dn, node] of nodeMap) {
    const commaIdx = dn.indexOf(',')
    if (commaIdx === -1) continue
    const parentDn = dn.substring(commaIdx + 1)
    const parent = nodeMap.get(parentDn)
    if (parent) {
      parent.children.push(node)
    }
  }

  // Root nodes are those whose parent DN is not in the map
  const roots = []
  for (const [dn, node] of nodeMap) {
    const commaIdx = dn.indexOf(',')
    if (commaIdx === -1) {
      roots.push(node)
    } else {
      const parentDn = dn.substring(commaIdx + 1)
      if (!nodeMap.has(parentDn)) {
        roots.push(node)
      }
    }
  }

  // Sort children alphabetically at each level
  function sortChildren(node) {
    node.children.sort((a, b) => a.rdn.localeCompare(b.rdn))
    node.children.forEach(sortChildren)
  }
  roots.sort((a, b) => a.rdn.localeCompare(b.rdn))
  roots.forEach(sortChildren)

  return roots
}

/**
 * Split a DN into its RDN components, respecting escaped commas.
 */
function parseDnComponents(dn) {
  const parts = []
  let current = ''
  for (let i = 0; i < dn.length; i++) {
    if (dn[i] === '\\' && i + 1 < dn.length) {
      current += dn[i] + dn[i + 1]
      i++
    } else if (dn[i] === ',') {
      parts.push(current.trim())
      current = ''
    } else {
      current += dn[i]
    }
  }
  if (current.trim()) parts.push(current.trim())
  return parts
}

async function openPicker() {
  if (!props.directoryId) return
  showPicker.value = true
  selectedDn.value = props.modelValue || ''
  treeLoading.value = true
  try {
    const { data } = await searchGroups(props.directoryId, { limit: 1000, attributes: 'dn' })
    const dns = data.map(e => e.dn)
    treeNodes.value = buildTree(dns)
  } catch (e) {
    console.warn('Failed to load groups:', e)
    treeNodes.value = []
  } finally {
    treeLoading.value = false
  }
}

function onSelect(dn) {
  selectedDn.value = dn
}

function confirmSelection() {
  emit('update:modelValue', selectedDn.value)
  showPicker.value = false
}
</script>
