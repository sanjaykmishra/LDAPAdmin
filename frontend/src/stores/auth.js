import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, logout as apiLogout, me } from '@/api/auth'
import { selfServiceLogin as apiSelfServiceLogin } from '@/api/selfservice'

export const useAuthStore = defineStore('auth', () => {
  const principal   = ref(null)
  const initialized = ref(false)

  const isLoggedIn    = computed(() => !!principal.value)
  const isSuperadmin  = computed(() => principal.value?.accountType === 'SUPERADMIN')
  const isSelfService = computed(() => principal.value?.accountType === 'SELF_SERVICE')
  const username      = computed(() => principal.value?.username || '')

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
        id:          data.id,
        username:    data.username,
        accountType: data.accountType,
        dn:          data.dn || null,
        directoryId: data.directoryId || null,
      }
    } catch {
      principal.value = null
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

  return { principal, isLoggedIn, isSuperadmin, isSelfService, username, init, login, selfServiceLogin, logout }
})
