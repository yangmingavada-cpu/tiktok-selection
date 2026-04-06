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

export const GREETING = `## 👋 你好，我是 AI 选品助手

我会帮你从 TikTok Shop 数据里找商品、看达人、做评分，并把你的自然语言需求规划成一套可执行的选品方案。

第一次使用也没关系：
- 先试试下面的 **预设方案演示**
- 熟悉之后，再直接自由描述你的需求

当前开放市场：**泰国（TH）**。`

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
]

export const FREEFORM_EXAMPLES = [
  '帮我在泰国选一批美妆护肤商品，价格5-20美元，推荐Top30，要AI评语',
  '泰国家居品类，近期销量增长快、评分高的商品，推荐Top20',
  '帮我看看泰国市场哪些达人带货效果好，找到他们卖的商品',
]
