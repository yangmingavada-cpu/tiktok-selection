import json
import logging
from collections.abc import AsyncIterator

from langchain_core.messages import HumanMessage, SystemMessage

from app.services.llm_factory import create_chat_llm_with_fallbacks

logger = logging.getLogger(__name__)

_SYSTEM_PROMPT = """你是跨境电商选品方案的解读专家，帮助普通卖家理解AI为他们设计的选品方案。

用户会给你一段JSON格式的选品方案配置，你需要用 **Markdown格式** 输出一份完整的方案解读报告。

## 输出结构（严格遵守）

## 🎯 方案概述
（1-2句话，说明这个方案要解决什么选品问题，面向什么市场）

## 🔄 执行流程详解

### 第N步：[带小表情的步骤标题]
- **做什么**：（这步的具体操作）
- **为什么**：（这样设计的原因）
- **预期结果**：（执行完后大概有多少数据/会产生什么变化）

（每个步骤都按上面格式输出）

## 🎯 选品策略特点
（用2-3个小节说明这个方案的设计思路和优势，如平衡性、风险控制、数据驱动等）

## 🎁 最终你会得到什么
（具体描述最终结果：数量、质量标准、包含哪些信息）

---
> 💡 如需调整筛选条件、评分权重、目标市场或商品数量，直接告诉AI即可修改方案。

## 写作要求
- 使用 Markdown 语法（##标题、###子标题、**粗体**、列表、引用块）
- 每个步骤标题带贴切的小表情（📊🔍📈⭐🏆💬🛒✨📉🔢🤖等）
- **禁止出现技术字段名**（如 total_sale_cnt、product_rank_field、tier_map）
- **禁止出现积木块ID**（如 SOURCE_PRODUCT_LIST、FILTER_CONDITION、SCORE_NUMERIC）
- 数字和条件要具体（如"近30天销量低于10件"，不能说"销量较低"）
- 如果有评分权重，要换算成百分比并解释占比逻辑
- 语气专业但友好，像一位有经验的选品顾问在为卖家解释方案"""


class InterpretService:
    """将 block chain 配置调用 LLM 转换为 Markdown 格式的方案解读报告。"""

    def __init__(self, llm_configs: list[dict] | None = None, token_quota: int | None = None):
        max_tokens = 3000
        if token_quota and token_quota > 0:
            max_tokens = min(max_tokens, token_quota)
        self._token_quota = token_quota
        self._llm = create_chat_llm_with_fallbacks(llm_configs or [], temperature=0.3, max_tokens=max_tokens)

    def _build_messages(self, block_chain: list[dict], user_memories: str | None = None) -> list:
        chain_json = json.dumps(block_chain, ensure_ascii=False, indent=2)
        messages = []
        if user_memories:
            messages.append(SystemMessage(content=
                f"## 用户背景（据此调整报告风格和深度）\n{user_memories}"
            ))
        messages.append(SystemMessage(content=_SYSTEM_PROMPT))
        messages.append(HumanMessage(
            content=f"请为以下选品方案配置生成解读报告：\n\n```json\n{chain_json}\n```"
        ))
        return messages

    async def interpret(self, block_chain: list[dict], user_memories: str | None = None) -> dict:
        """非流式：调用 LLM 将 block chain 解读为 Markdown 格式的方案报告。"""
        try:
            messages = self._build_messages(block_chain, user_memories)
            response = await self._llm.ainvoke(messages)
            interpretation = response.content or ""
            tokens = (response.usage_metadata or {}).get("total_tokens", 0)
            if self._token_quota and tokens >= self._token_quota:
                return {
                    "success": False,
                    "interpretation": "",
                    "llm_tokens_used": tokens,
                    "message": f"方案解读超出安全预算（{tokens}/{self._token_quota}）",
                }
            return {"success": True, "interpretation": interpretation, "llm_tokens_used": tokens}
        except Exception as e:
            logger.warning("InterpretService failed: %s", e)
            return {"success": False, "interpretation": "", "message": str(e)}

    async def interpret_stream(
        self, block_chain: list[dict], user_memories: str | None = None
    ) -> AsyncIterator[str]:
        """流式：逐 token 产出 Markdown 解读内容。"""
        messages = self._build_messages(block_chain, user_memories)
        async for chunk in self._llm.astream(messages):
            content = chunk.content
            if content:
                yield content
