import client from './client'

const base = (dirId) => `/directories/${dirId}/campaign-templates`

export const listTemplates = (dirId) =>
  client.get(base(dirId))

export const getTemplate = (dirId, templateId) =>
  client.get(`${base(dirId)}/${templateId}`)

export const createTemplate = (dirId, data) =>
  client.post(base(dirId), data)

export const updateTemplate = (dirId, templateId, data) =>
  client.put(`${base(dirId)}/${templateId}`, data)

export const deleteTemplate = (dirId, templateId) =>
  client.delete(`${base(dirId)}/${templateId}`)

export const createCampaignFromTemplate = (dirId, templateId, params = {}) =>
  client.post(`${base(dirId)}/${templateId}/create-campaign`, null, { params })

export const saveAsTemplate = (dirId, campaignId) =>
  client.post(`${base(dirId)}/from-campaign/${campaignId}`)
