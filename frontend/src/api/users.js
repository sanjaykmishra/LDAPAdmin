import client from './client'

const base = (dirId) => `/directories/${dirId}/users`

export const searchUsers = (dirId, params) =>
  client.get(base(dirId), { params })

export const getUser = (dirId, dn) =>
  client.get(`${base(dirId)}/entry`, { params: { dn } })

export const createUser = (dirId, data) =>
  client.post(base(dirId), data)

export const updateUser = (dirId, dn, data) =>
  client.put(`${base(dirId)}/entry`, data, { params: { dn } })

export const deleteUser = (dirId, dn) =>
  client.delete(`${base(dirId)}/entry`, { params: { dn } })

export const enableUser = (dirId, dn) =>
  client.post(`${base(dirId)}/enable`, null, { params: { dn } })

export const disableUser = (dirId, dn) =>
  client.post(`${base(dirId)}/disable`, null, { params: { dn } })

export const moveUser = (dirId, dn, data) =>
  client.post(`${base(dirId)}/move`, data, { params: { dn } })
