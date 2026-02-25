<template>
  <div class="p-6 max-w-3xl">
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Bulk Import / Export</h1>

    <!-- CSV Templates section -->
    <section class="bg-white border border-gray-200 rounded-xl p-6 mb-6">
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-lg font-semibold">Saved CSV Templates</h2>
        <button @click="openCreateTemplate" class="btn-sm-primary">+ New Template</button>
      </div>

      <div v-if="templatesLoading" class="text-sm text-gray-400 text-center py-4">Loading…</div>
      <div v-else-if="templates.length === 0" class="text-sm text-gray-400 text-center py-4">
        No templates yet. Save a column mapping to reuse it.
      </div>
      <table v-else class="w-full text-sm">
        <thead class="bg-gray-50 border-b border-gray-100">
          <tr>
            <th class="px-3 py-2 text-left font-medium text-gray-500">Name</th>
            <th class="px-3 py-2 text-left font-medium text-gray-500">Key Attr</th>
            <th class="px-3 py-2 text-left font-medium text-gray-500">Conflict</th>
            <th class="px-3 py-2 text-left font-medium text-gray-500">Columns</th>
            <th class="px-3 py-2"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-50">
          <tr v-for="t in templates" :key="t.id" class="hover:bg-gray-50">
            <td class="px-3 py-2 font-medium text-gray-900">{{ t.name }}</td>
            <td class="px-3 py-2 text-gray-600 font-mono text-xs">{{ t.targetKeyAttribute }}</td>
            <td class="px-3 py-2 text-gray-600 text-xs">{{ t.conflictHandling }}</td>
            <td class="px-3 py-2 text-gray-600">{{ t.entries?.length ?? 0 }}</td>
            <td class="px-3 py-2 text-right">
              <button @click="loadTemplate(t)" class="text-green-600 hover:text-green-800 text-xs font-medium mr-2">Use</button>
              <button @click="openEditTemplate(t)" class="text-blue-600 hover:text-blue-800 text-xs font-medium mr-2">Edit</button>
              <button @click="confirmDeleteTemplate(t)" class="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <!-- Import section -->
    <section class="bg-white border border-gray-200 rounded-xl p-6 mb-6">
      <h2 class="text-lg font-semibold mb-4">Import Users from CSV</h2>
      <div class="space-y-3">
        <FormField label="Parent DN" v-model="importForm.parentDn" placeholder="ou=people,dc=example,dc=com" required />
        <FormField label="Key Attribute (RDN)" v-model="importForm.targetKeyAttribute" placeholder="uid" />
        <FormField label="Conflict Handling" type="select" v-model="importForm.conflictHandling" :options="conflictOptions" />
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">CSV File</label>
          <input type="file" accept=".csv,text/csv" @change="onFileChange"
            class="block w-full text-sm text-gray-500 file:mr-3 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-medium file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100" />
        </div>
        <button @click="doImport" :disabled="!importFile || importing" class="btn-primary">
          {{ importing ? 'Importing…' : 'Import' }}
        </button>
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
        <FormField label="Base DN (optional)" v-model="exportForm.baseDn" />
        <FormField label="Attributes (comma-separated)" v-model="exportForm.attributes" placeholder="cn,mail,uid,sn" />
        <button @click="doExport" :disabled="exporting" class="btn-primary">
          {{ exporting ? 'Exporting…' : 'Download CSV' }}
        </button>
      </div>
    </section>

    <!-- Template create/edit modal -->
    <AppModal v-model="showTemplateModal" :title="editTemplate ? 'Edit Template' : 'New Template'" size="lg">
      <form @submit.prevent="saveTemplate" class="space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <FormField label="Template Name" v-model="templateForm.name" required />
          <FormField label="Key Attribute" v-model="templateForm.targetKeyAttribute" placeholder="uid" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Conflict Handling</label>
          <select v-model="templateForm.conflictHandling" class="input w-full">
            <option value="SKIP">Skip existing</option>
            <option value="OVERWRITE">Overwrite existing</option>
            <option value="PROMPT">Prompt (treat as skip)</option>
          </select>
        </div>

        <div>
          <div class="flex items-center justify-between mb-2">
            <label class="text-sm font-medium text-gray-700">Column Mappings</label>
            <button type="button" @click="addTemplateEntry" class="text-xs text-blue-600 hover:text-blue-800 font-medium">+ Add</button>
          </div>
          <div class="space-y-2 max-h-52 overflow-y-auto">
            <div v-for="(e, i) in templateForm.entries" :key="i" class="flex gap-2 items-center">
              <input v-model="e.csvColumn" placeholder="CSV column" class="input flex-1 text-xs" />
              <span class="text-gray-400">→</span>
              <input v-model="e.ldapAttribute" placeholder="LDAP attribute" class="input flex-1 text-xs" />
              <label class="flex items-center gap-1 text-xs text-gray-600 whitespace-nowrap">
                <input type="checkbox" v-model="e.ignored" class="rounded" /> Ignore
              </label>
              <button type="button" @click="removeTemplateEntry(i)" class="text-red-400 hover:text-red-600 text-lg leading-none">×</button>
            </div>
          </div>
        </div>

        <div class="flex justify-end gap-2 pt-2">
          <button type="button" @click="showTemplateModal = false" class="btn-secondary">Cancel</button>
          <button type="submit" :disabled="templateSaving" class="btn-primary">{{ templateSaving ? 'Saving…' : 'Save' }}</button>
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
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import {
  importCsv, exportCsv,
  listCsvTemplates, createCsvTemplate, updateCsvTemplate, deleteCsvTemplate,
} from '@/api/csvTemplates'
import { downloadBlob } from '@/composables/useApi'
import FormField from '@/components/FormField.vue'
import AppModal from '@/components/AppModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const route = useRoute()
const notif = useNotificationStore()
const dirId = route.params.dirId

const importing   = ref(false)
const exporting   = ref(false)
const importFile  = ref(null)
const importResult = ref(null)

const conflictOptions = [
  { value: 'SKIP',      label: 'Skip existing' },
  { value: 'OVERWRITE', label: 'Overwrite existing' },
  { value: 'PROMPT',    label: 'Prompt (treat as skip)' },
]

const importForm = ref({ parentDn: '', targetKeyAttribute: 'uid', conflictHandling: 'SKIP' })
const exportForm = ref({ filter: '', baseDn: '', attributes: 'cn,mail,uid' })

// ── Templates ─────────────────────────────────────────────────────────────────

const templatesLoading   = ref(false)
const templates          = ref([])
const showTemplateModal  = ref(false)
const editTemplate       = ref(null)
const templateSaving     = ref(false)
const deleteTemplateTarget = ref(null)
const templateForm = ref({ name: '', targetKeyAttribute: 'uid', conflictHandling: 'SKIP', entries: [] })

async function loadTemplates() {
  templatesLoading.value = true
  try {
    const { data } = await listCsvTemplates(dirId)
    templates.value = data
  } catch { /* silently ignore */ }
  finally { templatesLoading.value = false }
}

onMounted(loadTemplates)

function openCreateTemplate() {
  editTemplate.value = null
  templateForm.value = { name: '', targetKeyAttribute: 'uid', conflictHandling: 'SKIP', entries: [] }
  showTemplateModal.value = true
}

function openEditTemplate(t) {
  editTemplate.value = t
  templateForm.value = {
    name: t.name,
    targetKeyAttribute: t.targetKeyAttribute,
    conflictHandling: t.conflictHandling,
    entries: (t.entries ?? []).map(e => ({ ...e })),
  }
  showTemplateModal.value = true
}

function addTemplateEntry()      { templateForm.value.entries.push({ csvColumn: '', ldapAttribute: '', ignored: false }) }
function removeTemplateEntry(i)  { templateForm.value.entries.splice(i, 1) }

async function saveTemplate() {
  templateSaving.value = true
  try {
    if (editTemplate.value) {
      await updateCsvTemplate(dirId, editTemplate.value.id, templateForm.value)
      notif.success('Template updated')
    } else {
      await createCsvTemplate(dirId, templateForm.value)
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

function loadTemplate(t) {
  importForm.value.targetKeyAttribute = t.targetKeyAttribute ?? 'uid'
  importForm.value.conflictHandling   = t.conflictHandling   ?? 'SKIP'
  notif.success(`Loaded template '${t.name}'`)
}

function confirmDeleteTemplate(t) { deleteTemplateTarget.value = t }

async function doDeleteTemplate() {
  try {
    await deleteCsvTemplate(dirId, deleteTemplateTarget.value.id)
    notif.success('Template deleted')
    deleteTemplateTarget.value = null
    await loadTemplates()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
    deleteTemplateTarget.value = null
  }
}

// ── Import ────────────────────────────────────────────────────────────────────

function onFileChange(e) {
  importFile.value = e.target.files[0] || null
}

async function doImport() {
  if (!importFile.value) return
  importing.value = true
  importResult.value = null
  try {
    const { data } = await importCsv(dirId, importFile.value, {
      parentDn: importForm.value.parentDn,
      targetKeyAttribute: importForm.value.targetKeyAttribute,
      conflictHandling: importForm.value.conflictHandling,
      columnMappings: [],
    })
    importResult.value = data
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
.btn-sm-primary { @apply px-3 py-1.5 bg-blue-600 text-white rounded-lg text-xs font-medium hover:bg-blue-700; }
.input          { @apply border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
</style>
