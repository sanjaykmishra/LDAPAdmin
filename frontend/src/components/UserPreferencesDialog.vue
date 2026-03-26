<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="$emit('close')">
    <div class="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-lg max-h-[85vh] flex flex-col">
      <div class="flex items-center justify-between px-5 py-3 border-b border-gray-200 dark:border-gray-700">
        <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">User Preferences</h3>
        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 text-lg leading-none">&times;</button>
      </div>

      <div class="overflow-y-auto p-5 space-y-6">

        <!-- Theme -->
        <section>
          <h4 class="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">Theme</h4>
          <div class="flex gap-2">
            <button v-for="opt in themeOptions" :key="opt.value"
              @click="form.themePreference = opt.value"
              :class="['px-4 py-2 rounded-lg text-sm font-medium border transition-colors',
                form.themePreference === opt.value
                  ? 'bg-blue-50 dark:bg-blue-900 border-blue-300 dark:border-blue-600 text-blue-700 dark:text-blue-300'
                  : 'border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700']">
              {{ opt.label }}
            </button>
          </div>
        </section>

        <!-- Profile -->
        <section>
          <h4 class="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">Profile</h4>
          <div class="space-y-3">
            <div>
              <label class="block text-sm text-gray-600 dark:text-gray-400 mb-1">Display Name</label>
              <input v-model="form.displayName" type="text" autocomplete="off"
                class="input w-full" placeholder="Your name" />
            </div>
            <div>
              <label class="block text-sm text-gray-600 dark:text-gray-400 mb-1">Email</label>
              <input v-model="form.email" type="email" autocomplete="off"
                class="input w-full" placeholder="you@example.com" />
            </div>
          </div>
        </section>

        <!-- Change Password -->
        <section v-if="canChangePassword">
          <h4 class="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">Change Password</h4>
          <div class="space-y-3">
            <div>
              <label class="block text-sm text-gray-600 dark:text-gray-400 mb-1">Current Password</label>
              <input v-model="pwForm.currentPassword" type="password" autocomplete="new-password"
                class="input w-full" />
            </div>
            <div>
              <label class="block text-sm text-gray-600 dark:text-gray-400 mb-1">New Password</label>
              <input v-model="pwForm.newPassword" type="password" autocomplete="new-password"
                class="input w-full" />
            </div>
            <div>
              <label class="block text-sm text-gray-600 dark:text-gray-400 mb-1">Confirm New Password</label>
              <input v-model="pwForm.confirmPassword" type="password" autocomplete="new-password"
                class="input w-full" />
            </div>
            <button @click="doChangePassword" :disabled="!canSubmitPassword || savingPw"
              class="btn-secondary text-sm">
              {{ savingPw ? 'Changing…' : 'Change Password' }}
            </button>
            <p v-if="pwError" class="text-xs text-red-600">{{ pwError }}</p>
            <p v-if="pwSuccess" class="text-xs text-green-600">{{ pwSuccess }}</p>
          </div>
        </section>
      </div>

      <!-- Footer -->
      <div class="px-5 py-3 border-t border-gray-200 dark:border-gray-700 flex justify-between items-center">
        <p v-if="saveError" class="text-xs text-red-600">{{ saveError }}</p>
        <p v-if="saveSuccess" class="text-xs text-green-600">{{ saveSuccess }}</p>
        <div class="flex gap-2 ml-auto">
          <button @click="$emit('close')" class="btn-secondary text-sm">Cancel</button>
          <button @click="doSavePrefs" :disabled="saving" class="btn-primary text-sm">
            {{ saving ? 'Saving…' : 'Save' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'
import { updatePreferences, changePassword } from '@/api/auth'

const emit = defineEmits(['close'])

const auth = useAuthStore()
const { setTheme } = useTheme()

const themeOptions = [
  { label: 'Light', value: 'light' },
  { label: 'Dark', value: 'dark' },
  { label: 'System', value: 'system' },
]

const form = ref({
  themePreference: auth.themePreference || 'system',
  displayName: auth.principal?.displayName || '',
  email: auth.principal?.email || '',
})

const saving = ref(false)
const saveError = ref('')
const saveSuccess = ref('')

const canChangePassword = computed(() => {
  const t = auth.authType
  return t === 'LOCAL' || t === 'LDAP'
})

const pwForm = ref({ currentPassword: '', newPassword: '', confirmPassword: '' })
const savingPw = ref(false)
const pwError = ref('')
const pwSuccess = ref('')

const canSubmitPassword = computed(() =>
  pwForm.value.currentPassword && pwForm.value.newPassword &&
  pwForm.value.newPassword === pwForm.value.confirmPassword &&
  pwForm.value.newPassword.length >= 8
)

async function doSavePrefs() {
  saving.value = true
  saveError.value = ''
  saveSuccess.value = ''
  try {
    await updatePreferences({
      themePreference: form.value.themePreference,
      displayName: form.value.displayName,
      email: form.value.email,
    })
    // Apply theme immediately
    setTheme(form.value.themePreference)
    // Update auth store
    auth.updatePrincipal({
      themePreference: form.value.themePreference,
      displayName: form.value.displayName,
      email: form.value.email,
    })
    saveSuccess.value = 'Preferences saved.'
    setTimeout(() => { saveSuccess.value = '' }, 3000)
  } catch (e) {
    saveError.value = e.response?.data?.error || e.response?.data?.detail || e.message
  } finally {
    saving.value = false
  }
}

async function doChangePassword() {
  if (pwForm.value.newPassword !== pwForm.value.confirmPassword) {
    pwError.value = 'Passwords do not match.'
    return
  }
  savingPw.value = true
  pwError.value = ''
  pwSuccess.value = ''
  try {
    const { data } = await changePassword(pwForm.value.currentPassword, pwForm.value.newPassword)
    if (data.error) {
      pwError.value = data.error
    } else {
      pwSuccess.value = 'Password changed successfully.'
      pwForm.value = { currentPassword: '', newPassword: '', confirmPassword: '' }
      setTimeout(() => { pwSuccess.value = '' }, 3000)
    }
  } catch (e) {
    pwError.value = e.response?.data?.error || e.response?.data?.detail || e.message
  } finally {
    savingPw.value = false
  }
}
</script>

<style scoped>
@reference "tailwindcss";
.input { @apply border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-2 text-sm bg-white dark:bg-gray-700 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500; }
.btn-primary { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
.btn-secondary { @apply px-3 py-1.5 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg text-sm hover:bg-gray-50 dark:hover:bg-gray-700; }
</style>
