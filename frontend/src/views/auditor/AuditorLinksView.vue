<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Auditor Links</h1>
      <button @click="showCreate = true" :disabled="!dirId"
              class="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center gap-1.5">
        <svg class="w-4 h-4" fill="none" viewBox="0 0 20 20" stroke="currentColor" stroke-width="1.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M13.19 8.688a4.5 4.5 0 011.242 7.244l-4.5 4.5a4.5 4.5 0 01-6.364-6.364l1.757-1.757m9.86-2.94a4.5 4.5 0 00-1.242-7.244l-4.5-4.5a4.5 4.5 0 00-6.364 6.364L5.25 8.25" />
        </svg>
        Share with Auditor
      </button>
    </div>

    <!-- Directory picker (superadmin) -->
    <div v-if="!routeDirId" class="mb-4">
      <label class="block text-sm font-medium text-gray-700 mb-1">Directory</label>
      <select v-model="selectedDir" class="input w-64">
        <option value="" disabled>{{ loadingDirs ? 'Loading...' : '-- Select directory --' }}</option>
        <option v-for="d in directories" :key="d.id" :value="d.id">{{ d.displayName }}</option>
      </select>
    </div>

    <!-- Links table -->
    <div v-if="loading" class="text-sm text-gray-500">Loading links...</div>

    <div v-else-if="links.length === 0 && dirId" class="bg-gray-50 border border-gray-200 rounded-xl p-8 text-center">
      <p class="text-sm text-gray-500">No auditor links created yet. Share evidence with an auditor by creating a link.</p>
    </div>

    <div v-else-if="links.length > 0" class="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-gray-200 bg-gray-50">
              <th class="text-left py-2 px-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">Label</th>
              <th class="text-left py-2 px-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">Created</th>
              <th class="text-left py-2 px-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">Expires</th>
              <th class="text-left py-2 px-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">Accessed</th>
              <th class="text-left py-2 px-4 text-xs font-semibold text-gray-500 uppercase tracking-wider">Status</th>
              <th class="w-20"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="link in links" :key="link.id" class="border-b border-gray-100 hover:bg-gray-50/50">
              <td class="py-2 px-4">
                <div class="text-xs font-medium text-gray-800">{{ link.label || '(untitled)' }}</div>
                <div class="text-[10px] text-gray-400">by {{ link.createdBy }}</div>
              </td>
              <td class="py-2 px-4 text-xs text-gray-500 font-mono">{{ formatDate(link.createdAt) }}</td>
              <td class="py-2 px-4 text-xs text-gray-500 font-mono">{{ formatDate(link.expiresAt) }}</td>
              <td class="py-2 px-4">
                <div class="text-xs text-gray-700 font-semibold">{{ link.accessCount }}</div>
                <div v-if="link.lastAccessedAt" class="text-[10px] text-gray-400">{{ formatDate(link.lastAccessedAt) }}</div>
              </td>
              <td class="py-2 px-4">
                <span class="text-xs font-medium px-2 py-0.5 rounded-full" :class="statusClass(link)">{{ statusLabel(link) }}</span>
              </td>
              <td class="py-2 px-4 text-right">
                <button v-if="!link.revoked && !isExpired(link)"
                        @click="confirmRevoke(link)"
                        class="text-xs text-red-600 hover:text-red-800">Revoke</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Create modal -->
    <AppModal v-model="showCreate" title="Share with Auditor" size="lg">
      <div class="grid gap-4 md:grid-cols-2">
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-1">Label</label>
          <input v-model="form.label" type="text" maxlength="255" placeholder="Q1 2026 SOC 2 Audit — Deloitte"
                 class="input w-full" />
        </div>

        <!-- Campaign picker -->
        <div>
          <label class="block text-xs font-semibold text-gray-600 uppercase tracking-wider mb-2">Include Campaigns</label>
          <div v-if="!campaigns.length" class="text-sm text-gray-400">No campaigns available.</div>
          <div v-else class="max-h-40 overflow-y-auto border border-gray-200 rounded-lg divide-y divide-gray-100">
            <label v-for="c in campaigns" :key="c.id" class="flex items-center gap-3 px-3 py-2 hover:bg-gray-50 cursor-pointer">
              <input type="checkbox" :value="c.id" v-model="form.campaignIds" class="rounded border-gray-300" />
              <div class="min-w-0">
                <span class="text-sm font-medium text-gray-900 block truncate">{{ c.name }}</span>
                <span class="text-xs text-gray-400">{{ c.status }}</span>
              </div>
            </label>
          </div>
        </div>

        <!-- Options -->
        <div class="space-y-3">
          <label class="block text-xs font-semibold text-gray-600 uppercase tracking-wider mb-2">Options</label>
          <label class="flex items-center gap-2 cursor-pointer">
            <input type="checkbox" v-model="form.includeSod" class="rounded border-gray-300" />
            <span class="text-sm text-gray-700">Include SoD Policies & Violations</span>
          </label>
          <label class="flex items-center gap-2 cursor-pointer">
            <input type="checkbox" v-model="form.includeEntitlements" class="rounded border-gray-300" />
            <span class="text-sm text-gray-700">Include Entitlement Snapshot</span>
          </label>
          <label class="flex items-center gap-2 cursor-pointer">
            <input type="checkbox" v-model="form.includeAuditEvents" class="rounded border-gray-300" />
            <span class="text-sm text-gray-700">Include Audit Events</span>
          </label>
        </div>

        <!-- Date range -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Evidence Window Start</label>
          <input v-model="form.dataFrom" type="datetime-local" class="input w-full" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Evidence Window End</label>
          <input v-model="form.dataTo" type="datetime-local" class="input w-full" />
        </div>

        <!-- Expiry -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Link Expires In</label>
          <select v-model="form.expiryDays" class="input w-full">
            <option :value="7">7 days</option>
            <option :value="14">14 days</option>
            <option :value="30">30 days</option>
            <option :value="60">60 days</option>
            <option :value="90">90 days</option>
          </select>
        </div>
      </div>

      <template #footer>
        <button @click="showCreate = false" class="btn-neutral">Cancel</button>
        <button @click="createLink" :disabled="creating" class="btn-primary">
          {{ creating ? 'Creating...' : 'Generate Link' }}
        </button>
      </template>
    </AppModal>

    <!-- Created link modal -->
    <AppModal v-model="showCreated" title="Link Created" size="md">
      <p class="text-sm text-gray-600 mb-4">Share this URL with the auditor. They can access the evidence without an account.</p>
      <div class="flex gap-2">
        <input :value="createdUrl" readonly
               class="input w-full font-mono text-xs bg-gray-50" />
        <button @click="copyUrl"
                class="shrink-0 px-3 py-2 bg-blue-600 text-white text-xs rounded-lg hover:bg-blue-700">
          {{ urlCopied ? 'Copied!' : 'Copy' }}
        </button>
      </div>
      <template #footer>
        <button @click="showCreated = false" class="btn-primary">Done</button>
      </template>
    </AppModal>

    <!-- Revoke confirmation -->
    <AppModal v-model="showRevokeConfirm" title="Revoke Link" size="sm">
      <p class="text-sm text-gray-600">
        Are you sure you want to revoke the link <strong>{{ revokeTarget?.label || '(untitled)' }}</strong>?
        The auditor will immediately lose access.
      </p>
      <template #footer>
        <button @click="showRevokeConfirm = false" class="btn-neutral">Cancel</button>
        <button @click="doRevoke" :disabled="revoking"
                class="bg-red-600 text-white text-sm font-medium px-4 py-2 rounded-lg hover:bg-red-700 disabled:opacity-50">
          {{ revoking ? 'Revoking...' : 'Revoke' }}
        </button>
      </template>
    </AppModal>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import { listAuditorLinks, createAuditorLink, revokeAuditorLink } from '@/api/auditorLinks'
import { listCampaigns } from '@/api/accessReviews'
import { listDirectories } from '@/api/directories'
import AppModal from '@/components/AppModal.vue'

const route = useRoute()
const notif = useNotificationStore()
const routeDirId = route.params.dirId

const directories = ref([])
const loadingDirs = ref(false)
const selectedDir = ref('')
const dirId = computed(() => routeDirId || selectedDir.value)

const loading = ref(false)
const links = ref([])
const campaigns = ref([])

// Create form
const showCreate = ref(false)
const creating = ref(false)
const form = ref({
  label: '',
  campaignIds: [],
  includeSod: true,
  includeEntitlements: false,
  includeAuditEvents: true,
  dataFrom: '',
  dataTo: '',
  expiryDays: 30,
})

// Created link
const showCreated = ref(false)
const createdUrl = ref('')
const urlCopied = ref(false)

// Revoke
const showRevokeConfirm = ref(false)
const revokeTarget = ref(null)
const revoking = ref(false)

function statusClass(link) {
  if (link.revoked) return 'bg-red-100 text-red-700'
  if (isExpired(link)) return 'bg-gray-100 text-gray-600'
  return 'bg-green-100 text-green-700'
}

function statusLabel(link) {
  if (link.revoked) return 'Revoked'
  if (isExpired(link)) return 'Expired'
  return 'Active'
}

function isExpired(link) {
  return new Date(link.expiresAt) < new Date()
}

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
}

async function loadLinks() {
  if (!dirId.value) return
  loading.value = true
  try {
    const { data } = await listAuditorLinks(dirId.value)
    links.value = data
  } catch (e) {
    notif.error(e.response?.data?.detail || 'Failed to load auditor links')
  }
  loading.value = false
}

async function loadCampaigns() {
  if (!dirId.value) return
  try {
    const { data } = await listCampaigns(dirId.value, { size: 100 })
    campaigns.value = data.content || data
  } catch { /* ok */ }
}

async function createLink() {
  creating.value = true
  try {
    const body = {
      label: form.value.label || null,
      campaignIds: form.value.campaignIds,
      includeSod: form.value.includeSod,
      includeEntitlements: form.value.includeEntitlements,
      includeAuditEvents: form.value.includeAuditEvents,
      dataFrom: form.value.dataFrom ? new Date(form.value.dataFrom).toISOString() : null,
      dataTo: form.value.dataTo ? new Date(form.value.dataTo).toISOString() : null,
      expiryDays: form.value.expiryDays,
    }
    const { data } = await createAuditorLink(dirId.value, body)
    createdUrl.value = `${window.location.origin}/auditor/${data.token}`
    showCreate.value = false
    showCreated.value = true
    resetForm()
    await loadLinks()
    notif.success('Auditor link created')
  } catch (e) {
    notif.error(e.response?.data?.detail || 'Failed to create auditor link')
  }
  creating.value = false
}

function resetForm() {
  form.value = {
    label: '', campaignIds: [], includeSod: true, includeEntitlements: false,
    includeAuditEvents: true, dataFrom: '', dataTo: '', expiryDays: 30,
  }
}

function copyUrl() {
  navigator.clipboard?.writeText(createdUrl.value).then(() => {
    urlCopied.value = true
    setTimeout(() => { urlCopied.value = false }, 2000)
  }).catch(() => {})
}

function confirmRevoke(link) {
  revokeTarget.value = link
  showRevokeConfirm.value = true
}

async function doRevoke() {
  revoking.value = true
  try {
    await revokeAuditorLink(dirId.value, revokeTarget.value.id)
    showRevokeConfirm.value = false
    await loadLinks()
    notif.success('Auditor link revoked')
  } catch (e) {
    notif.error(e.response?.data?.detail || 'Failed to revoke link')
  }
  revoking.value = false
}

watch(dirId, (id) => { if (id) { loadLinks(); loadCampaigns() } })

onMounted(async () => {
  if (!routeDirId) {
    loadingDirs.value = true
    try {
      const { data } = await listDirectories()
      directories.value = data
      if (data.length === 1) selectedDir.value = data[0].id
    } catch (e) {
      notif.error(e.response?.data?.detail || 'Failed to load directories')
    }
    loadingDirs.value = false
  }
  await Promise.all([loadLinks(), loadCampaigns()])
})
</script>

<style scoped>
@reference "tailwindcss";
.input         { @apply border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
</style>
