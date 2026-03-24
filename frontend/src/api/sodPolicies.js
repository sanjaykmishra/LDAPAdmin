import client from './client'

const base = (dirId) => `/directories/${dirId}/sod-policies`

export const listPolicies = (dirId) =>
  client.get(base(dirId))

export const getPolicy = (dirId, policyId) =>
  client.get(`${base(dirId)}/${policyId}`)

export const createPolicy = (dirId, data) =>
  client.post(base(dirId), data)

export const updatePolicy = (dirId, policyId, data) =>
  client.put(`${base(dirId)}/${policyId}`, data)

export const deletePolicy = (dirId, policyId) =>
  client.delete(`${base(dirId)}/${policyId}`)

export const scanDirectory = (dirId) =>
  client.post(`${base(dirId)}/scan`)

export const listViolations = (dirId, params) =>
  client.get(`${base(dirId)}/violations`, { params })

export const exemptViolation = (dirId, violationId, data) =>
  client.post(`${base(dirId)}/violations/${violationId}/exempt`, data)
