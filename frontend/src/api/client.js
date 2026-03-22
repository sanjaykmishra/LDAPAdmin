import axios from 'axios'

const client = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true, // send the httpOnly JWT cookie on every request
})

// On 401 redirect to the appropriate login page
client.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      const path = window.location.pathname
      // Don't redirect if already on a login page
      if (path !== '/login' && path !== '/self-service/login') {
        // Self-service users go to self-service login; admins go to admin login
        if (path.startsWith('/self-service')) {
          window.location.href = '/self-service/login'
        } else if (!path.startsWith('/register')) {
          window.location.href = '/login'
        }
      }
    }
    return Promise.reject(err)
  }
)

export default client
