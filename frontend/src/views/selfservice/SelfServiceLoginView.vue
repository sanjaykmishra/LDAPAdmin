<template>
  <div class="min-h-screen bg-gray-100 flex items-center justify-center p-4">
    <div class="bg-white rounded-2xl shadow-xl w-full max-w-sm p-8">
      <div class="text-center mb-8">
        <svg class="w-12 h-12 mx-auto mb-3" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" fill="none">
          <rect width="32" height="32" rx="6" fill="#2563eb"/>
          <path d="M8 8h4v12H8z" fill="#fff"/>
          <path d="M8 20h10v3H8z" fill="#fff"/>
          <path d="M20 8h4v15h-4z" fill="#fff" opacity="0.55"/>
          <circle cx="22" cy="12" r="2.5" fill="#fff" opacity="0.55"/>
        </svg>
        <h1 class="text-2xl font-bold text-gray-900">My Account</h1>
        <p class="text-sm text-gray-500 mt-1">Sign in to manage your profile</p>
      </div>

      <form @submit.prevent="handleLogin" class="space-y-3">
        <!-- Directory picker -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Directory</label>
          <select v-model="form.directoryId" required
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500">
            <option value="">-- select directory --</option>
            <option v-for="dir in directories" :key="dir.id" :value="dir.id">
              {{ dir.displayName }}
            </option>
          </select>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Username</label>
          <input v-model="form.username" type="text" required placeholder="jdoe"
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Password</label>
          <input v-model="form.password" type="password" required placeholder="••••••••"
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
        </div>

        <p v-if="errorMsg" class="text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2">
          {{ errorMsg }}
        </p>

        <button type="submit" :disabled="loading"
          class="w-full bg-blue-600 text-white font-semibold py-2.5 rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50">
          {{ loading ? 'Signing in...' : 'Sign in' }}
        </button>
      </form>

      <!-- Registration link -->
      <div class="mt-6 text-center">
        <p class="text-sm text-gray-500">
          Don't have an account?
          <RouterLink to="/register" class="text-blue-600 hover:text-blue-700 font-medium">Request access</RouterLink>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

const router = useRouter()

const loading = ref(false)
const errorMsg = ref('')

const form = reactive({
  directoryId: '',
  username: '',
  password: '',
})

// Mockup directories — real implementation would fetch from API
const directories = ref([
  { id: 'dir-1', displayName: 'Corporate LDAP' },
  { id: 'dir-2', displayName: 'Partner Directory' },
])

async function handleLogin() {
  errorMsg.value = ''
  loading.value = true
  try {
    // Mockup — would call selfServiceLogin(form) API
    await new Promise(resolve => setTimeout(resolve, 800))
    router.push('/self-service/profile')
  } catch {
    errorMsg.value = 'Invalid username or password'
  } finally {
    loading.value = false
  }
}
</script>
