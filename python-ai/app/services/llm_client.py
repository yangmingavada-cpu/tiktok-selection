import json
import logging
import re
from typing import Any

from openai import AsyncOpenAI

logger = logging.getLogger(__name__)

_PERSONA_PROMPTS: dict[str, str] = {
    "cross_border_analyst": (
        "You are a TikTok cross-border e-commerce product analyst. "
        "Focus on market potential, trending appeal, and profit margin."
    ),
    "lifestyle_creator": (
        "You are a lifestyle content creator for TikTok. "
        "Focus on emotional resonance, storytelling appeal, and audience engagement."
    ),
    "bargain_hunter": (
        "You are a deal-oriented shopping advisor. "
        "Focus on value for money, price competitiveness, and practical utility."
    ),
}
_DEFAULT_PERSONA = "cross_border_analyst"


class LLMClient:
    """
    OpenAI兼容的LLM客户端，支持多配置 fallback。
    configs[0] 为主配置，其余为备选；主模型失败时自动切换下一个。
    """

    def __init__(self, configs: list[dict]):
        if not configs:
            raise ValueError("至少需要一个 LLM 配置")
        self._configs = configs
        self._init_client(configs[0])

    def _init_client(self, cfg: dict):
        self._model = cfg["model"]
        self._max_tokens = int(cfg.get("max_tokens") or 4096)
        self._client = AsyncOpenAI(base_url=cfg["base_url"], api_key=cfg["api_key"])

    async def chat(
        self,
        messages: list[dict],
        tools: list[dict] | None = None,
        temperature: float = 0.2,
    ) -> dict:
        """发送Chat Completion请求，支持多配置 fallback。"""
        last_error: Exception | None = None
        for i, cfg in enumerate(self._configs):
            if i > 0:
                logger.warning("LLMClient fallback → config #%d model=%s", i, cfg.get("model"))
                self._init_client(cfg)
            try:
                return await self._do_chat(messages, tools, temperature)
            except Exception as e:
                logger.warning("LLMClient config #%d model=%s 失败: %s", i, cfg.get("model"), e)
                last_error = e
        raise last_error  # type: ignore[misc]

    async def _do_chat(
        self,
        messages: list[dict],
        tools: list[dict] | None,
        temperature: float,
    ) -> dict:
        """实际执行单次 Chat Completion 请求。"""
        kwargs: dict[str, Any] = {
            "model": self._model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": self._max_tokens,
        }
        if tools:
            kwargs["tools"] = tools
            kwargs["tool_choice"] = "auto"

        response = await self._client.chat.completions.create(**kwargs)
        choice = response.choices[0]

        return {
            "content": choice.message.content,
            "tool_calls": [
                {
                    "id": tc.id,
                    "function": {
                        "name": tc.function.name,
                        "arguments": tc.function.arguments,
                    },
                }
                for tc in (choice.message.tool_calls or [])
            ],
            "usage": {
                "prompt_tokens": response.usage.prompt_tokens,
                "completion_tokens": response.usage.completion_tokens,
                "total_tokens": response.usage.total_tokens,
            },
        }

    async def semantic_score(
        self,
        products: list[dict],
        criteria: str,
        max_score: int = 100,
    ) -> dict:
        """LLM语义评分，每批≤5个商品。"""
        system_prompt = (
            f"You are a cross-border e-commerce product scoring expert. "
            f"Score each product from 0-{max_score} based on the given criteria. "
            f'Return a JSON array: [{{"index": 0, "score": N, "reason": "..."}}]'
        )
        user_content = (
            f"Scoring criteria: {criteria}\n\n"
            f"Products:\n{json.dumps(products, ensure_ascii=False, indent=2)}"
        )

        try:
            result = await self.chat(
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_content},
                ],
                temperature=0.2,
            )
            scores = _extract_json(result["content"] or "[]")
            return {
                "success": True,
                "results": scores,
                "usage": result["usage"],
                "llm_tokens_used": result["usage"]["total_tokens"],
            }
        except Exception as e:
            logger.error("Semantic scoring failed: %r", e)
            return {"success": False, "message": repr(e), "llm_tokens_used": 0}

    async def generate_comments(
        self,
        products: list[dict],
        language: str = "zh",
        max_chars: int = 100,
        persona: str = _DEFAULT_PERSONA,
    ) -> dict:
        """LLM评语生成，每批≤20个商品。"""
        lang_note = "Chinese" if language == "zh" else "English"
        persona_prompt = _PERSONA_PROMPTS.get(persona, _PERSONA_PROMPTS[_DEFAULT_PERSONA])
        system_prompt = (
            f"{persona_prompt} "
            f"Write a concise product selection comment in {lang_note} for each product. "
            f"Each comment must be within {max_chars} characters. "
            f'Return JSON array: [{{"index": 0, "comment": "..."}}]'
        )
        user_content = f"Products:\n{json.dumps(products, ensure_ascii=False, indent=2)}"

        try:
            result = await self.chat(
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_content},
                ],
                temperature=0.7,
            )
            comments = _extract_json(result["content"] or "[]")
            return {
                "success": True,
                "results": comments,
                "usage": result["usage"],
                "llm_tokens_used": result["usage"]["total_tokens"],
            }
        except Exception as e:
            logger.error("Comment generation failed: %r", e)
            return {"success": False, "message": repr(e), "llm_tokens_used": 0}


def _extract_json(text: str) -> list:
    """容错JSON提取：先直接解析，失败则找数组子串，再尝试去尾逗号。"""
    text = text.strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass
    start = text.find("[")
    end = text.rfind("]")
    if start != -1 and end != -1 and end > start:
        candidate = text[start: end + 1]
        try:
            return json.loads(candidate)
        except json.JSONDecodeError:
            candidate = re.sub(r",\s*([}\]])", r"\1", candidate)
            try:
                return json.loads(candidate)
            except json.JSONDecodeError:
                pass
    logger.warning("Failed to extract JSON from LLM response: %s", text[:200])
    return []
