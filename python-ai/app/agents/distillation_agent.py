"""
蒸馏 Agent：在 finalize_chain 成功后异步运行，从完整对话历史中提取值得跨会话保留的记忆。

设计原则：
- 纯函数入口（distill），无副作用之外只写记忆文件
- 权限最小化：只能调用 MemoryClient，无 MCP 工具访问权
- 与主 Agent 互斥：跳过主 Agent 在本次会话中已主动写入的记忆名称
- 为多 Agent 并行预留：agent_type="distillation" 用于追溯，并发写入由 Java 侧锁保护
"""

import json
import logging

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, ToolMessage
from langchain_openai import ChatOpenAI

from app.services.memory_client import MemoryClient

logger = logging.getLogger(__name__)

_DISTILL_SYSTEM_PROMPT = """你现在是记忆提取子 Agent。分析与用户的完整对话（含工具调用记录），\
将值得跨会话保留的信息写入记忆系统。

## 记忆类型

### user — 用户画像
用户的角色、目标市场、品类偏好、业务背景、知识水平。
**保存时机**：了解到用户的市场方向、选品策略偏好、行业经验等。
**示例**：主攻美区、偏好家居品类、习惯用价格区间筛选。

### feedback — 行为反馈
用户对 AI 行为的纠正、明确认可的做法、需要避免的模式。
**保存时机**：用户说"不要这样"、"就用这个方式"、或接受了某个非显而易见的选择。
**格式要求**：以规则开头，然后 **Why:**（用户给出的原因）和 **How to apply:**（何时生效）。

### project — 会话决策
本次选品的核心参数与主要决策。
**保存时机**：用户确定了目标市场、品类范围、价格区间、筛选策略等具体决策。
**格式要求**：以事实或决策开头，然后 **Why:**（动机）和 **How to apply:**（如何影响后续建议）。

### reference — 外部资源
用户提及的外部系统、数据来源、参考标准的位置信息。
**保存时机**：用户说"看这个报告"、"参考竞品 X"、"用这个平台的数据"。

## 不提取的内容

- 具体的积木链 JSON 结构、blockId、block 内部字段名（如 total_sale_cnt）
- 可从代码或当前状态推导出的技术细节
- 临时状态、当前对话上下文（不会在未来会话中有意义）
- already_written 列表中已存在的名称（防止重复写入）
- existing_index 列表中已存在的名称（防止与历史记忆重名）
- 任何空泛、没有具体信息量的内容（如"用户问了问题"）

## scope 选择

- **common**：跨会话永久有效的信息（用户偏好、行为反馈、长期参考资源）
- **session**：仅本次会话有意义的信息（本次选品参数、本次主要决策）

## 保存格式

直接输出 JSON 数组（不加代码块标记）。已在 already_written 或 existing_index 中的名称必须跳过。
每项结构：
{
  "scope": "common" | "session",
  "type": "user" | "feedback" | "project" | "reference",
  "name": "简短记忆名称（中文，≤10字，在所有记忆中唯一）",
  "description": "一句话摘要，用于未来相关性筛选（≤20字）",
  "content": "记忆正文（Markdown，feedback/project 类型需含 **Why:** 和 **How to apply:** 行）"
}

若没有值得记录的内容，返回空数组 []。

## 强制写入（不受 already_written / existing_index 去重限制，每次 finalize_chain 成功后必须包含）

即使 already_written 或 existing_index 中存在同名记忆，也必须写入以下条目（相当于覆盖更新）：

### selection_summary — 本次选品参数
- scope: session
- type: project
- name: 选品参数_<本次会话的核心一句话描述，如"泰区家居$5-50">
- description: 本次选品的核心决策参数，供后续会话直接复用
- content 固定格式（所有字段必填，不知道填"未指定"）：
  目标市场：xxx
  商品品类：xxx
  价格区间：xxx
  核心筛选条件：xxx（列举 1-3 条最重要的）
  评分维度与权重：xxx
  目标商品数量：xxx
  策略备注：xxx"""


async def distill(
    messages: list[BaseMessage],
    user_id: str,
    session_id: str,
    memory_client: MemoryClient,
    llm: ChatOpenAI,
    already_written_names: list[str] | None = None,
) -> None:
    """
    从完整对话历史中提取记忆并写入。异步执行，调用方无需等待。

    Args:
        messages:              完整 AgentState["messages"]，含工具调用记录
        user_id:               用户 ID
        session_id:            会话 ID
        memory_client:         记忆客户端（agent_type 应为 "distillation"）
        llm:                   用于蒸馏推理的 LLM 实例
        already_written_names: 主 Agent 本次会话已主动写入的记忆名称（跳过，防重复）
    """
    try:
        already_written = set(already_written_names or [])

        # 加载已有 common 记忆的 name 列表，防止与历史记忆重名/重复
        existing_index = await memory_client.list_index(session_id=None)
        existing_names = {e.get("name", "") for e in existing_index}

        conversation_text = _serialize_messages(messages)
        skip_names = already_written | existing_names

        prompt = (
            f"already_written（主 Agent 本次会话已写入，跳过）：{sorted(already_written)}\n"
            f"existing_index（历史已有记忆名称，跳过）：{sorted(existing_names)}\n\n"
            f"完整对话记录：\n{conversation_text}"
        )

        response = await llm.ainvoke([
            {"role": "system", "content": _DISTILL_SYSTEM_PROMPT},
            {"role": "user",   "content": prompt},
        ])

        memories = _parse_memories(response.content)
        if not memories:
            logger.info("DISTILL_AGENT userId=%s sessionId=%s: no memories extracted", user_id, session_id)
            return

        written = 0
        for mem in memories:
            name = mem.get("name", "")
            if name in skip_names:
                continue
            scope = mem.get("scope", "session")
            path = await memory_client.write_memory(
                scope=scope,
                memory_type=mem.get("type", "project"),
                name=name,
                description=mem.get("description", ""),
                content=mem.get("content", ""),
                session_id=None if scope == "common" else session_id,
            )
            if path:
                written += 1
                skip_names.add(name)

        logger.info("DISTILL_AGENT userId=%s sessionId=%s: wrote %d memories", user_id, session_id, written)

    except Exception as e:
        logger.error("DISTILL_AGENT failed userId=%s sessionId=%s: %s", user_id, session_id, e, exc_info=True)


def _serialize_messages(messages: list[BaseMessage]) -> str:
    """将 LangGraph 消息列表序列化为可读文本，供蒸馏 LLM 分析。"""
    parts: list[str] = []
    for msg in messages:
        if isinstance(msg, HumanMessage):
            parts.append(f"[用户]: {msg.content}")
        elif isinstance(msg, AIMessage):
            text = msg.content or ""
            tool_calls = getattr(msg, "tool_calls", [])
            if tool_calls:
                calls = ", ".join(
                    f"{tc['name']}({json.dumps(tc.get('args', {}), ensure_ascii=False)})"
                    for tc in tool_calls
                )
                text = (text + f"\n[调用工具]: {calls}").strip()
            if text:
                parts.append(f"[AI]: {text}")
        elif isinstance(msg, ToolMessage):
            # 只保留关键字段，避免噪音过多
            try:
                obs = json.loads(msg.content) if isinstance(msg.content, str) else msg.content
                summary = {
                    k: obs[k] for k in ("success", "message", "error", "data_type", "chain_length")
                    if k in obs
                }
                parts.append(f"[工具结果]: {json.dumps(summary, ensure_ascii=False)}")
            except Exception:
                parts.append(f"[工具结果]: {str(msg.content)[:200]}")
    return "\n".join(parts)


def _parse_memories(content: str) -> list[dict]:
    """从 LLM 输出中解析 JSON 记忆列表，容错处理。"""
    text = content.strip()
    # 去掉可能的代码块标记
    if text.startswith("```"):
        lines = text.split("\n")
        text = "\n".join(lines[1:-1] if lines[-1].strip() == "```" else lines[1:])
    try:
        result = json.loads(text)
        if isinstance(result, list):
            return [m for m in result if _is_valid_memory_candidate(m)]
    except json.JSONDecodeError:
        # 尝试找到第一个 [ ... ] 块
        start = text.find("[")
        end   = text.rfind("]")
        if start != -1 and end > start:
            try:
                result = json.loads(text[start:end + 1])
                if isinstance(result, list):
                    return [m for m in result if _is_valid_memory_candidate(m)]
            except json.JSONDecodeError:
                pass
    logger.warning("DISTILL_AGENT: failed to parse memories from LLM output")
    return []


def _is_valid_memory_candidate(item: object) -> bool:
    if not isinstance(item, dict):
        return False
    name = str(item.get("name") or "").strip()
    description = str(item.get("description") or "").strip()
    content = str(item.get("content") or "").strip()
    memory_type = str(item.get("type") or "").strip()
    scope = str(item.get("scope") or "").strip()
    if not name or len(name) > 12:
        return False
    if not description or len(description) < 4 or len(description) > 32:
        return False
    if not content or len(content) < 20:
        return False
    if memory_type not in {"user", "feedback", "project", "reference"}:
        return False
    if scope not in {"common", "session"}:
        return False
    generic_names = {"用户偏好", "项目背景", "会话记录", "参考信息"}
    if name in generic_names:
        return False
    if memory_type in {"feedback", "project"} and ("**Why:**" not in content or "**How to apply:**" not in content):
        return False
    return True
