<template>
  <div class="p-6 max-w-3xl">
    <h1 class="text-2xl font-bold text-gray-900">New Access Review Campaign</h1>
    <p class="text-sm text-gray-500 mt-1 mb-6">Configure and launch a new access review campaign</p>

    <form @submit.prevent="handleSubmit" class="space-y-6">
      <!-- Start from template -->
      <div v-if="templates.length" class="bg-blue-50 rounded-lg border border-blue-200 p-4">
        <label class="block text-sm font-medium text-blue-800 mb-2">Start from template</label>
        <div class="flex gap-3">
          <select v-model="selectedTemplateId"
            class="flex-1 border border-blue-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 bg-white">
            <option value="">-- blank campaign --</option>
            <option v-for="t in templates" :key="t.id" :value="t.id">{{ t.name }}</option>
          </select>
          <button type="button" @click="applyTemplate" :disabled="!selectedTemplateId"
            class="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50">
            Apply
          </button>
        </div>
      </div>

      <!-- Campaign details -->
      <div class="bg-white rounded-lg border p-5 space-y-4">
        <h2 class="text-lg font-semibold text-gray-800">Campaign Details</h2>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Name *</label>
          <input v-model="form.name" type="text" required
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            placeholder="Q1 2026 Access Review" />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
          <textarea v-model="form.description" rows="2"
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            placeholder="Quarterly access recertification..."></textarea>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Deadline (days from start) *</label>
            <input v-model.number="form.deadlineDays" type="number" min="1" required
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              placeholder="30" />
            <p class="text-xs text-gray-400 mt-1">Number of days reviewers have to complete the review</p>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Repeat every (months)</label>
            <input v-model.number="form.recurrenceMonths" type="number" min="1"
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              placeholder="e.g. 3 for quarterly" />
            <p class="text-xs text-gray-400 mt-1">Leave empty for one-time campaigns</p>
          </div>
        </div>

        <div class="space-y-3">
          <label class="flex items-center gap-2 text-sm">
            <input v-model="form.autoRevoke" type="checkbox" class="rounded border-gray-300" />
            Auto-revoke on decision
          </label>
          <div>
            <label class="flex items-center gap-2 text-sm">
              <input v-model="form.autoRevokeOnExpiry" type="checkbox" class="rounded border-gray-300" />
              Auto-revoke on expiry
            </label>
            <p v-if="form.autoRevokeOnExpiry" class="ml-6 mt-1 text-xs text-orange-600 bg-orange-50 rounded p-2">
              Warning: When the campaign deadline passes, all undecided memberships will be automatically revoked.
              This requires the global auto-revoke kill switch to be enabled on the server.
            </p>
          </div>
        </div>

        <!-- Escalation settings -->
        <div v-if="form.recurrenceMonths || form.deadlineDays" class="border-t pt-4 mt-4">
          <h3 class="text-sm font-medium text-gray-700 mb-2">Escalation &amp; Reminders</h3>
          <p class="text-xs text-gray-500 mb-2">
            Reminders are sent automatically when the deadline approaches (default: 3 days before).
            If a reviewer has not responded after 14 days, the campaign creator will receive an escalation notification.
            These thresholds are configured server-side.
          </p>
          <p v-if="form.recurrenceMonths && form.deadlineDays" class="text-xs text-blue-600">
            Next scheduled run: approximately {{ nextScheduledRun }}
          </p>
        </div>
      </div>

      <!-- Groups -->
      <div class="bg-white rounded-lg border p-5 space-y-4">
        <div class="flex items-center justify-between">
          <h2 class="text-lg font-semibold text-gray-800">Groups to Review</h2>
          <button type="button" @click="addGroup" class="btn-secondary text-xs">Add Group</button>
        </div>

        <div v-for="(g, i) in form.groups" :key="i" class="border rounded-lg p-4 space-y-3 relative">
          <button v-if="form.groups.length > 1" type="button" @click="form.groups.splice(i, 1)"
            class="absolute top-2 right-2 text-gray-400 hover:text-red-500 text-sm">Remove</button>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Group DN *</label>
            <GroupDnPicker v-model="g.groupDn" :directory-id="dirId" />
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Member Attribute</label>
              <select v-model="g.memberAttribute"
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500">
                <option value="member">member</option>
                <option value="uniqueMember">uniqueMember</option>
                <option value="memberUid">memberUid</option>
              </select>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Reviewer *</label>
              <select v-model="g.reviewerAccountId" required
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500">
                <option value="">-- select reviewer --</option>
                <option v-for="admin in admins" :key="admin.id" :value="admin.id">
                  {{ admin.displayName || admin.username }}
                </option>
              </select>
            </div>
          </div>
        </div>
      </div>

      <div class="flex gap-3">
        <button type="submit" :disabled="loading" class="btn-primary">
          {{ loading ? 'Creating...' : 'Create Campaign' }}
        </button>
        <button type="button" @click="$router.back()" class="btn-neutral">Cancel</button>
      </div>
    </form>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useApi } from '@/composables/useApi'
import { createCampaign, listReviewers } from '@/api/accessReviews'
import { listTemplates } from '@/api/campaignTemplates'
import GroupDnPicker from '@/components/GroupDnPicker.vue'

const route = useRoute()
const router = useRouter()
const { loading, call } = useApi()
const dirId = route.params.dirId

const admins = ref([])
const templates = ref([])
const selectedTemplateId = ref('')

const form = reactive({
  name: '',
  description: '',
  deadlineDays: 30,
  recurrenceMonths: null,
  autoRevoke: false,
  autoRevokeOnExpiry: false,
  groups: [{ groupDn: '', memberAttribute: 'member', reviewerAccountId: '' }],
})

const nextScheduledRun = computed(() => {
  if (!form.recurrenceMonths || !form.deadlineDays) return ''
  const d = new Date()
  d.setMonth(d.getMonth() + form.recurrenceMonths)
  return d.toLocaleDateString()
})

function addGroup() {
  form.groups.push({ groupDn: '', memberAttribute: 'member', reviewerAccountId: '' })
}

function applyTemplate() {
  const t = templates.value.find(t => t.id === selectedTemplateId.value)
  if (!t) return
  form.name = ''
  form.description = t.description || ''
  form.deadlineDays = t.config.deadlineDays
  form.recurrenceMonths = t.config.recurrenceMonths
  form.autoRevoke = t.config.autoRevoke
  form.autoRevokeOnExpiry = t.config.autoRevokeOnExpiry
  form.groups = t.config.groups.map(g => ({
    groupDn: g.groupDn,
    memberAttribute: g.memberAttribute || 'member',
    reviewerAccountId: g.reviewerAccountId,
  }))
}

async function handleSubmit() {
  const payload = {
    name: form.name,
    description: form.description || null,
    deadlineDays: form.deadlineDays,
    recurrenceMonths: form.recurrenceMonths || null,
    autoRevoke: form.autoRevoke,
    autoRevokeOnExpiry: form.autoRevokeOnExpiry,
    groups: form.groups.map(g => ({
      groupDn: g.groupDn,
      memberAttribute: g.memberAttribute,
      reviewerAccountId: g.reviewerAccountId,
    })),
  }

  try {
    const res = await call(() => createCampaign(dirId, payload), { successMsg: 'Campaign created' })
    router.push({ name: 'accessReviewDetail', params: { dirId, campaignId: res.data.id } })
  } catch { /* handled by useApi */ }
}

onMounted(async () => {
  try {
    const res = await listReviewers(dirId)
    admins.value = res.data
  } catch (e) {
    console.warn('Failed to load reviewers:', e)
  }
  try {
    const res = await listTemplates(dirId)
    templates.value = res.data
  } catch (e) {
    console.warn('Failed to load templates:', e)
  }
})
</script>

<style scoped>
@reference "tailwindcss";
</style>
