import request from '@/utils/request'
import type { ApiResponse, IntentParseRequest, IntentParseResponse, InterpretResponse, PreviewResponse, Block } from '@/types'
import { TIMEOUT, STORAGE_KEY } from '@/constants'

export function confirmPlanDraft(data: {
  agentThreadId?: string
  buildSessionId?: string
  conversationSummary?: string
  userText?: string
  qaHistory?: { q: string; a: string }[]
  plan?: import('@/types').PlanDraft
}) {
  return request.post<unknown, ApiResponse<IntentParseResponse>>('/intent/confirm-plan', data, {
    timeout: TIMEOUT.INTENT_PARSE,
  })
}

export type { IntentParseRequest, IntentParseResponse, PreviewResponse } from '@/types'

export function parseIntent(data: IntentParseRequest) {
  return request.post<unknown, ApiResponse<IntentParseResponse>>('/intent/parse', data, {
    timeout: TIMEOUT.INTENT_PARSE,
  })
}

export function previewBlockChain(blockChain: Block[]) {
  return request.post<unknown, ApiResponse<PreviewResponse>>('/intent/preview', { blockChain }, {
    timeout: TIMEOUT.INTENT_PREVIEW,
  })
}

export function interpretBlockChain(blockChain: Block[]) {
  return request.post<unknown, ApiResponse<InterpretResponse>>('/intent/interpret', { blockChain }, {
    timeout: TIMEOUT.INTENT_INTERPRET,
  })
}

interface SSECallbacks {
  onToken: (token: string) => void
  onDone: () => void
  onError: (err: string) => void
}

/** Parse a single SSE data line. Returns true if the stream should stop. */
function handleSSELine(line: string, cb: SSECallbacks): boolean {
  if (!line.startsWith('data:')) return false
  const jsonStr = line.slice(5).trim()
  if (!jsonStr) return false
  try {
    const data = JSON.parse(jsonStr)
    if (data.token) cb.onToken(data.token)
    if (data.done) { cb.onDone(); return true }
    if (data.error) { cb.onError(data.error); return true }
  } catch {
    // skip malformed SSE line
  }
  return false
}

/**
 * 流式解读积木链：通过 fetch SSE 逐 token 回调，实时渲染 Markdown。
 */
export async function interpretBlockChainStream(
  blockChain: Block[],
  onToken: (token: string) => void,
  onDone: () => void,
  onError: (err: string) => void,
  options?: {
    userId?: string
    sessionId?: string
    agentThreadId?: string
  },
) {
  const authToken = localStorage.getItem(STORAGE_KEY.TOKEN)
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (authToken) headers.Authorization = `Bearer ${authToken}`

  let response: Response
  try {
    response = await fetch('/api/intent/interpret-stream', {
      method: 'POST',
      headers,
      body: JSON.stringify({
        blockChain,
        user_id: options?.userId,
        session_id: options?.sessionId,
        agent_thread_id: options?.agentThreadId,
      }),
    })
  } catch {
    onError('网络请求失败')
    return
  }

  if (!response.ok || !response.body) {
    onError('请求失败')
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  const cb: SSECallbacks = { onToken, onDone, onError }
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const line of lines) {
      if (handleSSELine(line, cb)) return
    }
  }
  onDone()
}
