import client from './client'

const base = (dirId) => `/directories/${dirId}/realms`

export const listAllRealms = ()                    => client.get('/realms')
export const listRealms   = (dirId)                => client.get(base(dirId))
export const getRealm     = (dirId, realmId)       => client.get(`${base(dirId)}/${realmId}`)
export const createRealm  = (dirId, data)          => client.post(base(dirId), data)
export const updateRealm  = (dirId, realmId, data) => client.put(`${base(dirId)}/${realmId}`, data)
export const deleteRealm  = (dirId, realmId)       => client.delete(`${base(dirId)}/${realmId}`)
