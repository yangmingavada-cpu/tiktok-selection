import logging

from langchain_openai import ChatOpenAI

logger = logging.getLogger(__name__)

_RESERVED_KEYS = frozenset({"base_url", "api_key", "model", "max_tokens", "config_extra"})


def create_chat_llm(
    config: dict | None = None,
    *,
    temperature: float = 0.2,
    max_tokens: int | None = None,
) -> ChatOpenAI:
    """
    根据请求级配置创建 ChatOpenAI 实例。
    config 中 config_extra 的非保留字段会透传给 ChatOpenAI（如 proxy、自定义 headers）。
    """
    if not config:
        raise ValueError("LLM config is required (from database via Java backend)")
    cfg = config
    extra = {
        k: v
        for k, v in (cfg.get("config_extra") or {}).items()
        if k not in _RESERVED_KEYS and v is not None
    }
    return ChatOpenAI(
        base_url=cfg["base_url"],
        api_key=cfg["api_key"],
        model=cfg["model"],
        max_tokens=max_tokens or int(cfg.get("max_tokens") or 4096),
        temperature=temperature,
        **extra,
    )


def create_chat_llm_with_fallbacks(
    configs: list[dict],
    *,
    temperature: float = 0.2,
    max_tokens: int | None = None,
):
    """
    创建带 fallback 的 LLM 实例。
    configs[0] 为主配置，其余为备选；主模型失败时自动切换下一个。
    返回值兼容 ChatOpenAI 的 .ainvoke() / .astream() / .bind_tools() 等方法。
    """
    if not configs:
        raise ValueError("至少需要一个 LLM 配置")

    primary = create_chat_llm(configs[0], temperature=temperature, max_tokens=max_tokens)

    if len(configs) <= 1:
        return primary

    fallbacks = [
        create_chat_llm(cfg, temperature=temperature, max_tokens=max_tokens)
        for cfg in configs[1:]
    ]
    logger.info(
        "LLM fallback 已配置: primary=%s, fallbacks=%s",
        configs[0].get("model"),
        [c.get("model") for c in configs[1:]],
    )
    return primary.with_fallbacks(fallbacks)
