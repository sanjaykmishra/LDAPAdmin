import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listPolicies, getPolicy, listViolations } from '@/api/sodPolicies'

export const useSodPolicyStore = defineStore('sodPolicy', () => {
  const policies = ref([])
  const currentPolicy = ref(null)
  const violations = ref([])
  const loading = ref(false)

  async function loadPolicies(directoryId) {
    loading.value = true
    try {
      const { data } = await listPolicies(directoryId)
      policies.value = data
    } finally {
      loading.value = false
    }
  }

  async function loadPolicy(directoryId, policyId) {
    loading.value = true
    try {
      const { data } = await getPolicy(directoryId, policyId)
      currentPolicy.value = data
    } finally {
      loading.value = false
    }
  }

  async function loadViolations(directoryId, status) {
    loading.value = true
    try {
      const params = status ? { status } : {}
      const { data } = await listViolations(directoryId, params)
      violations.value = data
    } finally {
      loading.value = false
    }
  }

  return { policies, currentPolicy, violations, loading, loadPolicies, loadPolicy, loadViolations }
})
