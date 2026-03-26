<template>
  <div v-if="active" class="bg-blue-50 border border-blue-200 rounded-xl p-5 mb-6">
    <!-- Header -->
    <div class="flex items-center justify-between mb-3">
      <div class="text-sm font-semibold text-blue-900">
        Step {{ currentStep + 1 }} of {{ steps.length }}: {{ steps[currentStep].title }}
      </div>
      <button @click="exit" class="text-xs text-blue-600 hover:text-blue-800">Exit tour</button>
    </div>

    <!-- Explanation -->
    <p class="text-sm text-blue-800 mb-4">{{ steps[currentStep].explanation }}</p>

    <!-- Navigation -->
    <div class="flex items-center justify-between">
      <button @click="prev" :disabled="currentStep === 0"
              class="px-3 py-1.5 text-xs border border-blue-300 text-blue-700 rounded-lg hover:bg-blue-100 disabled:opacity-40">
        Previous
      </button>
      <div class="flex gap-1">
        <span v-for="(_, i) in steps" :key="i"
              class="w-2 h-2 rounded-full transition-colors"
              :class="i === currentStep ? 'bg-blue-600' : 'bg-blue-200'" />
      </div>
      <button v-if="currentStep < steps.length - 1" @click="next"
              class="px-3 py-1.5 text-xs bg-blue-600 text-white rounded-lg hover:bg-blue-700">
        Next
      </button>
      <button v-else @click="exit"
              class="px-3 py-1.5 text-xs bg-blue-600 text-white rounded-lg hover:bg-blue-700">
        Finish
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const props = defineProps({
  token: String,
  scope: Object,
})

const emit = defineEmits(['navigate'])

const route = useRoute()
const router = useRouter()

const active = ref(false)
const currentStep = ref(0)

const steps = computed(() => {
  const base = `/auditor/${props.token}`
  const items = [
    {
      title: 'Access Reviews',
      path: `${base}/campaigns`,
      explanation: 'This section shows all access review campaigns conducted during the audit period. Each campaign documents which user entitlements were reviewed, by whom, and what decisions were made (confirmed or revoked).',
    },
  ]
  if (props.scope?.includeSod) {
    items.push({
      title: 'Separation of Duties',
      path: `${base}/sod`,
      explanation: 'This section demonstrates that the organization monitors for separation of duties conflicts. It lists all SoD policies and any violations detected, along with their resolution status.',
    })
  }
  if (props.scope?.includeEntitlements) {
    items.push({
      title: 'User Entitlements',
      path: `${base}/entitlements`,
      explanation: 'This is a point-in-time snapshot of all users and their group memberships from the directory. It demonstrates the current state of access assignments at the time of evidence collection.',
    })
  }
  if (props.scope?.includeAuditEvents) {
    items.push({
      title: 'Audit Log',
      path: `${base}/audit-events`,
      explanation: 'This section contains the directory change log for the evidence window. It shows all user, group, and access changes made during the audit period, providing a complete trail of directory modifications.',
    })
  }
  items.push({
    title: 'Approval History',
    path: `${base}/approvals`,
    explanation: 'This section documents the approval workflow for all directory changes that required authorization. It shows who requested changes, who approved or rejected them, and when.',
  })
  return items
})

function start() {
  active.value = true
  currentStep.value = 0
  navigateToStep(0)
  updateUrl()
}

function next() {
  if (currentStep.value < steps.value.length - 1) {
    currentStep.value++
    navigateToStep(currentStep.value)
    updateUrl()
  }
}

function prev() {
  if (currentStep.value > 0) {
    currentStep.value--
    navigateToStep(currentStep.value)
    updateUrl()
  }
}

function exit() {
  active.value = false
  router.replace({ query: {} })
}

function navigateToStep(index) {
  const step = steps.value[index]
  if (step) {
    emit('navigate', step.path)
    router.push(step.path)
  }
}

function updateUrl() {
  router.replace({
    query: { guided: 'true', step: String(currentStep.value + 1) },
  })
}

// Restore state from URL on mount
onMounted(() => {
  if (route.query.guided === 'true') {
    active.value = true
    const stepNum = parseInt(route.query.step || '1', 10)
    currentStep.value = Math.max(0, Math.min(stepNum - 1, steps.value.length - 1))
  }
})

defineExpose({ start, active })
</script>
