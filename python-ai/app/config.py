from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # Java Backend
    java_backend_url: str = "http://localhost:8080"

    # MCP Server
    mcp_server_url: str = "http://localhost:8080/mcp/jsonrpc"
    # MCP 内部认证 Token，须与 Java 端 MCP_INTERNAL_TOKEN 环境变量保持一致
    mcp_internal_token: str = ""

    # Redis（用于 LangGraph checkpoint）
    redis_url: str = "redis://localhost:6379"

    # Server
    host: str = "0.0.0.0"
    port: int = 8000

    class Config:
        env_file = ".env"


settings = Settings()
