<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useNotificationStore } from '@/stores/notifications'
import {
  listAllProfiles, createProfile, updateProfile, deleteProfile, cloneProfile,
  getLifecyclePolicy, setLifecyclePolicy, deleteLifecyclePolicy,
  getApprovalConfig, setApprovalConfig, getApprovers, setApprovers,
  evaluateGroupChanges, applyGroupChanges
} from '@/api/profiles'
import { listDirectories } from '@/api/directories'
import { listObjectClasses, getObjectClass } from '@/api/schema'
import { listAdmins } from '@/api/adminManagement'
import AppModal from '@/components/AppModal.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FormLayoutDesigner from '@/components/FormLayoutDesigner.vue'
import DnPicker from '@/components/DnPicker.vue'
import GroupDnPicker from '@/components/GroupDnPicker.vue'
import DataTable from '@/components/DataTable.vue'

const profileCols = [
  { key: 'name', label: 'Name' },
  { key: 'directoryName', label: 'Directory' },
  { key: 'targetOuDn', label: 'Target OU' },
  { key: 'objectClassNames', label: 'Object Classes' },
  { key: 'enabled', label: 'Status' },
]

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

// Group change preview dialog
const showGroupChangeDialog = ref(false)
const groupChangePreview = ref(null)
const applyingGroupChanges = ref(false)

// Approval form
const approval = ref(emptyApproval())
const profileApprovers = ref([])

function emptyProfile() {
  return {
    name: '', description: '', targetOuDn: '',
    objectClassNames: [], rdnAttribute: '',
    showDnField: true, enabled: true, selfRegistrationAllowed: false,
    passwordLength: 16, passwordUppercase: true, passwordLowercase: true,
    passwordDigits: true, passwordSpecial: true, passwordSpecialChars: '!@#$%^&*',
    emailPasswordToUser: false,
    autoIncludeGroups: false, excludeAutoIncludes: false,
    additionalProfileIds: [],
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
  layoutMode.value = 'admin'
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
    passwordLength: p.passwordLength ?? 16,
    passwordUppercase: p.passwordUppercase ?? true,
    passwordLowercase: p.passwordLowercase ?? true,
    passwordDigits: p.passwordDigits ?? true,
    passwordSpecial: p.passwordSpecial ?? true,
    passwordSpecialChars: p.passwordSpecialChars ?? '!@#$%^&*',
    emailPasswordToUser: p.emailPasswordToUser ?? false,
    autoIncludeGroups: p.autoIncludeGroups ?? false,
    excludeAutoIncludes: p.excludeAutoIncludes ?? false,
    additionalProfileIds: (p.additionalProfiles || []).map(ap => ap.id),
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
      registrationSectionName: a.registrationSectionName ?? null,
      registrationColumnSpan: a.registrationColumnSpan ?? null, registrationDisplayOrder: a.registrationDisplayOrder ?? null,
      selfServiceSectionName: a.selfServiceSectionName ?? null,
      selfServiceColumnSpan: a.selfServiceColumnSpan ?? null, selfServiceDisplayOrder: a.selfServiceDisplayOrder ?? null
    })),
    groupAssignments: p.groupAssignments.map(g => ({
      groupDn: g.groupDn, memberAttribute: g.memberAttribute
    }))
  }
  modalTab.value = 'general'

  // Load schema data for existing object classes (for RDN picker and required tracking)
  schemaRequiredAttrs.value = new Set()
  ocSchemaCache.value = {}
  for (const ocName of p.objectClassNames) {
    try {
      const { data } = await getObjectClass(selectedDirId.value, ocName)
      const required = data.requiredAttributes || data.required || []
      const optional = data.optionalAttributes || data.optional || []
      ocSchemaCache.value[ocName] = { required: [...required], optional: [...optional] }
      for (const attr of required) schemaRequiredAttrs.value.add(attr.toLowerCase())
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
      // Check if group membership changes are needed for existing users
      const savedProfileId = editing.value
      showModal.value = false
      await reload()
      // Use saved ID since editing.value gets cleared
      editing.value = savedProfileId
      await handleGroupChangePreview()
      editing.value = null
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
      // If this new profile is auto-include, check impact on other profiles
      if (profile.value.autoIncludeGroups && profile.value.groupAssignments.length > 0) {
        showModal.value = false
        await reload()
        editing.value = data.id
        await handleGroupChangePreview()
        editing.value = null
      } else {
        showModal.value = false
        await reload()
      }
    }
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

// Additional profiles: profiles from the same directory that can be stacked
const availableAdditionalProfiles = computed(() => {
  if (!selectedDirId.value) return []
  return profiles.value
    .filter(p => p.directoryId === selectedDirId.value
      && p.id !== editing.value
      && !p.autoIncludeGroups) // auto-include profiles are implicit, not selectable
    .map(p => ({ id: p.id, name: p.name }))
    .sort((a, b) => a.name.localeCompare(b.name))
})

// Auto-included profiles (read-only display)
const autoIncludedProfiles = computed(() => {
  if (!selectedDirId.value) return []
  return profiles.value
    .filter(p => p.directoryId === selectedDirId.value
      && p.id !== editing.value
      && p.autoIncludeGroups)
    .map(p => ({ id: p.id, name: p.name }))
})

// Effective groups shown in the response
const effectiveGroups = computed(() => {
  // Find the current profile in the profiles list
  const current = profiles.value.find(p => p.id === editing.value)
  return current?.effectiveGroupAssignments || []
})

function toggleAdditionalProfile(profileId) {
  const ids = profile.value.additionalProfileIds
  const idx = ids.indexOf(profileId)
  if (idx >= 0) ids.splice(idx, 1)
  else ids.push(profileId)
}

async function handleGroupChangePreview() {
  if (!editing.value) return
  try {
    const { data } = await evaluateGroupChanges(selectedDirId.value, editing.value)
    if (data.changes && data.changes.length > 0) {
      groupChangePreview.value = data
      showGroupChangeDialog.value = true
    }
  } catch (e) {
    // Non-critical — just means we can't preview
    console.warn('Group change evaluation failed:', e)
  }
}

async function confirmApplyGroupChanges() {
  applyingGroupChanges.value = true
  try {
    await applyGroupChanges(selectedDirId.value, editing.value)
    notif.success('Group memberships updated')
    showGroupChangeDialog.value = false
    groupChangePreview.value = null
  } catch (e) {
    notif.error(e.response?.data?.detail || e.response?.data?.message || e.message)
  } finally {
    applyingGroupChanges.value = false
  }
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

// Human-readable labels for well-known LDAP attributes
const ATTR_LABELS = {
  cn: 'Common Name', sn: 'Last Name', givenname: 'First Name',
  displayname: 'Display Name', mail: 'Email', uid: 'User ID',
  telephonenumber: 'Phone', mobile: 'Mobile', facsimiletelephonenumber: 'Fax',
  homephone: 'Home Phone', pager: 'Pager',
  street: 'Street Address', l: 'City', st: 'State/Province',
  postalcode: 'Postal Code', postaladdress: 'Postal Address', co: 'Country',
  title: 'Job Title', description: 'Description', o: 'Organization',
  ou: 'Organizational Unit', dc: 'Domain Component',
  preferredlanguage: 'Preferred Language', labeleduri: 'URL',
  jpegphoto: 'Photo', userpassword: 'Password',
  employeenumber: 'Employee Number', employeetype: 'Employee Type',
  departmentnumber: 'Department Number', roomnumber: 'Room Number',
  manager: 'Manager', secretary: 'Secretary',
  initials: 'Initials', c: 'Country Code',
}

function guessLabel(attrName) {
  const known = ATTR_LABELS[attrName.toLowerCase()]
  if (known) return known
  // Split camelCase / snake_case into words and title-case them
  return attrName
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/[_-]/g, ' ')
    .replace(/\b\w/g, c => c.toUpperCase())
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
    for (const attr of required) schemaRequiredAttrs.value.add(attr.toLowerCase())
    ocSchemaCache.value[ocToAdd.value] = { required: [...required], optional: [...optional] }
    // Auto-add only schema-required attributes; optional ones can be added via the picker
    for (const attr of required) {
      if (!profile.value.attributeConfigs.find(a => a.attributeName.toLowerCase() === attr.toLowerCase())) {
        const isObjClass = attr.toLowerCase() === 'objectclass'
        profile.value.attributeConfigs.push({
          attributeName: attr, customLabel: isObjClass ? '' : guessLabel(attr), inputType: isObjClass ? 'HIDDEN_FIXED' : 'TEXT',
          requiredOnCreate: true, editableOnCreate: !isObjClass,
          editableOnUpdate: !isObjClass, selfServiceEdit: !isObjClass && isSelfServiceEditable(attr),
          selfRegistrationEdit: !isObjClass && isSelfServiceEditable(attr),
          defaultValue: '', computedExpression: '', validationRegex: '',
          validationMessage: '', allowedValues: '', minLength: null,
          maxLength: null, sectionName: '', columnSpan: 6, hidden: isObjClass,
          registrationSectionName: null, registrationColumnSpan: null, registrationDisplayOrder: null,
          selfServiceSectionName: null, selfServiceColumnSpan: null, selfServiceDisplayOrder: null
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
      for (const attr of cached.required) schemaRequiredAttrs.value.add(attr.toLowerCase())
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
  return schemaRequiredAttrs.value.has(attr.attributeName.toLowerCase())
}

// Helper: check if an attribute can be removed (required attributes cannot be removed)
function canRemoveAttribute(attr) {
  return !isRdnAttribute(attr) && !isSchemaRequired(attr) && !attr.requiredOnCreate
}

// Available attributes from selected object classes that haven't been added yet
const showAttrPicker = ref(false)
const attrPickerSelection = ref([])

const availableAttributes = computed(() => {
  const added = new Set(profile.value.attributeConfigs.map(a => a.attributeName.toLowerCase()))
  const attrs = []
  for (const ocName of profile.value.objectClassNames) {
    const cached = ocSchemaCache.value[ocName]
    if (!cached) continue
    for (const attr of [...cached.required, ...cached.optional]) {
      if (attr.toLowerCase() !== 'objectclass' && !added.has(attr.toLowerCase())) {
        attrs.push(attr)
        added.add(attr.toLowerCase()) // dedupe across OCs
      }
    }
  }
  return attrs.sort()
})

function toggleAttrPickerSelection(attr) {
  const idx = attrPickerSelection.value.indexOf(attr)
  if (idx >= 0) attrPickerSelection.value.splice(idx, 1)
  else attrPickerSelection.value.push(attr)
}

function toggleAttrPicker() {
  attrPickerSelection.value = []
  showAttrPicker.value = !showAttrPicker.value
}

function addSelectedAttributes() {
  for (const name of attrPickerSelection.value) {
    profile.value.attributeConfigs.push({
      attributeName: name, customLabel: guessLabel(name), inputType: 'TEXT',
      requiredOnCreate: schemaRequiredAttrs.value.has(name.toLowerCase()), editableOnCreate: true,
      editableOnUpdate: true, selfServiceEdit: false,
      selfRegistrationEdit: false,
      defaultValue: '', computedExpression: '', validationRegex: '',
      validationMessage: '', allowedValues: '', minLength: null,
      maxLength: null, sectionName: '', columnSpan: 6, hidden: false,
      registrationSectionName: null, registrationColumnSpan: null, registrationDisplayOrder: null,
      selfServiceSectionName: null, selfServiceColumnSpan: null, selfServiceDisplayOrder: null
    })
  }
  attrPickerSelection.value = []
  showAttrPicker.value = false
}

// When emailPasswordToUser is enabled, ensure 'mail' is present and required
watch(() => profile.value.emailPasswordToUser, (enabled) => {
  if (!enabled) return
  const existing = profile.value.attributeConfigs.find(
    a => a.attributeName.toLowerCase() === 'mail'
  )
  if (existing) {
    existing.requiredOnCreate = true
    existing.hidden = false
  } else {
    profile.value.attributeConfigs.push({
      attributeName: 'mail', customLabel: 'Email', inputType: 'TEXT',
      requiredOnCreate: true, editableOnCreate: true,
      editableOnUpdate: true, selfServiceEdit: true,
      selfRegistrationEdit: true,
      defaultValue: '', computedExpression: '', validationRegex: '',
      validationMessage: '', allowedValues: '', minLength: null,
      maxLength: null, sectionName: '', columnSpan: 6, hidden: false,
      registrationSectionName: null, registrationColumnSpan: null, registrationDisplayOrder: null,
      selfServiceSectionName: null, selfServiceColumnSpan: null, selfServiceDisplayOrder: null
    })
  }
})

// When requiredOnCreate is set, ensure hidden is cleared (unless attribute has a computed expression)
watch(() => profile.value.attributeConfigs.map(a => a.requiredOnCreate), () => {
  for (const attr of profile.value.attributeConfigs) {
    if (attr.requiredOnCreate && attr.hidden && !attr.computedExpression) attr.hidden = false
  }
})

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

// Registration layout: self-registration-enabled fields, defaulting to admin layout values.
const registrationAttributeConfigs = computed({
  get() {
    return profile.value.attributeConfigs
      .filter(a => a.selfRegistrationEdit && !a.hidden && a.inputType !== 'HIDDEN_FIXED')
      .map(a => ({
        ...a,
        rdn: a.attributeName === profile.value.rdnAttribute,
        sectionName: a.registrationSectionName ?? a.sectionName ?? '',
        columnSpan: a.registrationColumnSpan ?? a.columnSpan ?? 6,
      }))
  },
  set(val) {
    const lookup = new Map(val.map((v, i) => [v.attributeName, { ...v, registrationDisplayOrder: i }]))
    profile.value.attributeConfigs = profile.value.attributeConfigs.map(a => {
      const updated = lookup.get(a.attributeName)
      if (updated) {
        return {
          ...a,
          registrationSectionName: updated.sectionName ?? '',
          registrationColumnSpan: updated.columnSpan ?? 6,
          registrationDisplayOrder: updated.registrationDisplayOrder,
        }
      }
      return a
    })
  }
})

// Self-service layout: self-service-editable fields, defaulting to admin layout values.
const selfServiceAttributeConfigs = computed({
  get() {
    return profile.value.attributeConfigs
      .filter(a => a.selfServiceEdit && !a.hidden && a.inputType !== 'HIDDEN_FIXED')
      .map(a => ({
        ...a,
        rdn: a.attributeName === profile.value.rdnAttribute,
        sectionName: a.selfServiceSectionName ?? a.sectionName ?? '',
        columnSpan: a.selfServiceColumnSpan ?? a.columnSpan ?? 6,
      }))
  },
  set(val) {
    const lookup = new Map(val.map((v, i) => [v.attributeName, { ...v, selfServiceDisplayOrder: i }]))
    profile.value.attributeConfigs = profile.value.attributeConfigs.map(a => {
      const updated = lookup.get(a.attributeName)
      if (updated) {
        return {
          ...a,
          selfServiceSectionName: updated.sectionName ?? '',
          selfServiceColumnSpan: updated.columnSpan ?? 6,
          selfServiceDisplayOrder: updated.selfServiceDisplayOrder,
        }
      }
      return a
    })
  }
})

const layoutMode = ref('admin')

// Reset layout mode if self-registration is turned off while viewing that layout
watch(() => profile.value.selfRegistrationAllowed, (allowed) => {
  if (!allowed && layoutMode.value === 'registration') layoutMode.value = 'admin'
})

// Fixed modal height based on attribute count so switching tabs doesn't resize
const modalHeight = computed(() => {
  const count = profile.value.attributeConfigs.length
  if (count <= 6) return '50vh'
  if (count <= 12) return '60vh'
  return '70vh'
})

const modalTabs = [
  { id: 'general', label: 'General' },
  { id: 'attributes', label: 'Attributes' },
  { id: 'layout', label: 'Layout' },
  { id: 'groups', label: 'Groups' },
  { id: 'lifecycle', label: 'Lifecycle' },
]

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

    <DataTable :columns="profileCols" :rows="profiles" :loading="loading" row-key="id" empty-text="No provisioning profiles configured.">
      <template #cell-name="{ row }">
        <span class="font-medium">{{ row.name }}</span>
      </template>
      <template #cell-targetOuDn="{ value }">
        <span class="font-mono text-xs text-gray-600">{{ value }}</span>
      </template>
      <template #cell-objectClassNames="{ value }">
        <span class="text-xs text-gray-600">{{ value.join(', ') }}</span>
      </template>
      <template #cell-enabled="{ value }">
        <span :class="value ? 'text-green-600' : 'text-gray-400'">{{ value ? 'Enabled' : 'Disabled' }}</span>
      </template>
      <template #actions="{ row }">
        <div class="flex gap-3 justify-end whitespace-nowrap">
          <button class="text-blue-600 hover:text-blue-800 text-xs font-medium" @click="openEdit(row)">Edit</button>
          <button class="text-blue-600 hover:text-blue-800 text-xs font-medium" @click="doClone(row)">Clone</button>
          <button class="text-red-500 hover:text-red-700 text-xs font-medium" @click="confirmDelete(row)">Delete</button>
        </div>
      </template>
    </DataTable>

    <!-- Create/Edit Modal -->
    <AppModal v-model="showModal" :title="editing ? 'Edit Profile' : 'Create Profile'" size="xl" :fixedHeight="modalHeight">
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

          <!-- Password Generation Settings -->
          <fieldset class="border rounded-lg p-3 space-y-3">
            <legend class="text-sm font-semibold text-gray-800 px-1">Password Generation</legend>
            <div class="grid grid-cols-6 gap-3">
              <div class="col-span-2">
                <label class="block text-xs text-gray-500 mb-1">Length</label>
                <input type="number" v-model.number="profile.passwordLength" min="8" max="128"
                  class="input w-full text-sm" />
              </div>
              <div class="col-span-4 flex flex-wrap gap-4 items-end pb-1">
                <label class="flex items-center gap-1 text-sm">
                  <input type="checkbox" v-model="profile.passwordUppercase" /> A-Z
                </label>
                <label class="flex items-center gap-1 text-sm">
                  <input type="checkbox" v-model="profile.passwordLowercase" /> a-z
                </label>
                <label class="flex items-center gap-1 text-sm">
                  <input type="checkbox" v-model="profile.passwordDigits" /> 0-9
                </label>
                <label class="flex items-center gap-1 text-sm">
                  <input type="checkbox" v-model="profile.passwordSpecial" /> Special
                </label>
              </div>
            </div>
            <div v-if="profile.passwordSpecial">
              <label class="block text-xs text-gray-500 mb-1">Special Characters</label>
              <input v-model="profile.passwordSpecialChars" class="input w-full text-sm font-mono"
                placeholder="!@#$%^&*" />
            </div>
            <label class="flex items-center gap-2 text-sm">
              <input type="checkbox" v-model="profile.emailPasswordToUser" />
              Email generated password to user on creation
            </label>
          </fieldset>

          <!-- Group inclusion settings -->
          <fieldset class="border rounded-lg p-4 space-y-2">
            <legend class="text-sm font-semibold text-gray-700 px-1">Group Inclusion</legend>
            <label class="flex items-center gap-2 text-sm">
              <input type="checkbox" v-model="profile.autoIncludeGroups" />
              Automatically include with other profiles
              <span class="text-gray-400 text-xs">(this profile's groups will be added to users provisioned by any other profile in this directory)</span>
            </label>
            <label class="flex items-center gap-2 text-sm">
              <input type="checkbox" v-model="profile.excludeAutoIncludes" />
              Exclude auto-included groups
              <span class="text-gray-400 text-xs">(users provisioned by this profile will not receive groups from auto-included profiles)</span>
            </label>
          </fieldset>
        </div>

        <!-- Attributes Tab -->
        <div v-if="modalTab === 'attributes'" class="space-y-3">
          <div>
            <button class="btn-secondary text-sm" :disabled="availableAttributes.length === 0" @click="toggleAttrPicker">
              {{ showAttrPicker ? 'Cancel' : 'Add Attributes' }}
            </button>
            <div v-if="showAttrPicker" class="mt-2 border rounded-lg p-3 space-y-2 bg-gray-50">
              <div v-if="availableAttributes.length === 0" class="text-gray-500 text-sm">
                All attributes from the selected object classes have been added.
              </div>
              <template v-else>
                <div class="text-xs text-gray-500 mb-1">Select attributes to add:</div>
                <div class="max-h-48 overflow-y-auto space-y-1">
                  <label v-for="attr in availableAttributes" :key="attr"
                    class="flex items-center gap-2 text-sm p-1 hover:bg-white rounded cursor-pointer">
                    <input type="checkbox"
                      :checked="attrPickerSelection.includes(attr)"
                      @change="toggleAttrPickerSelection(attr)" />
                    <span class="font-mono text-xs">{{ attr }}</span>
                  </label>
                </div>
                <button class="btn-primary text-sm mt-2" :disabled="attrPickerSelection.length === 0" @click="addSelectedAttributes">
                  Add {{ attrPickerSelection.length }} attribute{{ attrPickerSelection.length !== 1 ? 's' : '' }}
                </button>
              </template>
            </div>
          </div>
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
              <label v-if="profile.selfRegistrationAllowed" class="flex items-center gap-1"><input type="checkbox" v-model="attr.selfRegistrationEdit" /> Self-registration</label>
              <label class="flex items-center gap-1">
                <input type="checkbox" v-model="attr.hidden"
                  :disabled="(attr.requiredOnCreate && !attr.computedExpression) || isRdnAttribute(attr) || isSchemaRequired(attr)" /> Hidden
              </label>
            </div>
          </div>
        </div>

        <!-- Layout Tab -->
        <div v-if="modalTab === 'layout'" class="space-y-3">
          <!-- Segmented control -->
          <div class="inline-flex rounded-md border border-gray-300 text-sm">
            <button v-for="mode in [
              { id: 'admin', label: 'Admin' },
              { id: 'self-service', label: 'Self-service' },
              ...(profile.selfRegistrationAllowed ? [{ id: 'registration', label: 'Self-registration' }] : [])
            ]" :key="mode.id"
              :class="['px-4 py-1.5 font-medium transition-colors first:rounded-l-md last:rounded-r-md',
                layoutMode === mode.id
                  ? 'bg-blue-600 text-white border-blue-600'
                  : 'text-gray-600 hover:bg-gray-50']"
              @click="layoutMode = mode.id">
              {{ mode.label }}
            </button>
          </div>

          <!-- Admin layout -->
          <FormLayoutDesigner
            v-if="layoutMode === 'admin'"
            v-model:attributeConfigs="layoutAttributeConfigs"
            v-model:showDnField="profile.showDnField"
          />

          <!-- Self-service layout -->
          <template v-else-if="layoutMode === 'self-service'">
            <div v-if="selfServiceAttributeConfigs.length === 0" class="text-gray-500 text-sm py-4">
              No self-service attributes configured. Mark attributes as "Self-service" on the Attributes tab to include them here.
            </div>
            <FormLayoutDesigner
              v-else
              v-model:attributeConfigs="selfServiceAttributeConfigs"
              :showDnField="false"
              :hideDnToggle="true"
            />
          </template>

          <!-- Self-registration layout -->
          <template v-else-if="layoutMode === 'registration'">
            <div v-if="registrationAttributeConfigs.length === 0" class="text-gray-500 text-sm py-4">
              No self-registration attributes configured. Mark attributes as "Self-registration" on the Attributes tab to include them here.
            </div>
            <FormLayoutDesigner
              v-else
              v-model:attributeConfigs="registrationAttributeConfigs"
              :showDnField="false"
              :hideDnToggle="true"
            />
          </template>
        </div>

        <!-- Groups Tab -->
        <div v-if="modalTab === 'groups'" class="space-y-5">
          <!-- Own group assignments -->
          <fieldset class="border rounded-lg p-4 space-y-3">
            <legend class="text-sm font-semibold text-gray-700 px-1">Own Group Assignments</legend>
            <p class="text-sm text-gray-600">Groups users will be automatically added to on creation.</p>
            <div v-for="(g, i) in profile.groupAssignments" :key="i" class="flex gap-2 items-end">
              <div class="flex-1">
                <label class="block text-xs text-gray-500">Group DN</label>
                <GroupDnPicker v-model="g.groupDn" :directory-id="selectedDirId" />
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
          </fieldset>

          <!-- Additional profiles -->
          <fieldset class="border rounded-lg p-4 space-y-3">
            <legend class="text-sm font-semibold text-gray-700 px-1">Additional Profiles</legend>
            <p class="text-sm text-gray-600">Select other profiles whose group assignments should also be applied to users provisioned with this profile.</p>
            <div v-if="availableAdditionalProfiles.length === 0" class="text-sm text-gray-400 italic">
              No other profiles available in this directory.
            </div>
            <div v-else class="flex flex-wrap gap-2">
              <label v-for="ap in availableAdditionalProfiles" :key="ap.id"
                class="flex items-center gap-1.5 text-sm border rounded px-3 py-1.5 cursor-pointer"
                :class="profile.additionalProfileIds.includes(ap.id) ? 'bg-blue-50 border-blue-300' : 'bg-white border-gray-200 hover:border-gray-300'">
                <input type="checkbox" :checked="profile.additionalProfileIds.includes(ap.id)"
                  @change="toggleAdditionalProfile(ap.id)" class="accent-blue-600" />
                {{ ap.name }}
              </label>
            </div>
          </fieldset>

          <!-- Auto-included profiles (read-only) -->
          <fieldset v-if="autoIncludedProfiles.length > 0 && !profile.excludeAutoIncludes" class="border rounded-lg p-4 space-y-2">
            <legend class="text-sm font-semibold text-gray-700 px-1">Auto-included Profiles</legend>
            <p class="text-sm text-gray-500">These profiles have "Automatically include with other profiles" enabled and their groups are included automatically.</p>
            <div class="flex flex-wrap gap-2">
              <span v-for="ap in autoIncludedProfiles" :key="ap.id"
                class="inline-flex items-center text-sm bg-green-50 border border-green-200 rounded px-3 py-1">
                {{ ap.name }}
              </span>
            </div>
          </fieldset>

          <!-- Effective groups summary -->
          <fieldset v-if="editing && effectiveGroups.length > 0" class="border rounded-lg p-4 space-y-2">
            <legend class="text-sm font-semibold text-gray-700 px-1">Effective Group Set</legend>
            <p class="text-sm text-gray-500">The combined set of groups that will be assigned on provisioning (own + additional + auto-included).</p>
            <div class="space-y-1">
              <div v-for="g in effectiveGroups" :key="g.groupDn" class="text-sm font-mono text-gray-700 bg-gray-50 px-2 py-1 rounded">
                {{ g.groupDn }} <span class="text-gray-400">({{ g.memberAttribute }})</span>
              </div>
            </div>
          </fieldset>
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

          <!-- Approval -->
          <div class="border-t border-gray-200 pt-4 mt-2 space-y-4">
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

    <!-- Group change preview dialog -->
    <AppModal v-model="showGroupChangeDialog" title="Group Membership Changes" size="lg">
      <template v-if="groupChangePreview">
        <p class="text-sm text-gray-600 mb-3">
          The following group membership changes are needed for <strong>{{ groupChangePreview.totalUsersAffected }}</strong> existing user(s):
        </p>
        <div class="max-h-80 overflow-y-auto space-y-3">
          <div v-for="change in groupChangePreview.changes" :key="change.userDn"
            class="border rounded-lg p-3 text-sm">
            <div class="font-mono text-gray-800 mb-1">{{ change.userDn }}</div>
            <div v-for="g in change.groupsToAdd" :key="g.groupDn" class="text-green-700 ml-4">
              + Add to {{ g.groupDn }}
            </div>
            <div v-for="g in change.groupsToRemove" :key="g.groupDn" class="text-red-700 ml-4">
              - Remove from {{ g.groupDn }}
            </div>
          </div>
        </div>
        <div class="flex justify-end gap-2 mt-4">
          <button class="btn-secondary" @click="showGroupChangeDialog = false; groupChangePreview = null">Skip</button>
          <button class="btn-primary" :disabled="applyingGroupChanges" @click="confirmApplyGroupChanges">
            {{ applyingGroupChanges ? 'Applying...' : 'Apply Changes' }}
          </button>
        </div>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
@reference "tailwindcss";
.btn-primary { @apply px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50; }
.btn-secondary { @apply px-4 py-2 bg-white border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed; }
.input { @apply border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none; }
</style>
