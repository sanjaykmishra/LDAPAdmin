import client from './client'

const base = (dirId) => `/directories/${dirId}/users`

export const searchUsers = (dirId, params) =>
  client.get(base(dirId), { params })

export const getUser = (dirId, dn) =>
  client.get(`${base(dirId)}/${encodeURIComponent(dn)}`)

export const createUser = (dirId, data) =>
  client.post(base(dirId), data)

export const updateUser = (dirId, dn, data) =>
  client.put(`${base(dirId)}/${encodeURIComponent(dn)}`, data)

export const deleteUser = (dirId, dn) =>
  client.delete(`${base(dirId)}/${encodeURIComponent(dn)}`)

export const enableUser = (dirId, dn) =>
  client.post(`${base(dirId)}/${encodeURIComponent(dn)}/enable`)

export const disableUser = (dirId, dn) =>
  client.post(`${base(dirId)}/${encodeURIComponent(dn)}/disable`)

export const moveUser = (dirId, dn, data) =>
  client.post(`${base(dirId)}/${encodeURIComponent(dn)}/move`, data)
