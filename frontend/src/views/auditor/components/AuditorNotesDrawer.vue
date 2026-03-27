<template>
  <div v-if="open" class="fixed inset-0 z-50 flex justify-end print:hidden"
       @keydown.escape="open = false" tabindex="-1">
    <div class="fixed inset-0 bg-black/30" @click="open = false" />
    <div class="relative w-full max-w-sm bg-white shadow-xl flex flex-col h-full">
      <!-- Header -->
      <div class="flex items-center justify-between px-5 py-4 border-b border-slate-200">
        <h3 class="text-sm font-semibold text-slate-900">Auditor Notes</h3>
        <button @click="open = false" class="text-slate-400 hover:text-slate-600">
          <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <!-- Disclaimer -->
      <div class="px-5 py-2 bg-slate-50 border-b border-slate-200">
        <p class="text-[10px] text-slate-500 flex items-center gap-1">
          <svg class="w-3 h-3 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z" />
          </svg>
          Your notes are stored locally and never sent to the server.
        </p>
      </div>

      <!-- Section selector -->
      <div class="px-5 py-3 border-b border-slate-200">
        <select v-model="currentSection" class="w-full border border-slate-200 rounded-lg px-3 py-1.5 text-xs">
          <option v-for="s in sections" :key="s.key" :value="s.key">{{ s.label }}</option>
        </select>
      </div>

      <!-- Notes textarea -->
      <div class="flex-1 px-5 py-3 overflow-y-auto">
        <textarea v-model="currentNote" @input="save"
                  class="w-full h-full min-h-[200px] border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-700 resize-none focus:outline-none focus:ring-2 focus:ring-slate-300"
                  placeholder="Type your notes for this section..." />
      </div>

      <!-- Actions -->
      <div class="px-5 py-3 border-t border-slate-200 flex gap-2">
        <button @click="copyWithNotes"
                class="flex-1 px-3 py-2 text-xs bg-slate-700 text-white rounded-lg hover:bg-slate-800 flex items-center justify-center gap-1.5">
          <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M15.666 3.888A2.25 2.25 0 0013.5 2.25h-3c-1.03 0-1.9.693-2.166 1.638m7.332 0c.055.194.084.4.084.612v0a.75.75 0 01-.75.75H9.75a.75.75 0 01-.75-.75v0c0-.212.03-.418.084-.612m7.332 0c.646.049 1.288.11 1.927.184 1.1.128 1.907 1.077 1.907 2.185V19.5a2.25 2.25 0 01-2.25 2.25H6.75A2.25 2.25 0 014.5 19.5V6.257c0-1.108.806-2.057 1.907-2.185a48.208 48.208 0 011.927-.184" />
          </svg>
          {{ copied ? 'Copied!' : 'Copy section + notes' }}
        </button>
        <button @click="clearNote"
                class="px-3 py-2 text-xs border border-slate-200 text-slate-600 rounded-lg hover:bg-slate-50">
          Clear
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'

const props = defineProps({
  token: String,
  scope: Object,
})

const route = useRoute()
const open = ref(false)
const copied = ref(false)

const sections = computed(() => {
  const items = [
    { key: 'overview', label: 'Overview' },
    { key: 'campaigns', label: 'Access Review Campaigns' },
  ]
  if (props.scope?.includeSod) items.push({ key: 'sod', label: 'Separation of Duties' })
  if (props.scope?.includeEntitlements) items.push({ key: 'entitlements', label: 'Entitlements' })
  if (props.scope?.includeAuditEvents) items.push({ key: 'audit-events', label: 'Audit Log' })
  items.push({ key: 'approvals', label: 'Approvals' })
  return items
})

// Auto-detect section from route
const currentSection = ref('overview')
watch(() => route.path, (path) => {
  const segment = path.split('/').pop()
  const match = sections.value.find(s => s.key === segment)
  if (match) currentSection.value = match.key
  else currentSection.value = 'overview'
}, { immediate: true })

// localStorage-backed notes keyed by token + section
function storageKey(section) {
  return `auditor-notes:${props.token}:${section}`
}

const currentNote = ref('')

// Load note when section changes
watch(currentSection, (section) => {
  currentNote.value = localStorage.getItem(storageKey(section)) || ''
}, { immediate: true })

function save() {
  localStorage.setItem(storageKey(currentSection.value), currentNote.value)
}

function clearNote() {
  currentNote.value = ''
  localStorage.removeItem(storageKey(currentSection.value))
}

function copyWithNotes() {
  const sectionLabel = sections.value.find(s => s.key === currentSection.value)?.label || currentSection.value
  const text = `--- ${sectionLabel} ---\nSection: ${currentSection.value}\nNotes:\n${currentNote.value}\n---`
  navigator.clipboard?.writeText(text).then(() => {
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  }).catch(() => {})
}

function toggle() {
  open.value = !open.value
}

defineExpose({ toggle, open })
</script>
