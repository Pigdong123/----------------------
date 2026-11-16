import request from './request'

export const aiStatus = () => request.get('/ai/status')
export const aiRecommend = (fileId) => request.get(`/ai/recommend/${fileId}`)
export const aiParams = (params) => request.get('/ai/params', { params })
export const aiDiagnose = (message) => request.get('/ai/diagnose', { params: { message } })
export const aiChat = (question) => request.get('/ai/chat', { params: { question } })
