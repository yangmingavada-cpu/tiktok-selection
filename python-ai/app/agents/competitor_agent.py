"""
竞品洞察 Subagent：分析选品执行结果的竞争格局，流式输出 Markdown 报告。

设计原则：
- 纯函数入口（analyze_competitors），无副作用
- 异步流式输出，不阻塞执行流
- 若无选品结果则短路返回空迭代器
"""

import json
import logging
from collections.abc import AsyncIterator

from langchain_openai import ChatOpenAI

logger = logging.getLogger(__name__)

_COMPETITOR_SYSTEM_PROMPT = """你是一位跨境电商竞争格局分析专家。
用户会给你一批选品结果（已执行的积木链输出商品列表）和可选的选品规划参数。
请输出一份简洁的竞品洞察报告（Markdown 格式，约 400-600 字）。

## 报告结构（严格遵守）

### 🗺️ 品类与价格分布
（该批商品在细分品类和价格段上的分布情况，哪些区间最集中）

### 👑 头部卖家特征
（TOP 商品的卖家规模特征：是否有大卖家/品牌垄断，中小卖家有多少机会）

### 🔴/🟢 红海 vs 蓝海判断
（根据竞争密度、评论数、价格战程度，判断此品类/价格段竞争强度）

### 💡 对新卖家的建议
（基于以上分析，新卖家应聚焦哪个切入点，避开哪些陷阱）

## 写作要求
- 数字具体（如"价格集中在 $15-30，占比 60%"）
- 禁止出现字段名（如 total_sale_cnt）
- 语气专业简洁，像给老板做的一页纸汇报"""


async def analyze_competitors(
    selected_products: list[dict],
    selection_plan: dict | None,
    llm: ChatOpenAI,
) -> AsyncIterator[str]:
    """
    分析选品结果的竞争格局，流式输出 Markdown 报告。

    Args:
        selected_products: 执行后的商品列表（来自 block_chain 执行结果）
        selection_plan:    选品规划参数（可选，用于提供更准确的市场上下文）
        llm:               LLM 实例

    Yields:
        Markdown 报告的流式 token
    """
    if not selected_products:
        return

    # 截取前 50 条，避免 token 过多（竞品分析不需要全量数据）
    sample = selected_products[:50]
    products_json = json.dumps(sample, ensure_ascii=False, indent=2)

    plan_context = ""
    if selection_plan:
        plan_context = f"\n\n## 选品规划参数\n{json.dumps(selection_plan, ensure_ascii=False, indent=2)}"

    user_content = (
        f"## 选品结果（共 {len(selected_products)} 个商品，以下为前 {len(sample)} 条样本）\n"
        f"```json\n{products_json}\n```"
        f"{plan_context}"
    )

    try:
        async for chunk in llm.astream([
            {"role": "system", "content": _COMPETITOR_SYSTEM_PROMPT},
            {"role": "user",   "content": user_content},
        ]):
            content = chunk.content
            if content:
                yield content
    except Exception as e:
        logger.warning("analyze_competitors failed: %s", e)
        yield f"\n\n> ⚠️ 竞品分析生成失败：{e}"
