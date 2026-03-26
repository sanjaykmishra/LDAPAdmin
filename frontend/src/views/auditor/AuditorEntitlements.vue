<template>
  <div>
    <h1 class="text-xl font-bold text-slate-900 mb-4">User Entitlements</h1>

    <SkeletonLoader v-if="loading" :rows="5" />
    <ErrorCard v-else-if="error" title="Failed to load entitlements" @retry="load" />

    <template v-else>
      <!-- Search -->
      <div class="mb-4">
        <input v-model="search" type="text" placeholder="Search by name, DN, or group..."
               class="border border-slate-200 rounded-lg px-3 py-2 text-xs w-full sm:w-80 focus:outline-none focus:ring-2 focus:ring-slate-300" />
      </div>

      <div v-if="filtered.length === 0" class="bg-slate-50 border border-slate-200 rounded-xl p-8 text-center">
        <p class="text-sm text-slate-500">
          {{ entitlements.length === 0 ? 'No entitlements data available.' : 'No users match the search query.' }}
        </p>
      </div>

      <!-- Desktop table -->
      <section v-if="filtered.length > 0" class="hidden sm:block bg-white border border-slate-200 rounded-xl overflow-hidden">
        <div class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-slate-200 bg-slate-50">
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">User</th>
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Login</th>
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Email</th>
                <th class="text-left py-2 px-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Groups</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="u in paged" :key="u.dn" class="border-b border-slate-100 hover:bg-slate-50/50">
                <td class="py-2 px-4">
                  <div class="text-xs font-medium text-slate-800">{{ u.displayName || u.cn || '—' }}</div>
                  <div class="text-[10px] text-slate-400 font-mono truncate max-w-xs" :title="u.dn">{{ u.dn }}</div>
                </td>
                <td class="py-2 px-4 text-xs text-slate-600 font-mono">{{ u.loginName || '—' }}</td>
                <td class="py-2 px-4 text-xs text-slate-600">{{ u.mail || '—' }}</td>
                <td class="py-2 px-4">
                  <div class="flex flex-wrap gap-1">
                    <span v-for="g in (u.groups || []).slice(0, groupsExpanded[u.dn] ? undefined : 3)" :key="g"
                          class="inline-block bg-slate-100 text-slate-600 text-[10px] px-1.5 py-0.5 rounded">
                      {{ g }}
                    </span>
                    <button v-if="(u.groups || []).length > 3 && !groupsExpanded[u.dn]"
                            @click="groupsExpanded[u.dn] = true"
                            class="text-[10px] text-blue-600 hover:text-blue-800">
                      +{{ u.groups.length - 3 }} more
                    </button>
                    <button v-if="groupsExpanded[u.dn]"
                            @click="groupsExpanded[u.dn] = false"
                            class="text-[10px] text-blue-600 hover:text-blue-800">
                      show less
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="totalPages > 1" class="px-4 py-3 border-t border-slate-200 flex items-center justify-between">
          <span class="text-xs text-slate-500">Page {{ page + 1 }} of {{ totalPages }} ({{ filtered.length }} users)</span>
          <div class="flex gap-1">
            <button @click="page = Math.max(0, page - 1)" :disabled="page === 0" class="btn-sm">Prev</button>
            <button @click="page = Math.min(totalPages - 1, page + 1)" :disabled="page >= totalPages - 1" class="btn-sm">Next</button>
          </div>
        </div>
      </section>

      <!-- Mobile card layout -->
      <div class="sm:hidden space-y-2" v-if="filtered.length > 0">
        <div v-for="u in paged" :key="u.dn + '-m'" class="bg-white border border-slate-200 rounded-xl p-3">
          <div class="text-xs font-medium text-slate-800 mb-0.5">{{ u.displayName || u.cn || '—' }}</div>
          <div class="text-[10px] text-slate-400 font-mono truncate mb-1">{{ u.dn }}</div>
          <div v-if="u.mail" class="text-[10px] text-slate-500 mb-1">{{ u.mail }}</div>
          <div class="flex flex-wrap gap-1">
            <span v-for="g in (u.groups || []).slice(0, 5)" :key="g"
                  class="inline-block bg-slate-100 text-slate-600 text-[10px] px-1.5 py-0.5 rounded">{{ g }}</span>
            <span v-if="(u.groups || []).length > 5" class="text-[10px] text-slate-400">+{{ u.groups.length - 5 }} more</span>
          </div>
        </div>
        <div v-if="totalPages > 1" class="flex items-center justify-between pt-2">
          <span class="text-xs text-slate-500">{{ page + 1 }}/{{ totalPages }}</span>
          <div class="flex gap-1">
            <button @click="page = Math.max(0, page - 1)" :disabled="page === 0" class="btn-sm">Prev</button>
            <button @click="page = Math.min(totalPages - 1, page + 1)" :disabled="page >= totalPages - 1" class="btn-sm">Next</button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { getPortalEntitlements } from '@/api/auditorPortal'
import SkeletonLoader from './components/SkeletonLoader.vue'
import ErrorCard from './components/ErrorCard.vue'

const props = defineProps({ token: String, metadata: Object, scope: Object })

const loading = ref(true)
const entitlements = ref([])
const search = ref('')
const page = ref(0)
const PAGE_SIZE = 50
const error = ref(false)
const groupsExpanded = reactive({})

const filtered = computed(() => {
  const q = search.value.toLowerCase()
  if (!q) return entitlements.value
  return entitlements.value.filter(u =>
    (u.displayName || '').toLowerCase().includes(q) ||
    (u.cn || '').toLowerCase().includes(q) ||
    (u.dn || '').toLowerCase().includes(q) ||
    (u.loginName || '').toLowerCase().includes(q) ||
    (u.mail || '').toLowerCase().includes(q) ||
    (u.groups || []).some(g => g.toLowerCase().includes(q))
  )
})

const totalPages = computed(() => Math.ceil(filtered.value.length / PAGE_SIZE))
const paged = computed(() => filtered.value.slice(page.value * PAGE_SIZE, (page.value + 1) * PAGE_SIZE))

watch(search, () => { page.value = 0 })

async function load() {
  loading.value = true
  error.value = false
  try {
    const { data } = await getPortalEntitlements(props.token)
    entitlements.value = data
  } catch { error.value = true }
  loading.value = false
}
onMounted(load)
</script>

<style scoped>
@reference "tailwindcss";
.btn-sm { @apply px-3 py-1 border border-slate-200 text-slate-600 rounded-lg text-xs hover:bg-slate-50 disabled:opacity-50; }
</style>
