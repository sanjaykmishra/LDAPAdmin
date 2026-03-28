<script setup>
import { ref, computed, onMounted } from 'vue'
import { listAlertRules, updateAlertRule, initializeAlertRules } from '@/api/alerts'
import { listDirectories } from '@/api/directories'
import { useNotificationStore } from '@/stores/notifications'

const notif = useNotificationStore()
const loading = ref(true)
const rules = ref([])
const directories = ref([])

function humanize(type) {
  return (type || '').replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
}

const severityOptions = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

const rulesByDirectory = computed(() => {
  const map = new Map()
  for (const r of rules.value) {
    const key = r.directoryId || 'global'
    if (!map.has(key)) map.set(key, { name: r.directoryName || 'Global', directoryId: r.directoryId, rules: [] })
    map.get(key).rules.push(r)
  }
  return [...map.values()]
})

const dirsWithoutRules = computed(() => {
  const configured = new Set(rules.value.map(r => r.directoryId).filter(Boolean))
  return directories.value.filter(d => !configured.has(d.id))
})

async function loadData() {
  loading.value = true
  try {
    const [rulesRes, dirsRes] = await Promise.all([listAlertRules({}), listDirectories()])
    rules.value = rulesRes.data
    directories.value = dirsRes.data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    loading.value = false
  }
}

async function toggleEnabled(rule) {
  try {
    const { data } = await updateAlertRule(rule.id, { enabled: !rule.enabled })
    Object.assign(rule, data)
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  }
}

async function updateSeverity(rule, severity) {
  try {
    const { data } = await updateAlertRule(rule.id, { severity })
    Object.assign(rule, data)
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  }
}

async function updateCooldown(rule, hours) {
  try {
    const { data } = await updateAlertRule(rule.id, { cooldownHours: parseInt(hours) || 24 })
    Object.assign(rule, data)
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  }
}

async function toggleEmail(rule) {
  try {
    const { data } = await updateAlertRule(rule.id, { notifyEmail: !rule.notifyEmail })
    Object.assign(rule, data)
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  }
}

async function updateRecipients(rule, recipients) {
  try {
    const { data } = await updateAlertRule(rule.id, { emailRecipients: recipients })
    Object.assign(rule, data)
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  }
}

async function doInitialize(directoryId) {
  try {
    await initializeAlertRules(directoryId)
    notif.success('Default alert rules created')
    await loadData()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  }
}

onMounted(loadData)
</script>

<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Alert Rules</h1>
        <p class="text-sm text-gray-500 mt-1">Configure monitoring rules and thresholds per directory</p>
      </div>
    </div>

    <div v-if="loading" class="text-center py-12 text-gray-400">Loading rules...</div>

    <template v-else>
      <!-- Directories without rules -->
      <div v-if="dirsWithoutRules.length" class="mb-6 bg-blue-50 border border-blue-200 rounded-xl p-4">
        <p class="text-sm text-blue-800 font-medium mb-2">Directories without monitoring rules:</p>
        <div class="flex flex-wrap gap-2">
          <button v-for="d in dirsWithoutRules" :key="d.id"
                  @click="doInitialize(d.id)"
                  class="btn-primary btn-sm">
            Initialize {{ d.displayName || d.name }}
          </button>
        </div>
      </div>

      <!-- Rules by directory -->
      <div v-for="group in rulesByDirectory" :key="group.directoryId || 'global'" class="mb-8">
        <h2 class="text-lg font-semibold text-gray-800 mb-3">{{ group.name }}</h2>
        <div class="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
          <table class="w-full text-sm">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 uppercase w-12">On</th>
                <th class="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 uppercase">Rule</th>
                <th class="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 uppercase w-32">Severity</th>
                <th class="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 uppercase w-28">Cooldown</th>
                <th class="px-4 py-2.5 text-center text-xs font-semibold text-gray-500 uppercase w-16">In-App</th>
                <th class="px-4 py-2.5 text-center text-xs font-semibold text-gray-500 uppercase w-16">Email</th>
                <th class="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 uppercase">Recipients</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-50">
              <tr v-for="r in group.rules" :key="r.id" :class="r.enabled ? '' : 'opacity-50'">
                <td class="px-4 py-2.5">
                  <input type="checkbox" :checked="r.enabled" @change="toggleEnabled(r)" class="rounded accent-blue-600" />
                </td>
                <td class="px-4 py-2.5 font-medium text-gray-900">{{ humanize(r.ruleType) }}</td>
                <td class="px-4 py-2.5">
                  <select :value="r.severity" @change="updateSeverity(r, $event.target.value)" class="input input-sm text-xs">
                    <option v-for="s in severityOptions" :key="s" :value="s">{{ s }}</option>
                  </select>
                </td>
                <td class="px-4 py-2.5">
                  <div class="flex items-center gap-1">
                    <input type="number" :value="r.cooldownHours" min="1" max="720"
                           @change="updateCooldown(r, $event.target.value)"
                           class="input input-sm w-16 text-xs" />
                    <span class="text-xs text-gray-400">hrs</span>
                  </div>
                </td>
                <td class="px-4 py-2.5 text-center">
                  <span v-if="r.notifyInApp" class="text-green-600 text-xs">Yes</span>
                  <span v-else class="text-gray-400 text-xs">No</span>
                </td>
                <td class="px-4 py-2.5 text-center">
                  <input type="checkbox" :checked="r.notifyEmail" @change="toggleEmail(r)" class="rounded accent-blue-600" />
                </td>
                <td class="px-4 py-2.5">
                  <input v-if="r.notifyEmail" type="text" :value="r.emailRecipients || ''"
                         @change="updateRecipients(r, $event.target.value)"
                         class="input input-sm text-xs w-full" placeholder="email@example.com, ..." />
                  <span v-else class="text-gray-400 text-xs">—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="!rulesByDirectory.length && !dirsWithoutRules.length" class="text-center py-12 text-gray-400">
        No directories configured. Add a directory first.
      </div>
    </template>
  </div>
</template>

<style scoped>
@reference "tailwindcss";
</style>
