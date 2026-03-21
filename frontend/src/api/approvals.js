import client from './client'

export const listPendingApprovals = (dirId) =>
    client.get(`/directories/${dirId}/approvals`)

export const getApproval = (dirId, id) =>
    client.get(`/directories/${dirId}/approvals/${id}`)

export const approveRequest = (dirId, id) =>
    client.post(`/directories/${dirId}/approvals/${id}/approve`)

export const rejectRequest = (dirId, id, reason) =>
    client.post(`/directories/${dirId}/approvals/${id}/reject`, { reason })

export const countPendingApprovals = (dirId) =>
    client.get(`/directories/${dirId}/approvals/count`)

export const getRealmSettings = (dirId, realmId) =>
    client.get(`/directories/${dirId}/realms/${realmId}/settings`)

export const updateRealmSettings = (dirId, realmId, settings) =>
    client.put(`/directories/${dirId}/realms/${realmId}/settings`, { settings })

export const getRealmApprovers = (dirId, realmId) =>
    client.get(`/directories/${dirId}/realms/${realmId}/approvers`)

export const setRealmApprovers = (dirId, realmId, accountIds) =>
    client.put(`/directories/${dirId}/realms/${realmId}/approvers`, { accountIds })

export const getApprovalConfig = (dirId, realmId) =>
    client.get(`/directories/${dirId}/realms/${realmId}/approval-config`)
