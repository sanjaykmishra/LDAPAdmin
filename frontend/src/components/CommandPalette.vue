<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-50 flex items-start justify-center pt-[15vh]" @click.self="open = false">
      <div class="fixed inset-0 bg-black/40" @click="open = false" />
      <div class="relative w-full max-w-lg bg-white rounded-xl shadow-2xl border border-gray-200 overflow-hidden" @keydown.escape="open = false">
        <!-- Search input -->
        <div class="flex items-center gap-3 px-4 py-3 border-b border-gray-200">
          <svg class="w-5 h-5 text-gray-400 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
            <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
          </svg>
          <input ref="inputRef" v-model="query" type="text" placeholder="Search pages, actions..."
                 class="flex-1 text-sm outline-none bg-transparent placeholder-gray-400"
                 @keydown.down.prevent="moveDown" @keydown.up.prevent="moveUp"
                 @keydown.enter.prevent="selectCurrent" />
          <kbd class="text-[10px] text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded font-mono">ESC</kbd>
        </div>

        <!-- Results -->
        <div class="max-h-[300px] overflow-y-auto py-1" v-if="filtered.length > 0">
          <button v-for="(item, i) in filtered" :key="item.path"
                  @click="go(item)" @mouseenter="activeIndex = i"
                  :class="['w-full text-left px-4 py-2.5 flex items-center gap-3 transition-colors',
                    i === activeIndex ? 'bg-blue-50 text-blue-700' : 'text-gray-700 hover:bg-gray-50']">
            <span class="text-xs w-5 h-5 flex items-center justify-center rounded bg-gray-100 text-gray-500 shrink-0" v-html="item.icon || '&#9656;'" />
            <div class="min-w-0">
              <div class="text-sm font-medium truncate">{{ item.label }}</div>
              <div v-if="item.section" class="text-[10px] text-gray-400 truncate">{{ item.section }}</div>
            </div>
          </button>
        </div>
        <div v-else-if="query" class="px-4 py-6 text-center text-sm text-gray-400">No results for "{{ query }}"</div>

        <!-- Footer -->
        <div class="px-4 py-2 border-t border-gray-100 flex items-center gap-4 text-[10px] text-gray-400">
          <span><kbd class="bg-gray-100 px-1 rounded font-mono">↑↓</kbd> navigate</span>
          <span><kbd class="bg-gray-100 px-1 rounded font-mono">↵</kbd> select</span>
          <span><kbd class="bg-gray-100 px-1 rounded font-mono">esc</kbd> close</span>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const open = ref(false)
const query = ref('')
const activeIndex = ref(0)
const inputRef = ref(null)

// Build the command list based on user role
const commands = computed(() => {
  const dirId = route.params.dirId || ''
  const items = []

  if (auth.isSuperadmin) {
    items.push(
      { label: 'Dashboard', path: '/superadmin/dashboard', section: 'Overview' },
      { label: 'Directory Browser', path: '/superadmin/directory-browser', section: 'Explore' },
      { label: 'Directory Search', path: '/superadmin/directory-search', section: 'Explore' },
      { label: 'Schema Browser', path: '/superadmin/directory-schema', section: 'Explore' },
      { label: 'Operational Reports', path: '/superadmin/reports', section: 'Report' },
      { label: 'Compliance Reports', path: '/superadmin/audit-reports', section: 'Report' },
      { label: 'Auditor Links', path: '/superadmin/auditor-links', section: 'Report' },
      { label: 'Directory Connections', path: '/superadmin/directories', section: 'Configure' },
      { label: 'HR Integration', path: '/superadmin/hr', section: 'Configure' },
      { label: 'Audit Sources', path: '/superadmin/audit-sources', section: 'Configure' },
      { label: 'Access Reviews', path: '/superadmin/access-reviews', section: 'Configure' },
      { label: 'SoD Policies', path: '/superadmin/sod-policies', section: 'Configure' },
      { label: 'SoD Violations', path: '/superadmin/sod-violations', section: 'Configure' },
      { label: 'Access Drift', path: '/superadmin/access-drift', section: 'Configure' },
      { label: 'Provisioning Profiles', path: '/superadmin/profiles', section: 'Configure' },
      { label: 'Lifecycle Playbooks', path: '/superadmin/playbooks', section: 'Configure' },
      { label: 'Application Settings', path: '/settings', section: 'Configure' },
      { label: 'Application Accounts', path: '/superadmin/admins', section: 'Configure' },
      { label: 'Audit Log', path: '/superadmin/audit-log', section: 'Configure' },
      { label: 'Integrity Check', path: '/superadmin/integrity', section: 'Configure' },
    )
  }

  if (dirId) {
    items.push(
      { label: 'Users', path: `/directories/${dirId}/users`, section: 'Directory' },
      { label: 'Groups', path: `/directories/${dirId}/groups`, section: 'Directory' },
      { label: 'Approvals', path: `/directories/${dirId}/approvals`, section: 'Directory' },
      { label: 'Playbooks', path: `/directories/${dirId}/playbooks`, section: 'Directory' },
      { label: 'Reports', path: `/directories/${dirId}/reports`, section: 'Directory' },
      { label: 'Bulk Import/Export', path: `/directories/${dirId}/bulk`, section: 'Directory' },
      { label: 'Access Reviews', path: `/directories/${dirId}/access-reviews`, section: 'Directory' },
      { label: 'Audit Log', path: `/directories/${dirId}/audit`, section: 'Directory' },
    )
  }

  items.push(
    { label: 'Notifications', path: '/notifications', section: 'General' },
  )

  return items
})

const filtered = computed(() => {
  if (!query.value) return commands.value
  const q = query.value.toLowerCase()
  return commands.value.filter(c =>
    c.label.toLowerCase().includes(q) ||
    (c.section && c.section.toLowerCase().includes(q))
  )
})

watch(query, () => { activeIndex.value = 0 })

watch(open, async (isOpen) => {
  if (isOpen) {
    query.value = ''
    activeIndex.value = 0
    await nextTick()
    inputRef.value?.focus()
  }
})

function moveDown() {
  activeIndex.value = Math.min(activeIndex.value + 1, filtered.value.length - 1)
}

function moveUp() {
  activeIndex.value = Math.max(activeIndex.value - 1, 0)
}

function selectCurrent() {
  const item = filtered.value[activeIndex.value]
  if (item) go(item)
}

function go(item) {
  open.value = false
  router.push(item.path)
}

function handleKeydown(e) {
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault()
    open.value = !open.value
  }
}

onMounted(() => document.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', handleKeydown))

defineExpose({ open })
</script>
