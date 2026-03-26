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
