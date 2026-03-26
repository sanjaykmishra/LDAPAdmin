<template>
  <div class="animate-pulse space-y-4">
    <!-- Header skeleton -->
    <div class="h-6 bg-slate-200 rounded w-1/3" />
    <!-- Card skeletons -->
    <div v-for="i in rows" :key="i" class="bg-white border border-slate-200 rounded-xl p-4 space-y-2">
      <div class="h-4 bg-slate-200 rounded w-2/3" />
      <div class="h-3 bg-slate-100 rounded w-1/2" />
      <div class="h-3 bg-slate-100 rounded w-1/4" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  rows: { type: Number, default: 3 },
})
</script>
