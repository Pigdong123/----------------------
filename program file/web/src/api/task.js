import request from './request'

export const createTask = (data) => request.post('/tasks', data)
export const listTasks = (params) => request.get('/tasks', { params })
export const getTask = (id) => request.get(`/tasks/${id}`)
export const cancelTask = (id) => request.post(`/tasks/${id}/cancel`)
export const retryTask = (id) => request.post(`/tasks/${id}/retry`)

export const downloadTask = (id) =>
  request.get(`/tasks/${id}/download`, { responseType: 'blob' })
