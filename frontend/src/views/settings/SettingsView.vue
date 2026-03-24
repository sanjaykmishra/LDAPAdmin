<template>
  <div class="p-6 max-w-3xl">
    <h1 class="text-2xl font-bold text-gray-900 mb-4">Application Settings</h1>

    <div v-if="loading" class="text-gray-500 text-sm">Loading…</div>

    <form v-else @submit.prevent="doSave" class="space-y-4">

      <!-- Theme -->
      <section class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-6">
        <h2 class="text-base font-semibold text-gray-900 dark:text-gray-100 mb-3">Theme</h2>
        <div class="flex gap-3">
          <button v-for="opt in themeOptions" :key="opt.value"
            @click="setTheme(opt.value)"
            :class="['px-4 py-2 rounded-lg text-sm font-medium border transition-colors',
              theme === opt.value
                ? 'bg-blue-50 dark:bg-blue-900 border-blue-300 dark:border-blue-600 text-blue-700 dark:text-blue-300'
                : 'border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700']">
            {{ opt.label }}
          </button>
        </div>
      </section>

      <!-- Branding -->
      <section class="bg-white border border-gray-200 rounded-xl p-6">
        <h2 class="text-base font-semibold text-gray-900 mb-3">Branding</h2>
        <div class="grid grid-cols-2 gap-3">
          <FormField label="Application Name" v-model="form.appName" required />
          <FormField label="Logo URL" v-model="form.logoUrl" placeholder="https://…/logo.png" />
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Primary Colour</label>
            <div class="flex items-center gap-2">
              <input type="color" v-model="form.primaryColour" class="h-9 w-10 rounded border border-gray-300 cursor-pointer p-0.5" />
              <input type="text" v-model="form.primaryColour" placeholder="#3b82f6" class="input flex-1" />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Secondary Colour</label>
            <div class="flex items-center gap-2">
              <input type="color" v-model="form.secondaryColour" class="h-9 w-10 rounded border border-gray-300 cursor-pointer p-0.5" />
              <input type="text" v-model="form.secondaryColour" placeholder="#64748b" class="input flex-1" />
            </div>
          </div>
        </div>
      </section>

      <!-- Approval Workflow -->
      <section class="bg-white border border-gray-200 rounded-xl p-6">
        <h2 class="text-base font-semibold text-gray-900 mb-3">Approval Workflow</h2>
        <div class="flex items-center gap-2">
          <input type="checkbox" id="superadminBypass" v-model="form.superadminBypassApproval" class="rounded" />
          <label for="superadminBypass" class="text-sm text-gray-700">Superadmins bypass approval workflow</label>
        </div>
        <p class="text-xs text-gray-400 mt-1">When enabled, requests submitted by superadmins are auto-approved immediately. An audit record is still created.</p>
      </section>

      <!-- Session -->
      <section class="bg-white border border-gray-200 rounded-xl p-6">
        <h2 class="text-base font-semibold text-gray-900 mb-3">Session</h2>
        <div class="max-w-xs">
          <FormField label="Session Timeout (minutes)" v-model.number="form.sessionTimeoutMinutes" type="number" required />
        </div>
      </section>

      <!-- Authentication -->
      <section class="bg-white border border-gray-200 rounded-xl p-6">
        <h2 class="text-base font-semibold text-gray-900 mb-3">Authentication</h2>

        <!-- Enabled auth methods -->
        <div class="mb-4">
          <label class="block text-sm font-medium text-gray-700 mb-2">Enabled login methods</label>
          <div class="flex gap-6">
            <label v-for="t in ['LOCAL', 'LDAP', 'OIDC']" :key="t" class="flex items-center gap-2">
              <input type="checkbox" :value="t" v-model="form.enabledAuthTypes"
                class="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
              <span class="text-sm text-gray-700">{{ t }}</span>
            </label>
          </div>
          <p class="text-xs text-gray-400 mt-1">At least one method must remain enabled.</p>
        </div>

        <!-- LDAP Auth Provider -->
        <div v-if="form.enabledAuthTypes.includes('LDAP')" class="border-t border-gray-100 pt-4 mb-3">
          <h3 class="text-sm font-semibold text-gray-700 mb-3">LDAP Auth Provider</h3>
          <div class="grid grid-cols-2 gap-3">
            <FormField label="Host" v-model="form.ldapAuthHost" placeholder="ldap.example.com" />
            <FormField label="Port" v-model.number="form.ldapAuthPort" type="number" placeholder="389" />
            <FormField label="SSL Mode" v-model="form.ldapAuthSslMode" type="select"
              :options="[{ value: '', label: 'None' }, { value: 'STARTTLS', label: 'STARTTLS' }, { value: 'LDAPS', label: 'LDAPS' }]" />
            <div class="flex items-center gap-2 pt-6">
              <input type="checkbox" id="ldapTrustAll" v-model="form.ldapAuthTrustAllCerts" class="rounded" />
              <label for="ldapTrustAll" class="text-sm text-gray-700">Trust all certificates</label>
            </div>
            <div class="col-span-2">
              <label class="block text-sm font-medium text-gray-700 mb-1">Trusted Certificate (PEM)</label>
              <textarea v-model="form.ldapAuthTrustedCertPem" rows="3" placeholder="-----BEGIN CERTIFICATE-----"
                class="input w-full font-mono text-xs"></textarea>
            </div>
            <FormField label="Service Account Bind DN" v-model="form.ldapAuthBindDn"
              placeholder="cn=admin,dc=example,dc=com" />
            <div>
              <FormField label="Bind Password" v-model="form.ldapAuthBindPassword" type="password"
                :placeholder="settings?.ldapAuthBindPasswordConfigured ? '●●●●●●●● (leave blank to keep)' : 'Set password'" />
              <p class="text-xs text-gray-400 mt-1">Leave blank to keep existing. Enter a space to clear.</p>
            </div>
            <FormField label="User Search Base" v-model="form.ldapAuthUserSearchBase"
              placeholder="ou=people,dc=example,dc=com" />
            <FormField label="Bind DN Pattern" v-model="form.ldapAuthBindDnPattern"
              placeholder="uid={username},ou=people,dc=example,dc=com"
              hint="{username} is replaced with the login username." />
          </div>
        </div>

        <!-- OIDC Provider -->
        <div v-if="form.enabledAuthTypes.includes('OIDC')" class="border-t border-gray-100 pt-4">
          <h3 class="text-sm font-semibold text-gray-700 mb-3">OIDC Provider</h3>
          <div class="grid grid-cols-2 gap-3">
            <div class="col-span-2">
              <FormField label="Issuer URL" v-model="form.oidcIssuerUrl"
                placeholder="https://accounts.google.com"
                hint="Must support OpenID Connect Discovery (/.well-known/openid-configuration)." />
            </div>
            <FormField label="Client ID" v-model="form.oidcClientId" placeholder="your-client-id" />
            <div>
              <FormField label="Client Secret" v-model="form.oidcClientSecret" type="password"
                :placeholder="settings?.oidcClientSecretConfigured ? '●●●●●●●● (leave blank to keep)' : 'Set secret'" />
              <p class="text-xs text-gray-400 mt-1">Leave blank to keep existing. Enter a space to clear.</p>
            </div>
            <FormField label="Scopes" v-model="form.oidcScopes" placeholder="openid profile email" />
            <FormField label="Username Claim" v-model="form.oidcUsernameClaim" placeholder="preferred_username"
              hint="ID token claim matched against Account.username." />
          </div>
        </div>
      </section>

      <!-- SMTP -->
      <section class="bg-white border border-gray-200 rounded-xl p-6">
        <h2 class="text-base font-semibold text-gray-900 mb-3">SMTP (Email Delivery)</h2>
        <div class="grid grid-cols-2 gap-3">
          <FormField label="SMTP Host" v-model="form.smtpHost" placeholder="smtp.example.com" />
          <FormField label="SMTP Port" v-model.number="form.smtpPort" type="number" placeholder="587" />
          <FormField label="Sender Address" v-model="form.smtpSenderAddress" placeholder="noreply@example.com" />
          <FormField label="Username" v-model="form.smtpUsername" placeholder="username" />
          <div class="col-span-2">
            <FormField
              label="Password"
              v-model="form.smtpPassword"
              type="password"
              :placeholder="settings?.smtpPasswordConfigured ? '●●●●●●●● (leave blank to keep)' : 'Set password'"
            />
            <p class="text-xs text-gray-400 mt-1">
              Leave blank to keep existing{{ settings?.smtpPasswordConfigured ? '' : ' (none set)' }}. Enter a space to clear.
            </p>
          </div>
          <div class="flex items-center gap-2">
            <input type="checkbox" id="smtpTls" v-model="form.smtpUseTls" class="rounded" />
            <label for="smtpTls" class="text-sm text-gray-700">Use TLS / STARTTLS</label>
          </div>
        </div>
      </section>

      <!-- S3 -->
      <section class="bg-white border border-gray-200 rounded-xl p-6">
        <h2 class="text-base font-semibold text-gray-900 mb-3">S3 (Report Storage)</h2>
        <div class="grid grid-cols-2 gap-3">
          <FormField label="Endpoint URL" v-model="form.s3EndpointUrl" placeholder="https://s3.amazonaws.com" />
          <FormField label="Bucket Name" v-model="form.s3BucketName" placeholder="my-reports-bucket" />
          <FormField label="Access Key" v-model="form.s3AccessKey" placeholder="AKIAIOSFODNN7EXAMPLE" />
          <div>
            <FormField
              label="Secret Key"
              v-model="form.s3SecretKey"
              type="password"
              :placeholder="settings?.s3SecretKeyConfigured ? '●●●●●●●● (leave blank to keep)' : 'Set secret key'"
            />
            <p class="text-xs text-gray-400 mt-1">Leave blank to keep existing. Enter a space to clear.</p>
          </div>
          <FormField label="Region" v-model="form.s3Region" placeholder="us-east-1" />
          <FormField label="Pre-signed URL TTL (hours)" v-model.number="form.s3PresignedUrlTtlHours" type="number" placeholder="24" />
        </div>
      </section>

      <!-- SIEM / Syslog Export -->
      <section class="bg-white border border-gray-200 rounded-xl p-6">
        <h2 class="text-base font-semibold text-gray-900 mb-3">SIEM / Syslog Export</h2>
        <p class="text-xs text-gray-500 mb-4">Forward audit events in real-time to a SIEM, syslog collector, or webhook endpoint.</p>

        <div class="flex items-center gap-2 mb-4">
          <input type="checkbox" id="siemEnabled" v-model="form.siemEnabled" class="rounded" />
          <label for="siemEnabled" class="text-sm text-gray-700">Enable SIEM export</label>
        </div>

        <div v-if="form.siemEnabled" class="space-y-4">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Protocol</label>
              <select v-model="form.siemProtocol"
                class="input w-full">
                <option value="">-- select --</option>
                <option value="SYSLOG_UDP">Syslog (UDP)</option>
                <option value="SYSLOG_TCP">Syslog (TCP)</option>
                <option value="WEBHOOK">Webhook (HTTPS)</option>
              </select>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Format</label>
              <select v-model="form.siemFormat"
                class="input w-full">
                <option value="">-- select --</option>
                <option value="RFC5424">RFC 5424 (Syslog)</option>
                <option value="CEF">CEF (Common Event Format)</option>
                <option value="JSON">JSON</option>
              </select>
            </div>
          </div>

          <!-- Syslog host/port (shown for SYSLOG_UDP and SYSLOG_TCP) -->
          <div v-if="form.siemProtocol === 'SYSLOG_UDP' || form.siemProtocol === 'SYSLOG_TCP'"
            class="grid grid-cols-2 gap-3">
            <FormField label="Host" v-model="form.siemHost" placeholder="siem.example.com" />
            <FormField label="Port" v-model.number="form.siemPort" type="number" placeholder="514" />
            <div>
              <FormField label="Auth Token" v-model="form.siemAuthToken" type="password"
                :placeholder="settings?.siemAuthTokenConfigured ? '●●●●●●●● (leave blank to keep)' : 'Optional'" />
              <p class="text-xs text-gray-400 mt-1">Optional bearer token for authenticated syslog. Leave blank to keep existing.</p>
            </div>
          </div>

          <!-- Webhook URL (shown for WEBHOOK) -->
          <div v-if="form.siemProtocol === 'WEBHOOK'" class="grid grid-cols-2 gap-3">
            <div class="col-span-2">
              <FormField label="Webhook URL" v-model="form.webhookUrl" placeholder="https://hooks.example.com/audit" />
            </div>
            <div class="col-span-2">
              <FormField label="Authorization Header" v-model="form.webhookAuthHeader" type="password"
                :placeholder="settings?.webhookAuthHeaderConfigured ? '●●●●●●●● (leave blank to keep)' : 'e.g. Bearer your-token'" />
              <p class="text-xs text-gray-400 mt-1">Sent as the Authorization header on each webhook request.</p>
            </div>
          </div>

          <!-- Test button -->
          <div class="flex items-center gap-3 pt-2">
            <button type="button" @click="doTestSiem" :disabled="testingSiem" class="btn-secondary text-sm">
              {{ testingSiem ? 'Testing…' : 'Test Connection' }}
            </button>
            <span v-if="siemTestResult" class="text-sm" :class="siemTestResult.ok ? 'text-green-600' : 'text-red-600'">
              {{ siemTestResult.message }}
            </span>
          </div>
        </div>
      </section>

      <div class="flex justify-end">
        <button type="submit" :disabled="saving" class="btn-primary">
          {{ saving ? 'Saving…' : 'Save Settings' }}
        </button>
      </div>
    </form>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useNotificationStore } from '@/stores/notifications'
import { useSettingsStore } from '@/stores/settings'
import { useTheme } from '@/composables/useTheme'
import { getSettings, updateSettings, testSiem } from '@/api/settings'
import FormField from '@/components/FormField.vue'

const { theme, setTheme } = useTheme()
const themeOptions = [
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
  { value: 'system', label: 'System' },
]

const notif         = useNotificationStore()
const settingsStore = useSettingsStore()
const loading = ref(false)
const saving  = ref(false)
const settings = ref(null)
const testingSiem = ref(false)
const siemTestResult = ref(null)

const form = ref({
  appName: 'LDAP Portal',
  logoUrl: '',
  primaryColour: '#3b82f6',
  secondaryColour: '#64748b',
  superadminBypassApproval: false,
  sessionTimeoutMinutes: 60,
  smtpHost: '',
  smtpPort: 587,
  smtpSenderAddress: '',
  smtpUsername: '',
  smtpPassword: null,
  smtpUseTls: true,
  s3EndpointUrl: '',
  s3BucketName: '',
  s3AccessKey: '',
  s3SecretKey: null,
  s3Region: '',
  s3PresignedUrlTtlHours: 24,
  // Authentication
  enabledAuthTypes: ['LOCAL'],
  // LDAP auth provider
  ldapAuthHost: '',
  ldapAuthPort: null,
  ldapAuthSslMode: '',
  ldapAuthTrustAllCerts: false,
  ldapAuthTrustedCertPem: '',
  ldapAuthBindDn: '',
  ldapAuthBindPassword: null,
  ldapAuthUserSearchBase: '',
  ldapAuthBindDnPattern: '',
  // OIDC provider
  oidcIssuerUrl: '',
  oidcClientId: '',
  oidcClientSecret: null,
  oidcScopes: 'openid profile email',
  oidcUsernameClaim: 'preferred_username',
  // SIEM
  siemEnabled: false,
  siemProtocol: '',
  siemHost: '',
  siemPort: null,
  siemFormat: '',
  siemAuthToken: null,
  webhookUrl: '',
  webhookAuthHeader: null,
})

async function loadSettings() {
  loading.value = true
  try {
    const { data } = await getSettings()
    settings.value = data
    Object.assign(form.value, {
      appName:                data.appName ?? 'LDAP Portal',
      logoUrl:                data.logoUrl ?? '',
      primaryColour:          data.primaryColour ?? '#3b82f6',
      secondaryColour:        data.secondaryColour ?? '#64748b',
      superadminBypassApproval: data.superadminBypassApproval ?? false,
      sessionTimeoutMinutes:  data.sessionTimeoutMinutes ?? 60,
      smtpHost:               data.smtpHost ?? '',
      smtpPort:               data.smtpPort ?? 587,
      smtpSenderAddress:      data.smtpSenderAddress ?? '',
      smtpUsername:           data.smtpUsername ?? '',
      smtpPassword:           null,
      smtpUseTls:             data.smtpUseTls ?? true,
      s3EndpointUrl:          data.s3EndpointUrl ?? '',
      s3BucketName:           data.s3BucketName ?? '',
      s3AccessKey:            data.s3AccessKey ?? '',
      s3SecretKey:            null,
      s3Region:               data.s3Region ?? '',
      s3PresignedUrlTtlHours: data.s3PresignedUrlTtlHours ?? 24,
      // Authentication
      enabledAuthTypes:       data.enabledAuthTypes ? [...data.enabledAuthTypes] : ['LOCAL'],
      // LDAP auth provider
      ldapAuthHost:           data.ldapAuthHost ?? '',
      ldapAuthPort:           data.ldapAuthPort ?? null,
      ldapAuthSslMode:        data.ldapAuthSslMode ?? '',
      ldapAuthTrustAllCerts:  data.ldapAuthTrustAllCerts ?? false,
      ldapAuthTrustedCertPem: data.ldapAuthTrustedCertPem ?? '',
      ldapAuthBindDn:         data.ldapAuthBindDn ?? '',
      ldapAuthBindPassword:   null,
      ldapAuthUserSearchBase: data.ldapAuthUserSearchBase ?? '',
      ldapAuthBindDnPattern:  data.ldapAuthBindDnPattern ?? '',
      // OIDC provider
      oidcIssuerUrl:          data.oidcIssuerUrl ?? '',
      oidcClientId:           data.oidcClientId ?? '',
      oidcClientSecret:       null,
      oidcScopes:             data.oidcScopes ?? 'openid profile email',
      oidcUsernameClaim:      data.oidcUsernameClaim ?? 'preferred_username',
      // SIEM
      siemEnabled:            data.siemEnabled ?? false,
      siemProtocol:           data.siemProtocol ?? '',
      siemHost:               data.siemHost ?? '',
      siemPort:               data.siemPort ?? null,
      siemFormat:             data.siemFormat ?? '',
      siemAuthToken:          null,
      webhookUrl:             data.webhookUrl ?? '',
      webhookAuthHeader:      null,
    })
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    loading.value = false
  }
}

async function doSave() {
  // Validate at least one auth method
  if (!form.value.enabledAuthTypes || form.value.enabledAuthTypes.length === 0) {
    notif.error('At least one authentication method must be enabled.')
    return
  }
  saving.value = true
  try {
    await updateSettings({
      appName:               form.value.appName,
      logoUrl:               form.value.logoUrl   || null,
      primaryColour:         form.value.primaryColour   || null,
      secondaryColour:       form.value.secondaryColour || null,
      superadminBypassApproval: form.value.superadminBypassApproval,
      sessionTimeoutMinutes: form.value.sessionTimeoutMinutes,
      smtpHost:              form.value.smtpHost   || null,
      smtpPort:              form.value.smtpPort   || null,
      smtpSenderAddress:     form.value.smtpSenderAddress || null,
      smtpUsername:          form.value.smtpUsername || null,
      smtpPassword:          form.value.smtpPassword,
      smtpUseTls:            form.value.smtpUseTls,
      s3EndpointUrl:         form.value.s3EndpointUrl   || null,
      s3BucketName:          form.value.s3BucketName    || null,
      s3AccessKey:           form.value.s3AccessKey     || null,
      s3SecretKey:           form.value.s3SecretKey,
      s3Region:              form.value.s3Region        || null,
      s3PresignedUrlTtlHours: form.value.s3PresignedUrlTtlHours ?? 24,
      // Authentication
      enabledAuthTypes:      form.value.enabledAuthTypes,
      // LDAP auth provider
      ldapAuthHost:          form.value.ldapAuthHost   || null,
      ldapAuthPort:          form.value.ldapAuthPort   || null,
      ldapAuthSslMode:       form.value.ldapAuthSslMode || null,
      ldapAuthTrustAllCerts: form.value.ldapAuthTrustAllCerts,
      ldapAuthTrustedCertPem: form.value.ldapAuthTrustedCertPem || null,
      ldapAuthBindDn:        form.value.ldapAuthBindDn || null,
      ldapAuthBindPassword:  form.value.ldapAuthBindPassword,
      ldapAuthUserSearchBase: form.value.ldapAuthUserSearchBase || null,
      ldapAuthBindDnPattern: form.value.ldapAuthBindDnPattern || null,
      // OIDC provider
      oidcIssuerUrl:         form.value.oidcIssuerUrl   || null,
      oidcClientId:          form.value.oidcClientId    || null,
      oidcClientSecret:      form.value.oidcClientSecret,
      oidcScopes:            form.value.oidcScopes      || null,
      oidcUsernameClaim:     form.value.oidcUsernameClaim || null,
      // SIEM
      siemEnabled:           form.value.siemEnabled,
      siemProtocol:          form.value.siemProtocol || null,
      siemHost:              form.value.siemHost     || null,
      siemPort:              form.value.siemPort     || null,
      siemFormat:            form.value.siemFormat   || null,
      siemAuthToken:         form.value.siemAuthToken,
      webhookUrl:            form.value.webhookUrl   || null,
      webhookAuthHeader:     form.value.webhookAuthHeader,
    })
    notif.success('Settings saved')
    // Sync branding store so sidebar + page title update immediately
    settingsStore.apply(form.value)
    await loadSettings()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    saving.value = false
  }
}

async function doTestSiem() {
  testingSiem.value = true
  siemTestResult.value = null
  try {
    const { data } = await testSiem()
    siemTestResult.value = {
      ok: !data.delivery?.includes('failed') && !data.delivery?.includes('not enabled'),
      message: data.delivery,
    }
  } catch (e) {
    siemTestResult.value = { ok: false, message: e.response?.data?.detail || e.message }
  } finally {
    testingSiem.value = false
  }
}

onMounted(loadSettings)
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
.input       { @apply border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
</style>
