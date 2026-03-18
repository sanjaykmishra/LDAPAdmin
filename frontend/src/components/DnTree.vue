<template>
  <ul class="dn-tree" :class="{ 'ml-4': depth > 0 }">
    <li v-for="node in nodes" :key="node.dn">
      <div
        class="flex items-center gap-1 px-2 py-1.5 rounded cursor-pointer text-sm select-none"
        :class="selectedDn === node.dn ? 'bg-blue-100 text-blue-800 font-medium' : 'text-gray-700 hover:bg-gray-100'"
        @click="select(node)"
      >
        <!-- Expand/collapse toggle -->
        <button
          v-if="node.hasChildren"
          class="w-4 h-4 flex items-center justify-center shrink-0 text-gray-400 hover:text-gray-600"
          @click.stop="toggle(node)"
        >
          <svg
            class="w-3 h-3 transition-transform duration-150"
            :class="{ 'rotate-90': expanded.has(node.dn) }"
            viewBox="0 0 12 12" fill="currentColor"
          ><path d="M4 2l5 4-5 4z"/></svg>
        </button>
        <span v-else class="w-4 shrink-0"></span>

        <!-- Node label -->
        <span class="truncate font-mono text-xs">{{ node.rdn || node.dn }}</span>
      </div>

      <!-- Children (lazy-loaded, recursive) -->
      <template v-if="node.hasChildren && expanded.has(node.dn)">
        <div v-if="loading.has(node.dn)" class="ml-8 py-1 text-xs text-gray-400">Loading…</div>
        <DnTree
          v-else-if="childrenMap.has(node.dn)"
          :nodes="childrenMap.get(node.dn)"
          :depth="depth + 1"
          :selected-dn="selectedDn"
          :load-children="loadChildren"
          @select="(dn) => $emit('select', dn)"
        />
      </template>
    </li>
  </ul>
</template>

<script setup>
import { reactive, ref } from 'vue'

const props = defineProps({
  nodes:        { type: Array, required: true },
  depth:        { type: Number, default: 0 },
  selectedDn:   { type: String, default: '' },
  loadChildren: { type: Function, required: true },
})

const emit = defineEmits(['select'])

const expanded    = reactive(new Set())
const loading     = reactive(new Set())
const childrenMap = ref(new Map())

async function toggle(node) {
  if (expanded.has(node.dn)) {
    expanded.delete(node.dn)
    return
  }

  expanded.add(node.dn)

  // Lazy-load children if not cached
  if (!childrenMap.value.has(node.dn)) {
    loading.add(node.dn)
    try {
      const children = await props.loadChildren(node.dn)
      const updated = new Map(childrenMap.value)
      updated.set(node.dn, children)
      childrenMap.value = updated
    } catch {
      expanded.delete(node.dn)
    } finally {
      loading.delete(node.dn)
    }
  }
}

function select(node) {
  emit('select', node.dn)
  // Auto-expand on select if it has children and isn't expanded
  if (node.hasChildren && !expanded.has(node.dn)) {
    toggle(node)
  }
}

/**
 * Refresh a node's children from externally provided data.
 * Called by the parent after a new entry is created.
 */
function refreshNode(dn, children) {
  const updated = new Map(childrenMap.value)
  updated.set(dn, children)
  childrenMap.value = updated

  // Ensure the node is expanded and marked as having children
  expanded.add(dn)

  // Also update the node's hasChildren flag in our props
  const node = props.nodes.find(n => n.dn === dn)
  if (node) {
    node.hasChildren = true
  }

  // Propagate to child DnTree instances via recursive search
  // (handled by the parent re-rendering with new childrenMap)
}

defineExpose({ refreshNode })
</script>
