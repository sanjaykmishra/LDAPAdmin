import client from './client'

export const getAuditLog = (dirId, params) =>
  client.get(`/directories/${dirId}/audit`, { params })

export const getSuperadminAuditLog = (params) =>
  client.get('/superadmin/audit', { params })

