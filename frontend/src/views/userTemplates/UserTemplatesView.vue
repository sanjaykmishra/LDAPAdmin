<template>
  <div class="p-6 max-w-5xl">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">User Templates</h1>
        <p class="text-sm text-gray-500 mt-1">Define how LDAP attributes are presented and validated in user creation and edit forms</p>
      </div>
      <button @click="openCreate" class="btn-primary">+ New Template</button>
    </div>

    <div class="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <div v-if="loading" class="p-8 text-center text-gray-500 text-sm">Loading…</div>
      <div v-else-if="templates.length === 0" class="p-8 text-center text-gray-400 text-sm">
        No user templates configured. Create one to define attribute display rules for user entries.
      </div>
      <table v-else class="w-full text-sm">
        <thead class="bg-gray-50 border-b border-gray-100">
          <tr>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Template Name</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Directory</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Object Classes</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Attributes</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-50">
          <tr v-for="t in templates" :key="t.id" class="hover:bg-gray-50">
            <td class="px-4 py-3 font-medium text-gray-900">{{ t.templateName }}</td>
            <td class="px-4 py-3 text-gray-600 text-xs">{{ dirName(t.directoryId) }}</td>
            <td class="px-4 py-3">
              <div class="flex flex-wrap gap-1">
                <span
                  v-for="oc in t.objectClassNames"
                  :key="oc"
                  class="text-xs bg-purple-50 text-purple-700 rounded px-1.5 py-0.5 font-mono"
                >{{ oc }}</span>
              </div>
            </td>
            <td class="px-4 py-3">
              <div class="flex flex-wrap gap-1">
                <span
                  v-for="attr in t.attributeConfigs"
                  :key="attr.id"
                  class="text-xs bg-blue-50 text-blue-700 rounded px-1.5 py-0.5"
                >{{ attr.attributeName }}</span>
                <span v-if="!t.attributeConfigs.length" class="text-xs text-gray-400">None</span>
              </div>
            </td>
            <td class="px-4 py-3 text-right whitespace-nowrap">
              <button @click="openEdit(t)" class="text-blue-600 hover:text-blue-800 text-xs font-medium mr-3">Edit</button>
              <button @click="confirmDelete(t)" class="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Create/Edit modal -->
    <AppModal v-model="showModal" :title="editing ? 'Edit User Template' : 'New User Template'" size="xl">
      <form @submit.prevent="save" class="space-y-4">
        <!-- Directory picker -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Directory</label>
          <select v-model="selectedDirId" class="input w-full">
            <option value="">— Select directory —</option>
            <option v-for="d in directories" :key="d.id" :value="d.id">{{ d.displayName }}</option>
          </select>
        </div>

        <FormField label="Template Name" v-model="template.templateName" required placeholder="e.g. Standard User Template" />

        <!-- Object Classes section -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">Object Classes <span class="text-red-500">*</span></label>
          <div v-if="template.objectClassNames.length" class="flex flex-wrap gap-2 mb-2">
            <span
              v-for="oc in template.objectClassNames"
              :key="oc"
              class="inline-flex items-center gap-1 text-sm bg-purple-50 text-purple-700 rounded-lg px-2.5 py-1 font-mono"
            >
              {{ oc }}
              <button type="button" @click="removeObjectClass(oc)" class="text-purple-400 hover:text-red-600 ml-1" title="Remove">&times;</button>
            </span>
          </div>
          <div class="flex gap-2">
            <select v-if="selectedDirId" v-model="ocToAdd" class="input flex-1">
              <option value="" disabled>{{ loadingOCs ? 'Loading…' : '— Add object class —' }}</option>
              <option v-for="oc in availableObjectClasses" :key="oc" :value="oc">{{ oc }}</option>
            </select>
            <input v-else v-model="ocToAdd" class="input flex-1" placeholder="e.g. inetOrgPerson" />
            <button type="button" @click="addObjectClass" :disabled="!ocToAdd" class="btn-primary text-xs">Add</button>
          </div>
        </div>

        <div v-if="loadingAttrs" class="text-sm text-gray-500">Loading attributes…</div>

        <!-- Tabs: Attributes | Layout -->
        <div class="border-b border-gray-200">
          <div class="flex">
            <button
              type="button"
              @click="modalTab = 'attributes'"
              class="px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors"
              :class="modalTab === 'attributes' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'"
            >Attributes</button>
            <button
              type="button"
              @click="modalTab = 'layout'"
              class="px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors"
              :class="modalTab === 'layout' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'"
            >Layout Designer</button>
          </div>
        </div>

        <!-- Attributes tab -->
        <div v-show="modalTab === 'attributes'">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">Attribute Configurations</label>
            <div v-if="template.attributeConfigs.length" class="border border-gray-200 rounded-lg overflow-hidden mb-2">
              <table class="w-full text-sm">
                <thead class="bg-gray-50">
                  <tr>
                    <th class="px-3 py-2 text-left text-xs font-medium text-gray-500">Attribute</th>
                    <th class="px-3 py-2 text-left text-xs font-medium text-gray-500">Label</th>
                    <th class="px-3 py-2 text-left text-xs font-medium text-gray-500">Input Type</th>
                    <th class="px-3 py-2 text-center text-xs font-medium text-gray-500">RDN</th>
                    <th class="px-3 py-2 text-center text-xs font-medium text-gray-500">Required</th>
                    <th class="px-3 py-2 text-center text-xs font-medium text-gray-500">Editable</th>
                    <th class="px-3 py-2 text-center text-xs font-medium text-gray-500">Hidden</th>
                    <th class="px-3 py-2"></th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-100">
                  <tr v-for="(attr, idx) in template.attributeConfigs" :key="idx">
                    <td class="px-3 py-2">
                      <input v-model="attr.attributeName" placeholder="e.g. cn" class="input w-full" required disabled />
                    </td>
                    <td class="px-3 py-2">
                      <input v-model="attr.customLabel" placeholder="Custom label" class="input w-full" />
                    </td>
                    <td class="px-3 py-2">
                      <select v-model="attr.inputType" class="input w-full">
                        <option v-for="t in inputTypes" :key="t" :value="t">{{ t }}</option>
                      </select>
                    </td>
                    <td class="px-3 py-2 text-center">
                      <input type="radio" name="rdn-selector" :checked="attr.rdn" @change="setRdn(idx)" />
                    </td>
                    <td class="px-3 py-2 text-center">
                      <input type="checkbox" v-model="attr.requiredOnCreate" :disabled="attr.rdn" />
                    </td>
                    <td class="px-3 py-2 text-center">
                      <input type="checkbox" v-model="attr.editableOnCreate" />
                    </td>
                    <td class="px-3 py-2 text-center">
                      <input type="checkbox" v-model="attr.hidden" :disabled="attr.rdn" />
                    </td>
                    <td class="px-3 py-2 text-right">
                      <button
                        type="button"
                        @click="template.attributeConfigs.splice(idx, 1)"
                        :disabled="attr.requiredOnCreate || attr.rdn"
                        :class="attr.requiredOnCreate || attr.rdn ? 'text-gray-300 cursor-not-allowed' : 'text-red-500 hover:text-red-700'"
                        class="text-xs font-medium"
                      >Remove</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <button
              type="button"
              @click="addAttribute"
              class="text-blue-600 hover:text-blue-800 text-xs font-medium"
            >+ Add attribute</button>
          </div>
        </div>

        <!-- Layout Designer tab -->
        <div v-show="modalTab === 'layout'">
          <FormLayoutDesigner
            :attribute-configs="template.attributeConfigs"
            @update:attribute-configs="onLayoutUpdate"
          />
        </div>

        <div class="flex justify-end gap-2 pt-2">
          <button type="button" @click="showModal = false" class="btn-secondary">Cancel</button>
          <button type="submit" :disabled="saving" class="btn-primary">{{ saving ? 'Saving…' : 'Save' }}</button>
        </div>
      </form>
    </AppModal>

    <!-- Add Attribute picker -->
    <Teleport to="body">
      <div v-if="showAddAttrPicker" class="fixed inset-0 z-40 flex items-center justify-center bg-black/40">
        <div class="bg-white rounded-xl shadow-xl max-w-md w-full mx-4 p-6">
          <h3 class="text-lg font-semibold text-gray-900 mb-2">Add Attributes</h3>
          <p class="text-sm text-gray-600 mb-3">Select attributes to add to the template:</p>
          <div v-if="availableAttrsForPicker.length === 0" class="text-sm text-gray-400 mb-4">All object class attributes are already in the template.</div>
          <div v-else class="max-h-64 overflow-y-auto mb-4 border border-gray-200 rounded-lg divide-y divide-gray-100">
            <label
              v-for="attr in availableAttrsForPicker"
              :key="attr.attributeName"
              class="flex items-center gap-2 px-3 py-2 hover:bg-gray-50 cursor-pointer"
            >
              <input type="checkbox" v-model="pickerSelected" :value="attr.attributeName" class="rounded" />
              <span class="text-sm font-mono">{{ attr.attributeName }}</span>
              <span v-if="attr.requiredOnCreate" class="text-xs text-red-500 ml-auto">required</span>
              <span v-else class="text-xs text-gray-400 ml-auto">optional</span>
            </label>
          </div>
          <div class="flex justify-end gap-3">
            <button @click="showAddAttrPicker = false" class="px-4 py-2 text-sm rounded-lg border border-gray-300 hover:bg-gray-50">Cancel</button>
            <button @click="addSelectedAttributes" :disabled="pickerSelected.length === 0" class="px-4 py-2 text-sm rounded-lg text-white font-medium bg-blue-600 hover:bg-blue-700 disabled:opacity-50">OK</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Delete confirm -->
    <ConfirmDialog
      v-if="deleteTarget"
      :message="`Delete template '${deleteTarget.templateName}'? This will remove all attribute configurations and unlink it from any realms.`"
      @confirm="doDelete"
      @cancel="deleteTarget = null"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useNotificationStore } from '@/stores/notifications'
import { listUserTemplates, createUserTemplate, updateUserTemplate, deleteUserTemplate } from '@/api/userTemplates'
import { listDirectories } from '@/api/directories'
import { listObjectClasses, getObjectClass } from '@/api/schema'
import FormField from '@/components/FormField.vue'
import AppModal from '@/components/AppModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormLayoutDesigner from '@/components/FormLayoutDesigner.vue'

const notif = useNotificationStore()

const inputTypes = ['TEXT', 'TEXTAREA', 'PASSWORD', 'BOOLEAN', 'DATE', 'DATETIME', 'MULTI_VALUE', 'DN_LOOKUP']

const loading        = ref(false)
const saving         = ref(false)
const templates      = ref([])
const directories    = ref([])
const objectClasses  = ref([])
const loadingOCs     = ref(false)
const loadingAttrs   = ref(false)
const showModal      = ref(false)
const editing        = ref(null)
const deleteTarget   = ref(null)
const selectedDirId  = ref('')
const ocToAdd        = ref('')
const modalTab       = ref('attributes')

const template = ref(emptyTemplate())

/**
 * Per-objectClass schema cache.  Key = class name, value = { required: Set, optional: Set }.
 * Used to determine which attributes to add/remove when objectClasses change.
 */
const ocSchemaCache = ref({})

// When selected directory changes, sync to template and fetch object classes
watch(selectedDirId, async (dirId) => {
  template.value.directoryId = dirId || null
  if (!dirId) {
    objectClasses.value = []
    return
  }
  loadingOCs.value = true
  try {
    const { data } = await listObjectClasses(dirId)
    objectClasses.value = data
  } catch (e) {
    objectClasses.value = []
    notif.error('Failed to load object classes: ' + (e.response?.data?.detail || e.message))
  } finally {
    loadingOCs.value = false
  }
})

/** Object classes not yet added to the template. */
const availableObjectClasses = computed(() => {
  const added = new Set(template.value.objectClassNames.map(n => n.toLowerCase()))
  return objectClasses.value.filter(oc => !added.has(oc.toLowerCase()))
})

// ── Add / remove object classes ──────────────────────────────────────────────

async function addObjectClass() {
  const oc = ocToAdd.value?.trim()
  if (!oc) return
  if (template.value.objectClassNames.some(n => n.toLowerCase() === oc.toLowerCase())) return

  template.value.objectClassNames.push(oc)
  ocToAdd.value = ''

  // Fetch schema attributes for this class, add new ones
  if (selectedDirId.value) {
    await fetchAndMergeAttributes(oc)
  }
}

async function removeObjectClass(oc) {
  template.value.objectClassNames = template.value.objectClassNames.filter(n => n !== oc)

  // Build the set of attributes owned by the remaining classes
  const remainingAttrs = new Set()
  for (const name of template.value.objectClassNames) {
    const cached = ocSchemaCache.value[name.toLowerCase()]
    if (cached) {
      cached.required.forEach(a => remainingAttrs.add(a.toLowerCase()))
      cached.optional.forEach(a => remainingAttrs.add(a.toLowerCase()))
    }
  }

  // Determine which attributes were contributed by the removed class
  const removedCache = ocSchemaCache.value[oc.toLowerCase()]
  if (removedCache) {
    const removedAttrs = new Set()
    removedCache.required.forEach(a => removedAttrs.add(a.toLowerCase()))
    removedCache.optional.forEach(a => removedAttrs.add(a.toLowerCase()))

    // Only remove attributes unique to the removed class
    template.value.attributeConfigs = template.value.attributeConfigs.filter(attr => {
      const lower = attr.attributeName.toLowerCase()
      if (!removedAttrs.has(lower)) return true   // not from the removed class
      if (remainingAttrs.has(lower)) return true   // shared with a remaining class
      return false                                 // unique to removed class — drop it
    })
  }

  delete ocSchemaCache.value[oc.toLowerCase()]
}

async function fetchAndMergeAttributes(ocName) {
  loadingAttrs.value = true
  try {
    const { data } = await getObjectClass(selectedDirId.value, ocName)

    const required = new Set(data.required || [])
    const optional = new Set(data.optional || [])
    ocSchemaCache.value[ocName.toLowerCase()] = { required, optional }

    // Determine which attributes are already in the template
    const existing = new Set(template.value.attributeConfigs.map(a => a.attributeName.toLowerCase()))

    // Add required attributes that are new (insert at top, before first non-required)
    const newRequired = []
    for (const name of required) {
      if (!existing.has(name.toLowerCase())) {
        newRequired.push({
          attributeName: name, customLabel: '', inputType: 'TEXT',
          requiredOnCreate: true, editableOnCreate: true, rdn: false,
          sectionName: '', columnSpan: 3, hidden: false,
        })
      }
    }

    // Add optional attributes that are new (append at end)
    const newOptional = []
    for (const name of optional) {
      if (!existing.has(name.toLowerCase()) && !required.has(name)) {
        newOptional.push({
          attributeName: name, customLabel: '', inputType: 'TEXT',
          requiredOnCreate: false, editableOnCreate: true, rdn: false,
          sectionName: '', columnSpan: 3, hidden: false,
        })
      }
    }

    // Insert required before the first optional attribute, or at the start
    if (newRequired.length) {
      // Find the first non-required attribute index
      const firstOptIdx = template.value.attributeConfigs.findIndex(a => !a.requiredOnCreate)
      const insertIdx = firstOptIdx >= 0 ? firstOptIdx : template.value.attributeConfigs.length
      template.value.attributeConfigs.splice(insertIdx, 0, ...newRequired)
    }

    // Append optional at the end
    template.value.attributeConfigs.push(...newOptional)
  } catch (e) {
    notif.error('Failed to load attributes for ' + ocName + ': ' + (e.response?.data?.detail || e.message))
  } finally {
    loadingAttrs.value = false
  }
}

/** Cached combined schema attrs for the Add Attribute picker. */
const cachedSchemaAttrs = computed(() => {
  const result = []
  const seen = new Set()
  for (const ocName of template.value.objectClassNames) {
    const cached = ocSchemaCache.value[ocName.toLowerCase()]
    if (!cached) continue
    for (const name of cached.required) {
      if (!seen.has(name.toLowerCase())) {
        seen.add(name.toLowerCase())
        result.push({ attributeName: name, requiredOnCreate: true })
      }
    }
    for (const name of cached.optional) {
      if (!seen.has(name.toLowerCase())) {
        seen.add(name.toLowerCase())
        result.push({ attributeName: name, requiredOnCreate: false })
      }
    }
  }
  return result
})

// Add Attribute picker state
const showAddAttrPicker       = ref(false)
const availableAttrsForPicker = ref([])
const pickerSelected          = ref([])

function emptyTemplate() {
  return {
    directoryId: null,
    templateName: '',
    objectClassNames: [],
    attributeConfigs: [],
  }
}

function onLayoutUpdate(updatedConfigs) {
  // Merge layout changes (sectionName, columnSpan, order) back into the template
  template.value.attributeConfigs = updatedConfigs.map(attr => {
    // Find existing config to preserve non-layout fields
    const existing = template.value.attributeConfigs.find(a => a.attributeName === attr.attributeName)
    return existing ? { ...existing, sectionName: attr.sectionName || '', columnSpan: attr.columnSpan ?? 3 } : attr
  })
}

function setRdn(idx) {
  template.value.attributeConfigs.forEach((attr, i) => {
    attr.rdn = i === idx
    if (attr.rdn) attr.requiredOnCreate = true
  })
}

function dirName(dirId) {
  if (!dirId) return '—'
  const d = directories.value.find(d => d.id === dirId)
  return d ? d.displayName : dirId
}

function addAttribute() {
  const existing = new Set(
    template.value.attributeConfigs.map(a => a.attributeName.toLowerCase())
  )
  availableAttrsForPicker.value = cachedSchemaAttrs.value.filter(
    a => !existing.has(a.attributeName.toLowerCase())
  )
  pickerSelected.value = []
  showAddAttrPicker.value = true
}

function addSelectedAttributes() {
  for (const name of pickerSelected.value) {
    const attr = availableAttrsForPicker.value.find(a => a.attributeName === name)
    if (attr) {
      template.value.attributeConfigs.push({
        attributeName: attr.attributeName,
        customLabel: '',
        inputType: 'TEXT',
        requiredOnCreate: attr.requiredOnCreate,
        editableOnCreate: true,
        rdn: false,
        sectionName: '',
        columnSpan: 3,
        hidden: false,
      })
    }
  }
  showAddAttrPicker.value = false
}

async function load() {
  loading.value = true
  try {
    const [templatesRes, dirsRes] = await Promise.all([listUserTemplates(), listDirectories()])
    templates.value = templatesRes.data
    directories.value = dirsRes.data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate() {
  editing.value = null
  selectedDirId.value = ''
  objectClasses.value = []
  ocSchemaCache.value = {}
  modalTab.value = 'attributes'
  template.value = emptyTemplate()
  showModal.value = true
}

async function openEdit(t) {
  editing.value = t.id
  ocSchemaCache.value = {}
  modalTab.value = 'attributes'
  // Pre-seed objectClasses list so the dropdown has matching options
  objectClasses.value = t.objectClassNames?.length ? [...t.objectClassNames] : []
  selectedDirId.value = t.directoryId || ''
  template.value = {
    directoryId: t.directoryId || null,
    templateName: t.templateName,
    objectClassNames: [...(t.objectClassNames || [])],
    attributeConfigs: (t.attributeConfigs || []).map(a => ({
      attributeName: a.attributeName,
      customLabel: a.customLabel || '',
      inputType: a.inputType,
      requiredOnCreate: a.requiredOnCreate,
      editableOnCreate: a.editableOnCreate,
      rdn: a.rdn || false,
      sectionName: a.sectionName || '',
      columnSpan: a.columnSpan ?? 3,
      hidden: a.hidden || false,
    })),
  }
  showModal.value = true

  // Pre-populate the schema cache for existing object classes
  if (t.directoryId && t.objectClassNames?.length) {
    for (const oc of t.objectClassNames) {
      try {
        const { data } = await getObjectClass(t.directoryId, oc)
        ocSchemaCache.value[oc.toLowerCase()] = {
          required: new Set(data.required || []),
          optional: new Set(data.optional || []),
        }
      } catch { /* best-effort */ }
    }
  }
}

async function save() {
  if (!template.value.objectClassNames.length) {
    notif.error('At least one object class is required')
    return
  }
  // Validate exactly one RDN attribute
  if (template.value.attributeConfigs.length > 0) {
    const rdnCount = template.value.attributeConfigs.filter(a => a.rdn).length
    if (rdnCount === 0) {
      notif.error('One attribute must be designated as the RDN attribute')
      return
    }
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateUserTemplate(editing.value, template.value)
      notif.success('Template updated')
    } else {
      await createUserTemplate(template.value)
      notif.success('Template created')
    }
    showModal.value = false
    await load()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    saving.value = false
  }
}

function confirmDelete(t) { deleteTarget.value = t }

async function doDelete() {
  try {
    await deleteUserTemplate(deleteTarget.value.id)
    notif.success('Template deleted')
    deleteTarget.value = null
    await load()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
    deleteTarget.value = null
  }
}
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary   { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
.btn-secondary { @apply px-4 py-2 bg-white border border-gray-300 rounded-lg text-sm font-medium hover:bg-gray-50; }
.input         { @apply border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
</style>
