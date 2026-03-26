<template>
  <div class="bg-red-50 border border-red-200 rounded-xl p-6 text-center">
    <svg class="w-8 h-8 mx-auto mb-2 text-red-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
      <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
    </svg>
    <h3 class="text-sm font-medium text-red-800 mb-1">{{ title }}</h3>
    <p class="text-xs text-red-600">{{ message }}</p>
    <button v-if="retryable" @click="$emit('retry')"
            class="mt-3 px-3 py-1.5 text-xs border border-red-300 text-red-700 rounded-lg hover:bg-red-100">
      Try again
    </button>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: 'Failed to load' },
  message: { type: String, default: 'Something went wrong. Please try again.' },
  retryable: { type: Boolean, default: true },
})
defineEmits(['retry'])
</script>
