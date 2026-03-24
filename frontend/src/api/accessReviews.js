import client from './client'

const base = (dirId) => `/directories/${dirId}/access-reviews`

export const listCampaigns = (dirId, params) =>
  client.get(base(dirId), { params })

export const createCampaign = (dirId, data) =>
  client.post(base(dirId), data)

export const getCampaign = (dirId, campaignId) =>
  client.get(`${base(dirId)}/${campaignId}`)

export const activateCampaign = (dirId, campaignId) =>
  client.post(`${base(dirId)}/${campaignId}/activate`)

export const closeCampaign = (dirId, campaignId, force = false) =>
  client.post(`${base(dirId)}/${campaignId}/close`, null, { params: { force } })

export const cancelCampaign = (dirId, campaignId) =>
  client.post(`${base(dirId)}/${campaignId}/cancel`)

export const listReviewGroups = (dirId, campaignId) =>
  client.get(`${base(dirId)}/${campaignId}/groups`)

export const listDecisions = (dirId, campaignId, groupId) =>
  client.get(`${base(dirId)}/${campaignId}/groups/${groupId}/decisions`)

export const submitDecision = (dirId, campaignId, groupId, decisionId, data) =>
  client.post(`${base(dirId)}/${campaignId}/groups/${groupId}/decisions/${decisionId}`, data)

export const bulkDecide = (dirId, campaignId, groupId, items) =>
  client.post(`${base(dirId)}/${campaignId}/groups/${groupId}/decisions/bulk`, { items })

export const exportCampaign = (dirId, campaignId, format = 'csv') =>
  client.get(`${base(dirId)}/${campaignId}/export`, { params: { format }, responseType: 'blob' })

export const getCampaignHistory = (dirId, campaignId) =>
  client.get(`${base(dirId)}/${campaignId}/history`)

export const listCampaignReminders = (dirId, campaignId) =>
  client.get(`${base(dirId)}/${campaignId}/reminders`)

export const listReviewers = (dirId) =>
  client.get(`${base(dirId)}/reviewers`)
