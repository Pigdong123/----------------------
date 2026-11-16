import request from './request'

export const queueStatus = () => request.get('/ops/queue')
export const engineHealth = () => request.get('/ops/engines')
export const storageStatus = () => request.get('/ops/storage')
export const opsOverview = () => request.get('/ops/overview')
export const runCleanup = () => request.post('/ops/maintenance/cleanup')
