import client from './client'

const base = (dirId) => `/directories/${dirId}/drift`

// Rules
export const listRules = (dirId) =>
  client.get(`${base(dirId)}/rules`)

export const createRule = (dirId, data) =>
  client.post(`${base(dirId)}/rules`, data)

export const updateRule = (dirId, ruleId, data) =>
  client.put(`${base(dirId)}/rules/${ruleId}`, data)

export const deleteRule = (dirId, ruleId) =>
  client.delete(`${base(dirId)}/rules/${ruleId}`)

// Analysis
export const runAnalysis = (dirId) =>
  client.post(`${base(dirId)}/analyze`)

// Findings
export const listFindings = (dirId, params) =>
  client.get(`${base(dirId)}/findings`, { params })

export const getFindingsSummary = (dirId) =>
  client.get(`${base(dirId)}/findings/summary`)

export const acknowledgeFinding = (dirId, findingId) =>
  client.post(`${base(dirId)}/findings/${findingId}/acknowledge`)

export const exemptFinding = (dirId, findingId, data) =>
  client.post(`${base(dirId)}/findings/${findingId}/exempt`, data)

// Snapshots
export const listSnapshots = (dirId) =>
  client.get(`${base(dirId)}/snapshots`)

// Visualization
export const getDriftVisualization = (dirId) =>
  client.get(`${base(dirId)}/visualization`)
