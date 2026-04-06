from langchain_openai import ChatOpenAI

from app.config import settings

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
    cfg = config or {}
    extra = {
        k: v
        for k, v in (cfg.get("config_extra") or {}).items()
        if k not in _RESERVED_KEYS and v is not None
    }
    return ChatOpenAI(
        base_url=cfg.get("base_url") or settings.llm_base_url,
        api_key=cfg.get("api_key") or settings.llm_api_key,
        model=cfg.get("model") or settings.llm_model,
        max_tokens=max_tokens or int(cfg.get("max_tokens") or 4096),
        temperature=temperature,
        **extra,
    )
