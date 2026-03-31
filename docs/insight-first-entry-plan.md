# Insight-First Entry Plan

## 结论

项目继续推进，但下一步只做一个入口包：

**Insight Report V1**

目标不是继续扩模拟世界，而是把当前模拟已经产出的 `factors + reasoning + segment` 变成用户一眼能用的洞察报告。

---

## 为什么选这个入口

### 1. 和产品方向完全一致

当前产品方向已经明确从“预测准确 CPA”转向“模拟用户真实反应 + 可操作洞察”。

### 2. 现有代码已经有 70% 基础

当前系统已经具备：

- staged simulation（3 阶段独立决策）
- `StageDecision.factors`
- `clusteredReasons`
- `segmentInsights`
- `topInsights`

真正缺的不是更多 Agent 能力，而是把这些结果组织成更稳定、更可执行的输出。

### 3. 这是最短验证路径

这个入口不需要：

- 新外部数据源
- 新基础设施
- 新存储系统
- 多方案并行执行框架

只需要把现有后端聚合层和结果页升级，就能验证用户是否愿意为“洞察”买单。

---

## 本阶段要交付什么

一次模拟完成后，用户第一页先看到这 4 个问题的答案：

1. 哪类人最容易转化？
2. 最大阻塞因素是什么？
3. 流失主要发生在哪个环节？
4. 下一步应该改什么？

这四个答案比 CTR / CVR / CPA 更应该成为结果页主入口。

---

## 范围

### In Scope

- 统一 `factors` 标签体系，作为稳定洞察主数据
- 基于 `factors` 做阶段阻塞分析
- 基于 `segment × factors` 做人群差异洞察
- 生成可操作建议文案
- 结果页改为“洞察优先，指标次级”

### Out of Scope

- 多方案对比
- MMM / Bayesian 校准
- 外部竞品素材抓取
- Agent 之间交互
- 长期记忆 / 图谱 / 推荐流仿真

---

## 设计原则

### 1. 以 `factors` 为主，`reasoning` 为辅

`reasoning` 适合做举例和引用，不适合作为核心聚合键。

聚合主链路应改为：

`StageDecision.factors -> 因素统计 -> 人群差异 -> 洞察结论 -> 推荐动作`

### 2. 洞察要稳定复现

常见结论应优先通过规则/统计生成，而不是每次再让 LLM 二次总结。

LLM 可以保留在：

- 原因聚类 fallback
- 洞察文案润色
- 少量报告摘要

### 3. 结果页先回答“怎么办”

指标卡和漏斗图继续保留，但移动到第二屏，不再作为主视图。

---

## 第一批实现包

### Package A: Factor Aggregation

目标：把当前分散在 prompt 里的因子标签收束成稳定字典，并进入结果模型。

建议新增或修改：

- `server/src/main/kotlin/com/adsim/simulation/ResultAggregator.kt`
- `server/src/main/kotlin/com/adsim/model/SimulationResults.kt`
- `server/src/main/kotlin/com/adsim/model/Agent.kt`
- `server/src/main/kotlin/com/adsim/simulation/FactorCatalog.kt`（新增）

输出建议新增：

- `factorInsights`
- `stageBlockers`
- `actionRecommendations`

最低验收：

- 每个阶段能输出 Top factors（count / percentage）
- 不再只依赖原始 `reasoning` 文本做聚合
- 相同 simulation 多次查看结果稳定一致

### Package B: Segment × Factor Insight

目标：让系统回答“哪个人群为什么掉”。

建议在聚合层增加：

- 高转化人群主因子
- 低转化人群主阻塞因子
- factor 在不同 segment 中的分布差

最低验收：

- 至少支持 `age / gender / income / priceSensitivity`
- 能输出 2-3 条可直接展示的洞察句子

### Package C: Insight-First Result UI

目标：把结果页从 dashboard 改成 report。

建议修改：

- `web/src/pages/SimulationResult.tsx`
- `web/src/components/InsightsBanner.tsx`
- `web/src/components/SegmentAnalysis.tsx`
- `web/src/components/DropOffReasons.tsx`
- `web/src/components/InsightReport.tsx`（新增）

页面顺序建议：

1. Executive Summary
2. Top Blockers
3. Winning / Losing Segments
4. What To Change Next
5. Funnel + Metrics
6. Agent List

最低验收：

- 用户无需读指标卡，也能理解“结果说明了什么”
- 第一屏能出现明确可操作建议

---

## 最小可开工任务

如果只开一个任务，就先做这个：

### Task 1: 用 `factors` 重写聚合主链路

原因：

- 这是整个洞察报告的地基
- 后端完成后，前端只是展示问题
- 它直接利用当前已有的 staged output，不需要改模拟引擎主流程

任务内容：

1. 抽出统一 `FactorCatalog`
2. 把 attention / click / conversion 的 factor 统计进 `SimulationResults`
3. 为每个阶段产出 Top blockers / Top drivers
4. 生成 3-5 条规则化 `actionRecommendations`

建议首版推荐动作格式：

- “注意阶段流失集中在 `no_brand_trust`，优先补品牌背书与用户评价。”
- “点击后流失集中在 `price_too_high`，优先测试价格锚点或试用装表达。”
- “高收入人群转化显著更高，可单独做高客单内容版本。”

---

## 完成标准

这个入口包完成后，应满足以下判断标准：

- 结果页主价值不再依赖 CPA 是否准确
- 用户能明确看到“谁会买、谁不会买、为什么、下一步改什么”
- 洞察主要来自结构化聚合，不是临时 LLM 总结
- 为后续方案对比打下稳定结果结构

---

## 完成后再做什么

Insight Report V1 做完后，下一阶段再进入：

1. 同 Agent 集对两套方案做 A/B 对比
2. 洞察差异报告
3. 历史结果回填与校准层

顺序不要反。
