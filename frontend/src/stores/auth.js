import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, logout as apiLogout, me } from '@/api/auth'
import { selfServiceLogin as apiSelfServiceLogin } from '@/api/selfservice'
import { getSetupStatus } from '@/api/setup'

export const useAuthStore = defineStore('auth', () => {
  const principal   = ref(null)
  const initialized = ref(false)
  const setupPending = ref(false)

  const isLoggedIn    = computed(() => !!principal.value)
  const isSuperadmin  = computed(() => principal.value?.accountType === 'SUPERADMIN')
  const isSelfService = computed(() => principal.value?.accountType === 'SELF_SERVICE')
  const username      = computed(() => principal.value?.username || '')
  const themePreference = computed(() => principal.value?.themePreference || 'system')
  const authType      = computed(() => principal.value?.authType || null)

  /**
   * Called once per page load (from the router guard) to restore the session
   * from the httpOnly JWT cookie by hitting /api/auth/me.
   */
  async function init() {
    if (initialized.value) return
    initialized.value = true
    try {
      const { data } = await me()
      principal.value = {
        id:              data.id,
        username:        data.username,
        accountType:     data.accountType,
        dn:              data.dn || null,
        directoryId:     data.directoryId || null,
        themePreference: data.themePreference || 'system',
        authType:        data.authType || null,
        email:           data.email || null,
        displayName:     data.displayName || null,
        features:        data.features || [],
      }
      // Check if first-run setup wizard is needed for superadmins
      if (data.accountType === 'SUPERADMIN') {
        try {
          const { data: status } = await getSetupStatus()
          setupPending.value = !status.setupCompleted
        } catch { /* treat as completed */ }
      }
    } catch {
      principal.value = null
    }
  }

  function markSetupComplete() {
    setupPending.value = false
  }

  function updatePrincipal(fields) {
    if (principal.value) {
      principal.value = { ...principal.value, ...fields }
    }
  }

  async function login(username, password) {
    const { data } = await apiLogin(username, password)
    principal.value = {
      id:          data.id,
      username:    data.username,
      accountType: data.accountType,
    }
    initialized.value = true
    // Re-fetch to get preferences
    try {
      const { data: meData } = await me()
      principal.value = { ...principal.value,
        themePreference: meData.themePreference || 'system',
        authType: meData.authType || null,
        email: meData.email || null,
        displayName: meData.displayName || null,
      }
    } catch { /* ok */ }
  }

  async function selfServiceLogin(directoryId, username, password) {
    const { data } = await apiSelfServiceLogin(directoryId, username, password)
    principal.value = {
      id:          data.id,
      username:    data.username,
      accountType: data.accountType,
      dn:          data.dn,
      directoryId: data.directoryId,
    }
    initialized.value = true
  }

  async function logout() {
    try {
      await apiLogout()
    } finally {
      principal.value = null
    }
  }

  function hasFeature(featureDbValue) {
    return principal.value?.features?.includes(featureDbValue) ?? false
  }

  return {
    principal, isLoggedIn, isSuperadmin, isSelfService, username,
    themePreference, authType, hasFeature,
    setupPending, init, login, selfServiceLogin, logout,
    markSetupComplete, updatePrincipal,
  }
})
