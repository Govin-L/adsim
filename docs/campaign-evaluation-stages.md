# 整体营销方案评估实施 Stage 拆分

本文基于 [整体营销方案评估技术方案](./campaign-evaluation-architecture.md)，将实现路径拆分为多个可执行 stage。

拆分原则：

- 每个 stage 必须有独立交付物
- 每个 stage 完成后系统状态都应比当前更诚实、更稳定
- 后续 stage 不建立在“虚假能力”之上
- 优先修系统骨架，再做复杂优化能力

---

## 总览

| Stage | 名称 | 核心目标 | 是否阻塞后续 |
|------|------|---------|------------|
| Stage 0 | 契约与口径对齐 | 修正文档、接口和结果口径 | 是 |
| Stage 1 | Plan Compiler 重构 | 从覆盖式输入改为 patch merge | 是 |
| Stage 2 | Placement-First 执行内核 | 去掉 `firstOrNull()`，建立 placement 级结果 | 是 |
| Stage 3 | Cohort 与样本质量体系 | 建立可复用 cohort 和质量门禁 | 是 |
| Stage 4 | Campaign Orchestrator | 建立跨 placement 的整案状态流转 | 否 |
| Stage 5 | Insight & Recommendation Engine | 从 factor 到建议，形成优化闭环 | 否 |
| Stage 6 | Calibration & Feedback Loop | 让业务数字逐步可信 | 否 |

依赖关系：

`Stage 0 -> Stage 1 -> Stage 2 -> Stage 3 -> Stage 4 -> Stage 5 -> Stage 6`

其中：

- `Stage 4` 可以在 `Stage 3` 完成后开始
- `Stage 5` 可在 `Stage 4` 做到轻量版本后启动
- `Stage 6` 最后做，不阻塞整案评估的产品闭环

---

## Stage 0：契约与口径对齐

### 目标

先把“系统到底做什么、结果到底代表什么”说清楚，避免代码继续沿着错误叙事演进。

### 要解决的问题

- README 还在讲 multi-channel orchestration、optimal recommendation、post-campaign validation
- 当前结果页和后端没有明确区分模拟信号与业务估算指标
- 当前系统输入模型和产品描述存在错位

### 范围

#### 后端

- 为现有结果模型补充口径注释或字段说明
- 明确 `estimatedCPA` 属于 calibrated metric，不是纯仿真结果

#### 前端

- 结果页文案明确区分：
  - 模拟结果
  - 估算指标

#### 文档

- 更新 `README.md`
- 更新 `README_CN.md`
- 在 `docs/` 中补充当前阶段能力边界说明

### 主要文件

- [README.md](/Users/liugaowei/project/adsim/README.md)
- [README_CN.md](/Users/liugaowei/project/adsim/README_CN.md)
- [SimulationResults.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/model/SimulationResults.kt)
- [MetricsCards.tsx](/Users/liugaowei/project/adsim/web/src/components/MetricsCards.tsx)
- [SimulationResult.tsx](/Users/liugaowei/project/adsim/web/src/pages/SimulationResult.tsx)

### 交付物

- 文档和界面对当前能力的表述一致
- 结果指标被拆成“模拟信号 / 业务估算”两类口径

### 验收标准

- 不再把当前系统描述成已完成的 multi-channel orchestration
- 用户能看懂哪些数字来自仿真，哪些数字来自估算

---

## Stage 1：Plan Compiler 重构

### 目标

把当前覆盖式 parser 改成增量编译器，解决高价值字段丢失和方案不可控的问题。

### 要解决的问题

- parser 返回完整 `SimulationInput`，导致前端直接整体覆盖
- `competitors / brandAwareness / campaignGoal` 没被 parser 正确解析和保留
- “自然语言编辑方案”没有数据契约，后续整案评估难以建立

### 范围

#### 数据模型

- 引入过渡版 `CampaignPlan`
- 引入 `PlanPatch`
- 为 plan 增加 `metadata / warnings / changedFields`

#### 后端

- 将 `PlanParser` 从“全文解析”改成“基于当前 plan 的 patch compiler”
- 新增 patch merge 逻辑
- 新增字段守恒校验

#### 前端

- `CreateSimulation` 改为：
  - 当前 plan + 用户指令
  - 返回 patch 预览
  - 应用 patch 后更新 plan

### 主要文件

- [PlanParser.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/agent/PlanParser.kt)
- [Simulation.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/model/Simulation.kt)
- [SimulationDto.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/api/dto/SimulationDto.kt)
- [SimulationController.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/api/SimulationController.kt)
- [CreateSimulation.tsx](/Users/liugaowei/project/adsim/web/src/pages/CreateSimulation.tsx)
- [client.ts](/Users/liugaowei/project/adsim/web/src/api/client.ts)

### 交付物

- `PlanPatch` API
- 过渡版 `CampaignPlan`
- patch merge 前端交互

### 验收标准

- 自然语言编辑后，未修改字段不会丢失
- `competitors / brandAwareness / campaignGoal` 可在多轮编辑中稳定保留
- 接口返回里能明确看到 `changedFields`

### 退出条件

只有在 Stage 1 完成后，才允许继续做更复杂的整案编排。否则上层全是建在不稳定输入上的假能力。

---

## Stage 2：Placement-First 执行内核

### 目标

把当前“只跑第一条 placement”的执行模型改成真正的 placement 级执行模型。

### 要解决的问题

- 当前主链路依赖 `adPlacements.firstOrNull()`
- 结果结构没有 placement 维度
- 当前引擎无法区分“单条 placement 行为”与“整案结果”

### 范围

#### 数据模型

- 新增 `PlacementPlan`
- 新增 `PlacementResult`
- 为 simulation 结果增加 `placementResults`

#### 后端执行

- 将 `SimulationEngine` 拆出 `PlacementSimulator`
- 每个 placement 独立执行 staged simulation
- agent interview 绑定到 placement 维度，而不是默认第一条

#### 前端展示

- 结果页增加 placement 结果入口
- 支持查看单个 placement 的漏斗、blockers、segment

### 主要文件

- [SimulationEngine.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/simulation/SimulationEngine.kt)
- [ResultAggregator.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/simulation/ResultAggregator.kt)
- [InterviewService.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/simulation/InterviewService.kt)
- [SimulationResults.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/model/SimulationResults.kt)
- [Agent.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/model/Agent.kt)
- [SimulationResult.tsx](/Users/liugaowei/project/adsim/web/src/pages/SimulationResult.tsx)
- [client.ts](/Users/liugaowei/project/adsim/web/src/api/client.ts)

### 交付物

- placement 逐个执行
- placement 逐个聚合
- placement 逐个展示

### 验收标准

- 多个 placement 输入时，每个 placement 都有独立结果
- 代码主链路中不再出现决定执行路径的 `firstOrNull()` 设计
- 用户能看见“哪个 placement 表现好 / 差，以及原因”

### 风险

- 执行成本会显著上升
- 需要在 Stage 3 里补上 cohort 和样本质量，否则多 placement 很快失控

---

## Stage 3：Cohort 与样本质量体系

### 目标

让仿真样本可复用、可比较、可解释，解决当前结果“完成了但不一定可信”的问题。

### 要解决的问题

- persona 每次重造，A/B 不可比
- 部分 agent 失败会被吞掉，结果仍可能完成
- `minSuccessRate` 没有真正进入流程

### 范围

#### 数据模型

- 新增 `CohortSnapshot`
- 新增 `SimulationQuality`
- simulation 保存 `cohortId / cohortVersion / seed`

#### 后端执行

- cohort 按平台与目标受众生成
- 同一批 cohort 可在多个 placement 和 candidate plans 中复用
- 失败统计进入质量报告
- 接入 sample completeness gating

#### 前端展示

- 结果页增加 sample quality 区块
- 清晰展示：
  - requested agents
  - successful agents
  - warnings

### 主要文件

- [AgentGenerator.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/agent/AgentGenerator.kt)
- [SimulationService.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/simulation/SimulationService.kt)
- [SimulationConfig.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/config/SimulationConfig.kt)
- [Simulation.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/model/Simulation.kt)
- [SimulationResults.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/model/SimulationResults.kt)
- [SimulationResult.tsx](/Users/liugaowei/project/adsim/web/src/pages/SimulationResult.tsx)

### 交付物

- 可复用 cohort
- 样本完整度报告
- 质量门禁状态

### 验收标准

- 基线方案和候选方案可复用同一 cohort
- 样本失败不会被静默伪装成完成
- 低于阈值时 simulation 状态不会显示为正常完成

### 退出条件

只有 Stage 3 完成后，后续 recommendation 才有可信基础。

---

## Stage 4：Campaign Orchestrator

### 目标

让系统从“placement 的并列集合”升级为“整场 campaign 的时序仿真”。

### 要解决的问题

- 当前多 placement 只能各跑各的，没有跨 placement 影响
- 品牌熟悉度、广告疲劳、已转化状态没有整案状态
- 还无法回答“这份整案组合是否合理”

### 范围

#### 数据模型

- 新增 `AgentCampaignState`
- 新增 `CrossPlacementEffect`

#### 后端执行

- 引入 `CampaignOrchestrator`
- placement 按 sequence 执行
- 更新 agent 的：
  - seen placements
  - exposure count
  - fatigue
  - brand familiarity
  - converted products

#### 聚合

- 产出 cross-placement summary
- 产出 campaign-level blockers

### 主要文件

- [SimulationEngine.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/simulation/SimulationEngine.kt)
- [SimulationService.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/simulation/SimulationService.kt)
- [Agent.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/model/Agent.kt)
- [SimulationResults.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/model/SimulationResults.kt)
- [ResultAggregator.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/simulation/ResultAggregator.kt)

### 交付物

- campaign-level 状态流转
- cross-placement effects
- campaign summary

### 验收标准

- 系统能回答同品牌多 placement 下的疲劳和熟悉度变化
- 已转化 agent 不会在后续 placement 里继续被当作未转化用户
- 整案结果不再只是 placement 结果的简单拼接

---

## Stage 5：Insight & Recommendation Engine

### 目标

建立从 `factor` 到“优化建议”的闭环，并让建议来自验证过的候选方案，而不是即时生成文案。

### 要解决的问题

- 当前 insights 仍偏摘要，不是动作建议
- factors 还没有真正成为 recommendation 的主驱动数据
- 还不能基于同一 cohort 做 candidate comparison

### 范围

#### 洞察层

- 统一 `FactorCatalog`
- 新增：
  - `stageBlockers`
  - `stageDrivers`
  - `segmentFactorInsights`
  - `actionCandidates`

#### 推荐层

- 新增 `RecommendationEngine`
- 从 blockers 推导候选动作
- 生成 candidate plans
- 复用同一 cohort 重跑
- 输出 uplift / risk / target segment

#### 前端

- 结果页改为 insight-first
- 增加 recommendation panel
- 增加 baseline vs candidate diff 展示

### 主要文件

- [ResultAggregator.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/simulation/ResultAggregator.kt)
- [SimulationResults.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/model/SimulationResults.kt)
- [SimulationResult.tsx](/Users/liugaowei/project/adsim/web/src/pages/SimulationResult.tsx)
- [InsightsBanner.tsx](/Users/liugaowei/project/adsim/web/src/components/InsightsBanner.tsx)
- [DropOffReasons.tsx](/Users/liugaowei/project/adsim/web/src/components/DropOffReasons.tsx)
- [SegmentAnalysis.tsx](/Users/liugaowei/project/adsim/web/src/components/SegmentAnalysis.tsx)

### 建议新增文件

- `server/src/main/kotlin/com/adsim/simulation/FactorCatalog.kt`
- `server/src/main/kotlin/com/adsim/simulation/RecommendationEngine.kt`
- `web/src/components/RecommendationPanel.tsx`
- `web/src/components/PlacementResultTabs.tsx`

### 交付物

- factor-first 洞察主链路
- 建议候选生成
- 反事实验证建议

### 验收标准

- 每条建议都能说明触发它的 blockers / segments
- 每条建议都能展示相对 baseline 的预期变化
- 结果页第一屏能直接回答“下一步该改什么”

---

## Stage 6：Calibration & Feedback Loop

### 目标

把系统从“有解释的仿真工具”进一步推进为“业务指标逐步可信的评估系统”。

### 要解决的问题

- CPA、reach、uplift 仍依赖静态常数
- 模型成本不透明
- 没有真实投放结果回填和校准闭环

### 范围

#### 成本与用量

- token usage tracking
- per-stage 成本分解
- provider pricing config

#### 校准层

- calibration config
- benchmark versioning
- campaign result confidence

#### 反馈闭环

- 实际投放结果回填
- 偏差分析
- 校准参数更新

### 主要文件

- [LlmRequestConfig.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/config/LlmRequestConfig.kt)
- [LlmConfig.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/config/LlmConfig.kt)
- [SimulationService.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/simulation/SimulationService.kt)
- [Simulation.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/model/Simulation.kt)
- [SimulationResults.kt](/Users/liugaowei/project/adsim/server/src/main/kotlin/com/adsim/model/SimulationResults.kt)
- [client.ts](/Users/liugaowei/project/adsim/web/src/api/client.ts)

### 交付物

- token / cost report
- calibrated metrics metadata
- feedback loop 能力

### 验收标准

- 用户能看到每次 simulation 的 token 消耗与成本估算
- calibrated metrics 带有 assumptions / confidence
- 有真实投放数据后可做偏差回填和参数修正

---

## 推荐执行节奏

### 第一批必须完成

- Stage 0
- Stage 1
- Stage 2
- Stage 3

原因：

- 这四个 stage 解决的是系统是否诚实、输入是否稳定、placement 是否真实执行、样本是否可信
- 不完成这四步，后面的 recommendation 和 calibration 都会建立在假基础上

### 第二批建议推进

- Stage 4
- Stage 5

原因：

- 这是整案评估和优化建议真正开始形成差异化的阶段

### 第三批再做

- Stage 6

原因：

- 它对业务价值很重要，但不应阻塞“整案评估 + 优化建议”主能力成型

---

## 最终实施建议

如果只开一个主任务流，建议按以下顺序：

1. 先完成 Stage 0-1，修正输入契约和系统口径
2. 再完成 Stage 2-3，建立真实 placement 执行和样本可信度
3. 再完成 Stage 4-5，形成整案评估与建议闭环
4. 最后完成 Stage 6，把数字从“参考”推进到“逐步可信”

对当前仓库来说，**真正的分水岭是 Stage 2 和 Stage 3**：

- Stage 2 决定系统是否真的在评估多个 placement
- Stage 3 决定系统结果是否值得相信
