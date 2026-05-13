[中文](README_CN.md) | English

# AdSim

> Simulate before you spend.

AI-agent-assisted marketing plan evaluator. The current build focuses on structured plan parsing, placement-level simulation from creative descriptions, and insight generation from agent behavior. Business metrics such as CPA remain benchmark-based estimates rather than pure emergent outputs.

## How It Works

AdSim uses LLM-driven agent simulation to help you understand how a marketing plan might land — not by predicting exact numbers, but by surfacing structured insights about audience reactions:

1. **Generate User Agents** — Create AI agents representing platform users, each with distinct demographics, interests, and behavior patterns
2. **Simulate Staged Decisions** — Each agent makes attention → click → conversion decisions in separate stages with only the information visible at each stage, producing structured reasoning tags
3. **Aggregate Structured Insights** — Cluster drop-off reasons, identify segment differences, surface key blockers, and generate actionable recommendations from agent reasoning
4. **Explain the "Why"** — Interview any agent to understand their decision path and blocking factors
5. **Business Metric Estimates** — CTR/CVR from simulation statistics; CPA estimated from budget and industry benchmarks, flagged as reference values

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
│  Platform Simulator: Placement-first simulation     │
│  Campaign Orchestrator: Incremental roadmap         │
├─────────────────────────────────────────────────────┤
│                   Data Layer                         │
│  ┌────────────┐  ┌─────────────┐  ┌──────────────┐ │
│  │  Industry   │  │  Ad Library │  │  User        │ │
│  │  Benchmarks │  │  (optional) │  │  Data (opt.) │ │
│  └────────────┘  └─────────────┘  └──────────────┘ │
│         MongoDB (active) / Redis (reserved)         │
└─────────────────────────────────────────────────────┘
```

## Current Features

- **Drop-off Reason Clustering** — Auto-group 5-8 key drop-off reasons from agent reasoning, with share percentages and representative quotes
- **Segment Insights** — Compare conversion differences by age, gender, income, and price sensitivity, with automatic highlight of best/worst segments
- **Placement-Level Funnel** — Attention → click → conversion funnel per placement, with stage blockers and recommendations
- **Agent Interview** — Drill into any agent's decision to understand *why*
- **Market Context Inputs** — Include competitors, brand awareness, and campaign goal in the plan
- **Reference Metrics** — CTR/CVR from simulation + industry benchmark-based CPA estimates, flagged as reference values

## In Progress

- **Multi-placement orchestration** — Evaluate multiple placements as a coordinated campaign rather than a single placement
- **Optimization recommendations** — Generate and validate candidate improvements against the same cohort
- **Post-campaign validation** — Feed back actual results to calibrate future estimates

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
