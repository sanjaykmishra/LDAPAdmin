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
          <optgroup v-for="dir in dirs" :key="dir.id" :label="dir.displayName">
            <option v-if="!dir.realms.length" :value="`dir:${dir.id}`">(no realms)</option>
            <option v-for="realm in dir.realms" :key="realm.id" :value="`realm:${realm.id}`">
              {{ realm.name }}
            </option>
          </optgroup>
        </select>
      </div>

      <!-- Navigation -->
      <nav class="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
        <template v-if="currentDirId">
          <RouterLink :to="`/directories/${currentDirId}/users`" class="nav-item">
            <span class="icon">👤</span> Users
          </RouterLink>
          <RouterLink :to="`/directories/${currentDirId}/groups`" class="nav-item">
            <span class="icon">👥</span> Groups
          </RouterLink>
          <RouterLink :to="`/directories/${currentDirId}/audit`" class="nav-item">
            <span class="icon">📋</span> Audit Log
          </RouterLink>
          <RouterLink :to="`/directories/${currentDirId}/bulk`" class="nav-item">
            <span class="icon">📤</span> Bulk Import/Export
          </RouterLink>
          <RouterLink :to="`/directories/${currentDirId}/reports`" class="nav-item">
            <span class="icon">📊</span> Reports
          </RouterLink>
        </template>

        <div class="border-t border-gray-700 my-2" />

        <RouterLink v-if="!auth.isSuperadmin" to="/settings" class="nav-item">
          <span class="icon">⚙️</span> Settings
        </RouterLink>

        <template v-if="auth.isSuperadmin">
          <RouterLink to="/superadmin/admins" class="nav-item">
            <span class="icon">👤</span> Accounts
          </RouterLink>
          <RouterLink to="/superadmin/directories" class="nav-item">
            <span class="icon">🗄</span> Directories
          </RouterLink>
          <RouterLink v-if="currentDirId" :to="`/directories/${currentDirId}/realms`" class="nav-item">
            <span class="icon">🏛</span> Realms
          </RouterLink>
          <RouterLink to="/superadmin/audit-sources" class="nav-item">
            <span class="icon">📋</span> Audit Sources
          </RouterLink>
          <RouterLink to="/superadmin/user-forms" class="nav-item">
            <span class="icon">📝</span> User Forms
          </RouterLink>
          <RouterLink to="/superadmin/schema" class="nav-item">
            <span class="icon">🔍</span> Schema Browser
          </RouterLink>
          <RouterLink to="/settings" class="nav-item">
            <span class="icon">⚙️</span> Settings
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
import { listDirectories } from '@/api/directories'
import { listRealms } from '@/api/realms'

const auth   = useAuthStore()
const router = useRouter()
const route  = useRoute()

const dirs        = ref([])   // directories with nested .realms arrays
const pickerValue = ref('')   // "realm:<id>" or "dir:<id>"

// Parse the picker value into a directoryId
const currentDirId = computed(() => {
  const v = pickerValue.value
  if (!v) return ''
  if (v.startsWith('dir:')) return v.slice(4)
  if (v.startsWith('realm:')) {
    const realmId = v.slice(6)
    for (const dir of dirs.value) {
      if (dir.realms?.some(r => r.id === realmId)) return dir.id
    }
  }
  return ''
})

// Build a picker value for a given directory (prefer first realm, fallback to dir)
function pickerValueForDir(dir) {
  return dir.realms?.length ? `realm:${dir.realms[0].id}` : `dir:${dir.id}`
}

// Load directories and their realms for the sidebar picker
onMounted(async () => {
  try {
    const { data: directories } = await listDirectories()
    const results = await Promise.all(
      directories.map(async (dir) => {
        try {
          const { data: realms } = await listRealms(dir.id)
          return { ...dir, realms }
        } catch {
          return { ...dir, realms: [] }
        }
      })
    )
    dirs.value = results

    // If currently on a directory-scoped route, select matching entry
    const routeDirId = route.params.dirId
    if (routeDirId) {
      const dir = results.find(d => d.id === routeDirId)
      if (dir) pickerValue.value = pickerValueForDir(dir)
    } else if (results.length) {
      // Auto-select first directory/realm
      pickerValue.value = pickerValueForDir(results[0])
    }
  } catch { /* silently ignore */ }
})

// Keep picker in sync when route dirId changes externally
watch(() => route.params.dirId, (dirId) => {
  if (!dirId) return
  if (currentDirId.value === dirId) return
  const dir = dirs.value.find(d => d.id === dirId)
  if (dir) pickerValue.value = pickerValueForDir(dir)
})

// Navigate when user picks a different entry from the dropdown
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
  @apply flex items-center gap-2 px-3 py-2 rounded-lg text-sm text-gray-300 hover:bg-gray-800 hover:text-white transition-colors;
}
.nav-item.router-link-active {
  @apply bg-gray-800 text-white;
}
.icon { @apply text-base; }
</style>
