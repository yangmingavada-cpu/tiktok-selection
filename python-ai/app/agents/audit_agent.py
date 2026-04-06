"""
质量审计 Subagent：在 block_chain 执行前分析其结构合理性。

设计原则：
- 规则检查优先（快速、确定性），LLM 辅助深度分析
- 纯函数入口（audit_chain），无副作用
- 同步返回结果（调用方根据 pass/fail 决定是否继续执行）
"""

import json
import logging

from langchain_openai import ChatOpenAI

logger = logging.getLogger(__name__)

# 必须出现的 Block ID 前缀
_REQUIRED_BLOCK_PREFIXES = ("SOURCE",)
_OUTPUT_BLOCK_PREFIXES   = ("OUTPUT", "SORT", "LIMIT")

_AUDIT_SYSTEM_PROMPT = """你是一个积木链质量审计员。分析给定的 block_chain JSON，
从数据流、筛选合理性、评分配置、输出完整性四个维度评分（各 25 分，总分 100）。

评分规则：
1. 数据流（25分）：是否有 SOURCE → (FILTER) → (SCORE) → OUTPUT 的完整链路
2. 筛选合理性（25分）：FILTER 条件是否可能导致零结果（条件过多、阈值过高扣分）
3. 评分配置（25分）：SCORE 积木权重之和是否约等于 100，维度是否合理
4. 输出完整性（25分）：是否有明确的输出条数限制，estimatedRowCount 是否合理

返回 JSON（不加代码块标记）：
{
  "pass": true/false,
  "score": <整数 0-100>,
  "issues": ["问题1", "问题2"],
  "suggestions": ["建议1", "建议2"]
}

pass=true 要求 score >= 60 且无严重问题（零结果风险、无 SOURCE 块）。"""


async def audit_chain(block_chain: list[dict], llm: ChatOpenAI) -> dict:
    """
    执行前审计 block_chain。

    Args:
        block_chain: 积木链 JSON（list of block dicts）
        llm:         用于深度分析的 LLM 实例

    Returns:
        {
          "pass":        bool,
          "score":       int (0-100),
          "issues":      list[str],
          "suggestions": list[str],
        }
    """
    # ── 快速规则检查 ───────────────────────────────────────────────────────────
    block_ids = [str(b.get("blockId", "")).upper() for b in block_chain]
    issues: list[str] = []
    suggestions: list[str] = []

    has_source = any(bid.startswith("SOURCE") for bid in block_ids)
    has_output = any(
        bid.startswith(pfx) for bid in block_ids for pfx in _OUTPUT_BLOCK_PREFIXES
    )

    if not has_source:
        issues.append("积木链缺少 SOURCE 数据源块，无法获取商品数据")
        suggestions.append("请添加 SOURCE_PRODUCT_LIST 或 SOURCE_RANK_LIST 块作为起始节点")

    if not has_output:
        issues.append("积木链缺少输出/排序块，最终结果无限制")
        suggestions.append("添加 SORT 或 LIMIT 块指定输出商品数量")

    # 明显的严重问题直接 fail，不消耗 LLM token
    if not has_source:
        return {
            "pass": False,
            "score": 0,
            "issues": issues,
            "suggestions": suggestions,
        }

    # ── LLM 深度分析 ──────────────────────────────────────────────────────────
    try:
        chain_json = json.dumps(block_chain, ensure_ascii=False, indent=2)
        response = await llm.ainvoke([
            {"role": "system", "content": _AUDIT_SYSTEM_PROMPT},
            {"role": "user",   "content": f"待审计的积木链：\n{chain_json}"},
        ])
        result = _parse_audit_result(response.content)
        # 合并规则检查结果
        result["issues"]      = issues + result.get("issues", [])
        result["suggestions"] = suggestions + result.get("suggestions", [])
        if issues:
            result["pass"]  = False
            result["score"] = min(result.get("score", 50), 40)
        return result
    except Exception as e:
        logger.warning("audit_chain LLM failed, using rule-only result: %s", e)
        return {
            "pass":        len(issues) == 0,
            "score":       80 if len(issues) == 0 else 30,
            "issues":      issues,
            "suggestions": suggestions,
        }


def _parse_audit_result(content: str) -> dict:
    """解析 LLM 返回的 JSON，容错处理。"""
    try:
        text = content.strip()
        # 去掉可能存在的 ```json ``` 包裹
        if text.startswith("```"):
            text = text.split("```")[1]
            if text.startswith("json"):
                text = text[4:]
        data = json.loads(text.strip())
        return {
            "pass":        bool(data.get("pass", True)),
            "score":       int(data.get("score", 70)),
            "issues":      list(data.get("issues", [])),
            "suggestions": list(data.get("suggestions", [])),
        }
    except Exception:
        return {"pass": True, "score": 70, "issues": [], "suggestions": []}
