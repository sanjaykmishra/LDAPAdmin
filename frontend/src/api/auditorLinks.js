import client from './client'

const base = (dirId) => `/directories/${dirId}/auditor-links`

export const listAuditorLinks = (dirId) =>
  client.get(base(dirId))

export const createAuditorLink = (dirId, data) =>
  client.post(base(dirId), data)

export const revokeAuditorLink = (dirId, linkId) =>
  client.delete(`${base(dirId)}/${linkId}`)
