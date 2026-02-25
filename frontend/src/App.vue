<template>
  <RouterView />
  <NotificationToast />
</template>

<script setup>
import { RouterView } from 'vue-router'
import NotificationToast from '@/components/NotificationToast.vue'
</script>
