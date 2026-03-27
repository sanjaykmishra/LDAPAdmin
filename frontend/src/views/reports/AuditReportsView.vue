<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Compliance Reports</h1>
        <p class="text-sm text-gray-500 mt-1">Generate compliance and audit evidence reports</p>
      </div>
      <RouterLink to="/superadmin/auditor-links" class="bg-blue-50 border border-blue-200 text-blue-600 rounded-full px-4 py-1.5 text-sm font-medium hover:bg-blue-100 transition-colors flex items-center gap-1.5">
        <svg class="w-4 h-4" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 13.5V17a1.5 1.5 0 001.5 1.5h11A1.5 1.5 0 0017 17v-3.5M10 2v11M10 2l4 4M10 2L6 6"/></svg>
        Auditor Links
      </RouterLink>
    </div>

    <!-- Directory picker (superadmin only) -->
    <div v-if="!routeDirId" class="mb-4">
      <label class="block text-sm font-medium text-gray-700 mb-1">Directory</label>
      <select v-model="selectedDir" class="input w-64">
        <option value="" disabled>{{ loadingDirs ? 'Loading…' : '— Select directory —' }}</option>
        <option v-for="d in directories" :key="d.id" :value="d.id">{{ d.displayName }}</option>
      </select>
    </div>

    <!-- Report runner -->
    <section class="bg-white border border-gray-200 rounded-xl p-5 mb-6">
      <div class="grid grid-cols-4 gap-3 mb-3">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Report Type</label>
          <select v-model="runForm.reportType" class="input w-full">
            <option v-for="t in reportTypes" :key="t.value" :value="t.value">{{ t.label }}</option>
          </select>
        </div>
        <div v-if="needsGroupDn">
          <label class="block text-sm font-medium text-gray-700 mb-1">Group DN (optional filter)</label>
          <GroupDnPicker v-model="runForm.groupDn" :directory-id="dirId" />
        </div>
        <div v-if="needsStatusFilter">
          <label class="block text-sm font-medium text-gray-700 mb-1">Status</label>
          <select v-model="runForm.statusFilter" class="input w-full">
            <option value="">All</option>
            <option value="OPEN">Open</option>
            <option value="RESOLVED">Resolved</option>
            <option value="EXEMPTED">Exempted</option>
          </select>
        </div>
        <div v-if="needsPolicyFilter">
          <label class="block text-sm font-medium text-gray-700 mb-1">SoD Policy</label>
          <select v-model="runForm.policyId" class="input w-full">
            <option value="">All Policies</option>
            <option v-for="p in sodPolicies" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
        </div>
        <div v-if="needsCampaign">
          <label class="block text-sm font-medium text-gray-700 mb-1">Campaign</label>
          <select v-model="runForm.campaignId" class="input w-full">
            <option value="">-- select to see decisions --</option>
            <option v-for="c in campaigns" :key="c.id" :value="c.id">{{ c.name }} ({{ c.status }})</option>
          </select>
        </div>
        <!-- Termination Velocity filters: from, to, SLA -->
        <div v-if="isTermVelocity">
          <label class="block text-sm font-medium text-gray-700 mb-1">From</label>
          <input v-model="runForm.fromDate" type="date" class="input w-full" />
        </div>
        <div v-if="isTermVelocity">
          <label class="block text-sm font-medium text-gray-700 mb-1">To</label>
          <input v-model="runForm.toDate" type="date" class="input w-full" />
        </div>
        <div v-if="isTermVelocity">
          <label class="block text-sm font-medium text-gray-700 mb-1">SLA Threshold (hours)</label>
          <input v-model.number="runForm.slaHours" type="number" min="1" class="input w-full" placeholder="24" />
        </div>
        <!-- Audit Log filters: from, to, action -->
        <div v-if="isAuditLog">
          <label class="block text-sm font-medium text-gray-700 mb-1">From</label>
          <input v-model="runForm.fromDate" type="datetime-local" class="input w-full" />
        </div>
        <div v-if="isAuditLog">
          <label class="block text-sm font-medium text-gray-700 mb-1">To</label>
          <input v-model="runForm.toDate" type="datetime-local" class="input w-full" />
        </div>
        <div v-if="isAuditLog">
          <label class="block text-sm font-medium text-gray-700 mb-1">Action</label>
          <select v-model="runForm.auditAction" class="input w-full">
            <option value="">All Actions</option>
            <option v-for="a in auditActions" :key="a" :value="a">{{ humanizeEnum(a) }}</option>
          </select>
        </div>
        <!-- Privileged Account Inventory: group filter -->
        <div v-if="isPrivilegedReport" class="col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-1">Privileged Group Filter (LDAP)</label>
          <div class="flex gap-1.5">
            <input v-model="runForm.groupFilter" :disabled="!groupFilterEditable" type="text"
              class="input w-full font-mono text-xs" :class="{ 'bg-gray-50 text-gray-500': !groupFilterEditable }" />
            <button @click="groupFilterEditable = !groupFilterEditable" type="button"
              class="btn-secondary text-xs shrink-0 flex items-center gap-1" :title="groupFilterEditable ? 'Lock filter' : 'Edit filter'">
              <svg v-if="!groupFilterEditable" class="w-3.5 h-3.5" fill="none" viewBox="0 0 20 20" stroke="currentColor" stroke-width="1.5"><path d="M13.586 3.586a2 2 0 112.828 2.828l-8.793 8.793-3.536.707.707-3.536 8.793-8.793z"/></svg>
              <svg v-else class="w-3.5 h-3.5" fill="none" viewBox="0 0 20 20" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7"/></svg>
              {{ groupFilterEditable ? 'Lock' : 'Edit' }}
            </button>
          </div>
        </div>
      </div>
      <button @click="doRun" :disabled="running || !dirId" class="btn-primary">
        {{ running ? 'Running...' : 'Run Report' }}
      </button>
    </section>

    <!-- Results -->
    <section v-if="hasResults" class="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <div class="px-5 py-3 border-b border-gray-200 flex items-center justify-between">
        <span class="text-sm text-gray-600">{{ resultRows.length }} result{{ resultRows.length !== 1 ? 's' : '' }}</span>
        <div class="flex gap-2">
          <!-- SoD / Drift bulk actions -->
          <template v-if="hasActionableRows && selectedIds.size > 0">
            <template v-if="isSodReport">
              <button @click="bulkResolve" :disabled="actioning" class="bg-green-600 text-white text-xs font-medium px-3 py-1.5 rounded-lg hover:bg-green-700 disabled:opacity-50">
                Resolve ({{ selectedIds.size }})
              </button>
              <button @click="showExemptModal = true" :disabled="actioning" class="bg-amber-500 text-white text-xs font-medium px-3 py-1.5 rounded-lg hover:bg-amber-600 disabled:opacity-50">
                Exempt ({{ selectedIds.size }})
              </button>
            </template>
            <template v-if="isDriftReport">
              <button @click="bulkAcknowledge" :disabled="actioning" class="bg-blue-600 text-white text-xs font-medium px-3 py-1.5 rounded-lg hover:bg-blue-700 disabled:opacity-50">
                Acknowledge ({{ selectedIds.size }})
              </button>
              <button @click="showExemptModal = true" :disabled="actioning" class="bg-amber-500 text-white text-xs font-medium px-3 py-1.5 rounded-lg hover:bg-amber-600 disabled:opacity-50">
                Exempt ({{ selectedIds.size }})
              </button>
            </template>
          </template>
          <button @click="doExport('CSV')" :disabled="exporting" class="btn-secondary text-xs">Export CSV</button>
          <button @click="doExport('PDF')" :disabled="exporting" class="btn-secondary text-xs">Export PDF</button>
        </div>
      </div>

      <div v-if="resultRows.length === 0" class="p-8 text-center text-sm text-gray-400">
        <svg class="w-8 h-8 mx-auto mb-2 text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
        No entries found for this report.
      </div>

      <div v-else class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-gray-200 bg-gray-50">
              <th v-if="hasActionableRows" class="py-2 px-3 w-8">
                <input type="checkbox" :checked="allPageSelected" @change="toggleSelectAll" class="rounded border-gray-300" />
              </th>
              <th v-for="col in visibleColumns" :key="col"
                @click="toggleSort(col)"
                class="text-left py-2 px-4 text-xs font-semibold text-gray-500 uppercase tracking-wider cursor-pointer hover:text-gray-700 select-none whitespace-nowrap">
                {{ col }}
                <span v-if="sortCol === col" class="ml-0.5">{{ sortAsc ? '&#9650;' : '&#9660;' }}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in pagedRows" :key="i" :class="i % 2 === 0 ? 'bg-white' : 'bg-gray-50/50'" class="hover:bg-blue-50/30 border-b border-gray-100/50">
              <td v-if="hasActionableRows" class="py-2 px-3 w-8">
                <input type="checkbox" :checked="selectedIds.has(row['id'])" @change="toggleSelect(row['id'])" class="rounded border-gray-300" />
              </td>
              <td v-for="col in visibleColumns" :key="col"
                class="py-2 px-4 text-xs text-gray-700 max-w-xs" :title="row[col]">
                <!-- Status badge -->
                <span v-if="col === 'Status'" :class="statusBadgeClass(row[col])" class="inline-block px-2 py-0.5 rounded-full text-xs font-medium">
                  {{ row[col] }}
                </span>
                <!-- Severity badge -->
                <span v-else-if="col === 'Severity'" :class="severityBadgeClass(row[col])" class="inline-block px-2 py-0.5 rounded-full text-xs font-medium">
                  {{ row[col] }}
                </span>
                <!-- Decision badge (Access Review) -->
                <span v-else-if="col === 'Decision'" :class="decisionBadgeClass(row[col])" class="inline-block px-2 py-0.5 rounded-full text-xs font-medium">
                  {{ row[col] === 'CONFIRM' ? 'Confirmed' : row[col] === 'REVOKE' ? 'Revoked' : 'Pending' }}
                </span>
                <!-- Action badge (Audit Log) -->
                <span v-else-if="col === 'Action'" :class="actionBadgeClass(row[col])" class="inline-block px-2 py-0.5 rounded-full text-xs font-medium whitespace-nowrap">
                  {{ humanizeEnum(row[col]) }}
                </span>
                <!-- SLA Status badge (Termination Velocity) -->
                <span v-else-if="col === 'SLA Status'" :class="slaBadgeClass(row[col])" class="inline-block px-2 py-0.5 rounded-full text-xs font-medium">
                  {{ row[col] }}
                </span>
                <!-- Velocity (color-coded) -->
                <span v-else-if="col === 'Velocity'" class="font-mono whitespace-nowrap" :class="velocityColor(row[col])">
                  {{ row[col] }}
                </span>
                <!-- Peer Match percentage -->
                <span v-else-if="col === 'Peer Match'" class="font-mono whitespace-nowrap">
                  <span :class="peerMatchColor(row[col])">{{ row[col] }}</span>
                </span>
                <!-- Groups column (semicolon-separated tags) -->
                <span v-else-if="col === 'Groups' && row[col]" class="flex flex-wrap gap-1">
                  <span v-for="g in row[col].split('; ').filter(Boolean)" :key="g" class="inline-block bg-blue-50 text-blue-700 text-xs px-1.5 py-0.5 rounded">{{ g }}</span>
                </span>
                <!-- Detail column (stacked key-value pairs) -->
                <span v-else-if="col === 'Detail' && row[col]" class="block max-w-sm">
                  <span v-for="(line, li) in row[col].split('\n').filter(Boolean)" :key="li" class="block text-xs leading-snug">
                    <span class="text-gray-500">{{ line.split(': ')[0] }}:</span> <span class="text-gray-700">{{ line.substring(line.indexOf(': ') + 2) }}</span>
                  </span>
                </span>
                <!-- Date columns -->
                <span v-else-if="isDateColumn(col, row[col])" :title="formatFullDate(row[col])" class="font-mono whitespace-nowrap">
                  {{ formatRelativeDate(row[col]) }}
                </span>
                <!-- DN columns (truncate) -->
                <span v-else-if="isDnColumn(col, row[col])" :title="row[col]" class="font-mono truncate block max-w-xs">
                  {{ truncateDn(row[col]) }}
                </span>
                <!-- Default -->
                <span v-else class="font-mono truncate block max-w-xs">{{ row[col] }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="px-5 py-3 border-t border-gray-200 flex items-center justify-between">
        <span class="text-xs text-gray-500">Page {{ page + 1 }} of {{ totalPages }}</span>
        <div class="flex gap-1">
          <button @click="page = Math.max(0, page - 1)" :disabled="page === 0" class="btn-secondary text-xs">Prev</button>
          <button @click="page = Math.min(totalPages - 1, page + 1)" :disabled="page >= totalPages - 1" class="btn-secondary text-xs">Next</button>
        </div>
      </div>
    </section>

    <!-- Exempt Modal (shared by SoD + Drift) -->
    <AppModal v-model="showExemptModal" title="Exempt Findings" size="md">
      <p class="text-sm text-gray-600 mb-4">Exempting {{ selectedIds.size }} finding{{ selectedIds.size !== 1 ? 's' : '' }}.</p>
      <div class="space-y-3">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Reason <span class="text-red-500">*</span></label>
          <textarea v-model="exemptReason" rows="3" class="input w-full" placeholder="Provide a reason for the exemption..."></textarea>
        </div>
        <div v-if="isSodReport">
          <label class="block text-sm font-medium text-gray-700 mb-1">Expiration Date (optional)</label>
          <input v-model="exemptExpires" type="date" class="input w-full" />
        </div>
      </div>
      <div class="flex justify-end gap-2 mt-5">
        <button @click="showExemptModal = false" class="btn-neutral text-sm">Cancel</button>
        <button @click="bulkExempt" :disabled="!exemptReason.trim() || actioning" class="bg-amber-500 text-white text-sm font-medium px-4 py-2 rounded-lg hover:bg-amber-600 disabled:opacity-50">
          {{ actioning ? 'Processing...' : 'Exempt' }}
        </button>
      </div>
    </AppModal>

  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import { runReport, runReportData } from '@/api/reports'
import { listDirectories } from '@/api/directories'
import { listCampaigns } from '@/api/accessReviews'
import { listPolicies, exemptViolation, resolveViolation } from '@/api/sodPolicies'
import { acknowledgeFinding, exemptFinding } from '@/api/accessDrift'
import { downloadBlob } from '@/composables/useApi'
import {
  statusBadgeClass, severityBadgeClass, decisionBadgeClass, actionBadgeClass,
  formatRelativeDate, formatFullDate, truncateDn,
  looksLikeTimestamp, looksLikeDn, humanizeEnum,
  DATE_COLUMNS, HIDDEN_COLUMNS,
} from '@/composables/useReportFormatting'
import GroupDnPicker from '@/components/GroupDnPicker.vue'
import AppModal from '@/components/AppModal.vue'

const route = useRoute()
const notif = useNotificationStore()
const routeDirId = route.params.dirId

const directories = ref([])
const loadingDirs = ref(false)
const selectedDir = ref('')
const dirId = computed(() => routeDirId || selectedDir.value)

const PAGE_SIZE = 50

const DEFAULT_PRIVILEGED_GROUP_FILTER =
  '(&(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames)(objectClass=group))' +
  '(|(cn=*admin*)(cn=*Admin*)(cn=*root*)(cn=*superuser*)(cn=*operator*)' +
  '(cn=*prod*)(cn=*Prod*)(cn=mgr-*)(cn=*DataAccess*)(cn=*Security*)' +
  '(cn=*privileged*)(cn=*Privileged*)(cn=Domain Admins)(cn=Enterprise Admins)' +
  '(cn=Schema Admins)(cn=Account Operators)(cn=Server Operators)))'

const reportTypes = [
  { value: 'USER_ACCESS_REPORT',          label: 'User Access Report',           lookback: false, statusFilter: false, groupDn: true,  campaign: false, policyFilter: false },
  { value: 'ACCESS_REVIEW_RESULTS',       label: 'Access Review Results',        lookback: false, statusFilter: false, groupDn: false, campaign: true,  policyFilter: false },
  { value: 'PRIVILEGED_ACCOUNT_INVENTORY', label: 'Privileged Account Inventory', lookback: false, statusFilter: false, groupDn: false, campaign: false, policyFilter: false },
  { value: 'ACCESS_DRIFT_REPORT',         label: 'Access Drift',                 lookback: false, statusFilter: false, groupDn: false, campaign: false, policyFilter: false },
  { value: 'SOD_VIOLATIONS',              label: 'SoD Violations',               lookback: false, statusFilter: true,  groupDn: false, campaign: false, policyFilter: true },
  { value: 'TERMINATION_VELOCITY',       label: 'Termination Velocity',         lookback: false, statusFilter: false, groupDn: false, campaign: false, policyFilter: false },
  { value: 'AUDIT_LOG_REPORT',            label: 'Audit Log',                    lookback: false, statusFilter: false, groupDn: false, campaign: false, policyFilter: false },
]

const auditActions = [
  'USER_CREATE', 'USER_UPDATE', 'USER_DELETE', 'USER_ENABLE', 'USER_DISABLE', 'USER_MOVE', 'PASSWORD_RESET',
  'GROUP_CREATE', 'GROUP_UPDATE', 'GROUP_DELETE', 'GROUP_MEMBER_ADD', 'GROUP_MEMBER_REMOVE', 'GROUP_BULK_IMPORT',
  'ENTRY_CREATE', 'ENTRY_UPDATE', 'ENTRY_DELETE', 'ENTRY_MOVE', 'ENTRY_RENAME', 'LDIF_IMPORT', 'INTEGRITY_CHECK',
  'APPROVAL_SUBMITTED', 'APPROVAL_APPROVED', 'APPROVAL_REJECTED',
  'CAMPAIGN_CREATED', 'CAMPAIGN_ACTIVATED', 'CAMPAIGN_CLOSED', 'REVIEW_CONFIRMED', 'REVIEW_REVOKED',
  'SOD_POLICY_CREATED', 'SOD_POLICY_UPDATED', 'SOD_SCAN_EXECUTED', 'SOD_VIOLATION_DETECTED', 'SOD_VIOLATION_EXEMPTED', 'SOD_VIOLATION_RESOLVED',
  'PLAYBOOK_EXECUTED', 'PLAYBOOK_ROLLED_BACK',
  'HR_SYNC_STARTED', 'HR_SYNC_COMPLETED', 'HR_SYNC_FAILED',
  'LDAP_CHANGE',
]

const runForm = ref({
  reportType: 'USER_ACCESS_REPORT', groupDn: '', statusFilter: '', lookbackDays: 30, campaignId: '', policyId: '',
  fromDate: '', toDate: '', auditAction: '', slaHours: 24, groupFilter: DEFAULT_PRIVILEGED_GROUP_FILTER,
})
const groupFilterEditable = ref(false)
const running = ref(false)
const exporting = ref(false)
const hasResults = ref(false)
const resultColumns = ref([])
const resultRows = ref([])
const sortCol = ref('')
const sortAsc = ref(true)
const page = ref(0)

const currentType       = computed(() => reportTypes.find(t => t.value === runForm.value.reportType))
const needsLookback     = computed(() => !!currentType.value?.lookback)
const needsStatusFilter = computed(() => !!currentType.value?.statusFilter)
const needsGroupDn      = computed(() => !!currentType.value?.groupDn)
const needsCampaign     = computed(() => !!currentType.value?.campaign)
const needsPolicyFilter = computed(() => !!currentType.value?.policyFilter)
const isAuditLog        = computed(() => runForm.value.reportType === 'AUDIT_LOG_REPORT')
const isTermVelocity    = computed(() => runForm.value.reportType === 'TERMINATION_VELOCITY')
const isPrivilegedReport = computed(() => runForm.value.reportType === 'PRIVILEGED_ACCOUNT_INVENTORY')
const isSodReport       = computed(() => runForm.value.reportType === 'SOD_VIOLATIONS' && hasResults.value)
const isDriftReport     = computed(() => runForm.value.reportType === 'ACCESS_DRIFT_REPORT' && hasResults.value)
const hasActionableRows = computed(() => isSodReport.value || isDriftReport.value)

// Hide 'id' column from display
const visibleColumns = computed(() => resultColumns.value.filter(c => !HIDDEN_COLUMNS.has(c)).slice(0, 10))

const sortedRows = computed(() => {
  if (!sortCol.value) return resultRows.value
  const col = sortCol.value
  const dir = sortAsc.value ? 1 : -1
  return [...resultRows.value].sort((a, b) => (a[col] || '').localeCompare(b[col] || '') * dir)
})

const totalPages = computed(() => Math.ceil(sortedRows.value.length / PAGE_SIZE))
const pagedRows = computed(() => sortedRows.value.slice(page.value * PAGE_SIZE, (page.value + 1) * PAGE_SIZE))

// SoD policies & campaigns
const sodPolicies = ref([])
const campaigns = ref([])

// ── Selection ─────────────────────────────────────────────────────────────────
const selectedIds = ref(new Set())

const allPageSelected = computed(() => {
  if (!pagedRows.value.length) return false
  return pagedRows.value.every(r => selectedIds.value.has(r['id']))
})

function toggleSelect(id) {
  const s = new Set(selectedIds.value)
  if (s.has(id)) s.delete(id); else s.add(id)
  selectedIds.value = s
}

function toggleSelectAll() {
  const s = new Set(selectedIds.value)
  if (allPageSelected.value) {
    pagedRows.value.forEach(r => s.delete(r['id']))
  } else {
    pagedRows.value.forEach(r => s.add(r['id']))
  }
  selectedIds.value = s
}

// ── Bulk actions ──────────────────────────────────────────────────────────────
const actioning = ref(false)
const showExemptModal = ref(false)
const exemptReason = ref('')
const exemptExpires = ref('')

async function bulkResolve() {
  if (!selectedIds.value.size) return
  actioning.value = true
  let ok = 0, fail = 0
  for (const vid of selectedIds.value) {
    try { await resolveViolation(dirId.value, vid); ok++ } catch { fail++ }
  }
  actioning.value = false
  notif.success(`Resolved ${ok} violation${ok !== 1 ? 's' : ''}${fail ? `, ${fail} failed` : ''}`)
  selectedIds.value = new Set()
  await doRun()
}

async function bulkAcknowledge() {
  if (!selectedIds.value.size) return
  actioning.value = true
  let ok = 0, fail = 0
  for (const fid of selectedIds.value) {
    try { await acknowledgeFinding(dirId.value, fid); ok++ } catch { fail++ }
  }
  actioning.value = false
  notif.success(`Acknowledged ${ok} finding${ok !== 1 ? 's' : ''}${fail ? `, ${fail} failed` : ''}`)
  selectedIds.value = new Set()
  await doRun()
}

async function bulkExempt() {
  if (!selectedIds.value.size || !exemptReason.value.trim()) return
  actioning.value = true
  let ok = 0, fail = 0

  if (isSodReport.value) {
    const body = {
      reason: exemptReason.value.trim(),
      expiresAt: exemptExpires.value ? new Date(exemptExpires.value).toISOString() : null,
    }
    for (const vid of selectedIds.value) {
      try { await exemptViolation(dirId.value, vid, body); ok++ } catch { fail++ }
    }
  } else if (isDriftReport.value) {
    const body = { reason: exemptReason.value.trim() }
    for (const fid of selectedIds.value) {
      try { await exemptFinding(dirId.value, fid, body); ok++ } catch { fail++ }
    }
  }

  actioning.value = false
  showExemptModal.value = false
  exemptReason.value = ''
  exemptExpires.value = ''
  notif.success(`Exempted ${ok} finding${ok !== 1 ? 's' : ''}${fail ? `, ${fail} failed` : ''}`)
  selectedIds.value = new Set()
  await doRun()
}

// ── Cell rendering helpers ────────────────────────────────────────────────────

function isDateColumn(col, val) {
  return DATE_COLUMNS.has(col) || (col.toLowerCase().includes('timestamp') && looksLikeTimestamp(val))
}

function isDnColumn(col, val) {
  if (!val) return false
  const cl = col.toLowerCase()
  return (cl === 'dn' || cl.endsWith(' dn') || cl === 'user' || cl === 'target' || cl === 'user dn') && looksLikeDn(val)
}

function peerMatchColor(pct) {
  if (!pct) return ''
  const n = parseInt(pct)
  if (n <= 10) return 'text-red-600 font-semibold'
  if (n <= 30) return 'text-orange-600 font-semibold'
  if (n <= 60) return 'text-amber-600'
  return 'text-green-600'
}

function slaBadgeClass(status) {
  switch (status) {
    case 'Within SLA': return 'bg-green-100 text-green-700'
    case 'Overdue':    return 'bg-red-100 text-red-700'
    case 'Pending':    return 'bg-amber-100 text-amber-700'
    default:           return 'bg-gray-100 text-gray-600'
  }
}

function velocityColor(val) {
  if (!val) return ''
  if (val.includes('pending')) return 'text-amber-600'
  const match = val.match(/^(\d+)d/)
  if (match && parseInt(match[1]) > 1) return 'text-red-600 font-semibold'
  const hMatch = val.match(/^(\d+)h/)
  if (hMatch && parseInt(hMatch[1]) > 24) return 'text-red-600 font-semibold'
  if (hMatch && parseInt(hMatch[1]) > 4) return 'text-amber-600'
  return 'text-green-600'
}

// ── Sorting / params ──────────────────────────────────────────────────────────

function toggleSort(col) {
  if (sortCol.value === col) { sortAsc.value = !sortAsc.value }
  else { sortCol.value = col; sortAsc.value = true }
  page.value = 0
}

function buildParams() {
  const params = {}
  if (needsGroupDn.value && runForm.value.groupDn) params.groupDn = runForm.value.groupDn
  if (needsStatusFilter.value && runForm.value.statusFilter) params.status = runForm.value.statusFilter
  if (needsCampaign.value && runForm.value.campaignId) params.campaignId = runForm.value.campaignId
  if (needsPolicyFilter.value && runForm.value.policyId) params.policyId = runForm.value.policyId
  if (isAuditLog.value) {
    if (runForm.value.fromDate) params.from = runForm.value.fromDate
    if (runForm.value.toDate) params.to = runForm.value.toDate
    if (runForm.value.auditAction) params.action = runForm.value.auditAction
    if (!runForm.value.fromDate) params.lookbackDays = 30 // default fallback
  }
  if (isTermVelocity.value) {
    if (runForm.value.fromDate) params.from = runForm.value.fromDate
    if (runForm.value.toDate) params.to = runForm.value.toDate
    if (runForm.value.slaHours) params.slaHours = runForm.value.slaHours
    if (!runForm.value.fromDate) params.lookbackDays = 90
  }
  if (isPrivilegedReport.value && runForm.value.groupFilter) {
    params.groupFilter = runForm.value.groupFilter
  }
  return params
}

async function doRun() {
  if (!dirId.value) { notif.error('Please select a directory.'); return }
  running.value = true
  hasResults.value = false
  selectedIds.value = new Set()
  try {
    const { data } = await runReportData(dirId.value, {
      reportType: runForm.value.reportType,
      reportParams: buildParams(),
    })
    resultColumns.value = data.columns || []
    resultRows.value = data.rows || []
    hasResults.value = true
    sortCol.value = ''
    page.value = 0
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    running.value = false
  }
}

async function doExport(format) {
  if (!dirId.value) return
  exporting.value = true
  try {
    const { data } = await runReport(dirId.value, {
      reportType: runForm.value.reportType,
      reportParams: buildParams(),
      outputFormat: format,
    })
    const ext = format === 'PDF' ? '.pdf' : '.csv'
    downloadBlob(data, `${runForm.value.reportType.toLowerCase()}${ext}`)
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    exporting.value = false
  }
}

// ── Init ──────────────────────────────────────────────────────────────────────

async function loadCampaigns() {
  if (!dirId.value) return
  try {
    const { data } = await listCampaigns(dirId.value, { size: 100 })
    campaigns.value = data.content || data
  } catch (e) { console.warn('Failed to load campaigns:', e) }
}

async function loadSodPolicies() {
  if (!dirId.value) return
  try {
    const { data } = await listPolicies(dirId.value)
    sodPolicies.value = data.content || data
  } catch (e) { console.warn('Failed to load SoD policies:', e) }
}

watch(dirId, () => { loadCampaigns(); loadSodPolicies() })

onMounted(async () => {
  if (!routeDirId) {
    loadingDirs.value = true
    try {
      const { data } = await listDirectories()
      directories.value = data
      if (data.length === 1) selectedDir.value = data[0].id
    } catch (e) {
      notif.error(e.response?.data?.detail || e.message)
    } finally {
      loadingDirs.value = false
    }
  }
  await Promise.all([loadCampaigns(), loadSodPolicies()])
})
</script>

<style scoped>
@reference "tailwindcss";
</style>
