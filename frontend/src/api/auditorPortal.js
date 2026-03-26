import axios from 'axios'

/**
 * Axios client for the public auditor portal API.
 * Does NOT use the main app's client (no JWT cookie, no 401 redirect).
 */
const portalClient = axios.create({
  baseURL: '/api/v1/auditor',
  headers: { 'Content-Type': 'application/json' },
})

const base = (token) => `/${token}`

export const getPortalMetadata = (token) =>
  portalClient.get(`${base(token)}`)

export const getPortalCampaigns = (token) =>
  portalClient.get(`${base(token)}/campaigns`)

export const getPortalCampaignDetail = (token, campaignId) =>
  portalClient.get(`${base(token)}/campaigns/${campaignId}`)

export const getPortalSod = (token) =>
  portalClient.get(`${base(token)}/sod`)

export const getPortalEntitlements = (token) =>
  portalClient.get(`${base(token)}/entitlements`)

export const getPortalAuditEvents = (token) =>
  portalClient.get(`${base(token)}/audit-events`)

export const getPortalApprovals = (token) =>
  portalClient.get(`${base(token)}/approvals`)

export const getPortalVerify = (token) =>
  portalClient.get(`${base(token)}/verify`)

export const getPortalExport = (token) =>
  portalClient.get(`${base(token)}/export`, { responseType: 'blob' })

// ── Per-section exports ─────────────────────────────────────────────────

export const exportCampaignCsv = (token, campaignId) =>
  portalClient.get(`${base(token)}/export/campaigns/${campaignId}/csv`, { responseType: 'blob' })

export const exportCampaignPdf = (token, campaignId) =>
  portalClient.get(`${base(token)}/export/campaigns/${campaignId}/pdf`, { responseType: 'blob' })

export const exportSodPdf = (token) =>
  portalClient.get(`${base(token)}/export/sod/pdf`, { responseType: 'blob' })

export const exportAuditEventsCsv = (token) =>
  portalClient.get(`${base(token)}/export/audit-events/csv`, { responseType: 'blob' })

export const exportAuditEventsPdf = (token) =>
  portalClient.get(`${base(token)}/export/audit-events/pdf`, { responseType: 'blob' })

export const exportWorkpaper = (token) =>
  portalClient.get(`${base(token)}/export/workpaper`, { responseType: 'blob' })
