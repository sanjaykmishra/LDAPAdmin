<template>
  <div class="p-6 max-w-5xl">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">User Forms</h1>
        <p class="text-sm text-gray-500 mt-1">Define how LDAP attributes are presented and validated in user creation and edit forms</p>
      </div>
      <button @click="openCreate" class="btn-primary">+ New Form</button>
    </div>

    <div class="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <div v-if="loading" class="p-8 text-center text-gray-500 text-sm">Loading…</div>
      <div v-else-if="forms.length === 0" class="p-8 text-center text-gray-400 text-sm">
        No user forms configured. Create one to define attribute display rules for user entries.
      </div>
      <table v-else class="w-full text-sm">
        <thead class="bg-gray-50 border-b border-gray-100">
          <tr>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Form Name</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Directory</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Object Class</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Attributes</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-50">
          <tr v-for="f in forms" :key="f.id" class="hover:bg-gray-50">
            <td class="px-4 py-3 font-medium text-gray-900">{{ f.formName }}</td>
            <td class="px-4 py-3 text-gray-600 text-xs">{{ dirName(f.directoryId) }}</td>
            <td class="px-4 py-3 text-gray-600 font-mono text-xs">{{ f.objectClassName }}</td>
            <td class="px-4 py-3">
              <div class="flex flex-wrap gap-1">
                <span
                  v-for="attr in f.attributeConfigs"
                  :key="attr.id"
                  class="text-xs bg-blue-50 text-blue-700 rounded px-1.5 py-0.5"
                >{{ attr.attributeName }}</span>
                <span v-if="!f.attributeConfigs.length" class="text-xs text-gray-400">None</span>
              </div>
            </td>
            <td class="px-4 py-3 text-right whitespace-nowrap">
              <button @click="openEdit(f)" class="text-blue-600 hover:text-blue-800 text-xs font-medium mr-3">Edit</button>
              <button @click="confirmDelete(f)" class="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Create/Edit modal -->
    <AppModal v-model="showModal" :title="editing ? 'Edit User Form' : 'New User Form'" size="xl">
      <form @submit.prevent="save" class="space-y-4">
        <!-- Directory picker (not persisted — used to look up schema) -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Directory</label>
          <select v-model="selectedDirId" class="input w-full">
            <option value="">— Select directory —</option>
            <option v-for="d in directories" :key="d.id" :value="d.id">{{ d.displayName }}</option>
          </select>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <FormField label="Form Name" v-model="form.formName" required placeholder="e.g. Standard User Form" />
          <!-- Object class picker or freetext depending on whether a directory is selected -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Object Class <span class="text-red-500">*</span></label>
            <select v-if="selectedDirId" v-model="form.objectClassName" class="input w-full" required>
              <option value="" disabled>{{ loadingOCs ? 'Loading…' : '— Select object class —' }}</option>
              <option v-for="oc in objectClasses" :key="oc" :value="oc">{{ oc }}</option>
            </select>
            <input v-else v-model="form.objectClassName" class="input w-full" required placeholder="e.g. inetOrgPerson" />
          </div>
        </div>

        <div v-if="loadingAttrs" class="text-sm text-gray-500">Loading attributes…</div>

        <!-- Attribute configs -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">Attribute Configurations</label>
          <div v-if="form.attributeConfigs.length" class="border border-gray-200 rounded-lg overflow-hidden mb-2">
            <table class="w-full text-sm">
              <thead class="bg-gray-50">
                <tr>
                  <th class="px-3 py-2 text-center text-xs font-medium text-gray-500">Order</th>
                  <th class="px-3 py-2 text-left text-xs font-medium text-gray-500">Attribute</th>
                  <th class="px-3 py-2 text-left text-xs font-medium text-gray-500">Label</th>
                  <th class="px-3 py-2 text-left text-xs font-medium text-gray-500">Input Type</th>
                  <th class="px-3 py-2 text-center text-xs font-medium text-gray-500">Required</th>
                  <th class="px-3 py-2 text-center text-xs font-medium text-gray-500">Editable</th>
                  <th class="px-3 py-2"></th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100">
                <tr v-for="(attr, idx) in form.attributeConfigs" :key="idx">
                  <td class="px-3 py-2 text-center whitespace-nowrap">
                    <button
                      type="button"
                      :disabled="idx === 0"
                      @click="moveAttribute(idx, -1)"
                      class="text-gray-400 hover:text-gray-700 disabled:opacity-25 disabled:cursor-not-allowed text-xs font-bold px-1"
                      title="Move up"
                    >&#9650;</button>
                    <button
                      type="button"
                      :disabled="idx === form.attributeConfigs.length - 1"
                      @click="moveAttribute(idx, 1)"
                      class="text-gray-400 hover:text-gray-700 disabled:opacity-25 disabled:cursor-not-allowed text-xs font-bold px-1"
                      title="Move down"
                    >&#9660;</button>
                  </td>
                  <td class="px-3 py-2">
                    <input v-model="attr.attributeName" placeholder="e.g. cn" class="input w-full" required />
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
                    <input type="checkbox" v-model="attr.requiredOnCreate" />
                  </td>
                  <td class="px-3 py-2 text-center">
                    <input type="checkbox" v-model="attr.editableOnCreate" />
                  </td>
                  <td class="px-3 py-2 text-right">
                    <button
                      type="button"
                      @click="form.attributeConfigs.splice(idx, 1)"
                      :disabled="attr.requiredOnCreate"
                      :class="attr.requiredOnCreate ? 'text-gray-300 cursor-not-allowed' : 'text-red-500 hover:text-red-700'"
                      class="text-xs font-medium"
                      :title="attr.requiredOnCreate ? 'Required attributes cannot be removed' : 'Remove attribute'"
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

        <div class="flex justify-end gap-2 pt-2">
          <button type="button" @click="showModal = false" class="btn-secondary">Cancel</button>
          <button type="submit" :disabled="saving" class="btn-primary">{{ saving ? 'Saving…' : 'Save' }}</button>
        </div>
      </form>
    </AppModal>

    <!-- Confirm object class change -->
    <Teleport to="body">
      <div v-if="showOcChangeConfirm" class="fixed inset-0 z-40 flex items-center justify-center bg-black/40">
        <div class="bg-white rounded-xl shadow-xl max-w-md w-full mx-4 p-6">
          <h3 class="text-lg font-semibold text-gray-900 mb-2">Change Object Class</h3>
          <p class="text-sm text-gray-600 mb-6">
            Changing the object class will clear all existing attributes and repopulate the form with attributes from the new object class. Do you want to continue?
          </p>
          <div class="flex justify-end gap-3">
            <button @click="cancelOcChange" class="px-4 py-2 text-sm rounded-lg border border-gray-300 hover:bg-gray-50">Cancel</button>
            <button @click="confirmOcChange" class="px-4 py-2 text-sm rounded-lg text-white font-medium bg-blue-600 hover:bg-blue-700">Continue</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Add Attribute picker -->
    <Teleport to="body">
      <div v-if="showAddAttrPicker" class="fixed inset-0 z-40 flex items-center justify-center bg-black/40">
        <div class="bg-white rounded-xl shadow-xl max-w-md w-full mx-4 p-6">
          <h3 class="text-lg font-semibold text-gray-900 mb-2">Add Attributes</h3>
          <p class="text-sm text-gray-600 mb-3">Select attributes to add to the form:</p>
          <div v-if="availableAttrsForPicker.length === 0" class="text-sm text-gray-400 mb-4">All object class attributes are already in the form.</div>
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
      :message="`Delete form '${deleteTarget.formName}'? This will remove all attribute configurations and unlink it from any realms.`"
      @confirm="doDelete"
      @cancel="deleteTarget = null"
    />
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useNotificationStore } from '@/stores/notifications'
import { listUserForms, createUserForm, updateUserForm, deleteUserForm } from '@/api/userForms'
import { listDirectories } from '@/api/directories'
import { listObjectClasses, getObjectClass } from '@/api/schema'
import FormField from '@/components/FormField.vue'
import AppModal from '@/components/AppModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const notif = useNotificationStore()

const inputTypes = ['TEXT', 'TEXTAREA', 'PASSWORD', 'BOOLEAN', 'DATE', 'DATETIME', 'MULTI_VALUE', 'DN_LOOKUP']

const loading        = ref(false)
const saving         = ref(false)
const forms          = ref([])
const directories    = ref([])
const objectClasses  = ref([])
const loadingOCs     = ref(false)
const loadingAttrs   = ref(false)
const showModal      = ref(false)
const editing        = ref(null)
const deleteTarget   = ref(null)
const selectedDirId  = ref('')

const form = ref(emptyForm())

// When selected directory changes, sync to form and fetch object classes
watch(selectedDirId, async (dirId) => {
  form.value.directoryId = dirId || null
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

// Track whether this is the initial object class value (set when dialog opens)
const initialOcLoad = ref(true)

// Object class change confirmation
const showOcChangeConfirm = ref(false)
const pendingOcName       = ref('')
const previousOcName      = ref('')
const revertingOc         = ref(false)

// Cached schema attributes for the current object class (used by Add Attribute picker)
const cachedSchemaAttrs = ref([])

// Add Attribute picker state
const showAddAttrPicker       = ref(false)
const availableAttrsForPicker = ref([])
const pickerSelected          = ref([])

watch(() => form.value.objectClassName, async (ocName, oldVal) => {
  if (!ocName || !selectedDirId.value) return

  // Skip if we're reverting from a cancelled change
  if (revertingOc.value) {
    revertingOc.value = false
    return
  }

  // On initial load (when dialog first opens), just fetch and cache — no confirmation, no clearing
  if (initialOcLoad.value) {
    initialOcLoad.value = false
    await fetchAndPopulateAttributes(ocName, false)
    return
  }

  // If the form already has attributes, confirm before clearing
  if (form.value.attributeConfigs.length > 0) {
    pendingOcName.value = ocName
    previousOcName.value = oldVal || ''
    showOcChangeConfirm.value = true
    return
  }

  // No existing attributes — just populate directly
  await fetchAndPopulateAttributes(ocName, true)
})

function cancelOcChange() {
  showOcChangeConfirm.value = false
  // Revert the object class selection without re-triggering the watcher
  revertingOc.value = true
  form.value.objectClassName = previousOcName.value
  pendingOcName.value = ''
}

async function confirmOcChange() {
  showOcChangeConfirm.value = false
  // Clear existing attributes and repopulate
  await fetchAndPopulateAttributes(pendingOcName.value, true)
  pendingOcName.value = ''
}

async function fetchAndPopulateAttributes(ocName, replaceAll) {
  loadingAttrs.value = true
  try {
    const { data } = await getObjectClass(selectedDirId.value, ocName)

    // Build full attribute list from the object class schema
    const allAttrs = []
    for (const name of (data.required || [])) {
      allAttrs.push({ attributeName: name, customLabel: '', inputType: 'TEXT', requiredOnCreate: true, editableOnCreate: true })
    }
    for (const name of (data.optional || [])) {
      allAttrs.push({ attributeName: name, customLabel: '', inputType: 'TEXT', requiredOnCreate: false, editableOnCreate: true })
    }

    // Cache for the Add Attribute picker
    cachedSchemaAttrs.value = allAttrs

    if (replaceAll) {
      form.value.attributeConfigs = allAttrs
    }
  } catch (e) {
    notif.error('Failed to load attributes: ' + (e.response?.data?.detail || e.message))
  } finally {
    loadingAttrs.value = false
  }
}

function emptyForm() {
  return {
    directoryId: null,
    formName: '',
    objectClassName: '',
    attributeConfigs: [],
  }
}

function emptyAttribute() {
  return {
    attributeName: '',
    customLabel: '',
    inputType: 'TEXT',
    requiredOnCreate: false,
    editableOnCreate: true,
  }
}

function dirName(dirId) {
  if (!dirId) return '—'
  const d = directories.value.find(d => d.id === dirId)
  return d ? d.displayName : dirId
}

function addAttribute() {
  const existing = new Set(
    form.value.attributeConfigs.map(a => a.attributeName.toLowerCase())
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
      form.value.attributeConfigs.push({ ...attr })
    }
  }
  showAddAttrPicker.value = false
}

function moveAttribute(idx, direction) {
  const list = form.value.attributeConfigs
  const target = idx + direction
  if (target < 0 || target >= list.length) return
  const [item] = list.splice(idx, 1)
  list.splice(target, 0, item)
}

async function load() {
  loading.value = true
  try {
    const [formsRes, dirsRes] = await Promise.all([listUserForms(), listDirectories()])
    forms.value = formsRes.data
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
  cachedSchemaAttrs.value = []
  initialOcLoad.value = false
  form.value = emptyForm()
  showModal.value = true
}

function openEdit(f) {
  editing.value = f.id
  // Pre-seed with the current value so the <select> has a matching option
  // while the full list loads asynchronously — prevents v-model reset.
  objectClasses.value = f.objectClassName ? [f.objectClassName] : []
  cachedSchemaAttrs.value = []
  initialOcLoad.value = true
  selectedDirId.value = f.directoryId || ''
  form.value = {
    directoryId: f.directoryId || null,
    formName: f.formName,
    objectClassName: f.objectClassName,
    attributeConfigs: (f.attributeConfigs || []).map(a => ({
      attributeName: a.attributeName,
      customLabel: a.customLabel || '',
      inputType: a.inputType,
      requiredOnCreate: a.requiredOnCreate,
      editableOnCreate: a.editableOnCreate,
    })),
  }
  showModal.value = true
}

async function save() {
  saving.value = true
  try {
    if (editing.value) {
      await updateUserForm(editing.value, form.value)
      notif.success('Form updated')
    } else {
      await createUserForm(form.value)
      notif.success('Form created')
    }
    showModal.value = false
    await load()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    saving.value = false
  }
}

function confirmDelete(f) { deleteTarget.value = f }

async function doDelete() {
  try {
    await deleteUserForm(deleteTarget.value.id)
    notif.success('Form deleted')
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
