import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('jwt') || null)
  const principal = ref(
    JSON.parse(localStorage.getItem('principal') || 'null')
  )

  const isLoggedIn = computed(() => !!token.value)
  const isSuperadmin = computed(() => principal.value?.accountType === 'SUPERADMIN')
  const username = computed(() => principal.value?.username || '')

  async function login(username, password) {
    const { data } = await apiLogin(username, password)
    token.value = data.token
    principal.value = {
      id: data.id,
      username: data.username,
      accountType: data.accountType,
      tenantId: data.tenantId,
    }
    localStorage.setItem('jwt', data.token)
    localStorage.setItem('principal', JSON.stringify(principal.value))
  }

  function logout() {
    token.value = null
    principal.value = null
    localStorage.removeItem('jwt')
    localStorage.removeItem('principal')
  }

  return { token, principal, isLoggedIn, isSuperadmin, username, login, logout }
})
