import client from './client'

const base = (dirId) => `/directories/${dirId}/csv-templates`

export const listCsvTemplates = (dirId) =>
  client.get(base(dirId))

export const getCsvTemplate = (dirId, templateId) =>
  client.get(`${base(dirId)}/${templateId}`)

export const createCsvTemplate = (dirId, data) =>
  client.post(base(dirId), data)

export const updateCsvTemplate = (dirId, templateId, data) =>
  client.put(`${base(dirId)}/${templateId}`, data)

export const deleteCsvTemplate = (dirId, templateId) =>
  client.delete(`${base(dirId)}/${templateId}`)

export const importCsv = (dirId, file, request) => {
  const form = new FormData()
  form.append('file', file)
  form.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }))
  return client.post(`/directories/${dirId}/users/import`, form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const exportCsv = (dirId, params) =>
  client.get(`/directories/${dirId}/users/export`, { params, responseType: 'blob' })
