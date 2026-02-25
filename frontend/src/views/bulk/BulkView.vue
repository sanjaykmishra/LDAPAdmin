<template>
  <div class="p-6 max-w-3xl">
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Bulk Import / Export</h1>

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
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import { importCsv, exportCsv } from '@/api/csvTemplates'
import { downloadBlob } from '@/composables/useApi'
import FormField from '@/components/FormField.vue'

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
.btn-primary { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
</style>
