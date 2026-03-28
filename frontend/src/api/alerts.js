import client from './client'

export const listAlerts = (params) =>
    client.get('/superadmin/alerts', { params })

export const getAlertSummary = () =>
    client.get('/superadmin/alerts/summary')

export const acknowledgeAlert = (id) =>
    client.post(`/superadmin/alerts/${id}/acknowledge`)

export const dismissAlert = (id) =>
    client.post(`/superadmin/alerts/${id}/dismiss`)

export const resolveAlert = (id) =>
    client.post(`/superadmin/alerts/${id}/resolve`)

export const listAlertRules = (params) =>
    client.get('/superadmin/alerts/rules', { params })

export const updateAlertRule = (id, data) =>
    client.put(`/superadmin/alerts/rules/${id}`, data)

export const initializeAlertRules = (directoryId) =>
    client.post(`/superadmin/alerts/rules/initialize/${directoryId}`)
