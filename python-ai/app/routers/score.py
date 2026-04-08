from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.schemas import LlmConfig
from app.services.llm_client import LLMClient

router = APIRouter()


class ScoreEvaluateRequest(BaseModel):
    products: list[dict] = Field(..., description="商品数据列表（最多5个）", max_length=5)
    eval_prompt: str = Field(..., description="评分维度描述")
    max_score: int = Field(100, description="满分值")
    llm_config: LlmConfig | None = Field(None, description="LLM配置（由Java传入）")
    llm_config_fallbacks: list[LlmConfig] | None = Field(None, description="备选LLM配置列表")


class ScoreEvaluateResponse(BaseModel):
    success: bool
    results: list[dict] | None = None
    usage: dict | None = None
    llm_tokens_used: int = 0
    message: str | None = None


@router.post("/evaluate", response_model=ScoreEvaluateResponse)
async def evaluate_score(request: ScoreEvaluateRequest):
    """LLM语义评分（每次最多5个商品）"""
    configs = []
    if request.llm_config:
        configs.append(request.llm_config.model_dump())
    if request.llm_config_fallbacks:
        configs.extend(cfg.model_dump() for cfg in request.llm_config_fallbacks)
    llm_client = LLMClient(configs=configs)
    result = await llm_client.semantic_score(
        products=request.products,
        criteria=request.eval_prompt,
        max_score=request.max_score,
    )
    return result
