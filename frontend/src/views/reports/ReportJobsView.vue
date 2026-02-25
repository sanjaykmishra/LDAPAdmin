<template>
  <div class="p-6 max-w-5xl">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Reports</h1>
      <button @click="openCreate" class="btn-primary">+ New Scheduled Job</button>
    </div>

    <!-- On-demand run -->
    <section class="bg-white border border-gray-200 rounded-xl p-6 mb-6">
      <h2 class="text-lg font-semibold mb-4">Run Report Now</h2>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Report Type</label>
          <select v-model="runForm.reportType" class="input w-full">
            <option v-for="t in reportTypes" :key="t.value" :value="t.value">{{ t.label }}</option>
          </select>
        </div>
        <div v-if="needsParam">
          <label class="block text-sm font-medium text-gray-700 mb-1">{{ paramLabel }}</label>
          <input v-model="runForm.paramValue" type="text" :placeholder="paramPlaceholder" class="input w-full" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Lookback Days</label>
          <input v-model.number="runForm.lookbackDays" type="number" min="1" class="input w-full" placeholder="30" />
        </div>
      </div>
      <button @click="doRun" :disabled="running" class="btn-primary mt-4">
        {{ running ? 'Running…' : 'Download CSV' }}
      </button>
    </section>

    <!-- Scheduled jobs list -->
    <section class="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <div class="px-6 py-4 border-b border-gray-100">
        <h2 class="text-lg font-semibold">Scheduled Jobs</h2>
      </div>

      <div v-if="loading" class="p-8 text-center text-gray-500 text-sm">Loading…</div>
      <div v-else-if="jobs.length === 0" class="p-8 text-center text-gray-400 text-sm">No scheduled jobs yet.</div>
      <table v-else class="w-full text-sm">
        <thead class="bg-gray-50 border-b border-gray-100">
          <tr>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Name</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Type</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Cron</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Delivery</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Last Run</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Enabled</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-50">
          <tr v-for="job in jobs" :key="job.id" class="hover:bg-gray-50">
            <td class="px-4 py-3 font-medium text-gray-900">{{ job.name }}</td>
            <td class="px-4 py-3 text-gray-600">{{ labelFor(job.reportType) }}</td>
            <td class="px-4 py-3 text-gray-600 font-mono text-xs">{{ job.cronExpression }}</td>
            <td class="px-4 py-3 text-gray-600">{{ job.deliveryMethod }}</td>
            <td class="px-4 py-3 text-gray-500 text-xs">
              <span v-if="job.lastRunAt">{{ fmtDate(job.lastRunAt) }}</span>
              <span v-else class="text-gray-300">—</span>
              <span v-if="job.lastRunStatus" :class="job.lastRunStatus === 'SUCCESS' ? 'text-green-600' : 'text-red-500'" class="ml-1">({{ job.lastRunStatus }})</span>
            </td>
            <td class="px-4 py-3">
              <button @click="toggleEnabled(job)" :class="job.enabled ? 'bg-green-500' : 'bg-gray-300'" class="relative inline-flex h-5 w-9 rounded-full transition-colors">
                <span :class="job.enabled ? 'translate-x-4' : 'translate-x-0'" class="inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform scale-75"></span>
              </button>
            </td>
            <td class="px-4 py-3">
              <div class="flex gap-2 justify-end">
                <button @click="openEdit(job)" class="text-blue-600 hover:text-blue-800 text-xs font-medium">Edit</button>
                <button @click="confirmDelete(job)" class="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <!-- Create / Edit modal -->
    <AppModal v-model="showModal" :title="editJob ? 'Edit Scheduled Job' : 'New Scheduled Job'" size="lg">
      <form @submit.prevent="saveJob" class="space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <FormField label="Name" v-model="form.name" required />
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Report Type</label>
            <select v-model="form.reportType" class="input w-full" required>
              <option v-for="t in reportTypes" :key="t.value" :value="t.value">{{ t.label }}</option>
            </select>
          </div>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <FormField label="Cron Expression" v-model="form.cronExpression" placeholder="0 8 * * 1" required />
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Delivery Method</label>
            <select v-model="form.deliveryMethod" class="input w-full">
              <option value="EMAIL">Email</option>
              <option value="S3">S3</option>
            </select>
          </div>
        </div>

        <!-- Email delivery -->
        <div v-if="form.deliveryMethod === 'EMAIL'" class="grid grid-cols-2 gap-4">
          <FormField label="Recipient Email" v-model="form.recipientEmail" placeholder="user@example.com" />
          <FormField label="Email Subject" v-model="form.emailSubject" placeholder="Scheduled report" />
        </div>

        <!-- S3 delivery -->
        <div v-if="form.deliveryMethod === 'S3'" class="grid grid-cols-2 gap-4">
          <FormField label="S3 Key Prefix" v-model="form.s3KeyPrefix" placeholder="reports/" />
        </div>

        <!-- Report params -->
        <div v-if="formNeedsParam" class="grid grid-cols-2 gap-4">
          <FormField :label="formParamLabel" v-model="form.paramValue" :placeholder="formParamPlaceholder" />
        </div>
        <FormField label="Lookback Days" v-model.number="form.lookbackDays" type="number" placeholder="30" />

        <div class="flex items-center gap-2">
          <input type="checkbox" id="enabled" v-model="form.enabled" class="rounded" />
          <label for="enabled" class="text-sm text-gray-700">Enabled</label>
        </div>

        <div class="flex justify-end gap-2 pt-2">
          <button type="button" @click="showModal = false" class="btn-secondary">Cancel</button>
          <button type="submit" :disabled="saving" class="btn-primary">{{ saving ? 'Saving…' : 'Save' }}</button>
        </div>
      </form>
    </AppModal>

    <!-- Delete confirm -->
    <ConfirmDialog v-if="deleteTarget" :message="`Delete job '${deleteTarget.name}'?`" @confirm="doDelete" @cancel="deleteTarget = null" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import {
  listReportJobs, createReportJob, updateReportJob,
  deleteReportJob, setReportJobEnabled, runReport,
} from '@/api/reports'
import { downloadBlob } from '@/composables/useApi'
import FormField from '@/components/FormField.vue'
import AppModal from '@/components/AppModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const route = useRoute()
const notif = useNotificationStore()
const dirId = route.params.dirId

const loading = ref(false)
const saving  = ref(false)
const running = ref(false)
const jobs    = ref([])

const showModal   = ref(false)
const editJob     = ref(null)
const deleteTarget = ref(null)

const reportTypes = [
  { value: 'USERS_IN_GROUP',       label: 'Users in Group',         param: 'groupDn',  paramLabel: 'Group DN',  paramPlaceholder: 'cn=admins,dc=example,dc=com' },
  { value: 'USERS_IN_BRANCH',      label: 'Users in Branch',        param: 'branchDn', paramLabel: 'Branch DN', paramPlaceholder: 'ou=people,dc=example,dc=com' },
  { value: 'USERS_WITH_NO_GROUP',  label: 'Users with No Group',    param: null },
  { value: 'RECENTLY_ADDED',       label: 'Recently Added',         param: null },
  { value: 'RECENTLY_MODIFIED',    label: 'Recently Modified',      param: null },
  { value: 'RECENTLY_DELETED',     label: 'Recently Deleted',       param: null },
  { value: 'DISABLED_ACCOUNTS',    label: 'Disabled Accounts',      param: null },
]

function labelFor(type) {
  return reportTypes.find(t => t.value === type)?.label ?? type
}

function fmtDate(iso) {
  return new Date(iso).toLocaleString()
}

// ── On-demand run ─────────────────────────────────────────────────────────────

const runForm = ref({ reportType: 'RECENTLY_ADDED', paramValue: '', lookbackDays: 30 })

const currentRunType = computed(() => reportTypes.find(t => t.value === runForm.value.reportType))
const needsParam     = computed(() => !!currentRunType.value?.param)
const paramLabel     = computed(() => currentRunType.value?.paramLabel ?? '')
const paramPlaceholder = computed(() => currentRunType.value?.paramPlaceholder ?? '')

async function doRun() {
  running.value = true
  try {
    const params = { lookbackDays: runForm.value.lookbackDays || 30 }
    if (currentRunType.value?.param) params[currentRunType.value.param] = runForm.value.paramValue
    const { data } = await runReport(dirId, { reportType: runForm.value.reportType, reportParams: params })
    downloadBlob(data, `${runForm.value.reportType.toLowerCase()}.csv`)
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    running.value = false
  }
}

// ── Load jobs ─────────────────────────────────────────────────────────────────

async function loadJobs() {
  loading.value = true
  try {
    const { data } = await listReportJobs(dirId, { size: 50 })
    jobs.value = data.content ?? data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    loading.value = false
  }
}

onMounted(loadJobs)

// ── Form ──────────────────────────────────────────────────────────────────────

const form = ref({})

function blankForm() {
  return {
    name: '', reportType: 'RECENTLY_ADDED', cronExpression: '0 8 * * 1',
    deliveryMethod: 'EMAIL', recipientEmail: '', emailSubject: '',
    s3KeyPrefix: '', paramValue: '', lookbackDays: 30, enabled: true,
  }
}

const currentFormType  = computed(() => reportTypes.find(t => t.value === form.value.reportType))
const formNeedsParam   = computed(() => !!currentFormType.value?.param)
const formParamLabel   = computed(() => currentFormType.value?.paramLabel ?? '')
const formParamPlaceholder = computed(() => currentFormType.value?.paramPlaceholder ?? '')

function buildPayload() {
  const params = { lookbackDays: form.value.lookbackDays || 30 }
  if (currentFormType.value?.param) params[currentFormType.value.param] = form.value.paramValue
  return {
    name: form.value.name,
    reportType: form.value.reportType,
    reportParams: params,
    cronExpression: form.value.cronExpression,
    deliveryMethod: form.value.deliveryMethod,
    recipientEmail: form.value.deliveryMethod === 'EMAIL' ? form.value.recipientEmail : null,
    emailSubject:   form.value.deliveryMethod === 'EMAIL' ? form.value.emailSubject   : null,
    s3KeyPrefix:    form.value.deliveryMethod === 'S3'    ? form.value.s3KeyPrefix    : null,
    enabled:        form.value.enabled,
    outputFormat:   'CSV',
  }
}

function openCreate() {
  editJob.value = null
  form.value = blankForm()
  showModal.value = true
}

function openEdit(job) {
  editJob.value = job
  const typeInfo = reportTypes.find(t => t.value === job.reportType)
  form.value = {
    name:          job.name,
    reportType:    job.reportType,
    cronExpression: job.cronExpression,
    deliveryMethod: job.deliveryMethod ?? 'EMAIL',
    recipientEmail: job.recipientEmail ?? '',
    emailSubject:   job.emailSubject ?? '',
    s3KeyPrefix:    job.s3KeyPrefix ?? '',
    paramValue:     typeInfo?.param ? (job.reportParams?.[typeInfo.param] ?? '') : '',
    lookbackDays:   job.reportParams?.lookbackDays ?? 30,
    enabled:        job.enabled,
  }
  showModal.value = true
}

async function saveJob() {
  saving.value = true
  try {
    const payload = buildPayload()
    if (editJob.value) {
      await updateReportJob(dirId, editJob.value.id, payload)
      notif.success('Job updated')
    } else {
      await createReportJob(dirId, payload)
      notif.success('Job created')
    }
    showModal.value = false
    await loadJobs()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    saving.value = false
  }
}

// ── Enable toggle ─────────────────────────────────────────────────────────────

async function toggleEnabled(job) {
  try {
    const { data } = await setReportJobEnabled(dirId, job.id, !job.enabled)
    job.enabled = data.enabled
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  }
}

// ── Delete ────────────────────────────────────────────────────────────────────

function confirmDelete(job) { deleteTarget.value = job }

async function doDelete() {
  try {
    await deleteReportJob(dirId, deleteTarget.value.id)
    notif.success('Job deleted')
    deleteTarget.value = null
    await loadJobs()
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
