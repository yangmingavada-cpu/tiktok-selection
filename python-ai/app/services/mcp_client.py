import logging
from typing import Any

import httpx

from app.config import settings

logger = logging.getLogger(__name__)


class MCPClient:
    """
    MCP Client，通过HTTP POST向Java MCP Server发送JSON-RPC 2.0请求。
    每次/intent/parse请求创建独立实例，传入对应的mcp_endpoint。
    """

    def __init__(self, mcp_base_url: str | None = None):
        base_url = mcp_base_url or settings.mcp_server_url
        self._base_url = base_url
        self._http_client: httpx.AsyncClient | None = None
        self._request_id = 0

    @property
    def http_client(self) -> httpx.AsyncClient:
        if self._http_client is None:
            # 注入 MCP 内部认证 Token（安全规约第1条：P0-2修复）
            # Token 通过环境变量 MCP_INTERNAL_TOKEN 注入，Java 端 McpAuthFilter 验证
            headers = {}
            if settings.mcp_internal_token:
                headers["X-MCP-Token"] = settings.mcp_internal_token
            self._http_client = httpx.AsyncClient(timeout=30.0, headers=headers)
        return self._http_client

    def _next_id(self) -> int:
        self._request_id += 1
        return self._request_id

    async def _jsonrpc_call(self, method: str, params: dict | None = None) -> Any:
        """发送JSON-RPC 2.0请求。"""
        payload = {
            "jsonrpc": "2.0",
            "id": self._next_id(),
            "method": method,
        }
        if params:
            payload["params"] = params

        response = await self.http_client.post(self._base_url, json=payload)
        response.raise_for_status()
        result = response.json()

        if "error" in result:
            raise MCPError(result["error"].get("message", "Unknown MCP error"))

        return result.get("result")

    async def list_tools(self, session_id: str | None = None) -> list[dict]:
        """获取当前可用Tool列表（动态，每次调用可能不同）。"""
        params: dict = {}
        if session_id:
            params["_meta"] = {"session_id": session_id}
        result = await self._jsonrpc_call("tools/list", params or None)
        return result.get("tools", []) if result else []

    async def call_tool(
        self,
        name: str,
        arguments: dict,
        session_id: str | None = None,
        tool_call_id: str | None = None,
    ) -> dict:
        """调用MCP Tool。"""
        params: dict = {"name": name, "arguments": arguments}
        meta: dict = {}
        if session_id:
            meta["session_id"] = session_id
        if tool_call_id:
            meta["tool_call_id"] = tool_call_id
        if meta:
            params["_meta"] = meta
        result = await self._jsonrpc_call("tools/call", params)
        return result or {}

    async def read_resource(self, uri: str, session_id: str | None = None) -> dict:
        """读取MCP Resource（品类树、字段字典等）。"""
        params: dict = {"uri": uri}
        if session_id:
            params["_meta"] = {"session_id": session_id}
        result = await self._jsonrpc_call("resources/read", params)
        return result or {}

    async def initialize(self, session_id: str, session_context: dict | None = None) -> None:
        """初始化MCP会话（增量模式时传入session_context）。"""
        meta: dict = {"session_id": session_id}
        if session_context:
            meta["session_context"] = session_context
        await self._jsonrpc_call("initialize", {"_meta": meta})

    async def notify_thinking(self, text: str, session_id: str | None = None) -> None:
        """推送 LLM 实时 token 到 Java 进度总线（best-effort，失败静默）。"""
        if not text:
            return
        params: dict = {"text": text}
        if session_id:
            params["_meta"] = {"session_id": session_id}
        try:
            await self._jsonrpc_call("notify/thinking", params)
        except Exception:
            pass  # best-effort，不中断主流程

    async def close(self) -> None:
        if self._http_client:
            await self._http_client.aclose()
            self._http_client = None

    @staticmethod
    def mcp_tools_to_openai_functions(mcp_tools: list[dict]) -> list[dict]:
        """将MCP Tool定义转换为OpenAI Function Calling格式。"""
        return [
            {
                "type": "function",
                "function": {
                    "name": tool["name"],
                    "description": tool.get("description", ""),
                    "parameters": tool.get("inputSchema", {"type": "object", "properties": {}}),
                },
            }
            for tool in mcp_tools
        ]


class MCPError(Exception):
    pass
