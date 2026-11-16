import request from './request'

export const listUsers = (params) => request.get('/admin/users', { params })
export const updateUserRole = (id, role) =>
  request.put(`/admin/users/${id}/role`, null, { params: { role } })
export const updateUserEnabled = (id, enabled) =>
  request.put(`/admin/users/${id}/enabled`, null, { params: { enabled } })
export const updateUserQuota = (id, data) =>
  request.put(`/admin/users/${id}/quota`, data)

export const listTemplates = () => request.get('/admin/templates')
export const createTemplate = (data) => request.post('/admin/templates', data)
export const updateTemplate = (id, data) => request.put(`/admin/templates/${id}`, data)
export const deleteTemplate = (id) => request.delete(`/admin/templates/${id}`)
export const toggleTemplate = (id, enabled) =>
  request.put(`/admin/templates/${id}/toggle`, null, { params: { enabled } })

export const statsOverview = () => request.get('/admin/stats/overview')
export const statsByFormat = (days = 7) =>
  request.get('/admin/stats/formats', { params: { days } })
export const statsByStatus = () => request.get('/admin/stats/status')
export const listAudit = (params) => request.get('/admin/audit', { params })
