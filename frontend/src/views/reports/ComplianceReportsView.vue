<template>
  <div class="p-6 max-w-5xl">
    <h1 class="text-2xl font-bold text-gray-900 mb-1">Compliance Reports</h1>
    <p class="text-sm text-gray-500 mb-6">Generate PDF compliance reports for audits and governance reviews.</p>

    <div class="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
      <!-- User Access Report -->
      <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-5 flex flex-col">
        <h2 class="text-base font-semibold text-gray-900 mb-1">User Access Report</h2>
        <p class="text-sm text-gray-500 mb-4 flex-1">
          Shows who has access to what — lists all group memberships in the directory.
        </p>
        <div class="mb-3">
          <label class="block text-xs font-medium text-gray-600 mb-1">Group DN filter (optional)</label>
          <input v-model="groupDnFilter" type="text" placeholder="e.g. cn=admins,ou=groups,dc=..."
                 class="w-full border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
        </div>
        <button @click="downloadUserAccess" :disabled="loading.userAccess"
                class="w-full bg-blue-600 text-white text-sm font-medium rounded-lg px-4 py-2 hover:bg-blue-700 disabled:opacity-50">
          {{ loading.userAccess ? 'Generating...' : 'Download PDF' }}
        </button>
      </div>

      <!-- Access Review Summary -->
      <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-5 flex flex-col">
        <h2 class="text-base font-semibold text-gray-900 mb-1">Access Review Summary</h2>
        <p class="text-sm text-gray-500 mb-4 flex-1">
          Summarizes decisions from an access review campaign — confirms, revokes, and pending items.
        </p>
        <div class="mb-3">
          <label class="block text-xs font-medium text-gray-600 mb-1">Campaign</label>
          <select v-model="selectedCampaignId"
                  class="w-full border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500">
            <option value="">— select campaign —</option>
            <option v-for="c in campaigns" :key="c.id" :value="c.id">{{ c.name }} ({{ c.status }})</option>
          </select>
        </div>
        <button @click="downloadReviewSummary" :disabled="!selectedCampaignId || loading.reviewSummary"
                class="w-full bg-blue-600 text-white text-sm font-medium rounded-lg px-4 py-2 hover:bg-blue-700 disabled:opacity-50">
          {{ loading.reviewSummary ? 'Generating...' : 'Download PDF' }}
        </button>
      </div>

      <!-- Privileged Account Inventory -->
      <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-5 flex flex-col"
           v-if="auth.isSuperadmin">
        <h2 class="text-base font-semibold text-gray-900 mb-1">Privileged Account Inventory</h2>
        <p class="text-sm text-gray-500 mb-4 flex-1">
          Lists all admin and superadmin accounts with their roles, profile access, and feature permissions.
        </p>
        <button @click="downloadPrivilegedAccounts" :disabled="loading.privileged"
                class="w-full mt-auto bg-blue-600 text-white text-sm font-medium rounded-lg px-4 py-2 hover:bg-blue-700 disabled:opacity-50">
          {{ loading.privileged ? 'Generating...' : 'Download PDF' }}
        </button>
      </div>
    </div>

    <!-- Error message -->
    <div v-if="error" class="mt-4 bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm">
      {{ error }}
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { listCampaigns } from '@/api/accessReviews'
import {
  downloadUserAccessReport,
  downloadAccessReviewSummary,
  downloadPrivilegedAccountInventory,
} from '@/api/complianceReports'

const route = useRoute()
const auth = useAuthStore()

const groupDnFilter = ref('')
const selectedCampaignId = ref('')
const campaigns = ref([])
const error = ref('')
const loading = ref({ userAccess: false, reviewSummary: false, privileged: false })

const dirId = () => route.params.dirId

onMounted(async () => {
  try {
    const { data } = await listCampaigns(dirId(), { size: 100 })
    campaigns.value = data.content || data
  } catch (e) {
    console.warn('Failed to load campaigns:', e)
  }
})

function triggerDownload(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

async function downloadUserAccess() {
  loading.value.userAccess = true
  error.value = ''
  try {
    const params = groupDnFilter.value ? { groupDn: groupDnFilter.value } : {}
    const { data } = await downloadUserAccessReport(dirId(), params)
    triggerDownload(data, 'user-access-report.pdf')
  } catch (e) {
    error.value = 'Failed to generate User Access Report: ' + (e.response?.data?.message || e.message)
  } finally {
    loading.value.userAccess = false
  }
}

async function downloadReviewSummary() {
  loading.value.reviewSummary = true
  error.value = ''
  try {
    const { data } = await downloadAccessReviewSummary(dirId(), selectedCampaignId.value)
    triggerDownload(data, 'access-review-summary.pdf')
  } catch (e) {
    error.value = 'Failed to generate Access Review Summary: ' + (e.response?.data?.message || e.message)
  } finally {
    loading.value.reviewSummary = false
  }
}

async function downloadPrivilegedAccounts() {
  loading.value.privileged = true
  error.value = ''
  try {
    const { data } = await downloadPrivilegedAccountInventory()
    triggerDownload(data, 'privileged-account-inventory.pdf')
  } catch (e) {
    error.value = 'Failed to generate Privileged Account Inventory: ' + (e.response?.data?.message || e.message)
  } finally {
    loading.value.privileged = false
  }
}
</script>
