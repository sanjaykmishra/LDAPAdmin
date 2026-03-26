<template>
  <div class="min-h-screen bg-slate-50 text-slate-800">
    <!-- Header -->
    <header class="bg-white border-b border-slate-200 shadow-sm sticky top-0 z-30">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
        <!-- Left: branding -->
        <div class="flex items-center gap-3">
          <img v-if="branding.logoUrl" :src="branding.logoUrl" alt="" class="h-8 w-8 object-contain" />
          <div v-else class="h-8 w-8 rounded-md flex items-center justify-center text-white font-bold text-sm"
               :style="{ backgroundColor: branding.primaryColour || '#475569' }">
            {{ (branding.appName || 'E')[0] }}
          </div>
          <div class="hidden sm:block">
            <div class="text-sm font-semibold text-slate-900">{{ metadata.label || 'Evidence Package' }}</div>
            <div class="text-xs text-slate-500">{{ metadata.directoryName }}</div>
          </div>
        </div>

        <!-- Center: verification badge -->
        <button @click="showVerifyDrawer = true"
                class="flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium transition-colors"
                :class="verifyBadgeClass">
          <svg v-if="verification.verified === true" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <svg v-else-if="verification.verified === false" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
          </svg>
          <svg v-else class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          <span class="hidden sm:inline">{{ verifyLabel }}</span>
        </button>

        <!-- Right: expiry + export -->
        <div class="flex items-center gap-3">
          <span v-if="daysRemaining !== null" class="text-xs text-slate-500 hidden sm:inline">
            Expires in {{ daysRemaining }}d
          </span>
          <button @click="downloadZip" :disabled="exporting"
                  class="bg-slate-700 text-white text-xs font-medium px-3 py-1.5 rounded-lg hover:bg-slate-800 disabled:opacity-50 flex items-center gap-1.5">
            <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 20 20" stroke="currentColor" stroke-width="1.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M10 2v12M10 14l4-4M10 14l-4-4M3 17h14" />
            </svg>
            ZIP
          </button>
          <button @click="window.print()"
                  class="text-slate-600 text-xs font-medium px-3 py-1.5 border border-slate-200 rounded-lg hover:bg-slate-50 hidden sm:flex items-center gap-1.5 print:hidden">
            <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 20 20" stroke="currentColor" stroke-width="1.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M6.72 13.829c-.24.03-.48.062-.72.096m.72-.096a42.415 42.415 0 0110.56 0m-10.56 0L6.34 18m10.94-4.171c.24.03.48.062.72.096m-.72-.096L17.66 18m0 0 .229 2.523a1.125 1.125 0 01-1.12 1.227H7.231c-.662 0-1.18-.568-1.12-1.227L6.34 18m11.318 0h1.091A2.25 2.25 0 0021 15.75V9.456c0-1.081-.768-2.015-1.837-2.175a48.055 48.055 0 00-1.913-.247M6.34 18H5.25A2.25 2.25 0 013 15.75V9.456c0-1.081.768-2.015 1.837-2.175a48.041 48.041 0 011.913-.247m10.5 0a48.536 48.536 0 00-10.5 0m10.5 0V3.375c0-.621-.504-1.125-1.125-1.125h-8.25c-.621 0-1.125.504-1.125 1.125v3.659" />
            </svg>
            Print
          </button>
        </div>
      </div>
    </header>

    <!-- Expiry banner (warning when < 7 days) -->
    <div v-if="daysRemaining !== null && daysRemaining <= 7"
         class="bg-amber-50 border-b border-amber-200 text-amber-800 text-xs text-center py-2 px-4">
      This link expires in {{ daysRemaining }} day{{ daysRemaining !== 1 ? 's' : '' }}
      ({{ formatDate(metadata.expiresAt) }})
    </div>

    <!-- Export error banner -->
    <div v-if="exportError"
         class="bg-red-50 border-b border-red-200 text-red-700 text-xs text-center py-2 px-4">
      {{ exportError }}
    </div>

    <!-- Activity transparency footer note -->
    <div class="text-center text-[10px] text-slate-400 py-1 border-b border-slate-100">
      Access logged for compliance audit trail
    </div>

    <!-- Loading / Error states -->
    <div v-if="loading" class="flex items-center justify-center py-32">
      <div class="text-center">
        <svg class="w-8 h-8 mx-auto mb-3 animate-spin text-slate-400" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
        <p class="text-sm text-slate-500">Loading evidence package...</p>
      </div>
    </div>

    <div v-else-if="error" class="flex items-center justify-center py-32">
      <div class="text-center max-w-md">
        <svg class="w-12 h-12 mx-auto mb-3 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
        </svg>
        <h2 class="text-lg font-semibold text-slate-700 mb-1">Link Not Found</h2>
        <p class="text-sm text-slate-500">This evidence package link is invalid, expired, or has been revoked.</p>
      </div>
    </div>

    <!-- Main content -->
    <div v-else class="max-w-7xl mx-auto px-4 sm:px-6 py-6">
      <!-- Landing page: no sidebar -->
      <div v-if="isLanding">
        <RouterView :token="token" :metadata="metadata" :scope="metadata.scope || {}" />
      </div>
      <!-- Section pages: sidebar + content -->
      <div v-else class="flex gap-6">
        <AuditorSidebar :token="token" :scope="metadata.scope || {}" class="hidden md:block" />
        <!-- Mobile hamburger -->
        <button @click="showMobileNav = !showMobileNav"
                class="md:hidden fixed bottom-4 left-4 z-20 bg-slate-700 text-white p-3 rounded-full shadow-lg">
          <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
          </svg>
        </button>
        <div v-if="showMobileNav" class="md:hidden fixed inset-0 z-30" @click.self="showMobileNav = false">
          <div class="fixed inset-0 bg-black/30" @click="showMobileNav = false" />
          <div class="relative w-64 bg-white shadow-xl h-full overflow-y-auto">
            <AuditorSidebar :token="token" :scope="metadata.scope || {}" @navigate="showMobileNav = false" />
          </div>
        </div>
        <main class="flex-1 min-w-0">
          <RouterView :token="token" :metadata="metadata" :scope="metadata.scope || {}" />
        </main>
      </div>
    </div>

    <!-- Verification detail drawer -->
    <div v-if="showVerifyDrawer" class="fixed inset-0 z-50 flex justify-end"
         @click.self="showVerifyDrawer = false" @keydown.escape="showVerifyDrawer = false"
         role="dialog" aria-label="Integrity Verification" tabindex="-1" ref="verifyDrawerRef">
      <div class="fixed inset-0 bg-black/30" @click="showVerifyDrawer = false" />
      <div class="relative w-full max-w-md bg-white shadow-xl p-6 overflow-y-auto">
        <div class="flex items-center justify-between mb-6">
          <h3 class="text-lg font-semibold text-slate-900">Integrity Verification</h3>
          <button @click="showVerifyDrawer = false" class="text-slate-400 hover:text-slate-600">
            <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div v-if="verification.verified === true" class="bg-green-50 border border-green-200 rounded-xl p-4 mb-4">
          <div class="flex items-center gap-2 text-green-700 font-medium text-sm mb-1">
            <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            Integrity Verified
          </div>
          <p class="text-xs text-green-600">The evidence package has not been tampered with. The cryptographic signature matches the data.</p>
        </div>
        <div v-else-if="verification.verified === false" class="bg-red-50 border border-red-200 rounded-xl p-4 mb-4">
          <div class="flex items-center gap-2 text-red-700 font-medium text-sm mb-1">
            <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
            </svg>
            Verification Failed
          </div>
          <p class="text-xs text-red-600">The cryptographic signature does not match. This evidence may have been tampered with.</p>
        </div>
        <div v-else-if="verification.error" class="bg-amber-50 border border-amber-200 rounded-xl p-4 mb-4">
          <div class="flex items-center gap-2 text-amber-700 font-medium text-sm mb-1">
            <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
            </svg>
            Verification Unavailable
          </div>
          <p class="text-xs text-amber-600">Could not reach the verification service. This does not indicate tampering — please try again later.</p>
        </div>

        <dl class="space-y-3 text-sm">
          <div>
            <dt class="text-xs font-medium text-slate-500 uppercase tracking-wider">Algorithm</dt>
            <dd class="font-mono text-slate-700">{{ verification.algorithm || '...' }}</dd>
          </div>
          <div>
            <dt class="text-xs font-medium text-slate-500 uppercase tracking-wider">Signature</dt>
            <dd class="font-mono text-xs text-slate-600 break-all bg-slate-50 rounded p-2">{{ verification.signature || '...' }}</dd>
          </div>
          <div>
            <dt class="text-xs font-medium text-slate-500 uppercase tracking-wider">Covered Fields</dt>
            <dd>
              <ul class="space-y-0.5 mt-1">
                <li v-for="f in (verification.coveredFields || [])" :key="f"
                    class="text-xs text-slate-600 flex items-center gap-1">
                  <svg class="w-3 h-3 text-green-500" fill="currentColor" viewBox="0 0 20 20">
                    <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd" />
                  </svg>
                  {{ f }}
                </li>
              </ul>
            </dd>
          </div>
          <div v-if="verification.verifiedAt">
            <dt class="text-xs font-medium text-slate-500 uppercase tracking-wider">Verified At</dt>
            <dd class="text-slate-700">{{ formatDate(verification.verifiedAt) }}</dd>
          </div>
        </dl>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { getPortalMetadata, getPortalVerify, getPortalExport } from '@/api/auditorPortal'
import AuditorSidebar from './components/AuditorSidebar.vue'

const route = useRoute()
const token = computed(() => route.params.token)

const loading = ref(true)
const error = ref(false)
const metadata = ref({})
const branding = ref({})
const verification = ref({})
const showVerifyDrawer = ref(false)
const exporting = ref(false)
const exportError = ref(null)
const showMobileNav = ref(false)

const isLanding = computed(() => route.name === 'auditorLanding')

// Close mobile nav on route change
watch(() => route.path, () => { showMobileNav.value = false })

const daysRemaining = computed(() => {
  if (!metadata.value.expiresAt) return null
  const diff = new Date(metadata.value.expiresAt) - new Date()
  return Math.max(0, Math.ceil(diff / (1000 * 60 * 60 * 24)))
})

const verifyLabel = computed(() => {
  if (verification.value.verified === true) return 'Integrity Verified'
  if (verification.value.verified === false) return 'Verification Failed'
  if (verification.value.error) return 'Verification Unavailable'
  return 'Verifying...'
})

const verifyBadgeClass = computed(() => {
  if (verification.value.verified === true) return 'bg-green-50 text-green-700 border border-green-200'
  if (verification.value.verified === false) return 'bg-red-50 text-red-700 border border-red-200'
  if (verification.value.error) return 'bg-amber-50 text-amber-700 border border-amber-200'
  return 'bg-slate-100 text-slate-500 border border-slate-200'
})

function formatDate(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

async function downloadZip() {
  exporting.value = true
  try {
    const { data } = await getPortalExport(token.value)
    const url = URL.createObjectURL(data)
    const a = document.createElement('a')
    a.href = url
    a.download = 'evidence-package.zip'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    exportError.value = 'Failed to download evidence package. Please try again.'
    setTimeout(() => { exportError.value = null }, 5000)
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  try {
    const { data } = await getPortalMetadata(token.value)
    metadata.value = data
    branding.value = data.branding || {}
    loading.value = false

    // Ambient verification — auto-check on load
    try {
      const { data: v } = await getPortalVerify(token.value)
      verification.value = v
    } catch {
      verification.value = { error: true }
    }
  } catch {
    loading.value = false
    error.value = true
  }
})
</script>

<style>
@media print {
  /* Hide interactive/navigation elements */
  header, nav, .print\\:hidden,
  button, [role="dialog"],
  .fixed { display: none !important; }

  /* Reset layout */
  body { background: white !important; }
  main, .max-w-7xl { max-width: 100% !important; padding: 0 !important; margin: 0 !important; }

  /* Clean tables for print */
  table { page-break-inside: auto; font-size: 9pt !important; }
  tr { page-break-inside: avoid; }
  th, td { padding: 4px 8px !important; }

  /* Show HMAC signature in footer */
  .print-footer {
    display: block !important;
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    font-size: 7pt;
    color: #999;
    text-align: center;
    border-top: 1px solid #ddd;
    padding: 4px;
  }
}
</style>
