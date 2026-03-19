import client from './client'

const base = '/user-templates'

export const listUserTemplates  = ()           => client.get(base)
export const getUserTemplate    = (id)         => client.get(`${base}/${id}`)
export const createUserTemplate = (data)       => client.post(base, data)
export const updateUserTemplate = (id, data)   => client.put(`${base}/${id}`, data)
export const deleteUserTemplate = (id)         => client.delete(`${base}/${id}`)
