import client from './client'

const base = (dirId) => `/directories/${dirId}/access-reviews/cross-campaign-report`

export const getCrossCampaignReport = (dirId, params) =>
  client.get(base(dirId), { params })

export const exportCrossCampaignReport = (dirId, params) =>
  client.get(`${base(dirId)}/export`, { params, responseType: 'blob' })
