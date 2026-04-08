import request from '@/utils/request'

export const getSeatunnelPipelineList = () => {
  return request.get('/seatunnel/pipeline/list')
}

export const createSeatunnelPipeline = (data) => {
  return request.post('/seatunnel/pipeline/add', data)
}

export const updateSeatunnelPipeline = (id, data) => {
  return request.post(`/seatunnel/pipeline/update/${id}`, data)
}

export const deleteSeatunnelPipeline = (id) => {
  return request.get(`/seatunnel/pipeline/delete/${id}`)
}

export const runSeatunnelPipeline = (id) => {
  return request.post(`/seatunnel/pipeline/run/${id}`)
}

export const getSeatunnelExecution = (id) => {
  return request.get(`/seatunnel/execution/${id}`)
}

export const getSeatunnelExecutionLog = (id, lines = 200) => {
  return request.get(`/seatunnel/execution/${id}/log`, { params: { lines } })
}

export const stopSeatunnelExecution = (id) => {
  return request.post(`/seatunnel/execution/stop/${id}`)
}
