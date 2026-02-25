<template>
  <div class="p-6">
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Audit Log</h1>

    <!-- Filters -->
    <div class="bg-white border border-gray-200 rounded-xl p-4 mb-4 grid grid-cols-3 gap-3">
      <FormField label="From" type="datetime-local" v-model="filters.from" />
      <FormField label="To"   type="datetime-local" v-model="filters.to" />
      <FormField label="Action" type="select" v-model="filters.action" :options="actionOptions" />
      <FormField label="Actor Username" v-model="filters.actorUsername" />
      <FormField label="Target DN" v-model="filters.targetDn" class="col-span-2" />
    </div>
    <div class="flex gap-3 mb-4">
      <button @click="load(0)" class="btn-primary">Filter</button>
      <button @click="clearFilters" class="btn-secondary">Clear</button>
    </div>

    <DataTable :columns="cols" :rows="events" :loading="loading" row-key="id">
      <template #cell-occurredAt="{ value }">{{ fmtDate(value) }}</template>
      <template #cell-action="{ value }">
        <span class="badge-gray">{{ value }}</span>
      </template>
      <template #cell-targetDn="{ value }"><code class="text-xs">{{ value }}</code></template>
    </DataTable>

    <!-- Pagination -->
    <div class="flex items-center justify-between mt-4">
      <button :disabled="page === 0" @click="load(page - 1)" class="btn-secondary">← Prev</button>
      <span class="text-sm text-gray-500">Page {{ page + 1 }} of {{ totalPages }}</span>
      <button :disabled="page >= totalPages - 1" @click="load(page + 1)" class="btn-secondary">Next →</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useApi } from '@/composables/useApi'
import { getAuditLog } from '@/api/audit'
import DataTable from '@/components/DataTable.vue'
import FormField from '@/components/FormField.vue'

const route = useRoute()
const { loading, call } = useApi()
const dirId = route.params.dirId

const events     = ref([])
const page       = ref(0)
const totalPages = ref(1)
const pageSize   = 20

const filters = ref({ from: '', to: '', action: '', actorUsername: '', targetDn: '' })

const actionOptions = [
  { value: '', label: 'All actions' },
  ...['user.create','user.update','user.delete','user.enable','user.disable','user.move',
      'group.create','group.delete','group.member_add','group.member_remove','ldap.change']
     .map(v => ({ value: v, label: v }))
]

const cols = [
  { key: 'occurredAt',    label: 'When' },
  { key: 'actorUsername', label: 'Actor' },
  { key: 'action',        label: 'Action' },
  { key: 'targetDn',      label: 'Target' },
]

function fmtDate(v) {
  if (!v) return '—'
  return new Date(v).toLocaleString()
}

function clearFilters() {
  filters.value = { from: '', to: '', action: '', actorUsername: '', targetDn: '' }
}

async function load(p = 0) {
  page.value = p
  await call(async () => {
    const params = {
      page, size: pageSize,
      from:          filters.value.from  || undefined,
      to:            filters.value.to    || undefined,
      action:        filters.value.action || undefined,
      actorUsername: filters.value.actorUsername || undefined,
      targetDn:      filters.value.targetDn || undefined,
    }
    const { data } = await getAuditLog(dirId, params)
    const paged = data.content ? data : { content: data, totalPages: 1 }
    events.value     = paged.content
    totalPages.value = paged.totalPages || 1
  })
}

onMounted(() => load(0))
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary   { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700; }
.btn-secondary { @apply px-3 py-1.5 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50 disabled:opacity-50; }
.badge-gray    { @apply inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-700; }
</style>
