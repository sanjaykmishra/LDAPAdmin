import client from './client'

const base = (tenantId, adminId) =>
  `/superadmin/tenants/${tenantId}/admins/${adminId}/permissions`

export const getPermissions = (tenantId, adminId) =>
  client.get(base(tenantId, adminId))

export const setDirectoryRole = (tenantId, adminId, data) =>
  client.put(`${base(tenantId, adminId)}/directory-roles`, data)

export const removeDirectoryRole = (tenantId, adminId, directoryId) =>
  client.delete(`${base(tenantId, adminId)}/directory-roles/${directoryId}`)

export const setBranchRestrictions = (tenantId, adminId, data) =>
  client.put(`${base(tenantId, adminId)}/branch-restrictions`, data)

export const setFeaturePermissions = (tenantId, adminId, features) =>
  client.put(`${base(tenantId, adminId)}/features`, features)

export const clearFeaturePermission = (tenantId, adminId, featureKey) =>
  client.delete(`${base(tenantId, adminId)}/features/${featureKey}`)
