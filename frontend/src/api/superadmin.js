import client from './client'

// ── Superadmins ───────────────────────────────────────────────────────────
export const listSuperadmins = () =>
  client.get('/superadmin/superadmins')

export const createSuperadmin = (data) =>
  client.post('/superadmin/superadmins', data)

export const deleteSuperadmin = (id) =>
  client.delete(`/superadmin/superadmins/${id}`)

// ── Tenants ───────────────────────────────────────────────────────────────
export const listTenants = () =>
  client.get('/superadmin/tenants')

export const getTenant = (id) =>
  client.get(`/superadmin/tenants/${id}`)

export const createTenant = (data) =>
  client.post('/superadmin/tenants', data)

export const updateTenant = (id, data) =>
  client.put(`/superadmin/tenants/${id}`, data)

export const deleteTenant = (id) =>
  client.delete(`/superadmin/tenants/${id}`)

// ── Tenant admins ─────────────────────────────────────────────────────────
export const listAdmins = (tenantId) =>
  client.get(`/admin/tenants/${tenantId}/admins`)

export const createAdmin = (tenantId, data) =>
  client.post(`/admin/tenants/${tenantId}/admins`, data)

export const updateAdmin = (tenantId, adminId, data) =>
  client.put(`/admin/tenants/${tenantId}/admins/${adminId}`, data)

export const deleteAdmin = (tenantId, adminId) =>
  client.delete(`/admin/tenants/${tenantId}/admins/${adminId}`)

// ── Tenant auth config ─────────────────────────────────────────────────────
export const getTenantAuthConfig = (id) =>
  client.get(`/superadmin/tenants/${id}/auth-config`)

export const updateTenantAuthConfig = (id, data) =>
  client.put(`/superadmin/tenants/${id}/auth-config`, data)

// ── Tenant directories ────────────────────────────────────────────────────
export const listTenantDirectories  = (tenantId)        => client.get(`/superadmin/tenants/${tenantId}/directories`)
export const createTenantDirectory  = (tenantId, data)  => client.post(`/superadmin/tenants/${tenantId}/directories`, data)
export const updateTenantDirectory  = (tenantId, id, data) => client.put(`/superadmin/tenants/${tenantId}/directories/${id}`, data)
export const deleteTenantDirectory  = (tenantId, id)    => client.delete(`/superadmin/tenants/${tenantId}/directories/${id}`)
export const testTenantConnection   = (tenantId, data)  => client.post(`/superadmin/tenants/${tenantId}/directories/test`, data)
export const evictTenantPool        = (tenantId, id)    => client.post(`/superadmin/tenants/${tenantId}/directories/${id}/evict-pool`)
