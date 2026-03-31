# 整体营销方案评估技术方案

## 1. 目标

AdSim 的目标不是只评估某一条广告素材，也不是只生成一份“洞察报告”。

目标是评估一整份营销方案，并给出可验证的优化建议：

- 这份方案在不同平台 / placement / 人群上的预期反应是什么
- 最大阻塞因素是什么
- 预算和素材应该往哪里调整
- 调整后是否比原方案更优

核心结论：

**实现上应保留多 Agent，但不能走“纯多 Agent 世界模拟”路线。**

推荐方向是：

**Plan Compiler + Cohort Engine + Placement Simulator + Campaign Orchestrator + Calibration + Counterfactual Optimizer**

其中：

- 多 Agent 负责消费者反应仿真
- 系统层负责整案编排、校准和优化建议

---

## 2. 为什么不是纯多 Agent 路线

### 2.1 纯多 Agent 的优点

- 能产出结构化推理和行为原因
- 能表达不同人群的差异反应
- 能支持“为什么没转化”这类可解释问题

### 2.2 纯多 Agent 的问题

- 成本高，方案越大越不可控
- 结果方差大，A/B 对比不稳定
- 很难直接得到整案预算优化建议
- 很难把 reach / CPA / 增量效果这类业务指标做得可信
- 容易把“单 placement 行为模拟”误包装成“整案评估系统”

### 2.3 为什么也不能退回纯 benchmark / 公式路线

- 只能给行业平均值，无法解释具体方案为什么好或差
- 无法回答“哪个人群会买、为什么”
- 无法基于素材和表达方式生成可操作建议

因此最终选择不是二选一，而是混合架构：

- **Agent 模拟行为**
- **规则与统计做聚合**
- **Benchmark 与真实回填做校准**
- **反事实实验做优化建议**

---

## 3. 总体架构

## 3.1 架构原则

1. **整案是顶层对象，不是 placement 列表的附属品**
2. **placement 是最小可模拟单元**
3. **cohort 必须可复用，A/B 必须共享同一批人**
4. **factors 是主数据，reasoning 是证据**
5. **模拟信号和业务估算指标必须分层**
6. **优化建议必须来自反事实验证，而不是 LLM 直接写建议**

## 3.2 模块分层

### A. Plan Compiler

职责：

- 将自然语言方案编译为结构化 `CampaignPlan`
- 支持增量编辑，不覆盖未修改字段
- 产出标准化 placement、预算、人群、素材、竞品、目标

输出不是临时 DTO，而是系统主数据模型。

### B. Cohort Engine

职责：

- 基于平台和目标受众生成可复用 cohort
- 对 cohort 进行分层和加权
- 固化 `cohortVersion`、`seed`、`samplingPolicy`

这层的目的不是“生成一批看起来像人的 persona”，而是生成可对比、可复现的模拟样本。

### C. Placement Simulator

职责：

- 对单个 `placement × cohort` 做行为仿真
- 输出 attention / click / conversion / factors / reasoning
- 记录 per-stage 完整度和失败情况

多 Agent 主要在这一层发挥作用。

### D. Campaign Orchestrator

职责：

- 按整案顺序编排多个 placement 的执行
- 维护 agent 在整场 campaign 中的状态
- 处理疲劳、品牌熟悉度变化、跨 placement 影响

这层是真正的“整案评估”核心，不能再让引擎只吃第一条 placement。

### E. Aggregation & Insight Engine

职责：

- 按 factor 做聚合
- 做 stage blockers / drivers 统计
- 做 segment × factor 差异分析
- 输出结构化洞察和证据引用

这层不以自由文本聚合为主。

### F. Calibration Layer

职责：

- 将模拟信号映射到业务指标
- 使用 benchmark、平台常数、后续真实回填数据做校准
- 输出 reach / CTR / CVR / CPA / uplift 的估算值和置信说明

这层明确承认业务指标不是纯涌现结果。

### G. Recommendation Engine

职责：

- 从 blockers / segment gaps 中抽取候选优化动作
- 生成 candidate plans
- 在同一 cohort 上做反事实重跑
- 输出最优建议、适用人群、收益和风险

这层解决“应该改什么”。

---

## 4. 核心数据模型

以下是建议的数据主模型。

```kotlin
data class CampaignPlan(
    val id: String,
    val product: ProductContext,
    val audience: AudienceDefinition,
    val placements: List<PlacementPlan>,
    val creatives: List<CreativeVariant>,
    val competitors: List<CompetitorInfo>,
    val budget: BudgetPlan,
    val goal: CampaignGoal,
    val constraints: PlanConstraints,
    val metadata: PlanMetadata
)

data class PlacementPlan(
    val placementId: String,
    val platform: Platform,
    val placementType: PlacementType,
    val objective: List<CampaignObjective>,
    val creativeRef: String,
    val allocatedBudget: Long,
    val targeting: PlacementTargeting,
    val sequenceOrder: Int
)

data class CohortSnapshot(
    val cohortId: String,
    val platform: Platform,
    val version: String,
    val seed: Long,
    val segments: List<CohortSegment>,
    val agents: List<SimAgent>
)

data class SimAgent(
    val agentId: String,
    val persona: Persona,
    val weight: Double,
    val campaignState: AgentCampaignState
)

data class AgentCampaignState(
    val seenPlacements: List<String> = emptyList(),
    val exposureCountByBrand: Map<String, Int> = emptyMap(),
    val fatigueLevel: Double = 0.0,
    val brandFamiliarity: Map<String, Double> = emptyMap(),
    val convertedProducts: List<String> = emptyList()
)

data class PlacementResult(
    val placementId: String,
    val sampleQuality: SimulationQuality,
    val funnel: PlacementFunnel,
    val factorInsights: List<FactorInsight>,
    val segmentInsights: List<SegmentFactorInsight>,
    val rawMetrics: RawBehaviorMetrics,
    val calibratedMetrics: CalibratedBusinessMetrics?
)

data class CampaignResult(
    val campaignId: String,
    val sampleQuality: SimulationQuality,
    val placementResults: List<PlacementResult>,
    val crossPlacementEffects: List<CrossPlacementEffect>,
    val summaryInsights: List<InsightSummary>,
    val recommendations: List<OptimizationRecommendation>
)

data class SimulationQuality(
    val requestedAgents: Int,
    val generatedAgents: Int,
    val simulatedAgents: Int,
    val successfulAgents: Int,
    val successRate: Double,
    val warnings: List<String>,
    val blockingIssues: List<String>
)
```

### 4.1 关键建模决定

- `CampaignPlan` 是顶层输入对象
- `PlacementPlan` 是最小执行单元
- `CohortSnapshot` 必须独立保存，不能每次随机重造
- `AgentCampaignState` 用于承接跨 placement 影响
- `PlacementResult` 和 `CampaignResult` 分层，避免单 placement 和整案结果混在一起
- `SimulationQuality` 是一等公民，不是日志字段

---

## 5. 执行流程

## 5.1 基线方案执行

### 步骤 1：自然语言编译

用户输入自然语言方案后，Plan Compiler 输出 `CampaignPlan`。

注意：

- 必须支持 patch merge
- 不允许 parser 直接覆盖整份 plan
- 返回结果中要显式标出 `changedFields`

### 步骤 2：cohort 生成或复用

系统按平台和目标受众生成 `CohortSnapshot`。

要求：

- 生成结果带 `seed`
- 相同方案对比必须复用同一 cohort
- 新旧方案只允许改 plan，不允许换样本

### 步骤 3：placement 级仿真

系统逐个 placement 执行：

`PlacementPlan × CohortSnapshot -> PlacementResult`

每个 agent 在每个 placement 中跑 staged simulation：

- Stage 1: attention
- Stage 2: click
- Stage 3: conversion / intent

输出：

- passed
- factors
- reasoning
- per-stage failure metadata

### 步骤 4：campaign orchestration

当 placement 数量大于 1 时，不是独立结果直接求平均，而是：

- 更新 `AgentCampaignState`
- 叠加已看过的品牌曝光
- 计算 ad fatigue
- 更新 brand familiarity
- 允许后续 placement 受前序 placement 影响

初版不做复杂社交网络扩散，但至少要支持：

- 同品牌多次曝光后的疲劳
- 品牌熟悉度提升
- 已转化后停止继续转化

### 步骤 5：洞察聚合

聚合主链路：

`StageDecision.factors -> stage blockers/drivers -> segment × factor -> summary insights`

`reasoning` 只用在：

- 代表性引用
- 低频因子解释
- 报告摘要润色

### 步骤 6：业务指标校准

输出分为两层：

- `RawBehaviorMetrics`
  - attention rate
  - click rate
  - conversion intent / conversion rate
- `CalibratedBusinessMetrics`
  - estimated reach
  - estimated CTR
  - estimated CVR
  - estimated CPA
  - confidence / assumptions

### 步骤 7：优化建议生成

不是直接让 LLM 读结果写建议，而是：

1. 从 blockers 中识别优化杠杆
2. 生成 3-5 个 candidate changes
3. 在同一 cohort 上重跑 candidate plan
4. 比较 uplift / 风险 / 适用人群
5. 输出 recommendation

---

## 6. 多 Agent 在系统中的正确职责

多 Agent 应保留，但职责要被限制在“行为仿真层”。

## 6.1 多 Agent 负责什么

- 模拟不同 persona 对 placement 的反应
- 输出结构化 `factors + reasoning`
- 支撑 segment 差异分析
- 为优化建议提供可解释证据

## 6.2 多 Agent 不负责什么

- 不直接负责整案预算优化
- 不直接负责 reach / CPA 等业务指标可信度
- 不直接负责生成最终建议结论
- 不直接承担 plan merge 和数据契约

---

## 7. 指标口径设计

必须把口径拆开，否则系统会持续陷入“既说自然涌现，又偷偷混 benchmark”的叙事矛盾。

## 7.1 模拟信号

来自 placement simulation：

- attention rate
- click-through propensity
- conversion propensity
- blockers / drivers
- segment differences

这些是产品的核心资产。

## 7.2 业务估算指标

来自 calibration：

- reach
- impression-adjusted CTR
- calibrated CVR
- estimated CPA
- estimated incremental gain

这些指标必须显式标注为：

- estimate
- based on assumptions
- confidence level

---

## 8. 关键技术设计

## 8.1 Plan Parser 改造为 Patch Compiler

当前 parser 的问题是覆盖式输出，容易丢字段。

应改为：

```kotlin
data class PlanPatch(
    val changedFields: List<String>,
    val patch: Map<String, Any?>,
    val mergedPlan: CampaignPlan,
    val warnings: List<String>
)
```

前端交互改为：

- 用户输入变更
- 后端返回 `PlanPatch`
- 前端展示“将修改哪些字段”
- 用户确认后应用

## 8.2 Cohort 共享机制

需要新增 cohort 持久化或快照层。

要求：

- 每次 simulation 保存 `cohortId`
- recommendation 和 A/B 对比强制复用 cohort
- 同一 cohort 可用于 baseline / candidate plan 对比

## 8.3 Factor Catalog

需要统一因子字典，而不是每个 prompt 自由发挥。

示例：

- attention negative: `no_interest`, `creative_boring`, `ad_fatigue`, `wrong_format`, `no_brand_trust`
- click negative: `no_need`, `weak_hook`, `low_relevance`
- conversion negative: `price_too_high`, `competitor_preference`, `no_reviews`, `impulse_resist`

要求：

- 因子分阶段定义
- 有统一中文说明
- 支持映射到 recommendation rule

## 8.4 Sample Completeness Gating

必须启用质量门禁：

- `successRate < threshold` 时不能标记为正常完成
- 单 stage 解析失败必须计入质量报告
- 聚合层必须区分“样本少”和“真实低转化”

建议状态：

- `COMPLETED`
- `COMPLETED_WITH_WARNINGS`
- `FAILED_INSUFFICIENT_SAMPLE`

## 8.5 模型角色拆分

建议至少分四类模型配置：

- parser model
- simulation model
- clustering/report model
- interview model

每次 simulation 记录：

- modelName
- temperature
- promptVersion
- provider

用于复现和比较。

---

## 9. 推荐的实施阶段

## P0：立系统骨架

目标：让系统从“单 placement demo”变成“整案评估架构雏形”。

范围：

- 引入 `CampaignPlan`
- parser 改 patch merge
- 引入 `PlacementResult` 和 `CampaignResult`
- 引入 `SimulationQuality`
- 移除主链路里的 `firstOrNull()` 设计
- 明确区分 raw metrics 和 calibrated metrics

验收标准：

- 任意 simulation 都能看出它评估的是哪些 placement
- 结果页能展示 sample quality
- 关键输入字段不会被 parser 覆盖丢失

## P1：做可用的整案评估

目标：支持多个 placement 的共享 cohort 仿真和整案聚合。

范围：

- 新增 `CohortSnapshot`
- placement 逐个运行
- 引入基础 `AgentCampaignState`
- factor-first 聚合
- 输出 stage blockers / action candidates

验收标准：

- 多个 placement 不再只是“填了多条但只跑第一条”
- 同一批 cohort 可用于基线方案和候选方案对比
- 第一屏可以回答“哪个 placement 卡住了什么人群”

## P2：做优化建议闭环

目标：让“建议”来自反事实验证。

范围：

- recommendation candidate generator
- candidate plans 批量重跑
- uplift ranking
- recommendation evidence report

验收标准：

- 每条建议都能回答“为什么推荐”
- 每条建议都能回答“在哪些人群有效”
- 每条建议都能看到相对 baseline 的提升或风险

## P3：做校准闭环

目标：让业务数字越来越可信。

范围：

- token / cost tracking
- calibration config
- 实际投放结果回填
- 版本化校准参数

验收标准：

- reach / CPA / uplift 有来源说明
- 历史真实数据可以反哺校准

---

## 10. 当前代码迁移建议

结合现状，建议按下面顺序改造：

1. 重构 `SimulationInput` 为 `CampaignPlan` 过渡结构
2. 改造 `PlanParser`，新增 patch merge 契约
3. 将 `SimulationEngine` 拆为 `PlacementSimulator` + `CampaignOrchestrator`
4. 将 `ResultAggregator` 改为 factor-first，并产出 `PlacementResult`
5. 新增 `SimulationQuality`，把 `minSuccessRate` 真正接入流程
6. 将 recommendation 独立成单独模块，不挂在聚合器里顺手生成

---

## 11. 非目标

以下内容不应在当前阶段优先推进：

- 复杂社交传播网络模拟
- Agent 之间对话式传播
- 完整 MMM 替代
- 一开始就接所有广告平台 API
- 追求“纯涌现业务指标”

这些都会把系统重新拖回“大叙事、弱落地”。

---

## 12. 最终判断

AdSim 应坚持“整案评估 + 优化建议”这个产品方向，但技术上必须做成：

**多 Agent 行为仿真内核 + campaign orchestration + calibration + counterfactual optimization**

而不是：

**一个会生成 persona、会给理由、再顺手拼出指标页的 Agent demo。**
