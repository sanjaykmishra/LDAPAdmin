<template>
  <div class="p-6 max-w-3xl">
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Bulk Import / Export</h1>

    <!-- Import section -->
    <section class="bg-white border border-gray-200 rounded-xl p-6 mb-6">
      <h2 class="text-lg font-semibold mb-4">Import Users from CSV</h2>
      <div class="space-y-3">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Parent DN <span class="text-red-500">*</span></label>
          <DnPicker v-model="importForm.parentDn" :directoryId="dirId" placeholder="ou=people,dc=example,dc=com" />
        </div>

        <!-- Template picker + New Template + Delete button -->
        <div class="grid grid-cols-10 gap-3 items-end">
          <div class="col-span-7">
            <label class="block text-sm font-medium text-gray-700 mb-1">Import Template <span class="text-red-500">*</span></label>
            <select v-model="selectedTemplateId" class="input w-full" @change="onTemplateSelected">
              <option value="">— Select a template —</option>
              <option v-for="t in templates" :key="t.id" :value="t.id">{{ t.name }}</option>
            </select>
          </div>
          <div class="col-span-2">
            <button @click="openCreateTemplate" class="btn-primary w-full whitespace-nowrap">+ New Template</button>
          </div>
          <div class="col-span-1">
            <button @click="confirmDeleteTemplate(selectedTemplate)" :disabled="!selectedTemplate"
              class="btn-danger w-full" title="Delete selected template">&times;</button>
          </div>
        </div>

        <!-- Template-driven fields (disabled, populated from template) -->
        <div v-if="selectedTemplate" class="grid grid-cols-3 gap-3">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Object Class</label>
            <div class="input w-full bg-gray-50 text-gray-500 min-h-[38px]">
              <span v-if="!selectedTemplate.objectClass">—</span>
              <span v-else class="flex flex-wrap gap-1">
                <span v-for="oc in selectedTemplate.objectClass.split(',')" :key="oc"
                  class="inline-block bg-blue-100 text-blue-700 text-xs px-1.5 py-0.5 rounded">{{ oc }}</span>
              </span>
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">RDN Attribute</label>
            <input :value="selectedTemplate.targetKeyAttribute" disabled class="input w-full bg-gray-50 text-gray-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Conflict Handling</label>
            <input :value="conflictLabel(selectedTemplate.conflictHandling)" disabled class="input w-full bg-gray-50 text-gray-500" />
          </div>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">CSV File <span class="text-red-500">*</span></label>
          <input type="file" accept=".csv,text/csv" @change="onFileChange"
            class="block w-full text-sm text-gray-500 file:mr-3 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-medium file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100" />
        </div>
        <button @click="doPreview" :disabled="!canImport || previewing" class="btn-primary">
          {{ previewing ? 'Loading preview…' : 'Preview Import' }}
        </button>
      </div>

      <!-- Preview section -->
      <div v-if="previewResult" class="mt-4">
        <div class="p-4 rounded-lg bg-blue-50 border border-blue-200 text-sm mb-3">
          <p class="font-medium text-blue-800 mb-2">Preview: {{ previewResult.totalRows }} rows to import</p>
          <div class="max-h-64 overflow-auto">
            <table class="w-full text-xs">
              <thead class="bg-blue-100 sticky top-0">
                <tr>
                  <th class="px-2 py-1 text-left font-medium text-blue-700">#</th>
                  <th class="px-2 py-1 text-left font-medium text-blue-700">Computed DN</th>
                  <th class="px-2 py-1 text-left font-medium text-blue-700">Attributes</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-blue-100">
                <tr v-for="row in previewResult.rows" :key="row.rowNumber">
                  <td class="px-2 py-1 text-gray-600">{{ row.rowNumber }}</td>
                  <td class="px-2 py-1 font-mono text-gray-800">{{ row.computedDn || '(missing RDN)' }}</td>
                  <td class="px-2 py-1 text-gray-600">{{ formatAttrs(row.attributes) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="flex gap-2 mt-3">
            <button @click="doConfirmImport" :disabled="importing" class="btn-primary">
              {{ importing ? 'Importing…' : 'Confirm Import' }}
            </button>
            <button @click="previewResult = null" class="btn-secondary">Cancel</button>
          </div>
        </div>
      </div>

      <!-- Import result -->
      <div v-if="importResult" class="mt-4 p-4 rounded-lg bg-gray-50 border border-gray-200 text-sm">
        <div class="grid grid-cols-4 gap-3 mb-3">
          <div class="text-center"><p class="text-2xl font-bold text-green-600">{{ importResult.created }}</p><p class="text-xs text-gray-500">Created</p></div>
          <div class="text-center"><p class="text-2xl font-bold text-blue-600">{{ importResult.updated }}</p><p class="text-xs text-gray-500">Updated</p></div>
          <div class="text-center"><p class="text-2xl font-bold text-yellow-600">{{ importResult.skipped }}</p><p class="text-xs text-gray-500">Skipped</p></div>
          <div class="text-center"><p class="text-2xl font-bold text-red-600">{{ importResult.errors }}</p><p class="text-xs text-gray-500">Errors</p></div>
        </div>
        <ul v-if="importResult.rows?.filter(r => r.status === 'ERROR').length" class="space-y-1">
          <li v-for="r in importResult.rows.filter(r => r.status === 'ERROR')" :key="r.rowNumber" class="text-red-600 text-xs">
            Row {{ r.rowNumber }}: {{ r.message }}
          </li>
        </ul>
      </div>
    </section>

    <!-- Export section -->
    <section class="bg-white border border-gray-200 rounded-xl p-6">
      <h2 class="text-lg font-semibold mb-4">Export Users to CSV</h2>
      <div class="space-y-3">
        <FormField label="LDAP Filter (optional)" v-model="exportForm.filter" placeholder="(objectClass=inetOrgPerson)" />
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Base DN (optional)</label>
          <DnPicker v-model="exportForm.baseDn" :directoryId="dirId" placeholder="dc=example,dc=com" />
        </div>
        <FormField label="Attributes (comma-separated)" v-model="exportForm.attributes" placeholder="cn,mail,uid,sn" />
        <button @click="doExport" :disabled="exporting" class="btn-primary">
          {{ exporting ? 'Exporting…' : 'Download CSV' }}
        </button>
      </div>
    </section>

    <!-- Template create/edit modal -->
    <AppModal v-model="showTemplateModal" :title="editTemplate ? 'Edit Template' : 'New Template'" size="xl">
      <form @submit.prevent="saveTemplate" class="space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <FormField label="Template Name" v-model="templateForm.name" required />
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Object Class <span class="text-red-500">*</span></label>
            <select multiple v-model="templateForm.objectClasses" class="input w-full h-24" @change="onObjectClassChange">
              <option v-for="oc in objectClasses" :key="oc" :value="oc">{{ oc }}</option>
            </select>
            <p class="text-xs text-gray-400 mt-0.5">Hold Ctrl/Cmd to select multiple</p>
          </div>
        </div>
        <div class="grid grid-cols-2 gap-4">
          <FormField label="RDN Attribute" v-model="templateForm.targetKeyAttribute" placeholder="uid" />
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Conflict Handling</label>
            <select v-model="templateForm.conflictHandling" class="input w-full">
              <option value="SKIP">Skip existing</option>
              <option value="OVERWRITE">Overwrite existing</option>
              <option value="PROMPT">Prompt (treat as skip)</option>
            </select>
          </div>
        </div>

        <div>
          <div class="flex items-center justify-between mb-2">
            <label class="text-sm font-medium text-gray-700">Column Mappings</label>
            <span v-if="loadingOcAttrs" class="text-xs text-gray-400">Loading attributes…</span>
          </div>
          <div v-if="templateForm.entries.length === 0 && !loadingOcAttrs" class="text-sm text-gray-400 text-center py-3">
            Select an object class to populate attribute mappings.
          </div>
          <div v-else class="space-y-2 max-h-96 overflow-y-scroll pr-2">
            <div v-for="(e, i) in templateForm.entries" :key="i" class="flex gap-2 items-center">
              <input v-model="e.csvColumn" placeholder="CSV column" class="input flex-1 text-xs" :class="{ 'border-red-300': e._required && !e.csvColumn }" />
              <span class="text-gray-400">→</span>
              <input :value="e.ldapAttribute" disabled class="input flex-1 text-xs bg-gray-50 text-gray-500" />
              <div class="w-16 flex-shrink-0 flex justify-start">
                <span v-if="e._required" class="text-red-500 text-xs font-medium">required</span>
                <button v-else type="button" @click="removeTemplateEntry(i)" class="text-red-400 hover:text-red-600 text-lg leading-none">&times;</button>
              </div>
            </div>
          </div>
        </div>

        <div class="flex justify-end gap-2 pt-2">
          <button type="button" @click="showTemplateModal = false" class="btn-secondary">Cancel</button>
          <button type="submit" :disabled="templateSaving || !canSaveTemplate" class="btn-primary">{{ templateSaving ? 'Saving…' : 'Save' }}</button>
        </div>
      </form>
    </AppModal>

    <ConfirmDialog
      v-if="deleteTemplateTarget"
      :message="`Delete template '${deleteTemplateTarget.name}'?`"
      @confirm="doDeleteTemplate"
      @cancel="deleteTemplateTarget = null"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import {
  importCsv, exportCsv, previewCsv,
  listCsvTemplates, createCsvTemplate, updateCsvTemplate, deleteCsvTemplate,
} from '@/api/csvTemplates'
import { listObjectClasses, getObjectClassesBulk } from '@/api/schema'
import { downloadBlob } from '@/composables/useApi'
import FormField from '@/components/FormField.vue'
import DnPicker from '@/components/DnPicker.vue'
import AppModal from '@/components/AppModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const route = useRoute()
const notif = useNotificationStore()
const dirId = route.params.dirId

const importing    = ref(false)
const previewing   = ref(false)
const exporting    = ref(false)
const importFile   = ref(null)
const importResult = ref(null)
const previewResult = ref(null)

const importForm = ref({ parentDn: '' })
const exportForm = ref({ filter: '', baseDn: '', attributes: 'cn,mail,uid' })

// ── Templates ─────────────────────────────────────────────────────────────────

const templatesLoading    = ref(false)
const templates           = ref([])
const selectedTemplateId  = ref('')
const showTemplateModal   = ref(false)
const editTemplate        = ref(null)
const templateSaving      = ref(false)
const deleteTemplateTarget = ref(null)
const templateForm = ref({
  name: '', objectClasses: [], targetKeyAttribute: 'uid', conflictHandling: 'SKIP', entries: []
})

// ObjectClass picker state
const objectClasses   = ref([])
const loadingOcAttrs  = ref(false)

const selectedTemplate = computed(() => {
  if (!selectedTemplateId.value) return null
  return templates.value.find(t => t.id === selectedTemplateId.value) || null
})

const canImport = computed(() => {
  return selectedTemplateId.value && importFile.value && importForm.value.parentDn
})

const canSaveTemplate = computed(() => {
  const f = templateForm.value
  if (!f.name || f.objectClasses.length === 0) return false
  return f.entries.filter(e => e._required).every(e => e.csvColumn && e.csvColumn.trim())
})

function conflictLabel(val) {
  const map = { SKIP: 'Skip existing', OVERWRITE: 'Overwrite existing', PROMPT: 'Prompt (treat as skip)' }
  return map[val] || val
}

function formatAttrs(attrs) {
  if (!attrs) return ''
  return Object.entries(attrs).map(([k, v]) => `${k}=${v}`).join(', ')
}

async function loadTemplates() {
  templatesLoading.value = true
  try {
    const { data } = await listCsvTemplates(dirId)
    templates.value = data
  } catch (e) { console.warn('Failed to load CSV templates:', e) }
  finally { templatesLoading.value = false }
}

async function loadObjectClasses() {
  try {
    const { data } = await listObjectClasses(dirId)
    objectClasses.value = data
  } catch (e) { console.warn('Failed to load objectClasses:', e) }
}

onMounted(() => {
  loadTemplates()
  loadObjectClasses()
})

function onTemplateSelected() {
  previewResult.value = null
  importResult.value = null
}

function openCreateTemplate() {
  editTemplate.value = null
  templateForm.value = {
    name: '', objectClasses: [], targetKeyAttribute: 'uid', conflictHandling: 'SKIP', entries: []
  }
  showTemplateModal.value = true
}

function openEditTemplate(t) {
  editTemplate.value = t
  templateForm.value = {
    name: t.name,
    objectClasses: t.objectClass ? t.objectClass.split(',') : [],
    targetKeyAttribute: t.targetKeyAttribute,
    conflictHandling: t.conflictHandling,
    entries: (t.entries ?? []).map(e => ({ ...e, _required: false })),
  }
  showTemplateModal.value = true
}

async function onObjectClassChange() {
  const ocs = templateForm.value.objectClasses
  if (ocs.length === 0) {
    templateForm.value.entries = []
    return
  }
  loadingOcAttrs.value = true
  try {
    const { data } = await getObjectClassesBulk(dirId, ocs)
    const entries = []
    for (const attr of (data.required || [])) {
      if (attr.toLowerCase() === 'objectclass') continue
      entries.push({ csvColumn: '', ldapAttribute: attr, ignored: false, _required: true })
    }
    for (const attr of (data.optional || [])) {
      if (attr.toLowerCase() === 'objectclass') continue
      entries.push({ csvColumn: '', ldapAttribute: attr, ignored: false, _required: false })
    }
    templateForm.value.entries = entries
  } catch (e) {
    notif.error('Failed to load objectClass attributes: ' + (e.response?.data?.detail || e.message))
  } finally {
    loadingOcAttrs.value = false
  }
}

function removeTemplateEntry(i) { templateForm.value.entries.splice(i, 1) }

async function saveTemplate() {
  templateSaving.value = true
  try {
    const payload = {
      name: templateForm.value.name,
      objectClass: templateForm.value.objectClasses.join(','),
      targetKeyAttribute: templateForm.value.targetKeyAttribute,
      conflictHandling: templateForm.value.conflictHandling,
      entries: templateForm.value.entries
        .filter(e => e.csvColumn && e.csvColumn.trim())
        .map(e => ({ csvColumn: e.csvColumn, ldapAttribute: e.ldapAttribute, ignored: false })),
    }
    if (editTemplate.value) {
      await updateCsvTemplate(dirId, editTemplate.value.id, payload)
      notif.success('Template updated')
    } else {
      await createCsvTemplate(dirId, payload)
      notif.success('Template created')
    }
    showTemplateModal.value = false
    await loadTemplates()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    templateSaving.value = false
  }
}

function confirmDeleteTemplate(t) {
  if (!t) return
  deleteTemplateTarget.value = t
}

async function doDeleteTemplate() {
  const target = deleteTemplateTarget.value
  deleteTemplateTarget.value = null
  try {
    await deleteCsvTemplate(dirId, target.id)
    notif.success('Template deleted')
    if (selectedTemplateId.value === target.id) {
      selectedTemplateId.value = ''
    }
    await loadTemplates()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  }
}

// ── Import ────────────────────────────────────────────────────────────────────

function onFileChange(e) {
  importFile.value = e.target.files[0] || null
  previewResult.value = null
  importResult.value = null
}

function buildImportRequest() {
  const t = selectedTemplate.value
  return {
    templateId: t.id,
    parentDn: importForm.value.parentDn,
    targetKeyAttribute: t.targetKeyAttribute,
    conflictHandling: t.conflictHandling,
    columnMappings: [],
  }
}

async function doPreview() {
  if (!canImport.value) return
  previewing.value = true
  previewResult.value = null
  importResult.value = null
  try {
    const { data } = await previewCsv(dirId, importFile.value, buildImportRequest())
    previewResult.value = data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    previewing.value = false
  }
}

async function doConfirmImport() {
  if (!canImport.value) return
  importing.value = true
  importResult.value = null
  try {
    const { data } = await importCsv(dirId, importFile.value, buildImportRequest())
    importResult.value = data
    previewResult.value = null
    notif.success(`Import done: ${data.created} created, ${data.errors} errors`)
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    importing.value = false
  }
}

// ── Export ────────────────────────────────────────────────────────────────────

async function doExport() {
  exporting.value = true
  try {
    const params = {
      filter:     exportForm.value.filter     || undefined,
      baseDn:     exportForm.value.baseDn     || undefined,
      attributes: exportForm.value.attributes || undefined,
    }
    const { data } = await exportCsv(dirId, params)
    downloadBlob(data, 'users.csv')
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary    { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
.btn-secondary  { @apply px-4 py-2 bg-white border border-gray-300 rounded-lg text-sm font-medium hover:bg-gray-50; }
.btn-danger     { @apply px-4 py-2 bg-red-600 text-white rounded-lg text-sm font-medium hover:bg-red-700 disabled:opacity-30; }
.input          { @apply border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
</style>
