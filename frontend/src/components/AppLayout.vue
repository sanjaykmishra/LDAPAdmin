<template>
  <div class="flex h-screen bg-gray-100 overflow-hidden">
    <!-- Sidebar -->
    <aside class="w-60 bg-gray-900 text-white flex flex-col shrink-0">
      <!-- Logo -->
      <div class="px-5 py-4 border-b border-gray-700">
        <span class="text-lg font-bold tracking-tight">LDAP Admin</span>
      </div>

      <!-- Directory picker (non-superadmin) -->
      <div v-if="!auth.isSuperadmin" class="px-3 py-3 border-b border-gray-700">
        <label class="text-xs text-gray-400 uppercase tracking-wider mb-1 block">Directory</label>
        <select
          v-model="selectedDirId"
          class="w-full bg-gray-800 border border-gray-600 text-white rounded px-2 py-1 text-sm"
        >
          <option value="">— select —</option>
          <option v-for="d in dirs" :key="d.id" :value="d.id">{{ d.name }}</option>
        </select>
      </div>

      <!-- Navigation -->
      <nav class="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
        <RouterLink to="/directories" class="nav-item">
          <span class="icon">🗂</span> Directories
        </RouterLink>

        <template v-if="selectedDirId">
          <RouterLink :to="`/directories/${selectedDirId}/users`" class="nav-item">
            <span class="icon">👤</span> Users
          </RouterLink>
          <RouterLink :to="`/directories/${selectedDirId}/groups`" class="nav-item">
            <span class="icon">👥</span> Groups
          </RouterLink>
          <RouterLink :to="`/directories/${selectedDirId}/audit`" class="nav-item">
            <span class="icon">📋</span> Audit Log
          </RouterLink>
          <RouterLink :to="`/directories/${selectedDirId}/bulk`" class="nav-item">
            <span class="icon">📤</span> Bulk Import/Export
          </RouterLink>
          <RouterLink :to="`/directories/${selectedDirId}/reports`" class="nav-item">
            <span class="icon">📊</span> Reports
          </RouterLink>
          <RouterLink :to="`/directories/${selectedDirId}/profiles`" class="nav-item">
            <span class="icon">🎨</span> Attr Profiles
          </RouterLink>
          <RouterLink :to="`/directories/${selectedDirId}/schema`" class="nav-item">
            <span class="icon">🔍</span> Schema
          </RouterLink>
        </template>

        <div class="border-t border-gray-700 my-2" />

        <RouterLink v-if="!auth.isSuperadmin" to="/settings" class="nav-item">
          <span class="icon">⚙️</span> Settings
        </RouterLink>

        <template v-if="auth.isSuperadmin">
          <RouterLink to="/superadmin" class="nav-item">
            <span class="icon">🛡</span> Superadmins
          </RouterLink>
          <RouterLink to="/superadmin/tenants" class="nav-item">
            <span class="icon">🏢</span> Tenants
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
import { ref, watch, onMounted } from 'vue'
import { RouterLink, RouterView, useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { listTenants } from '@/api/superadmin'
import client from '@/api/client'

const auth   = useAuthStore()
const router = useRouter()
const route  = useRoute()

const dirs          = ref([])
const selectedDirId = ref(route.params.dirId || '')

// Load directories for the current tenant (admins only)
onMounted(async () => {
  if (auth.isSuperadmin) return
  try {
    const tenantId = auth.principal?.tenantId
    if (!tenantId) return
    const { data } = await client.get(`/admin/tenants/${tenantId}/directories`)
    dirs.value = data
    if (!selectedDirId.value && data.length) {
      selectedDirId.value = data[0].id
    }
  } catch { /* silently ignore */ }
})

// Keep dirId in sync with route params
watch(() => route.params.dirId, id => {
  if (id) selectedDirId.value = id
})

// Navigate when user picks a different directory from the dropdown
const dirSections = ['users', 'groups', 'audit', 'bulk', 'reports', 'profiles', 'schema']
watch(selectedDirId, (newId) => {
  if (!newId || newId === route.params.dirId) return
  const section = dirSections.includes(route.name) ? route.name : 'users'
  router.push(`/directories/${newId}/${section}`)
})

function handleLogout() {
  auth.logout()
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
