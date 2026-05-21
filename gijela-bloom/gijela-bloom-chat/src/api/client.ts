import axios, { AxiosHeaders } from 'axios'
import { buildCommonHeaders } from '@/utils/auth'

const client = axios.create({
  baseURL: '/api',
  timeout: 300000  // 5 分钟
})

client.interceptors.request.use((config) => {
  const headers = AxiosHeaders.from(config.headers ?? {})
  const common = buildCommonHeaders()
  Object.entries(common).forEach(([key, value]) => {
    headers.set(key, value)
  })
  config.headers = headers
  return config
})

export default client
