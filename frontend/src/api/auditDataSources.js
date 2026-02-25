import client from './client'

const base = (tenantId) => `/superadmin/tenants/${tenantId}/audit-sources`

export const listAuditSources   = (tenantId)        => client.get(base(tenantId))
export const createAuditSource  = (tenantId, data)  => client.post(base(tenantId), data)
export const getAuditSource     = (tenantId, id)    => client.get(`${base(tenantId)}/${id}`)
export const updateAuditSource  = (tenantId, id, data) => client.put(`${base(tenantId)}/${id}`, data)
export const deleteAuditSource  = (tenantId, id)    => client.delete(`${base(tenantId)}/${id}`)
