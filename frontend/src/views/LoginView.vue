<template>
  <div class="min-h-screen bg-gray-100 flex items-center justify-center p-4">
    <div class="bg-white rounded-2xl shadow-xl w-full max-w-sm p-8">
      <div class="text-center mb-8">
        <svg class="w-12 h-12 mx-auto mb-3" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" fill="none">
          <rect width="32" height="32" rx="6" :fill="settings.primaryColour"/>
          <path d="M8 8h4v12H8z" fill="#fff"/>
          <path d="M8 20h10v3H8z" fill="#fff"/>
          <path d="M20 8h4v15h-4z" fill="#fff" opacity="0.55"/>
          <circle cx="22" cy="12" r="2.5" fill="#fff" opacity="0.55"/>
        </svg>
        <h1 class="text-2xl font-bold text-gray-900">{{ settings.appName }}</h1>
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
          class="w-full text-white font-semibold py-2.5 rounded-lg transition-colors disabled:opacity-50"
          :style="{ backgroundColor: settings.primaryColour }"
          @mouseenter="$event.target.style.filter = 'brightness(0.9)'"
          @mouseleave="$event.target.style.filter = ''"
        >
          {{ loading ? 'Signing in…' : 'Sign in' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useSettingsStore } from '@/stores/settings'
import FormField from '@/components/FormField.vue'

const router   = useRouter()
const route    = useRoute()
const auth     = useAuthStore()
const settings = useSettingsStore()

onMounted(() => settings.init())

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
      : '/'
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
