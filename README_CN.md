中文 | [English](README.md)

# AdSim

> Simulate before you spend. 投放之前，先模拟。

基于 AI Agent 的营销效果预测引擎。生成平台用户 Agent，模拟他们看到你的真实广告素材后的行为反应，让转化指标从 Agent 行为中自然涌现——而不是从公式中算出来。

## 工作原理

传统工具通过统计模型和行业均值来预测营销效果。AdSim 采用完全不同的方法：

1. **生成用户 Agent** — 创建数百个代表真实平台用户的 AI Agent（小红书用户、抖音用户等），每个 Agent 有独立的人口特征、兴趣偏好和行为模式
2. **模拟广告投放** — 将你的真实广告素材投放到 Agent 的信息流中，每个 Agent 独立决策：会不会注意到？会不会点击？会不会购买？
3. **涌现而非计算** — CTR、CVR、CPA 等指标从 Agent 群体行为中自然涌现，而不是从行业均值套公式
4. **解释"为什么"** — 可以采访任何 Agent 了解其决策原因："为什么没有转化？"——"这个价格超出了我的护肤品预算"

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
│  平台模拟器：按渠道模拟信息流环境                     │
│  投放编排器：多渠道协调与调度                         │
├─────────────────────────────────────────────────────┤
│                    数据层                             │
│  ┌────────────┐  ┌─────────────┐  ┌──────────────┐ │
│  │  行业基准   │  │  广告素材库  │  │  用户数据    │ │
│  │  (内置)     │  │  (可选)     │  │  (可选)      │ │
│  └────────────┘  └─────────────┘  └──────────────┘ │
│                 MongoDB / Redis                      │
└─────────────────────────────────────────────────────┘
```

## 核心功能

- **方案评估** — 输入营销方案，获得多维度效果预测及完整推理链
- **方案对比** — A/B 方案并排对比模拟结果
- **最优推荐** — 系统推荐最佳预算分配方案
- **Agent 采访** — 深入了解任意 Agent 的决策原因
- **投后验证** — 回填实际投放数据，评估预测准确度

## 技术栈

- **后端**: Kotlin + Spring Boot 3 + Langchain4j
- **前端**: React + Vite + Tailwind CSS
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
