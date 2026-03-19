<template>
  <div class="flex h-screen bg-gray-100 overflow-hidden">
    <!-- No-realms modal for admin users -->
    <Teleport to="body">
      <div v-if="showNoRealms" class="fixed inset-0 z-40 flex items-center justify-center bg-black/40">
        <div class="bg-white rounded-xl shadow-xl max-w-md w-full mx-4 p-6">
          <h3 class="text-lg font-semibold text-gray-900 mb-2">No Realms Assigned</h3>
          <p class="text-sm text-gray-600 mb-6">
            There are no realms assigned to your account. Please contact your administrator to request access.
          </p>
          <div class="flex justify-end">
            <button
              @click="handleNoRealmsOk"
              class="px-4 py-2 text-sm rounded-lg text-white font-medium"
              :style="{ backgroundColor: settings.primaryColour }"
            >OK</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Sidebar -->
    <aside class="w-60 text-white flex flex-col shrink-0" :style="{ backgroundColor: settings.secondaryColour }">
      <!-- Logo -->
      <div class="px-5 py-4 border-b border-white/15">
        <span class="text-lg font-bold tracking-tight">{{ settings.appName }}</span>
      </div>

      <!-- Realm picker (admin users only) -->
      <div v-if="!auth.isSuperadmin" class="px-3 py-3 border-b border-white/15">
        <label class="text-xs text-gray-400 uppercase tracking-wider mb-1 block">Realm</label>
        <select
          v-model="pickerValue"
          class="w-full bg-white/10 border border-white/20 text-white rounded px-2 py-1 text-sm"
        >
          <option value="">— select —</option>
          <option v-for="realm in realms" :key="realm.id" :value="realm.id">
            {{ realm.name }}
          </option>
        </select>
      </div>

      <!-- Navigation -->
      <nav class="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
        <!-- Admin navigation (directory-scoped) -->
        <template v-if="!auth.isSuperadmin">
          <template v-if="currentDirId">
            <RouterLink :to="{ path: `/directories/${currentDirId}/users`, query: { realmId: pickerValue } }" class="nav-item">
              <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="10" cy="6" r="3.25"/><path d="M3.5 17.5c0-3.59 2.91-6.5 6.5-6.5s6.5 2.91 6.5 6.5"/></svg>
              Users
            </RouterLink>
            <RouterLink :to="{ path: `/directories/${currentDirId}/groups`, query: { realmId: pickerValue } }" class="nav-item">
              <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="7.5" cy="6" r="2.75"/><circle cx="13.5" cy="6" r="2.75"/><path d="M1.5 17c0-3.04 2.46-5.5 5.5-5.5 1.26 0 2.42.42 3.35 1.14M12 11.64A5.48 5.48 0 0 1 18.5 17"/></svg>
              Groups
            </RouterLink>
            <RouterLink :to="{ path: `/directories/${currentDirId}/audit`, query: { realmId: pickerValue } }" class="nav-item">
              <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="2" width="14" height="16" rx="2"/><path d="M7 6h6M7 10h6M7 14h3"/></svg>
              Audit Log
            </RouterLink>
            <RouterLink :to="{ path: `/directories/${currentDirId}/bulk`, query: { realmId: pickerValue } }" class="nav-item">
              <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M10 2v12M10 2l4 4M10 2 6 6"/><path d="M3 13v3a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-3"/></svg>
              Bulk Import/Export
            </RouterLink>
            <RouterLink :to="{ path: `/directories/${currentDirId}/reports`, query: { realmId: pickerValue } }" class="nav-item">
              <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 16V10M10 16V4M15 16v-4"/></svg>
              Reports
            </RouterLink>
          </template>

        </template>

        <!-- Superadmin navigation -->
        <template v-if="auth.isSuperadmin">
          <RouterLink to="/superadmin/admins" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="10" cy="5.5" r="3.25"/><path d="M3.5 18c0-3.59 2.91-6.5 6.5-6.5s6.5 2.91 6.5 6.5"/><path d="M13.5 2.5l1 2 2 .5-1.5 1.5.5 2-2-1.25L11.5 8.5l.5-2L10.5 5l2-.5 1-2z"/></svg>
            Accounts
          </RouterLink>
          <RouterLink to="/superadmin/directories" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M2.5 5a2 2 0 0 1 2-2h11a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2V5z"/><path d="M6.5 3v14"/><path d="M2.5 7h4M2.5 11h4"/></svg>
            Directories
          </RouterLink>
          <RouterLink to="/superadmin/realms" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 17V5l7-3 7 3v12"/><path d="M3 17h14"/><path d="M7 9h2v4H7zM11 9h2v4h-2z"/><path d="M10 17v-4"/></svg>
            Realms
          </RouterLink>
          <RouterLink to="/superadmin/user-templates" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="2" width="14" height="16" rx="2"/><path d="M7 6h6M7 10h6M7 14h3"/><path d="M14 13l1.5 1.5 3-3"/></svg>
            User Templates
          </RouterLink>
          <RouterLink to="/superadmin/schema" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="8.5" cy="8.5" r="5.5"/><path d="M18 18l-4-4"/></svg>
            Schema Browser
          </RouterLink>
          <RouterLink to="/superadmin/browser" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 4h5l2 2h7a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z"/><path d="M8 10h4M10 8v4"/></svg>
            Directory Browser
          </RouterLink>
          <RouterLink to="/superadmin/search" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="8.5" cy="8.5" r="5.5"/><path d="M14 14l4 4"/></svg>
            LDAP Search
          </RouterLink>
          <RouterLink to="/superadmin/integrity" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M10 2l7 4v5c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6l7-4z"/><path d="M7 10l2 2 4-4"/></svg>
            Integrity Check
          </RouterLink>
          <RouterLink to="/superadmin/audit-sources" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M10 2a8 8 0 1 0 0 16 8 8 0 0 0 0-16z"/><path d="M10 6v4l2.5 2.5"/></svg>
            Audit Sources
          </RouterLink>
          <RouterLink to="/settings" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="10" cy="10" r="2.5"/><path d="M10 1.5v2M10 16.5v2M18.5 10h-2M3.5 10h-2M16 4l-1.4 1.4M5.4 14.6 4 16M16 16l-1.4-1.4M5.4 5.4 4 4"/></svg>
            Settings
          </RouterLink>
        </template>
      </nav>

      <!-- User info / logout -->
      <div class="px-4 py-3 border-t border-white/15 flex items-center justify-between">
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
import { useSettingsStore } from '@/stores/settings'
import { myRealms } from '@/api/auth'

const auth     = useAuthStore()
const settings = useSettingsStore()

onMounted(() => settings.init())
const router = useRouter()
const route  = useRoute()

const realms       = ref([])   // flat list of authorized realms (admin only)
const pickerValue  = ref('')   // realm id
const showNoRealms = ref(false)

// Derive the directory id from the selected realm
const currentDirId = computed(() => {
  if (!pickerValue.value) return ''
  const realm = realms.value.find(r => r.id === pickerValue.value)
  return realm?.directoryId || ''
})

// Load realms for admin users; superadmins don't need the picker
onMounted(async () => {
  if (auth.isSuperadmin) return

  try {
    const { data } = await myRealms()
    realms.value = data

    if (!data.length) {
      showNoRealms.value = true
      return
    }

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
const dirSections = ['users', 'groups', 'audit', 'bulk', 'reports']
watch(currentDirId, (newDirId) => {
  if (!newDirId || newDirId === route.params.dirId) return
  const section = dirSections.includes(route.name) ? route.name : 'users'
  router.push({ path: `/directories/${newDirId}/${section}`, query: { realmId: pickerValue.value } })
})

async function handleNoRealmsOk() {
  showNoRealms.value = false
  await auth.logout()
  router.push('/login')
}

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
@reference "tailwindcss";
.nav-item {
  @apply flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm text-white/70 hover:bg-white/10 hover:text-white transition-colors;
}
.nav-item.router-link-active {
  @apply bg-white/10 text-white;
}
.nav-icon { @apply w-5 h-5 shrink-0; }
</style>
