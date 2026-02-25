import client from './client'

const base = (dirId) => `/directories/${dirId}/attribute-profiles`

export const listProfiles   = (dirId)       => client.get(base(dirId))
export const getProfile     = (dirId, id)   => client.get(`${base(dirId)}/${id}`)
export const createProfile  = (dirId, data) => client.post(base(dirId), data)
export const updateProfile  = (dirId, id, data) => client.put(`${base(dirId)}/${id}`, data)
export const deleteProfile  = (dirId, id)   => client.delete(`${base(dirId)}/${id}`)
