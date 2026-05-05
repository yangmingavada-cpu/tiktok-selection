-- ============================================================
-- 官方方案：TikTok 泰国 TOP100 选品样板（小龙虾工作流）
-- 来源：/home/openclaw/.openclaw/workspace/TikTok泰国选品自动化流程.md
-- 写入到 db_platform.preset_package，用户在 /plans「官方方案库」tab 可见
-- ============================================================

INSERT INTO db_platform.preset_package (
    id, pkg_code, name_zh, name_en, description, block_chain, tags, sort_order, is_active
) VALUES (
    'preset-tiktok-th-top100-' || substr(md5(random()::text), 1, 12),
    'TIKTOK_TH_TOP100',
    'TikTok 泰国 TOP100 选品样板',
    'TikTok Thailand TOP100 Sample',
    '复刻官方"TikTok 泰国选品自动化流程": region=TH, category=601450(三级品类), 价格 3-50 USD, 30 天销量 200-30000, 销售趋势上升; 三轮筛选(评分/销量/价格); 五维评分(销量动能 + 盈利模型 + 用户口碑 + 本地化适配 + 合规风险) + AI 选品评语; 输出 Top100。',
    '[
      {
        "blockId": "DS01",
        "config": {
          "region": "TH",
          "category_id": "601450",
          "min_spu_avg_price": 3,
          "max_spu_avg_price": 50,
          "min_total_sale_30d_cnt": 200,
          "max_total_sale_30d_cnt": 30000,
          "sales_trend_flag": 1,
          "pageSize": 10,
          "totalPages": 100
        }
      },
      {
        "blockId": "FT01",
        "config": { "field": "product_rating", "operator": ">=", "value": 4.0 }
      },
      {
        "blockId": "FT01",
        "config": { "field": "total_sale_15d_cnt", "operator": ">", "value": 120 }
      },
      {
        "blockId": "SC01",
        "config": {
          "field": "total_sale_7d_cnt",
          "scoreType": "linear_map",
          "maxScore": 100,
          "outputFieldName": "growth_score"
        }
      },
      {
        "blockId": "SC01",
        "config": {
          "field": "product_rating",
          "scoreType": "linear_map",
          "maxScore": 100,
          "outputFieldName": "reputation_score"
        }
      },
      {
        "blockId": "SC02",
        "config": {
          "evalPrompt": "从泰国本地化适配视角评估该商品：防晒、防水、轻薄、清凉等热带气候适配特征加分；带绿色、数字 4 等当地文化禁忌减分。0-100 分。",
          "maxScore": 100,
          "batchSize": 5,
          "outputFieldName": "localization_score"
        }
      },
      {
        "blockId": "SC02",
        "config": {
          "evalPrompt": "评估该商品的平台合规风险：明显品牌侵权（出现知名品牌名）、敏感品类（医药、武器、烟酒）越多分越低；安全合规为高分。0-100 分。",
          "maxScore": 100,
          "batchSize": 5,
          "outputFieldName": "compliance_score"
        }
      },
      {
        "blockId": "SC00",
        "config": {
          "dimensions": [
            { "field": "growth_score",       "weight": 0.50 },
            { "field": "reputation_score",   "weight": 0.10 },
            { "field": "localization_score", "weight": 0.20 },
            { "field": "compliance_score",   "weight": 0.20 }
          ],
          "outputFieldName": "total_score"
        }
      },
      {
        "blockId": "ST01",
        "config": { "sortBy": "total_score", "order": "desc", "topN": 100 }
      },
      {
        "blockId": "LA01",
        "config": { "language": "zh", "maxChars": 100, "batchSize": 10 }
      },
      {
        "blockId": "OUT01",
        "config": { "summary": "TikTok 泰国 TOP100 选品（小龙虾样板）" }
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
