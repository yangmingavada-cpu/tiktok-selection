from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.schemas import LlmConfig
from app.services.llm_client import LLMClient

router = APIRouter()


class CommentConfig(BaseModel):
    max_chars: int = 100
    language: str = "zh"
    persona: str = "cross_border_analyst"


class CommentGenerateRequest(BaseModel):
    products: list[dict] = Field(..., description="商品数据列表（最多20个）", max_length=20)
    config: CommentConfig | None = Field(None, description="评语配置")
    llm_config: LlmConfig | None = Field(None, description="LLM配置（由Java传入）")
    llm_config_fallbacks: list[LlmConfig] | None = Field(None, description="备选LLM配置列表")


class CommentGenerateResponse(BaseModel):
    success: bool
    results: list[dict] | None = None
    usage: dict | None = None
    llm_tokens_used: int = 0
    message: str | None = None


@router.post("/generate", response_model=CommentGenerateResponse)
async def generate_comment(request: CommentGenerateRequest):
    """LLM评语生成（每次最多20个商品）"""
    configs = []
    if request.llm_config:
        configs.append(request.llm_config.model_dump())
    if request.llm_config_fallbacks:
        configs.extend(cfg.model_dump() for cfg in request.llm_config_fallbacks)
    llm_client = LLMClient(configs=configs)
    comment_cfg = request.config or CommentConfig()
    result = await llm_client.generate_comments(
        products=request.products,
        language=comment_cfg.language,
        max_chars=comment_cfg.max_chars,
        persona=comment_cfg.persona,
    )
    return result
