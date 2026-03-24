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

    <!-- Evidence Package section -->
    <div class="mt-8 bg-white rounded-xl shadow-sm border border-gray-200 p-6">
      <h2 class="text-lg font-semibold text-gray-900 mb-1">Evidence Package</h2>
      <p class="text-sm text-gray-500 mb-5">
        Generate a comprehensive ZIP package containing all compliance artifacts: PDF reports,
        campaign decisions, SoD data, approval history, and user entitlements.
      </p>

      <div class="grid gap-6 md:grid-cols-2">
        <!-- Campaign selection -->
        <div>
          <label class="block text-xs font-semibold text-gray-600 uppercase tracking-wider mb-2">
            Include Campaigns
          </label>
          <div v-if="!campaigns.length" class="text-sm text-gray-400">No campaigns available.</div>
          <div v-else class="max-h-48 overflow-y-auto border border-gray-200 rounded-lg divide-y divide-gray-100">
            <label v-for="c in campaigns" :key="c.id"
                   class="flex items-center gap-3 px-3 py-2 hover:bg-gray-50 cursor-pointer">
              <input type="checkbox" :value="c.id" v-model="evidencePackageCampaignIds"
                     class="rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
              <div class="min-w-0">
                <span class="text-sm font-medium text-gray-900 block truncate">{{ c.name }}</span>
                <span class="text-xs text-gray-400">{{ c.status }}</span>
              </div>
            </label>
          </div>
        </div>

        <!-- Options -->
        <div class="space-y-4">
          <label class="block text-xs font-semibold text-gray-600 uppercase tracking-wider mb-2">
            Options
          </label>
          <label class="flex items-start gap-3 cursor-pointer">
            <input type="checkbox" v-model="evidenceIncludeSod"
                   class="mt-0.5 rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
            <div>
              <span class="text-sm font-medium text-gray-900">Include SoD Policies &amp; Violations</span>
              <p class="text-xs text-gray-500">Exports all separation-of-duties policy definitions and open violations.</p>
            </div>
          </label>
          <label class="flex items-start gap-3 cursor-pointer">
            <input type="checkbox" v-model="evidenceIncludeEntitlements"
                   class="mt-0.5 rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
            <div>
              <span class="text-sm font-medium text-gray-900">Include Entitlement Snapshot</span>
              <p class="text-xs text-gray-500">Exports a point-in-time snapshot of all users and their group memberships from LDAP.</p>
            </div>
          </label>

          <button @click="downloadEvidencePackage"
                  :disabled="loading.evidence || evidencePackageCampaignIds.length === 0"
                  class="w-full bg-green-600 text-white text-sm font-medium rounded-lg px-4 py-2.5 hover:bg-green-700 disabled:opacity-50 flex items-center justify-center gap-2">
            <svg v-if="loading.evidence" class="animate-spin h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
            </svg>
            {{ loading.evidence ? 'Generating package...' : 'Download Evidence Package (ZIP)' }}
          </button>
        </div>
      </div>

      <!-- Success toast -->
      <div v-if="evidenceSuccess" class="mt-4 bg-green-50 border border-green-200 text-green-700 rounded-lg px-4 py-3 text-sm flex items-center gap-2">
        <svg class="w-4 h-4 shrink-0" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"></path></svg>
        Evidence package downloaded successfully ({{ evidenceFileSize }}).
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
  generateEvidencePackage,
} from '@/api/complianceReports'

const route = useRoute()
const auth = useAuthStore()

const groupDnFilter = ref('')
const selectedCampaignId = ref('')
const campaigns = ref([])
const error = ref('')
const loading = ref({ userAccess: false, reviewSummary: false, privileged: false, evidence: false })

// Evidence package state
const evidencePackageCampaignIds = ref([])
const evidenceIncludeSod = ref(true)
const evidenceIncludeEntitlements = ref(false)
const evidenceSuccess = ref(false)
const evidenceFileSize = ref('')

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

function formatBytes(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
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

async function downloadEvidencePackage() {
  loading.value.evidence = true
  error.value = ''
  evidenceSuccess.value = false
  try {
    const body = {
      campaignIds: evidencePackageCampaignIds.value,
      includeSod: evidenceIncludeSod.value,
      includeEntitlements: evidenceIncludeEntitlements.value,
    }
    const { data } = await generateEvidencePackage(dirId(), body)
    const today = new Date().toISOString().slice(0, 10)
    triggerDownload(data, `evidence-package-${today}.zip`)
    evidenceFileSize.value = formatBytes(data.size)
    evidenceSuccess.value = true
    setTimeout(() => { evidenceSuccess.value = false }, 10000)
  } catch (e) {
    if (e.response?.status === 429) {
      error.value = 'An evidence package is already being generated. Please wait and try again.'
    } else {
      error.value = 'Failed to generate Evidence Package: ' + (e.response?.data?.message || e.message)
    }
  } finally {
    loading.value.evidence = false
  }
}
</script>
