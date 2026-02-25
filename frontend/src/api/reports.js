import client from './client'

const base = (dirId) => `/directories/${dirId}/report-jobs`

export const listReportJobs = (dirId, params) =>
  client.get(base(dirId), { params })

export const getReportJob = (dirId, jobId) =>
  client.get(`${base(dirId)}/${jobId}`)

export const createReportJob = (dirId, data) =>
  client.post(base(dirId), data)

export const updateReportJob = (dirId, jobId, data) =>
  client.put(`${base(dirId)}/${jobId}`, data)

export const deleteReportJob = (dirId, jobId) =>
  client.delete(`${base(dirId)}/${jobId}`)

export const setReportJobEnabled = (dirId, jobId, enabled) =>
  client.patch(`${base(dirId)}/${jobId}/enabled`, null, { params: { enabled } })

export const runReport = (dirId, data) =>
  client.post(`/directories/${dirId}/reports/run`, data, { responseType: 'blob' })
