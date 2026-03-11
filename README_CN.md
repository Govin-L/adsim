中文 | [English](README.md)

# AdSim

> Simulate before you spend. 投放之前，先模拟。

基于 AI Agent 的营销效果预测引擎。输入预算和营销策略，通过多智能体模拟消费者行为，输出可量化的效果预测报告。

## 核心能力

- **消费者行为模拟** — 构建多类型消费者 Agent，模拟从曝光到转化的完整行为链路
- **预算分配优化** — 基于模拟结果，推荐最优渠道预算分配方案
- **量化效果预测** — 输出可衡量指标：ROI、CPA、转化率、渠道对比
- **可解释归因** — 不只给数字，还能解释"为什么这个渠道效果好/差"

## 架构

```
┌─────────────────────────────────────────────┐
│                  API 层                      │
│              (Spring Boot / Kotlin)          │
├─────────────────────────────────────────────┤
│                模拟引擎                      │
│  ┌─────────┐  ┌──────────┐  ┌────────────┐ │
│  │  消费者  │  │   渠道   │  │   活动     │ │
│  │  Agent   │  │  Agent   │  │  编排器    │ │
│  └─────────┘  └──────────┘  └────────────┘ │
├─────────────────────────────────────────────┤
│            MMM 校准层                        │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐ │
│  │ 广告延续 │  │ 饱和曲线 │  │  预算     │ │
│  │   模型   │  │          │  │  优化器   │ │
│  └──────────┘  └──────────┘  └───────────┘ │
├─────────────────────────────────────────────┤
│                 数据层                       │
│           MongoDB / Redis                    │
└─────────────────────────────────────────────┘
```

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
│           ├── agent/        # 消费者/渠道 Agent 定义
│           ├── simulation/   # 模拟引擎核心
│           ├── mmm/          # 营销组合模型（校准层）
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
