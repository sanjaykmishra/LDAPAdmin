<template>
  <div class="bg-white border border-slate-200 rounded-xl p-4">
    <div class="flex items-start justify-between">
      <div>
        <div class="text-xs font-medium text-slate-500 uppercase tracking-wider">{{ label }}</div>
        <div class="text-2xl font-bold mt-1" :class="alert ? 'text-red-600' : 'text-slate-900'">{{ value }}</div>
        <div v-if="sub" class="text-xs text-slate-400 mt-0.5">{{ sub }}</div>
      </div>
      <div class="p-2 rounded-lg" :class="iconBg">
        <!-- Campaign icon -->
        <svg v-if="icon === 'campaigns'" class="w-5 h-5" :class="iconColor" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 12h3.75M9 15h3.75M9 18h3.75m3 .75H18a2.25 2.25 0 002.25-2.25V6.108c0-1.135-.845-2.098-1.976-2.192a48.424 48.424 0 00-1.123-.08m-5.801 0c-.065.21-.1.433-.1.664 0 .414.336.75.75.75h4.5a.75.75 0 00.75-.75 2.25 2.25 0 00-.1-.664m-5.8 0A2.251 2.251 0 0113.5 2.25H15" />
        </svg>
        <!-- Decisions icon -->
        <svg v-else-if="icon === 'decisions'" class="w-5 h-5" :class="iconColor" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <!-- SoD icon -->
        <svg v-else-if="icon === 'sod'" class="w-5 h-5" :class="iconColor" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126z" />
        </svg>
        <!-- Approvals icon -->
        <svg v-else class="w-5 h-5" :class="iconColor" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M10.125 2.25h-4.5c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125v-9M10.125 2.25h.375a9 9 0 019 9v.375" />
        </svg>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  label: String,
  value: [String, Number],
  sub: String,
  icon: String,
  alert: Boolean,
})

const iconBg = computed(() => props.alert ? 'bg-red-50' : 'bg-slate-100')
const iconColor = computed(() => props.alert ? 'text-red-500' : 'text-slate-400')
</script>
