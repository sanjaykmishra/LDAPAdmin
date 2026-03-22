<template>
  <div>
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Change Password</h1>
      <p class="text-sm text-gray-500 mt-1">Update your LDAP account password</p>
    </div>

    <div class="bg-white rounded-lg border shadow-sm max-w-lg">
      <!-- Success message -->
      <div v-if="success" class="m-6 bg-green-50 border border-green-200 text-green-700 rounded-lg px-4 py-3 text-sm flex items-center gap-2">
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/></svg>
        Password changed successfully.
      </div>

      <form v-if="!success" @submit.prevent="handleSubmit" class="p-6 space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Current Password *</label>
          <input v-model="form.currentPassword" type="password" required placeholder="Enter current password"
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
        </div>

        <hr class="border-gray-100" />

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">New Password *</label>
          <input v-model="form.newPassword" type="password" required placeholder="Enter new password"
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Confirm New Password *</label>
          <input v-model="form.confirmPassword" type="password" required placeholder="Re-enter new password"
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
        </div>

        <!-- Password requirements -->
        <div class="bg-gray-50 rounded-lg p-3">
          <p class="text-xs font-medium text-gray-600 mb-2">Password requirements:</p>
          <ul class="text-xs text-gray-500 space-y-1">
            <li :class="checks.length ? 'text-green-600' : ''">
              {{ checks.length ? '&#10003;' : '&#10007;' }} At least 8 characters
            </li>
            <li :class="checks.uppercase ? 'text-green-600' : ''">
              {{ checks.uppercase ? '&#10003;' : '&#10007;' }} At least one uppercase letter
            </li>
            <li :class="checks.lowercase ? 'text-green-600' : ''">
              {{ checks.lowercase ? '&#10003;' : '&#10007;' }} At least one lowercase letter
            </li>
            <li :class="checks.number ? 'text-green-600' : ''">
              {{ checks.number ? '&#10003;' : '&#10007;' }} At least one number
            </li>
            <li :class="checks.match ? 'text-green-600' : ''">
              {{ checks.match ? '&#10003;' : '&#10007;' }} Passwords match
            </li>
          </ul>
        </div>

        <p v-if="errorMsg" class="text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2">{{ errorMsg }}</p>

        <button type="submit" :disabled="loading || !allValid"
          class="w-full bg-blue-600 text-white font-semibold py-2.5 rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50">
          {{ loading ? 'Changing...' : 'Change Password' }}
        </button>
      </form>

      <!-- Reset after success -->
      <div v-if="success" class="px-6 pb-6">
        <button @click="reset" class="text-sm text-blue-600 hover:text-blue-700">Change password again</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'

const loading = ref(false)
const errorMsg = ref('')
const success = ref(false)

const form = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const checks = computed(() => ({
  length: form.newPassword.length >= 8,
  uppercase: /[A-Z]/.test(form.newPassword),
  lowercase: /[a-z]/.test(form.newPassword),
  number: /[0-9]/.test(form.newPassword),
  match: form.newPassword.length > 0 && form.newPassword === form.confirmPassword,
}))

const allValid = computed(() => Object.values(checks.value).every(Boolean))

async function handleSubmit() {
  errorMsg.value = ''
  loading.value = true
  try {
    // Mockup — would call changePassword({ currentPassword, newPassword })
    await new Promise(resolve => setTimeout(resolve, 800))
    success.value = true
  } catch {
    errorMsg.value = 'Current password is incorrect'
  } finally {
    loading.value = false
  }
}

function reset() {
  success.value = false
  form.currentPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
}
</script>
