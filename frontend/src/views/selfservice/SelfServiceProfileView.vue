<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">My Profile</h1>
        <p class="text-sm text-gray-500 mt-1">View and update your account information</p>
      </div>
      <button v-if="!editing" @click="editing = true" class="btn-primary">Edit Profile</button>
      <div v-else class="flex gap-2">
        <button @click="handleSave" :disabled="saving" class="btn-primary">
          {{ saving ? 'Saving...' : 'Save Changes' }}
        </button>
        <button @click="cancelEdit" class="btn-secondary">Cancel</button>
      </div>
    </div>

    <!-- Success message -->
    <div v-if="saved" class="bg-green-50 border border-green-200 text-green-700 rounded-lg px-4 py-3 mb-6 text-sm flex items-center gap-2">
      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/></svg>
      Profile updated successfully.
    </div>

    <!-- Profile form -->
    <div class="bg-white rounded-lg border shadow-sm">
      <!-- Avatar / header section -->
      <div class="p-6 border-b border-gray-100 flex items-center gap-4">
        <div class="w-16 h-16 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-2xl font-bold">
          {{ initials }}
        </div>
        <div>
          <h2 class="text-lg font-semibold text-gray-900">{{ profile.displayName }}</h2>
          <p class="text-sm text-gray-500">{{ profile.dn }}</p>
        </div>
      </div>

      <!-- Fields -->
      <div class="p-6 space-y-4">
        <div v-for="field in fields" :key="field.name"
          class="grid grid-cols-3 gap-4 items-start py-2"
          :class="{ 'border-b border-gray-50': field !== fields[fields.length - 1] }">
          <label class="text-sm font-medium text-gray-600 pt-2">
            {{ field.label }}
            <span v-if="field.editable && editing" class="text-xs text-blue-500 block">editable</span>
            <span v-if="!field.editable && editing" class="text-xs text-gray-400 block">read-only</span>
          </label>
          <div class="col-span-2">
            <input v-if="editing && field.editable"
              v-model="editForm[field.name]"
              type="text"
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
            <p v-else class="text-sm text-gray-900 py-2">{{ profile[field.name] || '-' }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'

const editing = ref(false)
const saving = ref(false)
const saved = ref(false)

// Mockup profile data
const profile = reactive({
  dn: 'uid=jdoe,ou=people,dc=example,dc=com',
  uid: 'jdoe',
  displayName: 'John Doe',
  cn: 'John Doe',
  givenName: 'John',
  sn: 'Doe',
  mail: 'john.doe@example.com',
  telephoneNumber: '+1 555-0123',
  mobile: '+1 555-0456',
  title: 'Software Engineer',
  department: 'Engineering',
  l: 'San Francisco',
})

// Field definitions — editable flags come from UserTemplate selfServiceEditable config
const fields = [
  { name: 'uid', label: 'Username', editable: false },
  { name: 'cn', label: 'Full Name', editable: false },
  { name: 'givenName', label: 'First Name', editable: false },
  { name: 'sn', label: 'Last Name', editable: false },
  { name: 'mail', label: 'Email', editable: true },
  { name: 'telephoneNumber', label: 'Phone', editable: true },
  { name: 'mobile', label: 'Mobile', editable: true },
  { name: 'title', label: 'Job Title', editable: false },
  { name: 'department', label: 'Department', editable: false },
  { name: 'l', label: 'Location', editable: true },
]

const editForm = reactive({})

const initials = computed(() => {
  const parts = profile.displayName.split(' ')
  return parts.map(p => p[0]).join('').toUpperCase().slice(0, 2)
})

function cancelEdit() {
  editing.value = false
}

async function handleSave() {
  saving.value = true
  saved.value = false
  try {
    // Mockup — would call updateProfile(editForm)
    await new Promise(resolve => setTimeout(resolve, 600))
    // Apply changes to profile
    for (const field of fields) {
      if (field.editable && editForm[field.name] !== undefined) {
        profile[field.name] = editForm[field.name]
      }
    }
    editing.value = false
    saved.value = true
    setTimeout(() => { saved.value = false }, 3000)
  } finally {
    saving.value = false
  }
}

// Initialize edit form when entering edit mode
import { watch } from 'vue'
watch(editing, (val) => {
  if (val) {
    for (const field of fields) {
      if (field.editable) {
        editForm[field.name] = profile[field.name]
      }
    }
  }
})
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary   { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
.btn-secondary { @apply px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm font-medium hover:bg-gray-50; }
</style>
