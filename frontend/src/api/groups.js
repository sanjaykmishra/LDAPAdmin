import client from './client'

const base = (dirId) => `/directories/${dirId}/groups`

export const searchGroups = (dirId, params) =>
  client.get(base(dirId), { params })

export const getGroup = (dirId, dn) =>
  client.get(`${base(dirId)}/entry`, { params: { dn } })

export const createGroup = (dirId, data) =>
  client.post(base(dirId), data)

export const deleteGroup = (dirId, dn) =>
  client.delete(`${base(dirId)}/entry`, { params: { dn } })

export const addGroupMember = (dirId, dn, data) =>
  client.post(`${base(dirId)}/members`, data, { params: { dn } })

export const removeGroupMember = (dirId, dn, data) =>
  client.delete(`${base(dirId)}/members`, { data, params: { dn } })
