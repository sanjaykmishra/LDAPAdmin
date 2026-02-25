import client from './client'

const base = (dirId) => `/directories/${dirId}/groups`

export const searchGroups = (dirId, params) =>
  client.get(base(dirId), { params })

export const getGroup = (dirId, dn) =>
  client.get(`${base(dirId)}/${encodeURIComponent(dn)}`)

export const createGroup = (dirId, data) =>
  client.post(base(dirId), data)

export const deleteGroup = (dirId, dn) =>
  client.delete(`${base(dirId)}/${encodeURIComponent(dn)}`)

export const addGroupMember = (dirId, dn, data) =>
  client.post(`${base(dirId)}/${encodeURIComponent(dn)}/members`, data)

export const removeGroupMember = (dirId, dn, memberDn) =>
  client.delete(`${base(dirId)}/${encodeURIComponent(dn)}/members/${encodeURIComponent(memberDn)}`)
