# AdSim

> Simulate before you spend.

AI Agent-powered marketing effectiveness prediction engine. Input your budget and marketing strategy, simulate consumer behavior through multi-agent orchestration, and get quantifiable performance predictions.

## What It Does

- **Consumer Behavior Simulation** — Build multi-persona consumer agents that simulate the full journey from exposure to conversion
- **Budget Allocation Optimization** — Recommend optimal channel budget distribution based on simulation results
- **Quantifiable Predictions** — Output measurable metrics: ROI, CPA, conversion rates, channel comparisons
- **Explainable Attribution** — Not just numbers, but *why* a channel performs well or poorly

## Architecture

```
┌─────────────────────────────────────────────┐
│                  API Layer                   │
│              (Spring Boot / Kotlin)          │
├─────────────────────────────────────────────┤
│              Simulation Engine               │
│  ┌─────────┐  ┌──────────┐  ┌────────────┐ │
│  │ Consumer │  │ Channel  │  │  Campaign   │ │
│  │  Agents  │  │  Agents  │  │ Orchestrator│ │
│  └─────────┘  └──────────┘  └────────────┘ │
├─────────────────────────────────────────────┤
│            MMM Calibration Layer             │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐ │
│  │ Adstock  │  │Saturation│  │  Budget    │ │
│  │  Model   │  │  Curves  │  │ Optimizer  │ │
│  └──────────┘  └──────────┘  └───────────┘ │
├─────────────────────────────────────────────┤
│               Data Layer                     │
│        MongoDB / Redis                       │
└─────────────────────────────────────────────┘
```

## Tech Stack

- **Backend**: Kotlin + Spring Boot 3 + Langchain4j
- **Frontend**: React + Vite + Tailwind CSS
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
│           ├── agent/        # Consumer / Channel agent definitions
│           ├── simulation/   # Simulation engine core
│           ├── mmm/          # Marketing Mix Model (calibration layer)
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
