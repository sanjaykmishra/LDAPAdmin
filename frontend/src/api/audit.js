import client from './client'

export const getAuditLog = (dirId, params) =>
  client.get(`/directories/${dirId}/audit`, { params })

export const getSuperadminAuditLog = (params) =>
  client.get('/superadmin/audit', { params })

export const getAuditDataSources = (dirId) =>
  client.get(`/directories/${dirId}/audit-sources`)

export const createAuditDataSource = (dirId, data) =>
  client.post(`/directories/${dirId}/audit-sources`, data)

export const deleteAuditDataSource = (dirId, sourceId) =>
  client.delete(`/directories/${dirId}/audit-sources/${sourceId}`)

export const syncAuditDataSource = (dirId, sourceId) =>
  client.post(`/directories/${dirId}/audit-sources/${sourceId}/sync`)
