<template>
  <div>
    <!-- Tabs (shown in both create and edit modes) -->
    <div class="flex border-b border-gray-200 mb-2">
      <button
        @click="activeTab = 'attributes'"
        class="px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors"
        :class="activeTab === 'attributes' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'"
      >Attributes</button>
      <button
        @click="activeTab = 'groups'"
        class="px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors"
        :class="activeTab === 'groups' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'"
      >Groups</button>
    </div>

    <!-- ═══ Attributes tab ═══ -->
    <div v-show="activeTab === 'attributes'">

      <!-- ── Create mode ── -->
      <div v-if="!isEdit" class="space-y-2">
        <!-- Fallback RDN + DN row when no user form config -->
        <div v-if="!userTemplateConfig" class="grid grid-cols-6 gap-2">
          <FormField label="RDN Attribute" v-model="local.rdnAttribute" placeholder="uid" required />
          <div class="col-span-4">
            <FormField
              label="DN"
              :model-value="computedDn"
              placeholder="uid=jsmith,ou=people,dc=example,dc=com"
              required
              disabled
            />
          </div>
        </div>

        <!-- RDN Value when using fallback (no user form config) -->
        <FormField v-if="!userTemplateConfig" label="RDN Value" v-model="local.rdnValue" placeholder="jsmith" required />

        <!-- Dynamic fields from user form config (all attributes in layout order) -->
        <template v-if="userTemplateConfig?.attributeConfigs?.length">
          <template v-for="(section, sIdx) in createSections" :key="sIdx">
            <fieldset v-if="section.fields.length" class="space-y-2">
              <legend v-if="section.name" class="text-sm font-semibold text-gray-800 pb-1 border-b border-gray-100 w-full mb-2">{{ section.name }}</legend>
              <div class="grid grid-cols-6 gap-2">
                <template
                  v-for="attr in section.fields"
                  :key="attr.id || attr.attributeName"
                >
                  <!-- RDN field -->
                  <div v-if="attr.rdn" :style="{ gridColumn: showDnField ? 'span 2' : `span ${attr.columnSpan || 6}` }">
                    <FormField
                      :label="(attr.customLabel || attr.attributeName) + ' (RDN)'"
                      v-model="local.rdnValue"
                      :type="mapInputType(attr.inputType)"
                      required
                      :placeholder="attr.attributeName"
                    />
                  </div>
                  <!-- Computed DN (shown after RDN when enabled) -->
                  <div v-if="attr.rdn && showDnField" class="col-span-4">
                    <FormField
                      label="DN"
                      :model-value="computedDn"
                      placeholder="uid=jsmith,ou=people,dc=example,dc=com"
                      required
                      disabled
                    />
                  </div>
                  <!-- Regular field -->
                  <div
                    v-if="!attr.rdn"
                    :style="{ gridColumn: `span ${attr.columnSpan || 6}` }"
                  >
                    <FormField
                      :label="attr.customLabel || attr.attributeName"
                      v-model="local.attributes[attr.attributeName]"
                      :type="mapInputType(attr.inputType)"
                      :options="attr.inputType === 'SELECT' ? parseOptions(attr.allowedValues) : undefined"
                      :required="attr.requiredOnCreate"
                      :disabled="!attr.editableOnCreate"
                      :rows="attr.inputType === 'TEXTAREA' || attr.inputType === 'MULTI_VALUE' ? 3 : undefined"
                      :hint="attr.inputType === 'MULTI_VALUE' ? 'One value per line' : undefined"
                    />
                  </div>
                </template>
              </div>
            </fieldset>
          </template>
        </template>

        <!-- Fallback: hardcoded fields when no user form config -->
        <template v-if="!userTemplateConfig">
          <FormField label="cn (Common Name)" v-model="local.attributes.cn" required />
          <FormField label="sn (Surname)" v-model="local.attributes.sn" />
          <FormField label="mail" v-model="local.attributes.mail" />
          <FormField label="userPassword" type="password" v-model="local.attributes.userPassword" />
        </template>
      </div>

      <!-- ── Edit mode ── -->
      <div v-else class="space-y-2">
        <p class="text-xs text-gray-500 mb-2">Editing: <code class="bg-gray-100 px-1 rounded">{{ local.dn }}</code></p>

        <!-- When user form config is available, render structured fields -->
        <template v-if="userTemplateConfig?.attributeConfigs?.length">
          <template v-for="(section, sIdx) in editSections" :key="sIdx">
            <fieldset v-if="section.fields.length" class="space-y-2">
              <legend v-if="section.name" class="text-sm font-semibold text-gray-800 pb-1 border-b border-gray-100 w-full mb-2">{{ section.name }}</legend>
              <div class="grid grid-cols-6 gap-2">
                <template
                  v-for="attr in section.fields"
                  :key="attr.id || attr.attributeName"
                >
                  <!-- RDN field in edit mode -->
                  <div v-if="attr.rdn" :style="{ gridColumn: showDnField ? 'span 2' : `span ${attr.columnSpan || 6}` }">
                    <FormField
                      :label="attr.customLabel || attr.attributeName"
                      v-model="local.attributes[attr.attributeName]"
                      :type="mapInputType(attr.inputType)"
                      :required="attr.requiredOnCreate"
                      disabled
                      :rows="attr.inputType === 'TEXTAREA' || attr.inputType === 'MULTI_VALUE' ? 3 : undefined"
                      :hint="attr.inputType === 'MULTI_VALUE' ? 'One value per line' : undefined"
                    />
                  </div>
                  <!-- DN field (shown after RDN when enabled, edit mode) -->
                  <div v-if="attr.rdn && showDnField" class="col-span-4">
                    <FormField
                      label="DN"
                      :model-value="local.dn"
                      disabled
                    />
                  </div>
                  <!-- Regular field -->
                  <div
                    v-if="!attr.rdn"
                    :style="{ gridColumn: `span ${attr.columnSpan || 6}` }"
                  >
                    <FormField
                      :label="attr.customLabel || attr.attributeName"
                      v-model="local.attributes[attr.attributeName]"
                      :type="mapInputType(attr.inputType)"
                      :options="attr.inputType === 'SELECT' ? parseOptions(attr.allowedValues) : undefined"
                      :required="attr.requiredOnCreate"
                      :disabled="!attr.editableOnUpdate"
                      :rows="attr.inputType === 'TEXTAREA' || attr.inputType === 'MULTI_VALUE' ? 3 : undefined"
                      :hint="attr.inputType === 'MULTI_VALUE' ? 'One value per line' : undefined"
                    />
                  </div>
                </template>
              </div>
            </fieldset>
          </template>

          <!-- Other attributes not in the form config -->
          <div v-if="Object.keys(extraEditAttributes).length">
            <button @click="showExtraAttrs = !showExtraAttrs"
                    class="flex items-center gap-1 text-xs font-medium text-gray-500 hover:text-gray-700 mt-2">
              <svg :class="['w-3 h-3 transition-transform', showExtraAttrs && 'rotate-90']"
                   viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M7.21 14.77a.75.75 0 01.02-1.06L11.168 10 7.23 6.29a.75.75 0 111.04-1.08l4.5 4.25a.75.75 0 010 1.08l-4.5 4.25a.75.75 0 01-1.06-.02z" clip-rule="evenodd"/>
              </svg>
              Other Attributes ({{ Object.keys(extraEditAttributes).length }})
            </button>
            <div v-if="showExtraAttrs" class="space-y-2 mt-3 pl-3 border-l-2 border-gray-100">
              <template v-for="(_, key) in extraEditAttributes" :key="key">
                <FormField :label="key" v-model="local.attributes[key]" type="textarea" :rows="2" hint="One value per line" />
              </template>
            </div>
          </div>
        </template>

        <!-- Fallback: raw attribute editing when no form config -->
        <template v-else>
          <template v-for="(_, key) in editableAttributes" :key="key">
            <FormField :label="key" v-model="local.attributes[key]" type="textarea" :rows="2" hint="One value per line" />
          </template>
        </template>
      </div>
    </div>

    <!-- ═══ Groups tab ═══ -->
    <div v-show="activeTab === 'groups'">
      <p v-if="isEdit" class="text-xs text-gray-500 mb-2">Manage group memberships for <code class="bg-gray-100 px-1 rounded">{{ local.dn }}</code></p>
      <p v-else class="text-xs text-gray-500 mb-2">Select groups for the new user. Memberships will be created after the user is saved.</p>

      <!-- Current memberships (edit mode only) -->
      <div v-if="isEdit" class="mb-2">
        <h3 class="text-sm font-medium text-gray-700 mb-2">Current Groups</h3>
        <div v-if="loadingGroups" class="text-sm text-gray-400 py-3 text-center">Loading…</div>
        <ul v-else-if="memberGroups.length" class="divide-y divide-gray-100 border border-gray-200 rounded-lg overflow-hidden">
          <li v-for="g in memberGroups" :key="g.dn" class="flex items-center justify-between px-3 py-2 text-sm hover:bg-gray-50">
            <div>
              <span class="font-medium text-gray-800">{{ g.cn }}</span>
              <code class="text-xs text-gray-400 ml-2">{{ g.dn }}</code>
            </div>
            <button @click="removeFromGroup(g)" class="text-red-500 hover:text-red-700 text-xs font-medium">Remove</button>
          </li>
        </ul>
        <p v-else class="text-sm text-gray-400 py-3 text-center border border-gray-200 rounded-lg">Not a member of any groups</p>
      </div>

      <!-- Pending groups (create mode only) -->
      <div v-if="!isEdit && pendingGroups.length" class="mb-2">
        <h3 class="text-sm font-medium text-gray-700 mb-2">Groups to Join</h3>
        <ul class="divide-y divide-gray-100 border border-gray-200 rounded-lg overflow-hidden">
          <li v-for="g in pendingGroups" :key="g.dn" class="flex items-center justify-between px-3 py-2 text-sm hover:bg-gray-50">
            <div>
              <span class="font-medium text-gray-800">{{ g.cn }}</span>
              <code class="text-xs text-gray-400 ml-2">{{ g.dn }}</code>
            </div>
            <button @click="removePendingGroup(g)" class="text-red-500 hover:text-red-700 text-xs font-medium">Remove</button>
          </li>
        </ul>
      </div>

      <!-- Add to group -->
      <div>
        <h3 class="text-sm font-medium text-gray-700 mb-2">Add to Group</h3>
        <div class="flex gap-2 mb-2">
          <input
            v-model="groupFilter"
            placeholder="Search groups…"
            @keyup.enter="searchAvailableGroups"
            class="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <button @click="searchAvailableGroups" class="btn-primary text-xs">Search</button>
        </div>
        <div v-if="loadingGroups" class="text-sm text-gray-400 py-3 text-center">Loading…</div>
        <ul v-else-if="availableGroups.length" class="divide-y divide-gray-100 border border-gray-200 rounded-lg overflow-hidden max-h-48 overflow-y-auto">
          <li v-for="g in availableGroups" :key="g.dn" class="flex items-center justify-between px-3 py-2 text-sm hover:bg-gray-50">
            <div>
              <span class="font-medium text-gray-800">{{ g.cn }}</span>
              <code class="text-xs text-gray-400 ml-2">{{ g.dn }}</code>
            </div>
            <button @click="addToGroup(g)" class="text-blue-600 hover:text-blue-800 text-xs font-medium">Add</button>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, watch, nextTick, computed, onMounted } from 'vue'
import { useNotificationStore } from '@/stores/notifications'
import FormField from '@/components/FormField.vue'
import * as groupsApi from '@/api/groups'

const props = defineProps({
  data: { type: Object, required: true },
  isEdit: Boolean,
  userTemplateConfig: { type: Object, default: null },
  dirId: { type: String, default: null },
})
const emit = defineEmits(['update'])

const local = reactive({
  ...props.data,
  attributes: { ...(props.data.attributes || {}) }
})

// Ensure SELECT fields have their defaultValue applied even if emptyForm() missed them
if (!props.isEdit && props.userTemplateConfig?.attributeConfigs) {
  for (const attr of props.userTemplateConfig.attributeConfigs) {
    if (attr.inputType === 'SELECT' && attr.defaultValue && !local.attributes[attr.attributeName]) {
      local.attributes[attr.attributeName] = attr.defaultValue
    }
  }
}

const activeTab       = ref('attributes')
const loadingGroups   = ref(false)
const memberGroups    = ref([])
const availableGroups = ref([])
const groupFilter     = ref('')
const allGroups       = ref([])
const pendingGroups   = ref([])

const showExtraAttrs = ref(false)

const HIDDEN_EDIT_ATTRS = new Set(['objectclass', 'objectClass'])

/** Attributes to show in edit mode (excludes objectClass). */
const editableAttributes = computed(() => {
  const result = {}
  for (const key of Object.keys(local.attributes)) {
    if (!HIDDEN_EDIT_ATTRS.has(key)) {
      result[key] = local.attributes[key]
    }
  }
  return result
})

/** Attributes from the form config to show in edit mode (excludes objectClass and hidden, includes RDN). */
const editFormAttributes = computed(() => {
  if (!props.userTemplateConfig?.attributeConfigs) return []
  const rdnName = props.userTemplateConfig.rdnAttribute
  return props.userTemplateConfig.attributeConfigs
    .filter(a => !a.hidden && a.attributeName.toLowerCase() !== 'objectclass')
    .map(a => ({ ...a, rdn: a.attributeName === rdnName }))
})

/** Attributes present on the entry but NOT in the form config (edit mode overflow). */
const extraEditAttributes = computed(() => {
  if (!props.userTemplateConfig?.attributeConfigs) return {}
  const configuredNames = new Set(
    props.userTemplateConfig.attributeConfigs.map(a => a.attributeName.toLowerCase())
  )
  const result = {}
  for (const key of Object.keys(local.attributes)) {
    if (!HIDDEN_EDIT_ATTRS.has(key) && !configuredNames.has(key.toLowerCase())) {
      result[key] = local.attributes[key]
    }
  }
  return result
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
  SELECT: 'select',
  HIDDEN_FIXED: 'hidden',
}

function mapInputType(inputType) {
  return INPUT_TYPE_MAP[inputType] || 'text'
}

/** Parse the allowedValues JSON string into FormField options. */
function parseOptions(allowedValues) {
  if (!allowedValues) return []
  try {
    const arr = JSON.parse(allowedValues)
    if (!Array.isArray(arr)) return []
    return arr.map(v => ({ value: String(v), label: String(v) }))
  } catch {
    return allowedValues.split(',').map(v => ({ value: v.trim(), label: v.trim() }))
  }
}

/** The attribute marked as RDN in the user form config. */
const rdnAttr = computed(() => {
  if (!props.userTemplateConfig?.attributeConfigs) return null
  const rdnName = props.userTemplateConfig.rdnAttribute
  return props.userTemplateConfig.attributeConfigs.find(a => a.attributeName === rdnName) || null
})

/** Computed full DN based on RDN attribute, RDN value, and parent DN. */
const computedDn = computed(() => {
  const attr = rdnAttr.value?.attributeName || local.rdnAttribute || ''
  const val = local.rdnValue || ''
  const base = local.parentDn || ''
  if (!attr || !val || !base) return ''
  return `${attr}=${val},${base}`
})

/** Whether to show the DN field alongside the RDN. */
const showDnField = computed(() => props.userTemplateConfig?.showDnField !== false)

/** All non-hidden attributes (including RDN), preserving the order defined in the user form config. */
const allVisibleAttributes = computed(() => {
  if (!props.userTemplateConfig?.attributeConfigs) return []
  const rdnName = props.userTemplateConfig.rdnAttribute
  return props.userTemplateConfig.attributeConfigs
    .filter(a => !a.hidden && a.attributeName.toLowerCase() !== 'objectclass')
    .map(a => ({ ...a, rdn: a.attributeName === rdnName }))
})

/** Group all visible attributes into sections for create mode. */
const createSections = computed(() => groupIntoSections(allVisibleAttributes.value))

/** Group edit-mode attributes into sections. */
const editSections = computed(() => groupIntoSections(editFormAttributes.value))

function groupIntoSections(attrs) {
  const map = new Map()
  for (const attr of attrs) {
    const key = attr.sectionName || ''
    if (!map.has(key)) {
      map.set(key, { name: key, fields: [] })
    }
    map.get(key).fields.push(attr)
  }
  const result = Array.from(map.values())
  return result.length ? result : [{ name: '', fields: attrs }]
}

/**
 * Evaluate computed expressions like "${givenName}.${sn}@corp.com".
 * Substitutes ${attrName} references with current attribute values.
 */
function evaluateComputedExpressions() {
  if (!props.userTemplateConfig?.attributeConfigs) return
  for (const attr of props.userTemplateConfig.attributeConfigs) {
    if (!attr.computedExpression) continue
    const expr = attr.computedExpression
    let resolved = expr.replace(/\$\{(\w+)\}/g, (_, name) => {
      // Check rdnValue first since it's stored separately
      if (name === local.rdnAttribute) return local.rdnValue || ''
      return local.attributes[name] || ''
    })
    // Evaluate + concatenation with quoted strings, e.g. +" "+ or +' '+
    resolved = resolved.replace(/\+["']([^"']*)["']/g, '$1').replace(/["']([^"']*)["']\+/g, '$1')
    // Only set if expression produced a meaningful result (not all placeholders empty)
    if (resolved !== expr || !expr.includes('${')) {
      local.attributes[attr.attributeName] = resolved
    }
  }
}

let syncing = false
let computing = false
watch(local, v => {
  if (syncing) return
  if (!computing && !props.isEdit) {
    computing = true
    evaluateComputedExpressions()
    // Allow the watcher to re-fire with computed values included
    nextTick(() => { computing = false })
  }
  emit('update', JSON.parse(JSON.stringify(v)))
}, { deep: true })
watch(() => props.data, v => {
  syncing = true
  Object.assign(local, v)
  Object.assign(local.attributes, v.attributes || {})
  nextTick(() => { syncing = false })
}, { deep: true })

// ── Group membership management ──────────────────────────────────────────────

async function loadGroups() {
  if (!props.dirId) return
  loadingGroups.value = true
  try {
    const { data } = await groupsApi.searchGroups(props.dirId, {})
    const entries = Array.isArray(data) ? data : (data?.entries || [])
    allGroups.value = entries.map(e => ({
      dn: e.dn,
      cn: e.attributes?.cn?.[0] || '—',
      members: e.attributes?.member || e.attributes?.uniqueMember || e.attributes?.memberUid || [],
      memberAttr: e.attributes?.member ? 'member'
        : e.attributes?.uniqueMember ? 'uniqueMember'
        : e.attributes?.memberUid ? 'memberUid'
        : 'member',
    }))
    refreshMemberships()
  } catch (e) { console.warn('Failed to load groups:', e) }
  finally { loadingGroups.value = false }
}

function refreshMemberships() {
  if (props.isEdit) {
    const userDn = local.dn
    memberGroups.value = allGroups.value.filter(g =>
      g.members.some(m => m.toLowerCase() === userDn.toLowerCase())
    )
  }
  filterAvailableGroups()
}

function filterAvailableGroups() {
  const excludedDnSet = new Set()
  // Exclude groups user is already a member of (edit mode)
  for (const g of memberGroups.value) excludedDnSet.add(g.dn.toLowerCase())
  // Exclude groups already pending (create mode)
  for (const g of pendingGroups.value) excludedDnSet.add(g.dn.toLowerCase())

  const q = groupFilter.value.toLowerCase()
  availableGroups.value = allGroups.value.filter(g =>
    !excludedDnSet.has(g.dn.toLowerCase()) &&
    (!q || g.cn.toLowerCase().includes(q) || g.dn.toLowerCase().includes(q))
  )
}

function searchAvailableGroups() {
  filterAvailableGroups()
}

async function addToGroup(group) {
  if (props.isEdit) {
    // Edit mode: immediately persist the membership via API
    try {
      const res = await groupsApi.addGroupMember(props.dirId, group.dn, {
        memberAttribute: group.memberAttr,
        memberValue: local.dn,
      })
      if (res.status === 202) {
        const notif = useNotificationStore()
        notif.success('Group member addition submitted for approval')
      } else {
        group.members.push(local.dn)
      }
      refreshMemberships()
    } catch (e) {
      // silent
    }
  } else {
    // Create mode: queue for after save
    pendingGroups.value.push(group)
    filterAvailableGroups()
    emitPendingGroups()
  }
}

async function removeFromGroup(group) {
  try {
    await groupsApi.removeGroupMember(props.dirId, group.dn, {
      memberAttribute: group.memberAttr,
      memberValue: local.dn,
    })
    group.members = group.members.filter(m => m.toLowerCase() !== local.dn.toLowerCase())
    refreshMemberships()
  } catch (e) {
    // silent
  }
}

function removePendingGroup(group) {
  pendingGroups.value = pendingGroups.value.filter(g => g.dn !== group.dn)
  filterAvailableGroups()
  emitPendingGroups()
}

function emitPendingGroups() {
  emit('update', {
    ...JSON.parse(JSON.stringify(local)),
    _pendingGroups: pendingGroups.value.map(g => ({ dn: g.dn, memberAttr: g.memberAttr })),
  })
}

onMounted(() => {
  // Initialize pending groups from profile group assignments passed via data
  if (props.data?._pendingGroups?.length) {
    pendingGroups.value = props.data._pendingGroups.map(g => ({
      dn: g.dn,
      cn: g.dn.split(',')[0] || g.dn,
      memberAttr: g.memberAttr,
      members: [],
    }))
  }
  if (props.dirId) {
    loadGroups()
  }
})

// Reload groups when switching to edit mode with a new user
watch(() => props.data?.dn, () => {
  if (props.dirId) {
    activeTab.value = 'attributes'
    pendingGroups.value = []
    loadGroups()
  }
})
</script>
