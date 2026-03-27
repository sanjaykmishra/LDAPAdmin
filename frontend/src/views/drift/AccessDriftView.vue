<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Access Drift Detection</h1>
        <p class="text-sm text-gray-500 mt-1">Detect privilege creep by comparing user access against their peer group.</p>
      </div>
    </div>

    <!-- Directory picker + action buttons -->
    <div v-if="showPicker" class="mb-4 flex items-end gap-3">
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">Directory</label>
        <select v-model="selectedDir" class="w-64 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="" disabled>{{ loadingDirs ? 'Loading…' : '— Select directory —' }}</option>
          <option v-for="d in directories" :key="d.id" :value="d.id">{{ d.displayName }}</option>
        </select>
      </div>
      <button @click="showRulesModal = true" class="btn-secondary text-sm h-[38px]">Manage Rules</button>
      <button @click="handleAnalyze" :disabled="analyzing || !dirId" class="btn-primary text-sm h-[38px]">
        {{ analyzing ? 'Analyzing...' : 'Run Analysis' }}
      </button>
    </div>
    <div v-else class="mb-4 flex gap-2">
      <button @click="showRulesModal = true" class="btn-secondary text-sm">Manage Rules</button>
      <button @click="handleAnalyze" :disabled="analyzing || !dirId" class="btn-primary text-sm">
        {{ analyzing ? 'Analyzing...' : 'Run Analysis' }}
      </button>
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

    <!-- View toggle -->
    <div class="flex gap-1 mb-4 bg-gray-100 p-1 rounded-lg w-fit">
      <button @click="viewMode = 'findings'" :class="viewMode === 'findings' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'" class="px-4 py-1.5 rounded-md text-sm font-medium transition-colors">Findings</button>
      <button @click="viewMode = 'visualization'; loadVisualization()" :class="viewMode === 'visualization' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'" class="px-4 py-1.5 rounded-md text-sm font-medium transition-colors">Visualization</button>
    </div>

    <!-- ═══ Findings tab ═══ -->
    <template v-if="viewMode === 'findings'">

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

    </template>

    <!-- ═══ Visualization tab ═══ -->
    <template v-if="viewMode === 'visualization'">
      <div v-if="vizLoading" class="text-gray-400 text-sm py-8 text-center">Loading visualization...</div>
      <div v-else-if="!vizData?.peerGroups?.length" class="bg-white rounded-lg border p-8 text-center text-gray-500">
        No snapshot data available. Run an analysis first.
      </div>
      <div v-else class="space-y-6">
        <!-- Heatmap -->
        <div class="bg-white border border-gray-200 rounded-xl p-5">
          <h3 class="text-sm font-semibold text-gray-900 mb-3">Group Membership by Department</h3>
          <div class="overflow-x-auto">
            <table class="text-xs">
              <thead>
                <tr>
                  <th class="text-left py-1.5 px-2 font-medium text-gray-500 sticky left-0 bg-white min-w-[140px]">Department</th>
                  <th v-for="g in heatmapGroups" :key="g" class="py-1.5 px-2 font-medium text-gray-500 text-center whitespace-nowrap" :title="g">
                    {{ g.length > 14 ? g.slice(0, 12) + '…' : g }}
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="pg in vizData.peerGroups" :key="pg.name" class="border-t border-gray-100">
                  <td class="py-1.5 px-2 font-medium text-gray-700 sticky left-0 bg-white">
                    {{ pg.name }} <span class="text-gray-400 font-normal">({{ pg.userCount }})</span>
                  </td>
                  <td v-for="g in heatmapGroups" :key="g" class="py-1.5 px-2 text-center">
                    <span v-if="heatmapCell(pg, g) !== null"
                      :class="heatmapColor(heatmapCell(pg, g))"
                      class="inline-block w-10 py-0.5 rounded text-xs font-mono"
                      :title="`${pg.name} → ${g}: ${heatmapCell(pg, g)}%`">
                      {{ heatmapCell(pg, g) }}
                    </span>
                    <span v-else class="text-gray-200">—</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Per peer-group detail with bar chart -->
        <div v-for="pg in vizData.peerGroups" :key="pg.name" class="bg-white border border-gray-200 rounded-xl p-5">
          <div class="flex items-center justify-between mb-3">
            <h3 class="text-sm font-semibold text-gray-900">{{ pg.name }} <span class="font-normal text-gray-400">({{ pg.userCount }} users)</span></h3>
            <span v-if="pg.outliers.length" class="text-xs bg-red-50 text-red-600 px-2 py-0.5 rounded-full font-medium">{{ pg.outliers.length }} outlier{{ pg.outliers.length !== 1 ? 's' : '' }}</span>
          </div>

          <!-- Bar chart -->
          <div class="space-y-1.5 mb-4">
            <div v-for="g in pg.groups.slice(0, 15)" :key="g.groupName" class="flex items-center gap-2">
              <span class="text-xs text-gray-600 w-32 truncate shrink-0" :title="g.groupName">{{ g.groupName }}</span>
              <div class="flex-1 bg-gray-100 rounded-full h-4 overflow-hidden">
                <div :style="{ width: g.membershipPct + '%' }"
                  :class="g.membershipPct > 80 ? 'bg-blue-500' : g.membershipPct > 30 ? 'bg-blue-400' : g.membershipPct > 10 ? 'bg-amber-400' : 'bg-red-400'"
                  class="h-4 rounded-full transition-all"></div>
              </div>
              <span class="text-xs font-mono text-gray-500 w-10 text-right shrink-0">{{ g.membershipPct }}%</span>
            </div>
          </div>

          <!-- Outlier cards -->
          <div v-if="pg.outliers.length" class="border-t border-gray-100 pt-3">
            <p class="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Outliers</p>
            <div class="flex flex-wrap gap-2">
              <div v-for="o in pg.outliers" :key="o.userDn" class="bg-red-50 border border-red-100 rounded-lg px-3 py-2 text-xs">
                <div class="font-medium text-gray-900">{{ o.displayName || o.userDn }}</div>
                <div class="flex flex-wrap gap-1 mt-1">
                  <span v-for="g in o.extraGroups" :key="g" class="bg-red-100 text-red-700 px-1.5 py-0.5 rounded text-xs">{{ g }}</span>
                </div>
                <span :class="o.severity === 'HIGH' ? 'text-red-600' : o.severity === 'MEDIUM' ? 'text-amber-600' : 'text-gray-500'" class="text-xs font-medium mt-1 block">{{ o.severity }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

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
          <button @click="exemptDialog = null" class="btn-neutral text-sm">Cancel</button>
          <button @click="submitExempt" :disabled="!exemptReason.trim()" class="btn-primary text-sm">Confirm</button>
        </div>
      </div>
    </div>

    <!-- Rules modal -->
    <div v-if="showRulesModal" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50" @click.self="showRulesModal = false">
      <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-lg">
        <h3 class="font-semibold text-gray-900 mb-4">Peer Group Rules</h3>

        <!-- How it works -->
        <details class="mb-4 bg-blue-50 border border-blue-200 rounded-lg text-sm">
          <summary class="px-4 py-2.5 cursor-pointer font-medium text-blue-800 select-none hover:bg-blue-100 rounded-lg">
            How does drift analysis work?
          </summary>
          <div class="px-4 pb-3 pt-1 text-blue-900 space-y-2">
            <p><strong>Peer group comparison</strong> — Users are grouped into peer cohorts based on a shared
              LDAP attribute (e.g. <code class="bg-blue-100 px-1 rounded">departmentNumber</code> groups all
              Engineering users together). Each rule defines which attribute to use.</p>
            <p><strong>Group membership norm</strong> — For each peer group, the system calculates what percentage
              of peers belong to each access group. For example, if 18 of 20 Engineering users are in
              <code class="bg-blue-100 px-1 rounded">AWS-Developers</code>, that's a 90% peer membership rate.</p>
            <p><strong>Anomaly detection</strong> — A user is flagged when they belong to a group that fewer than
              the <em>anomaly threshold %</em> of their peers also belong to. For example, if only 1 of 20
              Engineering users (5%) is in <code class="bg-blue-100 px-1 rounded">Payroll-Admin</code> and the
              anomaly threshold is 10%, that membership is flagged as drift.</p>
            <p><strong>Severity</strong> — <span class="inline-block bg-red-100 text-red-800 px-1.5 rounded">HIGH</span>
              when peer membership is ≤ 5%,
              <span class="inline-block bg-yellow-100 text-yellow-800 px-1.5 rounded">MEDIUM</span> when between
              5% and the anomaly threshold,
              <span class="inline-block bg-blue-100 text-blue-800 px-1.5 rounded">LOW</span> otherwise.</p>
            <p><strong>Normal threshold</strong> — Groups where peer membership exceeds this percentage are
              considered standard for that cohort and are excluded from analysis.</p>
          </div>
        </details>

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
            <option value="departmentNumber">departmentNumber (inetOrgPerson)</option>
            <option value="title">title</option>
            <option value="ou">ou (organizational unit)</option>
          </select>
          <p class="text-xs text-gray-400 mt-1">Attribute used to group users into peer cohorts for comparison.</p>
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
          <button @click="showRulesModal = false" class="btn-neutral text-sm">Close</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useApi } from '@/composables/useApi'
import { useDirectoryPicker } from '@/composables/useDirectoryPicker'
import {
  listRules, createRule, deleteRule,
  runAnalysis, listFindings, getFindingsSummary,
  acknowledgeFinding, exemptFinding, getDriftVisualization,
} from '@/api/accessDrift'

const { dirId, directories, selectedDir, loadingDirs, showPicker } = useDirectoryPicker()
const { loading, call } = useApi()

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

// Visualization state
const viewMode = ref('findings')
const vizData = ref(null)
const vizLoading = ref(false)

// Heatmap helpers
const heatmapGroups = computed(() => {
  if (!vizData.value?.peerGroups?.length) return []
  const groupSet = new Set()
  for (const pg of vizData.value.peerGroups) {
    for (const g of pg.groups) groupSet.add(g.groupName)
  }
  return [...groupSet].slice(0, 20) // limit to top 20 groups
})

function heatmapCell(pg, groupName) {
  const g = pg.groups.find(x => x.groupName === groupName)
  return g ? g.membershipPct : null
}

function heatmapColor(pct) {
  if (pct >= 80) return 'bg-blue-500 text-white'
  if (pct >= 50) return 'bg-blue-400 text-white'
  if (pct >= 20) return 'bg-blue-100 text-blue-800'
  if (pct > 0) return 'bg-amber-100 text-amber-800'
  return 'bg-gray-50 text-gray-400'
}

async function loadVisualization() {
  if (!dirId.value || vizData.value) return
  vizLoading.value = true
  try {
    const { data } = await getDriftVisualization(dirId.value)
    vizData.value = data
  } catch (e) {
    console.warn('Failed to load visualization:', e)
  } finally {
    vizLoading.value = false
  }
}

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
    const res = await call(() => listFindings(dirId.value, params))
    findings.value = res.data
  } catch { /* handled */ }
}

async function loadSummary() {
  try {
    const res = await getFindingsSummary(dirId.value)
    summary.value = res.data
  } catch { /* ignore */ }
}

async function loadRules() {
  try {
    const res = await listRules(dirId.value)
    rules.value = res.data
  } catch { /* ignore */ }
}

async function handleAnalyze() {
  analyzing.value = true
  try {
    // Capture pre-analysis snapshot timestamp for comparison
    const preSummary = await getFindingsSummary(dirId.value)
    const preCaptured = preSummary.data?.lastAnalysisAt

    await call(() => runAnalysis(dirId.value), { successMsg: 'Analysis started…' })

    // Poll until a new snapshot appears (analysis complete)
    let attempts = 0
    const maxAttempts = 30
    while (attempts < maxAttempts) {
      await new Promise(r => setTimeout(r, 2000))
      attempts++
      const res = await getFindingsSummary(dirId.value)
      const newCaptured = res.data?.lastAnalysisAt
      if (newCaptured && newCaptured !== preCaptured) break
    }
    await loadFindings()
    await loadSummary()
    vizData.value = null // reset visualization to reload with new data
  } catch { /* handled */ }
  analyzing.value = false
}

async function handleAcknowledge(f) {
  try {
    await call(() => acknowledgeFinding(dirId.value, f.id), { successMsg: 'Acknowledged' })
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
    await call(() => exemptFinding(dirId.value, exemptDialog.value.id, { reason: exemptReason.value }),
      { successMsg: 'Exempted' })
    exemptDialog.value = null
    await loadFindings()
    await loadSummary()
  } catch { /* handled */ }
}

async function handleCreateRule() {
  try {
    await call(() => createRule(dirId.value, { ...newRule.value, enabled: true }), { successMsg: 'Rule created' })
    newRule.value = { name: '', groupingAttribute: '', anomalyThresholdPct: 10, normalThresholdPct: 50 }
    await loadRules()
  } catch { /* handled */ }
}

async function handleDeleteRule(r) {
  if (!confirm(`Delete rule "${r.name}"?`)) return
  try {
    await call(() => deleteRule(dirId.value, r.id), { successMsg: 'Rule deleted' })
    await loadRules()
  } catch { /* handled */ }
}

function loadAll() {
  if (!dirId.value) return
  vizData.value = null // reset visualization on dir change
  loadFindings()
  loadSummary()
  loadRules()
}
watch(dirId, () => loadAll())
onMounted(loadAll)
</script>

<style scoped>
@reference "tailwindcss";
.input { @apply w-full border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500; }
</style>
