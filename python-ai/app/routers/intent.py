import asyncio
import json
import logging
import time
from datetime import datetime, timezone

import httpx
from fastapi import APIRouter, Request
from fastapi.responses import StreamingResponse
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from pydantic import BaseModel, Field

from app.schemas import LlmConfig
from app.agents.main_agent import AgentService
from app.agents.interpret_agent import InterpretService
from app.agents.distillation_agent import distill
from app.agents.audit_agent import audit_chain
from app.agents.competitor_agent import analyze_competitors
from app.services.memory_client import MemoryClient
from app.services.llm_factory import create_chat_llm, create_chat_llm_with_fallbacks
from app.agents.memory_agent import MemoryAgent
from app.config import settings

logger = logging.getLogger(__name__)

router = APIRouter()

# 后台任务引用集合，防止任务被 GC 提前回收
_background_tasks: set = set()


def _build_config_list(
    primary: LlmConfig | None,
    fallbacks: list[LlmConfig] | None,
) -> list[dict]:
    """将主配置 + 备选配置列表合并为 dict list，供 factory/client 使用。"""
    configs: list[dict] = []
    if primary:
        configs.append(primary.model_dump())
    if fallbacks:
        configs.extend(cfg.model_dump() for cfg in fallbacks)
    return configs


class IntentParseRequest(BaseModel):
    session_id: str = Field(..., description="构建会话ID（由Java生成的UUID）")
    agent_thread_id: str | None = Field(None, description="规划Agent线程ID；未传时默认等于session_id")
    user_id: str | None = Field(None, description="用户ID，用于记忆系统读写。不传则禁用记忆功能")
    user_text: str = Field(..., description="用户自然语言输入")
    conversation_summary: str | None = Field(None, description="当前规划线程的压缩摘要（checkpoint丢失或重建时使用）")
    session_context: dict | None = Field(None, description="增量模式时传入的当前Session状态")
    llm_config: LlmConfig | None = Field(None, description="LLM连接配置（由Java从DB读取后传入）")
    llm_config_fallbacks: list[LlmConfig] | None = Field(None, description="备选LLM配置列表（按优先级排序）")
    mcp_endpoint: str | None = Field(None, description="MCP Server的JSON-RPC端点地址")
    token_quota: int | None = Field(None, description="本次调用的Token预算上限（可选）")
    qa_history: list[dict] | None = Field(
        None,
        description="ask_user 多轮问答历史（Java 始终传入）。正常路径由 checkpoint 接管，"
                    "checkpoint 丢失时作为降级兜底用于恢复对话上下文",
    )


class IntentParseResponse(BaseModel):
    success: bool
    type: str | None = None
    agent_thread_id: str | None = None
    block_chain: list[dict] | None = None
    action: dict | None = None
    summary: str | None = None
    conversation_summary: str | None = None
    ambiguities: list[str] | None = None
    llm_tokens_used: int = 0
    usage: dict | None = None
    iterations: int = 0
    message: str | None = None
    suggestions: list[str] | None = None
    plan: dict | None = None


@router.post("/parse", response_model=IntentParseResponse)
async def parse_intent(request: IntentParseRequest, req: Request):
    """
    解析用户意图，通过ReAct Agent + MCP构建block_chain。
    会话状态通过 Redis checkpoint 持久化，多轮对话自动续接。
    Java调用此接口，传入llm_config和mcp_endpoint。
    """
    configs = _build_config_list(request.llm_config, request.llm_config_fallbacks)

    # 创建 Memory Agent（独立子 Agent，管理记忆读写检索）
    memory_agent = None
    if request.user_id:
        memory_agent = MemoryAgent(
            memory_client=MemoryClient(user_id=request.user_id, agent_type="memory"),
            llm=create_chat_llm_with_fallbacks(configs, temperature=0.1, max_tokens=500),
            agent_thread_id=request.agent_thread_id or request.session_id,
            user_id=request.user_id,
        )

    agent_service = AgentService(
        llm_configs=configs,
        mcp_base_url=request.mcp_endpoint,
        checkpointer=req.app.state.checkpointer,
        memory_agent=memory_agent,
    )

    result = await agent_service.parse_intent(
        session_id=request.session_id,
        agent_thread_id=request.agent_thread_id,
        user_id=request.user_id,
        user_text=request.user_text,
        conversation_summary=request.conversation_summary,
        session_context=request.session_context,
        token_quota=request.token_quota,
        qa_history=request.qa_history,
    )
    return result


class InterpretRequest(BaseModel):
    block_chain: list[dict] = Field(..., description="待解读的积木链")
    llm_config: LlmConfig | None = Field(None, description="LLM连接配置")
    llm_config_fallbacks: list[LlmConfig] | None = Field(None, description="备选LLM配置列表")
    token_quota: int | None = Field(None, description="方案解读阶段的Token预算上限（可选）")
    user_id: str | None = Field(None, description="用户ID，用于记忆蒸馏（可选）")
    session_id: str | None = Field(None, description="会话ID，用于记忆蒸馏（可选）")
    agent_thread_id: str | None = Field(None, description="规划Agent线程ID，用于读取checkpoint历史（可选）")
    user_memories: str | None = Field(None, description="用户画像记忆文本（memoryType=user），用于个性化报告风格")


class InterpretResponse(BaseModel):
    success: bool
    interpretation: str = ""
    llm_tokens_used: int = 0
    message: str | None = None


@router.post("/interpret", response_model=InterpretResponse)
async def interpret_block_chain(request: InterpretRequest):
    """
    将 block chain 解读为用户友好的 Markdown 方案报告（非流式，保留兼容）。
    """
    service = InterpretService(
        llm_configs=_build_config_list(request.llm_config, request.llm_config_fallbacks),
        token_quota=request.token_quota,
    )
    return await service.interpret(request.block_chain)


@router.post("/interpret-stream")
async def interpret_block_chain_stream(request: InterpretRequest, req: Request):
    """
    流式解读积木链：逐 token 返回 SSE 事件，前端实时渲染 Markdown。
    解读完成后，若提供了 user_id 和 session_id，会触发蒸馏 Agent 提取记忆。
    """
    service = InterpretService(
        llm_configs=_build_config_list(request.llm_config, request.llm_config_fallbacks),
        token_quota=request.token_quota,
    )

    async def event_generator():
        interpretation_tokens = []
        try:
            async for token in service.interpret_stream(request.block_chain, request.user_memories):
                interpretation_tokens.append(token)
                yield f"data: {json.dumps({'token': token}, ensure_ascii=False)}\n\n"
            yield f"data: {json.dumps({'done': True})}\n\n"

            # 流式完成后，保存会话快照（蒸馏已改为 Java 调 /distillation 端点触发）
            logger.info("INTERPRET_STREAM_DONE userId=%s sessionId=%s", request.user_id, request.session_id)
            if request.user_id and request.session_id and request.agent_thread_id:
                interpretation_text = "".join(interpretation_tokens)
                await _save_interpretation_snapshot(
                    interpretation=interpretation_text,
                    block_chain=request.block_chain,
                    user_id=request.user_id,
                    session_id=request.session_id,
                    agent_thread_id=request.agent_thread_id,
                    checkpointer=req.app.state.checkpointer,
                )
        except Exception as e:
            yield f"data: {json.dumps({'error': str(e)}, ensure_ascii=False)}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")


async def _read_checkpoint_state(checkpointer, thread_id: str):
    """从 checkpoint 读取 AgentState，使用最小合法图（LangGraph 要求至少有一个 START 边）。"""
    from langgraph.graph import StateGraph, START, END
    from app.agents.main_agent import AgentState

    graph = StateGraph(AgentState)
    graph.add_node("_noop", lambda s: s)
    graph.add_edge(START, "_noop")
    graph.add_edge("_noop", END)
    compiled = graph.compile(checkpointer=checkpointer)
    return await compiled.aget_state({"configurable": {"thread_id": thread_id}})


async def _save_interpretation_snapshot(
    interpretation: str,
    block_chain: list[dict],
    user_id: str,
    session_id: str,
    agent_thread_id: str,
    checkpointer,
) -> None:
    """
    解读完成后，从 checkpoint 读取对话历史并保存会话快照到 PostgreSQL。
    蒸馏已改为 Java 调用 /intent/distillation 端点独立触发。
    """
    try:
        state = await _read_checkpoint_state(checkpointer, agent_thread_id)

        if not state or not state.values:
            logger.warning("Snapshot skipped: checkpoint not found userId=%s thread=%s", user_id, agent_thread_id)
            return

        await _save_conversation_snapshot(
            session_id=session_id,
            interpretation=interpretation,
            block_chain=block_chain,
            state=state,
        )
    except Exception as e:
        logger.error("Save interpretation snapshot failed: %s", e, exc_info=True)


def _generate_planning_summary(qa_history: list[dict], block_chain: list[dict]) -> str:
    """
    生成规划摘要文本
    """
    parts = []

    # 从 QA 提取关键信息
    for qa in qa_history:
        q = qa.get("q", "")
        a = qa.get("a", "")
        if q in ["选品目标", "投放周期", "目标人群", "预算范围"]:
            parts.append(a)

    # 添加方案规模
    if block_chain:
        parts.append(f"{len(block_chain)}个选品方案")

    return " | ".join(parts) if parts else "选品方案"


async def _save_conversation_snapshot(
    session_id: str,
    interpretation: str,
    block_chain: list[dict],
    state,
) -> None:
    """
    保存会话快照到 PostgreSQL
    """
    try:
        # 1. 从 AgentState 提取数据
        messages = list(state.values.get("messages") or [])
        qa_history = list(state.values.get("qa_history") or [])

        # 2. 构建前端需要的消息格式
        formatted_messages = []
        for msg in messages:
            if isinstance(msg, HumanMessage):
                formatted_messages.append({
                    "role": "user",
                    "text": msg.content,
                    "timestamp": datetime.now(timezone.utc).isoformat()
                })
            elif isinstance(msg, AIMessage):
                formatted_messages.append({
                    "role": "ai",
                    "text": msg.content,
                    "timestamp": datetime.now(timezone.utc).isoformat()
                })

        # 3. 如果最后一条消息包含方案，添加额外字段
        if block_chain and formatted_messages:
            last_msg = formatted_messages[-1]
            if last_msg["role"] == "ai":
                last_msg["plan"] = block_chain
                last_msg["interpretation"] = interpretation

        # 4. 生成规划摘要
        planning_summary = _generate_planning_summary(qa_history, block_chain)

        # 5. 构建快照对象
        snapshot = {
            "messages": formatted_messages,
            "qaHistory": [{"q": item.get("q", ""), "a": item.get("a", "")} for item in qa_history],
            "planningSummary": planning_summary,
            "savedAt": datetime.now(timezone.utc).isoformat()
        }

        # 6. 调用 Java API 保存
        java_api_url = f"{settings.java_backend_url}/api/sessions/{session_id}/conversation-snapshot"
        async with httpx.AsyncClient() as client:
            response = await client.post(
                java_api_url,
                json=snapshot,
                headers={"Content-Type": "application/json"},
                timeout=10.0
            )
            response.raise_for_status()

        logger.info(f"会话快照已保存: session_id={session_id}, messages={len(formatted_messages)}")

    except Exception as e:
        logger.error(f"保存会话快照失败: session_id={session_id}, error={e}", exc_info=True)
        # 不抛出异常，避免影响主流程


# ─── 新端点：独立蒸馏 / 审计 / 竞品洞察 ──────────────────────────────────────────


class DistillationRequest(BaseModel):
    user_id: str = Field(..., description="用户ID")
    session_id: str = Field(..., description="会话ID")
    agent_thread_id: str = Field(..., description="规划Agent checkpoint 线程ID")
    block_chain: list[dict] | None = Field(None, description="积木链（追加到对话上下文，可选）")
    llm_config: LlmConfig | None = Field(None, description="LLM连接配置")
    llm_config_fallbacks: list[LlmConfig] | None = Field(None, description="备选LLM配置列表")


@router.post("/distillation", status_code=202)
async def trigger_distillation(request: DistillationRequest, req: Request):
    """
    火花即忘式蒸馏触发端点（Java 执行完成后调用）。
    从 checkpoint 读取完整对话历史，提取跨会话记忆写入记忆系统。
    立即返回 202，蒸馏异步执行，不阻塞响应。
    """
    async def _run():
        try:
            state = await _read_checkpoint_state(req.app.state.checkpointer, request.agent_thread_id)
            if not state or not state.values:
                logger.warning("Distillation skipped: checkpoint not found thread=%s", request.agent_thread_id)
                return

            messages = list(state.values.get("messages") or [])
            if request.block_chain:
                messages.append(AIMessage(
                    content=f"积木链已提交执行（{len(request.block_chain)} 个积木块）"
                ))

            configs = _build_config_list(request.llm_config, request.llm_config_fallbacks)
            llm = create_chat_llm_with_fallbacks(configs, temperature=0.2, max_tokens=2000) if configs else create_chat_llm(None)
            memory_client = MemoryClient(user_id=request.user_id, agent_type="distillation")
            await distill(
                messages=messages,
                user_id=request.user_id,
                session_id=request.session_id,
                memory_client=memory_client,
                llm=llm,
                already_written_names=state.values.get("written_memory_names") or [],
            )
            logger.info("DISTILLATION_DONE userId=%s sessionId=%s", request.user_id, request.session_id)
        except Exception as e:
            logger.error("Distillation failed userId=%s: %s", request.user_id, e, exc_info=True)

    task = asyncio.create_task(_run())
    _background_tasks.add(task)
    task.add_done_callback(_background_tasks.discard)
    return {"status": "accepted"}


class AuditRequest(BaseModel):
    block_chain: list[dict] = Field(..., description="待审计的积木链")
    llm_config: LlmConfig | None = Field(None, description="LLM连接配置")
    llm_config_fallbacks: list[LlmConfig] | None = Field(None, description="备选LLM配置列表")


@router.post("/audit")
async def audit_block_chain(request: AuditRequest):
    """
    积木链执行前质量审计。
    返回 pass/fail + score + issues + suggestions。
    pass=false 时建议让用户决定是否继续执行。
    """
    configs = _build_config_list(request.llm_config, request.llm_config_fallbacks)
    llm = create_chat_llm_with_fallbacks(configs, temperature=0.1, max_tokens=800)
    result = await audit_chain(request.block_chain, llm)
    return result


class CompetitorRequest(BaseModel):
    selected_products: list[dict] = Field(..., description="执行结果商品列表")
    selection_plan: dict | None = Field(None, description="选品规划参数（可选）")
    llm_config: LlmConfig | None = Field(None, description="LLM连接配置")
    llm_config_fallbacks: list[LlmConfig] | None = Field(None, description="备选LLM配置列表")


@router.post("/competitor-stream")
async def competitor_stream(request: CompetitorRequest):
    """
    竞品洞察流式端点：分析选品结果的竞争格局，逐 token 返回 SSE 事件。
    """
    configs = _build_config_list(request.llm_config, request.llm_config_fallbacks)
    llm = create_chat_llm_with_fallbacks(configs, temperature=0.4, max_tokens=1200)

    async def event_generator():
        try:
            async for token in analyze_competitors(request.selected_products, request.selection_plan, llm):
                yield f"data: {json.dumps({'token': token}, ensure_ascii=False)}\n\n"
            yield f"data: {json.dumps({'done': True})}\n\n"
        except Exception as e:
            yield f"data: {json.dumps({'error': str(e)}, ensure_ascii=False)}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")


# ─── LLM 配置测试端点 ────────────────────────────────────────────────────────


class TestLlmRequest(BaseModel):
    llm_config: LlmConfig = Field(..., description="要测试的LLM配置")


class TestLlmResponse(BaseModel):
    success: bool
    latency_ms: int = 0
    model: str = ""
    message: str = ""


@router.post("/test-llm", response_model=TestLlmResponse)
async def test_llm(request: TestLlmRequest):
    """测试单个LLM配置是否可用：发送最小请求，返回成功/失败 + 延迟。"""
    cfg = request.llm_config.model_dump()
    model = cfg.get("model", "")
    start = time.monotonic()
    try:
        llm = create_chat_llm(cfg, temperature=0, max_tokens=10)
        await llm.ainvoke([HumanMessage(content="hi")])
        latency = int((time.monotonic() - start) * 1000)
        return TestLlmResponse(success=True, latency_ms=latency, model=model, message="OK")
    except Exception as e:
        latency = int((time.monotonic() - start) * 1000)
        return TestLlmResponse(success=False, latency_ms=latency, model=model, message=str(e))

