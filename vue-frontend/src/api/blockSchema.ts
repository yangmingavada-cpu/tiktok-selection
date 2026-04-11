import request from '@/utils/request'
import type { ApiResponse } from '@/types'

/**
 * 单个字段的 schema 元数据（来自后端 @McpParam 注解的反射）
 */
export interface BlockSchemaField {
  type: 'string' | 'integer' | 'number' | 'boolean' | 'array'
  description: string
  enum?: string[]
  default?: string
  example?: string
}

/**
 * 单个 block 的完整 schema
 * 用于"方案库执行前可视化编辑"按 schema 动态渲染表单
 */
export interface BlockSchema {
  blockId: string
  /** 来自 @McpBlock.description，例如"商品榜单" */
  label: string
  /** Echotik API 端点路径，例如"product/ranklist"；纯计算块为空 */
  endpoint: string
  outputType: string
  schema: {
    type: 'object'
    properties: Record<string, BlockSchemaField>
    required: string[]
  }
}

/**
 * 拉取 block schema
 * @param blockIds 要拉取的 blockId 列表，留空时返回全部
 */
export function getBlockSchemas(blockIds?: string[]) {
  const params = blockIds && blockIds.length > 0
    ? { ids: blockIds.join(',') }
    : undefined
  return request.get<unknown, ApiResponse<Record<string, BlockSchema>>>(
    '/blocks/schemas',
    { params },
  )
}
