<template>
  <div class="grid grid-cols-2 gap-4">
    <FormField label="Name" v-model="local.name" required class="col-span-2" />
    <FormField label="Host" v-model="local.host" required />
    <FormField label="Port" type="number" v-model="local.port" required />
    <FormField label="Bind DN" v-model="local.bindDn" placeholder="cn=admin,dc=example,dc=com" required class="col-span-2" />
    <FormField label="Bind Password" type="password" v-model="local.bindPassword" :placeholder="isEdit ? '(unchanged)' : ''" class="col-span-2" />
    <FormField label="Base DN" v-model="local.baseDn" placeholder="dc=example,dc=com" required class="col-span-2" />
    <FormField label="Paging Size" type="number" v-model="local.pagingSize" />
    <div class="flex items-center gap-2 mt-6">
      <input id="tls" type="checkbox" v-model="local.useTls" class="rounded" />
      <label for="tls" class="text-sm text-gray-700">Use TLS/LDAPS</label>
    </div>
  </div>
</template>

<script setup>
import { reactive, watch } from 'vue'
import FormField from '@/components/FormField.vue'

const props = defineProps({
  data:   { type: Object, required: true },
  isEdit: Boolean,
})
const emit = defineEmits(['update'])

const local = reactive({ ...props.data })
watch(local, v => emit('update', { ...v }), { deep: true })
watch(() => props.data, v => Object.assign(local, v), { deep: true })
</script>
