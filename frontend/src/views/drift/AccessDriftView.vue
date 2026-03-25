<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Access Drift Detection</h1>
        <p class="text-sm text-gray-500 mt-1">Detect privilege creep by comparing user access against their peer group.</p>
      </div>
      <div class="flex gap-2">
        <button @click="showRulesModal = true" class="btn-secondary text-sm">Manage Rules</button>
        <button @click="handleAnalyze" :disabled="analyzing" class="btn-primary text-sm">
          {{ analyzing ? 'Analyzing...' : 'Run Analysis' }}
        </button>
      </div>
    </div>

    <!-- Summary cards -->
    <div v-if="summary" class="grid grid-cols-4 gap-4 mb-6">
      <div class="rounded-xl border-2 p-5" :class="summary.openHigh > 0 ? 'border-red-200 bg-red-50/30' : 'border-gray-200'">
        <p class="text-sm font-medium text-gray-500 mb-1">High Severity</p>
        <p class="text-3xl font-bold" :class="summary.openHigh > 0 ? 'text-red-600' : 'text-green-700'">{{ summary.openHigh }}</p>
      </div>
      <div class="rounded-xl border-2 p-5" :class="summary.openMedium > 0 ? 'border-amber-200 bg-amber-50/30' : 'border-gray-200'">
        <p class="text-sm font-medium text-gray-500 mb-1">Medium Severity</p>
        <p class="text-3xl font-bold" :class="summary.openMedium > 0 ? 'text-amber-600' : 'text-green-700'">{{ summary.openMedium }}</p>
      </div>
      <div class="rounded-xl border-2 p-5 border-gray-200">
        <p class="text-sm font-medium text-gray-500 mb-1">Low Severity</p>
        <p class="text-3xl font-bold text-gray-700">{{ summary.openLow }}</p>
      </div>
      <div class="rounded-xl border-2 p-5 border-gray-200">
        <p class="text-sm font-medium text-gray-500 mb-1">Last Analysis</p>
        <p class="text-sm font-medium text-gray-700 mt-2">{{ summary.lastAnalysisAt ? new Date(summary.lastAnalysisAt).toLocaleString() : 'Never' }}</p>
      </div>
    </div>

    <!-- Analysis result banner -->
    <div v-if="analysisResult" class="mb-4 bg-blue-50 border border-blue-200 rounded-lg p-3 text-sm flex justify-between items-center">
      <span>
        Analysis complete: {{ analysisResult.totalFindings }} findings
        ({{ analysisResult.highFindings }} high, {{ analysisResult.mediumFindings }} medium, {{ analysisResult.lowFindings }} low)
        across {{ analysisResult.peerGroupsAnalyzed }} peer groups
      </span>
      <button @click="analysisResult = null" class="text-blue-400 hover:text-blue-600">Dismiss</button>
    </div>

    <!-- Filter -->
    <div class="flex gap-2 items-center mb-4">
      <label class="text-sm text-gray-600">Filter:</label>
      <select v-model="statusFilter" @change="loadFindings" class="border border-gray-300 rounded-lg px-3 py-1.5 text-sm">
        <option value="OPEN">Open</option>
        <option value="">All</option>
        <option value="ACKNOWLEDGED">Acknowledged</option>
        <option value="EXEMPTED">Exempted</option>
      </select>
    </div>

    <!-- Findings -->
    <div v-if="loading" class="text-gray-400">Loading...</div>
    <div v-else-if="!findings.length" class="bg-white rounded-lg border p-8 text-center text-gray-500">
      No drift findings{{ statusFilter ? ' with status ' + statusFilter : '' }}.
      {{ !summary?.lastAnalysisAt ? 'Run an analysis to detect privilege creep.' : '' }}
    </div>
    <div v-else class="space-y-2">
      <div v-for="f in findings" :key="f.id" class="bg-white rounded-lg border p-4 flex items-start gap-4">
        <span :class="severityBadge(f.severity)">{{ f.severity }}</span>
        <div class="flex-1 min-w-0">
          <div class="font-medium text-gray-900 text-sm">{{ f.userDisplay || f.userDn }}</div>
          <div class="text-xs text-gray-500">
            Peer group: <strong>{{ f.peerGroupValue }}</strong> ({{ f.peerGroupSize }} peers)
            &mdash; Rule: {{ f.ruleName }}
          </div>
          <div class="text-xs text-gray-500 mt-1">
            Anomalous membership: <strong>{{ f.groupName || f.groupDn }}</strong>
            &mdash; only {{ f.peerMembershipPct }}% of peers have this
          </div>
          <div v-if="f.exemptionReason" class="text-xs text-gray-400 mt-1">
            Exempted: {{ f.exemptionReason }}
          </div>
        </div>
        <div class="flex gap-2 shrink-0" v-if="f.status === 'OPEN'">
          <button @click="handleAcknowledge(f)" class="btn-secondary text-xs">Acknowledge</button>
          <button @click="openExempt(f)" class="btn-secondary text-xs">Exempt</button>
        </div>
        <span v-else class="text-xs px-2 py-0.5 rounded-full bg-gray-100 text-gray-500">{{ f.status }}</span>
      </div>
    </div>

    <!-- Exempt dialog -->
    <div v-if="exemptDialog" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50" @click.self="exemptDialog = null">
      <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-md">
        <h3 class="font-semibold text-gray-900 mb-3">Exempt Finding</h3>
        <div class="mb-4">
          <label class="block text-sm font-medium text-gray-700 mb-1">Reason *</label>
          <textarea v-model="exemptReason" rows="3" class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
            placeholder="Business justification..."></textarea>
        </div>
        <div class="flex gap-2 justify-end">
          <button @click="exemptDialog = null" class="btn-secondary text-sm">Cancel</button>
          <button @click="submitExempt" :disabled="!exemptReason.trim()" class="btn-primary text-sm">Confirm</button>
        </div>
      </div>
    </div>

    <!-- Rules modal -->
    <div v-if="showRulesModal" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50" @click.self="showRulesModal = false">
      <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-lg">
        <h3 class="font-semibold text-gray-900 mb-4">Peer Group Rules</h3>
        <div v-if="rules.length" class="space-y-2 mb-4">
          <div v-for="r in rules" :key="r.id" class="flex items-center justify-between bg-gray-50 rounded-lg p-3">
            <div>
              <div class="text-sm font-medium text-gray-900">{{ r.name }}</div>
              <div class="text-xs text-gray-500">Attribute: {{ r.groupingAttribute }} | Anomaly &lt; {{ r.anomalyThresholdPct }}%</div>
            </div>
            <button @click="handleDeleteRule(r)" class="text-red-500 hover:text-red-700 text-xs">Delete</button>
          </div>
        </div>
        <div v-else class="text-sm text-gray-400 mb-4">No rules configured.</div>
        <div class="border-t pt-4 space-y-3">
          <h4 class="text-sm font-medium text-gray-700">Add Rule</h4>
          <input v-model="newRule.name" placeholder="Rule name" class="input w-full" />
          <select v-model="newRule.groupingAttribute" class="input w-full">
            <option value="">— Grouping attribute —</option>
            <option value="department">department</option>
            <option value="title">title</option>
            <option value="ou">ou (organizational unit)</option>
            <option value="l">l (location)</option>
          </select>
          <div class="grid grid-cols-2 gap-2">
            <div>
              <label class="text-xs text-gray-500">Anomaly threshold %</label>
              <input v-model.number="newRule.anomalyThresholdPct" type="number" min="1" max="100" class="input w-full" />
            </div>
            <div>
              <label class="text-xs text-gray-500">Normal threshold %</label>
              <input v-model.number="newRule.normalThresholdPct" type="number" min="1" max="100" class="input w-full" />
            </div>
          </div>
          <button @click="handleCreateRule" :disabled="!newRule.name || !newRule.groupingAttribute"
            class="btn-primary text-sm w-full">Add Rule</button>
        </div>
        <div class="flex justify-end mt-4">
          <button @click="showRulesModal = false" class="btn-secondary text-sm">Close</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useApi } from '@/composables/useApi'
import {
  listRules, createRule, deleteRule,
  runAnalysis, listFindings, getFindingsSummary,
  acknowledgeFinding, exemptFinding,
} from '@/api/accessDrift'

const route = useRoute()
const { loading, call } = useApi()
const dirId = route.params.dirId

const findings = ref([])
const summary = ref(null)
const rules = ref([])
const statusFilter = ref('OPEN')
const analyzing = ref(false)
const analysisResult = ref(null)
const showRulesModal = ref(false)
const exemptDialog = ref(null)
const exemptReason = ref('')
const newRule = ref({ name: '', groupingAttribute: '', anomalyThresholdPct: 10, normalThresholdPct: 50 })

function severityBadge(s) {
  const base = 'px-2 py-0.5 rounded-full text-xs font-medium shrink-0'
  switch (s) {
    case 'HIGH': return base + ' bg-red-100 text-red-800'
    case 'MEDIUM': return base + ' bg-yellow-100 text-yellow-800'
    case 'LOW': return base + ' bg-blue-100 text-blue-800'
    default: return base + ' bg-gray-100 text-gray-800'
  }
}

async function loadFindings() {
  try {
    const params = statusFilter.value ? { status: statusFilter.value } : {}
    const res = await call(() => listFindings(dirId, params))
    findings.value = res.data
  } catch { /* handled */ }
}

async function loadSummary() {
  try {
    const res = await getFindingsSummary(dirId)
    summary.value = res.data
  } catch { /* ignore */ }
}

async function loadRules() {
  try {
    const res = await listRules(dirId)
    rules.value = res.data
  } catch { /* ignore */ }
}

async function handleAnalyze() {
  analyzing.value = true
  try {
    const res = await call(() => runAnalysis(dirId), { successMsg: 'Analysis complete' })
    analysisResult.value = res.data
    await loadFindings()
    await loadSummary()
  } catch { /* handled */ }
  analyzing.value = false
}

async function handleAcknowledge(f) {
  try {
    await call(() => acknowledgeFinding(dirId, f.id), { successMsg: 'Acknowledged' })
    await loadFindings()
    await loadSummary()
  } catch { /* handled */ }
}

function openExempt(f) {
  exemptDialog.value = f
  exemptReason.value = ''
}

async function submitExempt() {
  try {
    await call(() => exemptFinding(dirId, exemptDialog.value.id, { reason: exemptReason.value }),
      { successMsg: 'Exempted' })
    exemptDialog.value = null
    await loadFindings()
    await loadSummary()
  } catch { /* handled */ }
}

async function handleCreateRule() {
  try {
    await call(() => createRule(dirId, { ...newRule.value, enabled: true }), { successMsg: 'Rule created' })
    newRule.value = { name: '', groupingAttribute: '', anomalyThresholdPct: 10, normalThresholdPct: 50 }
    await loadRules()
  } catch { /* handled */ }
}

async function handleDeleteRule(r) {
  if (!confirm(`Delete rule "${r.name}"?`)) return
  try {
    await call(() => deleteRule(dirId, r.id), { successMsg: 'Rule deleted' })
    await loadRules()
  } catch { /* handled */ }
}

onMounted(() => {
  loadFindings()
  loadSummary()
  loadRules()
})
</script>

<style scoped>
@reference "tailwindcss";
.input { @apply w-full border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500; }
.btn-primary { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
.btn-secondary { @apply px-3 py-1.5 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50; }
</style>
