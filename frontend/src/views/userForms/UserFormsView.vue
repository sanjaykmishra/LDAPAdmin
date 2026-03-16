<template>
  <div class="p-6 max-w-5xl">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">User Forms</h1>
        <p class="text-sm text-gray-500 mt-1">Define how LDAP attributes are presented and validated in user creation and edit forms</p>
      </div>
      <button @click="openCreate" class="btn-primary">+ New Form</button>
    </div>

    <div class="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <div v-if="loading" class="p-8 text-center text-gray-500 text-sm">Loading…</div>
      <div v-else-if="forms.length === 0" class="p-8 text-center text-gray-400 text-sm">
        No user forms configured. Create one to define attribute display rules for user entries.
      </div>
      <table v-else class="w-full text-sm">
        <thead class="bg-gray-50 border-b border-gray-100">
          <tr>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Form Name</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Object Class</th>
            <th class="px-4 py-3 text-left font-medium text-gray-500">Attributes</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-50">
          <tr v-for="f in forms" :key="f.id" class="hover:bg-gray-50">
            <td class="px-4 py-3 font-medium text-gray-900">{{ f.formName }}</td>
            <td class="px-4 py-3 text-gray-600 font-mono text-xs">{{ f.objectClassName }}</td>
            <td class="px-4 py-3">
              <div class="flex flex-wrap gap-1">
                <span
                  v-for="attr in f.attributeConfigs"
                  :key="attr.id"
                  class="text-xs bg-blue-50 text-blue-700 rounded px-1.5 py-0.5"
                >{{ attr.attributeName }}</span>
                <span v-if="!f.attributeConfigs.length" class="text-xs text-gray-400">None</span>
              </div>
            </td>
            <td class="px-4 py-3 text-right whitespace-nowrap">
              <button @click="openEdit(f)" class="text-blue-600 hover:text-blue-800 text-xs font-medium mr-3">Edit</button>
              <button @click="confirmDelete(f)" class="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Create/Edit modal -->
    <AppModal v-model="showModal" :title="editing ? 'Edit User Form' : 'New User Form'" size="xl">
      <form @submit.prevent="save" class="space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <FormField label="Form Name" v-model="form.formName" required placeholder="e.g. Standard User Form" />
          <FormField label="Object Class" v-model="form.objectClassName" required placeholder="e.g. inetOrgPerson" />
        </div>

        <!-- Attribute configs -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">Attribute Configurations</label>
          <div v-if="form.attributeConfigs.length" class="border border-gray-200 rounded-lg overflow-hidden mb-2">
            <table class="w-full text-sm">
              <thead class="bg-gray-50">
                <tr>
                  <th class="px-3 py-2 text-left text-xs font-medium text-gray-500">Attribute</th>
                  <th class="px-3 py-2 text-left text-xs font-medium text-gray-500">Label</th>
                  <th class="px-3 py-2 text-left text-xs font-medium text-gray-500">Input Type</th>
                  <th class="px-3 py-2 text-center text-xs font-medium text-gray-500">Required</th>
                  <th class="px-3 py-2 text-center text-xs font-medium text-gray-500">Editable</th>
                  <th class="px-3 py-2"></th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100">
                <tr v-for="(attr, idx) in form.attributeConfigs" :key="idx">
                  <td class="px-3 py-2">
                    <input v-model="attr.attributeName" placeholder="e.g. cn" class="input w-full" required />
                  </td>
                  <td class="px-3 py-2">
                    <input v-model="attr.customLabel" placeholder="Custom label" class="input w-full" />
                  </td>
                  <td class="px-3 py-2">
                    <select v-model="attr.inputType" class="input w-full">
                      <option v-for="t in inputTypes" :key="t" :value="t">{{ t }}</option>
                    </select>
                  </td>
                  <td class="px-3 py-2 text-center">
                    <input type="checkbox" v-model="attr.requiredOnCreate" />
                  </td>
                  <td class="px-3 py-2 text-center">
                    <input type="checkbox" v-model="attr.editableOnCreate" />
                  </td>
                  <td class="px-3 py-2 text-right">
                    <button type="button" @click="form.attributeConfigs.splice(idx, 1)" class="text-red-500 hover:text-red-700 text-xs font-medium">Remove</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <button
            type="button"
            @click="addAttribute"
            class="text-blue-600 hover:text-blue-800 text-xs font-medium"
          >+ Add attribute</button>
        </div>

        <div class="flex justify-end gap-2 pt-2">
          <button type="button" @click="showModal = false" class="btn-secondary">Cancel</button>
          <button type="submit" :disabled="saving" class="btn-primary">{{ saving ? 'Saving…' : 'Save' }}</button>
        </div>
      </form>
    </AppModal>

    <!-- Delete confirm -->
    <ConfirmDialog
      v-if="deleteTarget"
      :message="`Delete form '${deleteTarget.formName}'? This will remove all attribute configurations and unlink it from any realms.`"
      @confirm="doDelete"
      @cancel="deleteTarget = null"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useNotificationStore } from '@/stores/notifications'
import { listUserForms, createUserForm, updateUserForm, deleteUserForm } from '@/api/userForms'
import FormField from '@/components/FormField.vue'
import AppModal from '@/components/AppModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const notif = useNotificationStore()

const inputTypes = ['TEXT', 'TEXTAREA', 'PASSWORD', 'BOOLEAN', 'DATE', 'DATETIME', 'MULTI_VALUE', 'DN_LOOKUP']

const loading      = ref(false)
const saving       = ref(false)
const forms        = ref([])
const showModal    = ref(false)
const editing      = ref(null)
const deleteTarget = ref(null)

const form = ref(emptyForm())

function emptyForm() {
  return {
    formName: '',
    objectClassName: '',
    attributeConfigs: [],
  }
}

function emptyAttribute() {
  return {
    attributeName: '',
    customLabel: '',
    inputType: 'TEXT',
    requiredOnCreate: false,
    editableOnCreate: true,
  }
}

function addAttribute() {
  form.value.attributeConfigs.push(emptyAttribute())
}

async function load() {
  loading.value = true
  try {
    const { data } = await listUserForms()
    forms.value = data
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
  showModal.value = true
}

function openEdit(f) {
  editing.value = f.id
  form.value = {
    formName: f.formName,
    objectClassName: f.objectClassName,
    attributeConfigs: (f.attributeConfigs || []).map(a => ({
      attributeName: a.attributeName,
      customLabel: a.customLabel || '',
      inputType: a.inputType,
      requiredOnCreate: a.requiredOnCreate,
      editableOnCreate: a.editableOnCreate,
    })),
  }
  showModal.value = true
}

async function save() {
  saving.value = true
  try {
    if (editing.value) {
      await updateUserForm(editing.value, form.value)
      notif.success('Form updated')
    } else {
      await createUserForm(form.value)
      notif.success('Form created')
    }
    showModal.value = false
    await load()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    saving.value = false
  }
}

function confirmDelete(f) { deleteTarget.value = f }

async function doDelete() {
  try {
    await deleteUserForm(deleteTarget.value.id)
    notif.success('Form deleted')
    deleteTarget.value = null
    await load()
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
