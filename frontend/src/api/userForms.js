import client from './client'

const base = '/user-forms'

export const listUserForms  = ()           => client.get(base)
export const getUserForm    = (id)         => client.get(`${base}/${id}`)
export const createUserForm = (data)       => client.post(base, data)
export const updateUserForm = (id, data)   => client.put(`${base}/${id}`, data)
export const deleteUserForm = (id)         => client.delete(`${base}/${id}`)
