<template>
  <div class="flex h-screen bg-gray-100 overflow-hidden">
    <!-- No-profiles modal for admin users -->
    <Teleport to="body">
      <div v-if="showNoProfiles" class="fixed inset-0 z-40 flex items-center justify-center bg-black/40">
        <div class="bg-white rounded-xl shadow-xl max-w-md w-full mx-4 p-6">
          <h3 class="text-lg font-semibold text-gray-900 mb-2">No Profiles Assigned</h3>
          <p class="text-sm text-gray-600 mb-6">
            There are no provisioning profiles assigned to your account. Please contact your administrator to request access.
          </p>
          <div class="flex justify-end">
            <button
              @click="handleNoProfilesOk"
              class="px-4 py-2 text-sm rounded-lg text-white font-medium"
              :style="{ backgroundColor: settings.primaryColour }"
            >OK</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Sidebar -->
    <aside :class="['text-white flex flex-col shrink-0 transition-all duration-200', collapsed ? 'w-14' : 'w-60']"
           :style="{ backgroundColor: settings.secondaryColour }">
      <!-- Logo + collapse toggle -->
      <div :class="['px-3 py-4 border-b border-white/15 flex items-center', collapsed ? 'justify-center' : 'justify-between']">
        <span v-if="!collapsed" class="text-lg font-bold tracking-tight pl-2">{{ settings.appName }}</span>
        <button @click="collapsed = !collapsed" class="p-1 rounded hover:bg-white/10 text-white/60 hover:text-white transition-colors" :title="collapsed ? 'Expand sidebar' : 'Collapse sidebar'">
          <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path v-if="collapsed" stroke-linecap="round" stroke-linejoin="round" d="M11.25 4.5l7.5 7.5-7.5 7.5m-6-15l7.5 7.5-7.5 7.5" />
            <path v-else stroke-linecap="round" stroke-linejoin="round" d="M18.75 19.5l-7.5-7.5 7.5-7.5m-6 15L5.25 12l7.5-7.5" />
          </svg>
        </button>
      </div>

      <!-- Profile picker (admin users only) -->
      <div v-if="!auth.isSuperadmin && !collapsed" class="px-3 py-3 border-b border-white/15">
        <label class="text-xs text-gray-400 uppercase tracking-wider mb-1 block">Profile</label>
        <select
          v-model="pickerValue"
          class="w-full bg-white/10 border border-white/20 text-white rounded px-2 py-1 text-sm"
        >
          <option value="">— select —</option>
          <option v-for="p in profiles" :key="p.id" :value="p.id">
            {{ p.name }}
          </option>
        </select>
      </div>

      <!-- Navigation -->
      <nav :class="['flex-1 py-4 space-y-1 overflow-y-auto', collapsed ? 'px-1 nav-collapsed' : 'px-3']">
        <!-- Admin navigation (directory-scoped) -->
        <template v-if="!auth.isSuperadmin">
          <template v-if="currentDirId">
            <RouterLink :to="{ path: `/directories/${currentDirId}/users` }" class="nav-item">
              <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="10" cy="6" r="3.25"/><path d="M3.5 17.5c0-3.59 2.91-6.5 6.5-6.5s6.5 2.91 6.5 6.5"/></svg>
              <span v-if="!collapsed">Users</span>
            </RouterLink>
            <RouterLink :to="{ path: `/directories/${currentDirId}/groups` }" class="nav-item">
              <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="7.5" cy="6" r="2.75"/><circle cx="13.5" cy="6" r="2.75"/><path d="M1.5 17c0-3.04 2.46-5.5 5.5-5.5 1.26 0 2.42.42 3.35 1.14M12 11.64A5.48 5.48 0 0 1 18.5 17"/></svg>
              <span v-if="!collapsed">Groups</span>
            </RouterLink>
            <RouterLink :to="{ path: `/directories/${currentDirId}/approvals` }" class="nav-item">
              <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M10 2l7 4v5c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6l7-4z"/><path d="M7 10l2 2 4-4"/></svg>
              <span v-if="!collapsed">Approvals</span>
              <span v-if="pendingCount > 0 && !collapsed" class="ml-auto bg-red-500 text-white text-xs rounded-full w-5 h-5 flex items-center justify-center">{{ pendingCount }}</span>
            </RouterLink>
            <RouterLink :to="{ path: `/directories/${currentDirId}/playbooks` }" class="nav-item">
              <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h12M4 8h12M4 12h8M4 16h6"/><path d="M15 12l2 2-2 2"/></svg>
              <span v-if="!collapsed">Playbooks</span>
            </RouterLink>
            <RouterLink :to="{ path: `/directories/${currentDirId}/reports` }" class="nav-item">
              <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 16V10M10 16V4M15 16v-4"/></svg>
              <span v-if="!collapsed">Reports</span>
            </RouterLink>
            <RouterLink :to="{ path: `/directories/${currentDirId}/bulk` }" class="nav-item">
              <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M10 2v12M10 2l4 4M10 2 6 6"/><path d="M3 13v3a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-3"/></svg>
              <span v-if="!collapsed">Bulk Import/Export</span>
            </RouterLink>
            <RouterLink :to="{ path: `/directories/${currentDirId}/access-reviews` }" class="nav-item">
              <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M9 2H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9"/><path d="M9 11l8-8"/><path d="M14 3h3v3"/></svg>
              <span v-if="!collapsed">Access Reviews</span>
              <span v-if="activeReviewCount > 0 && !collapsed" class="ml-auto bg-blue-500 text-white text-xs rounded-full w-5 h-5 flex items-center justify-center">{{ activeReviewCount }}</span>
            </RouterLink>
            <RouterLink :to="{ path: `/directories/${currentDirId}/audit` }" class="nav-item">
              <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="2" width="14" height="16" rx="2"/><path d="M7 6h6M7 10h6M7 14h3"/></svg>
              <span v-if="!collapsed">Audit Log</span>
            </RouterLink>
          </template>

        </template>

        <!-- Superadmin navigation -->
        <template v-if="auth.isSuperadmin">
          <RouterLink to="/superadmin/dashboard" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="2" width="7" height="7" rx="1.5"/><rect x="11" y="2" width="7" height="7" rx="1.5"/><rect x="2" y="11" width="7" height="7" rx="1.5"/><rect x="11" y="11" width="7" height="7" rx="1.5"/></svg>
            <span v-if="!collapsed">Dashboard</span>
          </RouterLink>

          <!-- Explore -->
          <button v-if="!collapsed" @click="sections.explore = !sections.explore" class="nav-section-toggle">
            Explore
            <svg :class="['w-3 h-3 transition-transform', sections.explore ? '' : '-rotate-90']" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M19.5 8.25l-7.5 7.5-7.5-7.5" /></svg>
          </button>
          <template v-if="sections.explore || collapsed">
          <RouterLink to="/superadmin/directory-browser" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 4h5l2 2h7a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z"/><path d="M8 10h4M10 8v4"/></svg>
            <span v-if="!collapsed">Directory Browser</span>
          </RouterLink>
          <RouterLink to="/superadmin/directory-search" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="8.5" cy="8.5" r="5.5"/><path d="M14 14l4 4"/></svg>
            <span v-if="!collapsed">Directory Search</span>
          </RouterLink>
          <RouterLink to="/superadmin/directory-schema" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="8.5" cy="8.5" r="5.5"/><path d="M18 18l-4-4"/></svg>
            <span v-if="!collapsed">Schema Browser</span>
          </RouterLink>
          </template>

          <!-- Report -->
          <button v-if="!collapsed" @click="sections.report = !sections.report" class="nav-section-toggle">
            Report
            <svg :class="['w-3 h-3 transition-transform', sections.report ? '' : '-rotate-90']" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M19.5 8.25l-7.5 7.5-7.5-7.5" /></svg>
          </button>
          <template v-if="sections.report || collapsed">
          <RouterLink to="/superadmin/reports" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 16V10M10 16V4M15 16v-4"/></svg>
            <span v-if="!collapsed">Operational Reports</span>
          </RouterLink>
          <RouterLink to="/superadmin/audit-reports" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M10 2l7 4v5c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6l7-4z"/><path d="M7 10l2 2 4-4"/></svg>
            <span v-if="!collapsed">Compliance Reports</span>
          </RouterLink>
          <RouterLink to="/superadmin/auditor-links" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 13.5V17a1.5 1.5 0 001.5 1.5h11A1.5 1.5 0 0017 17v-3.5M10 2v11M10 2l4 4M10 2L6 6"/></svg>
            <span v-if="!collapsed">Auditor Links</span>
          </RouterLink>
          </template>

          <!-- Configure -->
          <button v-if="!collapsed" @click="sections.configure = !sections.configure" class="nav-section-toggle">
            Configure
            <svg :class="['w-3 h-3 transition-transform', sections.configure ? '' : '-rotate-90']" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M19.5 8.25l-7.5 7.5-7.5-7.5" /></svg>
          </button>
          <template v-if="sections.configure || collapsed">
          <RouterLink to="/superadmin/directories" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M2.5 5a2 2 0 0 1 2-2h11a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2V5z"/><path d="M6.5 3v14"/><path d="M2.5 7h4M2.5 11h4"/></svg>
            <span v-if="!collapsed">Directory Connections</span>
          </RouterLink>
          <RouterLink to="/superadmin/hr" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="7" cy="5" r="2.5"/><path d="M2 14c0-2.76 2.24-5 5-5s5 2.24 5 5"/><path d="M14 6h4M14 9h3M14 12h2"/></svg>
            <span v-if="!collapsed">HR Integration</span>
          </RouterLink>
          <RouterLink to="/superadmin/audit-sources" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M10 2a8 8 0 1 0 0 16 8 8 0 0 0 0-16z"/><path d="M10 6v4l2.5 2.5"/></svg>
            <span v-if="!collapsed">Audit Sources</span>
          </RouterLink>
          <RouterLink to="/superadmin/access-reviews" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M9 2H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9"/><path d="M9 11l8-8"/><path d="M14 3h3v3"/></svg>
            <span v-if="!collapsed">Access Reviews</span>
          </RouterLink>
          <RouterLink to="/superadmin/sod-policies" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h5v5H4zM11 4h5v5h-5zM4 11h5v5H4z"/><path d="M11 11h5v5h-5"/><path d="M11 11l5 5M16 11l-5 5"/></svg>
            <span v-if="!collapsed">SoD Policy</span>
          </RouterLink>
          <RouterLink to="/superadmin/access-drift" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 17l4-8 4 4 6-10"/><path d="M14 3h4v4"/></svg>
            <span v-if="!collapsed">Access Drift Policy</span>
          </RouterLink>
          <RouterLink to="/superadmin/profiles" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="2" width="14" height="16" rx="2"/><path d="M7 6h6M7 10h6M7 14h3"/><path d="M14 13l1.5 1.5 3-3"/></svg>
            <span v-if="!collapsed">Provisioning Profiles</span>
          </RouterLink>
          <RouterLink to="/superadmin/playbooks" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h12M4 8h12M4 12h8M4 16h6"/><path d="M15 12l2 2-2 2"/></svg>
            <span v-if="!collapsed">Lifecycle Playbooks</span>
          </RouterLink>
          <RouterLink to="/settings" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="10" cy="10" r="2.5"/><path d="M10 1.5v2M10 16.5v2M18.5 10h-2M3.5 10h-2M16 4l-1.4 1.4M5.4 14.6 4 16M16 16l-1.4-1.4M5.4 5.4 4 4"/></svg>
            <span v-if="!collapsed">Application Settings</span>
          </RouterLink>
          <RouterLink to="/superadmin/admins" class="nav-item">
            <svg class="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="10" cy="5.5" r="3.25"/><path d="M3.5 18c0-3.59 2.91-6.5 6.5-6.5s6.5 2.91 6.5 6.5"/><path d="M13.5 2.5l1 2 2 .5-1.5 1.5.5 2-2-1.25L11.5 8.5l.5-2L10.5 5l2-.5 1-2z"/></svg>
            <span v-if="!collapsed">Application Accounts</span>
          </RouterLink>
          </template>
        </template>
      </nav>

      <!-- User info / logout -->
      <div :class="['px-3 py-3 border-t border-white/15 flex items-center gap-2', collapsed ? 'justify-center' : 'justify-between']">
        <button @click="showPreferences = true" class="text-sm truncate text-left hover:text-white/90 transition-colors" :title="auth.username">
          <template v-if="!collapsed">
            <p class="font-medium">{{ auth.username }}</p>
            <p class="text-xs text-gray-400">{{ auth.isSuperadmin ? 'Superadmin' : 'Admin' }}</p>
          </template>
          <div v-else class="w-7 h-7 rounded-full bg-white/20 flex items-center justify-center text-xs font-bold">{{ (auth.username || '?')[0].toUpperCase() }}</div>
        </button>
        <div v-if="!collapsed" class="flex items-center gap-1">
          <NotificationBell />
          <button @click="handleLogout" class="text-gray-400 hover:text-white text-xs">Logout</button>
        </div>
      </div>
    </aside>

    <!-- Main content -->
    <main class="flex-1 overflow-y-auto">
      <BreadcrumbNav />
      <RouterView v-slot="{ Component }">
        <Transition name="page-fade" mode="out-in">
          <component :is="Component" :key="$route.path" />
        </Transition>
      </RouterView>
    </main>

    <!-- Keyboard shortcuts help -->
    <KeyboardShortcutsHelp v-model="showShortcutsHelp" />

    <!-- User preferences dialog -->
    <UserPreferencesDialog v-if="showPreferences" @close="showPreferences = false" />

    <!-- Command palette (Cmd+K) -->
    <CommandPalette />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { RouterLink, RouterView, useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { usePermissions } from '@/composables/usePermissions'
import { useSettingsStore } from '@/stores/settings'
import { useKeyboardShortcuts } from '@/composables/useKeyboardShortcuts'
import { myProfiles } from '@/api/auth'
import { countPendingApprovals } from '@/api/approvals'
import { listCampaigns } from '@/api/accessReviews'
import KeyboardShortcutsHelp from '@/components/KeyboardShortcutsHelp.vue'
import UserPreferencesDialog from '@/components/UserPreferencesDialog.vue'
import NotificationBell from '@/components/NotificationBell.vue'
import BreadcrumbNav from '@/components/BreadcrumbNav.vue'
import CommandPalette from '@/components/CommandPalette.vue'

const auth     = useAuthStore()
const { hasFeature } = usePermissions()
const settings = useSettingsStore()

onMounted(() => settings.init())
const router = useRouter()
const route  = useRoute()

const profiles       = ref([])   // flat list of authorized profiles (admin only)
const pickerValue    = ref('')   // profile id
const showNoProfiles = ref(false)
const pendingCount   = ref(0)
const activeReviewCount = ref(0)
const collapsed      = ref(false)
const sections       = ref({ explore: true, report: true, configure: true })
const showPreferences = ref(false)

// Derive the directory id from the selected profile
const currentDirId = computed(() => {
  if (!pickerValue.value) return ''
  const profile = profiles.value.find(p => p.id === pickerValue.value)
  return profile?.directoryId || ''
})

// Keyboard shortcuts
const { showHelp: showShortcutsHelp } = useKeyboardShortcuts({
  dirId: () => currentDirId.value || route.params.dirId,
})

// Load profiles for admin users; superadmins don't need the picker
onMounted(async () => {
  if (auth.isSuperadmin) return

  try {
    const { data } = await myProfiles()
    profiles.value = data

    if (!data.length) {
      showNoProfiles.value = true
      return
    }

    // If currently on a directory-scoped route, select the matching profile
    const routeDirId = route.params.dirId
    if (routeDirId) {
      const match = data.find(p => p.directoryId === routeDirId)
      if (match) pickerValue.value = match.id
    }

    // Auto-select first profile if nothing matched
    if (!pickerValue.value && data.length) {
      pickerValue.value = data[0].id
    }
  } catch (e) {
    console.warn('Failed to load profiles:', e)
  }
})

// Keep picker in sync when route dirId changes externally
watch(() => route.params.dirId, (dirId) => {
  if (!dirId) return
  if (currentDirId.value === dirId) return
  const match = profiles.value.find(p => p.directoryId === dirId)
  if (match) pickerValue.value = match.id
})

// Navigate when user picks a different profile
const dirSections = ['users', 'groups', 'audit', 'bulk', 'reports', 'hrConnection', 'hrEmployees', 'approvals', 'accessReviews']
watch(currentDirId, (newDirId) => {
  if (!newDirId || newDirId === route.params.dirId) return
  const section = dirSections.includes(route.name) ? route.name : 'users'
  router.push({ path: `/directories/${newDirId}/${section}` })
})

// Load pending approval count for the current directory
watch(currentDirId, async (newDirId) => {
  if (!newDirId) { pendingCount.value = 0; return }
  try {
    const { data } = await countPendingApprovals(newDirId)
    pendingCount.value = data.pending || 0
  } catch { pendingCount.value = 0 }
}, { immediate: true })

// Load active review count for badge
watch(currentDirId, async (newDirId) => {
  if (!newDirId) { activeReviewCount.value = 0; return }
  try {
    const { data } = await listCampaigns(newDirId, { size: 1, status: 'ACTIVE' })
    activeReviewCount.value = data.totalElements || 0
  } catch { activeReviewCount.value = 0 }
}, { immediate: true })

async function handleNoProfilesOk() {
  showNoProfiles.value = false
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
.nav-collapsed .nav-item {
  @apply justify-center px-0 gap-0;
}
.nav-item.router-link-active {
  @apply bg-white/10 text-white;
}
.nav-icon { @apply w-5 h-5 shrink-0; }
.nav-header { @apply text-xs text-gray-400 uppercase tracking-wider mt-4 mb-1 px-3; }
.nav-section-toggle {
  @apply flex items-center justify-between w-full text-xs text-gray-400 uppercase tracking-wider mt-4 mb-1 px-3 py-1 rounded hover:text-white/70 transition-colors cursor-pointer;
}
</style>
