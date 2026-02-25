<template>
  <div class="p-6 max-w-3xl">
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Application Settings</h1>

    <div v-if="loading" class="text-gray-500 text-sm">Loading…</div>

    <form v-else @submit.prevent="doSave" class="space-y-6">

      <!-- Branding -->
      <section class="bg-white border border-gray-200 rounded-xl p-6">
        <h2 class="text-base font-semibold text-gray-900 mb-4">Branding</h2>
        <div class="grid grid-cols-2 gap-4">
          <FormField label="Application Name" v-model="form.appName" required />
          <FormField label="Logo URL" v-model="form.logoUrl" placeholder="https://…/logo.png" />
          <FormField label="Primary Colour" v-model="form.primaryColour" placeholder="#3b82f6" />
          <FormField label="Secondary Colour" v-model="form.secondaryColour" placeholder="#64748b" />
        </div>
      </section>

      <!-- Session -->
      <section class="bg-white border border-gray-200 rounded-xl p-6">
        <h2 class="text-base font-semibold text-gray-900 mb-4">Session</h2>
        <div class="max-w-xs">
          <FormField label="Session Timeout (minutes)" v-model.number="form.sessionTimeoutMinutes" type="number" required />
        </div>
      </section>

      <!-- SMTP -->
      <section class="bg-white border border-gray-200 rounded-xl p-6">
        <h2 class="text-base font-semibold text-gray-900 mb-4">SMTP (Email Delivery)</h2>
        <div class="grid grid-cols-2 gap-4">
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
        <h2 class="text-base font-semibold text-gray-900 mb-4">S3 (Report Storage)</h2>
        <div class="grid grid-cols-2 gap-4">
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
import { getSettings, updateSettings } from '@/api/settings'
import FormField from '@/components/FormField.vue'

const notif   = useNotificationStore()
const loading = ref(false)
const saving  = ref(false)
const settings = ref(null)

const form = ref({
  appName: 'LDAP Portal',
  logoUrl: '',
  primaryColour: '',
  secondaryColour: '',
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
})

async function loadSettings() {
  loading.value = true
  try {
    const { data } = await getSettings()
    settings.value = data
    Object.assign(form.value, {
      appName:                data.appName ?? 'LDAP Portal',
      logoUrl:                data.logoUrl ?? '',
      primaryColour:          data.primaryColour ?? '',
      secondaryColour:        data.secondaryColour ?? '',
      sessionTimeoutMinutes:  data.sessionTimeoutMinutes ?? 60,
      smtpHost:               data.smtpHost ?? '',
      smtpPort:               data.smtpPort ?? 587,
      smtpSenderAddress:      data.smtpSenderAddress ?? '',
      smtpUsername:           data.smtpUsername ?? '',
      smtpPassword:           null,   // never populate from server
      smtpUseTls:             data.smtpUseTls ?? true,
      s3EndpointUrl:          data.s3EndpointUrl ?? '',
      s3BucketName:           data.s3BucketName ?? '',
      s3AccessKey:            data.s3AccessKey ?? '',
      s3SecretKey:            null,   // never populate from server
      s3Region:               data.s3Region ?? '',
      s3PresignedUrlTtlHours: data.s3PresignedUrlTtlHours ?? 24,
    })
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    loading.value = false
  }
}

async function doSave() {
  saving.value = true
  try {
    await updateSettings({
      appName:               form.value.appName,
      logoUrl:               form.value.logoUrl   || null,
      primaryColour:         form.value.primaryColour   || null,
      secondaryColour:       form.value.secondaryColour || null,
      sessionTimeoutMinutes: form.value.sessionTimeoutMinutes,
      smtpHost:              form.value.smtpHost   || null,
      smtpPort:              form.value.smtpPort   || null,
      smtpSenderAddress:     form.value.smtpSenderAddress || null,
      smtpUsername:          form.value.smtpUsername || null,
      smtpPassword:          form.value.smtpPassword,  // null=keep, ''=clear, text=set
      smtpUseTls:            form.value.smtpUseTls,
      s3EndpointUrl:         form.value.s3EndpointUrl   || null,
      s3BucketName:          form.value.s3BucketName    || null,
      s3AccessKey:           form.value.s3AccessKey     || null,
      s3SecretKey:           form.value.s3SecretKey,
      s3Region:              form.value.s3Region        || null,
      s3PresignedUrlTtlHours: form.value.s3PresignedUrlTtlHours ?? 24,
    })
    notif.success('Settings saved')
    await loadSettings()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    saving.value = false
  }
}

onMounted(loadSettings)
</script>

<style scoped>
@reference "tailwindcss";
.btn-primary { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
</style>
