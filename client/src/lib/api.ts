import axios from 'axios'

const baseURL =
  import.meta.env.VITE_API_URL ??
  (import.meta.env.PROD ? 'https://api.cove.greatshiftai.com' : 'http://localhost:8080')

export const api = axios.create({
  baseURL,
  withCredentials: false,
})

export const setAuthToken = (token?: string) => {
  if (token) {
    api.defaults.headers.common.Authorization = `Bearer ${token}`
  } else {
    delete api.defaults.headers.common.Authorization
  }
}
