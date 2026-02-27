import client from './client'

const base = (adminId) => `/superadmin/admins/${adminId}/permissions`

export const getPermissions         = (adminId)             => client.get(base(adminId))
export const setRealmRole           = (adminId, data)       => client.put(`${base(adminId)}/realm-roles`, data)
export const removeRealmRole        = (adminId, realmId)    => client.delete(`${base(adminId)}/realm-roles/${realmId}`)
export const setBranchRestrictions  = (adminId, data)       => client.put(`${base(adminId)}/branch-restrictions`, data)
export const setFeaturePermissions  = (adminId, features)   => client.put(`${base(adminId)}/features`, features)
export const clearFeaturePermission = (adminId, featureKey) => client.delete(`${base(adminId)}/features/${featureKey}`)
