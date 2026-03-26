<template>
  <RouterLink :to="to" class="sidebar-link" active-class="sidebar-link-active" @click="$emit('click')">
    <span class="text-sm">{{ label }}</span>
  </RouterLink>
</template>

<script setup>
import { RouterLink } from 'vue-router'
defineProps({ to: String, label: String, icon: String })
defineEmits(['click'])
</script>

<style scoped>
@reference "tailwindcss";
.sidebar-link {
  @apply flex items-center gap-2 px-3 py-2 rounded-lg text-sm text-slate-600 hover:bg-slate-100 hover:text-slate-800 transition-colors;
}
.sidebar-link-active {
  @apply bg-slate-100 text-slate-900 font-medium;
}
</style>
