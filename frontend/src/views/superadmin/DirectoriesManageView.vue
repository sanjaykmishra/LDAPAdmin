<template>
  <div class="p-6 max-w-5xl">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">LDAP Directories</h1>
        <p class="text-sm text-gray-500 mt-1">Manage LDAP directory connections</p>
      </div>
      <button @click="openCreate" class="btn-primary">+ New Directory</button>
    </div>

    <div class="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <div v-if="loading" class="p-8 text-center text-gray-500 text-sm">Loading…</div>
      <div v-else-if="dirs.length === 0" class="p-8 text-center text-gray-400 text-sm">No directories configured.</div>
      <table v-else class="w-full text-sm">
        <thead class="bg-gray-50 border-b border-gray-100">
          <tr>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Name</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Host</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Port</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">SSL</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Base DN</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Enabled</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-50">
          <tr v-for="d in dirs" :key="d.id" class="hover:bg-gray-50">
            <td class="px-4 py-3 font-medium text-gray-900">{{ d.displayName }}</td>
            <td class="px-4 py-3 text-gray-600 font-mono text-xs">{{ d.host }}</td>
            <td class="px-4 py-3 text-gray-600">{{ d.port }}</td>
            <td class="px-4 py-3 text-gray-600 text-xs">{{ d.sslMode }}</td>
            <td class="px-4 py-3 text-gray-600 font-mono text-xs">{{ d.baseDn }}</td>
            <td class="px-4 py-3">
              <span :class="d.enabled ? 'text-green-600' : 'text-gray-400'" class="text-xs font-medium">
                {{ d.enabled ? 'Yes' : 'No' }}
              </span>
            </td>
            <td class="px-4 py-3 text-right whitespace-nowrap">
              <button v-if="d.enabled" @click="$router.push(`/superadmin/directories/${d.id}/discover`)" class="text-green-600 hover:text-green-800 text-xs font-medium mr-2">Discover</button>
              <button @click="doEvictPool(d)" class="text-amber-600 hover:text-amber-800 text-xs font-medium mr-2">Evict Pool</button>
              <button @click="openEdit(d)" class="text-blue-600 hover:text-blue-800 text-xs font-medium mr-2">Edit</button>
              <button @click="confirmDelete(d)" class="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Create/Edit modal -->
    <AppModal v-model="showModal" :title="editing ? 'Edit Directory' : 'New Directory'" size="lg">
      <form @submit.prevent="save" class="space-y-2">
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Directory Type</label>
            <select v-model="form.directoryType" @change="applyPreset" class="input w-full">
              <option value="GENERIC">Generic LDAP</option>
              <option value="ACTIVE_DIRECTORY">Active Directory</option>
              <option value="OPENLDAP">OpenLDAP</option>
            </select>
          </div>
          <FormField label="Display Name" v-model="form.displayName" required />
          <FormField label="Port" v-model.number="form.port" type="number" placeholder="389" />
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">SSL Mode</label>
            <select v-model="form.sslMode" class="input w-full">
              <option value="NONE">None</option>
              <option value="LDAPS">LDAPS</option>
              <option value="STARTTLS">STARTTLS</option>
            </select>
          </div>
          <FormField label="Bind DN" v-model="form.bindDn" required placeholder="cn=admin,dc=example,dc=com" />
          <FormField label="Bind Password" v-model="form.bindPassword" type="password" :placeholder="editing ? 'Leave blank to keep' : ''" />
          <div class="col-span-2">
            <FormField label="Base DN" v-model="form.baseDn" required placeholder="dc=example,dc=com" />
          </div>
        </div>
        <div class="flex items-center gap-3">
          <label class="flex items-center gap-2 text-sm text-gray-700">
            <input type="checkbox" v-model="form.trustAllCerts" class="rounded" />
            Trust all certificates
          </label>
          <label class="flex items-center gap-2 text-sm text-gray-700">
            <input type="checkbox" v-model="form.enabled" class="rounded" />
            Enabled
          </label>
        </div>

        <!-- Self-service settings -->
        <details class="border border-gray-200 rounded-lg">
          <summary class="px-4 py-2 text-sm font-medium text-gray-700 cursor-pointer">Self-service portal</summary>
          <div class="px-4 pb-4 pt-2 space-y-3">
            <label class="flex items-center gap-2 text-sm text-gray-700">
              <input type="checkbox" v-model="form.selfServiceEnabled" class="rounded" />
              Enable self-service portal for this directory
            </label>
            <div v-if="form.selfServiceEnabled">
              <FormField label="Login Attribute" v-model="form.selfServiceLoginAttribute"
                placeholder="uid (or sAMAccountName for AD)" />
              <p class="text-xs text-gray-400 mt-1">The LDAP attribute used to identify users during self-service login (e.g. uid, sAMAccountName, mail)</p>
            </div>
          </div>
        </details>

        <!-- Connection pool settings -->
        <details class="border border-gray-200 rounded-lg">
          <summary class="px-4 py-2 text-sm font-medium text-gray-700 cursor-pointer">Advanced settings</summary>
          <div class="px-4 pb-4 pt-2 grid grid-cols-2 gap-3">
            <FormField label="Paging Size" v-model.number="form.pagingSize" type="number" />
            <FormField label="Pool Min Size" v-model.number="form.poolMinSize" type="number" />
            <FormField label="Pool Max Size" v-model.number="form.poolMaxSize" type="number" />
            <FormField label="Connect Timeout (s)" v-model.number="form.poolConnectTimeoutSeconds" type="number" />
            <FormField label="Response Timeout (s)" v-model.number="form.poolResponseTimeoutSeconds" type="number" />
            <FormField label="Secondary Host" v-model="form.secondaryHost" placeholder="Failover DC (optional)" />
            <FormField label="Secondary Port" v-model.number="form.secondaryPort" type="number" placeholder="Same as primary" />
            <FormField label="Global Catalog Port" v-model.number="form.globalCatalogPort" type="number" placeholder="3268 (AD only)" />
            <div></div>
            <FormField label="Enable/Disable Attribute" v-model="form.enableDisableAttribute" placeholder="e.g. nsAccountLock" />
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Enable/Disable Value Type</label>
              <select v-model="form.enableDisableValueType" class="input w-full">
                <option value="BOOLEAN">BOOLEAN</option>
                <option value="TIMESTAMP">TIMESTAMP</option>
              </select>
            </div>
            <FormField label="Enable Value" v-model="form.enableValue" placeholder="e.g. false" />
            <FormField label="Disable Value" v-model="form.disableValue" placeholder="e.g. true" />
          </div>
        </details>

        <!-- Test connection result -->
        <div v-if="testResult" :class="testResult.success ? 'bg-green-50 border-green-200 text-green-800' : 'bg-red-50 border-red-200 text-red-700'" class="border rounded-lg px-3 py-2 text-sm">
          {{ testResult.message }}
        </div>
        <div class="flex justify-between items-center pt-2">
          <button type="button" @click="doTest" :disabled="testLoading" class="btn-secondary text-sm">
            {{ testLoading ? 'Testing…' : 'Test Connection' }}
          </button>
          <div class="flex gap-2">
            <button type="button" @click="showModal = false" class="btn-neutral">Cancel</button>
            <button type="submit" :disabled="saving" class="btn-primary">{{ saving ? 'Saving…' : 'Save' }}</button>
          </div>
        </div>
      </form>
    </AppModal>

    <!-- Delete confirm -->
    <ConfirmDialog
      v-if="deleteTarget"
      :message="`Delete directory '${deleteTarget.displayName}'? All associated profiles and configuration will be removed.`"
      @confirm="doDelete"
      @cancel="deleteTarget = null"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useNotificationStore } from '@/stores/notifications'
import { listDirectories, createDirectory, updateDirectory, deleteDirectory, testDirectory, evictPool } from '@/api/directories'
import FormField from '@/components/FormField.vue'
import AppModal from '@/components/AppModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const notif = useNotificationStore()

const loading      = ref(false)
const saving       = ref(false)
const dirs         = ref([])
const showModal    = ref(false)
const editing      = ref(null)
const deleteTarget = ref(null)
const testLoading  = ref(false)
const testResult   = ref(null)

const form = ref(emptyForm())

function emptyForm() {
  return {
    directoryType: 'GENERIC',
    displayName: '', host: '', port: 389, sslMode: 'NONE',
    trustAllCerts: false, bindDn: '', bindPassword: '', baseDn: '',
    pagingSize: 500, poolMinSize: 2, poolMaxSize: 10,
    poolConnectTimeoutSeconds: 10, poolResponseTimeoutSeconds: 30,
    enableDisableAttribute: '', enableDisableValueType: 'BOOLEAN',
    enableValue: '', disableValue: '', enabled: true,
    selfServiceEnabled: false, selfServiceLoginAttribute: 'uid',
    secondaryHost: '', secondaryPort: null, globalCatalogPort: null,
  }
}

function applyPreset() {
  const t = form.value.directoryType
  if (t === 'ACTIVE_DIRECTORY') {
    if (!form.value.port || form.value.port === 389) form.value.port = 636
    if (form.value.sslMode === 'NONE') form.value.sslMode = 'LDAPS'
    if (!form.value.selfServiceLoginAttribute || form.value.selfServiceLoginAttribute === 'uid')
      form.value.selfServiceLoginAttribute = 'sAMAccountName'
    if (!form.value.enableDisableAttribute) {
      form.value.enableDisableAttribute = 'userAccountControl'
      form.value.enableDisableValueType = 'BOOLEAN'
      form.value.enableValue = '512'
      form.value.disableValue = '514'
    }
  } else if (t === 'OPENLDAP') {
    if (!form.value.port || form.value.port === 636) form.value.port = 389
    if (!form.value.selfServiceLoginAttribute || form.value.selfServiceLoginAttribute === 'sAMAccountName')
      form.value.selfServiceLoginAttribute = 'uid'
  }
}

async function load() {
  loading.value = true
  try {
    const { data } = await listDirectories()
    dirs.value = data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate() {
  editing.value = null
  form.value = emptyForm()
  testResult.value = null
  showModal.value = true
}

function openEdit(d) {
  editing.value = d.id
  form.value = {
    directoryType: d.directoryType || 'GENERIC',
    displayName: d.displayName, host: d.host, port: d.port, sslMode: d.sslMode,
    trustAllCerts: d.trustAllCerts, bindDn: d.bindDn, bindPassword: '', baseDn: d.baseDn,
    pagingSize: d.pagingSize, poolMinSize: d.poolMinSize, poolMaxSize: d.poolMaxSize,
    poolConnectTimeoutSeconds: d.poolConnectTimeoutSeconds,
    poolResponseTimeoutSeconds: d.poolResponseTimeoutSeconds,
    enableDisableAttribute: d.enableDisableAttribute || '',
    enableDisableValueType: d.enableDisableValueType || 'BOOLEAN',
    enableValue: d.enableValue || '', disableValue: d.disableValue || '',
    enabled: d.enabled,
    selfServiceEnabled: d.selfServiceEnabled || false,
    selfServiceLoginAttribute: d.selfServiceLoginAttribute || 'uid',
    secondaryHost: d.secondaryHost || '',
    secondaryPort: d.secondaryPort || null,
    globalCatalogPort: d.globalCatalogPort || null,
  }
  testResult.value = null
  showModal.value = true
}

async function save() {
  saving.value = true
  try {
    const payload = { ...form.value }
    if (editing.value && !payload.bindPassword) delete payload.bindPassword
    if (editing.value) {
      await updateDirectory(editing.value, payload)
      notif.success('Directory updated')
    } else {
      await createDirectory(payload)
      notif.success('Directory created')
    }
    showModal.value = false
    await load()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    saving.value = false
  }
}

function confirmDelete(d) { deleteTarget.value = d }

async function doDelete() {
  try {
    await deleteDirectory(deleteTarget.value.id)
    notif.success('Directory deleted')
    deleteTarget.value = null
    await load()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
    deleteTarget.value = null
  }
}

async function doTest() {
  testLoading.value = true
  testResult.value = null
  try {
    const { data } = await testDirectory(form.value)
    testResult.value = data
  } catch (e) {
    testResult.value = { success: false, message: e.response?.data?.detail || e.message }
  } finally {
    testLoading.value = false
  }
}

async function doEvictPool(d) {
  try {
    await evictPool(d.id)
    notif.success('Connection pool evicted')
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  }
}
</script>

<style scoped>
@reference "tailwindcss";
</style>
