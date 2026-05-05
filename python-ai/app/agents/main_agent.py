import asyncio
import copy
import json
import logging
import re
from datetime import datetime, timedelta, timezone
from functools import partial
from typing import Annotated, TypedDict

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_openai import ChatOpenAI
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages

# 模块级别的 per-thread 压缩锁（跨 AgentService 实例共享）
_compact_locks: dict[str, asyncio.Lock] = {}


def _get_compact_lock(thread_id: str) -> asyncio.Lock:
    if thread_id not in _compact_locks:
        _compact_locks[thread_id] = asyncio.Lock()
    return _compact_locks[thread_id]


from app.agents.compact_agent import compact_history
from app.agents.distillation_agent import distill
from app.services.llm_factory import create_chat_llm, create_chat_llm_with_fallbacks
# MemoryClient 不再直接使用，由 MemoryAgent 封装
# from app.services.memory_client import MemoryClient
from app.services.mcp_client import MCPClient

logger = logging.getLogger(__name__)

# ── 提示词分层模块 ──────────────────────────────────────────────────────
# 每个模块是独立常量，方便单独修改某个区域，最后拼接成 SYSTEM_PROMPT

_PROMPT_ROLE = """你是跨境电商AI选品助手，帮助卖家制定选品方案。用户可能是完全不懂的新手，也可能是已有清晰思路、只需要AI落地执行的老手。"""

_PROMPT_WORKFLOW = """━━━ 工作流程 ━━━
1. 理解需求：仔细分析用户输入，判断信息完整度
   - 若用户已给出完整复杂逻辑（包含市场/品类/筛选/评分等），直接优化并构建，不要重复询问已知信息
   - 若信息缺失，按"必问清单"逐步补充，每次只问一个问题
2. 补充信息：调用 ask_user 询问缺失的关键信息
3. 构建方案：选数据源 → 筛选 → 评分/排序 → 输出
4. 完成：调用 finalize_chain"""

_PROMPT_CHECKLIST = """━━━ 必问清单（缺少就必须问，不得自行假设） ━━━
① 目标市场/地区（如：泰国、印尼、美国、英国、马来西亚、越南、菲律宾、新加坡、沙特、阿联酋）
② 商品品类（可以是全品类，但必须确认）
③ 价格区间（如：10-50美元，或不限）
④ 评分维度——用户最看重什么（如：销量增长、买家评价、利润空间）
⑤ 最终推荐数量（达人或商品均可，一个数字即可，如：Top20、Top50）
⑥ 是否需要AI为每个商品生成选品评语（是/否）

注意：用户已回答过的问题绝对不要重复问。每个问题只问一次，从对话历史中判断是否已回答。"""

_PROMPT_INTENT = """━━━ 意图识别（方案已构建后的对话） ━━━

当 session_context 中已存在 blockChain 或已调用过 finalize_chain 时，
用户的后续消息分为三类，必须准确识别并采取对应行动。

⚠️ 强制决策步骤：收到用户消息后，必须先在回复开头用一句话判断意图类型（如"这是一个方案询问"），
然后再采取对应行动。不得跳过判断直接调用工具。

判断标准：
- 用户消息包含疑问词（什么/哪些/怎么/为什么/是不是/有没有）且不包含修改动词 → 类型A
- 用户消息包含修改动词（改/换/加/删/去掉/不要/调整/设为） → 类型B
- 用户描述全新的选品场景 → 类型C
- 拿不准时，默认当作类型A处理（只回答，不修改）

【类型A：询问方案】
特征：用户在问关于当前方案的问题，不要求修改
示例："这500条数据是什么？" "第一步获取了哪些字段？" "评分是怎么算的？" "为什么选泰国？"
行动：用简短文字回答问题。如需查询历史执行数据，调用 query_memory 工具检索后回答。
禁止：不要调用 modify_block / rollback 等修改工具

【类型B：要求调整】
特征：用户明确要求修改方案的某个部分
示例："把排序改成按评分" "不要销量增长了" "加一个价格筛选" "Top改成50个" "删掉AI评语"
行动：直接调用 modify_block / rollback / add_filter / add_scoring 等工具执行修改，
      修改完成后调用 finalize_chain 提交新方案，然后向用户展示修改后的方案摘要，
      等用户确认后才执行。绝对不要 finalize_chain 后直接执行。
禁止：不要用文字解释当前方案的逻辑，不要重新询问已确认的信息，不要未经确认就执行方案

【类型C：全新需求】
特征：用户描述了一个与当前方案无关的全新选品需求
示例："帮我找美国的达人" "换个品类看看电子产品"
行动：当作全新规划请求，走必问清单流程"""

_PROMPT_CONDITIONAL = """━━━ 根据情况询问（信息不明确或影响方案质量时再问） ━━━
- 数据来源类型：用商品列表还是榜单（用户说"热销榜""日榜"时自动选榜单，否则默认商品列表）
- 榜单类型/周期：选了榜单时，若类型不明确则询问（热销榜/促销榜，日榜/周榜/月榜）
- 榜单多日期查询：若用户明确指定了日期范围（如"4月1日到4日"、"最近3天"），调用 select_product_source / select_influencer_source / select_seller_source / select_video_source 时使用 ranking_date_list 参数传入多个日期（如 ["2026-04-01","2026-04-02","2026-04-03","2026-04-04"]），执行引擎会自动合并去重，无需询问用户选哪一天；若用户未提及具体日期则照常使用 ranking_period
- 销量/评分门槛：若用户未明确给出，不要偷偷加激进硬门槛。优先通过排序或评分体现"增长快""评分高"，只有在用户明确要求时才加具体阈值
- 如确实需要默认阈值，只能使用宽松默认值，例如近30天销量≥10、评分≥3.5，并且应在 summary 里用通俗语言说明
- 评分权重：若用户给了多个维度但未说明占比，可给出建议权重；若用户有明确偏好则按用户要求执行
- 计算方法：复杂场景下（如用户要求非线性评分、自定义公式）再询问，否则自动选最优方式"""

_PROMPT_COMPLEX = """━━━ 处理复杂需求 ━━━
- 用户已描述完整逻辑时：直接理解并构建，将用户逻辑翻译为积木链，不重复问已知内容
- 如果预览或执行结果为空，优先引导用户调整价格区间、销量门槛、评分要求或品类范围
- 用户描述有歧义时：选择最合理的解释先构建，并在 finalize_chain 的 summary 里说明你的理解
- 每次只问一个问题，问完等用户回答再继续"""

_PROMPT_ASK_STYLE = """━━━ ask_user 消息风格 ━━━
- message 必须简短（1句话），口语化，直接问缺少的信息，禁止使用 Markdown
- 好例子："你想推荐多少个商品？"  坏例子："**待确认：**\\n- 推荐数量..."
- suggestions 给2-4个简短选项，每项不超过10个字"""

_PROMPT_BUILD_RULES = """━━━ 构建原则 ━━━
- 先筛后评：筛选后再评分，减少评分量
- 数值评分优先于语义评分（省Token成本），用户明确要求语义时才用
- 先问后建：必问信息缺失时不得开始构建
- 用户只表达"增长快""评分高"时，优先用排序/评分表达需求，不要自动塞入过高的默认筛选值
- 调用 select_product_source（product_listing 模式）时，默认带上 min_total_sale_30d_cnt=200，
  避免拉到大量 0 销量商品；除非用户明确说"不限销量"或"看冷启动商品"才不传"""

_PROMPT_FINALIZE = """━━━ finalize_chain 的 summary 写法 ━━━
必须用普通人能看懂的大白话，禁止出现字段名（如 total_sale_cnt）或技术术语（如 blockId）。
summary 要说清楚：
  ① 从哪里拿数据（来源 + 市场 + 周期）
  ② 怎么筛选（通俗描述条件）
  ③ 怎么打分排序（维度 + 权重占比）
  ④ 最终给出什么（数量 + 是否有AI评语）

好的 summary 示例：
"从泰国商品数据里找家居类商品，优先看近期销量走势更好、买家反馈更好的商品，再按综合评分排序，给出最值得关注的20个商品。"

调用 finalize_chain 即表示积木链构建完成。"""

# 拼接所有模块
_SYSTEM_PROMPT_PARTS = [
    _PROMPT_ROLE,
    _PROMPT_WORKFLOW,
    _PROMPT_CHECKLIST,
    _PROMPT_INTENT,
    _PROMPT_CONDITIONAL,
    _PROMPT_COMPLEX,
    _PROMPT_ASK_STYLE,
    _PROMPT_BUILD_RULES,
    _PROMPT_FINALIZE,
]


def build_system_prompt() -> str:
    """每次请求动态生成 SYSTEM_PROMPT，注入当前日期和 T+2 延迟说明。"""
    today = datetime.now(timezone.utc)
    date_str = today.strftime("%Y-%m-%d")
    latest_data = (today - timedelta(days=2)).strftime("%Y-%m-%d")
    date_hint = (
        f"━━━ 系统信息 ━━━\n"
        f"当前日期：{date_str}。API 数据有 T+2 延迟，最新可用数据日期为 {latest_data}。\n"
        f"榜单日期未指定时，默认使用最新可用日期（当前日期 - 2天）。"
    )
    return "\n\n".join([date_hint] + _SYSTEM_PROMPT_PARTS)

MAX_ITERATIONS = 30
MAX_CHAIN_LENGTH = 30
LLM_NODE_TIMEOUT_SECONDS = 120.0
_INCREMENTAL_TOOLS = frozenset({"modify_block", "rollback", "create_branch"})
MEMORY_INDEX_SCAN_LIMIT = 20
MEMORY_INDEX_SELECT_LIMIT = 5
MEMORY_FILE_EXCERPT_CHARS = 1800
MICROCOMPACT_TEXT_LIMIT = 1600
AI_MESSAGE_COMPACT_LIMIT = 1200
COMPACT_TRIGGER_MESSAGE_CHARS = 18_000
COMPACT_KEEP_RECENT_MESSAGES = 8
MAX_COMPACT_FAILURES = 3

# 合成工具：让 LLM 可以按需调用 resources/read 查询知识（品类树、字段字典等）
_RESOURCE_TOOL: dict = {
    "type": "function",
    "function": {
        "name": "read_resource",
        "description": "查询MCP知识资源（按需调用，不确定品类ID、字段名、连接规则时使用）。",
        "parameters": {
            "type": "object",
            "properties": {
                "uri": {
                    "type": "string",
                    "description": (
                        "资源URI。可选值：\n"
                        "- echotik://regions（地区列表）\n"
                        "- echotik://categories/{region}（品类树，region如TH/MY/PH/VN）\n"
                        "- echotik://fields/{entity_type}（字段字典，如product_list/influencer_list）\n"
                        "- echotik://endpoints（数据源端点能力）\n"
                        "- echotik://connection-rules（连接规则表）\n"
                        "- echotik://scoring-algorithms（评分算法说明）\n"
                        "- echotik://formula-templates（常用计算公式模板）\n"
                        "- echotik://preset-examples（预设套餐示例，Few-shot参考）\n"
                        "- chain://state（当前积木链状态）\n"
                        "- chain://cost-estimate（成本预估）"
                    ),
                }
            },
            "required": ["uri"],
        },
    },
}


# 合成工具：通过 Memory Agent 检索记忆（替代原来的 read_memory）
_QUERY_MEMORY_TOOL: dict = {
    "type": "function",
    "function": {
        "name": "query_memory",
        "description": "用自然语言检索记忆系统，获取历史数据、用户偏好或之前的执行结果。\n"
                       "例如：'上次达人数据有哪些' '用户的市场偏好' '第一步执行结果'",
        "parameters": {
            "type": "object",
            "properties": {
                "question": {
                    "type": "string",
                    "description": "要检索的问题（自然语言）",
                }
            },
            "required": ["question"],
        },
    },
}

# 合成工具：通过 Memory Agent 写入记忆（替代原来的 write_memory）
_SAVE_MEMORY_TOOL: dict = {
    "type": "function",
    "function": {
        "name": "save_memory",
        "description": "将值得保留的信息写入记忆系统。\n"
                       "仅在以下情况调用：①用户明确纠正AI行为 ②用户表达了明确市场/品类偏好 "
                       "③finalize_chain完成后记录本次链路核心参数。",
        "parameters": {
            "type": "object",
            "properties": {
                "scope": {
                    "type": "string",
                    "enum": ["common", "session"],
                    "description": "common=跨会话永久记忆，session=仅本次会话",
                },
                "memory_type": {
                    "type": "string",
                    "enum": ["user", "feedback", "project", "reference"],
                },
                "name":        {"type": "string", "description": "记忆名称（10字以内）"},
                "description": {"type": "string", "description": "一句话摘要"},
                "content":     {"type": "string", "description": "记忆正文（Markdown格式）"},
            },
            "required": ["scope", "memory_type", "name", "description", "content"],
        },
    },
}


def _append_list(left: list, right: list) -> list:
    """LangGraph reducer：将新条目追加到已有列表（用于 ambiguities 字段）。"""
    return left + (right or [])


# 保存后台任务引用，防止 asyncio.create_task 结果被 GC 提前回收
_background_tasks: set = set()


class AgentState(TypedDict):
    """LangGraph 图状态：messages 通过 add_messages reducer 自动追加，ambiguities/written_memory_names 累积。"""
    messages: Annotated[list[BaseMessage], add_messages]
    session_id: str
    user_id: str | None               # 用户ID，记忆系统读写用；None 时禁用记忆功能
    openai_tools: list[dict]          # 当前轮次从 MCP 获取的工具列表（OpenAI 格式）
    iterations: int
    total_tokens: int
    prompt_tokens: int
    completion_tokens: int
    done: bool
    result: dict | None
    action: dict | None               # 增量模式：最近一次增量操作的摘要
    ambiguities: Annotated[list[str], _append_list]       # 累积的不确定性提示（来自 Observation hint）
    written_memory_names: Annotated[list[str], _append_list]  # 主 Agent 已写入的记忆名称（蒸馏互斥用）
    surfaced_memory_paths: Annotated[list[str], _append_list]  # 当前线程已显式读取过的记忆文件，避免反复注入/筛选
    chain_length: int                 # 当前积木链长度（来自 MCP Observation）
    token_quota: int | None           # 本次请求的 Token 配额上限（由 Java 传入）
    chain_snapshot: dict | None       # 积木链恢复上下文（随 checkpoint 持久化，用于 Java ChainBuildSession 重建）
    compact_summary: str | None       # 当前线程的压缩摘要
    compact_generation: int           # 已触发压缩的代数
    compact_failures: int             # 连续压缩失败计数（熔断器：≥3 时停止尝试）
    memory_updated_at_map: dict       # {filePath: updatedAt}，read_memory 时注入新鲜度警告


class AgentService:
    """
    LangGraph ReAct Agent：使用 MCP 工具从自然语言构建 block_chain。
    图结构：fetch_tools → call_llm → call_tools → (fetch_tools | END)
    每次 /intent/parse 请求创建独立实例，checkpointer 由外部注入（app 级单例）。
    会话状态通过 Redis checkpoint 持久化，多轮对话无需重传 qa_history。
    """

    def __init__(
        self,
        llm_configs: list[dict] | None = None,
        mcp_base_url: str | None = None,
        checkpointer=None,
        memory_agent=None,
    ):
        configs = llm_configs or []
        self._llm = create_chat_llm_with_fallbacks(configs, temperature=0.2)
        self._mcp_base_url = mcp_base_url
        self._checkpointer = checkpointer
        self._memory_agent = memory_agent
        # 蒸馏 Agent 使用低温度保证结构化输出稳定（与主 Agent 共用同一 LLM 配置）
        self._distill_llm = create_chat_llm_with_fallbacks(configs, temperature=0.1)
        self._compact_llm = create_chat_llm_with_fallbacks(configs, temperature=0.1, max_tokens=1200)
        # 压缩参数：从主 LLM 配置读取，未配置时用默认值
        primary = configs[0] if configs else {}
        self._compact_message_limit = primary.get("compact_message_limit", 48)
        self._compact_char_limit = primary.get("compact_char_limit", COMPACT_TRIGGER_MESSAGE_CHARS)

    # ------------------------------------------------------------------
    # 图节点（静态方法，复杂度独立核算）
    # ------------------------------------------------------------------

    @staticmethod
    async def _fetch_tools_node(state: AgentState, *, mcp_client: MCPClient) -> dict:
        """每轮循环开始时重新获取动态工具列表（随链状态变化）。"""
        mcp_tools = await mcp_client.list_tools(session_id=state["session_id"])
        openai_tools = mcp_client.mcp_tools_to_openai_functions(mcp_tools)
        openai_tools.append(_RESOURCE_TOOL)
        # 记忆工具仅在 user_id 存在时注入（通过 Memory Agent 代理）
        if state.get("user_id"):
            openai_tools.append(_QUERY_MEMORY_TOOL)
            openai_tools.append(_SAVE_MEMORY_TOOL)
        logger.info(
            "Fetched tools session=%s mcp_tools=%d openai_tools=%d",
            state["session_id"],
            len(mcp_tools),
            len(openai_tools),
        )
        return {"openai_tools": openai_tools}

    @staticmethod
    async def _llm_node(state: AgentState, *, llm: ChatOpenAI, mcp_client: MCPClient) -> dict:
        """Think + Act：流式调用 LLM，每 100 字符向前端推送 thinking 事件。"""
        session_id = state.get("session_id")
        openai_tools = state.get("openai_tools") or []
        logger.info(
            "Starting LLM node session=%s iteration=%s tools=%d messages=%d",
            session_id,
            state.get("iterations", 0) + 1,
            len(openai_tools),
            len(state["messages"]),
        )
        bound_llm = (
            llm.bind_tools(openai_tools, tool_choice="auto") if openai_tools else llm
        )

        # 微压缩：截断旧 ToolMessage 以减少 token 消耗（不影响 checkpoint）
        llm_messages = AgentService._micro_compact_old_tool_messages(state["messages"])

        # 流式收集，每 100 字符 fire-and-forget 推送一次 thinking 事件
        response = None
        thinking_buf = ""
        last_pushed_len = 0

        async def _collect_stream() -> None:
            nonlocal response, thinking_buf, last_pushed_len
            async for chunk in bound_llm.astream(llm_messages):
                response = chunk if response is None else response + chunk
                if chunk.content and session_id:
                    thinking_buf += chunk.content
                    if len(thinking_buf) - last_pushed_len >= 100:
                        asyncio.create_task(
                            mcp_client.notify_thinking(thinking_buf, session_id)
                        )
                        last_pushed_len = len(thinking_buf)

        # Keepalive：推理型模型在 reasoning 阶段无 content 输出，可能长达数十秒没有任何事件，
        # 导致前端 60s idle timeout 误判。每 15s 推送一次心跳，仅在无真实 content 时触发。
        async def _keepalive_loop() -> None:
            while True:
                await asyncio.sleep(15)
                if session_id and last_pushed_len == 0:
                    asyncio.create_task(
                        mcp_client.notify_thinking("AI 正在深度分析中…", session_id)
                    )

        keepalive_task = asyncio.create_task(_keepalive_loop()) if session_id else None
        try:
            await asyncio.wait_for(_collect_stream(), timeout=LLM_NODE_TIMEOUT_SECONDS)
        except asyncio.TimeoutError as exc:
            logger.warning("LLM node timed out after %.1fs session=%s", LLM_NODE_TIMEOUT_SECONDS, session_id)
            raise TimeoutError(f"LLM planning timed out after {int(LLM_NODE_TIMEOUT_SECONDS)}s") from exc
        finally:
            if keepalive_task is not None:
                keepalive_task.cancel()

        # 推送剩余内容
        if thinking_buf and session_id and last_pushed_len < len(thinking_buf):
            asyncio.create_task(mcp_client.notify_thinking(thinking_buf, session_id))

        if response is None:
            try:
                response = await asyncio.wait_for(
                    bound_llm.ainvoke(state["messages"]),
                    timeout=LLM_NODE_TIMEOUT_SECONDS,
                )
            except asyncio.TimeoutError as exc:
                logger.warning("LLM invoke timed out after %.1fs session=%s", LLM_NODE_TIMEOUT_SECONDS, session_id)
                raise TimeoutError(f"LLM planning timed out after {int(LLM_NODE_TIMEOUT_SECONDS)}s") from exc

        response = AgentService._compact_ai_message_content(response)

        prompt_this_turn, completion_this_turn, total_this_turn = AgentService._normalize_usage_tokens(
            state["messages"],
            response,
            session_id=session_id,
        )
        total_tokens      = state.get("total_tokens", 0)      + total_this_turn
        prompt_tokens     = state.get("prompt_tokens", 0)     + prompt_this_turn
        completion_tokens = state.get("completion_tokens", 0) + completion_this_turn
        iterations = state.get("iterations", 0) + 1
        logger.info("ReAct iteration %d/%d, tokens_this_turn=%s",
                    iterations, MAX_ITERATIONS, total_this_turn)
        logger.info(
            "LLM node completed session=%s iteration=%s tool_calls=%d content_len=%d",
            session_id,
            iterations,
            len(response.tool_calls or []),
            len(response.content or ""),
        )
        return {
            "messages": [response],
            "total_tokens": total_tokens,
            "prompt_tokens": prompt_tokens,
            "completion_tokens": completion_tokens,
            "iterations": iterations,
        }

    @staticmethod
    def _message_text_for_estimate(message: BaseMessage) -> str:
        content = getattr(message, "content", "")
        if isinstance(content, str):
            return content
        if content is None:
            return ""
        try:
            return json.dumps(content, ensure_ascii=False)
        except Exception:
            return str(content)

    @staticmethod
    def _estimate_usage_tokens(messages: list[BaseMessage], response: AIMessage) -> tuple[int, int, int]:
        prompt_chars = sum(len(AgentService._message_text_for_estimate(m)) for m in messages)
        response_text = AgentService._message_text_for_estimate(response)
        tool_calls = getattr(response, "tool_calls", None) or []
        if tool_calls:
            response_text += json.dumps(tool_calls, ensure_ascii=False)
        prompt_tokens = max(1, (prompt_chars + 1) // 2)
        completion_tokens = max(1, (len(response_text) + 1) // 2) if response_text else max(1, len(tool_calls) * 16)
        total_tokens = prompt_tokens + completion_tokens
        return prompt_tokens, completion_tokens, total_tokens

    @staticmethod
    def _normalize_usage_tokens(
        messages: list[BaseMessage],
        response: AIMessage,
        *,
        session_id: str | None = None,
    ) -> tuple[int, int, int]:
        """
        归一化 LLM 响应里的 token 计数，用于预算守门。

        关键修正：provider 会把 cache_read（缓存命中、计费近乎免费）算进 input_tokens，
        导致执行预算被夸大几个数量级（曾观测到 reported=2.5M 但 estimated=1.6K）。
        这里把 cache_read 从 input 扣掉，得到「真实计费 tokens」用于预算累加。
        """
        usage = response.usage_metadata or {}
        estimated_prompt, estimated_completion, estimated_total = AgentService._estimate_usage_tokens(messages, response)

        def _to_int(value: object) -> int:
            return int(value) if isinstance(value, (int, float)) and not isinstance(value, bool) else 0

        raw_prompt = _to_int(usage.get("input_tokens"))
        completion_tokens = _to_int(usage.get("output_tokens"))

        # 扣除缓存命中：langchain 的 usage_metadata 里 cache_read 在 input_token_details 下
        cache_read = 0
        details = usage.get("input_token_details")
        if isinstance(details, dict):
            cache_read = _to_int(details.get("cache_read")) + _to_int(details.get("cache_creation"))

        prompt_tokens = max(0, raw_prompt - cache_read)
        total_tokens = prompt_tokens + completion_tokens

        # 仍保留一道宽松的离群检测：扣掉缓存后还远超本地估算才算可疑
        suspicious = (
            total_tokens <= 0
            or total_tokens > max(estimated_total * 12, 50_000)
            or prompt_tokens > max(estimated_prompt * 12, 50_000)
            or completion_tokens > max(estimated_completion * 12, 50_000)
        )
        if suspicious:
            logger.warning(
                "Suspicious token usage from provider session=%s reported=%s cache_read=%s "
                "adjusted=(prompt=%s, completion=%s, total=%s) estimated_total=%s",
                session_id, usage, cache_read,
                prompt_tokens, completion_tokens, total_tokens, estimated_total,
            )
            return estimated_prompt, estimated_completion, estimated_total

        if cache_read > 0 and logger.isEnabledFor(logging.DEBUG):
            logger.debug(
                "Token usage cache adjustment session=%s raw_prompt=%s cache_read=%s -> billable=%s",
                session_id, raw_prompt, cache_read, prompt_tokens,
            )

        return prompt_tokens, completion_tokens, total_tokens

    @staticmethod
    def _microcompact_text(text: str, limit: int = MICROCOMPACT_TEXT_LIMIT) -> str:
        normalized = (text or "").strip()
        if len(normalized) <= limit:
            return normalized
        head = normalized[: limit // 2]
        tail = normalized[-limit // 4 :]
        removed = len(normalized) - len(head) - len(tail)
        return f"{head}\n...[已压缩 {removed} 字]...\n{tail}"

    @staticmethod
    def _compact_json_value(value, *, depth: int = 0):
        if depth >= 3:
            return "（深层内容已压缩）"
        if isinstance(value, str):
            return AgentService._microcompact_text(value)
        if isinstance(value, list):
            if len(value) <= 6:
                return [AgentService._compact_json_value(v, depth=depth + 1) for v in value]
            head = [AgentService._compact_json_value(v, depth=depth + 1) for v in value[:3]]
            tail = [AgentService._compact_json_value(v, depth=depth + 1) for v in value[-2:]]
            return head + [f"... 共 {len(value)} 项，已压缩 ..."] + tail
        if isinstance(value, dict):
            compacted: dict = {}
            for idx, (key, val) in enumerate(value.items()):
                if idx >= 12:
                    compacted["__truncated__"] = f"其余 {len(value) - 12} 个字段已压缩"
                    break
                compacted[key] = AgentService._compact_json_value(val, depth=depth + 1)
            return compacted
        return value

    @staticmethod
    def _compact_tool_observation(obs: dict) -> dict:
        return AgentService._compact_json_value(obs, depth=0)

    @staticmethod
    def _compact_ai_message_content(response: AIMessage) -> AIMessage:
        content = response.content
        if isinstance(content, str) and len(content) > AI_MESSAGE_COMPACT_LIMIT:
            response.content = AgentService._microcompact_text(content, AI_MESSAGE_COMPACT_LIMIT)
        return response

    @staticmethod
    def _message_char_count(messages: list[BaseMessage]) -> int:
        return sum(len(AgentService._message_text_for_estimate(msg)) for msg in messages)

    @staticmethod
    def _micro_compact_old_tool_messages(messages: list[BaseMessage], keep_recent: int = 6) -> list[BaseMessage]:
        """零 LLM 成本的微压缩：截断旧 ToolMessage 内容，只保留关键字段。
        使用浅拷贝，不修改 checkpoint 中的原始消息。"""
        human_indices = [i for i, m in enumerate(messages) if isinstance(m, HumanMessage)]
        if len(human_indices) <= 1:
            return messages
        cutoff = human_indices[-keep_recent] if len(human_indices) > keep_recent else human_indices[0]
        result = list(messages)
        for i in range(cutoff):
            msg = result[i]
            if isinstance(msg, ToolMessage) and len(str(msg.content or "")) > 300:
                compacted = copy.copy(msg)
                try:
                    obs = json.loads(msg.content) if isinstance(msg.content, str) else msg.content
                    summary = {k: obs[k] for k in ("success", "message", "error", "chain_length") if k in obs}
                    compacted.content = json.dumps(summary, ensure_ascii=False)
                except Exception:
                    compacted.content = str(msg.content)[:200]
                result[i] = compacted
        return result

    @staticmethod
    async def _execute_tool_call(
        tc: dict,
        session_id: str,
        mcp_client: MCPClient,
        memory_agent=None,
        user_id: str | None = None,
        state: dict | None = None,
    ) -> dict:
        """执行单个工具调用，返回 observation dict。"""
        func_name: str = tc["name"]
        func_args: dict = tc["args"]
        logger.info("Calling tool: %s(%s)", func_name, func_args)
        try:
            if func_name == "read_resource":
                uri = func_args.get("uri", "")
                raw = await mcp_client.read_resource(uri=uri, session_id=session_id)
                contents = raw.get("contents", [])
                return (
                    {"success": True, "uri": uri, "content": contents[0].get("text", "{}")}
                    if contents
                    else {"success": False, "error": "Resource returned no contents"}
                )

            if func_name == "query_memory":
                if not memory_agent:
                    return {"success": False, "error": "记忆系统未初始化（user_id 未传入）"}
                question = func_args.get("question", "")
                content = await memory_agent.query(question)
                return {"success": True, "content": content}

            if func_name == "save_memory":
                if not memory_agent:
                    return {"success": False, "error": "记忆系统未初始化（user_id 未传入）"}
                result = await memory_agent.save(
                    name=func_args.get("name", ""),
                    description=func_args.get("description", ""),
                    content=func_args.get("content", ""),
                    memory_type=func_args.get("memory_type", "project"),
                    scope=func_args.get("scope", "session"),
                )
                return {
                    "success": True,
                    "message": result,
                    "_written_memory_name": func_args.get("name", ""),
                }

            return await mcp_client.call_tool(
                name=func_name,
                arguments=func_args,
                session_id=session_id,
                tool_call_id=tc["id"],
            )
        except Exception as e:
            logger.warning("Tool %s failed: %s", func_name, e)
            return {"success": False, "error": str(e)}

    @staticmethod
    def _extract_chain_snapshot(tc_name: str, obs: dict) -> dict | None:
        """从 Observation 提取积木链恢复快照。ask_user/finalize_chain 时链已终止，返回 None。"""
        if not obs.get("chain_snapshot") or tc_name in ("ask_user", "finalize_chain"):
            return None
        return {
            "blockChain": obs["chain_snapshot"],
            "currentOutputType": obs.get("data_type") or "",
            "availableFields": obs.get("available_fields") or [],
            "scoreFields": obs.get("score_fields") or [],
        }

    @staticmethod
    def _process_observation_updates(
        tc_name: str,
        tc_args: dict,
        obs: dict,
        chain_length: int,
        new_hints: list[str],
    ) -> tuple[int, dict | None, dict | None, bool]:
        """从单次 Observation 提取状态更新。返回 (chain_length, result, action, done)。"""
        if "chain_length" in obs:
            chain_length = obs["chain_length"]
        hint = obs.get("hint", "")
        if hint:
            new_hints.append(hint)

        result = None
        action = None
        done = False

        if tc_name == "ask_user" and obs.get("type") == "needs_input":
            result = {
                "success": True,
                "type": "needs_input",
                "message": obs.get("message", ""),
                "suggestions": obs.get("suggestions") or [],
            }
            done = True
        elif tc_name == "finalize_chain":
            block_chain = obs.get("block_chain")
            if block_chain:
                result = {
                    "success": True,
                    "type": "block_chain",
                    "block_chain": block_chain,
                    "summary": obs.get("message", ""),
                }
                done = True
        elif tc_name == "create_plan":
            plan = obs.get("selection_plan")
            if plan:
                result = {
                    "success": True,
                    "type": "plan_draft",
                    "plan": plan,
                    "message": obs.get("message", ""),
                }
                done = True
        elif tc_name in _INCREMENTAL_TOOLS and obs.get("success"):
            action = {"type": tc_name, **tc_args, "message": obs.get("message", "")}

        return chain_length, result, action, done

    @staticmethod
    def _process_single_obs(
        tc: dict,
        obs: dict,
        chain_length: int,
        new_hints: list[str],
    ) -> tuple[int, bool, dict | None, dict | None, dict | None]:
        """处理单次工具 Observation，返回 (chain_length, done, result, action, snapshot)。"""
        if not isinstance(obs, dict):
            return chain_length, False, None, None, None
        chain_length, result, action, done = AgentService._process_observation_updates(
            tc["name"], tc["args"], obs, chain_length, new_hints
        )
        snapshot = AgentService._extract_chain_snapshot(tc["name"], obs)
        return chain_length, done, result, action, snapshot

    @staticmethod
    async def _tool_node(
        state: AgentState,
        *,
        mcp_client: MCPClient,
        memory_agent=None,
    ) -> dict:
        """Observe：顺序执行所有工具调用，收集观测结果，提取 chain_length 和 hint。"""
        last: AIMessage = state["messages"][-1]
        session_id = state["session_id"]
        user_id    = state.get("user_id")
        tool_messages: list[ToolMessage] = []
        done = False
        result = None
        action = None
        new_hints: list[str] = []
        new_written_names: list[str] = []
        latest_snapshot: dict | None = None
        chain_length = state.get("chain_length", 0)

        for tc in last.tool_calls:
            obs = await AgentService._execute_tool_call(
                tc, session_id, mcp_client, memory_agent, user_id, state=state,
            )
            # 收集主动写入的记忆名称（蒸馏互斥用）
            written_name = obs.pop("_written_memory_name", None)
            if written_name:
                new_written_names.append(written_name)

            # 自动将 MCP 工具调用结果保存到记忆（排除记忆工具自身和非数据工具）
            func_name = tc.get("name", "")
            if memory_agent and func_name not in (
                "read_resource", "query_memory", "save_memory", "ask_user", "create_plan",
            ):
                try:
                    from app.agents.memory_agent import compute_chain_hash
                    chain_hash = compute_chain_hash(
                        state.get("chain_snapshot", {}).get("blockChain")
                        if state.get("chain_snapshot") else None
                    )
                    await memory_agent.save_tool_result(
                        phase="规划",
                        block_chain_hash=chain_hash,
                        tool_name=func_name,
                        tool_args=tc.get("args", {}),
                        tool_result=obs,
                    )
                except Exception as e:
                    logger.warning("Auto-save tool result failed tool=%s: %s", func_name, e)

            compact_obs = AgentService._compact_tool_observation(obs)
            tool_messages.append(ToolMessage(
                content=json.dumps(compact_obs, ensure_ascii=False),
                tool_call_id=tc["id"],
            ))
            chain_length, obs_done, obs_result, obs_action, obs_snapshot = \
                AgentService._process_single_obs(tc, obs, chain_length, new_hints)
            if obs_done:
                done, result = True, obs_result
            if obs_action:
                action = obs_action
            if obs_snapshot:
                latest_snapshot = obs_snapshot
            # ask_user/finalize_chain are terminal for the current turn.
            # Stop immediately so later tool calls in the same LLM response
            # do not keep running and delay the frontend state transition.
            if obs_done:
                break

        update: dict = {"messages": tool_messages, "chain_length": chain_length}
        if done:
            update["done"] = True
            update["result"] = result
        if action:
            update["action"] = action
        if new_hints:
            update["ambiguities"] = new_hints
        if new_written_names:
            update["written_memory_names"] = new_written_names
        # surfaced_memory_paths 已移入 MemoryAgent 内部管理
        if latest_snapshot:
            update["chain_snapshot"] = latest_snapshot
        return update

    # ------------------------------------------------------------------
    # 条件边（静态方法）
    # ------------------------------------------------------------------

    @staticmethod
    def _after_llm(state: AgentState) -> str:
        last = state["messages"][-1]
        if isinstance(last, AIMessage) and last.tool_calls:
            return "call_tools"
        return END

    @staticmethod
    def _after_tools(state: AgentState) -> str:
        if state.get("done") or state.get("iterations", 0) >= MAX_ITERATIONS:
            return END
        if state.get("chain_length", 0) >= MAX_CHAIN_LENGTH:
            return END
        quota = state.get("token_quota")
        if quota and state.get("total_tokens", 0) >= quota:
            return END
        return "fetch_tools"

    # ------------------------------------------------------------------
    # 图装配（仅做连线）
    # ------------------------------------------------------------------

    async def _run_graph(
        self,
        graph,
        initial_state: dict,
        config: dict,
        session_id: str,
    ) -> tuple["AgentState | None", bool]:
        """运行 LangGraph 图，限时 600 秒。返回 (last_state, timed_out)。"""
        last_state: AgentState | None = None
        timed_out = False

        async def _stream() -> None:
            nonlocal last_state
            async for state in graph.astream(initial_state, config=config, stream_mode="values"):
                last_state = state

        try:
            await asyncio.wait_for(_stream(), timeout=600.0)
        except asyncio.TimeoutError:
            timed_out = True
            iters = last_state.get("iterations", 0) if last_state else 0
            logger.warning("parse_intent timed out session=%s iterations=%s", session_id, iters)

        return last_state, timed_out

    def _build_graph(self, mcp_client: MCPClient):
        graph = StateGraph(AgentState)
        graph.add_node("fetch_tools", partial(self._fetch_tools_node, mcp_client=mcp_client))
        graph.add_node("call_llm",    partial(self._llm_node,         llm=self._llm, mcp_client=mcp_client))
        graph.add_node("call_tools",  partial(self._tool_node,        mcp_client=mcp_client,
                                                                       memory_agent=self._memory_agent))

        graph.add_edge(START, "fetch_tools")
        graph.add_edge("fetch_tools", "call_llm")
        graph.add_conditional_edges(
            "call_llm", self._after_llm, {"call_tools": "call_tools", END: END}
        )
        graph.add_conditional_edges(
            "call_tools", self._after_tools, {"fetch_tools": "fetch_tools", END: END}
        )
        return graph.compile(checkpointer=self._checkpointer)

    # ------------------------------------------------------------------
    # 异步压缩：后台删旧 checkpoint + 同 ID 重建
    # ------------------------------------------------------------------

    async def _async_compact_and_rebuild(
        self,
        thread_id: str,
        state_values: dict,
        session_id: str,
        user_id: str | None,
        session_context: dict | None,
        qa_history: list[dict] | None,
        token_quota: int | None,
    ):
        """后台异步压缩：LLM 生成摘要 → 删旧 checkpoint → 同 thread_id 重建。"""
        if state_values.get("compact_failures", 0) >= MAX_COMPACT_FAILURES:
            return
        try:
            summary = await compact_history(
                state_values.get("messages") or [],
                self._compact_llm,
                user_text="",
                chain_snapshot=state_values.get("chain_snapshot") or session_context,
                prior_summary=state_values.get("compact_summary"),
            )
            if not summary:
                raise ValueError("Compact returned empty summary")

            # 保留最近 10 条消息
            messages = state_values.get("messages") or []
            recent = messages[-10:] if len(messages) > 10 else messages

            rebuilt_messages = [
                SystemMessage(content=f"## 历史摘要\n{summary}"),
                *recent,
            ]

            rebuilt_state = {
                **self._base_state_fields(session_id, user_id, token_quota),
                "messages": rebuilt_messages,
                "chain_snapshot": state_values.get("chain_snapshot") or session_context,
                "chain_length": state_values.get("chain_length", 0),
                "compact_summary": summary,
                "compact_failures": 0,
                "written_memory_names": state_values.get("written_memory_names") or [],
            }

            # 删除旧 checkpoint + 用同一个 thread_id 重建（加锁防竞态）
            async with _get_compact_lock(thread_id):
                checkpointer = self._checkpointer
                await checkpointer.adelete_thread(thread_id)

                mcp_client = MCPClient(mcp_base_url=self._mcp_base_url)
                try:
                    graph = self._build_graph(mcp_client)
                    config = {"configurable": {"thread_id": thread_id}}
                    await graph.ainvoke(rebuilt_state, config)
                finally:
                    await mcp_client.close()

            logger.info(
                "ASYNC_COMPACT_DONE thread=%s messages=%d→%d summary_len=%d",
                thread_id, len(messages), len(rebuilt_messages), len(summary),
            )
        except Exception as e:
            logger.warning("ASYNC_COMPACT_FAILED thread=%s: %s", thread_id, e)

    # ------------------------------------------------------------------
    # 响应构建（静态方法，与 parse_intent 分离以降低认知复杂度）
    # ------------------------------------------------------------------

    @staticmethod
    def _build_parse_result(final_state: AgentState, timed_out: bool) -> dict:
        """从最终图状态构建 parse_intent 响应字典。"""
        total_tokens = final_state.get("total_tokens", 0)
        iterations   = final_state.get("iterations", 0)
        last_action  = final_state.get("action")
        ambiguities: list[str] = final_state.get("ambiguities") or []
        usage = {
            "prompt_tokens":     final_state.get("prompt_tokens", 0),
            "completion_tokens": final_state.get("completion_tokens", 0),
            "total_tokens":      total_tokens,
        }
        common: dict = {
            "llm_tokens_used": total_tokens,
            "usage": usage,
            "iterations": iterations,
            "ambiguities": ambiguities or None,
            "conversation_summary": final_state.get("compact_summary"),
        }

        if final_state.get("result"):
            r = {**final_state["result"], "action": last_action, **common}
            if timed_out:
                r["message"] = (r.get("message") or "") + "（请求已超时，返回部分结果）"
            return r

        if last_action:
            r = {"success": True, "type": "action", "action": last_action, **common}
            if timed_out:
                r["message"] = "（请求已超时，返回部分结果）"
            return r

        if timed_out:
            return {"success": False, "type": "block_chain",
                    "message": f"请求超时（单轮LLM规划超过{int(LLM_NODE_TIMEOUT_SECONDS)}秒），积木链未完成", **common}

        if iterations >= MAX_ITERATIONS:
            return {"success": False, "type": "block_chain",
                    "message": f"达到最大迭代次数({MAX_ITERATIONS})，积木链未完成", **common}

        if final_state.get("chain_length", 0) >= MAX_CHAIN_LENGTH:
            return {"success": False, "type": "block_chain",
                    "message": f"积木链长度已达上限（{MAX_CHAIN_LENGTH}块），终止构建", **common}

        quota_val = final_state.get("token_quota")
        if quota_val and total_tokens >= quota_val:
            return {"success": False, "type": "block_chain",
                    "message": f"Token配额已用尽（{total_tokens}/{quota_val}），积木链未完成",
                    **common}

        last_msg = final_state["messages"][-1]
        return {"success": True, "type": "block_chain",
                "message": getattr(last_msg, "content", "完成") or "完成", **common}

    # ------------------------------------------------------------------
    # 公开接口
    # ------------------------------------------------------------------

    @staticmethod
    def _base_state_fields(session_id: str, user_id: str | None, token_quota: int | None) -> dict:
        """三条执行路径共用的基础状态字段。"""
        return {
            "session_id": session_id,
            "user_id": user_id,
            "openai_tools": [],
            "iterations": 0,
            "total_tokens": 0,
            "prompt_tokens": 0,
            "completion_tokens": 0,
            "done": False,
            "result": None,
            "action": None,
            "ambiguities": [],
            "written_memory_names": [],
            "surfaced_memory_paths": [],
            "chain_length": 0,
            "chain_snapshot": None,
            "token_quota": token_quota,
            "compact_summary": None,
            "compact_generation": 0,
            "compact_failures": 0,
            "memory_updated_at_map": {},
        }

    _MEMORY_FILTER_SYSTEM_PROMPT = """你是记忆相关性筛选器。根据用户当前问题，从记忆索引中选出最相关的条目。

筛选原则：
- 用户正在使用的工具（active_tools）：不需要召回这些工具的基本用法文档，但务必召回与这些工具相关的警告、陷阱、已知问题
- 用户画像和行为反馈类记忆（user/feedback）：如果描述与当前问题的市场/品类/策略相关，优先召回
- 会话决策类记忆（project）：仅当用户明确想复用或参考历史方案时才召回
- 外部资源类记忆（reference）：仅当用户提及了相关的数据源或参考时才召回
- 描述过于泛化的记忆（如"选品相关""用户偏好"）相关性应降低

直接返回选中序号的 JSON 数组，如 [0,2,4]。无相关时返回 []。"""

    @staticmethod
    async def _filter_relevant_memories(
        user_text: str,
        memory_index: list[dict],
        llm: ChatOpenAI,
        max_count: int = MEMORY_INDEX_SELECT_LIMIT,
        surfaced_paths: list[str] | None = None,
        active_tool_names: list[str] | None = None,
    ) -> list[dict]:
        """
        轻量 side call：从记忆索引中筛选与当前用户输入最相关的条目（最多 max_count 条）。
        条目数 ≤ max_count 时直接返回，不发起 LLM 调用。
        失败时回退到前 max_count 条，保证记忆注入不会因筛选出错而完全丢失。
        """
        surfaced = set(surfaced_paths or [])
        candidates = [
            entry for entry in memory_index
            if entry.get("filePath") not in surfaced
        ]

        if len(candidates) <= max_count:
            return candidates

        index_lines = [
            f"{i}: [{e.get('memoryType', '')}] 【{e.get('name', '')}】{e.get('description', '')}"
            for i, e in enumerate(candidates)
        ]
        active_tools_hint = ""
        if active_tool_names:
            active_tools_hint = f"\n当前用户可用工具：{', '.join(active_tool_names[:10])}\n"
        user_prompt = (
            f"用户问题：{user_text}\n"
            f"{active_tools_hint}\n"
            f"记忆索引（序号 [类型] 名称 描述）：\n" + "\n".join(index_lines) +
            f"\n\n从上述记忆中选出最多 {max_count} 条最相关的序号。"
        )
        try:
            resp = await llm.ainvoke([
                {"role": "system", "content": AgentService._MEMORY_FILTER_SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ])
            match = re.search(r'\[[\d,\s]*\]', resp.content.strip())
            if match:
                indices = json.loads(match.group())
                selected = [
                    candidates[i] for i in indices
                    if isinstance(i, int) and 0 <= i < len(candidates)
                ]
                return selected[:max_count]
        except Exception as e:
            logger.warning("Memory filter side call failed, falling back to head-%d: %s", max_count, e)
        return candidates[:max_count]

    @staticmethod
    def _freshness_hint(updated_at_str: str | None) -> str:
        """将 updatedAt 字符串转换为新鲜度提示文本。>1天即提示，>30天建议核实。"""
        if not updated_at_str:
            return ""
        try:
            updated_dt = datetime.fromisoformat(updated_at_str.replace("Z", "+00:00"))
            days_old = (datetime.now(timezone.utc) - updated_dt).days
            if days_old >= 30:
                return f" ⚠（{days_old}天前，可能严重过时，使用前务必核实）"
            if days_old >= 7:
                return f" *（{days_old}天前，建议核实后再使用）*"
            if days_old >= 1:
                return f" *（{days_old}天前）*"
        except Exception:
            pass
        return ""

    @staticmethod
    def _freshness_hint_for_content(updated_at_str: str | None) -> str:
        """用于 read_memory 返回内容时注入的更强新鲜度警告文本。"""
        if not updated_at_str:
            return ""
        try:
            updated_dt = datetime.fromisoformat(updated_at_str.replace("Z", "+00:00"))
            days_old = (datetime.now(timezone.utc) - updated_dt).days
            if days_old >= 30:
                return (f"此记忆已 {days_old} 天未更新，可能严重过时。"
                        "不要将其内容作为当前事实断言——请先与用户核实。")
            if days_old >= 7:
                return f"此记忆已 {days_old} 天未更新，使用前请核实其是否仍然适用。"
            if days_old >= 1:
                return f"此记忆最后更新于 {days_old} 天前。"
        except Exception:
            pass
        return ""

    @staticmethod
    def _extract_post_compact_context(messages: list[BaseMessage]) -> str | None:
        """从压缩前的消息历史中提取关键资源上下文（read_resource 结果和最近 MCP 工具结果）。"""
        resource_parts: list[str] = []
        mcp_part: str | None = None
        for i in range(len(messages) - 1, -1, -1):
            msg = messages[i]
            if not isinstance(msg, ToolMessage):
                continue
            try:
                obs = json.loads(msg.content) if isinstance(msg.content, str) else msg.content
                if not isinstance(obs, dict) or not obs.get("success"):
                    continue
            except Exception:
                continue
            # 查找对应的 AIMessage 获取工具名
            tool_name = ""
            tool_call_id = getattr(msg, "tool_call_id", "")
            for j in range(i - 1, max(i - 5, -1), -1):
                prev = messages[j]
                if isinstance(prev, AIMessage) and getattr(prev, "tool_calls", None):
                    for tc in prev.tool_calls:
                        if tc.get("id") == tool_call_id:
                            tool_name = tc.get("name", "")
                            break
                    if tool_name:
                        break
            if tool_name == "read_resource" and len(resource_parts) < 2:
                content_text = str(obs.get("content") or obs.get("data") or "")[:500]
                if content_text:
                    resource_parts.append(f"### {obs.get('uri', 'resource')}\n{content_text}")
            elif tool_name and tool_name not in ("read_memory", "write_memory", "read_resource") and not mcp_part:
                content_text = str(obs.get("message") or obs.get("data") or "")[:300]
                if content_text and obs.get("chain_length"):
                    mcp_part = f"### {tool_name} (chain_length={obs['chain_length']})\n{content_text}"
            if len(resource_parts) >= 2 and mcp_part:
                break
        if not resource_parts and not mcp_part:
            return None
        parts = ["## 压缩前的关键工具结果（供参考）\n"]
        parts.extend(resource_parts)
        if mcp_part:
            parts.append(mcp_part)
        return "\n\n".join(parts)

    @staticmethod
    def _qa_history_to_messages(user_text: str, qa_history: list[dict]) -> list[BaseMessage]:
        """将 Java 传来的历史对话重建为消息列表（checkpoint 丢失降级路径）。"""
        messages: list[BaseMessage] = [HumanMessage(content=user_text)]
        for qa in qa_history:
            if qa.get("q"):
                messages.append(AIMessage(content=qa["q"]))
            if qa.get("a"):
                messages.append(HumanMessage(content=qa["a"]))
        return messages

    @staticmethod
    def _build_memory_index_message(memory_index: list[dict]) -> str:
        """构建记忆索引的系统消息内容（用于续接会话时注入）。"""
        lines = ["## 该用户的历史记忆（按需用 read_memory 工具读取详情）\n"]
        for entry in memory_index:
            name = entry.get("name", "")
            path = entry.get("filePath", "")
            desc = entry.get("description") or ""
            freshness = AgentService._freshness_hint(entry.get("updatedAt"))
            lines.append(f"- [{name}]({path}) — {desc}{freshness}")
        return "\n".join(lines)

    @staticmethod
    def _build_initial_messages(
        user_text: str,
        session_context: dict | None,
        qa_history: list[dict] | None = None,
        memory_index: list[dict] | None = None,
        conversation_summary: str | None = None,
    ) -> list[BaseMessage]:
        """
        构建初始消息列表：系统提示 + 记忆索引 + 可选增量上下文 + 对话历史 + 用户输入。
        qa_history 仅在 checkpoint 丢失时用于降级恢复，正常路径不传。
        memory_index 仅新会话时注入（续接会话 checkpoint 已含历史上下文）。
        """
        messages: list[BaseMessage] = [SystemMessage(content=build_system_prompt())]

        if conversation_summary:
            messages.append(SystemMessage(content=f"## 当前规划线程的压缩摘要\n{conversation_summary}"))

        # 注入记忆索引（已由 _filter_relevant_memories 筛选，仅指针+description）
        if memory_index:
            lines = ["## 该用户的历史记忆（按需用 read_memory 工具读取详情）\n"]
            for entry in memory_index:
                name = entry.get("name", "")
                path = entry.get("filePath", "")
                desc = entry.get("description") or ""
                freshness = AgentService._freshness_hint(entry.get("updatedAt"))
                lines.append(f"- [{name}]({path}) — {desc}{freshness}")
            messages.append(SystemMessage(content="\n".join(lines)))

        if session_context:
            context_desc = (
                f"当前Session状态（增量模式）：\n"
                f"积木链长度={len(session_context.get('blockChain', []))}，"
                f"数据类型={session_context.get('currentOutputType', '未知')}，"
                f"可用字段={session_context.get('availableFields', [])}"
            )
            messages.append(SystemMessage(content=context_desc))

        if qa_history:
            messages += AgentService._qa_history_to_messages(user_text, qa_history)
        else:
            messages.append(HumanMessage(content=user_text))
        return messages

    def _should_compact_state(self, state_values: dict) -> bool:
        # 熔断器：连续失败 ≥ MAX_COMPACT_FAILURES 次后停止尝试
        if state_values.get("compact_failures", 0) >= MAX_COMPACT_FAILURES:
            return False
        messages = state_values.get("messages") or []
        if len(messages) >= self._compact_message_limit:
            return True
        return AgentService._message_char_count(messages) >= self._compact_char_limit

    async def _build_compacted_state(
        self,
        *,
        state_values: dict,
        session_id: str,
        agent_thread_id: str,
        user_id: str | None,
        user_text: str,
        session_context: dict | None,
        qa_history: list[dict] | None,
        token_quota: int | None,
        memory_index: list[dict] | None,
    ) -> tuple[str, dict]:
        prior_summary = state_values.get("compact_summary")
        compact_generation = int(state_values.get("compact_generation", 0)) + 1
        compacted_thread_id = f"{session_id}:c{compact_generation}"
        try:
            summary = await compact_history(
                state_values.get("messages") or [],
                self._compact_llm,
                user_text=user_text,
                chain_snapshot=state_values.get("chain_snapshot") or session_context,
                prior_summary=prior_summary,
            )
            if not summary or summary == (prior_summary or "").strip():
                raise ValueError("Compact returned empty or unchanged summary")
        except Exception as exc:
            failures = state_values.get("compact_failures", 0) + 1
            logger.warning(
                "Compact failed (attempt %d/%d) session=%s: %s",
                failures, MAX_COMPACT_FAILURES, session_id, exc,
            )
            if failures >= MAX_COMPACT_FAILURES:
                logger.warning("Compact circuit breaker tripped session=%s, will not retry", session_id)
            # 失败时不创建新线程，仅递增失败计数
            fallback_state = {
                "compact_failures": failures,
            }
            return agent_thread_id, fallback_state

        compacted_messages = self._build_initial_messages(
            user_text=user_text,
            session_context=state_values.get("chain_snapshot") or session_context,
            qa_history=qa_history,
            memory_index=memory_index,
            conversation_summary=summary,
        )
        # 压缩后资源恢复：从旧消息中提取关键 read_resource/MCP 工具结果
        resource_context = self._extract_post_compact_context(state_values.get("messages") or [])
        if resource_context:
            # 插入到最后一条 HumanMessage 之前
            compacted_messages.insert(-1, SystemMessage(content=resource_context))
        compacted_state = {
            **self._base_state_fields(session_id, user_id, token_quota),
            "messages": compacted_messages,
            "ambiguities": state_values.get("ambiguities") or [],
            "written_memory_names": state_values.get("written_memory_names") or [],
            "surfaced_memory_paths": state_values.get("surfaced_memory_paths") or [],
            "chain_length": state_values.get("chain_length", 0),
            "chain_snapshot": state_values.get("chain_snapshot") or session_context,
            "compact_summary": summary,
            "compact_generation": compact_generation,
            "compact_failures": 0,  # 成功时重置熔断计数
        }
        logger.info(
            "Compacted planning thread session=%s agentThread=%s -> %s generation=%s",
            session_id,
            agent_thread_id,
            compacted_thread_id,
            compact_generation,
        )
        return compacted_thread_id, compacted_state

    @staticmethod
    def _detect_direct_requirements(user_text: str) -> dict[str, bool]:
        text = user_text.lower()
        market = bool(re.search(r"(泰国|th\b|美国|us\b|印尼|id\b|马来|my\b|菲律宾|ph\b|越南|vn\b|新加坡|sg\b|英国|uk\b)", text))
        category = (
            "不限品类" in user_text
            or "全品类" in user_text
            or bool(re.search(r"(品类|类目|家居|家具|家电|美妆|服饰|服装|宠物|母婴|数码|3c|食品|箱包|鞋靴|户外|厨房)", text))
        )
        price_range = (
            "不限价格" in user_text
            or "价格不限" in user_text
            or bool(re.search(r"(价格|价位).*(不限|以上|以下|之间)", user_text))
            or bool(re.search(r"\d+\s*[-~到]\s*\d+\s*(元|美元|usd|泰铢|thb)", text))
        )
        scoring = (
            "打分" in user_text
            or "评分" in user_text
            or "权重" in user_text
            or "%" in user_text
            or bool(re.search(r"(销量增长|买家评价|买家评分|利润|增速|评论)", user_text))
        )
        top_n = bool(re.search(r"(top\s*\d+|前\s*\d+\s*(个|款|条|件)?|推荐\s*\d+\s*(个|款|条|件)?)", text))
        ai_comment = bool(re.search(r"(ai评语|选品评语|ai点评|生成评语|生成点评|不需要评语|需要评语)", user_text))
        return {
            "market": market,
            "category": category,
            "price_range": price_range,
            "scoring": scoring,
            "top_n": top_n,
            "ai_comment": ai_comment,
        }

    @staticmethod
    def _apply_qa_answers(requirements: dict[str, bool], qa_history: list[dict] | None) -> dict[str, bool]:
        if not qa_history:
            return requirements
        merged = {**requirements}
        for qa in qa_history:
            question = str(qa.get("q") or "")
            answer = str(qa.get("a") or "").strip()
            if not answer:
                continue
            if "市场" in question or "地区" in question:
                merged["market"] = True
            elif "品类" in question or "类目" in question:
                merged["category"] = True
            elif "价格" in question or "价位" in question:
                merged["price_range"] = True
            elif "评分" in question or "打分" in question or "看重" in question:
                merged["scoring"] = True
            elif "多少" in question or "Top" in question or "推荐数量" in question:
                merged["top_n"] = True
            elif "评语" in question or "点评" in question:
                merged["ai_comment"] = True
        return merged

    @staticmethod
    def _next_missing_requirement(user_text: str, qa_history: list[dict] | None) -> tuple[str, list[str]] | None:
        requirements = AgentService._apply_qa_answers(
            AgentService._detect_direct_requirements(user_text),
            qa_history,
        )
        ordered_questions: list[tuple[str, str, list[str]]] = [
            ("market", "你想看哪个目标市场？", ["泰国", "印度尼西亚", "马来西亚", "越南", "菲律宾", "新加坡", "美国", "英国", "沙特阿拉伯", "阿联酋"]),
            ("category", "你想聚焦哪个品类？", ["家居", "美妆", "不限品类"]),
            ("price_range", "价格区间有要求吗？", ["不限价格", "10-50美元", "50-100美元"]),
            ("scoring", "你最看重哪些评分维度？", ["销量增长", "买家评价", "利润空间"]),
            ("top_n", "你想推荐多少个商品？", ["Top20", "Top50", "Top100"]),
            ("ai_comment", "需要AI为每个商品生成选品评语吗？", ["需要", "不需要"]),
        ]
        for key, question, suggestions in ordered_questions:
            if not requirements.get(key):
                return question, suggestions
        return None

    async def parse_intent(
        self,
        session_id: str,
        user_text: str,
        user_id: str | None = None,
        session_context: dict | None = None,
        token_quota: int | None = None,
        qa_history: list[dict] | None = None,
        agent_thread_id: str | None = None,
        conversation_summary: str | None = None,
    ) -> dict:
        """
        运行 LangGraph ReAct 循环，将用户意图转换为 block_chain。总超时 600 秒。

        三条执行路径：
        1. checkpoint 存在 → 从 Redis 直接续接（正常路径）
        2. checkpoint 丢失 + qa_history 不空 → 从 Java 历史记录降级恢复（兜底路径）
        3. 无 checkpoint 且无 qa_history → 全新会话（加载 common 记忆注入上下文）

        记忆机制：
        - 新会话时加载 common 记忆索引注入 system prompt
        - 续接会话时加载 common + session 记忆索引（新消息追加，无需重注入）
        - finalize_chain 成功后异步触发蒸馏 Agent
        """
        current_thread_id = agent_thread_id or session_id
        logger.warning("parse_intent: session_id=%s agent_thread_id=%s current_thread_id=%s", session_id, agent_thread_id, current_thread_id)
        config = {"configurable": {"thread_id": current_thread_id}}
        mcp_client     = MCPClient(mcp_base_url=self._mcp_base_url)
        # 记忆系统由 MemoryAgent 管理，不再在 parse_intent 中直接操作 MemoryClient

        try:
            # 仅全新会话（无 agent_thread_id）才运行预检。
            # 续接会话时 checkpoint 已含历史上下文，LLM 可自行判断是否需要追问，
            # 跳过预检避免用户调整已有方案时被重复询问必填项。
            if not agent_thread_id:
                missing_requirement = self._next_missing_requirement(user_text, qa_history)
                if missing_requirement:
                    question, suggestions = missing_requirement
                    logger.warning("Precheck asks user session=%s question=%s", session_id, question)
                    # 不创建 checkpoint，不返回 agent_thread_id
                    # 前端下次 sendAskReply 仍然 agentThreadId=undefined → 再走 precheck
                    # 直到 qa_history 收齐所有必问项后才进入 LangGraph 图
                    return {
                        "success": True,
                        "type": "needs_input",
                        "message": question,
                        "suggestions": suggestions,
                        "llm_tokens_used": 0,
                        "iterations": 0,
                        "conversation_summary": conversation_summary,
                    }

            # 如果后台压缩正在删除+重建 checkpoint，等待完成后再读取
            compact_lock = _get_compact_lock(current_thread_id)
            if compact_lock.locked():
                logger.warning("Waiting for compact to finish thread=%s", current_thread_id)
                async with compact_lock:
                    pass  # 压缩完成，锁释放

            graph = self._build_graph(mcp_client)
            existing = await graph.aget_state(config)
            is_new_session = not existing.values.get("messages")
            needs_compact = False
            restore_context = session_context
            logger.warning("parse_intent: is_new_session=%s thread=%s msg_count=%d",
                          is_new_session, current_thread_id,
                          len(existing.values.get("messages") or []))

            if not is_new_session:
                # 路径1：checkpoint 存在，续接会话
                # 每次续接都加载记忆，确保能读取到跨会话的用户偏好和本次项目决策
                restore_context = existing.values.get("chain_snapshot") or session_context
                await mcp_client.initialize(session_id, restore_context)
                logger.warning("Resuming checkpoint session=%s thread=%s", session_id, current_thread_id)

                # 记忆加载/筛选/注入已委托给 MemoryAgent，主 Agent 通过 query_memory 工具按需检索
                # 压缩改为异步后台执行，不阻塞当前请求
                needs_compact = self._should_compact_state(existing.values)
                if needs_compact:
                    logger.warning("Compact triggered thread=%s msg_count=%d",
                                  current_thread_id, len(existing.values.get("messages") or []))

                initial_state = {
                    "messages": [HumanMessage(content=user_text)],
                    "done": False, "result": None, "action": None,
                    "iterations": 0, "total_tokens": 0,
                    "prompt_tokens": 0, "completion_tokens": 0,
                    "token_quota": token_quota,
                }
            else:
                # 路径2/3：新会话——MCP 初始化（记忆加载已委托给 MemoryAgent）
                await mcp_client.initialize(session_id, session_context)

                if qa_history:
                    logger.warning("Checkpoint missing session=%s, recovering from qa_history len=%d",
                                   session_id, len(qa_history))
                    msgs = self._build_initial_messages(
                        user_text, session_context, qa_history,
                        conversation_summary=conversation_summary,
                    )
                else:
                    msgs = self._build_initial_messages(
                        user_text, session_context,
                        conversation_summary=conversation_summary,
                    )
                initial_state = {**self._base_state_fields(session_id, user_id, token_quota),
                                 "messages": msgs,
                                 "compact_summary": conversation_summary}

            last_state, timed_out = await self._run_graph(graph, initial_state, config, session_id)

            if last_state is None:
                return {"success": False, "message": f"请求超时（单轮LLM规划超过{int(LLM_NODE_TIMEOUT_SECONDS)}秒），未能启动积木链构建",
                        "llm_tokens_used": 0, "iterations": 0, "agent_thread_id": current_thread_id,
                        "conversation_summary": conversation_summary}

            result = self._build_parse_result(last_state, timed_out)
            result["agent_thread_id"] = current_thread_id

            # 后台异步压缩：不阻塞返回，下次请求时使用压缩后的 checkpoint
            if needs_compact and last_state:
                import asyncio as _aio
                _aio.create_task(self._async_compact_and_rebuild(
                    thread_id=current_thread_id,
                    state_values=last_state,
                    session_id=session_id,
                    user_id=user_id,
                    session_context=restore_context,
                    qa_history=qa_history,
                    token_quota=token_quota,
                ))

            return result

        except Exception as e:
            logger.error("AgentService.parse_intent failed: %s", e, exc_info=True)
            return {"success": False, "message": str(e), "llm_tokens_used": 0, "iterations": 0}
        finally:
            await mcp_client.close()
            if self._memory_agent:
                await self._memory_agent.close()
