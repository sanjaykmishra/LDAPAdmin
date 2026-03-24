import client from './client'

export const downloadUserAccessReport = (dirId, params = {}) =>
  client.get(`/directories/${dirId}/compliance-reports/user-access`, {
    params,
    responseType: 'blob',
  })

export const downloadAccessReviewSummary = (dirId, campaignId) =>
  client.get(`/directories/${dirId}/compliance-reports/access-review-summary/${campaignId}`, {
    responseType: 'blob',
  })

export const downloadPrivilegedAccountInventory = () =>
  client.get('/compliance-reports/privileged-accounts', {
    responseType: 'blob',
  })

export const generateEvidencePackage = (dirId, body) =>
  client.post(`/directories/${dirId}/evidence-package`, body, {
    responseType: 'blob',
    timeout: 120000,
  })
