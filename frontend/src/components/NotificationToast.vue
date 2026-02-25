<template>
  <div class="fixed top-4 right-4 z-50 flex flex-col gap-2 w-80">
    <TransitionGroup name="toast">
      <div
        v-for="n in store.items"
        :key="n.id"
        :class="[
          'flex items-start gap-3 rounded-lg px-4 py-3 shadow-lg text-sm font-medium',
          n.type === 'success' && 'bg-green-600 text-white',
          n.type === 'error'   && 'bg-red-600 text-white',
          n.type === 'info'    && 'bg-blue-600 text-white',
        ]"
      >
        <span class="flex-1">{{ n.message }}</span>
        <button @click="store.remove(n.id)" class="opacity-70 hover:opacity-100">✕</button>
      </div>
    </TransitionGroup>
  </div>
</template>

<script setup>
import { useNotificationStore } from '@/stores/notifications'
const store = useNotificationStore()
</script>

<style scoped>
.toast-enter-active, .toast-leave-active { transition: all 0.3s ease; }
.toast-enter-from { opacity: 0; transform: translateX(100%); }
.toast-leave-to   { opacity: 0; transform: translateX(100%); }
</style>
