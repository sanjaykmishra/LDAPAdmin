<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">SoD Violations</h1>
        <p class="text-sm text-gray-500 mt-1">Users who violate separation of duties policies</p>
      </div>
      <div class="flex gap-2 items-center" v-if="dirId">
        <label class="text-sm text-gray-600">Filter:</label>
        <select v-model="statusFilter" @change="loadViolations"
          class="border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500">
          <option value="">All</option>
          <option value="OPEN">Open</option>
          <option value="RESOLVED">Resolved</option>
          <option value="EXEMPTED">Exempted</option>
        </select>
      </div>
    </div>

    <!-- Directory picker -->
    <div v-if="showPicker" class="mb-4">
      <label class="block text-sm font-medium text-gray-700 mb-1">Directory</label>
      <select v-model="selectedDir" class="input w-64">
        <option value="" disabled>{{ loadingDirs ? 'Loading…' : '— Select directory —' }}</option>
        <option v-for="d in directories" :key="d.id" :value="d.id">{{ d.displayName }}</option>
      </select>
    </div>

    <div v-if="loading && !violations.length" class="text-gray-500">Loading...</div>

    <div v-if="!loading && !violations.length" class="text-gray-500 bg-white rounded-lg border p-8 text-center">
      No violations found{{ statusFilter ? ' with status ' + statusFilter : '' }}.
    </div>

    <div class="space-y-2">
      <div v-for="v in violations" :key="v.id"
        class="bg-white rounded-lg border p-4 flex items-center gap-4">
        <span :class="statusBadge(v.status)">{{ v.status }}</span>
        <div class="flex-1 min-w-0">
          <div class="font-medium text-sm truncate">
            <button @click.stop="showUserDetail(v.userDn)" class="text-blue-600 hover:text-blue-800 hover:underline text-left">
              {{ v.userDisplayName || v.userDn }}
            </button>
          </div>
          <div class="text-xs text-gray-500 truncate">
            Policy: <strong>{{ v.policyName }}</strong> &mdash; {{ v.userDn }}
          </div>
          <div class="text-xs text-gray-500">
            Conflicting groups:
            <strong>{{ v.groupAName || v.groupADn }}</strong> &amp;
            <strong>{{ v.groupBName || v.groupBDn }}</strong>
          </div>
          <div class="text-xs text-gray-400">
            Detected: {{ fmtDate(v.detectedAt) }}
            <template v-if="v.resolvedAt"> &mdash; Resolved: {{ fmtDate(v.resolvedAt) }}</template>
          </div>
          <div v-if="v.exemptionReason" class="text-xs text-gray-500 mt-1">
            Exempted by {{ v.exemptedByUsername }}: {{ v.exemptionReason }}
            <template v-if="v.exemptionExpiresAt">
              (expires {{ fmtDate(v.exemptionExpiresAt) }})
            </template>
          </div>
        </div>
        <div class="flex gap-2 shrink-0">
          <button v-if="v.status === 'OPEN'" @click="openExempt(v)" class="btn-secondary text-xs">
            Exempt
          </button>
          <button v-if="v.status === 'OPEN'" @click="confirmResolve(v)" class="btn-secondary text-xs">
            Resolve
          </button>
        </div>
      </div>
    </div>

    <!-- Exempt dialog -->
    <div v-if="exemptDialog" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50" @click.self="exemptDialog = null">
      <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-md">
        <h3 class="font-semibold text-gray-900 mb-3">Exempt Violation</h3>
        <p class="text-sm text-gray-600 mb-3">
          User: <strong>{{ exemptDialog.userDisplayName || exemptDialog.userDn }}</strong><br/>
          Policy: <strong>{{ exemptDialog.policyName }}</strong>
        </p>
        <div class="mb-4">
          <label class="block text-sm font-medium text-gray-700 mb-1">Reason *</label>
          <textarea v-model="exemptReason" rows="3" required
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            placeholder="Business justification for this exemption..."></textarea>
        </div>
        <div class="mb-4">
          <label class="block text-sm font-medium text-gray-700 mb-1">Expires At (optional)</label>
          <input v-model="exemptExpiresAt" type="datetime-local"
            class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
          <p class="text-xs text-gray-400 mt-1">Leave empty for a permanent exemption.</p>
        </div>
        <div class="flex gap-2 justify-end">
          <button @click="exemptDialog = null" class="btn-neutral text-sm">Cancel</button>
          <button @click="submitExempt" :disabled="!exemptReason.trim() || loading" class="btn-primary text-sm">
            {{ loading ? 'Saving...' : 'Confirm Exemption' }}
          </button>
        </div>
      </div>
    </div>

    <!-- User detail modal -->
    <div v-if="userDetail" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="userDetail = null">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-2xl max-h-[80vh] flex flex-col">
        <div class="flex items-center justify-between px-5 py-3 border-b border-gray-200">
          <h3 class="text-sm font-semibold text-gray-900 truncate">{{ userDetail.dn }}</h3>
          <button @click="userDetail = null" class="text-gray-400 hover:text-gray-600 text-lg leading-none">&times;</button>
        </div>
        <div class="overflow-y-auto p-5">
          <div v-if="loadingUser" class="text-sm text-gray-400 text-center py-4">Loading user details...</div>
          <template v-else>
            <table class="w-full text-sm">
              <tbody>
                <tr v-for="(values, attr) in userDetail.attributes" :key="attr" class="border-b border-gray-100">
                  <td class="py-2 pr-4 font-medium text-gray-600 align-top whitespace-nowrap">{{ attr }}</td>
                  <td class="py-2 font-mono text-xs text-gray-800 break-all">
                    <div v-for="(v, i) in values" :key="i">{{ v }}</div>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="!Object.keys(userDetail.attributes || {}).length" class="text-sm text-gray-400 text-center py-4">No attributes returned.</p>

            <!-- Group memberships -->
            <div class="mt-4 border-t border-gray-200 pt-4">
              <button @click="loadUserGroups(userDetail.dn)" :disabled="loadingGroups" class="btn-secondary text-sm">
                {{ loadingGroups ? 'Loading…' : (userGroups !== null ? 'Refresh Groups' : 'View Groups') }}
              </button>
              <div v-if="userGroups !== null" class="mt-3">
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wider mb-2">
                  Group Memberships ({{ userGroups.length }})
                </p>
                <div v-if="userGroups.length === 0" class="text-sm text-gray-400">Not a member of any groups.</div>
                <div v-else class="space-y-1 max-h-48 overflow-y-auto">
                  <div v-for="g in userGroups" :key="g.dn"
                    class="font-mono text-xs text-gray-700 bg-gray-50 rounded px-3 py-1.5 break-all">
                    <span class="font-medium text-gray-900">{{ g.cn || '' }}</span>
                    <span v-if="g.cn" class="text-gray-400 ml-1">—</span>
                    {{ g.dn }}
                  </div>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
  <ConfirmDialog v-model="showResolveConfirm"
    message="Mark this violation as resolved?"
    confirmLabel="Resolve" @confirm="handleResolve" />
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useApi } from '@/composables/useApi'
import { useDirectoryPicker } from '@/composables/useDirectoryPicker'
import { listViolations, exemptViolation, resolveViolation } from '@/api/sodPolicies'
import { searchEntries } from '@/api/browse'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const { dirId, directories, selectedDir, loadingDirs, showPicker } = useDirectoryPicker()
const { loading, call } = useApi()

const violations = ref([])
const statusFilter = ref('OPEN')
const exemptDialog = ref(null)
const exemptReason = ref('')
const exemptExpiresAt = ref('')
const userDetail = ref(null)
const loadingUser = ref(false)
const userGroups = ref(null)
const loadingGroups = ref(false)

function statusBadge(s) {
  const base = 'px-2 py-0.5 rounded-full text-xs font-medium shrink-0'
  switch (s) {
    case 'OPEN': return base + ' bg-red-100 text-red-800'
    case 'RESOLVED': return base + ' bg-green-100 text-green-800'
    case 'EXEMPTED': return base + ' bg-yellow-100 text-yellow-800'
    default: return base + ' bg-gray-100 text-gray-800'
  }
}

function fmtDate(val) {
  if (!val) return ''
  return new Date(val).toLocaleString()
}

function openExempt(v) {
  exemptDialog.value = v
  exemptReason.value = ''
  exemptExpiresAt.value = ''
}

async function submitExempt() {
  try {
    const data = { reason: exemptReason.value }
    if (exemptExpiresAt.value) {
      data.expiresAt = new Date(exemptExpiresAt.value).toISOString()
    }
    await call(() => exemptViolation(dirId.value, exemptDialog.value.id, data),
      { successMsg: 'Violation exempted' })
    exemptDialog.value = null
    await loadViolations()
  } catch { /* handled */ }
}

const showResolveConfirm = ref(false)
const resolveTarget = ref(null)

function confirmResolve(v) {
  resolveTarget.value = v
  showResolveConfirm.value = true
}

async function handleResolve() {
  const v = resolveTarget.value
  showResolveConfirm.value = false
  if (!v) return
  try {
    await call(() => resolveViolation(dirId.value, v.id), { successMsg: 'Violation resolved' })
    await loadViolations()
  } catch { /* handled */ }
}

async function loadViolations() {
  try {
    const params = statusFilter.value ? { status: statusFilter.value } : {}
    const res = await call(() => listViolations(dirId.value, params))
    violations.value = res.data
  } catch { /* handled */ }
}

async function showUserDetail(dn) {
  userDetail.value = { dn, attributes: {} }
  userGroups.value = null
  loadingUser.value = true
  try {
    const { data } = await searchEntries(dirId.value, {
      baseDn: dn,
      scope: 'base',
      filter: '(objectClass=*)',
      limit: 1,
    })
    const entry = Array.isArray(data) && data.length ? data[0] : null
    userDetail.value = entry || { dn, attributes: {} }
  } catch {
    userDetail.value = { dn, attributes: {} }
  } finally {
    loadingUser.value = false
  }
}

async function loadUserGroups(dn) {
  if (!dirId.value || !dn) return
  loadingGroups.value = true
  try {
    const escapedDn = dn.replace(/([\\*()])/g, '\\$1')
    const uid = dn.split(',')[0].split('=')[1] || dn
    const { data } = await searchEntries(dirId.value, {
      scope: 'sub',
      filter: `(|(member=${escapedDn})(uniqueMember=${escapedDn})(memberUid=${uid}))`,
      attributes: 'cn,dn',
      limit: 200,
    })
    userGroups.value = (Array.isArray(data) ? data : []).map(e => ({
      dn: e.dn,
      cn: (e.attributes?.cn || [])[0] || '',
    })).sort((a, b) => (a.cn || a.dn).localeCompare(b.cn || b.dn))
  } catch {
    userGroups.value = []
  } finally {
    loadingGroups.value = false
  }
}

watch(dirId, (v) => { if (v) loadViolations() })
onMounted(() => { if (dirId.value) loadViolations() })
</script>

<style scoped>
@reference "tailwindcss";
</style>
