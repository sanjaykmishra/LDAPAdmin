import client from './client'

export const browse = (dirId, dn) =>
  client.get(`/superadmin/directories/${dirId}/browse`, {
    params: { dn: dn || undefined },
  })
