<template>
  <div class="relative" ref="dropdownRef">
    <button @click="open = !open"
            class="flex items-center gap-1.5 px-3 py-1.5 text-xs border border-slate-200 rounded-lg text-slate-600 hover:bg-slate-50 transition-colors">
      <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 20 20" stroke="currentColor" stroke-width="1.5">
        <path stroke-linecap="round" stroke-linejoin="round" d="M10 2v12M10 14l4-4M10 14l-4-4M3 17h14" />
      </svg>
      {{ exporting ? '...' : 'Export' }}
      <svg class="w-3 h-3" fill="none" viewBox="0 0 20 20" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round" d="M5.25 7.5L10 12.25L14.75 7.5" />
      </svg>
    </button>
    <div v-if="errorMsg" class="absolute right-0 mt-1 w-48 bg-red-50 border border-red-200 rounded-lg p-2 text-xs text-red-700 z-10">
      {{ errorMsg }}
    </div>
    <div v-if="open" class="absolute right-0 mt-1 w-40 bg-white border border-slate-200 rounded-lg shadow-lg z-10 py-1">
      <button v-for="opt in options" :key="opt.label"
              @click="handleExport(opt)"
              :disabled="exporting"
              class="w-full text-left px-3 py-1.5 text-xs text-slate-700 hover:bg-slate-50 disabled:opacity-50">
        {{ opt.label }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  options: { type: Array, required: true },
  // Each option: { label: 'Export CSV', fn: async () => blob }
})

const open = ref(false)
const exporting = ref(false)
const errorMsg = ref(null)
const dropdownRef = ref(null)

function handleClickOutside(e) {
  if (dropdownRef.value && !dropdownRef.value.contains(e.target)) {
    open.value = false
  }
}

async function handleExport(opt) {
  exporting.value = true
  open.value = false
  try {
    const { data } = await opt.fn()
    const url = URL.createObjectURL(data)
    const a = document.createElement('a')
    a.href = url
    a.download = opt.filename || 'export'
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    errorMsg.value = 'Export failed. Please try again.'
    setTimeout(() => { errorMsg.value = null }, 4000)
  } finally {
    exporting.value = false
  }
}

onMounted(() => document.addEventListener('click', handleClickOutside))
onBeforeUnmount(() => document.removeEventListener('click', handleClickOutside))
</script>
