<template>
  <div class="flex h-screen bg-gray-100 overflow-hidden">
    <!-- Sidebar -->
    <aside class="w-60 bg-gray-900 text-white flex flex-col shrink-0">
      <!-- Logo -->
      <div class="px-5 py-4 border-b border-gray-700">
        <span class="text-lg font-bold tracking-tight">LDAP Admin</span>
      </div>

      <!-- Realm picker -->
      <div class="px-3 py-3 border-b border-gray-700">
        <label class="text-xs text-gray-400 uppercase tracking-wider mb-1 block">Realm</label>
        <select
          v-model="pickerValue"
          class="w-full bg-gray-800 border border-gray-600 text-white rounded px-2 py-1 text-sm"
        >
          <option value="">— select —</option>
          <option v-for="realm in realms" :key="realm.id" :value="realm.id">
            {{ realm.name }}
          </option>
        </select>
      </div>

      <!-- Navigation -->
      <nav class="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
        <template v-if="currentDirId">
          <RouterLink :to="`/directories/${currentDirId}/users`" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="10" cy="6" r="3.25"/><path d="M3.5 17.5c0-3.59 2.91-6.5 6.5-6.5s6.5 2.91 6.5 6.5"/></svg>
            Users
          </RouterLink>
          <RouterLink :to="`/directories/${currentDirId}/groups`" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="7.5" cy="6" r="2.75"/><circle cx="13.5" cy="6" r="2.75"/><path d="M1.5 17c0-3.04 2.46-5.5 5.5-5.5 1.26 0 2.42.42 3.35 1.14M12 11.64A5.48 5.48 0 0 1 18.5 17"/></svg>
            Groups
          </RouterLink>
          <RouterLink :to="`/directories/${currentDirId}/audit`" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="2" width="14" height="16" rx="2"/><path d="M7 6h6M7 10h6M7 14h3"/></svg>
            Audit Log
          </RouterLink>
          <RouterLink :to="`/directories/${currentDirId}/bulk`" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M10 2v12M10 2l4 4M10 2 6 6"/><path d="M3 13v3a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-3"/></svg>
            Bulk Import/Export
          </RouterLink>
          <RouterLink :to="`/directories/${currentDirId}/reports`" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 16V10M10 16V4M15 16v-4"/></svg>
            Reports
          </RouterLink>
        </template>

        <div class="border-t border-gray-700 my-2" />

        <RouterLink v-if="!auth.isSuperadmin" to="/settings" class="nav-item">
          <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="10" cy="10" r="2.5"/><path d="M10 1.5v2M10 16.5v2M18.5 10h-2M3.5 10h-2M16 4l-1.4 1.4M5.4 14.6 4 16M16 16l-1.4-1.4M5.4 5.4 4 4"/></svg>
          Settings
        </RouterLink>

        <template v-if="auth.isSuperadmin">
          <RouterLink to="/superadmin/admins" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="10" cy="5.5" r="3.25"/><path d="M3.5 18c0-3.59 2.91-6.5 6.5-6.5s6.5 2.91 6.5 6.5"/><path d="M13.5 2.5l1 2 2 .5-1.5 1.5.5 2-2-1.25L11.5 8.5l.5-2L10.5 5l2-.5 1-2z"/></svg>
            Accounts
          </RouterLink>
          <RouterLink to="/superadmin/directories" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M2.5 5a2 2 0 0 1 2-2h11a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2V5z"/><path d="M6.5 3v14"/><path d="M2.5 7h4M2.5 11h4"/></svg>
            Directories
          </RouterLink>
          <RouterLink v-if="currentDirId" :to="`/directories/${currentDirId}/realms`" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 17V5l7-3 7 3v12"/><path d="M3 17h14"/><path d="M7 9h2v4H7zM11 9h2v4h-2z"/><path d="M10 17v-4"/></svg>
            Realms
          </RouterLink>
          <RouterLink to="/superadmin/audit-sources" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M10 2a8 8 0 1 0 0 16 8 8 0 0 0 0-16z"/><path d="M10 6v4l2.5 2.5"/></svg>
            Audit Sources
          </RouterLink>
          <RouterLink to="/superadmin/user-forms" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="2" width="14" height="16" rx="2"/><path d="M7 6h6M7 10h6M7 14h3"/><path d="M14 13l1.5 1.5 3-3"/></svg>
            User Forms
          </RouterLink>
          <RouterLink to="/superadmin/schema" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="8.5" cy="8.5" r="5.5"/><path d="M18 18l-4-4"/></svg>
            Schema Browser
          </RouterLink>
          <RouterLink to="/settings" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="10" cy="10" r="2.5"/><path d="M10 1.5v2M10 16.5v2M18.5 10h-2M3.5 10h-2M16 4l-1.4 1.4M5.4 14.6 4 16M16 16l-1.4-1.4M5.4 5.4 4 4"/></svg>
            Settings
          </RouterLink>
        </template>
      </nav>

      <!-- User info / logout -->
      <div class="px-4 py-3 border-t border-gray-700 flex items-center justify-between">
        <div class="text-sm truncate">
          <p class="font-medium">{{ auth.username }}</p>
          <p class="text-xs text-gray-400">{{ auth.isSuperadmin ? 'Superadmin' : 'Admin' }}</p>
        </div>
        <button @click="handleLogout" class="text-gray-400 hover:text-white text-xs ml-2">Logout</button>
      </div>
    </aside>

    <!-- Main content -->
    <main class="flex-1 overflow-y-auto">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { RouterLink, RouterView, useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { myRealms } from '@/api/auth'

const auth   = useAuthStore()
const router = useRouter()
const route  = useRoute()

const realms      = ref([])   // flat list of authorized realms
const pickerValue = ref('')   // realm id

// Derive the directory id from the selected realm
const currentDirId = computed(() => {
  if (!pickerValue.value) return ''
  const realm = realms.value.find(r => r.id === pickerValue.value)
  return realm?.directoryId || ''
})

// Load only the realms the current user is authorized for
onMounted(async () => {
  try {
    const { data } = await myRealms()
    realms.value = data

    // If currently on a directory-scoped route, select the matching realm
    const routeDirId = route.params.dirId
    if (routeDirId) {
      const match = data.find(r => r.directoryId === routeDirId)
      if (match) pickerValue.value = match.id
    }

    // Auto-select first realm if nothing matched
    if (!pickerValue.value && data.length) {
      pickerValue.value = data[0].id
    }
  } catch { /* silently ignore */ }
})

// Keep picker in sync when route dirId changes externally
watch(() => route.params.dirId, (dirId) => {
  if (!dirId) return
  if (currentDirId.value === dirId) return
  const match = realms.value.find(r => r.directoryId === dirId)
  if (match) pickerValue.value = match.id
})

// Navigate when user picks a different realm
const dirSections = ['users', 'groups', 'audit', 'bulk', 'reports', 'realms']
watch(currentDirId, (newDirId) => {
  if (!newDirId || newDirId === route.params.dirId) return
  const section = dirSections.includes(route.name) ? route.name : 'users'
  router.push(`/directories/${newDirId}/${section}`)
})

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
@reference "tailwindcss";
.nav-item {
  @apply flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm text-gray-300 hover:bg-gray-800 hover:text-white transition-colors;
}
.nav-item.router-link-active {
  @apply bg-gray-800 text-white;
}
.nav-icon { @apply w-5 h-5 shrink-0; }
</style>
