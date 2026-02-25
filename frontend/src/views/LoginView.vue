<template>
  <div class="min-h-screen bg-gray-100 flex items-center justify-center p-4">
    <div class="bg-white rounded-2xl shadow-xl w-full max-w-sm p-8">
      <div class="text-center mb-8">
        <div class="text-4xl mb-3">🔐</div>
        <h1 class="text-2xl font-bold text-gray-900">LDAP Admin</h1>
        <p class="text-sm text-gray-500 mt-1">Sign in to continue</p>
      </div>

      <form @submit.prevent="handleLogin" class="space-y-4">
        <FormField label="Username" v-model="form.username" placeholder="username" required />
        <FormField label="Password" type="password" v-model="form.password" placeholder="••••••••" required />

        <p v-if="errorMsg" class="text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2">
          {{ errorMsg }}
        </p>

        <button
          type="submit"
          :disabled="loading"
          class="w-full bg-blue-600 hover:bg-blue-700 text-white font-semibold py-2.5 rounded-lg transition-colors disabled:opacity-50"
        >
          {{ loading ? 'Signing in…' : 'Sign in' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import FormField from '@/components/FormField.vue'

const router = useRouter()
const route  = useRoute()
const auth   = useAuthStore()

const form     = ref({ username: '', password: '' })
const loading  = ref(false)
const errorMsg = ref('')

async function handleLogin() {
  errorMsg.value = ''
  loading.value  = true
  try {
    await auth.login(form.value.username, form.value.password)
    const rawRedirect = route.query.redirect
    const redirect = (typeof rawRedirect === 'string' && rawRedirect.startsWith('/') && rawRedirect !== '/login')
      ? rawRedirect
      : '/directories'
    router.push(redirect)
  } catch (err) {
    errorMsg.value = err.response?.data?.detail
      || err.response?.data?.message
      || 'Invalid username or password'
  } finally {
    loading.value = false
  }
}
</script>
