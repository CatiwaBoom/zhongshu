import request from '@/utils/request'

const CHUNK_UPLOAD_TIMEOUT = 3 * 60 * 1000
const MERGE_TIMEOUT = 30 * 60 * 1000

export const initFileUpload = (data) => {
    return request.post('/file/platform/init', data)
}

export const uploadFileChunk = (formData) => {
    return request.post('/file/platform/chunk', formData, {
        headers: {
            'Content-Type': 'multipart/form-data'
        },
        timeout: CHUNK_UPLOAD_TIMEOUT
    })
}

export const mergeFileUpload = (params) => {
    return request.post('/file/platform/merge', null, {
        params,
        timeout: MERGE_TIMEOUT
    })
}

export const getUploadStatus = (uploadId) => {
    return request.get(`/file/platform/upload/${uploadId}/status`)
}

export const listFileObjects = (params) => {
    return request.get('/file/platform/list', {params})
}

export const downloadFileObject = (fileId) => {
    return request.get(`/file/platform/download/${fileId}`, {
        responseType: 'blob'
    })
}

export const deleteFileObject = (fileId) => {
  return request.post(`/file/platform/delete/${fileId}`)
}

