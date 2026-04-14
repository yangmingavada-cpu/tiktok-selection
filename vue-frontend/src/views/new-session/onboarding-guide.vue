<script setup lang="ts">
import {
  FREEFORM_EXAMPLES,
  STARTER_CAPABILITIES,
  STARTER_PRESETS,
} from './constants'

const props = defineProps<{
  disabled: boolean
}>()

const emit = defineEmits<{
  fillPrompt: [prompt: string]
  runPrompt: [prompt: string]
}>()
</script>

<template>
  <section class="onboarding-card">
    <div class="hero">
      <div class="hero-copy">
        <span class="hero-kicker">第一次使用，建议先看这里</span>
        <h3>不知道该怎么提需求，也能很快跑出第一版方案</h3>
        <p>
          你可以先点一个预设方案体验完整流程。熟悉以后，再直接用自然语言自由发挥，
          AI 会继续帮你补条件、调权重、生成待确认方案。
        </p>
      </div>
      <div class="hero-note">
        <span class="hero-note-label">当前支持</span>
        <strong>全球 10 大市场（TH / ID / MY / VN / PH / SG / US / UK / SA / AE）</strong>
        <span>商品 / 达人 / 店铺 / 视频</span>
      </div>
    </div>

    <div class="section">
      <div class="section-header">
        <h4>这个 AI 能帮你做什么</h4>
        <span>从“我想看看什么”到“给我一套方案”</span>
      </div>
      <div class="capability-grid">
        <article
          v-for="capability in STARTER_CAPABILITIES"
          :key="capability.title"
          class="capability-card"
        >
          <div class="capability-icon">{{ capability.icon }}</div>
          <div class="capability-title">{{ capability.title }}</div>
          <p>{{ capability.description }}</p>
        </article>
      </div>
    </div>

    <div class="section">
      <div class="section-header">
        <h4>新手先试试这些预设方案</h4>
        <span>一键演示，感受完整流程；也可以先填到输入框再改</span>
      </div>
      <div class="preset-grid">
        <article
          v-for="preset in STARTER_PRESETS"
          :key="preset.id"
          class="preset-card"
        >
          <div class="preset-head">
            <span class="preset-badge">{{ preset.badge }}</span>
            <h5>{{ preset.title }}</h5>
          </div>
          <p class="preset-description">{{ preset.description }}</p>
          <div class="preset-prompt">{{ preset.prompt }}</div>
          <div class="preset-actions">
            <el-button
              size="small"
              :disabled="props.disabled"
              @click="emit('fillPrompt', preset.prompt)"
            >
              填入输入框
            </el-button>
            <el-button
              size="small"
              type="primary"
              :disabled="props.disabled"
              @click="emit('runPrompt', preset.prompt)"
            >
              直接演示
            </el-button>
          </div>
        </article>
      </div>
    </div>

    <div class="section">
      <div class="section-header compact">
        <h4>已经知道要什么？可以直接这样问</h4>
      </div>
      <div class="example-list">
        <button
          v-for="example in FREEFORM_EXAMPLES"
          :key="example"
          type="button"
          class="example-chip"
          :disabled="props.disabled"
          @click="emit('fillPrompt', example)"
        >
          {{ example }}
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.onboarding-card {
  display: flex;
  flex-direction: column;
  gap: 22px;
  width: 100%;
  padding: 20px;
  border: 1px solid rgba(99, 102, 241, 0.12);
  border-radius: 20px;
  background:
    radial-gradient(circle at top right, rgba(99, 102, 241, 0.12), transparent 28%),
    linear-gradient(135deg, #fcfdff 0%, #f4f7ff 52%, #f7fffb 100%);
  box-shadow: 0 16px 36px rgba(79, 70, 229, 0.08);
}

.hero {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: stretch;
}

.hero-copy {
  flex: 1;
}

.hero-kicker {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(99, 102, 241, 0.1);
  color: #4f46e5;
  font-size: 12px;
  font-weight: 600;
}

.hero-copy h3 {
  margin: 12px 0 8px;
  font-size: 24px;
  line-height: 1.3;
  color: #111827;
}

.hero-copy p {
  margin: 0;
  max-width: 680px;
  color: #4b5563;
  font-size: 14px;
  line-height: 1.8;
}

.hero-note {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  min-width: 220px;
  padding: 16px 18px;
  border: 1px solid rgba(56, 189, 248, 0.22);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.78);
  color: #374151;
}

.hero-note-label {
  font-size: 12px;
  color: #6b7280;
}

.hero-note strong {
  font-size: 18px;
  color: #0f172a;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-end;
}

.section-header.compact {
  align-items: center;
}

.section-header h4 {
  margin: 0;
  font-size: 17px;
  color: #111827;
}

.section-header span {
  font-size: 12px;
  color: #6b7280;
}

.capability-grid,
.preset-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.capability-card,
.preset-card {
  min-width: 0;
  padding: 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.capability-card {
  box-shadow: 0 10px 24px rgba(148, 163, 184, 0.12);
}

.capability-icon {
  font-size: 22px;
}

.capability-title {
  margin: 12px 0 8px;
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.capability-card p,
.preset-description {
  margin: 0;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.7;
}

.preset-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-shadow: 0 12px 28px rgba(99, 102, 241, 0.08);
}

.preset-head {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preset-badge {
  width: fit-content;
  padding: 4px 10px;
  border-radius: 999px;
  background: linear-gradient(90deg, #eef2ff 0%, #ecfeff 100%);
  color: #4338ca;
  font-size: 12px;
  font-weight: 600;
}

.preset-head h5 {
  margin: 0;
  font-size: 16px;
  color: #111827;
}

.preset-prompt {
  padding: 12px 14px;
  border-radius: 14px;
  background: #f8fafc;
  color: #1f2937;
  font-size: 13px;
  line-height: 1.7;
}

.preset-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.example-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.example-chip {
  padding: 10px 14px;
  border: 1px solid rgba(99, 102, 241, 0.18);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  color: #374151;
  font-size: 13px;
  line-height: 1.5;
  cursor: pointer;
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}

.example-chip:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: rgba(99, 102, 241, 0.35);
  box-shadow: 0 10px 22px rgba(99, 102, 241, 0.1);
}

.example-chip:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

@media (max-width: 1200px) {
  .capability-grid,
  .preset-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .onboarding-card {
    padding: 16px;
  }

  .hero {
    flex-direction: column;
  }

  .hero-copy h3 {
    font-size: 20px;
  }

  .hero-note {
    min-width: 0;
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .capability-grid,
  .preset-grid {
    grid-template-columns: 1fr;
  }

  .preset-actions {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}
</style>
