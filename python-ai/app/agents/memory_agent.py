"""
Memory Agent：对话中的记忆管理子 Agent，被主 Agent 同步调用。

职责：
- query_memory：自然语言检索记忆，返回格式化内容
- save_memory：写入记忆，自动分配 seq 和分类
- save_tool_result：自动保存 MCP 工具调用的完整返回数据

与 Distillation Agent 的区别：
- Memory Agent：对话过程中随时调用，处理读/写/检索
- Distillation Agent：任务执行完成后异步触发，提取跨会话用户偏好
"""

from __future__ import annotations

import hashlib
import json
import logging
import re
from datetime import datetime, timezone

from langchain_openai import ChatOpenAI

from app.services.memory_client import MemoryClient

logger = logging.getLogger(__name__)

MEMORY_INDEX_SCAN_LIMIT = 20
MEMORY_SELECT_LIMIT = 5
MEMORY_FILE_EXCERPT_CHARS = 1800

_MEMORY_QUERY_PROMPT = """你是记忆管理专家。你的职责是从记忆索引中精确匹配用户问题最相关的条目。

记忆索引格式说明：
- #seq [phase-名称](文件路径) — chain:hash | 阶段 | API | 数据量
- phase 标签：规划=规划阶段API预览数据，执行=执行阶段实际数据
- chain:hash 标识属于哪个积木链版本（同一个 hash 表示同一轮规划/执行）
- common 记忆无 chain hash，是跨会话的用户画像/偏好

筛选原则：
- 用户问"第一步数据"→ 匹配最近一轮的执行步骤1
- 用户问"上次拿了什么数据"→ 匹配最近一轮的所有执行步骤
- 用户问"规划时的预览"→ 匹配规划阶段的数据
- 用户问偏好/习惯 → 匹配 common 用户画像记忆
- 不同 chain hash 的数据属于不同轮次，优先匹配最新轮次
- 描述过于泛化的记忆（如"选品相关""用户偏好"）相关性应降低

直接返回选中序号的 JSON 数组，如 [0,2,4]。无相关时返回 []。"""


def compute_chain_hash(block_chain: list[dict] | None) -> str | None:
    """计算 block chain 内容的哈希前6位，用于标识轮次。"""
    if not block_chain:
        return None
    raw = json.dumps(block_chain, sort_keys=True, ensure_ascii=False)
    return hashlib.md5(raw.encode()).hexdigest()[:6]


def _freshness_hint(updated_at_str: str | None) -> str:
    """将 updatedAt 转换为新鲜度提示。"""
    if not updated_at_str:
        return ""
    try:
        updated_dt = datetime.fromisoformat(updated_at_str.replace("Z", "+00:00"))
        days_old = (datetime.now(timezone.utc) - updated_dt).days
        if days_old >= 30:
            return f" ⚠（{days_old}天前，可能严重过时）"
        if days_old >= 7:
            return f" *（{days_old}天前）*"
        if days_old >= 1:
            return f" *（{days_old}天前）*"
    except Exception:
        pass
    return ""


def _freshness_warning(updated_at_str: str | None) -> str:
    """用于 read_memory 返回内容时的新鲜度警告。"""
    if not updated_at_str:
        return ""
    try:
        updated_dt = datetime.fromisoformat(updated_at_str.replace("Z", "+00:00"))
        days_old = (datetime.now(timezone.utc) - updated_dt).days
        if days_old >= 30:
            return f"此记忆已 {days_old} 天未更新，可能严重过时，使用前请核实。"
        if days_old >= 7:
            return f"此记忆已 {days_old} 天未更新，建议核实。"
    except Exception:
        pass
    return ""


class MemoryAgent:
    """对话中的记忆管理子 Agent，被主 Agent 同步调用。"""

    def __init__(
        self,
        memory_client: MemoryClient,
        llm: ChatOpenAI,
        agent_thread_id: str,
        user_id: str,
    ):
        self._memory_client = memory_client
        self._llm = llm
        self._agent_thread_id = agent_thread_id
        self._user_id = user_id
        # 所有历史线程ID（压缩会生成新ID，旧ID的记忆仍需可查）
        self._all_thread_ids: list[str] = [agent_thread_id]
        # 内部状态：已读取的文件路径（避免重复筛选）
        self._surfaced_paths: set[str] = set()
        # 已写入的记忆名称（供蒸馏去重）
        self.written_memory_names: list[str] = []
        # 缓存的 updatedAt 映射
        self._updated_at_map: dict[str, str] = {}

    def update_thread_id(self, new_id: str):
        """压缩后更新线程ID。新写入用新ID，查询时合并所有历史ID。"""
        self._agent_thread_id = new_id
        if new_id not in self._all_thread_ids:
            self._all_thread_ids.append(new_id)

    async def query(self, question: str, active_tools: list[str] | None = None) -> str:
        """
        自然语言检索记忆，返回格式化内容。
        主 Agent 调用 query_memory 工具时触发。
        """
        # 合并查询所有历史线程ID下的记忆（压缩前+压缩后）
        raw_index = []
        for tid in self._all_thread_ids:
            idx = await self._memory_client.list_index(session_id=tid)
            raw_index.extend(idx)
        if not raw_index:
            return "（记忆系统为空，尚无历史记录）"

        # 缓存 updatedAt
        for entry in raw_index:
            if entry.get("filePath"):
                self._updated_at_map[entry["filePath"]] = entry.get("updatedAt", "")

        # 排除已读取的路径
        candidates = [
            e for e in raw_index[:MEMORY_INDEX_SCAN_LIMIT]
            if e.get("filePath") not in self._surfaced_paths
        ]

        if not candidates:
            return "（所有相关记忆已在对话中展示过）"

        # 筛选最相关条目
        selected = await self._filter_relevant(question, candidates, active_tools)
        if not selected:
            return "（未找到与问题相关的记忆）"

        # 读取文件内容
        file_paths = [e["filePath"] for e in selected if e.get("filePath")]
        files = await self._memory_client.read_files(file_paths)

        # 标记已读取
        self._surfaced_paths.update(files.keys())

        # 组装返回
        parts = []
        for path, content in files.items():
            header = f"## {path}"
            warning = _freshness_warning(self._updated_at_map.get(path))
            if warning:
                header += f"\n> ⚠ {warning}"
            # 截断过长内容
            if len(content) > MEMORY_FILE_EXCERPT_CHARS:
                content = content[:MEMORY_FILE_EXCERPT_CHARS] + "\n\n...(内容过长已截断)"
            parts.append(f"{header}\n{content}")

        return "\n\n---\n\n".join(parts) if parts else "（文件内容为空）"

    async def save(
        self,
        name: str,
        description: str,
        content: str,
        memory_type: str = "project",
        scope: str = "session",
        phase: str | None = None,
        block_chain_hash: str | None = None,
    ) -> str:
        """
        写入记忆。主 Agent 调用 save_memory 工具或自动保存工具结果时触发。
        """
        session_id = None if scope == "common" else self._agent_thread_id
        result = await self._memory_client.write_memory(
            scope=scope,
            memory_type=memory_type,
            name=name,
            description=description,
            content=content,
            session_id=session_id,
            phase=phase,
            block_chain_hash=block_chain_hash,
        )
        if result:
            self.written_memory_names.append(name)
            return f"记忆已写入: {name}"
        return f"记忆写入失败: {name}"

    async def save_tool_result(
        self,
        phase: str,
        block_chain_hash: str | None,
        tool_name: str,
        tool_args: dict | str,
        tool_result: dict | str,
    ) -> None:
        """
        自动保存 MCP 工具调用的完整返回数据到记忆。
        在 _tool_node 中每次工具调用后自动触发。
        """
        try:
            # 解析参数
            if isinstance(tool_args, str):
                try:
                    tool_args = json.loads(tool_args)
                except Exception:
                    tool_args = {"raw": tool_args}

            if isinstance(tool_result, str):
                try:
                    tool_result = json.loads(tool_result)
                except Exception:
                    tool_result = {"raw": tool_result}

            # 提取关键信息
            success = tool_result.get("success", True)
            data_count = tool_result.get("chain_length") or tool_result.get("outputCount") or ""
            message = tool_result.get("message", "")

            name = f"{phase}-{tool_name}"
            desc_parts = [phase]
            if block_chain_hash:
                desc_parts.append(f"chain:{block_chain_hash}")
            if data_count:
                desc_parts.append(f"{data_count}条")
            if message and len(message) < 60:
                desc_parts.append(message)
            description = " | ".join(desc_parts)

            # 构建内容
            content = f"## 工具调用: {tool_name}\n\n"
            content += f"- 阶段: {phase}\n"
            if block_chain_hash:
                content += f"- 积木链: chain:{block_chain_hash}\n"
            content += f"- 成功: {success}\n\n"
            content += f"### 参数\n```json\n{json.dumps(tool_args, ensure_ascii=False, indent=2)}\n```\n\n"
            content += f"### 返回结果\n```json\n{json.dumps(tool_result, ensure_ascii=False, indent=2)}\n```"

            await self.save(
                name=name,
                description=description,
                content=content,
                memory_type="project",
                scope="session",
                phase=phase,
                block_chain_hash=block_chain_hash,
            )
        except Exception as e:
            logger.warning("save_tool_result failed tool=%s: %s", tool_name, e)

    async def _filter_relevant(
        self,
        question: str,
        candidates: list[dict],
        active_tools: list[str] | None = None,
    ) -> list[dict]:
        """用 LLM 从候选记忆中筛选最相关的条目。"""
        if len(candidates) <= MEMORY_SELECT_LIMIT:
            return candidates

        index_lines = [
            f"{i}: [seq={e.get('seq', '?')}] [{e.get('phase') or 'common'}] "
            f"【{e.get('name', '')}】{e.get('description', '')}"
            for i, e in enumerate(candidates)
        ]
        tools_hint = ""
        if active_tools:
            tools_hint = f"\n当前用户可用工具：{', '.join(active_tools[:10])}\n"

        user_prompt = (
            f"用户问题：{question}\n{tools_hint}\n"
            f"记忆索引（序号 [seq] [阶段] 名称 描述）：\n" + "\n".join(index_lines) +
            f"\n\n从上述记忆中选出最多 {MEMORY_SELECT_LIMIT} 条最相关的序号。"
        )

        try:
            resp = await self._llm.ainvoke([
                {"role": "system", "content": _MEMORY_QUERY_PROMPT},
                {"role": "user", "content": user_prompt},
            ])
            match = re.search(r'\[[\d,\s]*\]', resp.content.strip())
            if match:
                indices = json.loads(match.group())
                return [
                    candidates[i] for i in indices
                    if isinstance(i, int) and 0 <= i < len(candidates)
                ][:MEMORY_SELECT_LIMIT]
        except Exception as e:
            logger.warning("Memory filter LLM call failed, falling back to head-%d: %s", MEMORY_SELECT_LIMIT, e)

        return candidates[:MEMORY_SELECT_LIMIT]

    async def close(self) -> None:
        """释放资源。"""
        await self._memory_client.close()
