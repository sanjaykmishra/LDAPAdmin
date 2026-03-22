<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useNotificationStore } from '@/stores/notifications'
import {
  listAllProfiles, createProfile, updateProfile, deleteProfile, cloneProfile,
  getLifecyclePolicy, setLifecyclePolicy, deleteLifecyclePolicy,
  getApprovalConfig, setApprovalConfig, getApprovers, setApprovers
} from '@/api/profiles'
import { listDirectories } from '@/api/directories'
import { listObjectClasses, getObjectClass, getObjectClassesBulk } from '@/api/schema'
import { listAdmins } from '@/api/adminManagement'
import AppModal from '@/components/AppModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormLayoutDesigner from '@/components/FormLayoutDesigner.vue'
import DnPicker from '@/components/DnPicker.vue'

const notif = useNotificationStore()

const loading = ref(false)
const saving = ref(false)
const profiles = ref([])
const directories = ref([])
const admins = ref([])

const showModal = ref(false)
const editing = ref(null)
const showDeleteConfirm = ref(false)
const deleteTarget = ref(null)
const modalTab = ref('general')

// Schema caching
const objectClasses = ref([])
const loadingOCs = ref(false)
const selectedDirId = ref(null)
const ocSchemaCache = ref({})

// Profile form
const profile = ref(emptyProfile())

// Lifecycle form
const lifecycle = ref(emptyLifecycle())

// Approval form
const approval = ref(emptyApproval())
const profileApprovers = ref([])

function emptyProfile() {
  return {
    name: '', description: '', targetOuDn: '',
    objectClassNames: [], rdnAttribute: '',
    showDnField: true, enabled: true, selfRegistrationAllowed: false,
    attributeConfigs: [], groupAssignments: []
  }
}

function emptyLifecycle() {
  return {
    expiresAfterDays: null, maxRenewals: null, renewalDays: null,
    onExpiryAction: 'DISABLE', onExpiryMoveDn: '', onExpiryRemoveGroups: true,
    onExpiryNotify: true, warningDaysBefore: null
  }
}

function emptyApproval() {
  return {
    requireApproval: false, approverMode: 'DATABASE',
    approverGroupDn: '', autoEscalateDays: null, escalationAccountId: null
  }
}

onMounted(async () => {
  loading.value = true
  try {
    const [profilesRes, dirsRes, adminsRes] = await Promise.all([
      listAllProfiles(), listDirectories(), listAdmins()
    ])
    profiles.value = profilesRes.data
    directories.value = dirsRes.data
    admins.value = adminsRes.data
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  } finally {
    loading.value = false
  }
})

watch(selectedDirId, async (dirId) => {
  if (!dirId) return
  objectClasses.value = []
  loadingOCs.value = true
  try {
    const { data } = await listObjectClasses(dirId)
    objectClasses.value = data.map(oc => typeof oc === 'string' ? oc : oc.name)
  } catch (e) {
    notif.error('Failed to load object classes')
  } finally {
    loadingOCs.value = false
  }
})

function openCreate() {
  editing.value = null
  profile.value = emptyProfile()
  lifecycle.value = emptyLifecycle()
  approval.value = emptyApproval()
  profileApprovers.value = []
  schemaRequiredAttrs.value = new Set()
  ocSchemaCache.value = {}
  selectedDirId.value = directories.value.length > 0 ? directories.value[0].id : null
  modalTab.value = 'general'
  showModal.value = true
}

async function openEdit(p) {
  editing.value = p.id
  selectedDirId.value = p.directoryId
  profile.value = {
    name: p.name, description: p.description || '', targetOuDn: p.targetOuDn,
    objectClassNames: [...p.objectClassNames], rdnAttribute: p.rdnAttribute,
    showDnField: p.showDnField, enabled: p.enabled,
    selfRegistrationAllowed: p.selfRegistrationAllowed,
    attributeConfigs: p.attributeConfigs.map(a => ({
      attributeName: a.attributeName, customLabel: a.customLabel || '',
      inputType: a.inputType, requiredOnCreate: a.requiredOnCreate,
      editableOnCreate: a.editableOnCreate, editableOnUpdate: a.editableOnUpdate,
      selfServiceEdit: a.selfServiceEdit, selfRegistrationEdit: a.selfRegistrationEdit,
      defaultValue: a.defaultValue || '',
      computedExpression: a.computedExpression || '',
      validationRegex: a.validationRegex || '', validationMessage: a.validationMessage || '',
      allowedValues: a.allowedValues || '', minLength: a.minLength,
      maxLength: a.maxLength, sectionName: a.sectionName || '',
      columnSpan: a.columnSpan, hidden: a.hidden,
      registrationSectionName: a.registrationSectionName || '',
      registrationColumnSpan: a.registrationColumnSpan, registrationDisplayOrder: a.registrationDisplayOrder,
      selfServiceSectionName: a.selfServiceSectionName || '',
      selfServiceColumnSpan: a.selfServiceColumnSpan, selfServiceDisplayOrder: a.selfServiceDisplayOrder
    })),
    groupAssignments: p.groupAssignments.map(g => ({
      groupDn: g.groupDn, memberAttribute: g.memberAttribute
    }))
  }
  modalTab.value = 'general'

  // Load schema data for existing object classes (for RDN picker and required tracking)
  schemaRequiredAttrs.value = new Set()
  ocSchemaCache.value = {}
  if (p.objectClassNames.length > 0) {
    try {
      const { data } = await getObjectClassesBulk(p.directoryId, p.objectClassNames)
      for (const oc of data) {
        const required = oc.requiredAttributes || oc.required || []
        const optional = oc.optionalAttributes || oc.optional || []
        ocSchemaCache.value[oc.name] = { required: [...required], optional: [...optional] }
        for (const attr of required) schemaRequiredAttrs.value.add(attr)
      }
    } catch { /* schema lookup optional */ }
  }

  // Load lifecycle & approval data
  try {
    const { data } = await getLifecyclePolicy(p.id)
    lifecycle.value = { ...data }
  } catch { lifecycle.value = emptyLifecycle() }

  try {
    const { data } = await getApprovalConfig(p.id)
    approval.value = { ...data }
  } catch { approval.value = emptyApproval() }

  try {
    const { data } = await getApprovers(p.id)
    profileApprovers.value = data.map(a => a.accountId)
  } catch { profileApprovers.value = [] }

  showModal.value = true
}

async function save() {
  if (!profile.value.name || !profile.value.targetOuDn) {
    notif.error('Name and Target OU DN are required')
    return
  }
  if (profile.value.objectClassNames.length === 0) {
    notif.error('At least one object class is required')
    return
  }
  if (!profile.value.rdnAttribute) {
    notif.error('RDN Attribute is required')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateProfile(selectedDirId.value, editing.value, profile.value)
      // Save lifecycle
      if (lifecycle.value.expiresAfterDays != null) {
        await setLifecyclePolicy(editing.value, lifecycle.value)
      }
      // Save approval config
      await setApprovalConfig(editing.value, approval.value)
      await setApprovers(editing.value, { accountIds: profileApprovers.value })
      notif.success('Profile updated')
    } else {
      const { data } = await createProfile(selectedDirId.value, profile.value)
      // Save lifecycle if configured
      if (lifecycle.value.expiresAfterDays != null) {
        await setLifecyclePolicy(data.id, lifecycle.value)
      }
      // Save approval config
      await setApprovalConfig(data.id, approval.value)
      if (profileApprovers.value.length > 0) {
        await setApprovers(data.id, { accountIds: profileApprovers.value })
      }
      notif.success('Profile created')
    }
    showModal.value = false
    await reload()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.response?.data?.message || e.message)
  } finally {
    saving.value = false
  }
}

async function confirmDelete(p) {
  deleteTarget.value = p
  showDeleteConfirm.value = true
}

async function doDelete() {
  try {
    await deleteProfile(deleteTarget.value.directoryId, deleteTarget.value.id)
    notif.success('Profile deleted')
    showDeleteConfirm.value = false
    await reload()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  }
}

async function doClone(p) {
  const name = prompt('New profile name:', p.name + ' (Copy)')
  if (!name) return
  try {
    await cloneProfile(p.directoryId, p.id, name)
    notif.success('Profile cloned')
    await reload()
  } catch (e) {
    notif.error(e.response?.data?.detail || e.message)
  }
}

async function reload() {
  const { data } = await listAllProfiles()
  profiles.value = data
}

// Group assignment management
function addGroupAssignment() {
  profile.value.groupAssignments.push({ groupDn: '', memberAttribute: 'member' })
}
function removeGroupAssignment(index) {
  profile.value.groupAssignments.splice(index, 1)
}

// Object class management
const ocToAdd = ref('')
// Track which attributes are required by the schema (cannot uncheck required or remove)
const schemaRequiredAttrs = ref(new Set())

// Attributes commonly safe for users to self-edit
const SELF_SERVICE_EDITABLE_ATTRS = new Set([
  'givenname', 'sn', 'displayname', 'cn', 'preferredlanguage',
  'mail', 'telephonenumber', 'mobile', 'facsimiletelephonenumber', 'pager',
  'street', 'l', 'st', 'postalcode', 'postaladdress', 'co',
  'title', 'description',
  'jpegphoto', 'labeleduri', 'homephone',
])

function isSelfServiceEditable(attrName) {
  return SELF_SERVICE_EDITABLE_ATTRS.has(attrName.toLowerCase())
}

async function addObjectClass() {
  if (!ocToAdd.value) return
  profile.value.objectClassNames.push(ocToAdd.value)
  // Load schema attributes for this OC
  try {
    const { data } = await getObjectClass(selectedDirId.value, ocToAdd.value)
    const required = data.requiredAttributes || data.required || []
    const optional = data.optionalAttributes || data.optional || []
    // Track schema-required attributes and cache for RDN picker
    for (const attr of required) schemaRequiredAttrs.value.add(attr)
    ocSchemaCache.value[ocToAdd.value] = { required: [...required], optional: [...optional] }
    for (const attr of [...required, ...optional]) {
      if (!profile.value.attributeConfigs.find(a => a.attributeName === attr)) {
        const isObjClass = attr.toLowerCase() === 'objectclass'
        profile.value.attributeConfigs.push({
          attributeName: attr, customLabel: '', inputType: isObjClass ? 'HIDDEN_FIXED' : 'TEXT',
          requiredOnCreate: required.includes(attr), editableOnCreate: !isObjClass,
          editableOnUpdate: !isObjClass, selfServiceEdit: !isObjClass && isSelfServiceEditable(attr),
          selfRegistrationEdit: false,
          defaultValue: '', computedExpression: '', validationRegex: '',
          validationMessage: '', allowedValues: '', minLength: null,
          maxLength: null, sectionName: '', columnSpan: 3, hidden: isObjClass,
          registrationSectionName: '', registrationColumnSpan: 3, registrationDisplayOrder: 0,
          selfServiceSectionName: '', selfServiceColumnSpan: 3, selfServiceDisplayOrder: 0
        })
      }
    }
  } catch { /* schema lookup optional */ }
  ocToAdd.value = ''
}
function removeObjectClass(name) {
  profile.value.objectClassNames = profile.value.objectClassNames.filter(n => n !== name)
  // Rebuild schema-required set from remaining OCs
  rebuildSchemaRequired()
}

async function rebuildSchemaRequired() {
  schemaRequiredAttrs.value = new Set()
  for (const ocName of profile.value.objectClassNames) {
    const cached = ocSchemaCache.value[ocName]
    if (cached) {
      for (const attr of cached.required) schemaRequiredAttrs.value.add(attr)
    }
  }
}

function dirName(dirId) {
  const d = directories.value.find(d => d.id === dirId)
  return d ? d.displayName : dirId
}

const availableObjectClasses = computed(() => {
  const added = new Set(profile.value.objectClassNames.map(n => n.toLowerCase()))
  return objectClasses.value.filter(oc => !added.has(oc.toLowerCase()))
})

// RDN attribute candidates: all attributes from selected object classes
const rdnCandidates = computed(() => {
  const attrs = new Set()
  for (const ocName of profile.value.objectClassNames) {
    const cached = ocSchemaCache.value[ocName]
    if (cached) {
      for (const a of [...cached.required, ...cached.optional]) {
        if (a.toLowerCase() !== 'objectclass') attrs.add(a)
      }
    }
  }
  // Also include any configured attribute names
  for (const a of profile.value.attributeConfigs) {
    if (a.attributeName.toLowerCase() !== 'objectclass') attrs.add(a.attributeName)
  }
  return [...attrs].sort()
})

// Helper: check if an attribute is the RDN attribute
function isRdnAttribute(attr) {
  return attr.attributeName === profile.value.rdnAttribute
}

// Helper: check if an attribute is schema-required
function isSchemaRequired(attr) {
  return schemaRequiredAttrs.value.has(attr.attributeName)
}

// Helper: check if an attribute can be removed
function canRemoveAttribute(attr) {
  return !isRdnAttribute(attr) && !isSchemaRequired(attr)
}

// Helper: determine which fields to show based on input type
function showFieldFor(inputType, fieldName) {
  const rules = {
    defaultValue:       ['TEXT', 'TEXTAREA', 'PASSWORD', 'DATE', 'DATETIME', 'MULTI_VALUE', 'HIDDEN_FIXED', 'SELECT'],
    allowedValues:      ['SELECT'],
    computedExpression: ['TEXT', 'TEXTAREA', 'PASSWORD', 'MULTI_VALUE', 'DATE', 'DATETIME', 'DN_LOOKUP'],
    validationRegex:    ['TEXT', 'TEXTAREA', 'PASSWORD', 'MULTI_VALUE'],
  }
  return (rules[fieldName] || []).includes(inputType)
}

// Ensure RDN attribute is always marked as required
watch(() => profile.value.rdnAttribute, (rdnAttr) => {
  if (!rdnAttr) return
  const attr = profile.value.attributeConfigs.find(a => a.attributeName === rdnAttr)
  if (attr) attr.requiredOnCreate = true
})

// Attribute configs with RDN flag for the layout designer
const layoutAttributeConfigs = computed({
  get() {
    return profile.value.attributeConfigs.map(a => ({
      ...a,
      rdn: a.attributeName === profile.value.rdnAttribute,
    }))
  },
  set(val) {
    profile.value.attributeConfigs = val.map(({ rdn, ...rest }) => rest)
  }
})

// Registration layout: only self-registration-enabled, non-hidden fields,
// mapping registration-specific layout properties to sectionName/columnSpan
// so FormLayoutDesigner can manage them independently.
const registrationAttributeConfigs = computed({
  get() {
    return profile.value.attributeConfigs
      .filter(a => a.selfRegistrationEdit && !a.hidden && a.inputType !== 'HIDDEN_FIXED')
      .map(a => ({
        ...a,
        rdn: a.attributeName === profile.value.rdnAttribute,
        // Present registration layout fields as sectionName/columnSpan for the designer
        sectionName: a.registrationSectionName || '',
        columnSpan: a.registrationColumnSpan ?? 3,
      }))
  },
  set(val) {
    // Write back registration layout properties to the main attributeConfigs
    const lookup = new Map(val.map((v, i) => [v.attributeName, { ...v, registrationDisplayOrder: i }]))
    profile.value.attributeConfigs = profile.value.attributeConfigs.map(a => {
      const updated = lookup.get(a.attributeName)
      if (updated) {
        return {
          ...a,
          registrationSectionName: updated.sectionName || '',
          registrationColumnSpan: updated.columnSpan ?? 3,
          registrationDisplayOrder: updated.registrationDisplayOrder,
        }
      }
      return a
    })
  }
})

// Self-service layout: only self-service-editable, non-hidden fields,
// mapping self-service-specific layout properties to sectionName/columnSpan
// so FormLayoutDesigner can manage them independently.
const selfServiceAttributeConfigs = computed({
  get() {
    return profile.value.attributeConfigs
      .filter(a => a.selfServiceEdit && !a.hidden && a.inputType !== 'HIDDEN_FIXED')
      .map(a => ({
        ...a,
        rdn: a.attributeName === profile.value.rdnAttribute,
        sectionName: a.selfServiceSectionName || '',
        columnSpan: a.selfServiceColumnSpan ?? 3,
      }))
  },
  set(val) {
    const lookup = new Map(val.map((v, i) => [v.attributeName, { ...v, selfServiceDisplayOrder: i }]))
    profile.value.attributeConfigs = profile.value.attributeConfigs.map(a => {
      const updated = lookup.get(a.attributeName)
      if (updated) {
        return {
          ...a,
          selfServiceSectionName: updated.sectionName || '',
          selfServiceColumnSpan: updated.columnSpan ?? 3,
          selfServiceDisplayOrder: updated.selfServiceDisplayOrder,
        }
      }
      return a
    })
  }
})

const modalTabs = computed(() => {
  const tabs = [
    { id: 'general', label: 'General' },
    { id: 'attributes', label: 'Attributes' },
    { id: 'admin-layout', label: 'Admin Layout' },
    { id: 'self-service-layout', label: 'Self-service Layout' },
    { id: 'registration-layout', label: 'Self-registration Layout' },
    { id: 'groups', label: 'Groups' },
    { id: 'lifecycle', label: 'Lifecycle' },
    { id: 'approval', label: 'Approval' },
  ]
  return tabs
})

function toggleApprover(accountId) {
  const idx = profileApprovers.value.indexOf(accountId)
  if (idx >= 0) profileApprovers.value.splice(idx, 1)
  else profileApprovers.value.push(accountId)
}
</script>

<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Provisioning Profiles</h1>
      <button class="btn-primary" @click="openCreate">+ Create Profile</button>
    </div>

    <div v-if="loading" class="text-gray-500">Loading…</div>

    <table v-else-if="profiles.length" class="w-full text-sm">
      <thead>
        <tr class="border-b text-left text-gray-500">
          <th class="py-2 px-3">Name</th>
          <th class="py-2 px-3">Directory</th>
          <th class="py-2 px-3">Target OU</th>
          <th class="py-2 px-3">Object Classes</th>
          <th class="py-2 px-3">Status</th>
          <th class="py-2 px-3 text-right">Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="p in profiles" :key="p.id" class="border-b hover:bg-gray-50">
          <td class="py-2 px-3 font-medium">{{ p.name }}</td>
          <td class="py-2 px-3 text-gray-600">{{ p.directoryName }}</td>
          <td class="py-2 px-3 text-gray-600 font-mono text-xs">{{ p.targetOuDn }}</td>
          <td class="py-2 px-3 text-gray-600 text-xs">{{ p.objectClassNames.join(', ') }}</td>
          <td class="py-2 px-3">
            <span :class="p.enabled ? 'text-green-600' : 'text-gray-400'">
              {{ p.enabled ? 'Enabled' : 'Disabled' }}
            </span>
          </td>
          <td class="py-2 px-3 text-right space-x-2">
            <button class="text-blue-600 hover:underline" @click="openEdit(p)">Edit</button>
            <button class="text-blue-600 hover:underline" @click="doClone(p)">Clone</button>
            <button class="text-red-600 hover:underline" @click="confirmDelete(p)">Delete</button>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-else class="text-gray-500">No provisioning profiles configured.</div>

    <!-- Create/Edit Modal -->
    <AppModal v-model="showModal" :title="editing ? 'Edit Profile' : 'Create Profile'" size="xl">
      <div class="space-y-4">
        <!-- Tab Navigation -->
        <div class="flex border-b gap-1">
          <button v-for="tab in modalTabs" :key="tab.id"
            :class="['px-4 py-2 text-sm font-medium border-b-2 -mb-px whitespace-nowrap',
              modalTab === tab.id ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700']"
            @click="modalTab = tab.id">
            {{ tab.label }}
          </button>
        </div>

        <!-- General Tab -->
        <div v-if="modalTab === 'general'" class="space-y-4">
          <div v-if="!editing">
            <label class="block text-sm font-medium text-gray-700 mb-1">Directory</label>
            <select v-model="selectedDirId" class="input w-full">
              <option v-for="d in directories" :key="d.id" :value="d.id">{{ d.displayName }}</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Name</label>
            <input v-model="profile.name" class="input w-full" placeholder="e.g. Full-Time Engineer" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
            <textarea v-model="profile.description" class="input w-full" rows="2"></textarea>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Target OU DN</label>
            <DnPicker v-model="profile.targetOuDn" :directory-id="selectedDirId"
              placeholder="e.g. ou=engineers,ou=people,dc=corp" />
          </div>
          <div class="grid grid-cols-3 gap-4 items-end">
            <div class="col-span-2">
              <label class="block text-sm font-medium text-gray-700 mb-1">Object Classes</label>
              <div v-if="profile.objectClassNames.length" class="flex gap-2 mb-2 flex-wrap">
                <span v-for="oc in profile.objectClassNames" :key="oc"
                  class="inline-flex items-center gap-1 px-2 py-1 bg-blue-100 text-blue-700 rounded text-xs">
                  {{ oc }}
                  <button @click="removeObjectClass(oc)" class="text-blue-400 hover:text-red-600">&times;</button>
                </span>
              </div>
              <div class="flex gap-2">
                <select v-model="ocToAdd" class="input flex-1">
                  <option value="">Select object class…</option>
                  <option v-for="oc in availableObjectClasses" :key="oc" :value="oc">{{ oc }}</option>
                </select>
                <button class="btn-secondary" @click="addObjectClass" :disabled="!ocToAdd">Add</button>
              </div>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">
                RDN Attribute <span class="text-red-500">*</span>
              </label>
              <select v-model="profile.rdnAttribute" class="input w-full"
                :disabled="profile.objectClassNames.length === 0">
                <option value="">{{ profile.objectClassNames.length === 0 ? 'Add an object class first' : 'Select RDN attribute…' }}</option>
                <option v-for="attr in rdnCandidates" :key="attr" :value="attr">{{ attr }}</option>
              </select>
            </div>
          </div>
          <div class="flex gap-6">
            <label class="flex items-center gap-2 text-sm">
              <input type="checkbox" v-model="profile.enabled" /> Enabled
            </label>
            <label class="flex items-center gap-2 text-sm">
              <input type="checkbox" v-model="profile.selfRegistrationAllowed" /> Self-registration
            </label>
          </div>
        </div>

        <!-- Attributes Tab -->
        <div v-if="modalTab === 'attributes'" class="space-y-3">
          <div v-if="profile.attributeConfigs.length === 0" class="text-gray-500 text-sm">
            Add object classes in the General tab to populate attributes.
          </div>
          <div v-for="(attr, i) in profile.attributeConfigs" :key="i"
            class="border rounded-lg p-3 space-y-2">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <span class="font-medium text-sm">{{ attr.attributeName }}</span>
                <span v-if="isRdnAttribute(attr)"
                  class="text-[10px] bg-amber-100 text-amber-700 rounded px-1.5 py-0.5 font-medium">RDN</span>
                <span v-if="isSchemaRequired(attr)"
                  class="text-[10px] bg-blue-50 text-blue-600 rounded px-1.5 py-0.5 font-medium">schema required</span>
              </div>
              <button v-if="canRemoveAttribute(attr)"
                class="text-red-500 text-xs hover:underline"
                @click="profile.attributeConfigs.splice(i, 1)">Remove</button>
              <span v-else class="text-xs text-gray-400 italic">cannot remove</span>
            </div>
            <div class="grid grid-cols-3 gap-3 text-sm">
              <div>
                <label class="block text-xs text-gray-500">Custom Label</label>
                <input v-model="attr.customLabel" class="input w-full text-sm" />
              </div>
              <div>
                <label class="block text-xs text-gray-500">Input Type</label>
                <select v-model="attr.inputType" class="input w-full text-sm">
                  <option v-for="t in ['TEXT','TEXTAREA','PASSWORD','BOOLEAN','DATE','DATETIME','MULTI_VALUE','DN_LOOKUP','SELECT','HIDDEN_FIXED']"
                    :key="t" :value="t">{{ t }}</option>
                </select>
              </div>
              <div v-if="showFieldFor(attr.inputType, 'defaultValue')">
                <label class="block text-xs text-gray-500">Default Value</label>
                <input v-model="attr.defaultValue" class="input w-full text-sm" />
              </div>
              <div v-if="showFieldFor(attr.inputType, 'computedExpression')">
                <label class="block text-xs text-gray-500">Computed Expression</label>
                <input v-model="attr.computedExpression" class="input w-full text-sm"
                  placeholder="${givenName}.${sn}@corp.com" />
              </div>
              <div v-if="showFieldFor(attr.inputType, 'validationRegex')">
                <label class="block text-xs text-gray-500">Validation Regex</label>
                <input v-model="attr.validationRegex" class="input w-full text-sm" />
              </div>
              <div v-if="showFieldFor(attr.inputType, 'allowedValues')">
                <label class="block text-xs text-gray-500">Allowed Values (JSON array)</label>
                <input v-model="attr.allowedValues" class="input w-full text-sm"
                  placeholder='["Eng","Finance","HR"]' />
              </div>
            </div>
            <div class="flex gap-4 text-xs">
              <label class="flex items-center gap-1">
                <input type="checkbox" v-model="attr.requiredOnCreate"
                  :disabled="isRdnAttribute(attr) || isSchemaRequired(attr)" /> Required
              </label>
              <label class="flex items-center gap-1"><input type="checkbox" v-model="attr.editableOnCreate" /> Editable (create)</label>
              <label class="flex items-center gap-1"><input type="checkbox" v-model="attr.editableOnUpdate" /> Editable (update)</label>
              <label class="flex items-center gap-1"><input type="checkbox" v-model="attr.selfServiceEdit" /> Self-service</label>
              <label class="flex items-center gap-1"><input type="checkbox" v-model="attr.selfRegistrationEdit" /> Self-registration</label>
              <label class="flex items-center gap-1"><input type="checkbox" v-model="attr.hidden" /> Hidden</label>
            </div>
          </div>
        </div>

        <!-- Admin Layout Tab -->
        <div v-if="modalTab === 'admin-layout'">
          <FormLayoutDesigner
            v-model:attributeConfigs="layoutAttributeConfigs"
            v-model:showDnField="profile.showDnField"
          />
        </div>

        <!-- Self-service Layout Tab -->
        <div v-if="modalTab === 'self-service-layout'">
          <div v-if="selfServiceAttributeConfigs.length === 0" class="text-gray-500 text-sm py-4">
            No self-service-editable attributes configured. Mark attributes as "Self-service" on the Attributes tab to include them here.
          </div>
          <FormLayoutDesigner
            v-else
            v-model:attributeConfigs="selfServiceAttributeConfigs"
            :showDnField="false"
            :hideDnToggle="true"
          />
        </div>

        <!-- Self-registration Layout Tab -->
        <div v-if="modalTab === 'registration-layout'">
          <div v-if="registrationAttributeConfigs.length === 0" class="text-gray-500 text-sm py-4">
            No self-registration attributes configured. Mark attributes as "Self-registration" on the Attributes tab to include them in the registration form.
          </div>
          <FormLayoutDesigner
            v-else
            v-model:attributeConfigs="registrationAttributeConfigs"
            :showDnField="false"
            :hideDnToggle="true"
          />
        </div>

        <!-- Groups Tab -->
        <div v-if="modalTab === 'groups'" class="space-y-3">
          <p class="text-sm text-gray-600">Groups users will be automatically added to on creation.</p>
          <div v-for="(g, i) in profile.groupAssignments" :key="i" class="flex gap-2 items-end">
            <div class="flex-1">
              <label class="block text-xs text-gray-500">Group DN</label>
              <input v-model="g.groupDn" class="input w-full font-mono text-sm" />
            </div>
            <div class="w-40">
              <label class="block text-xs text-gray-500">Member Attribute</label>
              <select v-model="g.memberAttribute" class="input w-full text-sm">
                <option>member</option>
                <option>uniqueMember</option>
                <option>memberUid</option>
              </select>
            </div>
            <button class="text-red-500 hover:underline text-sm pb-1" @click="removeGroupAssignment(i)">Remove</button>
          </div>
          <button class="btn-secondary text-sm" @click="addGroupAssignment">Add Group</button>
        </div>

        <!-- Lifecycle Tab -->
        <div v-if="modalTab === 'lifecycle'" class="space-y-4">
          <div class="grid grid-cols-3 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Expires After (days)</label>
              <input v-model.number="lifecycle.expiresAfterDays" type="number" class="input w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Max Renewals</label>
              <input v-model.number="lifecycle.maxRenewals" type="number" class="input w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Renewal Days</label>
              <input v-model.number="lifecycle.renewalDays" type="number" class="input w-full" />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">On Expiry Action</label>
              <select v-model="lifecycle.onExpiryAction" class="input w-full">
                <option>DISABLE</option>
                <option>DELETE</option>
                <option>MOVE</option>
              </select>
            </div>
            <div v-if="lifecycle.onExpiryAction === 'MOVE'">
              <label class="block text-sm font-medium text-gray-700 mb-1">Move to DN</label>
              <input v-model="lifecycle.onExpiryMoveDn" class="input w-full font-mono text-sm" />
            </div>
          </div>
          <div class="flex gap-6">
            <label class="flex items-center gap-2 text-sm">
              <input type="checkbox" v-model="lifecycle.onExpiryRemoveGroups" /> Remove from groups on expiry
            </label>
            <label class="flex items-center gap-2 text-sm">
              <input type="checkbox" v-model="lifecycle.onExpiryNotify" /> Send notification on expiry
            </label>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Warning Days Before Expiry</label>
            <input v-model.number="lifecycle.warningDaysBefore" type="number" class="input w-48" />
          </div>
        </div>

        <!-- Approval Tab -->
        <div v-if="modalTab === 'approval'" class="space-y-4">
          <label class="flex items-center gap-2 text-sm font-medium">
            <input type="checkbox" v-model="approval.requireApproval" /> Require approval for user creation
          </label>
          <div v-if="approval.requireApproval" class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Approver Mode</label>
              <select v-model="approval.approverMode" class="input w-full">
                <option value="DATABASE">Database (select approvers below)</option>
                <option value="LDAP_GROUP">LDAP Group</option>
              </select>
            </div>
            <div v-if="approval.approverMode === 'LDAP_GROUP'">
              <label class="block text-sm font-medium text-gray-700 mb-1">Approver Group DN</label>
              <input v-model="approval.approverGroupDn" class="input w-full font-mono text-sm" />
            </div>
            <div v-if="approval.approverMode === 'DATABASE'">
              <label class="block text-sm font-medium text-gray-700 mb-2">Approvers</label>
              <div class="space-y-1 max-h-48 overflow-y-auto border rounded p-2">
                <label v-for="admin in admins.filter(a => a.role === 'ADMIN')" :key="admin.id"
                  class="flex items-center gap-2 text-sm p-1 hover:bg-gray-50 rounded cursor-pointer">
                  <input type="checkbox"
                    :checked="profileApprovers.includes(admin.id)"
                    @change="toggleApprover(admin.id)" />
                  {{ admin.username }}
                  <span class="text-gray-400" v-if="admin.email">({{ admin.email }})</span>
                </label>
              </div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="flex justify-end gap-3">
          <button class="btn-secondary" @click="showModal = false">Cancel</button>
          <button class="btn-primary" @click="save" :disabled="saving">
            {{ saving ? 'Saving…' : (editing ? 'Update' : 'Create') }}
          </button>
        </div>
      </template>
    </AppModal>

    <ConfirmDialog v-model="showDeleteConfirm"
      :message="`Delete profile '${deleteTarget?.name}'? This cannot be undone.`"
      confirmLabel="Delete" :danger="true" @confirm="doDelete" />
  </div>
</template>

<style scoped>
@reference "tailwindcss";
.btn-primary { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
.btn-secondary { @apply px-4 py-2 bg-white border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50; }
.input { @apply border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none; }
</style>
