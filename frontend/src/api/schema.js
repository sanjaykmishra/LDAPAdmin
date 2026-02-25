import client from './client'

const base = (dirId) => `/directories/${dirId}/schema`

export const listObjectClasses  = (dirId)       => client.get(`${base(dirId)}/object-classes`)
export const getObjectClass     = (dirId, name) => client.get(`${base(dirId)}/object-classes/${encodeURIComponent(name)}`)
export const listAttributeTypes = (dirId)       => client.get(`${base(dirId)}/attribute-types`)
export const getAttributeType   = (dirId, name) => client.get(`${base(dirId)}/attribute-types/${encodeURIComponent(name)}`)
