import client from './client'

const base = (dirId) => `/directories/${dirId}/hr`

export const getHrConnection      = (dirId)        => client.get(base(dirId))
export const createHrConnection   = (dirId, data)  => client.post(base(dirId), data)
export const updateHrConnection   = (dirId, data)  => client.put(base(dirId), data)
export const deleteHrConnection   = (dirId)        => client.delete(base(dirId))

export const testHrConnection     = (dirId, data)  => client.post(`${base(dirId)}/test`, data)
export const triggerHrSync        = (dirId)        => client.post(`${base(dirId)}/sync`)

export const getHrSyncHistory     = (dirId, params) => client.get(`${base(dirId)}/sync-history`, { params })
export const listHrEmployees      = (dirId, params) => client.get(`${base(dirId)}/employees`, { params })
export const listOrphanedAccounts = (dirId)         => client.get(`${base(dirId)}/employees/orphaned`)
export const getHrSummary         = (dirId)         => client.get(`${base(dirId)}/summary`)
