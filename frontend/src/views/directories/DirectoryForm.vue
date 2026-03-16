<template>
  <div class="grid grid-cols-2 gap-4">
    <FormField label="Display Name" v-model="local.displayName" required class="col-span-2" />
    <FormField label="Host" v-model="local.host" required />
    <FormField label="Port" type="number" v-model="local.port" required />
    <FormField label="Bind DN" v-model="local.bindDn" placeholder="cn=admin,dc=example,dc=com" required class="col-span-2" />
    <FormField label="Bind Password" type="password" v-model="local.bindPassword" :placeholder="isEdit ? '(unchanged)' : ''" class="col-span-2" />
    <FormField label="Base DN" v-model="local.baseDn" placeholder="dc=example,dc=com" required class="col-span-2" />
    <FormField label="Paging Size" type="number" v-model="local.pagingSize" />
    <FormField label="SSL Mode" type="select" v-model="local.sslMode" :options="sslOptions" />
    <div class="flex items-center gap-2 mt-6">
      <input id="trustAllCerts" type="checkbox" v-model="local.trustAllCerts" class="rounded" />
      <label for="trustAllCerts" class="text-sm text-gray-700">Trust all certificates</label>
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

const sslOptions = [
  { value: 'NONE',     label: 'None' },
  { value: 'LDAPS',    label: 'LDAPS' },
  { value: 'STARTTLS', label: 'STARTTLS' },
]

const local = reactive({ ...props.data })
watch(local, v => emit('update', { ...v }), { deep: true })
watch(() => props.data, v => Object.assign(local, v), { deep: true })
</script>
