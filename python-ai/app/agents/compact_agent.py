"""
Compact Agent：当规划线程历史过长时，生成一份结构化摘要，供后续轮次替代旧消息上下文。

设计目标：
- 不写记忆文件，不污染长期记忆
- 只服务当前规划线程的上下文压缩
- 输出稳定、简短、面向后续 Agent 继续执行
"""

from __future__ import annotations

import logging

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_openai import ChatOpenAI

logger = logging.getLogger(__name__)

_COMPACT_SYSTEM_PROMPT = """你是规划会话压缩子 Agent。你的任务不是回答用户问题，而是把一段很长的 AI 选品规划历史压缩成一份可继续执行的工作摘要。

请输出简洁的 Markdown，严格包含以下 6 个部分：
1. 用户目标
2. 已确认条件
3. 未确认条件
4. 已完成动作
5. 当前积木链状态
6. 下一步建议

要求：
- 只保留后续规划真正需要的信息
- 不要重复工具原始 JSON、blockId、字段名等底层细节
- 如果历史里有 ask_user 追问和用户回答，要明确哪些问题已经确认
- 如果历史里有“结果为空”“条件过严”之类信号，要在摘要里写出
- 总长度尽量控制在 600 中文字以内
"""


async def compact_history(
    messages: list[BaseMessage],
    llm: ChatOpenAI,
    *,
    user_text: str,
    chain_snapshot: dict | None = None,
    prior_summary: str | None = None,
) -> str:
    """将长历史压缩为继续规划可用的摘要。失败时退回到 prior_summary 或空串。"""
    try:
        history = _serialize_messages(messages)
        snapshot_text = ""
        if chain_snapshot:
            snapshot_text = (
                "\n当前链路快照：\n"
                f"- block 数量：{len(chain_snapshot.get('blockChain', []))}\n"
                f"- 当前输出类型：{chain_snapshot.get('currentOutputType', '未知')}\n"
                f"- 可用字段：{chain_snapshot.get('availableFields', [])[:12]}"
            )
        prior = f"\n已有压缩摘要（若有价值请继承）：\n{prior_summary}\n" if prior_summary else ""
        prompt = (
            f"当前用户输入：{user_text}\n"
            f"{prior}"
            f"\n历史消息：\n{history}\n"
            f"{snapshot_text}"
        )
        response = await llm.ainvoke([
            {"role": "system", "content": _COMPACT_SYSTEM_PROMPT},
            {"role": "user", "content": prompt},
        ])
        content = (response.content or "").strip()
        if content:
            return content[:4000]
    except Exception as exc:
        logger.warning("Compact agent failed: %s", exc)
    return (prior_summary or "").strip()


def _serialize_messages(messages: list[BaseMessage]) -> str:
    parts: list[str] = []
    for msg in messages:
        if isinstance(msg, SystemMessage):
            continue
        if isinstance(msg, HumanMessage):
            parts.append(f"[用户] {msg.content}")
            continue
        if isinstance(msg, AIMessage):
            content = (msg.content or "").strip()
            if content:
                parts.append(f"[AI] {content[:800]}")
            tool_calls = getattr(msg, "tool_calls", None) or []
            if tool_calls:
                names = ", ".join(tc.get("name", "") for tc in tool_calls[:8])
                parts.append(f"[AI工具调用] {names}")
            continue
        if isinstance(msg, ToolMessage):
            text = str(msg.content or "").strip()
            if text:
                parts.append(f"[工具结果] {text[:800]}")
    return "\n".join(parts[-60:])
