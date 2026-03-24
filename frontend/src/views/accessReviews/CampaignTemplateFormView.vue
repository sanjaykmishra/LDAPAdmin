<template>
  <div class="p-6 max-w-3xl">
    <h1 class="text-2xl font-bold text-gray-900 mb-6">{{ isEdit ? 'Edit' : 'New' }} Campaign Template</h1>

    <form @submit.prevent="handleSubmit" class="space-y-6">
      <!-- Template details -->
      <div class="bg-white rounded-lg border p-5 space-y-4">
        <h2 class="text-lg font-semibold text-gray-800">Template Details</h2>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Name *</label>
          <input v-model="form.name" type="text" required
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            placeholder="Quarterly Review Template" />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
          <textarea v-model="form.description" rows="2"
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            placeholder="Template for quarterly access recertification..."></textarea>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Deadline (days) *</label>
            <input v-model.number="form.deadlineDays" type="number" min="1" required
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              placeholder="30" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Repeat every (months)</label>
            <input v-model.number="form.recurrenceMonths" type="number" min="1"
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              placeholder="e.g. 3 for quarterly" />
          </div>
        </div>

        <div class="flex gap-6">
          <label class="flex items-center gap-2 text-sm">
            <input v-model="form.autoRevoke" type="checkbox" class="rounded border-gray-300" />
            Auto-revoke on decision
          </label>
          <label class="flex items-center gap-2 text-sm">
            <input v-model="form.autoRevokeOnExpiry" type="checkbox" class="rounded border-gray-300" />
            Auto-revoke on expiry
          </label>
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
          {{ loading ? 'Saving...' : (isEdit ? 'Update Template' : 'Create Template') }}
        </button>
        <button type="button" @click="$router.back()" class="btn-secondary">Cancel</button>
      </div>
    </form>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useApi } from '@/composables/useApi'
import { createTemplate, getTemplate, updateTemplate } from '@/api/campaignTemplates'
import { listReviewers } from '@/api/accessReviews'
import GroupDnPicker from '@/components/GroupDnPicker.vue'

const route = useRoute()
const router = useRouter()
const { loading, call } = useApi()
const dirId = route.params.dirId
const templateId = route.params.templateId
const isEdit = computed(() => !!templateId)

const admins = ref([])

const form = reactive({
  name: '',
  description: '',
  deadlineDays: 30,
  recurrenceMonths: null,
  autoRevoke: false,
  autoRevokeOnExpiry: false,
  groups: [{ groupDn: '', memberAttribute: 'member', reviewerAccountId: '' }],
})

function addGroup() {
  form.groups.push({ groupDn: '', memberAttribute: 'member', reviewerAccountId: '' })
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
    if (isEdit.value) {
      await call(() => updateTemplate(dirId, templateId, payload), { successMsg: 'Template updated' })
    } else {
      await call(() => createTemplate(dirId, payload), { successMsg: 'Template created' })
    }
    router.push({ name: 'campaignTemplates', params: { dirId } })
  } catch { /* handled by useApi */ }
}

onMounted(async () => {
  try {
    const res = await listReviewers(dirId)
    admins.value = res.data
  } catch (e) {
    console.warn('Failed to load reviewers:', e)
  }

  if (isEdit.value) {
    try {
      const res = await call(() => getTemplate(dirId, templateId))
      const t = res.data
      form.name = t.name
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
    } catch { /* handled */ }
  }
})
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary   { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
.btn-secondary { @apply px-3 py-1.5 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50; }
</style>
