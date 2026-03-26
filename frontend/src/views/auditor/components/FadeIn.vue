<template>
  <Transition name="fade-in" appear>
    <div v-if="show">
      <slot />
    </div>
  </Transition>
</template>

<script setup>
defineProps({
  show: { type: Boolean, default: true },
})
</script>

<style>
.fade-in-enter-active {
  transition: opacity 0.4s ease, transform 0.4s ease;
}
.fade-in-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.fade-in-enter-to {
  opacity: 1;
  transform: translateY(0);
}
</style>
