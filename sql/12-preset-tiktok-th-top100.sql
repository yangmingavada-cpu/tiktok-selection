-- ============================================================
-- 官方方案：TikTok 泰国 TOP100 选品样板（小龙虾工作流）
-- 来源：/home/openclaw/.openclaw/workspace/TikTok泰国选品自动化流程.md
-- 写入到 db_platform.preset_package，用户在 /plans「官方方案库」tab 可见
--
-- 设计要点：
--   * 把贵的 SCORE_SEMANTIC × 2 和 ANNOTATE_LLM_COMMENT 放在 SORT_TOPN 之后，
--     只对 Top 100 候选做语义评分和 AI 评语，避免对 800+ 原始数据做 LLM 调用
--     （前一版本对 794 条做语义评分耗 380 万 tokens / 2 小时，被预算熔断）
--   * 数值评分（销量增长 + 用户口碑）零 LLM 成本，对全量做
--   * 一轮"数值评分聚合 → 取 Top 100 → 再做语义评分 → 重新聚合 4 维分"
-- ============================================================

INSERT INTO db_platform.preset_package (
    id, pkg_code, name_zh, name_en, description, block_chain, tags, sort_order, is_active
) VALUES (
    'preset-tiktok-th-top100-' || substr(md5(random()::text), 1, 12),
    'TIKTOK_TH_TOP100',
    'TikTok 泰国 TOP100 选品样板',
    'TikTok Thailand TOP100 Sample',
    '复刻"TikTok 泰国选品自动化流程": region=TH, category=601450, 价格 3-50 USD, 30 天销量 200-30000, 销售趋势上升; 两轮筛选(评分/销量); 先用销量增长 + 口碑做数值评分截 Top100, 再对 Top100 做泰国本地化 + 合规风险语义评分, 最后 AI 评语。',
    '[
      {
        "seq": 1,
        "label": "商品列表(TH·小龙虾样板)",
        "blockId": "SOURCE_PRODUCT_LIST",
        "config": {
          "region": "TH",
          "category_id": "601450",
          "min_spu_avg_price": 3,
          "max_spu_avg_price": 50,
          "min_total_sale_30d_cnt": 200,
          "max_total_sale_30d_cnt": 30000,
          "sales_trend_flag": 1,
          "page_size": 10,
          "total_pages": 100
        }
      },
      {
        "seq": 2,
        "label": "筛选: product_rating >= 4.0",
        "blockId": "FILTER_CONDITION",
        "config": { "field": "product_rating", "operator": ">=", "value": 4.0 }
      },
      {
        "seq": 3,
        "label": "筛选: total_sale_15d_cnt > 120",
        "blockId": "FILTER_CONDITION",
        "config": { "field": "total_sale_15d_cnt", "operator": ">", "value": 120 }
      },
      {
        "seq": 4,
        "label": "数值评分: 销量增长动能(权重50)",
        "blockId": "SCORE_NUMERIC",
        "config": {
          "source_field": "total_sale_7d_cnt",
          "algorithm": "linear_map",
          "max_score": 100,
          "output_field": "growth_score",
          "dimension_name": "销量增长动能",
          "weight": 50
        }
      },
      {
        "seq": 5,
        "label": "数值评分: 用户口碑(权重10)",
        "blockId": "SCORE_NUMERIC",
        "config": {
          "source_field": "product_rating",
          "algorithm": "linear_map",
          "max_score": 100,
          "output_field": "reputation_score",
          "dimension_name": "用户口碑",
          "weight": 10
        }
      },
      {
        "seq": 6,
        "label": "数值维评分汇总(pre_score)",
        "blockId": "SCORE_AGGREGATE",
        "config": {
          "dimensions": [
            { "weight": 50, "output_field": "growth_score",     "dimension_name": "销量增长动能" },
            { "weight": 10, "output_field": "reputation_score", "dimension_name": "用户口碑" }
          ],
          "output_field": "pre_score"
        }
      },
      {
        "seq": 7,
        "label": "排序取Top100(数值维): pre_score desc",
        "blockId": "SORT_TOPN",
        "config": { "sort_by": "pre_score", "order": "desc", "top_n": 100 }
      },
      {
        "seq": 8,
        "label": "语义评分: 泰国本地化(权重20，只跑Top100)",
        "blockId": "SCORE_SEMANTIC",
        "config": {
          "semantic_prompt": "从泰国本地化适配视角评估该商品：防晒、防水、轻薄、清凉等热带气候适配特征加分；带绿色、数字 4 等当地文化禁忌减分。0-100 分。",
          "max_score": 100,
          "output_field": "localization_score",
          "dimension_name": "泰国本地化适配",
          "weight": 20
        }
      },
      {
        "seq": 9,
        "label": "语义评分: 平台合规(权重20，只跑Top100)",
        "blockId": "SCORE_SEMANTIC",
        "config": {
          "semantic_prompt": "评估该商品的平台合规风险：明显品牌侵权（出现知名品牌名）、敏感品类（医药、武器、烟酒）越多分越低；安全合规为高分。0-100 分。",
          "max_score": 100,
          "output_field": "compliance_score",
          "dimension_name": "平台合规",
          "weight": 20
        }
      },
      {
        "seq": 10,
        "label": "评分汇总(全4维: total_score)",
        "blockId": "SCORE_AGGREGATE",
        "config": {
          "dimensions": [
            { "weight": 50, "output_field": "growth_score",       "dimension_name": "销量增长动能" },
            { "weight": 10, "output_field": "reputation_score",   "dimension_name": "用户口碑" },
            { "weight": 20, "output_field": "localization_score", "dimension_name": "泰国本地化适配" },
            { "weight": 20, "output_field": "compliance_score",   "dimension_name": "平台合规" }
          ],
          "output_field": "total_score"
        }
      },
      {
        "seq": 11,
        "label": "AI 选品评语(中文，仅Top100)",
        "blockId": "ANNOTATE_LLM_COMMENT",
        "config": { "language": "zh", "max_chars": 100 }
      },
      {
        "seq": 12,
        "label": "输出结果",
        "blockId": "OUTPUT_FINAL",
        "config": { "summary": "TikTok 泰国 TOP100 选品样板（小龙虾工作流）" }
      }
    ]'::jsonb,
    '["官方样板","泰国","选品","TOP100","小龙虾"]'::jsonb,
    0,
    true
)
ON CONFLICT (pkg_code) DO UPDATE SET
    name_zh = EXCLUDED.name_zh,
    name_en = EXCLUDED.name_en,
    description = EXCLUDED.description,
    block_chain = EXCLUDED.block_chain,
    tags = EXCLUDED.tags,
    sort_order = EXCLUDED.sort_order,
    is_active = EXCLUDED.is_active,
    update_time = now();
