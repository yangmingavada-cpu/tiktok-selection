import type { Block, PreviewResponse } from '@/types'

interface ParsedSource {
  entity: string
  mode: string
  market?: string
  category?: string
  estimatedVolume?: string
}

interface ParsedScore {
  name: string
  mode: 'numeric' | 'semantic'
  weight: number
  sourceField?: string
  algorithm?: string
  prompt?: string
  outputField?: string
}

interface PlanDigest {
  source?: ParsedSource
  filters: string[]
  computations: string[]
  enrichments: string[]
  traverses: string[]
  scoring: ParsedScore[]
  scoringSummary?: string
  sortSummary?: string
  outputSummary?: string
  hasComment: boolean
  adjustableItems: string[]
}

const FIELD_LABELS: Record<string, string> = {
  region: '目标市场',
  category_id: '一级品类',
  category_l2_id: '二级品类',
  category_l3_id: '三级品类',
  min_spu_avg_price: '最低客单价',
  max_spu_avg_price: '最高客单价',
  min_product_rating: '最低商品评分',
  max_product_rating: '最高商品评分',
  min_total_sale_cnt: '最低累计销量',
  max_total_sale_cnt: '最高累计销量',
  min_total_sale_30_d_cnt: '最近30天最低销量',
  max_total_sale_30_d_cnt: '最近30天最高销量',
  min_total_sale_gmv_amt: '最低累计 GMV',
  max_total_sale_gmv_amt: '最高累计 GMV',
  min_total_sale_gmv_30_d_amt: '最近30天最低 GMV',
  max_total_sale_gmv_30_d_amt: '最近30天最高 GMV',
  total_sale_cnt: '累计销量',
  total_sale_7d_cnt: '近7天销量',
  total_sale_30d_cnt: '近30天销量',
  total_sale_30_d_cnt: '近30天销量',
  total_sale_gmv_amt: '累计 GMV',
  total_sale_gmv_30d_amt: '近30天 GMV',
  total_sale_gmv_30_d_amt: '近30天 GMV',
  product_rating: '商品评分',
  review_count: '评论数',
  product_commission_rate: '佣金比例',
  total_ifl_cnt: '关联达人数',
  total_video_cnt: '关联视频数',
  total_views_cnt: '总播放量',
  sales_trend_flag: '销量趋势',
  total_score: '综合总分',
  growth_rate: '增长率',
}

const REGION_LABELS: Record<string, string> = {
  TH: '泰国',
  US: '美国',
  UK: '英国',
  ID: '印尼',
  MY: '马来西亚',
  PH: '菲律宾',
  VN: '越南',
  SG: '新加坡',
  SA: '沙特',
  AE: '阿联酋',
}

const SOURCE_LABELS: Record<string, { entity: string; mode: string }> = {
  SOURCE_PRODUCT_LIST: { entity: '商品', mode: '列表筛选' },
  SOURCE_PRODUCT_RANKLIST: { entity: '商品', mode: '榜单起步' },
  SOURCE_INFLUENCER_LIST: { entity: '达人', mode: '列表筛选' },
  SOURCE_INFLUENCER_RANKLIST: { entity: '达人', mode: '榜单起步' },
  SOURCE_SELLER_LIST: { entity: '店铺', mode: '列表筛选' },
  SOURCE_SELLER_RANKLIST: { entity: '店铺', mode: '榜单起步' },
  SOURCE_VIDEO_LIST: { entity: '视频', mode: '列表筛选' },
  SOURCE_VIDEO_RANKLIST: { entity: '视频', mode: '榜单起步' },
  SOURCE_KEYWORD_INSIGHT: { entity: '关键词', mode: '趋势洞察' },
  SOURCE_HASHTAG_TRENDING: { entity: '话题', mode: '趋势洞察' },
}

function labelField(field?: string): string {
  if (!field) return '该指标'
  return FIELD_LABELS[field] || field
}

function labelRegion(region?: string): string {
  if (!region) return ''
  return REGION_LABELS[region] || region
}

function formatValue(value: unknown): string {
  if (Array.isArray(value)) {
    return value.map(item => formatValue(item)).join(' ~ ')
  }
  if (typeof value === 'boolean') {
    return value ? '是' : '否'
  }
  if (typeof value === 'number') {
    return Number.isInteger(value) ? `${value}` : value.toFixed(2)
  }
  if (value == null || value === '') {
    return '未设置'
  }
  if (typeof value === 'string' && REGION_LABELS[value]) {
    return labelRegion(value)
  }
  return String(value)
}

function normalizeWeight(weight: number, totalWeight: number): string {
  if (!totalWeight) return `${weight}`
  return `${Math.round((weight / totalWeight) * 100)}%`
}

function describeFilter(field?: string, operator?: string, value?: unknown): string {
  const left = labelField(field)
  if (operator === 'between' && Array.isArray(value) && value.length >= 2) {
    return `${left}介于 ${formatValue(value[0])} 到 ${formatValue(value[1])}`
  }
  if (operator === 'in' && Array.isArray(value)) {
    return `${left}限定在 ${value.map(item => formatValue(item)).join('、')}`
  }
  if (field === 'sales_trend_flag') {
    const trendMap: Record<string, string> = {
      '1': '上升',
      '2': '下降',
      '3': '平稳',
    }
    return `销量趋势为${trendMap[String(value)] || formatValue(value)}`
  }
  return `${left} ${operator || ''} ${formatValue(value)}`.trim()
}

function describeSource(block: Block): ParsedSource | undefined {
  const base = SOURCE_LABELS[block.blockId]
  if (!base) return undefined

  const config = block.config || {}
  const totalPages = Number(config.total_pages || 1)
  const pageSize = Number(config.page_size || 10)
  const estimatedRows = totalPages > 0 && pageSize > 0 ? `约 ${totalPages * pageSize} 条` : undefined

  return {
    entity: base.entity,
    mode: base.mode,
    market: typeof config.region === 'string' ? labelRegion(config.region) : undefined,
    category:
      typeof config.category === 'string'
        ? config.category
        : typeof config.category_id === 'string'
          ? `类目 ${config.category_id}`
          : undefined,
    estimatedVolume: estimatedRows,
  }
}

function analyzePlan(blocks: readonly Block[]): PlanDigest {
  const digest: PlanDigest = {
    filters: [],
    computations: [],
    enrichments: [],
    traverses: [],
    scoring: [],
    hasComment: false,
    adjustableItems: [],
  }

  for (const block of blocks) {
    const config = block.config || {}

    if (block.blockId.startsWith('SOURCE_')) {
      digest.source = describeSource(block)

      if ('min_spu_avg_price' in config || 'max_spu_avg_price' in config) {
        const parts = []
        if (config.min_spu_avg_price != null) parts.push(`最低价格 ${formatValue(config.min_spu_avg_price)} USD`)
        if (config.max_spu_avg_price != null) parts.push(`最高价格 ${formatValue(config.max_spu_avg_price)} USD`)
        if (parts.length) digest.filters.push(parts.join('，'))
      }

      if ('sales_trend_flag' in config) {
        digest.filters.push(describeFilter('sales_trend_flag', '==', config.sales_trend_flag))
      }
    }

    if (block.blockId === 'FILTER_CONDITION') {
      digest.filters.push(describeFilter(String(config.field || ''), String(config.operator || ''), config.value))
    }

    if (block.blockId === 'COMPUTE_GROWTH_RATE') {
      digest.computations.push(
        `先用 ${labelField(String(config.field_a || '指标 A'))} 对比 ${labelField(String(config.field_b || '指标 B'))}，计算增长率字段 ${labelField(String(config.output_field_name || 'growth_rate'))}`,
      )
    }

    if (block.blockId === 'COMPUTE_PROFIT_MARGIN') {
      digest.computations.push(
        `计算利润率：用 ${labelField(String(config.price_field || '售价'))} 与 ${labelField(String(config.cost_field || '成本'))} 推导出 ${labelField(String(config.output_field_name || 'profit_margin'))}`,
      )
    }

    if (block.blockId === 'COMPUTE_FORMULA') {
      digest.computations.push(
        `新增一个自定义公式字段 ${labelField(String(config.outputFieldName || '公式结果'))}，用于补充排序或评分依据`,
      )
    }

    if (block.blockId.startsWith('ENRICH_')) {
      const enrichMap: Record<string, string> = {
        ENRICH_PRODUCT_TREND: '补充商品历史趋势数据',
        ENRICH_PRODUCT_DETAIL: '补充商品详情',
        ENRICH_PRODUCT_COMMENT: '补充商品评论',
        ENRICH_INFLUENCER_TREND: '补充达人历史趋势数据',
        ENRICH_INFLUENCER_DETAIL: '补充达人详情',
      }
      const label = (block as Block & { label?: string }).label
      digest.enrichments.push(enrichMap[block.blockId] || `补充 ${label || block.blockId}`)
    }

    if (block.blockId.startsWith('TRAVERSE_')) {
      const traverseMap: Record<string, string> = {
        TRAVERSE_INFLUENCER_TO_PRODUCT: '先看达人，再追到达人卖的商品',
        TRAVERSE_SELLER_TO_PRODUCT: '先看店铺，再追到店铺在卖的商品',
        TRAVERSE_VIDEO_TO_PRODUCT: '先看视频，再追到视频挂载的商品',
        TRAVERSE_HASHTAG_TO_VIDEO: '先看话题，再追到相关视频',
      }
      digest.traverses.push(traverseMap[block.blockId] || `执行实体跳转 ${block.blockId}`)
    }

    if (block.blockId === 'SCORE_NUMERIC' || block.blockId === 'SCORE_SEMANTIC') {
      digest.scoring.push({
        name: String(config.dimension_name || '评分维度'),
        mode: block.blockId === 'SCORE_NUMERIC' ? 'numeric' : 'semantic',
        weight: Number(config.weight || 1),
        sourceField: typeof config.source_field === 'string' ? config.source_field : undefined,
        algorithm: typeof config.algorithm === 'string' ? config.algorithm : undefined,
        prompt: typeof config.semantic_prompt === 'string' ? config.semantic_prompt : undefined,
        outputField: typeof config.output_field === 'string' ? config.output_field : undefined,
      })
    }

    if (block.blockId === 'SCORE_AGGREGATE' && Array.isArray(config.dimensions)) {
      const dims = config.dimensions as Array<Record<string, unknown>>
      const totalWeight = dims.reduce((sum, item) => sum + Number(item.weight || 0), 0)
      digest.scoringSummary = dims
        .map(item => `${String(item.dimension_name || item.output_field || '维度')} ${normalizeWeight(Number(item.weight || 0), totalWeight)}`)
        .join(' + ')
    }

    if (block.blockId === 'SORT_TOPN') {
      digest.sortSummary = `按 ${labelField(String(config.sort_by || '目标字段'))} ${config.order === 'asc' ? '升序' : '降序'} 排序，截取 Top ${formatValue(config.top_n || 100)}`
      if (config.category_disperse === true) {
        digest.sortSummary += '，并尽量让品类分布更均衡'
      }
      if (config.deduplicate !== false) {
        digest.sortSummary += '，同时自动去重'
      }
    }

    if (block.blockId === 'ANNOTATE_LLM_COMMENT') {
      digest.hasComment = true
    }

    if (block.blockId === 'OUTPUT_FINAL') {
      digest.outputSummary = typeof config.summary === 'string' ? config.summary : undefined
    }
  }

  const adjustable = new Set<string>()
  if (digest.source?.market) adjustable.add(`改目标市场（当前：${digest.source.market}）`)
  if (digest.source?.category) adjustable.add(`改品类范围（当前：${digest.source.category}）`)
  if (digest.filters.length) adjustable.add('改筛选条件')
  if (digest.scoring.length) adjustable.add('改评分维度或权重')
  if (digest.sortSummary) adjustable.add('改排序规则或推荐数量')
  if (digest.hasComment) adjustable.add('改是否生成 AI 评语')
  digest.adjustableItems = [...adjustable]

  return digest
}

export function buildPlanInterpretation(blocks: readonly Block[], preview?: PreviewResponse | null): string {
  const digest = analyzePlan(blocks)
  const lines: string[] = []

  lines.push('## 这套方案实际会怎么跑')
  lines.push('')

  if (digest.source) {
    const sourceLine = [
      `先从**${digest.source.market || '目标市场'}**的**${digest.source.entity} ${digest.source.mode}**开始取数`,
      digest.source.category ? `聚焦在**${digest.source.category}**相关范围` : '',
      digest.source.estimatedVolume ? `初始抓取规模约为**${digest.source.estimatedVolume}**` : '',
    ].filter(Boolean).join('，')
    lines.push(`1. **数据起点**：${sourceLine}。`)
  }

  if (digest.traverses.length) {
    lines.push(`2. **实体跳转**：${digest.traverses.join('；')}。`)
  }

  const filterSection = [...digest.filters, ...digest.enrichments]
  if (filterSection.length) {
    lines.push(`${lines.length ? lines.filter(line => /^\d+\./.test(line)).length + 1 : 2}. **筛选与补充**：${filterSection.join('；')}。`)
  }

  if (digest.computations.length) {
    lines.push(`${lines.filter(line => /^\d+\./.test(line)).length + 1}. **新增计算字段**：${digest.computations.join('；')}。`)
  }

  if (digest.scoring.length) {
    const totalWeight = digest.scoring.reduce((sum, item) => sum + item.weight, 0)
    const scoringText = digest.scoring.map(item => {
      if (item.mode === 'numeric') {
        const algorithmMap: Record<string, string> = {
          linear_map: '值越大分越高',
          inverse_map: '值越小分越高',
          tier_map: '按分段给分',
        }
        return `**${item.name}**：根据 ${labelField(item.sourceField)} 做数值评分，规则是${algorithmMap[item.algorithm || 'linear_map'] || '按数值映射给分'}，在总分中占 **${normalizeWeight(item.weight, totalWeight)}**`
      }
      return `**${item.name}**：用 AI 做语义判断，在总分中占 **${normalizeWeight(item.weight, totalWeight)}**`
    }).join('；')
    lines.push(`${lines.filter(line => /^\d+\./.test(line)).length + 1}. **评分逻辑**：${scoringText}。`)
  }

  if (digest.scoringSummary) {
    lines.push(`- **总分合成方式**：${digest.scoringSummary}，最后汇总成 **综合总分**。`)
  }

  if (digest.sortSummary) {
    lines.push(`${lines.filter(line => /^\d+\./.test(line)).length + 1}. **最终输出**：${digest.sortSummary}。`)
  }

  if (digest.hasComment) {
    lines.push(`- **附加输出**：结果里还会给每个对象生成 AI 选品评语，方便你快速判断。`)
  }

  if (preview) {
    const previewLabel = preview.status === 'ok'
      ? `当前预览验证通过，已经能拿到 **${preview.sampleCount ?? 0} 条**样例数据。`
      : preview.status === 'empty'
        ? '当前预览结果偏少，说明筛选条件可能偏严，建议先放宽价格、销量门槛或评分要求。'
        : preview.status === 'error'
          ? '当前预览验证失败，建议先检查数据源和筛选项是否冲突。'
          : ''
    if (previewLabel) {
      lines.push('')
      lines.push(`> ${previewLabel}`)
    }
  }

  if (digest.adjustableItems.length) {
    lines.push('')
    lines.push('## 你现在可以直接调整这些项')
    for (const item of digest.adjustableItems) {
      lines.push(`- ${item}`)
    }
  }

  return lines.join('\n')
}

export function buildPlanAdjustmentGuide(blocks: readonly Block[], preview?: PreviewResponse | null) {
  const digest = analyzePlan(blocks)
  const suggestions = digest.adjustableItems.length
    ? digest.adjustableItems.slice(0, 4)
    : ['改目标市场', '改筛选条件', '改评分权重', '改推荐数量']

  let question = '我已经把当前方案拆成了数据源、筛选、评分和输出四部分。你想先调整哪一项？'
  if (preview?.status === 'empty') {
    question = '这版方案结果偏少，通常是筛选过严或 TopN 太紧。你想先放宽哪一项？'
  }

  return { question, suggestions }
}

export function answerPlanQuestion(question: string, blocks: readonly Block[]): string | null {
  const normalized = question.trim()
  if (!normalized) return null

  const digest = analyzePlan(blocks)
  const lower = normalized.toLowerCase()

  if (/(增长|增速|趋势分|销量增长)/.test(normalized)) {
    const growthScore = digest.scoring.find(item =>
      /增长|增速|趋势/.test(item.name) || /growth/.test(item.sourceField || '') || /growth/.test(item.outputField || ''),
    )
    const growthComputation = digest.computations.find(item => /增长率/.test(item))
    if (!growthScore && !growthComputation) return null

    const parts = ['这部分不是凭感觉打分，而是先算指标，再映射成分数。']
    if (growthComputation) parts.push(growthComputation + '。')
    if (growthScore) {
      parts.push(
        growthScore.mode === 'numeric'
          ? `然后把这个增长指标按“${growthScore.algorithm === 'inverse_map' ? '越小越高分' : growthScore.algorithm === 'tier_map' ? '分段给分' : '越大越高分'}”映射到分数，并作为“${growthScore.name}”参与总分。`
          : `然后再用 AI 判断增长表现，形成“${growthScore.name}”这个维度分。`,
      )
      const totalWeight = digest.scoring.reduce((sum, item) => sum + item.weight, 0)
      parts.push(`它在总分里的占比约为 **${normalizeWeight(growthScore.weight, totalWeight)}**。`)
    }
    return parts.join('')
  }

  if (/(评分|权重|总分|综合分|打分)/.test(normalized)) {
    if (!digest.scoring.length) return '这套方案当前没有额外评分环节，主要是先筛选，再按指定字段直接排序输出。'
    const totalWeight = digest.scoring.reduce((sum, item) => sum + item.weight, 0)
    const details = digest.scoring.map(item => `- ${item.name}：${item.mode === 'numeric' ? `基于 ${labelField(item.sourceField)} 做数值评分` : '由 AI 做语义评分'}，占比约 ${normalizeWeight(item.weight, totalWeight)}`).join('\n')
    return `当前总分是按多个维度加权汇总出来的：\n${details}\n\n${digest.scoringSummary ? `最后会把这些分数汇总成综合总分：${digest.scoringSummary}。` : '最后会把这些维度分汇总成综合总分，再用于排序。'}`
  }

  if (/(筛选|条件|为什么少|结果少|太严|放宽)/.test(normalized)) {
    if (!digest.filters.length) {
      return '这套方案当前没有很多硬筛选，更多是通过评分和排序来挑结果。如果你觉得结果偏少，更可能需要调整 TopN、评分权重，或者放宽数据源本身的价格和销量范围。'
    }
    return `当前明确生效的筛选条件主要有：\n- ${digest.filters.join('\n- ')}\n\n如果你要放宽结果，优先建议先改价格区间、销量门槛，或者把“趋势必须上升”改成更宽松。`
  }

  if (/(排序|top|数量|推荐多少|输出)/.test(lower + normalized)) {
    if (!digest.sortSummary) return '这套方案还没有走到最终排序截取这一步，所以当前更多是在构建筛选和评分逻辑。'
    return `${digest.sortSummary}。如果你想让结果更宽，可以直接把 TopN 调大，或者取消去重/品类分散。`
  }

  if (/(市场|国家|地区|品类|价格)/.test(normalized) && digest.source) {
    const parts = [
      digest.source.market ? `目标市场是 **${digest.source.market}**` : '',
      digest.source.category ? `当前聚焦品类是 **${digest.source.category}**` : '当前还没有把品类限制得很死',
      digest.filters.find(item => /价格/.test(item)) ? `价格条件是：${digest.filters.find(item => /价格/.test(item))}` : '价格目前没有加很强的硬门槛',
    ].filter(Boolean)
    return `${parts.join('，')}。如果你要改，我可以直接围绕市场、品类或价格区间继续调整。`
  }

  return null
}
