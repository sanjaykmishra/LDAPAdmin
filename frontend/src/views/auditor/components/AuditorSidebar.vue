<template>
  <nav class="w-52 shrink-0">
    <RouterLink :to="`/auditor/${token}`"
                class="flex items-center gap-2 px-3 py-2 text-sm text-slate-500 hover:text-slate-700 mb-2"
                @click="$emit('navigate')">
      <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
        <path stroke-linecap="round" stroke-linejoin="round" d="M10.5 19.5L3 12m0 0l7.5-7.5M3 12h18" />
      </svg>
      Overview
    </RouterLink>

    <div class="space-y-0.5">
      <SidebarLink v-for="item in links" :key="item.to"
                   :to="item.to" :label="item.label" :icon="item.icon"
                   @click="$emit('navigate')" />
    </div>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import SidebarLink from './SidebarLink.vue'

const props = defineProps({
  token: String,
  scope: Object,
})

defineEmits(['navigate'])

const links = computed(() => {
  const base = `/auditor/${props.token}`
  const items = [
    { to: `${base}/campaigns`, label: 'Access Review Campaigns', icon: 'campaigns' },
  ]
  if (props.scope?.includeSod) {
    items.push({ to: `${base}/sod`, label: 'Separation of Duties', icon: 'sod' })
  }
  if (props.scope?.includeEntitlements) {
    items.push({ to: `${base}/entitlements`, label: 'Entitlements', icon: 'entitlements' })
  }
  if (props.scope?.includeAuditEvents) {
    items.push({ to: `${base}/audit-events`, label: 'Audit Log', icon: 'audit' })
  }
  items.push({ to: `${base}/approvals`, label: 'Approvals', icon: 'approvals' })
  return items
})
</script>
