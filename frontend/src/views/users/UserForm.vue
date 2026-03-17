<template>
  <div v-if="!isEdit" class="space-y-3">
    <FormField label="Parent DN" v-model="local.parentDn" placeholder="ou=people,dc=example,dc=com" required />
    <div class="grid grid-cols-2 gap-3">
      <FormField label="RDN Attribute" v-model="local.rdnAttribute" placeholder="uid" required />
      <FormField label="RDN Value" v-model="local.rdnValue" placeholder="jsmith" required />
    </div>

    <!-- Object Class (disabled, from user form definition) -->
    <FormField
      v-if="userFormConfig"
      label="Object Class"
      :model-value="userFormConfig.objectClassName"
      disabled
    />

    <!-- Dynamic fields from user form config -->
    <template v-if="userFormConfig?.attributeConfigs?.length">
      <template v-for="attr in sortedAttributeConfigs" :key="attr.id">
        <FormField
          :label="attr.customLabel || attr.attributeName"
          v-model="local.attributes[attr.attributeName]"
          :type="mapInputType(attr.inputType)"
          :required="attr.requiredOnCreate"
          :disabled="!attr.editableOnCreate"
          :placeholder="attr.attributeName"
        />
      </template>
    </template>

    <!-- Fallback: hardcoded fields when no user form config -->
    <template v-else>
      <FormField label="cn (Common Name)" v-model="local.attributes.cn" required />
      <FormField label="sn (Surname)" v-model="local.attributes.sn" />
      <FormField label="mail" v-model="local.attributes.mail" />
      <FormField label="userPassword" type="password" v-model="local.attributes.userPassword" />
    </template>
  </div>

  <div v-else class="space-y-3">
    <p class="text-xs text-gray-500 mb-3">Editing: <code class="bg-gray-100 px-1 rounded">{{ local.dn }}</code></p>
    <template v-for="(_, key) in local.attributes" :key="key">
      <FormField :label="key" v-model="local.attributes[key]" type="textarea" :rows="2" hint="One value per line" />
    </template>
  </div>
</template>

<script setup>
import { reactive, watch, nextTick, computed } from 'vue'
import FormField from '@/components/FormField.vue'

const props = defineProps({
  data: { type: Object, required: true },
  isEdit: Boolean,
  userFormConfig: { type: Object, default: null },
})
const emit = defineEmits(['update'])

const local = reactive({
  ...props.data,
  attributes: { ...(props.data.attributes || {}) }
})

const INPUT_TYPE_MAP = {
  TEXT: 'text',
  TEXTAREA: 'textarea',
  PASSWORD: 'password',
  BOOLEAN: 'checkbox',
  DATE: 'date',
  DATETIME: 'datetime-local',
  MULTI_VALUE: 'textarea',
  DN_LOOKUP: 'text',
}

function mapInputType(inputType) {
  return INPUT_TYPE_MAP[inputType] || 'text'
}

const sortedAttributeConfigs = computed(() => {
  if (!props.userFormConfig?.attributeConfigs) return []
  return [...props.userFormConfig.attributeConfigs].sort((a, b) => {
    // Required fields first, then alphabetical
    if (a.requiredOnCreate !== b.requiredOnCreate) return a.requiredOnCreate ? -1 : 1
    return a.attributeName.localeCompare(b.attributeName)
  })
})

let syncing = false
watch(local, v => {
  if (syncing) return
  emit('update', JSON.parse(JSON.stringify(v)))
}, { deep: true })
watch(() => props.data, v => {
  syncing = true
  Object.assign(local, v)
  Object.assign(local.attributes, v.attributes || {})
  nextTick(() => { syncing = false })
}, { deep: true })
</script>
