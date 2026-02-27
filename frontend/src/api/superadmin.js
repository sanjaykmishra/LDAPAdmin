import client from './client'

// ── Superadmins ───────────────────────────────────────────────────────────
export const listSuperadmins  = ()      => client.get('/superadmin/superadmins')
export const createSuperadmin = (data)  => client.post('/superadmin/superadmins', data)
export const deleteSuperadmin = (id)    => client.delete(`/superadmin/superadmins/${id}`)
