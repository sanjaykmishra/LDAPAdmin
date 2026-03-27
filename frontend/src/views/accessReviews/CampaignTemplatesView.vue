<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Campaign Templates</h1>
        <p class="text-sm text-gray-500 mt-1">Reusable configurations for access review campaigns</p>
      </div>
      <button @click="$router.push({ name: 'campaignTemplateCreate', params: { dirId } })"
        class="btn-primary text-sm">New Template</button>
    </div>

    <div v-if="loading && !templates.length" class="text-gray-500">Loading...</div>

    <div v-if="!loading && !templates.length" class="text-center py-12 text-gray-400">
      <p class="text-lg">No templates yet</p>
      <p class="text-sm mt-1">Create a template to speed up recurring campaign creation</p>
    </div>

    <div v-if="templates.length" class="space-y-3">
      <div v-for="t in templates" :key="t.id"
        class="bg-white rounded-lg border p-5 hover:border-blue-300 transition-colors">
        <div class="flex items-start justify-between">
          <div class="flex-1">
            <h3 class="text-base font-semibold text-gray-900">{{ t.name }}</h3>
            <p v-if="t.description" class="text-sm text-gray-500 mt-0.5">{{ t.description }}</p>
            <div class="flex gap-4 text-xs text-gray-400 mt-2">
              <span>Deadline: {{ t.config.deadlineDays }} days</span>
              <span v-if="t.config.recurrenceMonths">Recurrence: every {{ t.config.recurrenceMonths }} month{{ t.config.recurrenceMonths > 1 ? 's' : '' }}</span>
              <span>{{ t.config.groups.length }} group{{ t.config.groups.length !== 1 ? 's' : '' }}</span>
              <span v-if="t.config.autoRevoke" class="text-orange-500">Auto-revoke</span>
              <span>Created by: {{ t.createdByUsername }}</span>
            </div>
          </div>
          <div class="flex gap-2 ml-4 shrink-0">
            <button @click="handleCreateCampaign(t)" :disabled="loading" class="btn-primary text-xs">
              Create Campaign
            </button>
            <button @click="$router.push({ name: 'campaignTemplateEdit', params: { dirId, templateId: t.id } })"
              class="btn-secondary text-xs">Edit</button>
            <button @click="confirmDelete(t)" class="text-xs px-3 py-1.5 rounded-lg text-red-600 hover:bg-red-50 border border-red-200">
              Delete
            </button>
          </div>
        </div>
      </div>
    </div>

    <ConfirmDialog v-model="showDeleteConfirm" title="Delete Template"
      :message="`Delete template '${templateToDelete?.name}'? This cannot be undone.`"
      confirm-label="Delete" @confirm="doDelete" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useApi } from '@/composables/useApi'
import { listTemplates, deleteTemplate, createCampaignFromTemplate } from '@/api/campaignTemplates'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const route = useRoute()
const router = useRouter()
const { loading, call } = useApi()
const dirId = route.params.dirId

const templates = ref([])
const showDeleteConfirm = ref(false)
const templateToDelete = ref(null)

async function loadTemplates() {
  try {
    const res = await call(() => listTemplates(dirId))
    templates.value = res.data
  } catch { /* handled */ }
}

async function handleCreateCampaign(template) {
  try {
    const res = await call(
      () => createCampaignFromTemplate(dirId, template.id),
      { successMsg: 'Campaign created from template' }
    )
    router.push({ name: 'accessReviewDetail', params: { dirId, campaignId: res.data.id } })
  } catch { /* handled */ }
}

function confirmDelete(template) {
  templateToDelete.value = template
  showDeleteConfirm.value = true
}

async function doDelete() {
  showDeleteConfirm.value = false
  if (!templateToDelete.value) return
  try {
    await call(() => deleteTemplate(dirId, templateToDelete.value.id), { successMsg: 'Template deleted' })
    await loadTemplates()
  } catch { /* handled */ }
}

onMounted(loadTemplates)
</script>

<style scoped>
@reference "tailwindcss";
</style>
