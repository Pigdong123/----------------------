import request from './request'

// ---------- 用户端 ----------
export const createFeedback = (data) => request.post('/feedback', data)
// 兼容旧的任务详情页调用
export const submitFeedback = createFeedback
export const listMyFeedback = (params) => request.get('/feedback/mine', { params })
export const getMyFeedback = (id) => request.get(`/feedback/${id}`)
export const withdrawFeedback = (id) => request.post(`/feedback/${id}/withdraw`)
export const listFeedbackByTask = (taskId, params) =>
  request.get(`/feedback/task/${taskId}`, { params })

// ---------- 管理端 ----------
export const adminListFeedback = (params) => request.get('/admin/feedback', { params })
export const adminFeedbackStats = () => request.get('/admin/feedback/stats')
export const adminGetFeedback = (id) => request.get(`/admin/feedback/${id}`)
export const adminReplyFeedback = (id, data) => request.post(`/admin/feedback/${id}/reply`, data)
export const adminUpdateFeedbackStatus = (id, status) =>
  request.put(`/admin/feedback/${id}/status`, { status })
