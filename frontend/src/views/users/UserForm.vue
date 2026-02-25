<template>
  <div v-if="!isEdit" class="space-y-3">
    <FormField label="Parent DN" v-model="local.parentDn" placeholder="ou=people,dc=example,dc=com" required />
    <div class="grid grid-cols-2 gap-3">
      <FormField label="RDN Attribute" v-model="local.rdnAttribute" placeholder="uid" required />
      <FormField label="RDN Value" v-model="local.rdnValue" placeholder="jsmith" required />
    </div>
    <FormField label="cn (Common Name)" v-model="local.attributes.cn" required />
    <FormField label="sn (Surname)" v-model="local.attributes.sn" />
    <FormField label="mail" v-model="local.attributes.mail" />
    <FormField label="userPassword" type="password" v-model="local.attributes.userPassword" />
  </div>

  <div v-else class="space-y-3">
    <p class="text-xs text-gray-500 mb-3">Editing: <code class="bg-gray-100 px-1 rounded">{{ local.dn }}</code></p>
    <template v-for="(_, key) in local.attributes" :key="key">
      <FormField :label="key" v-model="local.attributes[key]" type="textarea" :rows="2" hint="One value per line" />
    </template>
  </div>
</template>

<script setup>
import { reactive, watch } from 'vue'
import FormField from '@/components/FormField.vue'

const props = defineProps({
  data: { type: Object, required: true },
  isEdit: Boolean,
})
const emit = defineEmits(['update'])

const local = reactive({
  ...props.data,
  attributes: { ...(props.data.attributes || {}) }
})
watch(local, v => emit('update', JSON.parse(JSON.stringify(v))), { deep: true })
watch(() => props.data, v => { Object.assign(local, v); Object.assign(local.attributes, v.attributes || {}) }, { deep: true })
</script>
