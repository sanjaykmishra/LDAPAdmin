import client from './client'

export const login = (username, password) =>
  client.post('/auth/login', { username, password })

export const logout = () =>
  client.post('/auth/logout')

export const me = () =>
  client.get('/auth/me')

export const myProfiles = () =>
  client.get('/auth/me/profiles')

export const oidcAuthorize = () =>
  client.get('/auth/oidc/authorize')

export const oidcCallback = (code, state) =>
  client.post('/auth/oidc/callback', { code, state })
