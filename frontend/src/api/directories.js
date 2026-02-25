import client from './client'

const base = '/admin/tenants'

// ── Directory connections scoped to a tenant ──────────────────────────────
export const listDirectories = tenantId =>
  client.get(`${base}/${tenantId}/directories`)

export const getDirectory = (tenantId, dirId) =>
  client.get(`${base}/${tenantId}/directories/${dirId}`)

export const createDirectory = (tenantId, data) =>
  client.post(`${base}/${tenantId}/directories`, data)

export const updateDirectory = (tenantId, dirId, data) =>
  client.put(`${base}/${tenantId}/directories/${dirId}`, data)

export const deleteDirectory = (tenantId, dirId) =>
  client.delete(`${base}/${tenantId}/directories/${dirId}`)

export const testDirectory = (tenantId, dirId) =>
  client.post(`${base}/${tenantId}/directories/${dirId}/test`)
