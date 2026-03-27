<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="modelValue" class="fixed inset-0 z-40 flex items-center justify-center p-4"
           @click.self="$emit('update:modelValue', false)"
           role="dialog" aria-modal="true">
        <div class="fixed inset-0 bg-black/40" />
        <div :class="['relative bg-white rounded-xl shadow-xl w-full', sizeClass]">
          <!-- Header -->
          <div class="flex items-center justify-between px-6 py-4 border-b border-gray-200">
            <h2 class="text-lg font-semibold text-gray-900">{{ title }}</h2>
            <button @click="$emit('update:modelValue', false)"
                    class="text-gray-400 hover:text-gray-600 text-xl leading-none transition-colors">&#215;</button>
          </div>
          <!-- Body -->
          <div class="px-6 py-4 overflow-y-auto" :style="{ height: fixedHeight || undefined, maxHeight: fixedHeight ? undefined : '80vh' }">
            <slot />
          </div>
          <!-- Footer -->
          <div v-if="$slots.footer" class="px-6 py-4 border-t border-gray-200 flex justify-end gap-3">
            <slot name="footer" />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({
  modelValue: Boolean,
  title: { type: String, default: '' },
  size: { type: String, default: 'md' },
  fixedHeight: { type: String, default: '' },
})
defineEmits(['update:modelValue'])
const sizeClass = computed(() => ({
  sm: 'max-w-sm',
  md: 'max-w-lg',
  lg: 'max-w-2xl',
  xl: 'max-w-4xl',
}[props.size] || 'max-w-lg'))
</script>

<style>
.modal-enter-active, .modal-leave-active {
  transition: opacity 0.2s ease;
}
.modal-enter-active > div:last-child, .modal-leave-active > div:last-child {
  transition: transform 0.2s ease, opacity 0.2s ease;
}
.modal-enter-from, .modal-leave-to { opacity: 0; }
.modal-enter-from > div:last-child { transform: scale(0.95) translateY(8px); }
.modal-leave-to > div:last-child { transform: scale(0.95); }
</style>
