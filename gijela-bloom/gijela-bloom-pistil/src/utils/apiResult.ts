import { ElMessage } from 'element-plus'

/**
 * 统一处理后端 API 统一响应体（可能是 { code, msg } 或直接为值）
 * 返回 true 表示视为成功并已显示成功信息（若提供）；false 表示失败并已显示后端 msg
 */
export function showApiResult(res: any, successText?: string, onSuccess?: () => void): boolean {
  const api = res && res.data ? res.data : res
  if (api && typeof api.code === 'number' && api.code !== 0) {
    const m = api.msg || api.message || String(api)
    ElMessage.error(String(m))
    return false
  }
  if (successText) ElMessage.success(String(successText))
  try { onSuccess && onSuccess() } catch (e) {}
  return true
}

export default { showApiResult }
