import client from './client'

export const getSettings = () =>
  client.get('/settings')

export const updateSettings = (data) =>
  client.put('/settings', data)

/** Public (no auth required) — returns only branding fields. */
export const getBranding = () =>
  client.get('/settings/branding')

export const testSiem = () =>
  client.post('/settings/siem/test')
