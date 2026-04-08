-- ============================================================
-- TikTok AI 选品系统 — 完整初始化脚本（幂等，可重复执行）
-- 合并 V1~V5 + intent_parse_log 扩展字段 + MCP工具配置 + 用户记忆文件索引
-- 最后更新：2026-04-05
-- ============================================================

-- ============================================================
-- 1. Schema
-- ============================================================
CREATE SCHEMA IF NOT EXISTS db_core;
CREATE SCHEMA IF NOT EXISTS db_session;
CREATE SCHEMA IF NOT EXISTS db_platform;
CREATE SCHEMA IF NOT EXISTS db_analytics;
CREATE SCHEMA IF NOT EXISTS db_cache;

-- ============================================================
-- 2. 公共触发器函数
-- ============================================================
CREATE OR REPLACE FUNCTION update_time_trigger()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- 3. db_core — 用户与方案
-- ============================================================

CREATE TABLE IF NOT EXISTS db_core."user" (
    id              VARCHAR(36) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    name            VARCHAR(64) DEFAULT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    avatar_url      VARCHAR(512) DEFAULT NULL,
    tier_id         VARCHAR(36) NOT NULL,
    role            VARCHAR(16) NOT NULL DEFAULT 'user',
    status          VARCHAR(16) NOT NULL DEFAULT 'active',
    last_login_time TIMESTAMPTZ DEFAULT NULL,
    create_time     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delete_time     TIMESTAMPTZ DEFAULT NULL,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_email ON db_core."user" (email);
CREATE INDEX IF NOT EXISTS idx_user_tier ON db_core."user" (tier_id);
CREATE INDEX IF NOT EXISTS idx_user_status ON db_core."user" (status) WHERE delete_time IS NULL;
DROP TRIGGER IF EXISTS trg_user_update_time ON db_core."user";
CREATE TRIGGER trg_user_update_time BEFORE UPDATE ON db_core."user" FOR EACH ROW EXECUTE FUNCTION update_time_trigger();

CREATE TABLE IF NOT EXISTS db_core.user_tier (
    id                      VARCHAR(36) NOT NULL,
    name                    VARCHAR(32) NOT NULL,
    display_name            VARCHAR(64) NOT NULL,
    monthly_api_quota       INTEGER NOT NULL DEFAULT 0,
    monthly_token_quota     BIGINT NOT NULL DEFAULT 0,
    max_concurrent_sessions INTEGER NOT NULL DEFAULT 1,
    max_api_per_session     INTEGER NOT NULL DEFAULT 100,
    max_token_per_session   BIGINT NOT NULL DEFAULT 50000,
    max_products_per_query  INTEGER NOT NULL DEFAULT 100,
    max_saved_plans         INTEGER NOT NULL DEFAULT 3,
    price_monthly           DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    features                JSONB NOT NULL,
    sort_order              INTEGER NOT NULL DEFAULT 0,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    create_time             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_tier_name ON db_core.user_tier (name);
DROP TRIGGER IF EXISTS trg_user_tier_update_time ON db_core.user_tier;
CREATE TRIGGER trg_user_tier_update_time BEFORE UPDATE ON db_core.user_tier FOR EACH ROW EXECUTE FUNCTION update_time_trigger();

CREATE TABLE IF NOT EXISTS db_core.user_plan (
    id                  VARCHAR(36) NOT NULL,
    user_id             VARCHAR(36) NOT NULL,
    name                VARCHAR(128) NOT NULL,
    description         TEXT DEFAULT NULL,
    source_text         TEXT DEFAULT NULL,
    block_chain         JSONB NOT NULL,
    variable_params     JSONB DEFAULT NULL,
    tags                JSONB DEFAULT NULL,
    source_type         VARCHAR(32) DEFAULT NULL,
    source_preset_id    VARCHAR(36) DEFAULT NULL,
    source_user_plan_id VARCHAR(36) DEFAULT NULL,
    is_public           BOOLEAN NOT NULL DEFAULT FALSE,
    use_count           INTEGER NOT NULL DEFAULT 0,
    last_used_time      TIMESTAMPTZ DEFAULT NULL,
    create_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delete_time         TIMESTAMPTZ DEFAULT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_plan_user ON db_core.user_plan (user_id);
CREATE INDEX IF NOT EXISTS idx_plan_public ON db_core.user_plan (is_public, use_count) WHERE delete_time IS NULL;
DROP TRIGGER IF EXISTS trg_user_plan_update_time ON db_core.user_plan;
CREATE TRIGGER trg_user_plan_update_time BEFORE UPDATE ON db_core.user_plan FOR EACH ROW EXECUTE FUNCTION update_time_trigger();

CREATE TABLE IF NOT EXISTS db_core.score_template (
    id              VARCHAR(36) NOT NULL,
    user_id         VARCHAR(36) NOT NULL,
    name            VARCHAR(64) NOT NULL,
    block_type      VARCHAR(8) NOT NULL,
    config          JSONB NOT NULL,
    description     TEXT DEFAULT NULL,
    use_count       INTEGER NOT NULL DEFAULT 0,
    is_public       BOOLEAN NOT NULL DEFAULT FALSE,
    create_time     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delete_time     TIMESTAMPTZ DEFAULT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_score_tpl_user ON db_core.score_template (user_id);
DROP TRIGGER IF EXISTS trg_score_template_update_time ON db_core.score_template;
CREATE TRIGGER trg_score_template_update_time BEFORE UPDATE ON db_core.score_template FOR EACH ROW EXECUTE FUNCTION update_time_trigger();

-- ============================================================
-- 4. db_session — 选品会话
-- ============================================================

CREATE TABLE IF NOT EXISTS db_session.session (
    id                  VARCHAR(36) NOT NULL,
    user_id             VARCHAR(36) NOT NULL,
    title               VARCHAR(128) DEFAULT NULL,
    status              VARCHAR(24) NOT NULL DEFAULT 'created',
    current_step        INTEGER NOT NULL DEFAULT 0,
    source_text         TEXT DEFAULT NULL,
    source_type         VARCHAR(24) DEFAULT NULL,
    source_plan_id      VARCHAR(36) DEFAULT NULL,
    matched_preset      VARCHAR(32) DEFAULT NULL,
    block_chain         JSONB NOT NULL,
    echotik_api_calls   INTEGER NOT NULL DEFAULT 0,
    llm_input_tokens    BIGINT NOT NULL DEFAULT 0,
    llm_output_tokens   BIGINT NOT NULL DEFAULT 0,
    llm_total_tokens    BIGINT NOT NULL DEFAULT 0,
    echotik_key_ids_used JSONB DEFAULT NULL,
    conversation_snapshot JSONB DEFAULT NULL,
    remark              TEXT DEFAULT NULL,
    start_time          TIMESTAMPTZ DEFAULT NULL,
    complete_time       TIMESTAMPTZ DEFAULT NULL,
    create_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delete_time         TIMESTAMPTZ DEFAULT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_session_user_time ON db_session.session (user_id, create_time);
CREATE INDEX IF NOT EXISTS idx_session_user_status ON db_session.session (user_id, status) WHERE delete_time IS NULL;
CREATE INDEX IF NOT EXISTS idx_session_conversation_snapshot ON db_session.session USING GIN (conversation_snapshot);
DROP TRIGGER IF EXISTS trg_session_update_time ON db_session.session;
CREATE TRIGGER trg_session_update_time BEFORE UPDATE ON db_session.session FOR EACH ROW EXECUTE FUNCTION update_time_trigger();

COMMENT ON COLUMN db_session.session.conversation_snapshot IS '多智能体会话快照，包含完整对话记录、QA历史、规划摘要';
COMMENT ON COLUMN db_session.session.remark IS '用户备注信息';

-- 兼容迁移：审计结果 + 竞品分析
ALTER TABLE db_session.session ADD COLUMN IF NOT EXISTS audit_result        JSONB DEFAULT NULL;
ALTER TABLE db_session.session ADD COLUMN IF NOT EXISTS competitor_analysis  TEXT DEFAULT NULL;
COMMENT ON COLUMN db_session.session.audit_result IS '执行前审计结果: {pass, score, issues, suggestions}';
COMMENT ON COLUMN db_session.session.competitor_analysis IS '竞品洞察分析报告（Markdown）';

-- 兼容迁移：对话线程ID（LangGraph checkpoint 标识，用于会话级记忆隔离）
ALTER TABLE db_session.session ADD COLUMN IF NOT EXISTS agent_thread_id VARCHAR(64) DEFAULT NULL;
COMMENT ON COLUMN db_session.session.agent_thread_id IS '对话线程ID，整个对话生命周期唯一，用于会话级记忆隔离';

CREATE TABLE IF NOT EXISTS db_session.session_data (
    session_id          VARCHAR(36) NOT NULL,
    current_view        JSONB DEFAULT NULL,
    candidate_pool      JSONB DEFAULT NULL,
    update_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (session_id)
);
DROP TRIGGER IF EXISTS trg_session_data_update_time ON db_session.session_data;
CREATE TRIGGER trg_session_data_update_time BEFORE UPDATE ON db_session.session_data FOR EACH ROW EXECUTE FUNCTION update_time_trigger();

CREATE TABLE IF NOT EXISTS db_session.session_step (
    id                  BIGSERIAL NOT NULL,
    session_id          VARCHAR(36) NOT NULL,
    branch_id           VARCHAR(32) NOT NULL DEFAULT 'main',
    seq                 INTEGER NOT NULL,
    block_id            VARCHAR(64) NOT NULL,
    block_config        JSONB NOT NULL,
    label               VARCHAR(128) DEFAULT NULL,
    input_count         INTEGER DEFAULT NULL,
    output_count        INTEGER DEFAULT NULL,
    product_ids         JSONB DEFAULT NULL,
    echotik_api_calls   INTEGER NOT NULL DEFAULT 0,
    llm_input_tokens    INTEGER NOT NULL DEFAULT 0,
    llm_output_tokens   INTEGER NOT NULL DEFAULT 0,
    llm_total_tokens    INTEGER NOT NULL DEFAULT 0,
    echotik_key_id      VARCHAR(36) DEFAULT NULL,
    api_endpoints_called JSONB DEFAULT NULL,
    duration_ms         INTEGER DEFAULT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'completed',
    error_message       TEXT DEFAULT NULL,
    create_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_step_session_branch_seq ON db_session.session_step (session_id, branch_id, seq);
CREATE INDEX IF NOT EXISTS idx_step_session ON db_session.session_step (session_id);

CREATE TABLE IF NOT EXISTS db_session.session_step_snapshot (
    step_id             BIGINT NOT NULL,
    session_id          VARCHAR(36) NOT NULL,
    snapshot_type       VARCHAR(16) NOT NULL,
    snapshot_data       JSONB DEFAULT NULL,
    snapshot_ids        JSONB DEFAULT NULL,
    create_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (step_id)
);
CREATE INDEX IF NOT EXISTS idx_snapshot_session ON db_session.session_step_snapshot (session_id);

CREATE TABLE IF NOT EXISTS db_session.session_branch (
    id                  VARCHAR(36) NOT NULL,
    session_id          VARCHAR(36) NOT NULL,
    branch_id           VARCHAR(32) NOT NULL,
    parent_step_seq     INTEGER NOT NULL,
    trigger_text        TEXT DEFAULT NULL,
    block_chain         JSONB DEFAULT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'active',
    merged_products     JSONB DEFAULT NULL,
    create_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_branch_session ON db_session.session_branch (session_id, branch_id);
CREATE INDEX IF NOT EXISTS idx_branch_session ON db_session.session_branch (session_id);

CREATE TABLE IF NOT EXISTS db_session.session_message (
    id                  BIGSERIAL NOT NULL,
    session_id          VARCHAR(36) NOT NULL,
    role                VARCHAR(16) NOT NULL,
    content             TEXT NOT NULL,
    parsed_action       JSONB DEFAULT NULL,
    llm_tokens_used     INTEGER NOT NULL DEFAULT 0,
    create_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_message_session ON db_session.session_message (session_id, create_time);

-- intent_parse_log（含内测扩展字段）
CREATE TABLE IF NOT EXISTS db_session.intent_parse_log (
    id                BIGSERIAL PRIMARY KEY,
    user_id           VARCHAR(36),
    build_session_id  VARCHAR(64),
    llm_tokens_used   INTEGER DEFAULT 0,
    iterations        INTEGER DEFAULT 0,
    api_calls         INTEGER DEFAULT 0,
    success           BOOLEAN DEFAULT FALSE,
    user_text         TEXT,
    qa_history        JSONB,
    response_type     VARCHAR(32),
    response_message  TEXT,
    block_chain       JSONB,
    suggestions       JSONB,
    create_time       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_parse_log_user ON db_session.intent_parse_log (user_id);
CREATE INDEX IF NOT EXISTS idx_parse_log_time ON db_session.intent_parse_log (create_time);

-- 兼容：如果表已存在但缺少扩展字段，补建
ALTER TABLE db_session.intent_parse_log ADD COLUMN IF NOT EXISTS user_text         TEXT;
ALTER TABLE db_session.intent_parse_log ADD COLUMN IF NOT EXISTS qa_history        JSONB;
ALTER TABLE db_session.intent_parse_log ADD COLUMN IF NOT EXISTS response_type     VARCHAR(32);
ALTER TABLE db_session.intent_parse_log ADD COLUMN IF NOT EXISTS response_message  TEXT;
ALTER TABLE db_session.intent_parse_log ADD COLUMN IF NOT EXISTS block_chain       JSONB;
ALTER TABLE db_session.intent_parse_log ADD COLUMN IF NOT EXISTS suggestions       JSONB;

-- ============================================================
-- 5. db_platform — 平台配置
-- ============================================================

CREATE TABLE IF NOT EXISTS db_platform.llm_config (
    id                  VARCHAR(36) NOT NULL,
    name                VARCHAR(64) NOT NULL,
    provider            VARCHAR(32) NOT NULL,
    base_url            VARCHAR(512) NOT NULL,
    api_key_encrypted   TEXT NOT NULL,
    model               VARCHAR(128) NOT NULL,
    max_tokens          INTEGER NOT NULL DEFAULT 4096,
    is_active           BOOLEAN NOT NULL DEFAULT FALSE,
    priority            INTEGER NOT NULL DEFAULT 100,
    monthly_token_limit BIGINT NOT NULL DEFAULT -1,
    monthly_tokens_used BIGINT NOT NULL DEFAULT 0,
    config_extra        JSONB DEFAULT NULL,
    create_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);
DROP TRIGGER IF EXISTS trg_llm_config_update_time ON db_platform.llm_config;
CREATE TRIGGER trg_llm_config_update_time BEFORE UPDATE ON db_platform.llm_config FOR EACH ROW EXECUTE FUNCTION update_time_trigger();

CREATE TABLE IF NOT EXISTS db_platform.echotik_api_key (
    id                      VARCHAR(36) NOT NULL,
    name                    VARCHAR(64) NOT NULL,
    api_key_encrypted       TEXT NOT NULL,
    api_secret_encrypted    TEXT NOT NULL,
    total_calls             INTEGER NOT NULL DEFAULT 0,
    remaining_calls         INTEGER NOT NULL DEFAULT 0,
    alert_threshold         INTEGER NOT NULL DEFAULT 1000,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_time          TIMESTAMPTZ DEFAULT NULL,
    create_time             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);
DROP TRIGGER IF EXISTS trg_echotik_api_key_update_time ON db_platform.echotik_api_key;
CREATE TRIGGER trg_echotik_api_key_update_time BEFORE UPDATE ON db_platform.echotik_api_key FOR EACH ROW EXECUTE FUNCTION update_time_trigger();

CREATE TABLE IF NOT EXISTS db_platform.preset_package (
    id                  VARCHAR(36) NOT NULL,
    pkg_code            VARCHAR(32) NOT NULL,
    name_zh             VARCHAR(64) NOT NULL,
    name_en             VARCHAR(64) DEFAULT NULL,
    description         TEXT DEFAULT NULL,
    block_chain         JSONB NOT NULL,
    tags                JSONB DEFAULT NULL,
    use_count           INTEGER NOT NULL DEFAULT 0,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order          INTEGER NOT NULL DEFAULT 0,
    create_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_preset_code ON db_platform.preset_package (pkg_code);
DROP TRIGGER IF EXISTS trg_preset_package_update_time ON db_platform.preset_package;
CREATE TRIGGER trg_preset_package_update_time BEFORE UPDATE ON db_platform.preset_package FOR EACH ROW EXECUTE FUNCTION update_time_trigger();

CREATE TABLE IF NOT EXISTS db_platform.block_registry (
    block_id            VARCHAR(8) NOT NULL,
    block_type          VARCHAR(16) NOT NULL,
    name_zh             VARCHAR(64) NOT NULL,
    name_en             VARCHAR(64) DEFAULT NULL,
    description         TEXT DEFAULT NULL,
    input_type          VARCHAR(32) DEFAULT NULL,
    output_type         VARCHAR(32) DEFAULT NULL,
    config_schema       JSONB NOT NULL,
    echotik_api         VARCHAR(128) DEFAULT NULL,
    is_llm_required     BOOLEAN NOT NULL DEFAULT FALSE,
    estimated_cost      VARCHAR(32) DEFAULT NULL,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order          INTEGER NOT NULL DEFAULT 0,
    create_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (block_id)
);
DROP TRIGGER IF EXISTS trg_block_registry_update_time ON db_platform.block_registry;
CREATE TRIGGER trg_block_registry_update_time BEFORE UPDATE ON db_platform.block_registry FOR EACH ROW EXECUTE FUNCTION update_time_trigger();

-- ============================================================
-- 6. db_analytics — 用量统计
-- ============================================================

CREATE TABLE IF NOT EXISTS db_analytics.user_usage_daily (
    id                  BIGSERIAL NOT NULL,
    user_id             VARCHAR(36) NOT NULL,
    usage_date          DATE NOT NULL,
    api_calls           INTEGER NOT NULL DEFAULT 0,
    llm_input_tokens    BIGINT NOT NULL DEFAULT 0,
    llm_output_tokens   BIGINT NOT NULL DEFAULT 0,
    llm_total_tokens    BIGINT NOT NULL DEFAULT 0,
    sessions_created    INTEGER NOT NULL DEFAULT 0,
    update_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_usage_daily ON db_analytics.user_usage_daily (user_id, usage_date);
DROP TRIGGER IF EXISTS trg_user_usage_daily_update_time ON db_analytics.user_usage_daily;
CREATE TRIGGER trg_user_usage_daily_update_time BEFORE UPDATE ON db_analytics.user_usage_daily FOR EACH ROW EXECUTE FUNCTION update_time_trigger();

CREATE TABLE IF NOT EXISTS db_analytics.user_usage_monthly (
    id                  BIGSERIAL NOT NULL,
    user_id             VARCHAR(36) NOT NULL,
    year_month          VARCHAR(7) NOT NULL,
    api_calls_used      INTEGER NOT NULL DEFAULT 0,
    tokens_used         BIGINT NOT NULL DEFAULT 0,
    sessions_created    INTEGER NOT NULL DEFAULT 0,
    plans_created       INTEGER NOT NULL DEFAULT 0,
    update_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_usage_monthly ON db_analytics.user_usage_monthly (user_id, year_month);
DROP TRIGGER IF EXISTS trg_user_usage_monthly_update_time ON db_analytics.user_usage_monthly;
CREATE TRIGGER trg_user_usage_monthly_update_time BEFORE UPDATE ON db_analytics.user_usage_monthly FOR EACH ROW EXECUTE FUNCTION update_time_trigger();

CREATE TABLE IF NOT EXISTS db_analytics.echotik_key_usage_log (
    id                  BIGSERIAL NOT NULL,
    key_id              VARCHAR(36) NOT NULL,
    session_id          VARCHAR(36) NOT NULL,
    user_id             VARCHAR(36) NOT NULL,
    api_endpoint        VARCHAR(128) NOT NULL,
    calls_count         INTEGER NOT NULL DEFAULT 1,
    create_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_key_log_key_time ON db_analytics.echotik_key_usage_log (key_id, create_time);
CREATE INDEX IF NOT EXISTS idx_key_log_time ON db_analytics.echotik_key_usage_log (create_time);

-- ============================================================
-- 7. db_cache — 数据缓存
-- ============================================================

CREATE TABLE IF NOT EXISTS db_cache.product_cache (
    product_id          VARCHAR(32) NOT NULL,
    region              VARCHAR(8) NOT NULL,
    data                JSONB NOT NULL,
    data_source         VARCHAR(32) DEFAULT NULL,
    fetch_time          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expire_time         TIMESTAMPTZ NOT NULL,
    create_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (product_id, region)
);
CREATE INDEX IF NOT EXISTS idx_cache_expires ON db_cache.product_cache (expire_time);
DROP TRIGGER IF EXISTS trg_product_cache_update_time ON db_cache.product_cache;
CREATE TRIGGER trg_product_cache_update_time BEFORE UPDATE ON db_cache.product_cache FOR EACH ROW EXECUTE FUNCTION update_time_trigger();

CREATE TABLE IF NOT EXISTS db_cache.echotik_category (
    category_id         VARCHAR(16) NOT NULL,
    region              VARCHAR(8) NOT NULL,
    parent_id           VARCHAR(16) DEFAULT NULL,
    level               INTEGER NOT NULL,
    name_en             VARCHAR(128) DEFAULT NULL,
    name_zh             VARCHAR(128) DEFAULT NULL,
    create_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (category_id, region)
);
DROP TRIGGER IF EXISTS trg_echotik_category_update_time ON db_cache.echotik_category;
CREATE TRIGGER trg_echotik_category_update_time BEFORE UPDATE ON db_cache.echotik_category FOR EACH ROW EXECUTE FUNCTION update_time_trigger();

-- ============================================================
-- 8. 初始种子数据
-- ============================================================

-- 等级
INSERT INTO db_core.user_tier (id, name, display_name, monthly_api_quota, monthly_token_quota, max_concurrent_sessions, max_api_per_session, max_token_per_session, max_products_per_query, max_saved_plans, price_monthly, features, sort_order) VALUES
('tier-free-0001', 'free', '免费版', 500, 100000, 1, 50, 20000, 50, 3, 0.00, '{"llm_scoring":false,"excel_export":false,"branch":false}', 1),
('tier-basic-0001', 'basic', '基础版', 5000, 500000, 2, 200, 100000, 200, 10, 99.00, '{"llm_scoring":true,"excel_export":true,"branch":false}', 2),
('tier-pro-0001', 'pro', '专业版', 20000, 2000000, 5, 500, 500000, 500, 50, 299.00, '{"llm_scoring":true,"excel_export":true,"branch":true}', 3),
('tier-ent-0001', 'enterprise', '企业版', -1, -1, -1, -1, -1, -1, -1, 999.00, '{"llm_scoring":true,"excel_export":true,"branch":true}', 4)
ON CONFLICT (id) DO NOTHING;

-- 默认管理员 (password: admin123456)
INSERT INTO db_core."user" (id, email, name, password_hash, tier_id, role, status) VALUES
('admin-0001-0001-0001-000000000001', 'admin@tiktok-selection.com', 'Admin', '$2a$10$c2Q6jJyJ5OoZcC8m95mn4uGyeiLdxEW2HPMKCZ2vV.R7vPZ6TwxTu', 'tier-ent-0001', 'admin', 'active')
ON CONFLICT (id) DO NOTHING;

-- 积木块注册（V2 + V3）
INSERT INTO db_platform.block_registry (block_id, block_type, name_zh, name_en, description, input_type, output_type, config_schema, echotik_api, is_llm_required, estimated_cost, is_active, sort_order) VALUES
('DS01', 'DATA_SOURCE', '商品列表筛选', 'Product List Filter', '按条件筛选TikTok商品列表', NULL, 'product_list', '{"type":"object","properties":{"region":{"type":"string"},"categoryId":{"type":"string"},"priceMin":{"type":"number"},"priceMax":{"type":"number"},"pageSize":{"type":"integer","default":50}}}', 'product/list', FALSE, '1 call/page', TRUE, 1),
('DS02', 'DATA_SOURCE', '商品排行榜', 'Product Ranking', '获取TikTok商品排行数据', NULL, 'product_list', '{"type":"object","properties":{"region":{"type":"string"},"rankingType":{"type":"string","enum":["hot_sale","hot_promotion"]},"rankingPeriod":{"type":"string","enum":["day","week","month"]},"categoryId":{"type":"string"},"topN":{"type":"integer","default":100}}}', 'product/ranking', FALSE, '1 call', TRUE, 2),
('DS03', 'DATA_SOURCE', '达人列表筛选', 'Influencer List Filter', '按条件筛选TikTok达人列表', NULL, 'influencer_list', '{"type":"object","properties":{"region":{"type":"string"},"categoryId":{"type":"string"},"followerMin":{"type":"integer"},"followerMax":{"type":"integer"},"pageSize":{"type":"integer","default":50}}}', 'influencer/list', FALSE, '1 call/page', TRUE, 3),
('DS07', 'DATA_SOURCE', '视频数据', 'Video Data', '获取TikTok视频数据', NULL, 'video_list', '{"type":"object","properties":{"region":{"type":"string"},"keyword":{"type":"string"},"publishDays":{"type":"integer"},"pageSize":{"type":"integer","default":50}}}', 'video/list', FALSE, '1 call/page', TRUE, 7),
('FS01', 'FIELD_SELECT', '字段裁剪', 'Field Trim', '保留指定字段,裁剪其余字段', '*', NULL, '{"type":"object","properties":{"keepFields":{"type":"array","items":{"type":"string"}}}}', NULL, FALSE, 'free', TRUE, 20),
('CM01', 'COMPUTE', '增长率计算', 'Growth Rate', '计算两个字段的增长率', 'product_list', NULL, '{"type":"object","properties":{"fieldA":{"type":"string"},"fieldB":{"type":"string"},"outputFieldName":{"type":"string","default":"growth_rate"}}}', NULL, FALSE, 'free', TRUE, 30),
('CM02', 'COMPUTE', '利润率计算', 'Profit Margin', '计算价格与成本的利润率', 'product_list', NULL, '{"type":"object","properties":{"priceField":{"type":"string"},"costField":{"type":"string"},"outputFieldName":{"type":"string","default":"profit_margin"}}}', NULL, FALSE, 'free', TRUE, 31),
('CM04', 'COMPUTE', '自定义公式', 'Custom Formula', '使用自定义公式计算新字段', '*', NULL, '{"type":"object","properties":{"formula":{"type":"string"},"outputFieldName":{"type":"string"}}}', NULL, FALSE, 'free', TRUE, 33),
('FT01', 'FILTER', '条件筛选', 'Filter', '按条件过滤数据行', '*', NULL, '{"type":"object","properties":{"field":{"type":"string"},"operator":{"type":"string","enum":[">",">=","<","<=","==","!=","between","in","contains"]},"value":{},"valueTo":{}}}', NULL, FALSE, 'free', TRUE, 40),
('ST01', 'SORT', '排序截取', 'Sort Top-N', '按字段排序并截取前N条', '*', NULL, '{"type":"object","properties":{"sortBy":{"type":"string"},"order":{"type":"string","enum":["desc","asc"],"default":"desc"},"topN":{"type":"integer","default":100}}}', NULL, FALSE, 'free', TRUE, 50),
('OUT01', 'OUTPUT', '最终输出', 'Final Output', '标记选品链完成并输出最终结果', '*', NULL, '{"type":"object","properties":{"summary":{"type":"string"}}}', NULL, FALSE, 'free', TRUE, 99),
('SC00', 'SCORE', '综合评分汇总', 'Score Aggregator', '将多个维度评分加权求和，生成 total_score 字段', '*', NULL, '{"type":"object","required":["dimensions"],"properties":{"dimensions":{"type":"array","items":{"type":"object","required":["field","weight"],"properties":{"field":{"type":"string"},"weight":{"type":"number"}}}},"outputFieldName":{"type":"string","default":"total_score"}}}', NULL, FALSE, 'free', TRUE, 60),
('SC01', 'SCORE', '数值评分', 'Numeric Score', '将数值字段映射为标准化评分（线性/分段/反向映射）', '*', NULL, '{"type":"object","required":["field","scoreType"],"properties":{"field":{"type":"string"},"scoreType":{"type":"string","enum":["linear_map","tier_map","inverse_map"]},"maxScore":{"type":"integer","default":100},"tiers":{"type":"array"},"outputFieldName":{"type":"string"}}}', NULL, FALSE, 'free', TRUE, 61),
('SC02', 'SCORE', 'LLM语义评分', 'Semantic Score', '调用LLM对商品进行语义理解评分', 'product_list', NULL, '{"type":"object","required":["evalPrompt"],"properties":{"evalPrompt":{"type":"string"},"maxScore":{"type":"integer","default":100},"outputFieldName":{"type":"string","default":"semantic_score"},"batchSize":{"type":"integer","default":5}}}', NULL, TRUE, '~0.01$/item', TRUE, 62),
('LA01', 'LABEL', 'AI评语生成', 'AI Comment', '调用LLM为每个商品生成选品评语，输出 ai_comment 字段', 'product_list', NULL, '{"type":"object","properties":{"language":{"type":"string","enum":["zh","en"],"default":"zh"},"maxChars":{"type":"integer","default":100},"batchSize":{"type":"integer","default":20}}}', NULL, TRUE, '~0.005$/item', TRUE, 70),
('SS01', 'SNAPSHOT', '暂停等待', 'Pause & Wait', '暂停选品链执行，等待用户审核后继续', '*', NULL, '{"type":"object","properties":{"message":{"type":"string","default":"请审核当前结果，确认后继续执行"}}}', NULL, FALSE, 'free', TRUE, 80)
ON CONFLICT (block_id) DO NOTHING;

-- 预设方案包（V4）
INSERT INTO db_platform.preset_package (id, pkg_code, name_zh, name_en, description, block_chain, tags, sort_order) VALUES
(gen_random_uuid(), 'BLUE_OCEAN_RANK', '蓝海爆品榜单', 'Blue Ocean Ranking', '从TikTok商品排行榜抓取数据，通过增长率评分和综合评分筛选蓝海爆款，输出Top100商品', '[{"blockId":"DS02","config":{"region":"US","rankingType":"hot_sale","rankingPeriod":"week","topN":200}},{"blockId":"CM01","config":{"fieldA":"sales_week","fieldB":"sales_last_week","outputFieldName":"growth_rate"}},{"blockId":"FT01","config":{"field":"growth_rate","operator":">","value":0.1}},{"blockId":"SC01","config":{"field":"growth_rate","scoreType":"linear_map","maxScore":100,"outputFieldName":"growth_score"}},{"blockId":"SC01","config":{"field":"sales_week","scoreType":"linear_map","maxScore":100,"outputFieldName":"sales_score"}},{"blockId":"SC00","config":{"dimensions":[{"field":"growth_score","weight":0.6},{"field":"sales_score","weight":0.4}],"outputFieldName":"total_score"}},{"blockId":"ST01","config":{"sortBy":"total_score","order":"desc","topN":100}},{"blockId":"OUT01","config":{"summary":"蓝海爆品榜单 Top100"}}]'::jsonb, '["蓝海","爆品","榜单"]'::jsonb, 1),
(gen_random_uuid(), 'BLUE_OCEAN_LIST', '蓝海爆品列表', 'Blue Ocean Products', '从商品列表中筛选增长迅猛且利润率高的蓝海商品，适合日常选品巡查', '[{"blockId":"DS01","config":{"region":"US","pageSize":100}},{"blockId":"CM01","config":{"fieldA":"sales_week","fieldB":"sales_last_week","outputFieldName":"growth_rate"}},{"blockId":"CM02","config":{"priceField":"price","costField":"cost","outputFieldName":"profit_margin"}},{"blockId":"FT01","config":{"field":"growth_rate","operator":">","value":0.05}},{"blockId":"FT01","config":{"field":"profit_margin","operator":">","value":0.2}},{"blockId":"ST01","config":{"sortBy":"growth_rate","order":"desc","topN":50}},{"blockId":"OUT01","config":{"summary":"蓝海爆品列表 Top50"}}]'::jsonb, '["蓝海","爆品","列表"]'::jsonb, 2),
(gen_random_uuid(), 'THAILAND_LOCAL', '泰国本地化', 'Thailand Localization', '专为泰国市场设计，通过AI语义评分分析商品的本地化契合度', '[{"blockId":"DS01","config":{"region":"TH","pageSize":100}},{"blockId":"FT01","config":{"field":"sales_week","operator":">","value":100}},{"blockId":"SC02","config":{"evalPrompt":"请从泰国市场本地化视角评估该商品","maxScore":100,"outputFieldName":"localization_score","batchSize":10}},{"blockId":"SC01","config":{"field":"sales_week","scoreType":"linear_map","maxScore":100,"outputFieldName":"sales_score"}},{"blockId":"SC00","config":{"dimensions":[{"field":"localization_score","weight":0.7},{"field":"sales_score","weight":0.3}],"outputFieldName":"total_score"}},{"blockId":"LA01","config":{"language":"zh","maxChars":120,"batchSize":20}},{"blockId":"ST01","config":{"sortBy":"total_score","order":"desc","topN":50}},{"blockId":"OUT01","config":{"summary":"泰国本地化选品 Top50"}}]'::jsonb, '["泰国","本地化","东南亚"]'::jsonb, 3),
(gen_random_uuid(), 'INFLUENCER_REVERSE', '达人反查', 'Influencer Reverse Lookup', '筛选粉丝量适中、带货效率高的达人，分析其推广商品特征', '[{"blockId":"DS03","config":{"region":"US","followerMin":10000,"followerMax":500000,"pageSize":50}},{"blockId":"FT01","config":{"field":"gmv_rate","operator":">","value":0.02}},{"blockId":"SC01","config":{"field":"gmv_rate","scoreType":"linear_map","maxScore":100,"outputFieldName":"conversion_score"}},{"blockId":"SC01","config":{"field":"follower_count","scoreType":"inverse_map","maxScore":100,"outputFieldName":"niche_score"}},{"blockId":"SC00","config":{"dimensions":[{"field":"conversion_score","weight":0.6},{"field":"niche_score","weight":0.4}],"outputFieldName":"total_score"}},{"blockId":"ST01","config":{"sortBy":"total_score","order":"desc","topN":30}},{"blockId":"OUT01","config":{"summary":"高带货潜力达人 Top30"}}]'::jsonb, '["达人","反查","KOL"]'::jsonb, 4),
(gen_random_uuid(), 'COMPETITOR_SHOP', '竞品店铺', 'Competitor Store Analysis', '抓取竞品方向的商品数据，分析利润结构和销量表现', '[{"blockId":"DS01","config":{"region":"US","pageSize":100}},{"blockId":"CM02","config":{"priceField":"price","costField":"cost","outputFieldName":"profit_margin"}},{"blockId":"CM01","config":{"fieldA":"sales_week","fieldB":"sales_last_week","outputFieldName":"growth_rate"}},{"blockId":"FT01","config":{"field":"profit_margin","operator":">","value":0.15}},{"blockId":"SS01","config":{"message":"请审核筛选结果"}},{"blockId":"SC01","config":{"field":"profit_margin","scoreType":"linear_map","maxScore":100,"outputFieldName":"profit_score"}},{"blockId":"SC01","config":{"field":"growth_rate","scoreType":"linear_map","maxScore":100,"outputFieldName":"growth_score"}},{"blockId":"SC00","config":{"dimensions":[{"field":"profit_score","weight":0.5},{"field":"growth_score","weight":0.5}],"outputFieldName":"total_score"}},{"blockId":"ST01","config":{"sortBy":"total_score","order":"desc","topN":50}},{"blockId":"OUT01","config":{"summary":"竞品店铺分析 Top50"}}]'::jsonb, '["竞品","店铺","利润"]'::jsonb, 5),
(gen_random_uuid(), 'VIRAL_VIDEO', '爆款视频', 'Viral Video Products', '抓取近期高播放量视频数据，挖掘内容驱动的商品机会', '[{"blockId":"DS07","config":{"region":"US","publishDays":7,"pageSize":100}},{"blockId":"FT01","config":{"field":"view_count","operator":">","value":100000}},{"blockId":"CM01","config":{"fieldA":"view_count","fieldB":"like_count","outputFieldName":"engagement_rate"}},{"blockId":"SC01","config":{"field":"view_count","scoreType":"linear_map","maxScore":100,"outputFieldName":"view_score"}},{"blockId":"SC01","config":{"field":"engagement_rate","scoreType":"linear_map","maxScore":100,"outputFieldName":"engagement_score"}},{"blockId":"SC00","config":{"dimensions":[{"field":"view_score","weight":0.4},{"field":"engagement_score","weight":0.6}],"outputFieldName":"total_score"}},{"blockId":"ST01","config":{"sortBy":"total_score","order":"desc","topN":50}},{"blockId":"OUT01","config":{"summary":"爆款视频选品 Top50"}}]'::jsonb, '["视频","爆款","内容"]'::jsonb, 6),
(gen_random_uuid(), 'TOPIC_TREND', '话题趋势', 'Topic Trend Products', '结合热门话题和趋势关键词，筛选正在上升期的商品', '[{"blockId":"DS01","config":{"region":"US","pageSize":100}},{"blockId":"CM01","config":{"fieldA":"search_volume_week","fieldB":"search_volume_last_week","outputFieldName":"search_growth"}},{"blockId":"FT01","config":{"field":"search_growth","operator":">","value":0.2}},{"blockId":"SC02","config":{"evalPrompt":"请评估该商品与当前社交媒体热门话题的契合度","maxScore":100,"outputFieldName":"trend_score","batchSize":10}},{"blockId":"SC01","config":{"field":"search_growth","scoreType":"linear_map","maxScore":100,"outputFieldName":"growth_score"}},{"blockId":"SC00","config":{"dimensions":[{"field":"trend_score","weight":0.6},{"field":"growth_score","weight":0.4}],"outputFieldName":"total_score"}},{"blockId":"ST01","config":{"sortBy":"total_score","order":"desc","topN":30}},{"blockId":"OUT01","config":{"summary":"话题趋势选品 Top30"}}]'::jsonb, '["话题","趋势","热门"]'::jsonb, 7),
(gen_random_uuid(), 'KEYWORD_INSIGHT', '关键词洞察', 'Keyword Insights', '分析商品关键词搜索量与竞争程度', '[{"blockId":"DS01","config":{"region":"US","pageSize":150}},{"blockId":"FT01","config":{"field":"search_volume","operator":">","value":1000}},{"blockId":"CM04","config":{"formula":"search_volume / (competitor_count + 1)","outputFieldName":"opportunity_index"}},{"blockId":"FT01","config":{"field":"opportunity_index","operator":">","value":50}},{"blockId":"SC02","config":{"evalPrompt":"请评估该商品在搜索引擎中的SEO潜力","maxScore":100,"outputFieldName":"seo_score","batchSize":10}},{"blockId":"SC01","config":{"field":"opportunity_index","scoreType":"linear_map","maxScore":100,"outputFieldName":"opportunity_score"}},{"blockId":"SC00","config":{"dimensions":[{"field":"seo_score","weight":0.5},{"field":"opportunity_score","weight":0.5}],"outputFieldName":"total_score"}},{"blockId":"ST01","config":{"sortBy":"total_score","order":"desc","topN":50}},{"blockId":"OUT01","config":{"summary":"关键词洞察选品 Top50"}}]'::jsonb, '["关键词","SEO","搜索"]'::jsonb, 8),
(gen_random_uuid(), 'HIGH_PROFIT', '高利润', 'High Profit Margin', '专门筛选利润率高、客单价合理的商品', '[{"blockId":"DS01","config":{"region":"US","pageSize":200}},{"blockId":"CM02","config":{"priceField":"price","costField":"cost","outputFieldName":"profit_margin"}},{"blockId":"FT01","config":{"field":"profit_margin","operator":">=","value":0.35}},{"blockId":"FT01","config":{"field":"sales_week","operator":">","value":50}},{"blockId":"SC01","config":{"field":"profit_margin","scoreType":"linear_map","maxScore":100,"outputFieldName":"profit_score"}},{"blockId":"SC01","config":{"field":"sales_week","scoreType":"linear_map","maxScore":100,"outputFieldName":"sales_score"}},{"blockId":"SC00","config":{"dimensions":[{"field":"profit_score","weight":0.7},{"field":"sales_score","weight":0.3}],"outputFieldName":"total_score"}},{"blockId":"ST01","config":{"sortBy":"total_score","order":"desc","topN":50}},{"blockId":"OUT01","config":{"summary":"高利润选品 Top50"}}]'::jsonb, '["高利润","精品","利润率"]'::jsonb, 9),
(gen_random_uuid(), 'CUSTOM_SCORE', '自定义评分', 'Custom Scoring', '提供灵活的多维度评分模板', '[{"blockId":"DS02","config":{"region":"US","rankingType":"hot_sale","rankingPeriod":"week","topN":200}},{"blockId":"CM01","config":{"fieldA":"sales_week","fieldB":"sales_last_week","outputFieldName":"growth_rate"}},{"blockId":"CM02","config":{"priceField":"price","costField":"cost","outputFieldName":"profit_margin"}},{"blockId":"SC01","config":{"field":"growth_rate","scoreType":"linear_map","maxScore":100,"outputFieldName":"growth_score"}},{"blockId":"SC01","config":{"field":"profit_margin","scoreType":"linear_map","maxScore":100,"outputFieldName":"profit_score"}},{"blockId":"SC01","config":{"field":"sales_week","scoreType":"linear_map","maxScore":100,"outputFieldName":"sales_score"}},{"blockId":"SC00","config":{"dimensions":[{"field":"growth_score","weight":0.4},{"field":"profit_score","weight":0.4},{"field":"sales_score","weight":0.2}],"outputFieldName":"total_score"}},{"blockId":"SS01","config":{"message":"评分完成，请审核"}},{"blockId":"ST01","config":{"sortBy":"total_score","order":"desc","topN":100}},{"blockId":"OUT01","config":{"summary":"自定义评分选品 Top100"}}]'::jsonb, '["评分","自定义","多维度"]'::jsonb, 10),
(gen_random_uuid(), 'LIVE_COMMERCE', '直播带货分析', 'Live Commerce Analysis', '分析适合TikTok直播带货的商品', '[{"blockId":"DS01","config":{"region":"US","pageSize":100}},{"blockId":"CM01","config":{"fieldA":"sales_week","fieldB":"sales_last_week","outputFieldName":"growth_rate"}},{"blockId":"CM02","config":{"priceField":"price","costField":"cost","outputFieldName":"profit_margin"}},{"blockId":"FT01","config":{"field":"price","operator":"between","value":10,"valueTo":100}},{"blockId":"SC02","config":{"evalPrompt":"请从直播带货视角评估该商品","maxScore":100,"outputFieldName":"live_score","batchSize":10}},{"blockId":"SC01","config":{"field":"growth_rate","scoreType":"linear_map","maxScore":100,"outputFieldName":"growth_score"}},{"blockId":"SC00","config":{"dimensions":[{"field":"live_score","weight":0.6},{"field":"growth_score","weight":0.4}],"outputFieldName":"total_score"}},{"blockId":"LA01","config":{"language":"zh","maxChars":150,"batchSize":20}},{"blockId":"ST01","config":{"sortBy":"total_score","order":"desc","topN":50}},{"blockId":"OUT01","config":{"summary":"直播带货选品 Top50"}}]'::jsonb, '["直播","带货","冲动消费"]'::jsonb, 11),
(gen_random_uuid(), 'AD_SELECTION', '广告选品', 'Ad Product Selection', '从排行榜挖掘适合付费广告投放的商品', '[{"blockId":"DS02","config":{"region":"US","rankingType":"hot_promotion","rankingPeriod":"week","topN":200}},{"blockId":"CM02","config":{"priceField":"price","costField":"cost","outputFieldName":"profit_margin"}},{"blockId":"FT01","config":{"field":"profit_margin","operator":">","value":0.25}},{"blockId":"SC02","config":{"evalPrompt":"请评估该商品投放TikTok付费广告的潜力","maxScore":100,"outputFieldName":"ad_score","batchSize":10}},{"blockId":"SC01","config":{"field":"profit_margin","scoreType":"linear_map","maxScore":100,"outputFieldName":"profit_score"}},{"blockId":"SC00","config":{"dimensions":[{"field":"ad_score","weight":0.65},{"field":"profit_score","weight":0.35}],"outputFieldName":"total_score"}},{"blockId":"LA01","config":{"language":"zh","maxChars":120,"batchSize":20}},{"blockId":"ST01","config":{"sortBy":"total_score","order":"desc","topN":50}},{"blockId":"OUT01","config":{"summary":"广告选品 Top50"}}]'::jsonb, '["广告","ROAS","付费推广"]'::jsonb, 12)
ON CONFLICT (pkg_code) DO NOTHING;

-- ============================================================
-- 9. V5 补丁：session_step.block_id 列宽度
-- ============================================================
ALTER TABLE db_session.session_step ALTER COLUMN block_id TYPE VARCHAR(64);

-- ============================================================
-- 10. db_platform — MCP工具配置 & 用户记忆文件索引
-- ============================================================

-- MCP 工具启用/禁用配置（管理员可按工具名或标签批量操作）
CREATE TABLE IF NOT EXISTS db_platform.mcp_tool_config (
    id             VARCHAR(36)  NOT NULL,
    tool_name      VARCHAR(64)  NOT NULL,
    is_enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    sensitive_flag BOOLEAN      NOT NULL DEFAULT FALSE,
    ban_reason     VARCHAR(255) DEFAULT NULL,
    updated_by     VARCHAR(64)  DEFAULT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    UNIQUE (tool_name)
);

-- 存量数据补列（멱等，多次执行安全）
ALTER TABLE db_platform.mcp_tool_config
    ADD COLUMN IF NOT EXISTS sensitive_flag BOOLEAN NOT NULL DEFAULT FALSE;

-- 用户记忆文件索引（仅存元数据，文件内容存于磁盘/OSS）
-- session_id = NULL 表示 common 跨会话永久记忆
-- agent_type 为多 Agent 并行调度预留写入来源追溯字段
CREATE TABLE IF NOT EXISTS db_platform.user_memory_files (
    id           VARCHAR(36)  NOT NULL,
    user_id      VARCHAR(64)  NOT NULL,
    session_id   VARCHAR(64)  DEFAULT NULL,
    file_path    VARCHAR(512) NOT NULL,
    memory_type  VARCHAR(20)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    description  VARCHAR(255) DEFAULT NULL,
    agent_type   VARCHAR(64)  DEFAULT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    UNIQUE (user_id, file_path)
);
CREATE INDEX IF NOT EXISTS idx_umf_user_session ON db_platform.user_memory_files (user_id, session_id);
