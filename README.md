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

---

## Philosophical Reflection: The "Emergence" Illusion in Multi-Agent Simulation

> **Added 2026-05-13.** This is an open question — discussion welcome.

Since this project started, we've kept asking ourselves: can this kind of agent simulation truly "emerge" intelligence beyond prompt engineering?

### The Problem: Curve-Fitting vs. True Emergence

Most multi-agent simulations today, AdSim included, fundamentally resemble quantitative strategy backtesting:

| Quant Trading | Multi-Agent Simulation |
|-------------|-----------------------|
| Tune parameters to fit historical price curves | Tune prompts / agent count / decision flow to fit expected outcomes |
| Looks great backtesting, blows up live | Demo is impressive, falls apart on new scenarios |
| Overfitting | "Intelligence" tuned for specific campaign plans |

True emergence should be a system-level phase transition, not a config that a developer hand-tuned to make outputs look good. If emergence can be "designed," it's not emergence.

### Root Cause: Homogeneous Agent Population

Most multi-agent frameworks — and AdSim isn't an exception here — drive all agents with the same underlying LLM. This means:

- The same knowledge boundaries
- The same reasoning flaws
- The same cognitive biases

A crowd of identical individuals cannot produce intelligence beyond the individual level. This is glorified majority voting — statistically more stable, but nothing genuinely new is born.

### What This Means for AdSim

Acknowledging this limit doesn't invalidate agent simulation. AdSim's value lies in **structured insight generation**, not emergent prediction:

1. **Drop-off clustering** is valuable — it helps human decision-makers see collective response patterns
2. **Segment comparison** is valuable — it reveals behavioral differences across demographics
3. **Agent interviews** are valuable — they anchor LLM reasoning in a concrete decision scenario

None of these require "emergence." They leverage what LLMs already do well: structured reasoning in context.

### The Real Breakthrough: Heterogeneity

If we're going to push past this ceiling, the direction is clear: **introduce agent heterogeneity.**

- **Model-level**: Different agents use different underlying models (one analytical, one intuitive, one conservative)
- **Knowledge-level**: Each agent has independent memory and knowledge bases, simulating real users' personalized cognition
- **Personality diversity**: Not just demographic tags, but genuine decision-style variance
- **Inconsistent behavior**: Same input triggers different reactions across agents, rather than all being driven by the same LLM with different system prompts

### The Takeaway

**Heterogeneity is a prerequisite for emergent intelligence.**

If every agent is a clone of the same person, the group won't outperform the individual. AdSim's current value doesn't depend on emergence — and that's a conscious choice to build a useful product within current constraints. If and when agent heterogeneity becomes a practical reality, emergence may finally become a real feature.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

[Apache License 2.0](LICENSE)
