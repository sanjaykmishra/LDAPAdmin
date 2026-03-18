import client from './client'

const base = (dirId) => `/superadmin/directories/${dirId}/browse`

export const browse = (dirId, dn) =>
  client.get(base(dirId), { params: { dn: dn || undefined } })

export const createEntry = (dirId, data) =>
  client.post(base(dirId), data)

export const browseObjectClasses = (dirId) =>
  client.get(`${base(dirId)}/schema/object-classes`)

export const browseObjectClassesBulk = (dirId, names) =>
  client.get(`${base(dirId)}/schema/object-classes/bulk`, {
    params: { names: names.join(',') },
  })
