import client from './client'

export const listDirectories  = ()              => client.get('/superadmin/directories')
export const getDirectory     = (id)            => client.get(`/superadmin/directories/${id}`)
export const createDirectory  = (data)          => client.post('/superadmin/directories', data)
export const updateDirectory  = (id, data)      => client.put(`/superadmin/directories/${id}`, data)
export const deleteDirectory  = (id)            => client.delete(`/superadmin/directories/${id}`)
export const testDirectory    = (data)          => client.post('/superadmin/directories/test', data)
export const evictPool        = (id)            => client.post(`/superadmin/directories/${id}/evict-pool`)
