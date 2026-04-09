from pydantic import BaseModel


class LlmConfig(BaseModel):
    """LLM 连接配置（由 Java 从 DB 读取后传入）。所有路由共用。"""
    base_url: str = "https://api.openai.com/v1"
    api_key: str = ""
    model: str = "gpt-4o-mini"
    max_tokens: int = 4096
    context_window: int = 128000
    compact_message_limit: int = 48
    compact_char_limit: int = 18000
    config_extra: dict | None = None
