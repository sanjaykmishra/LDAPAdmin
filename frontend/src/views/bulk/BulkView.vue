<template>
  <div class="p-6 max-w-3xl">
    <h1 class="text-2xl font-bold text-gray-900 mb-4">Bulk Import / Export</h1>

    <!-- Entity type selector -->
    <div class="flex gap-2 mb-4">
      <button @click="entityType = 'users'"
        :class="['px-4 py-1.5 text-sm rounded-lg border transition-colors',
          entityType === 'users' ? 'bg-blue-50 border-blue-300 text-blue-700 font-medium' : 'border-gray-200 text-gray-600 hover:bg-gray-50']">
        Users
      </button>
      <button @click="entityType = 'groups'"
        :class="['px-4 py-1.5 text-sm rounded-lg border transition-colors',
          entityType === 'groups' ? 'bg-blue-50 border-blue-300 text-blue-700 font-medium' : 'border-gray-200 text-gray-600 hover:bg-gray-50']">
        Groups
      </button>
    </div>

    <!-- Tabs -->
    <div class="flex border-b border-gray-200 mb-0">
      <button @click="activeTab = 'import'"
        class="px-5 py-2.5 text-sm font-medium -mb-px"
        :class="activeTab === 'import' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-gray-500 hover:text-gray-700'">
        Import
      </button>
      <button @click="activeTab = 'export'"
        class="px-5 py-2.5 text-sm font-medium -mb-px"
        :class="activeTab === 'export' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-gray-500 hover:text-gray-700'">
        Export
      </button>
    </div>

    <!-- Import tab — Users -->
    <section v-if="activeTab === 'import' && entityType === 'users'" class="bg-white border border-gray-200 border-t-0 rounded-b-xl p-6">
      <h2 class="text-lg font-semibold mb-3">Import Users from CSV</h2>
      <div class="space-y-2">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Parent DN <span class="text-red-500">*</span></label>
          <DnPicker v-model="importForm.parentDn" :directoryId="dirId" :superadmin="false" />
        </div>

        <!-- Template picker + actions dropdown -->
        <div class="flex gap-2 items-end">
          <div class="flex-1">
            <label class="block text-sm font-medium text-gray-700 mb-1">Import Template <span class="text-red-500">*</span></label>
            <select v-model="selectedTemplateId" class="input w-full" @change="onTemplateSelected">
              <option value="">— Select a template —</option>
              <option v-for="t in templates" :key="t.id" :value="t.id">{{ t.name }}</option>
            </select>
          </div>
          <div class="relative" ref="menuRef">
            <button @click="showTemplateMenu = !showTemplateMenu" class="btn-primary whitespace-nowrap flex items-center gap-1">
              Template <span class="text-xs">&#9660;</span>
            </button>
            <div v-if="showTemplateMenu" class="absolute right-0 mt-1 w-44 bg-white border border-gray-200 rounded-lg shadow-lg z-10 py-1">
              <button @click="menuAction('add')" class="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                Add Template
              </button>
              <button @click="menuAction('edit')" :disabled="!selectedTemplate"
                class="w-full text-left px-4 py-2 text-sm hover:bg-gray-50"
                :class="selectedTemplate ? 'text-gray-700' : 'text-gray-300 cursor-not-allowed'">
                Edit Template
              </button>
              <button @click="menuAction('delete')" :disabled="!selectedTemplate"
                class="w-full text-left px-4 py-2 text-sm hover:bg-red-50"
                :class="selectedTemplate ? 'text-red-600' : 'text-gray-300 cursor-not-allowed'">
                Delete Template
              </button>
            </div>
          </div>
        </div>

        <!-- Template-driven fields (disabled, populated from template) -->
        <div v-if="selectedTemplate" class="grid grid-cols-3 gap-2">
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
            <button @click="previewResult = null" class="btn-neutral">Cancel</button>
          </div>
        </div>
      </div>

      <!-- Import result -->
      <div v-if="importResult" class="mt-4 p-4 rounded-lg bg-gray-50 border border-gray-200 text-sm">
        <div class="grid grid-cols-4 gap-2 mb-3">
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

    <!-- Export tab — Users -->
    <section v-if="activeTab === 'export' && entityType === 'users'" class="bg-white border border-gray-200 border-t-0 rounded-b-xl p-6">
      <h2 class="text-lg font-semibold mb-3">Export Users to CSV</h2>
      <div class="space-y-2">
        <FormField label="LDAP Filter (optional)" v-model="exportForm.filter" placeholder="(objectClass=inetOrgPerson)" />
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Base DN (optional)</label>
          <DnPicker v-model="exportForm.baseDn" :directoryId="dirId" :superadmin="false" placeholder="dc=example,dc=com" />
        </div>
        <FormField label="Attributes (comma-separated)" v-model="exportForm.attributes" placeholder="cn,mail,uid,sn" />
        <button @click="doExport" :disabled="exporting" class="btn-primary">
          {{ exporting ? 'Exporting…' : 'Download CSV' }}
        </button>
      </div>
    </section>

    <!-- Import tab — Groups -->
    <section v-if="activeTab === 'import' && entityType === 'groups'" class="bg-white border border-gray-200 border-t-0 rounded-b-xl p-6">
      <h2 class="text-lg font-semibold mb-3">Import Groups from CSV</h2>
      <div class="space-y-2">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Parent DN <span class="text-red-500">*</span></label>
          <DnPicker v-model="groupImportForm.parentDn" :directoryId="dirId" :superadmin="false" />
        </div>
        <div class="grid grid-cols-2 gap-2">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Object Class</label>
            <select v-model="groupImportForm.objectClass" class="input w-full">
              <option value="groupOfNames">groupOfNames</option>
              <option value="groupOfUniqueNames">groupOfUniqueNames</option>
              <option value="posixGroup">posixGroup</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Member Attribute</label>
            <input :value="groupMemberAttr" disabled class="input w-full bg-gray-50 text-gray-500" />
          </div>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Conflict Handling</label>
          <select v-model="groupImportForm.conflictHandling" class="input w-full">
            <option value="SKIP">Skip existing</option>
            <option value="OVERWRITE">Overwrite existing</option>
          </select>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">CSV File <span class="text-red-500">*</span></label>
          <input type="file" accept=".csv,text/csv" @change="onGroupFileChange"
            class="block w-full text-sm text-gray-500 file:mr-3 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-medium file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100" />
        </div>
        <p class="text-xs text-gray-500">
          CSV columns: <code>cn</code> (required), <code>description</code>, <code>owner</code>, <code>members</code> (pipe-separated DNs).
          First row must be a header row.
        </p>
        <button @click="doGroupPreview" :disabled="!canGroupImport || groupPreviewing" class="btn-primary">
          {{ groupPreviewing ? 'Loading preview…' : 'Preview Import' }}
        </button>
      </div>

      <!-- Preview -->
      <div v-if="groupPreviewResult" class="mt-4">
        <div class="p-4 rounded-lg bg-blue-50 border border-blue-200 text-sm mb-3">
          <p class="font-medium text-blue-800 mb-2">Preview: {{ groupPreviewResult.totalRows }} groups to import</p>
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
                <tr v-for="row in groupPreviewResult.rows" :key="row.rowNumber">
                  <td class="px-2 py-1 text-gray-600">{{ row.rowNumber }}</td>
                  <td class="px-2 py-1 font-mono text-gray-800">{{ row.computedDn || '(missing cn)' }}</td>
                  <td class="px-2 py-1 text-gray-600">{{ formatAttrs(row.attributes) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="flex gap-2 mt-3">
            <button @click="doGroupConfirmImport" :disabled="groupImporting" class="btn-primary">
              {{ groupImporting ? 'Importing…' : 'Confirm Import' }}
            </button>
            <button @click="groupPreviewResult = null" class="btn-neutral">Cancel</button>
          </div>
        </div>
      </div>

      <!-- Import result -->
      <div v-if="groupImportResult" class="mt-4 p-4 rounded-lg bg-gray-50 border border-gray-200 text-sm">
        <div class="grid grid-cols-4 gap-2 mb-3">
          <div class="text-center"><p class="text-2xl font-bold text-green-600">{{ groupImportResult.created }}</p><p class="text-xs text-gray-500">Created</p></div>
          <div class="text-center"><p class="text-2xl font-bold text-blue-600">{{ groupImportResult.updated }}</p><p class="text-xs text-gray-500">Updated</p></div>
          <div class="text-center"><p class="text-2xl font-bold text-yellow-600">{{ groupImportResult.skipped }}</p><p class="text-xs text-gray-500">Skipped</p></div>
          <div class="text-center"><p class="text-2xl font-bold text-red-600">{{ groupImportResult.errors }}</p><p class="text-xs text-gray-500">Errors</p></div>
        </div>
        <ul v-if="groupImportResult.rows?.filter(r => r.status === 'ERROR').length" class="space-y-1">
          <li v-for="r in groupImportResult.rows.filter(r => r.status === 'ERROR')" :key="r.rowNumber" class="text-red-600 text-xs">
            Row {{ r.rowNumber }}: {{ r.message }}
          </li>
        </ul>
      </div>
    </section>

    <!-- Export tab — Groups -->
    <section v-if="activeTab === 'export' && entityType === 'groups'" class="bg-white border border-gray-200 border-t-0 rounded-b-xl p-6">
      <h2 class="text-lg font-semibold mb-3">Export Groups to CSV</h2>
      <div class="space-y-2">
        <FormField label="LDAP Filter (optional)" v-model="groupExportForm.filter" placeholder="(objectClass=groupOfNames)" />
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Base DN (optional)</label>
          <DnPicker v-model="groupExportForm.baseDn" :directoryId="dirId" :superadmin="false" placeholder="ou=groups,dc=example,dc=com" />
        </div>
        <FormField label="Attributes (comma-separated)" v-model="groupExportForm.attributes" placeholder="cn,description,owner" />
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Member Attribute</label>
          <select v-model="groupExportForm.memberAttribute" class="input w-full">
            <option value="member">member</option>
            <option value="uniqueMember">uniqueMember</option>
            <option value="memberUid">memberUid</option>
          </select>
        </div>
        <button @click="doGroupExport" :disabled="groupExporting" class="btn-primary">
          {{ groupExporting ? 'Exporting…' : 'Download CSV' }}
        </button>
      </div>
    </section>

    <!-- Template create/edit modal -->
    <AppModal v-model="showTemplateModal" :title="editTemplate ? 'Edit Template' : 'New Template'" size="xl">
      <form @submit.prevent="saveTemplate" class="space-y-2">
        <div class="grid grid-cols-2 gap-2 items-end">
          <div class="space-y-2">
            <FormField label="Template Name" v-model="templateForm.name" required />
            <FormField label="RDN Attribute" v-model="templateForm.targetKeyAttribute" placeholder="uid" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Object Class <span class="text-red-500">*</span></label>
            <div class="flex items-stretch gap-0">
              <!-- Selected list -->
              <div class="flex-1">
                <div class="text-xs text-gray-500 mb-1">Selected</div>
                <div class="border border-gray-300 rounded-l-lg h-36 overflow-y-auto">
                  <div v-for="oc in templateForm.objectClasses" :key="oc"
                    @click="selectedOcHighlight = oc"
                    class="px-2 py-1 text-sm cursor-pointer"
                    :class="selectedOcHighlight === oc ? 'bg-blue-100 text-blue-800' : 'hover:bg-gray-50'">
                    {{ oc }}
                  </div>
                  <p v-if="templateForm.objectClasses.length === 0" class="text-xs text-gray-400 text-center py-4">None</p>
                </div>
              </div>
              <!-- Add / Remove buttons -->
              <div class="flex flex-col items-center justify-center gap-1 px-2">
                <button type="button" @click="addObjectClass" :disabled="!availableOcHighlight"
                  class="w-8 h-8 flex items-center justify-center rounded border border-gray-300 text-sm hover:bg-gray-100 disabled:opacity-30 disabled:cursor-not-allowed">◀</button>
                <button type="button" @click="removeObjectClass" :disabled="!selectedOcHighlight"
                  class="w-8 h-8 flex items-center justify-center rounded border border-gray-300 text-sm hover:bg-gray-100 disabled:opacity-30 disabled:cursor-not-allowed">▶</button>
              </div>
              <!-- Available list -->
              <div class="flex-1">
                <div class="text-xs text-gray-500 mb-1">Available</div>
                <div class="border border-gray-300 rounded-r-lg h-36 overflow-y-auto">
                  <div v-for="oc in availableObjectClasses" :key="oc"
                    @click="availableOcHighlight = oc"
                    class="px-2 py-1 text-sm cursor-pointer"
                    :class="availableOcHighlight === oc ? 'bg-blue-100 text-blue-800' : 'hover:bg-gray-50'">
                    {{ oc }}
                  </div>
                  <p v-if="availableObjectClasses.length === 0" class="text-xs text-gray-400 text-center py-4">None</p>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="grid grid-cols-2 gap-2">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Conflict Handling</label>
            <select v-model="templateForm.conflictHandling" class="input w-full">
              <option value="SKIP">Skip existing</option>
              <option value="OVERWRITE">Overwrite existing</option>
              <option value="PROMPT">Prompt (treat as skip)</option>
            </select>
          </div>
          <div class="flex items-end pb-1">
            <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input type="checkbox" v-model="templateForm.skipHeaderRow" class="rounded text-blue-600" />
              CSV first row is header (skip on import)
            </label>
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
              <div class="w-8 flex-shrink-0 flex justify-center">
                <span v-if="e._required" class="text-red-500 text-sm font-bold">*</span>
                <button v-else type="button" @click="removeTemplateEntry(i)" class="text-red-400 hover:text-red-600 text-lg leading-none">&times;</button>
              </div>
            </div>
          </div>
        </div>

        <div class="flex justify-end gap-2 pt-2">
          <button type="button" @click="showTemplateModal = false" class="btn-neutral">Cancel</button>
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
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import {
  importCsv, exportCsv, previewCsv,
  listCsvTemplates, createCsvTemplate, updateCsvTemplate, deleteCsvTemplate,
  previewGroupCsv, importGroupCsv, exportGroupCsv,
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

const entityType   = ref('users')
const activeTab    = ref('import')
const importing    = ref(false)
const previewing   = ref(false)
const exporting    = ref(false)
const importFile   = ref(null)
const importResult = ref(null)
const previewResult = ref(null)

const importForm = ref({ parentDn: '' })
const exportForm = ref({ filter: '', baseDn: '', attributes: 'cn,mail,uid' })

// ── Group bulk state ─────────────────────────────────────────────────────────
const groupImporting    = ref(false)
const groupPreviewing   = ref(false)
const groupExporting    = ref(false)
const groupImportFile   = ref(null)
const groupImportResult = ref(null)
const groupPreviewResult = ref(null)
const groupImportForm = ref({
  parentDn: '',
  objectClass: 'groupOfNames',
  conflictHandling: 'SKIP',
})
const groupExportForm = ref({
  filter: '',
  baseDn: '',
  attributes: 'cn,description,owner',
  memberAttribute: 'member',
})

// ── Template actions dropdown ─────────────────────────────────────────────────

const showTemplateMenu = ref(false)
const menuRef = ref(null)

function onClickOutside(e) {
  if (menuRef.value && !menuRef.value.contains(e.target)) {
    showTemplateMenu.value = false
  }
}
onMounted(() => document.addEventListener('click', onClickOutside))
onBeforeUnmount(() => document.removeEventListener('click', onClickOutside))

function menuAction(action) {
  showTemplateMenu.value = false
  if (action === 'add') openCreateTemplate()
  else if (action === 'edit' && selectedTemplate.value) openEditTemplate(selectedTemplate.value)
  else if (action === 'delete' && selectedTemplate.value) confirmDeleteTemplate(selectedTemplate.value)
}

// ── Templates ─────────────────────────────────────────────────────────────────

const templatesLoading    = ref(false)
const templates           = ref([])
const selectedTemplateId  = ref('')
const showTemplateModal   = ref(false)
const editTemplate        = ref(null)
const templateSaving      = ref(false)
const deleteTemplateTarget = ref(null)
const templateForm = ref({
  name: '', objectClasses: [], targetKeyAttribute: 'uid', conflictHandling: 'SKIP',
  skipHeaderRow: true, entries: []
})

// ObjectClass picker state
const objectClasses       = ref([])
const loadingOcAttrs      = ref(false)
const selectedOcHighlight = ref(null)
const availableOcHighlight = ref(null)

const availableObjectClasses = computed(() =>
  objectClasses.value.filter(oc => !templateForm.value.objectClasses.includes(oc))
)

function addObjectClass() {
  if (!availableOcHighlight.value) return
  templateForm.value.objectClasses.push(availableOcHighlight.value)
  availableOcHighlight.value = null
  onObjectClassChange()
}

function removeObjectClass() {
  if (!selectedOcHighlight.value) return
  const idx = templateForm.value.objectClasses.indexOf(selectedOcHighlight.value)
  if (idx >= 0) templateForm.value.objectClasses.splice(idx, 1)
  selectedOcHighlight.value = null
  onObjectClassChange()
}

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
    objectClasses.value = data.map(oc => typeof oc === 'string' ? oc : oc.name)
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
  selectedOcHighlight.value = null
  availableOcHighlight.value = null
  templateForm.value = {
    name: '', objectClasses: [], targetKeyAttribute: 'uid', conflictHandling: 'SKIP',
    skipHeaderRow: true, entries: []
  }
  showTemplateModal.value = true
}

function openEditTemplate(t) {
  editTemplate.value = t
  selectedOcHighlight.value = null
  availableOcHighlight.value = null
  templateForm.value = {
    name: t.name,
    objectClasses: t.objectClass ? t.objectClass.split(',') : [],
    targetKeyAttribute: t.targetKeyAttribute,
    conflictHandling: t.conflictHandling,
    skipHeaderRow: t.skipHeaderRow !== false,
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
    // Preserve existing csvColumn values where the ldapAttribute still exists
    const existingMap = {}
    for (const e of templateForm.value.entries) {
      if (e.csvColumn) existingMap[e.ldapAttribute] = e.csvColumn
    }
    const entries = []
    for (const attr of (data.required || [])) {
      if (attr.toLowerCase() === 'objectclass') continue
      entries.push({ csvColumn: existingMap[attr] || '', ldapAttribute: attr, ignored: false, _required: true })
    }
    for (const attr of (data.optional || [])) {
      if (attr.toLowerCase() === 'objectclass') continue
      entries.push({ csvColumn: existingMap[attr] || '', ldapAttribute: attr, ignored: false, _required: false })
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
      skipHeaderRow: templateForm.value.skipHeaderRow,
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
    skipHeaderRow: t.skipHeaderRow !== false,
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
    const resp = await importCsv(dirId, importFile.value, buildImportRequest())
    const data = resp.data
    if (resp.status === 202 || data.approvalId) {
      // Import submitted for approval
      previewResult.value = null
      notif.success('Bulk import submitted for approval. An approver will review your request.')
    } else {
      importResult.value = data
      previewResult.value = null
      notif.success(`Import done: ${data.created} created, ${data.errors} errors`)
    }
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

// ── Group import / export ────────────────────────────────────────────────────

const MEMBER_ATTR_MAP = {
  groupOfNames: 'member',
  groupOfUniqueNames: 'uniqueMember',
  posixGroup: 'memberUid',
}

const groupMemberAttr = computed(() =>
  MEMBER_ATTR_MAP[groupImportForm.value.objectClass] || 'member'
)

const canGroupImport = computed(() =>
  groupImportFile.value && groupImportForm.value.parentDn
)

function onGroupFileChange(e) {
  groupImportFile.value = e.target.files[0] || null
  groupPreviewResult.value = null
  groupImportResult.value = null
}

function buildGroupImportRequest() {
  return {
    parentDn: groupImportForm.value.parentDn,
    conflictHandling: groupImportForm.value.conflictHandling,
    skipHeaderRow: true,
    columnMappings: [],
  }
}

async function doGroupPreview() {
  if (!canGroupImport.value) return
  groupPreviewing.value = true
  groupPreviewResult.value = null
  groupImportResult.value = null
  try {
    const { data } = await previewGroupCsv(dirId, groupImportFile.value, buildGroupImportRequest())
    groupPreviewResult.value = data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    groupPreviewing.value = false
  }
}

async function doGroupConfirmImport() {
  if (!canGroupImport.value) return
  groupImporting.value = true
  groupImportResult.value = null
  try {
    const req = buildGroupImportRequest()
    const resp = await importGroupCsv(
      dirId, groupImportFile.value, req,
      groupMemberAttr.value, groupImportForm.value.objectClass
    )
    groupImportResult.value = resp.data
    groupPreviewResult.value = null
    notif.success(`Import done: ${resp.data.created} created, ${resp.data.errors} errors`)
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    groupImporting.value = false
  }
}

async function doGroupExport() {
  groupExporting.value = true
  try {
    const params = {
      filter:          groupExportForm.value.filter     || undefined,
      baseDn:          groupExportForm.value.baseDn     || undefined,
      attributes:      groupExportForm.value.attributes || undefined,
      memberAttribute: groupExportForm.value.memberAttribute,
    }
    const { data } = await exportGroupCsv(dirId, params)
    downloadBlob(data, 'groups.csv')
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    groupExporting.value = false
  }
}
</script>

<style scoped>
@reference "tailwindcss";
</style>
