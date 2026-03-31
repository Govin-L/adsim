中文 | [English](README.md)

# AdSim

> Simulate before you spend. 投放之前，先模拟。

基于 AI Agent 的营销方案评估系统。当前版本重点支持结构化方案解析、基于素材描述的 placement 级行为模拟，以及从 Agent 反应中提炼洞察。像 CPA 这类业务指标目前仍是结合行业基准的估算值，而不是纯涌现结果。

## 工作原理

传统工具通过统计模型和行业均值来预测营销效果。AdSim 当前采用分层混合的方法：

1. **生成用户 Agent** — 创建代表不同平台用户的人群样本，每个 Agent 有独立的人口特征、兴趣偏好和行为模式
2. **模拟 placement 反应** — 基于素材描述和市场环境，让每个 Agent 独立判断：会不会注意到？会不会点击？会不会购买？
3. **区分模拟信号与业务估算** — 行为信号来自仿真，像 CPA 这样的业务指标则结合预算和行业基准进行估算
4. **解释"为什么"** — 可以采访任何 Agent 了解其决策原因和阻塞因素

## 架构

```
┌─────────────────────────────────────────────────────┐
│                     API 层                           │
│                (Spring Boot / Kotlin)                │
├─────────────────────────────────────────────────────┤
│                    模拟引擎                           │
│  ┌────────────┐  ┌─────────────┐  ┌──────────────┐ │
│  │  Agent      │  │   平台      │  │   投放       │ │
│  │  生成器     │  │   模拟器    │  │   编排器     │ │
│  └────────────┘  └─────────────┘  └──────────────┘ │
│                                                     │
│  Agent 生成器：LLM 驱动的用户画像创建                 │
│  平台模拟器：placement 优先的行为模拟                 │
│  投放编排器：分阶段建设中                             │
├─────────────────────────────────────────────────────┤
│                    数据层                             │
│  ┌────────────┐  ┌─────────────┐  ┌──────────────┐ │
│  │  行业基准   │  │  广告素材库  │  │  用户数据    │ │
│  │  (内置)     │  │  (可选)     │  │  (可选)      │ │
│  └────────────┘  └─────────────┘  └──────────────┘ │
│          MongoDB（当前使用）/ Redis（预留）           │
└─────────────────────────────────────────────────────┘
```

## 当前能力

- **结构化方案评估** — 输入营销方案，获得模拟行为信号及推理链
- **placement 级洞察报告** — 查看漏斗、人群差异和流失原因
- **Agent 采访** — 深入了解任意 Agent 的决策原因
- **市场环境输入** — 支持竞品、品牌知名度、投放目标类型等上下文

## 正在建设

- **多 placement 整案编排** — 将多个 placement 作为一整套方案协同评估
- **优化建议** — 基于同一批样本对候选方案做反事实验证
- **投后校准** — 回填实际结果，逐步修正业务指标估算

## 技术栈

- **后端**: Kotlin + Spring Boot 3 + Langchain4j
- **前端**: React + Vite + Tailwind CSS + shadcn/ui
- **数据库**: MongoDB + Redis
- **部署**: Docker Compose

## 快速开始

### 环境要求

- JDK 21+
- Node.js 20+
- Docker & Docker Compose
- MongoDB & Redis（或使用 Docker Compose）

### 后端

```bash
cd server
./gradlew bootRun
```

### 前端

```bash
cd web
npm install
npm run dev
```

### Docker Compose

```bash
docker compose up -d
```

## 项目结构

```
adsim/
├── server/               # Kotlin + Spring Boot 后端
│   └── src/main/kotlin/
│       └── com/adsim/
│           ├── agent/        # 用户 Agent 生成与管理
│           ├── simulation/   # 模拟引擎核心
│           ├── api/          # REST API 控制器
│           └── model/        # 数据模型
├── web/                  # React + Vite 前端
├── docs/                 # 文档
└── docker-compose.yml    # Docker Compose 配置
```

## 参与贡献

请参阅 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 开源协议

[Apache License 2.0](LICENSE)
