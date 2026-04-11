import { computed, ref, shallowRef, toValue, watch } from 'vue'
import { listSessions } from '@/api/session'
import type { Session, SessionStatus } from '@/types'
import type { MaybeRefOrGetter } from 'vue'

interface SessionHistorySnapshot {
  updatedAt: number
  sessions: Session[]
}

const DB_NAME = 'tiktok-selection-history'
const STORE_NAME = 'session-history'
const DB_VERSION = 1
const CACHE_LIMIT = 12

interface UseSessionHistoryOptions {
  /** 过滤特定状态的会话 */
  statusFilter?: SessionStatus[]
  /** 缓存 key 前缀，默认为 'session-history' */
  cachePrefix?: string
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = window.indexedDB.open(DB_NAME, DB_VERSION)
    request.onerror = () => reject(request.error)
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME)
      }
    }
    request.onsuccess = () => resolve(request.result)
  })
}

async function getCache(key: string): Promise<SessionHistorySnapshot | null> {
  const db = await openDb()
  return new Promise<SessionHistorySnapshot | null>((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readonly')
    const store = tx.objectStore(STORE_NAME)
    const request = store.get(key)
    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve((request.result as SessionHistorySnapshot | null) ?? null)
  }).finally(() => db.close())
}

async function setCache(key: string, value: SessionHistorySnapshot): Promise<void> {
  const db = await openDb()
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)
    const request = store.put(value, key)
    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve()
  }).finally(() => db.close())
}

// ── 模块级内存缓存：key = session id，值 = 完整 Session 对象 ──────────────
const sessionDetailCache = new Map<string, import('@/types').Session>()

export async function getCachedSessionDetail(
  id: string,
  fetcher: (id: string) => Promise<import('@/types').Session | null>,
): Promise<import('@/types').Session | null> {
  // 层1：内存
  if (sessionDetailCache.has(id)) return sessionDetailCache.get(id)!

  // 层2：IndexedDB
  try {
    const cached = await getCache(`session-full:${id}`)
    if (cached) {
      const session = cached as unknown as import('@/types').Session
      sessionDetailCache.set(id, session)
      return session
    }
  } catch { /* ignore */ }

  // 层3：服务器
  const session = await fetcher(id)
  if (session) {
    sessionDetailCache.set(id, session)
    // 仅终态写 IndexedDB（不再变化）
    if (['completed', 'failed', 'cancelled'].includes(session.status)) {
      void setCache(`session-full:${id}`, session as unknown as SessionHistorySnapshot)
    }
  }
  return session
}

export function invalidateSessionDetailCache(id: string) {
  sessionDetailCache.delete(id)
}

export function useSessionHistory(
  cacheKey: MaybeRefOrGetter<string>,
  options: UseSessionHistoryOptions = {}
) {
  const { statusFilter, cachePrefix = 'session-history' } = options

  const sessions = ref<Session[]>([])
  const loading = shallowRef(false)
  const hydratedFromCache = shallowRef(false)
  const cacheUpdatedAt = shallowRef<number | null>(null)

  const hasSessions = computed(() => sessions.value.length > 0)

  async function hydrateFromCache() {
    try {
      const fullKey = `${cachePrefix}:${toValue(cacheKey)}`
      const snapshot = await getCache(fullKey)
      if (!snapshot?.sessions?.length) return
      sessions.value = snapshot.sessions
      cacheUpdatedAt.value = snapshot.updatedAt
      hydratedFromCache.value = true
    } catch {
      // ignore IndexedDB read failures and fall back to server
    }
  }

  async function refresh() {
    loading.value = true
    try {
      // 将 statusFilter 传给后端，减少不必要的数据传输
      // context=chat 让后端按 hidden_from_chat 过滤，跟选品记录页的 hidden_from_records 完全独立
      const statusParam = statusFilter && statusFilter.length > 0 ? statusFilter.join(',') : undefined
      const res = await listSessions({
        pageNum: 1,
        pageSize: CACHE_LIMIT,
        status: statusParam,
        context: 'chat',
      })
      const records = res.data?.records ?? []

      sessions.value = records
      cacheUpdatedAt.value = Date.now()
      const fullKey = `${cachePrefix}:${toValue(cacheKey)}`
      await setCache(fullKey, {
        updatedAt: cacheUpdatedAt.value,
        sessions: records,
      })
    } finally {
      loading.value = false
    }
  }

  async function persistCurrentSessions() {
    if (!cacheUpdatedAt.value) {
      cacheUpdatedAt.value = Date.now()
    }
    try {
      const fullKey = `${cachePrefix}:${toValue(cacheKey)}`
      await setCache(fullKey, {
        updatedAt: cacheUpdatedAt.value,
        sessions: sessions.value,
      })
    } catch {
      // ignore cache write failures
    }
  }

  function prependSession(session: Session) {
    sessions.value = [session, ...sessions.value.filter(s => s.id !== session.id)]
    cacheUpdatedAt.value = Date.now()
    void persistCurrentSessions()
  }

  function removeSessionFromHistory(sessionId: string) {
    sessions.value = sessions.value.filter(session => session.id !== sessionId)
    cacheUpdatedAt.value = Date.now()
    void persistCurrentSessions()
  }

  function updateSessionInHistory(sessionId: string, updates: Partial<Session>) {
    const session = sessions.value.find(s => s.id === sessionId)
    if (session) {
      Object.assign(session, updates)
      cacheUpdatedAt.value = Date.now()
      void persistCurrentSessions()
    }
  }

  async function bootstrap() {
    hydratedFromCache.value = false
    cacheUpdatedAt.value = null
    sessions.value = []
    await hydrateFromCache()
    void refresh()
  }

  watch(
    () => toValue(cacheKey),
    () => {
      void bootstrap()
    },
    { immediate: true },
  )

  return {
    sessions,
    loading,
    hydratedFromCache,
    cacheUpdatedAt,
    hasSessions,
    refresh,
    prependSession,
    removeSessionFromHistory,
    updateSessionInHistory,
  }
}
