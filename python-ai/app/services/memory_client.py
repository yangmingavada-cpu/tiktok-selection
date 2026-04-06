import logging
from typing import Any

import httpx

from app.config import settings

logger = logging.getLogger(__name__)


class MemoryClient:
    """
    用户记忆文件 HTTP 客户端，对接 Java MemoryFileController。

    所有文件 I/O 由 Java 完成，Python 侧零磁盘操作。
    迁移 OSS 时只改 Java MemoryFileService 实现，本客户端无需改动。

    多 Agent 扩展说明：
    每个 Agent 实例传入不同的 agent_type，Java 侧通过 agent_type 字段追溯写入来源。
    不同 Agent 并发调用同一 userId 的写入接口时，Java 侧 per-userId ReentrantLock 保证顺序安全。
    """

    def __init__(self, user_id: str, agent_type: str = "main"):
        self._user_id = user_id
        self._agent_type = agent_type
        self._base_url = settings.java_backend_url.rstrip("/") + "/api/internal/memory"
        self._http_client: httpx.AsyncClient | None = None

    @property
    def _client(self) -> httpx.AsyncClient:
        if self._http_client is None:
            headers = {}
            if settings.mcp_internal_token:
                headers["X-MCP-Token"] = settings.mcp_internal_token
            self._http_client = httpx.AsyncClient(timeout=3.0, headers=headers)
        return self._http_client

    async def list_index(self, session_id: str | None = None) -> list[dict]:
        """
        查询记忆索引。
        session_id=None → 只返回 common 记忆。
        session_id 非空 → 返回 common + 该 session 的记忆。
        返回: [{name, description, memoryType, filePath, agentType, updatedAt}]
        """
        params: dict[str, Any] = {}
        if session_id:
            params["sessionId"] = session_id
        try:
            resp = await self._client.get(
                f"{self._base_url}/{self._user_id}/index", params=params
            )
            resp.raise_for_status()
            return resp.json().get("data") or []
        except Exception as e:
            logger.warning("MemoryClient.list_index failed: %s", e)
            return []

    async def read_files(self, file_paths: list[str]) -> dict[str, str]:
        """
        批量读取文件内容。
        file_paths 来自 list_index 返回的 filePath 字段。
        返回: {filePath: content}，文件不存在时跳过。
        """
        if not file_paths:
            return {}
        try:
            resp = await self._client.post(
                f"{self._base_url}/{self._user_id}/read-files",
                json={"filePaths": file_paths},
            )
            resp.raise_for_status()
            return resp.json().get("data") or {}
        except Exception as e:
            logger.warning("MemoryClient.read_files failed: %s", e)
            return {}

    async def write_memory(
        self,
        scope: str,
        memory_type: str,
        name: str,
        description: str,
        content: str,
        session_id: str | None = None,
    ) -> str | None:
        """
        写入一条记忆。
        scope: "common"（跨会话）| "session"（本次会话）
        memory_type: user / feedback / project / reference
        返回: 写入的相对路径（失败返回 None）
        """
        try:
            resp = await self._client.post(
                f"{self._base_url}/{self._user_id}/write",
                json={
                    "sessionId": session_id,
                    "scope": scope,
                    "memoryType": memory_type,
                    "name": name,
                    "description": description,
                    "content": content,
                    "agentType": self._agent_type,
                },
            )
            resp.raise_for_status()
            return resp.json().get("data")
        except Exception as e:
            logger.warning("MemoryClient.write_memory failed name=%s: %s", name, e)
            return None

    async def close(self) -> None:
        if self._http_client:
            await self._http_client.aclose()
            self._http_client = None
