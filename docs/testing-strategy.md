# AdSim 测试设计方案

## 目标

测试不是为了追求覆盖率数字，而是为了稳住下面四类风险：

- 输入契约被 parser 或前端改坏
- 聚合口径被重构时悄悄漂移
- 接口返回结构变化导致前端失配
- 后续做 Stage 2/3 时，基础层没有回归保护

因此当前阶段的测试设计采用分层策略，而不是直接上大而全 E2E。

---

## 分层设计

### 第一层：纯逻辑单测

对象：

- `PlanParser`
- `ResultAggregator`
- 未来的 `FactorCatalog` / `RecommendationEngine`

目标：

- 快速
- 稳定
- 不依赖 Spring / Mongo / Redis / 真实 LLM

策略：

- 用 `FakeChatModel` 喂固定 JSON
- 构造固定 `Agent` 样本
- 对输入和输出做确定性断言

### 第二层：接口契约测试

对象：

- `SimulationController`

目标：

- 保证 API 返回结构稳定
- 保证 DTO 调整后前端不会被悄悄打坏

策略：

- 用 `MockMvc` 跑 controller slice
- mock service / parser / llm config
- 断言 JSON 字段名和结构

### 第三层：前端组件测试

当前暂不实施，但建议后续补：

- `CreateSimulation`
- `MetricsCards`
- `SimulationResult`

目标：

- 保证 patch preview、文案口径、状态禁用逻辑不回退

建议技术栈：

- `vitest`
- `@testing-library/react`
- `@testing-library/jest-dom`

### 第四层：少量 E2E smoke

当前不建议优先做。

等 Stage 2/3 稳定后，再补最小链路：

- 创建方案
- 解析 patch
- 启动 simulation
- 查看结果页

不建议当前阶段做真实 LLM E2E，因为：

- 成本高
- 不稳定
- 很难做确定性断言

---

## 当前优先级

### P0：必须补

- `PlanParserTest`
- `ResultAggregatorTest`
- `SimulationControllerTest`

### P1：后续补

- `CreateSimulation` 组件测试
- `MetricsCards` 组件测试

### P2：更后面

- campaign orchestration 的状态流转测试
- recommendation engine 的反事实对比测试
- 少量 E2E smoke

---

## 当前已落地

本轮已补第一批后端测试：

- `server/src/test/kotlin/com/adsim/agent/PlanParserTest.kt`
- `server/src/test/kotlin/com/adsim/simulation/ResultAggregatorTest.kt`
- `server/src/test/kotlin/com/adsim/api/SimulationControllerTest.kt`
- `server/src/test/kotlin/com/adsim/support/FakeChatModel.kt`

这些测试重点覆盖：

- Stage 1 的 patch merge 契约
- Stage 0 的聚合口径
- `/api/simulations/parse` 的接口结构

---

## 后续建议

如果继续补测试，建议顺序是：

1. 先补前端 `CreateSimulation` 的 patch preview 流程
2. 再补 `MetricsCards` 的口径展示测试
3. 然后等 Stage 2 落地后，补 placement-first 执行路径测试
4. 等 Stage 5 落地后，再补 recommendation 的反事实测试
