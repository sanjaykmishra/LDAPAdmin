<template>
  <div class="min-h-screen bg-gray-100 flex items-center justify-center p-4">
    <div class="bg-white rounded-2xl shadow-xl w-full max-w-lg p-8">
      <!-- Header -->
      <div class="text-center mb-6">
        <svg class="w-12 h-12 mx-auto mb-3" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" fill="none">
          <rect width="32" height="32" rx="6" fill="#2563eb"/>
          <path d="M8 8h4v12H8z" fill="#fff"/>
          <path d="M8 20h10v3H8z" fill="#fff"/>
          <path d="M20 8h4v15h-4z" fill="#fff" opacity="0.55"/>
          <circle cx="22" cy="12" r="2.5" fill="#fff" opacity="0.55"/>
        </svg>
        <h1 class="text-2xl font-bold text-gray-900">Request an Account</h1>
        <p class="text-sm text-gray-500 mt-1">Fill out the form below to request access</p>
      </div>

      <!-- Success state -->
      <div v-if="submitted" class="text-center space-y-4">
        <div class="w-16 h-16 bg-green-100 text-green-600 rounded-full flex items-center justify-center mx-auto">
          <svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"/></svg>
        </div>
        <h2 class="text-lg font-semibold text-gray-900">Check your email</h2>
        <p class="text-sm text-gray-600">
          We've sent a verification link to <strong>{{ form.mail }}</strong>.
          Click the link to verify your email address and submit your request for approval.
        </p>
        <div class="bg-gray-50 rounded-lg p-4 text-sm text-gray-500">
          <p>Your request ID: <code class="bg-gray-200 px-1.5 py-0.5 rounded text-xs">REQ-2026-0042</code></p>
          <p class="mt-1">You can check the status of your request at any time.</p>
        </div>
        <div class="flex gap-3 justify-center pt-2">
          <RouterLink to="/register/status/mock-id" class="btn-secondary">Check Status</RouterLink>
          <RouterLink to="/self-service/login" class="btn-primary">Back to Login</RouterLink>
        </div>
      </div>

      <!-- Registration form -->
      <form v-else @submit.prevent="handleSubmit" class="space-y-4">
        <!-- Step 1: Directory & Realm selection -->
        <div class="space-y-3">
          <h2 class="text-sm font-semibold text-gray-700 uppercase tracking-wider">Directory</h2>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Directory *</label>
            <select v-model="form.directoryId" required
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500">
              <option value="">-- select directory --</option>
              <option v-for="dir in directories" :key="dir.id" :value="dir.id">{{ dir.displayName }}</option>
            </select>
          </div>

          <div v-if="form.directoryId">
            <label class="block text-sm font-medium text-gray-700 mb-1">Realm *</label>
            <select v-model="form.realmId" required
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500">
              <option value="">-- select realm --</option>
              <option v-for="realm in realms" :key="realm.id" :value="realm.id">{{ realm.name }}</option>
            </select>
          </div>
        </div>

        <!-- Step 2: User details (shown after realm selected) -->
        <template v-if="form.realmId">
          <hr class="border-gray-200" />
          <h2 class="text-sm font-semibold text-gray-700 uppercase tracking-wider">Account Details</h2>

          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">First Name *</label>
              <input v-model="form.givenName" type="text" required
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Last Name *</label>
              <input v-model="form.sn" type="text" required
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Desired Username *</label>
            <input v-model="form.uid" type="text" required placeholder="jdoe"
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Email Address *</label>
            <input v-model="form.mail" type="email" required placeholder="john.doe@example.com"
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
            <p class="text-xs text-gray-400 mt-1">A verification link will be sent to this address</p>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Phone</label>
            <input v-model="form.telephoneNumber" type="tel"
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Justification *</label>
            <textarea v-model="form.justification" rows="3" required
              placeholder="Please explain why you need access..."
              class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"></textarea>
          </div>

          <p v-if="errorMsg" class="text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2">{{ errorMsg }}</p>

          <button type="submit" :disabled="loading"
            class="w-full bg-blue-600 text-white font-semibold py-2.5 rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50">
            {{ loading ? 'Submitting...' : 'Submit Request' }}
          </button>
        </template>
      </form>

      <!-- Login link -->
      <div v-if="!submitted" class="mt-6 text-center">
        <p class="text-sm text-gray-500">
          Already have an account?
          <RouterLink to="/self-service/login" class="text-blue-600 hover:text-blue-700 font-medium">Sign in</RouterLink>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { RouterLink } from 'vue-router'

const loading = ref(false)
const errorMsg = ref('')
const submitted = ref(false)

const form = reactive({
  directoryId: '',
  realmId: '',
  givenName: '',
  sn: '',
  uid: '',
  mail: '',
  telephoneNumber: '',
  justification: '',
})

// Mockup data
const directories = ref([
  { id: 'dir-1', displayName: 'Corporate LDAP' },
])

const realms = ref([
  { id: 'realm-1', name: 'Employees' },
  { id: 'realm-2', name: 'Contractors' },
])

async function handleSubmit() {
  errorMsg.value = ''
  loading.value = true
  try {
    // Mockup — would call registerSubmit(form)
    await new Promise(resolve => setTimeout(resolve, 1000))
    submitted.value = true
  } catch {
    errorMsg.value = 'Registration failed. Please try again.'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary   { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700; }
.btn-secondary { @apply px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm font-medium hover:bg-gray-50; }
</style>
