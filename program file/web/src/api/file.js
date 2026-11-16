import request from './request'

export const uploadFile = (file, onProgress) => {
  const form = new FormData()
  form.append('file', file)
  return request.post('/files', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (e) => {
      if (onProgress && e.total) {
        onProgress(Math.round((e.loaded / e.total) * 100))
      }
    }
  })
}

export const listFiles = (params) => request.get('/files', { params })
export const getFile = (id) => request.get(`/files/${id}`)
export const deleteFile = (id) => request.delete(`/files/${id}`)
export const recommendFormats = (id) => request.post(`/files/${id}/recommend`)
