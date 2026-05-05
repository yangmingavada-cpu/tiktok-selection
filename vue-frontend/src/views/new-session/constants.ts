export interface StarterCapability {
  icon: string
  title: string
  description: string
}

export interface StarterPreset {
  id: string
  badge: string
  title: string
  description: string
  prompt: string
}


export const STARTER_CAPABILITIES: StarterCapability[] = [
  {
    icon: '📦',
    title: '找值得做的商品',
    description: '按品类、价格、销量增长、买家评分等条件筛选商品，并自动排出 Top N。',
  },
  {
    icon: '🎥',
    title: '从达人反查商品',
    description: '先找到带货效果好的达人，再下钻到他们卖的商品和关联线索。',
  },
  {
    icon: '📊',
    title: '自动评分和解释',
    description: '支持增长、评分、利润和 AI 评语，给出更适合落地的推荐结果。',
  },
]

export const STARTER_PRESETS: StarterPreset[] = [
  {
    id: 'growth-products',
    badge: '新手推荐',
    title: '热销增长商品',
    description: '先快速体验一轮完整选品流程，适合第一次上手。',
    prompt: '泰国家居品类，近期销量增长快、评分高的商品，推荐Top20',
  },
  {
    id: 'creator-products',
    badge: '达人反查',
    title: '先找达人，再找商品',
    description: '适合从带货效果好的达人，反推目前卖得动的商品。',
    prompt: '帮我看看泰国市场哪些达人带货效果好，找到他们卖的商品',
  },
  {
    id: 'ranking-template',
    badge: '预设模板',
    title: '榜单打分模板',
    description: '用一套比较稳的榜单打分方法，快速跑出第一版结果。',
    prompt: '泰国热销榜，不限品类，不限价格，按销量增长70%+买家评分30%打分，Top50',
  },
  {
    id: 'tiktok-th-top100-template',
    badge: '官方样板',
    title: 'TikTok 泰国 TOP100 选品样板',
    description: '复刻"TikTok 泰国选品自动化流程"：5 维度评分 + AI 标注，输出 Top100。',
    prompt: `严格按以下规格构建积木链（TikTok 泰国 TOP100 选品样板）：

【数据源】select_product_source
- source_type: product_listing
- region: TH
- category: 三级品类 ID 601450（直接传 category_id="601450"）
- price_min: 3, price_max: 50（USD）
- min_total_sale_30d_cnt: 200, max_total_sale_30d_cnt: 30000
- sales_trend_flag: 1（上升）
- data_volume: large

【计算字段 add_computation × 8（custom_formula，USD→THB 汇率固定 35.5）】
1. min_price_thb = min_price * 35.5
2. max_price_thb = max_price * 35.5
3. spu_avg_price_thb = spu_avg_price * 35.5
4. logistics_cost_thb = spu_avg_price_thb * 0.06 + 15
5. supply_cost_thb = spu_avg_price_thb * 0.20
6. tiktok_commission_thb = spu_avg_price_thb * 0.18
7. net_profit_thb = spu_avg_price_thb - logistics_cost_thb - supply_cost_thb - tiktok_commission_thb
8. net_margin_pct = net_profit_thb / spu_avg_price_thb * 100

【基础筛选 add_filter × 3】
- net_margin_pct >= 25
- total_sale_15d_cnt > 120
- product_rating >= 4.0

【五维度评分（总分 100）】
- dim1 销量增长动能（权重 50）：数值评分，输入 total_sale_7d_cnt 和 total_sale_15d_cnt 计算近 7 天日均 vs 近 14 天日均的增长率，得分 = min(50, 增长率 × 0.2 × 100)
- dim2 盈利模型健康度（权重 20）：数值评分，输入 net_margin_pct，得分 = min(20, net_margin_pct / 40 × 20)
- dim3 用户口碑与真实性（权重 10）：数值评分，输入 product_rating，得分 = (product_rating / 4.7) × 10
- dim4 泰国本地化适配性（权重 10）：语义评分（score_semantic），prompt 检测防晒/防水/轻薄等正向关键词，绿色/数字 4 等文化禁忌负向
- dim5 平台合规风险（权重 10）：语义评分（score_semantic），prompt 检测品牌侵权/敏感品类负向

【汇总】score_aggregate 输出 total_score
【排序】add_sort: sort_by=total_score, order=desc, top_n=100

【AI 标注 annotate_llm_comment × 4】
- ai_recommendation: < 100 字中文选品建议
- advantage_tags: 中文数组（高增长/高利润/高口碑/本地化强/物流成本低）
- risk_warning: 中文风险提示（评论数不足/价格过低/增长放缓/物流成本过高/毛利率偏低）
- suggested_action: 优先投放/测试观察/谨慎跟进/暂不推荐

【完成】finalize_chain，summary 用大白话说明流程。`,
  },
]

export const FREEFORM_EXAMPLES = [
  '帮我在泰国选一批美妆护肤商品，价格5-20美元，推荐Top30，要AI评语',
  '泰国家居品类，近期销量增长快、评分高的商品，推荐Top20',
  '帮我看看泰国市场哪些达人带货效果好，找到他们卖的商品',
]
