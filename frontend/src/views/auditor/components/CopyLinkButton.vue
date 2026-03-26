<template>
  <button @click="copy" class="text-slate-400 hover:text-slate-600 transition-colors" :title="copied ? 'Copied!' : 'Copy link'">
    <svg v-if="!copied" class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
      <path stroke-linecap="round" stroke-linejoin="round" d="M13.19 8.688a4.5 4.5 0 011.242 7.244l-4.5 4.5a4.5 4.5 0 01-6.364-6.364l1.757-1.757m9.86-2.94a4.5 4.5 0 00-1.242-7.244l-4.5-4.5a4.5 4.5 0 00-6.364 6.364L5.25 8.25" />
    </svg>
    <svg v-else class="w-3.5 h-3.5 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
      <path stroke-linecap="round" stroke-linejoin="round" d="M4.5 12.75l6 6 9-13.5" />
    </svg>
  </button>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({ anchor: String })
const copied = ref(false)

function copy() {
  const url = window.location.origin + window.location.pathname + '#' + props.anchor
  navigator.clipboard?.writeText(url).then(() => {
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  }).catch(() => {
    // Clipboard API unavailable (HTTP context or permission denied) — no-op
  })
}
</script>
