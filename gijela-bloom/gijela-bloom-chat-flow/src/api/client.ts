import axios from 'axios'

const instance = axios.create({
  baseURL: (import.meta.env.VITE_API_BASE as string) || '/api',
  timeout: 15000
})

instance.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = token.startsWith('Bearer ') ? token : `Bearer ${token}`
  }
  return config
})

export default instance
