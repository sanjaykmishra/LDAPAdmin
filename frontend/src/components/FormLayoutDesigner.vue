<template>
  <div class="space-y-4">
    <!-- Toolbar -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <button type="button" @click="addSection" class="btn-secondary text-xs">+ Add Section</button>
        <span class="text-xs text-gray-400">Drag fields to reorder. Use column spans to control width.</span>
      </div>
      <div class="flex items-center gap-3">
        <label class="flex items-center gap-2 text-xs text-gray-600 cursor-pointer select-none">
          <input type="checkbox" v-model="localShowDnField" class="rounded" />
          Show DN field on form
        </label>
        <button
          type="button"
          @click="showPreview = !showPreview"
          :class="showPreview ? 'btn-primary' : 'btn-secondary'"
          class="text-xs"
        >{{ showPreview ? 'Hide Preview' : 'Preview' }}</button>
      </div>
    </div>

    <!-- Live Preview -->
    <div v-if="showPreview" class="border border-blue-200 bg-blue-50/30 rounded-xl p-4">
      <h4 class="text-sm font-semibold text-blue-800 mb-3">Form Preview</h4>
      <div class="bg-white rounded-lg border border-gray-200 p-4 space-y-4">
        <template v-for="(section, sIdx) in previewSections" :key="section.id">
          <fieldset v-if="section.fields.length" class="space-y-3">
            <legend v-if="section.name" class="text-sm font-semibold text-gray-800 pb-1 border-b border-gray-100 w-full mb-2">{{ section.name }}</legend>
            <div class="grid grid-cols-3 gap-3">
              <template v-for="field in section.fields" :key="field.attributeName">
                <!-- RDN field -->
                <div v-if="field.rdn" :style="{ gridColumn: localShowDnField ? 'span 1' : `span ${field.columnSpan || 3}` }">
                  <label class="block text-sm font-medium text-gray-700 mb-1">
                    {{ field.customLabel || field.attributeName }}
                    <span class="text-red-500">*</span>
                    <span class="text-xs bg-amber-100 text-amber-700 rounded px-1 ml-1">RDN</span>
                  </label>
                  <div class="w-full h-9 border border-gray-200 rounded-lg bg-gray-50"></div>
                </div>
                <!-- Computed DN (shown immediately after RDN when enabled) -->
                <div v-if="field.rdn && localShowDnField" class="col-span-2">
                  <label class="block text-sm font-medium text-gray-700 mb-1">
                    DN
                    <span class="text-xs bg-gray-100 text-gray-500 rounded px-1 ml-1">computed</span>
                  </label>
                  <div class="w-full h-9 border border-gray-200 rounded-lg bg-gray-100 flex items-center px-3 text-xs text-gray-400 italic">
                    {{ field.attributeName }}=…,ou=…,dc=…
                  </div>
                </div>
                <!-- Regular field -->
                <div
                  v-if="!field.rdn"
                  :style="{ gridColumn: `span ${field.columnSpan || 3}` }"
                >
                  <label class="block text-sm font-medium text-gray-700 mb-1">
                    {{ field.customLabel || field.attributeName }}
                    <span v-if="field.requiredOnCreate" class="text-red-500">*</span>
                  </label>
                  <div v-if="field.inputType === 'TEXTAREA' || field.inputType === 'MULTI_VALUE'" class="w-full h-16 border border-gray-200 rounded-lg bg-gray-50"></div>
                  <div v-else-if="field.inputType === 'BOOLEAN'" class="flex items-center gap-2">
                    <div class="w-4 h-4 border border-gray-300 rounded bg-white"></div>
                    <span class="text-sm text-gray-500">{{ field.customLabel || field.attributeName }}</span>
                  </div>
                  <div v-else class="w-full h-9 border border-gray-200 rounded-lg bg-gray-50"></div>
                </div>
              </template>
            </div>
          </fieldset>
        </template>
      </div>
    </div>

    <!-- Section editor -->
    <div class="space-y-3">
      <div
        v-for="(section, sIdx) in sections"
        :key="section.id"
        class="border border-gray-200 rounded-xl overflow-hidden"
        :class="dragOverSection === sIdx ? 'ring-2 ring-blue-400' : ''"
        @dragover.prevent="onDragOverSection($event, sIdx)"
        @dragleave="onDragLeaveSection"
        @drop="onDropSection($event, sIdx)"
      >
        <!-- Section header -->
        <div class="flex items-center gap-2 px-3 py-2 bg-gray-50 border-b border-gray-100">
          <svg class="w-4 h-4 text-gray-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16"/>
          </svg>
          <input
            v-model="section.name"
            placeholder="Section name (optional)"
            class="flex-1 bg-transparent text-sm font-medium text-gray-700 placeholder-gray-400 focus:outline-none"
            @input="syncToParent"
          />
          <span class="text-xs text-gray-400">{{ section.fields.length }} field{{ section.fields.length !== 1 ? 's' : '' }}</span>
          <button
            v-if="sections.length > 1"
            type="button"
            @click="removeSection(sIdx)"
            class="text-gray-400 hover:text-red-500 text-xs"
            title="Remove section"
          >&times;</button>
        </div>

        <!-- Fields in section -->
        <div class="p-2 min-h-[48px]">
          <div v-if="section.fields.length === 0" class="text-center text-xs text-gray-400 py-3">
            Drag fields here or add attributes above
          </div>
          <div class="grid grid-cols-3 gap-2">
            <template
              v-for="(field, fIdx) in section.fields"
              :key="field.attributeName"
            >
              <!-- RDN field card -->
              <div
                v-if="field.rdn"
                :style="{ gridColumn: localShowDnField ? 'span 1' : `span ${field.columnSpan || 3}` }"
                :class="[
                  dragField?.attributeName === field.attributeName ? 'opacity-30' : '',
                ]"
                class="group relative flex items-center gap-2 px-3 py-2 bg-amber-50 border border-amber-200 rounded-lg hover:border-amber-400 cursor-grab transition-colors"
                draggable="true"
                @dragstart="onDragStart($event, sIdx, fIdx, field)"
                @dragend="onDragEnd"
                @dragover.prevent.stop="onDragOverField($event, sIdx, fIdx)"
                @drop.prevent.stop="onDropField($event, sIdx, fIdx)"
              >
                <svg class="w-3.5 h-3.5 text-amber-300 shrink-0" viewBox="0 0 20 20" fill="currentColor">
                  <path d="M7 2a2 2 0 10.001 4.001A2 2 0 007 2zm0 6a2 2 0 10.001 4.001A2 2 0 007 8zm0 6a2 2 0 10.001 4.001A2 2 0 007 14zm6-8a2 2 0 10-.001-4.001A2 2 0 0013 6zm0 2a2 2 0 10.001 4.001A2 2 0 0013 8zm0 6a2 2 0 10.001 4.001A2 2 0 0013 14z"/>
                </svg>
                <div class="flex-1 min-w-0">
                  <div class="flex items-center gap-1.5">
                    <span class="text-sm font-medium text-gray-800 truncate">{{ field.customLabel || field.attributeName }}</span>
                    <span class="text-[10px] font-mono text-gray-400" v-if="field.customLabel">{{ field.attributeName }}</span>
                    <span class="text-[10px] bg-amber-100 text-amber-700 rounded px-1 font-medium">RDN</span>
                    <span class="text-red-400 text-xs">*</span>
                  </div>
                  <div class="text-[10px] text-gray-400">{{ field.inputType }} · {{ localShowDnField ? '1/3 width' : `${field.columnSpan || 3}/3 width` }}</div>
                </div>
                <!-- Column span selector (only when DN is not shown, otherwise forced to 1/3) -->
                <div v-if="!localShowDnField" class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button
                    v-for="span in [1, 2, 3]"
                    :key="span"
                    type="button"
                    @click="setColumnSpan(sIdx, fIdx, span)"
                    :class="[
                      'w-5 h-5 rounded text-[10px] font-bold border transition-colors',
                      (field.columnSpan || 3) === span
                        ? 'bg-blue-600 text-white border-blue-600'
                        : 'bg-white text-gray-500 border-gray-300 hover:border-blue-400'
                    ]"
                    :title="`${span}/3 width`"
                  >{{ span }}</button>
                </div>
              </div>
              <!-- Computed DN card (shown after RDN when enabled) -->
              <div
                v-if="field.rdn && localShowDnField"
                class="col-span-2 flex items-center gap-2 px-3 py-2 bg-gray-50 border border-dashed border-gray-300 rounded-lg"
              >
                <div class="flex-1 min-w-0">
                  <div class="flex items-center gap-1.5">
                    <span class="text-sm font-medium text-gray-500">DN</span>
                    <span class="text-[10px] bg-gray-100 text-gray-500 rounded px-1 font-medium">computed</span>
                  </div>
                  <div class="text-[10px] text-gray-400">Auto-generated from RDN · 2/3 width</div>
                </div>
              </div>

              <!-- Regular field card -->
              <div
                v-if="!field.rdn"
                :style="{ gridColumn: `span ${field.columnSpan || 3}` }"
                :class="[
                  dragField?.attributeName === field.attributeName ? 'opacity-30' : '',
                ]"
                class="group relative flex items-center gap-2 px-3 py-2 bg-white border border-gray-200 rounded-lg hover:border-blue-300 cursor-grab transition-colors"
                draggable="true"
                @dragstart="onDragStart($event, sIdx, fIdx, field)"
                @dragend="onDragEnd"
                @dragover.prevent.stop="onDragOverField($event, sIdx, fIdx)"
                @drop.prevent.stop="onDropField($event, sIdx, fIdx)"
              >
                <!-- Drag handle -->
                <svg class="w-3.5 h-3.5 text-gray-300 shrink-0" viewBox="0 0 20 20" fill="currentColor">
                  <path d="M7 2a2 2 0 10.001 4.001A2 2 0 007 2zm0 6a2 2 0 10.001 4.001A2 2 0 007 8zm0 6a2 2 0 10.001 4.001A2 2 0 007 14zm6-8a2 2 0 10-.001-4.001A2 2 0 0013 6zm0 2a2 2 0 10.001 4.001A2 2 0 0013 8zm0 6a2 2 0 10.001 4.001A2 2 0 0013 14z"/>
                </svg>

                <!-- Field info -->
                <div class="flex-1 min-w-0">
                  <div class="flex items-center gap-1.5">
                    <span class="text-sm font-medium text-gray-800 truncate">{{ field.customLabel || field.attributeName }}</span>
                    <span class="text-[10px] font-mono text-gray-400" v-if="field.customLabel">{{ field.attributeName }}</span>
                    <span v-if="field.requiredOnCreate" class="text-red-400 text-xs">*</span>
                  </div>
                  <div class="text-[10px] text-gray-400">{{ field.inputType }}</div>
                </div>

                <!-- Column span selector -->
                <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button
                    v-for="span in [1, 2, 3]"
                    :key="span"
                    type="button"
                    @click="setColumnSpan(sIdx, fIdx, span)"
                    :class="[
                      'w-5 h-5 rounded text-[10px] font-bold border transition-colors',
                      (field.columnSpan || 3) === span
                        ? 'bg-blue-600 text-white border-blue-600'
                        : 'bg-white text-gray-500 border-gray-300 hover:border-blue-400'
                    ]"
                    :title="`${span}/3 width`"
                  >{{ span }}</button>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  attributeConfigs: { type: Array, required: true },
  showDnField: { type: Boolean, default: true },
})
const emit = defineEmits(['update:attributeConfigs', 'update:showDnField'])

const showPreview = ref(false)
const localShowDnField = ref(props.showDnField)

watch(() => props.showDnField, (v) => { localShowDnField.value = v })
watch(localShowDnField, (v) => {
  emit('update:showDnField', v)
  // When DN display is toggled on, force RDN field to 1/3 width
  if (v) {
    for (const section of sections.value) {
      for (const field of section.fields) {
        if (field.rdn) field.columnSpan = 1
      }
    }
    syncToParent()
  }
})

let sectionIdCounter = 0
function nextSectionId() { return `section-${++sectionIdCounter}` }

// ── Build sections from flat attribute list ──────────────────────────────────

const sections = ref([])

function buildSections(attrs) {
  const map = new Map()
  for (const attr of attrs) {
    if (attr.hidden) continue
    const key = attr.sectionName || ''
    if (!map.has(key)) {
      map.set(key, { id: nextSectionId(), name: key, fields: [] })
    }
    const field = { ...attr }
    // RDN field defaults to 1/3 width when DN display is enabled
    if (field.rdn && localShowDnField.value && !field.columnSpan) {
      field.columnSpan = 1
    }
    map.get(key).fields.push(field)
  }
  const result = Array.from(map.values())
  if (result.length === 0) {
    result.push({ id: nextSectionId(), name: '', fields: [] })
  }
  return result
}

/** The RDN field (used for preview). */
const rdnField = computed(() => {
  for (const section of sections.value) {
    const f = section.fields.find(f => f.rdn)
    if (f) return f
  }
  return null
})

/** Sections for preview (includes all fields). */
const previewSections = computed(() => sections.value)

// Initialize from props
sections.value = buildSections(props.attributeConfigs)

// Watch for external changes to attributeConfigs (e.g. attributes added/removed on the Attributes tab).
// Merge new attributes into existing sections rather than rebuilding, so empty sections survive.
let syncing = false
watch(() => props.attributeConfigs, (newConfigs) => {
  if (syncing) return

  const currentNames = new Set(
    sections.value.flatMap(s => s.fields.map(f => f.attributeName))
  )
  const visibleConfigs = newConfigs.filter(a => !a.hidden)
  const incomingNames = new Set(visibleConfigs.map(a => a.attributeName))

  // Nothing changed — skip
  if (
    currentNames.size === incomingNames.size &&
    [...currentNames].every(n => incomingNames.has(n))
  ) return

  // Remove fields that were deleted on the Attributes tab
  for (const section of sections.value) {
    section.fields = section.fields.filter(f => incomingNames.has(f.attributeName))
  }

  // Add newly-added visible fields into the first section
  for (const attr of visibleConfigs) {
    if (!currentNames.has(attr.attributeName)) {
      const field = { ...attr }
      if (field.rdn && localShowDnField.value) field.columnSpan = 1
      sections.value[0].fields.push(field)
    }
  }
}, { deep: true })

function flattenSections() {
  const result = []
  for (const section of sections.value) {
    for (const field of section.fields) {
      result.push({
        ...field,
        sectionName: section.name || '',
      })
    }
  }
  return result
}

function syncToParent() {
  syncing = true
  emit('update:attributeConfigs', flattenSections())
  // Allow the next tick to propagate before re-enabling the watch
  setTimeout(() => { syncing = false }, 0)
}

// ── Section management ───────────────────────────────────────────────────────

function addSection() {
  sections.value.push({ id: nextSectionId(), name: '', fields: [] })
}

function removeSection(idx) {
  const removed = sections.value.splice(idx, 1)[0]
  // Move orphaned fields to the first section
  if (removed.fields.length && sections.value.length) {
    sections.value[0].fields.push(...removed.fields)
  }
  syncToParent()
}

// ── Column span ──────────────────────────────────────────────────────────────

function setColumnSpan(sIdx, fIdx, span) {
  sections.value[sIdx].fields[fIdx].columnSpan = span
  syncToParent()
}

// ── Drag and drop ────────────────────────────────────────────────────────────

const dragField = ref(null)
const dragSource = ref(null) // { sIdx, fIdx }
const dragOverSection = ref(null)

function onDragStart(e, sIdx, fIdx, field) {
  dragField.value = field
  dragSource.value = { sIdx, fIdx }
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', '')
}

function onDragEnd() {
  dragField.value = null
  dragSource.value = null
  dragOverSection.value = null
}

function onDragOverSection(e, sIdx) {
  dragOverSection.value = sIdx
}

function onDragLeaveSection() {
  dragOverSection.value = null
}

function onDropSection(e, targetSIdx) {
  if (!dragField.value || !dragSource.value) return
  const { sIdx: srcSIdx, fIdx: srcFIdx } = dragSource.value
  // Remove from source
  const [moved] = sections.value[srcSIdx].fields.splice(srcFIdx, 1)
  // Add to target section at the end
  sections.value[targetSIdx].fields.push(moved)
  dragOverSection.value = null
  syncToParent()
  onDragEnd()
}

function onDragOverField(e, sIdx, fIdx) {
  dragOverSection.value = sIdx
}

function onDropField(e, targetSIdx, targetFIdx) {
  if (!dragField.value || !dragSource.value) return
  const { sIdx: srcSIdx, fIdx: srcFIdx } = dragSource.value

  // Remove from source
  const [moved] = sections.value[srcSIdx].fields.splice(srcFIdx, 1)

  // Adjust target index if same section and source was before target
  let insertIdx = targetFIdx
  if (srcSIdx === targetSIdx && srcFIdx < targetFIdx) {
    insertIdx = targetFIdx
  }

  // Insert at target position
  sections.value[targetSIdx].fields.splice(insertIdx, 0, moved)

  dragOverSection.value = null
  syncToParent()
  onDragEnd()
}
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary   { @apply px-3 py-1.5 bg-blue-600 text-white rounded-lg text-xs font-medium hover:bg-blue-700 disabled:opacity-50; }
.btn-secondary { @apply px-3 py-1.5 bg-white border border-gray-300 rounded-lg text-xs font-medium hover:bg-gray-50; }
</style>
