<template>
  <div class="min-h-screen bg-gray-50 flex flex-col items-center py-10 px-4">
    <div class="w-full max-w-2xl">
      <!-- Header -->
      <div class="text-center mb-8">
        <h1 class="text-2xl font-bold text-gray-900">{{ settings.appName }}</h1>
        <p class="text-sm text-gray-500 mt-1">First-Run Setup Wizard</p>
      </div>

      <!-- Step indicator -->
      <div class="flex items-center justify-center gap-2 mb-8">
        <template v-for="s in totalSteps" :key="s">
          <div
            class="w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium transition-colors"
            :class="s === step ? 'bg-blue-600 text-white' : s < step ? 'bg-green-500 text-white' : 'bg-gray-200 text-gray-500'"
          >{{ s }}</div>
          <div v-if="s < totalSteps" class="w-8 h-0.5" :class="s < step ? 'bg-green-400' : 'bg-gray-200'" />
        </template>
      </div>

      <!-- Step content -->
      <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6">

        <!-- ── Step 1: Welcome ────────────────────────────────────────── -->
        <template v-if="step === 1">
          <h2 class="text-xl font-semibold text-gray-900 mb-3">Welcome</h2>
          <p class="text-sm text-gray-600 mb-4">
            Let's get your LDAP directory connected in a few minutes. This wizard will walk you through:
          </p>
          <ol class="text-sm text-gray-600 list-decimal list-inside space-y-1 mb-6">
            <li>Connecting to your LDAP directory</li>
            <li>Verifying the connection works</li>
            <li>Creating a provisioning profile</li>
            <li>Optionally starting your first access review</li>
          </ol>
          <div class="flex justify-end">
            <button @click="step = 2" class="btn-primary">Get Started</button>
          </div>
        </template>

        <!-- ── Step 2: Connect LDAP ───────────────────────────────────── -->
        <template v-if="step === 2">
          <h2 class="text-xl font-semibold text-gray-900 mb-4">Connect LDAP Directory</h2>
          <div class="space-y-4">
            <div>
              <label class="label">Display Name *</label>
              <input v-model="dir.displayName" type="text" class="input" placeholder="e.g. Corporate Directory" />
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="label">Host *</label>
                <input v-model="dir.host" type="text" class="input" placeholder="ldap.example.com" />
              </div>
              <div>
                <label class="label">Port</label>
                <input v-model.number="dir.port" type="number" class="input" />
              </div>
            </div>
            <div>
              <label class="label">SSL Mode</label>
              <select v-model="dir.sslMode" class="input">
                <option value="NONE">None</option>
                <option value="LDAPS">LDAPS</option>
                <option value="STARTTLS">STARTTLS</option>
              </select>
            </div>
            <div>
              <label class="label">Bind DN *</label>
              <input v-model="dir.bindDn" type="text" class="input" placeholder="cn=admin,dc=example,dc=com" />
            </div>
            <div>
              <label class="label">Bind Password *</label>
              <input v-model="dir.bindPassword" type="password" class="input" />
            </div>
            <div>
              <label class="label">Base DN *</label>
              <input v-model="dir.baseDn" type="text" class="input" placeholder="dc=example,dc=com" />
            </div>
            <div class="flex items-center gap-2">
              <input v-model="dir.trustAllCerts" type="checkbox" id="trustCerts" class="rounded" />
              <label for="trustCerts" class="text-sm text-gray-600">Trust all certificates (dev/lab only)</label>
            </div>

            <!-- Test result -->
            <div v-if="testResult" class="rounded-lg px-4 py-3 text-sm" :class="testResult.ok ? 'bg-green-50 text-green-700 border border-green-200' : 'bg-red-50 text-red-700 border border-red-200'">
              {{ testResult.message }}
            </div>

            <div v-if="error" class="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm">{{ error }}</div>
          </div>

          <div class="flex justify-between mt-6">
            <button @click="step = 1" class="btn-neutral">Back</button>
            <div class="flex gap-3">
              <button @click="testConnection" :disabled="!canTest || testing" class="btn-secondary">
                {{ testing ? 'Testing...' : 'Test Connection' }}
              </button>
              <button @click="saveDirectory" :disabled="!canTest || saving" class="btn-primary">
                {{ saving ? 'Saving...' : 'Save & Continue' }}
              </button>
            </div>
          </div>
        </template>

        <!-- ── Step 3: Verify Connection ──────────────────────────────── -->
        <template v-if="step === 3">
          <h2 class="text-xl font-semibold text-gray-900 mb-4">Verify Connection</h2>
          <div v-if="verifying" class="text-sm text-gray-500 py-8 text-center">Querying directory...</div>
          <div v-else class="space-y-4">
            <div class="bg-gray-50 rounded-lg p-4">
              <p class="text-sm font-medium text-gray-700">{{ dir.displayName }}</p>
              <p class="text-xs text-gray-500">{{ dir.host }}:{{ dir.port }} &middot; Base DN: {{ dir.baseDn }}</p>
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div class="bg-blue-50 rounded-lg p-4 text-center">
                <p class="text-2xl font-bold text-blue-700">{{ verifyData.userCount }}</p>
                <p class="text-xs text-gray-600">Users found</p>
              </div>
              <div class="bg-blue-50 rounded-lg p-4 text-center">
                <p class="text-2xl font-bold text-blue-700">{{ verifyData.groupCount }}</p>
                <p class="text-xs text-gray-600">Groups found</p>
              </div>
            </div>
            <div v-if="verifyError"
                 class="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm">
              Failed to query directory: {{ verifyError }}
            </div>
            <div v-else-if="verifyData.userCount === 0 && verifyData.groupCount === 0"
                 class="bg-yellow-50 border border-yellow-200 text-yellow-700 rounded-lg px-4 py-3 text-sm">
              No entries found. Check your base DN and try again.
            </div>
            <div v-if="verifyData.sampleUsers.length" class="text-sm">
              <p class="font-medium text-gray-700 mb-1">Sample users:</p>
              <ul class="text-xs text-gray-500 space-y-0.5">
                <li v-for="u in verifyData.sampleUsers" :key="u">{{ u }}</li>
              </ul>
            </div>
          </div>
          <div class="flex justify-between mt-6">
            <button @click="step = 2" class="btn-neutral">Back</button>
            <button @click="step = 4" :disabled="verifying" class="btn-primary">Continue</button>
          </div>
        </template>

        <!-- ── Step 4: Create Profile ─────────────────────────────────── -->
        <template v-if="step === 4">
          <h2 class="text-xl font-semibold text-gray-900 mb-4">Create Provisioning Profile</h2>
          <div class="space-y-4">
            <div>
              <label class="label">Profile Name *</label>
              <input v-model="profile.name" type="text" class="input" />
            </div>
            <div>
              <label class="label">Target OU *</label>
              <DnPicker v-model="profile.targetOuDn" :directory-id="directoryId" />
            </div>
            <div>
              <label class="label">Object Classes *</label>
              <div class="flex flex-wrap gap-1 mb-2">
                <span v-for="oc in profile.objectClasses" :key="oc"
                      class="inline-flex items-center gap-1 bg-blue-100 text-blue-700 text-xs px-2 py-1 rounded-full">
                  {{ oc }}
                  <button @click="profile.objectClasses = profile.objectClasses.filter(x => x !== oc)" class="hover:text-blue-900">&times;</button>
                </span>
              </div>
              <select @change="addObjectClass($event)" class="input text-sm">
                <option value="">+ Add object class</option>
                <option v-for="oc in availableObjectClasses" :key="oc" :value="oc">{{ oc }}</option>
              </select>
            </div>
            <div>
              <label class="label">RDN Attribute</label>
              <input v-model="profile.rdnAttribute" type="text" class="input" />
            </div>
            <div v-if="error" class="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm">{{ error }}</div>

            <!-- Discovery Wizard callout -->
            <div class="bg-indigo-50 border border-indigo-200 rounded-lg px-4 py-3">
              <p class="text-sm text-indigo-800">
                <span class="font-medium">Migrating an existing directory with multiple OUs?</span>
                Use the Discovery Wizard to auto-generate profiles from your directory structure.
              </p>
              <p class="text-xs text-indigo-600 mt-1">Available from Directories management after setup is complete.</p>
            </div>
          </div>
          <div class="flex justify-between mt-6">
            <button @click="step = 3" class="btn-neutral">Back</button>
            <button @click="saveProfile" :disabled="!canSaveProfile || saving" class="btn-primary">
              {{ saving ? 'Saving...' : 'Save & Continue' }}
            </button>
          </div>
        </template>

        <!-- ── Step 5: Access Review (Optional) ──────────────────────── -->
        <template v-if="step === 5">
          <h2 class="text-xl font-semibold text-gray-900 mb-2">First Access Review</h2>
          <p class="text-sm text-gray-500 mb-4">Optional — create an access review campaign to audit group memberships.</p>
          <div class="space-y-4">
            <div>
              <label class="label">Campaign Name</label>
              <input v-model="campaign.name" type="text" class="input" />
            </div>
            <div>
              <label class="label">Deadline (days)</label>
              <input v-model.number="campaign.deadlineDays" type="number" min="1" class="input" />
            </div>
            <div>
              <label class="label">Group to Review</label>
              <GroupDnPicker v-model="campaign.groupDn" :directory-id="directoryId" />
            </div>
            <div v-if="error" class="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm">{{ error }}</div>
          </div>
          <div class="flex justify-between mt-6">
            <button @click="step = 4" class="btn-neutral">Back</button>
            <div class="flex gap-3">
              <button @click="step = 6" class="btn-secondary">Skip</button>
              <button @click="createReview" :disabled="!campaign.groupDn || campaign.deadlineDays < 1 || saving" class="btn-primary">
                {{ saving ? 'Creating...' : 'Create Campaign & Continue' }}
              </button>
            </div>
          </div>
        </template>

        <!-- ── Step 6: Done ───────────────────────────────────────────── -->
        <template v-if="step === 6">
          <h2 class="text-xl font-semibold text-gray-900 mb-4">Setup Complete</h2>
          <div class="space-y-3 mb-6">
            <div class="flex items-center gap-2 text-sm">
              <span class="text-green-500 font-bold">&#10003;</span>
              <span class="text-gray-700">Directory "<strong>{{ dir.displayName }}</strong>" connected at {{ dir.host }}:{{ dir.port }}</span>
            </div>
            <div class="flex items-center gap-2 text-sm">
              <span class="text-green-500 font-bold">&#10003;</span>
              <span class="text-gray-700">Profile "<strong>{{ profile.name }}</strong>" targeting {{ profile.targetOuDn }}</span>
            </div>
            <div class="flex items-center gap-2 text-sm">
              <span v-if="campaignId" class="text-green-500 font-bold">&#10003;</span>
              <span v-else class="text-gray-400 font-bold">&mdash;</span>
              <span class="text-gray-700">
                <template v-if="campaignId">Access review "<strong>{{ campaign.name }}</strong>" created</template>
                <template v-else>Access review skipped</template>
              </span>
            </div>
          </div>

          <div class="bg-indigo-50 border border-indigo-200 rounded-lg px-4 py-3 mb-6">
            <p class="text-sm text-indigo-800">
              <span class="font-medium">Want more profiles?</span>
              Use the Discovery Wizard from the Directories management page to auto-generate them from your directory structure.
            </p>
          </div>

          <div v-if="error" class="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm mb-4">{{ error }}</div>

          <div class="flex justify-end">
            <button @click="completeSetup" :disabled="saving" class="btn-primary">
              {{ saving ? 'Completing...' : 'Complete Setup & Go to Dashboard' }}
            </button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useSettingsStore } from '@/stores/settings'
import { testDirectory, createDirectory, updateDirectory } from '@/api/directories'
import { createProfile } from '@/api/profiles'
import { listObjectClasses } from '@/api/schema'
import { createCampaign } from '@/api/accessReviews'
import { completeSetup as apiCompleteSetup } from '@/api/settings'
import DnPicker from '@/components/DnPicker.vue'
import GroupDnPicker from '@/components/GroupDnPicker.vue'

const router = useRouter()
const auth = useAuthStore()
const settings = useSettingsStore()

const step = ref(1)
const totalSteps = 6
const saving = ref(false)
const testing = ref(false)
const error = ref('')
const testResult = ref(null)

// ── Step 2: Directory ──────────────────────────────────────────────────
const dir = ref({
  displayName: '',
  host: '',
  port: 389,
  sslMode: 'NONE',
  bindDn: '',
  bindPassword: '',
  baseDn: '',
  trustAllCerts: false,
})

const directoryId = ref(null)

const canTest = computed(() =>
  dir.value.displayName && dir.value.host && dir.value.bindDn && dir.value.bindPassword && dir.value.baseDn
    && dir.value.port > 0 && dir.value.port <= 65535
)

async function testConnection() {
  testing.value = true
  testResult.value = null
  error.value = ''
  try {
    const { data } = await testDirectory({
      host: dir.value.host,
      port: dir.value.port,
      sslMode: dir.value.sslMode,
      trustAllCerts: dir.value.trustAllCerts,
      bindDn: dir.value.bindDn,
      bindPassword: dir.value.bindPassword,
    })
    testResult.value = { ok: data.success, message: data.success ? `Connected successfully (${data.responseTimeMs}ms)` : data.error }
  } catch (e) {
    testResult.value = { ok: false, message: e.response?.data?.detail || e.response?.data?.message || e.message }
  } finally {
    testing.value = false
  }
}

async function saveDirectory() {
  saving.value = true
  error.value = ''
  try {
    const payload = {
      displayName: dir.value.displayName,
      host: dir.value.host,
      port: dir.value.port,
      sslMode: dir.value.sslMode,
      trustAllCerts: dir.value.trustAllCerts,
      trustedCertificatePem: null,
      bindDn: dir.value.bindDn,
      bindPassword: dir.value.bindPassword,
      baseDn: dir.value.baseDn,
      pagingSize: 500,
      poolMinSize: 2,
      poolMaxSize: 10,
      poolConnectTimeoutSeconds: 10,
      poolResponseTimeoutSeconds: 30,
      enableDisableAttribute: null,
      enableDisableValueType: null,
      enableValue: null,
      disableValue: null,
      auditDataSourceId: null,
      enabled: true,
    }
    if (directoryId.value) {
      // Update existing directory if user went back and changed settings
      await updateDirectory(directoryId.value, payload)
    } else {
      const { data } = await createDirectory(payload)
      directoryId.value = data.id
    }
    // Reset verification data so Step 3 re-queries with updated config
    verifyData.value = { userCount: 0, groupCount: 0, sampleUsers: [] }
    step.value = 3
  } catch (e) {
    error.value = e.response?.data?.detail || e.response?.data?.message || e.message
  } finally {
    saving.value = false
  }
}

// ── Step 3: Verify ─────────────────────────────────────────────────────
const verifying = ref(false)
const verifyError = ref('')
const verifyData = ref({ userCount: 0, groupCount: 0, sampleUsers: [], capped: false })

watch(step, async (s) => {
  if (s !== 3) return
  // Skip re-verification if already successfully loaded
  if (verifyData.value.userCount > 0 || verifyData.value.groupCount > 0) return
  verifying.value = true
  verifyError.value = ''
  try {
    const fetchLimit = 201 // fetch 201 to detect "more than 200"
    const [usersRes, groupsRes] = await Promise.all([
      import('@/api/users').then(m => m.searchUsers(directoryId.value, { limit: fetchLimit })),
      import('@/api/groups').then(m => m.searchGroups(directoryId.value, { limit: fetchLimit })),
    ])
    const users = Array.isArray(usersRes.data) ? usersRes.data : []
    const groups = Array.isArray(groupsRes.data) ? groupsRes.data : []
    verifyData.value = {
      userCount: users.length >= fetchLimit ? '200+' : users.length,
      groupCount: groups.length >= fetchLimit ? '200+' : groups.length,
      sampleUsers: users.slice(0, 5).map(u => u.dn || u.attributes?.dn || 'unknown'),
    }
  } catch (e) {
    verifyError.value = e.response?.data?.detail || e.response?.data?.message || e.message
  } finally {
    verifying.value = false
  }
})

// ── Step 4: Profile ────────────────────────────────────────────────────
const profile = ref({
  name: '',
  targetOuDn: '',
  objectClasses: ['inetOrgPerson', 'organizationalPerson', 'person', 'top'],
  rdnAttribute: 'uid',
})
const schemaObjectClasses = ref([])

const availableObjectClasses = computed(() =>
  schemaObjectClasses.value.filter(oc => !profile.value.objectClasses.includes(oc))
)

const canSaveProfile = computed(() =>
  profile.value.name && profile.value.targetOuDn && profile.value.objectClasses.length > 0 && profile.value.rdnAttribute
)

function addObjectClass(event) {
  const val = event.target.value
  if (val && !profile.value.objectClasses.includes(val)) {
    profile.value.objectClasses.push(val)
  }
  event.target.value = ''
}

// Load schema when entering step 4
watch(step, async (s) => {
  if (s !== 4) return
  if (!profile.value.name) profile.value.name = dir.value.displayName
  if (!profile.value.targetOuDn) profile.value.targetOuDn = dir.value.baseDn
  if (schemaObjectClasses.value.length === 0 && directoryId.value) {
    try {
      const { data } = await listObjectClasses(directoryId.value)
      schemaObjectClasses.value = data.map(oc => oc.name).sort()
    } catch (e) { console.warn('Schema load failed:', e) }
  }
})

const profileId = ref(null)

async function saveProfile() {
  if (profileId.value) {
    // Profile already created — skip to next step
    step.value = 5
    return
  }
  saving.value = true
  error.value = ''
  try {
    const { data } = await createProfile(directoryId.value, {
      name: profile.value.name,
      targetOuDn: profile.value.targetOuDn,
      objectClassNames: profile.value.objectClasses,
      rdnAttribute: profile.value.rdnAttribute,
      showDnField: false,
      enabled: true,
      selfRegistrationAllowed: false,
      autoIncludeGroups: false,
      excludeAutoIncludes: false,
      attributeConfigs: [],
      groupAssignments: [],
    })
    profileId.value = data.id
    step.value = 5
  } catch (e) {
    error.value = e.response?.data?.detail || e.response?.data?.message || e.message
  } finally {
    saving.value = false
  }
}

// ── Step 5: Access Review ──────────────────────────────────────────────
const campaign = ref({
  name: 'Initial Access Review',
  deadlineDays: 30,
  groupDn: '',
})
const campaignId = ref(null)

async function createReview() {
  saving.value = true
  error.value = ''
  try {
    const { data } = await createCampaign(directoryId.value, {
      name: campaign.value.name,
      deadlineDays: campaign.value.deadlineDays,
      autoRevoke: false,
      autoRevokeOnExpiry: false,
      groups: [{
        groupDn: campaign.value.groupDn,
        memberAttribute: 'member',
        reviewerAccountId: auth.principal.id,
      }],
    })
    campaignId.value = data.id
    step.value = 6
  } catch (e) {
    error.value = e.response?.data?.detail || e.response?.data?.message || e.message
  } finally {
    saving.value = false
  }
}

// ── Step 6: Complete ───────────────────────────────────────────────────
async function completeSetup() {
  saving.value = true
  error.value = ''
  try {
    await apiCompleteSetup()
    auth.markSetupComplete()
    router.push('/superadmin/dashboard')
  } catch (e) {
    error.value = e.response?.data?.detail || e.response?.data?.message || e.message
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
@reference "tailwindcss";
.label { @apply block text-xs font-medium text-gray-600 mb-1; }
.input { @apply w-full border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500; }
</style>
