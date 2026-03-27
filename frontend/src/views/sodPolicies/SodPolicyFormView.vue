<template>
  <div class="p-6 max-w-3xl">
    <h1 class="text-2xl font-bold text-gray-900 mb-6">{{ isEdit ? 'Edit SoD Policy' : 'New SoD Policy' }}</h1>

    <form @submit.prevent="handleSubmit" class="space-y-6">
      <div class="bg-white rounded-lg border p-5 space-y-4">
        <h2 class="text-lg font-semibold text-gray-800">Policy Details</h2>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Name *</label>
          <input v-model="form.name" type="text" required
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            placeholder="e.g. Finance Admin vs Audit" />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
          <textarea v-model="form.description" rows="2"
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            placeholder="Why these groups are mutually exclusive..."></textarea>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Group A DN *</label>
            <GroupDnPicker v-model="form.groupADn" :directory-id="dirId" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Group A Display Name</label>
            <input v-model="form.groupAName" type="text"
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              placeholder="e.g. Finance Admins" />
          </div>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Group B DN *</label>
            <GroupDnPicker v-model="form.groupBDn" :directory-id="dirId" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Group B Display Name</label>
            <input v-model="form.groupBName" type="text"
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              placeholder="e.g. Audit Committee" />
          </div>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Severity *</label>
            <select v-model="form.severity" required
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500">
              <option value="CRITICAL">CRITICAL</option>
              <option value="HIGH">HIGH</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="LOW">LOW</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Action *</label>
            <select v-model="form.action" required
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500">
              <option value="ALERT">ALERT - Warn but allow</option>
              <option value="BLOCK">BLOCK - Prevent assignment</option>
            </select>
            <p v-if="form.action === 'BLOCK'" class="mt-1 text-xs text-red-600 bg-red-50 rounded p-2">
              BLOCK will prevent adding users to the conflicting group, returning a 409 error.
            </p>
          </div>
        </div>

        <label class="flex items-center gap-2 text-sm">
          <input v-model="form.enabled" type="checkbox" class="rounded border-gray-300" />
          Enabled
        </label>
      </div>

      <div class="flex gap-3">
        <button type="submit" :disabled="loading" class="btn-primary">
          {{ loading ? 'Saving...' : (isEdit ? 'Update Policy' : 'Create Policy') }}
        </button>
        <button type="button" @click="$router.back()" class="btn-neutral">Cancel</button>
        <button v-if="isEdit" type="button" @click="showDeleteConfirm = true" :disabled="loading"
          class="ml-auto text-sm px-3 py-1.5 rounded-lg bg-red-600 text-white hover:bg-red-700">
          Delete Policy
        </button>
      </div>
    </form>
  </div>
  <ConfirmDialog v-model="showDeleteConfirm"
    message="Delete this SoD policy? All associated violations will also be removed."
    confirmLabel="Delete" :danger="true" @confirm="handleDelete" />
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useApi } from '@/composables/useApi'
import { getPolicy, createPolicy, updatePolicy, deletePolicy } from '@/api/sodPolicies'
import GroupDnPicker from '@/components/GroupDnPicker.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const route = useRoute()
const router = useRouter()
const { loading, call } = useApi()
const dirId = route.params.dirId
const policyId = route.params.policyId

const isEdit = computed(() => !!policyId)

const form = reactive({
  name: '',
  description: '',
  groupADn: '',
  groupBDn: '',
  groupAName: '',
  groupBName: '',
  severity: 'HIGH',
  action: 'ALERT',
  enabled: true,
})

async function handleSubmit() {
  try {
    if (isEdit.value) {
      await call(() => updatePolicy(dirId, policyId, form), { successMsg: 'Policy updated' })
    } else {
      await call(() => createPolicy(dirId, form), { successMsg: 'Policy created' })
    }
    router.push({ name: 'sodPolicies', params: { dirId } })
  } catch { /* handled by useApi */ }
}

const showDeleteConfirm = ref(false)

async function handleDelete() {
  showDeleteConfirm.value = false
  try {
    await call(() => deletePolicy(dirId, policyId), { successMsg: 'Policy deleted' })
    router.push({ name: 'sodPolicies', params: { dirId } })
  } catch { /* handled */ }
}

onMounted(async () => {
  if (isEdit.value) {
    try {
      const res = await getPolicy(dirId, policyId)
      const p = res.data
      form.name = p.name
      form.description = p.description || ''
      form.groupADn = p.groupADn
      form.groupBDn = p.groupBDn
      form.groupAName = p.groupAName || ''
      form.groupBName = p.groupBName || ''
      form.severity = p.severity
      form.action = p.action
      form.enabled = p.enabled
    } catch (e) {
      console.warn('Failed to load policy:', e)
    }
  }
})
</script>

<style scoped>
@reference "tailwindcss";
</style>
