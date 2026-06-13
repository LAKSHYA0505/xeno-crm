import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8087',
  headers: { 'Content-Type': 'application/json' }
})

export const customerApi = {
  getAll: (page: number = 0, size: number = 20, search: string = '') =>
    api.get(`/api/customers?page=${page}&size=${size}&search=${search}`),
  getById: (id: string | number) => api.get(`/api/customers/${id}`),
  getOrders: (id: string | number) => api.get(`/api/customers/${id}/orders`)
}

export const segmentApi = {
  getAll: () => api.get('/api/segments'),
  getById: (id: string | number) => api.get(`/api/segments/${id}`),
  parse: (query: string) => api.post('/api/segments/parse', { query }),
  create: (data: Record<string, unknown>) => api.post('/api/segments', data),
  generateMessage:  (segmentDescription: string) =>   
    api.post('/api/segments/generate-message', { segmentDescription }),
}

export const campaignApi = {
  getAll: () => api.get('/api/campaigns'),
  getById: (id: string | number) => api.get(`/api/campaigns/${id}`),
  create: (data: Record<string, unknown>) => api.post('/api/campaigns', data),
  launch: (id: string | number) => api.post(`/api/campaigns/${id}/launch`),
  aiSummary: (id: string | number) => api.post(`/api/campaigns/${id}/ai-summary`)
}

export default api