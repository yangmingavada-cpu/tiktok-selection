from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from langgraph.checkpoint.redis.aio import AsyncRedisSaver

from app.config import settings
from app.routers import intent, score, comment


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 初始化 Redis checkpoint，TTL 2小时后自动清理过期会话数据
    # Redis 不可用时降级为无持久化模式（checkpointer=None）
    try:
        async with AsyncRedisSaver.from_conn_string(
            settings.redis_url,
            ttl={"default_ttl": 7200},
        ) as checkpointer:
            await checkpointer.asetup()
            app.state.checkpointer = checkpointer
            yield
    except Exception as e:
        import logging
        logging.getLogger(__name__).warning("Redis checkpoint 初始化失败，降级为无持久化模式: %s", e)
        app.state.checkpointer = None
        yield


app = FastAPI(
    title="TikTok AI Selection - AI Service",
    version="0.1.0",
    description="MCP Client + ReAct Agent for intent parsing, scoring, and commenting",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(intent.router, prefix="/intent", tags=["Intent Parsing"])
app.include_router(score.router, prefix="/score", tags=["Scoring"])
app.include_router(comment.router, prefix="/comment", tags=["Comment"])


@app.get("/health")
async def health_check():
    return {"status": "ok", "service": "python-ai"}
