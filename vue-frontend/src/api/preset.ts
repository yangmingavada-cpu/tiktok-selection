import request from '@/utils/request'
import type { ApiResponse, PresetPackage } from '@/types'

/** 列出所有 active 的官方预设方案（用户端只读） */
export function listPresetPackages() {
  return request.get<unknown, ApiResponse<PresetPackage[]>>('/preset-packages')
}
