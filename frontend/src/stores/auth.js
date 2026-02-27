import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, logout as apiLogout, me } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const principal   = ref(null)
  const initialized = ref(false)

  const isLoggedIn    = computed(() => !!principal.value)
  const isSuperadmin  = computed(() => principal.value?.accountType === 'SUPERADMIN')
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
      }
    } catch {
      principal.value = null
    }
  }

  async function login(username, password) {
    const { data } = await apiLogin(username, password)
    // Populate principal from login response body (token is in httpOnly cookie)
    principal.value = {
      id:          data.id,
      username:    data.username,
      accountType: data.accountType,
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

  return { principal, isLoggedIn, isSuperadmin, username, init, login, logout }
})
