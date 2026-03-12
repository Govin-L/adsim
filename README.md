[中文](README_CN.md) | English

# AdSim

> Simulate before you spend.

AI Agent-powered marketing effectiveness prediction engine. Generate platform-specific user agents, simulate their reactions to your actual ad creatives, and let conversion metrics emerge naturally from agent behavior — not from formulas.

## How It Works

Traditional tools predict marketing performance by plugging numbers into statistical models. AdSim takes a fundamentally different approach:

1. **Generate User Agents** — Create hundreds of AI agents representing real platform users (Xiaohongshu users, TikTok users, etc.), each with unique demographics, interests, and behavior patterns
2. **Simulate Ad Delivery** — Place your actual ad creatives into agents' feeds and let each agent independently decide: Do I notice this? Do I click? Do I buy?
3. **Emerge, Don't Calculate** — CTR, CVR, CPA and other metrics emerge naturally from aggregated agent behavior, not from industry averages
4. **Explain the "Why"** — Interview any agent to understand their decision: "Why didn't you convert?" — "The price exceeded my budget for skincare products"

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                     API Layer                        │
│                (Spring Boot / Kotlin)                │
├─────────────────────────────────────────────────────┤
│                 Simulation Engine                    │
│  ┌────────────┐  ┌─────────────┐  ┌──────────────┐ │
│  │  Agent      │  │  Platform   │  │  Campaign    │ │
│  │  Generator  │  │  Simulator  │  │  Orchestrator│ │
│  └────────────┘  └─────────────┘  └──────────────┘ │
│                                                     │
│  Agent Generator:  LLM-driven persona creation      │
│  Platform Simulator: Feed simulation per channel    │
│  Campaign Orchestrator: Multi-channel coordination  │
├─────────────────────────────────────────────────────┤
│                   Data Layer                         │
│  ┌────────────┐  ┌─────────────┐  ┌──────────────┐ │
│  │  Industry   │  │  Ad Library │  │  User        │ │
│  │  Benchmarks │  │  (optional) │  │  Data (opt.) │ │
│  └────────────┘  └─────────────┘  └──────────────┘ │
│                 MongoDB / Redis                      │
└─────────────────────────────────────────────────────┘
```

## Key Features

- **Campaign Evaluation** — Input your plan, get multi-dimensional effectiveness predictions with full reasoning chains
- **Plan Comparison** — Compare Plan A vs Plan B side by side with simulated results
- **Optimal Recommendation** — Let the system recommend the best budget allocation
- **Agent Interview** — Drill into any agent's decision to understand *why*
- **Post-Campaign Validation** — Feed back actual results to measure prediction accuracy

## Tech Stack

- **Backend**: Kotlin + Spring Boot 3 + Langchain4j
- **Frontend**: React + Vite + Tailwind CSS + shadcn/ui
- **Database**: MongoDB + Redis
- **Deployment**: Docker Compose

## Getting Started

### Prerequisites

- JDK 21+
- Node.js 20+
- Docker & Docker Compose
- MongoDB & Redis (or use Docker Compose)

### Backend

```bash
cd server
./gradlew bootRun
```

### Frontend

```bash
cd web
npm install
npm run dev
```

### Docker Compose

```bash
docker compose up -d
```

## Project Structure

```
adsim/
├── server/               # Kotlin + Spring Boot backend
│   └── src/main/kotlin/
│       └── com/adsim/
│           ├── agent/        # User agent generation & management
│           ├── simulation/   # Simulation engine core
│           ├── api/          # REST API controllers
│           └── model/        # Data models
├── web/                  # React + Vite frontend
├── docs/                 # Documentation
└── docker-compose.yml    # Docker Compose config
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

[Apache License 2.0](LICENSE)
