import client from './client'

export const getDashboard = () =>
  client.get('/superadmin/dashboard')

export const getAdminDashboard = () =>
  client.get('/admin/dashboard')
