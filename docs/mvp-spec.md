# AdSim MVP Specification

## Goal

Validate the core hypothesis: **Can aggregated LLM agent behavior produce meaningful marketing effectiveness predictions?**

## Scope

### In Scope

| Feature | Description |
|---------|-------------|
| Single channel simulation | Xiaohongshu (小红书) only |
| User input | Product info + creative description + budget + target audience |
| Agent generation | Generate N user agents with independent personas |
| Simulation execution | Each agent decides through the full conversion funnel in one pass |
| Result aggregation | Emerged metrics from agent behavior distribution |
| Basic report | Simulation results + funnel breakdown + top reasons |
| Agent interview | Multi-turn conversation with any agent about their decision |

### Out of Scope (Future Iterations)

| Feature | Reason |
|---------|--------|
| Multi-channel comparison | Validate single channel first |
| Optimal plan recommendation | Depends on multi-plan comparison |
| Competitor analysis (Ad Library API) | Not on critical validation path |
| Post-campaign validation (data backfill) | After product-market fit |
| Ad platform API integration | Enhancement, not MVP |
| Image/video upload | MVP uses text description for creatives, multimodal later |
| User authentication | Open access for MVP |

## User Story

### Input

```
Product: Skincare serum, ¥299, anti-aging category
Platform: Xiaohongshu
Monthly budget: ¥200,000
Creative description: "Anti-aging focus, video seeding format, before/after comparison"
Target audience: Female, 25-35
```

### System Execution

1. Generate 200 Xiaohongshu user agents with diverse personas
2. Simulate ad delivery: each agent processes the full funnel in one LLM call
3. Aggregate all agent behaviors into metrics

### Output

```
Simulation completed: 200 agents processed

Funnel Breakdown:
  Exposure (200) → Attention (90, 45%) → Click (18, 20%) → Convert (7, 39%)

Emerged Metrics:
  Attention Rate: 45.0% (90 / 200)
  CTR: 20.0% (18 / 90 noticed)
  CVR: 38.9% (7 / 18 clicked)
  Overall Conversion Rate: 3.5% (7 / 200)
  Estimated CPA: ¥200,000 ÷ 7 × (200 / total_reach) = ~¥68

Top Drop-off Reasons:
  Attention → Click:
    - "Not interested in anti-aging at my age" (34%)
    - "Too many similar ads, skipped" (28%)
    - "Video thumbnail not appealing" (22%)
  Click → Convert:
    - "¥299 exceeds my skincare budget" (45%)
    - "No ingredient list, can't assess quality" (27%)
    - "Prefer to check reviews first" (18%)

Any agent can be interviewed for detailed reasoning.
```

## Technical Modules

### 1. API Layer

```
POST   /api/simulations              — Create and start a simulation
GET    /api/simulations/{id}         — Get simulation status and results
GET    /api/simulations/{id}/agents  — List all agents with their decisions
GET    /api/simulations/{id}/agents/{agentId}  — Get single agent detail

POST   /api/simulations/{id}/agents/{agentId}/interview  — Send interview message
  Request:  { message: string, conversationId?: string }
  Response: { reply: string, conversationId: string }

GET    /api/simulations/{id}/progress  — SSE endpoint for real-time progress
```

### 2. Agent Generator

Responsibility: Generate N platform user agents with independent personas.

Each agent persona includes:
- **Demographics**: age, gender, income level, city tier
- **Interests**: category preferences, brand awareness level
- **Platform behavior**: daily usage time, content format preferences, purchase frequency on platform
- **Consumption habits**: price sensitivity, decision speed, brand loyalty tendency

Generation strategy:
- Batch generation: 20 agents per LLM call (10 calls for 200 agents)
- Each batch specifies a demographic segment to ensure diversity
- Platform-specific persona templates (Xiaohongshu user characteristics built into prompt)

### 3. Simulation Engine

Responsibility: Run each agent through the conversion funnel.

**One agent = one LLM call.** The full funnel is evaluated in a single call to maintain decision coherence:

```
Input:  Agent persona + Ad creative description + Platform context
Output: Structured JSON with decisions and reasoning at each stage

{
  "attention": {
    "noticed": true,
    "reasoning": "The anti-aging topic matches my current skincare concerns"
  },
  "click": {
    "clicked": true,
    "reasoning": "The before/after format is convincing, want to learn more"
  },
  "conversion": {
    "converted": false,
    "reasoning": "¥299 is above my monthly skincare budget of ¥200"
  }
}
```

Funnel stages (3-step simplified model):
```
Attention: Would I notice this ad in my feed?
Click: Would I tap to learn more?
Conversion: Would I purchase?
```

Key design decisions:
- **One call per agent**: Ensures reasoning coherence across funnel stages
- **Structured output**: JSON mode for reliable parsing
- **Concurrency**: Kotlin coroutines, 20 agents in parallel (configurable)
- **Idempotency**: Simulation can be retried on partial failure

### 4. Result Aggregator

Responsibility: Collect all agent decisions and compute emerged metrics.

Output metrics:
- **Attention rate**: noticed / total agents
- **CTR**: clicked / noticed (not clicked / total)
- **CVR**: converted / clicked
- **Overall conversion rate**: converted / total agents
- **Estimated CPA**: budget × (agent_count / estimated_reach) / converted_count
- **Funnel drop-off distribution**: count + percentage + top reasons at each stage

Reason aggregation: Group similar reasons using LLM summarization, output top 3-5 reasons per drop-off point.

### 5. Agent Interview

Responsibility: Multi-turn conversation with any agent about their decision.

Implementation:
- LLM call with system prompt containing: agent persona + ad creative + their full decision record
- Conversation history maintained per session (conversationId)
- User can ask follow-up questions: "What would make you buy?", "What if the price was ¥199?"

### 6. Frontend

Pages:
- **Input form**: Product info, creative description, budget, target audience, platform selection
- **Simulation progress**: Real-time progress via SSE (e.g., "Processing agent 142/200...")
- **Results dashboard**: Funnel visualization, metrics cards, drop-off reason charts
- **Agent list**: Browse all agents, filter by decision (converted / not converted / etc.)
- **Agent interview**: Chat interface with selected agent

## Data Model

### Simulation

```json
{
  "id": "string",
  "status": "pending | generating | simulating | aggregating | completed | failed",
  "progress": { "total": 200, "completed": 142 },
  "input": {
    "product": {
      "name": "string",
      "price": "number",
      "category": "string",
      "description": "string"
    },
    "creative": {
      "description": "string",
      "format": "video | image | text"
    },
    "platform": "xiaohongshu",
    "budget": "number",
    "targetAudience": {
      "ageRange": [25, 35],
      "gender": "female",
      "interests": ["string"]
    }
  },
  "results": "SimulationResults (see below)",
  "createdAt": "timestamp",
  "completedAt": "timestamp"
}
```

### Agent

```json
{
  "id": "string",
  "simulationId": "string",
  "persona": {
    "name": "string",
    "age": "number",
    "gender": "string",
    "income": "string (low/medium/high)",
    "cityTier": "number (1-5)",
    "interests": ["string"],
    "platformBehavior": {
      "dailyUsageMinutes": "number",
      "contentPreferences": ["string"],
      "purchaseFrequency": "string (never/rarely/sometimes/often)"
    },
    "consumptionHabits": {
      "priceSensitivity": "string (low/medium/high)",
      "decisionSpeed": "string (impulsive/moderate/deliberate)",
      "brandLoyalty": "string (low/medium/high)"
    }
  },
  "decisions": {
    "attention": {
      "noticed": "boolean",
      "reasoning": "string"
    },
    "click": {
      "clicked": "boolean",
      "reasoning": "string"
    },
    "conversion": {
      "converted": "boolean",
      "reasoning": "string"
    }
  }
}
```

### SimulationResults

```json
{
  "totalAgents": "number",
  "metrics": {
    "attentionRate": "number",
    "ctr": "number",
    "cvr": "number",
    "overallConversionRate": "number",
    "estimatedCPA": "number"
  },
  "funnel": {
    "exposure": { "count": "number" },
    "attention": { "count": "number", "rate": "number" },
    "click": { "count": "number", "rate": "number" },
    "conversion": { "count": "number", "rate": "number" }
  },
  "dropOffReasons": {
    "attentionToClick": [
      { "reason": "string", "percentage": "number" }
    ],
    "clickToConversion": [
      { "reason": "string", "percentage": "number" }
    ]
  }
}
```

## LLM Cost Estimate

Per simulation (200 agents):

| Step | Calls | Tokens per call | Total tokens |
|------|-------|-----------------|-------------|
| Agent generation | 10 batch calls | ~2K output each | ~20K |
| Simulation (1 call/agent) | 200 calls | ~800 each (prompt+response) | ~160K |
| Reason aggregation | 1 call | ~3K | ~3K |
| **Total** | **211 calls** | | **~183K tokens** |

Estimated cost: **< $1 per simulation** with GPT-4o-mini or qwen-plus.

## LLM Configuration

MVP supports any OpenAI-compatible API:

```yaml
adsim:
  llm:
    api-key: ${LLM_API_KEY}
    base-url: ${LLM_BASE_URL:https://api.openai.com/v1}
    model: ${LLM_MODEL:gpt-4o-mini}
```

Recommended models:
- **GPT-4o-mini**: Good balance of quality and cost
- **qwen-plus**: Cost-effective for Chinese market scenarios
- **Claude Haiku**: Fast, good at structured output

## Prompt Design Strategy

The prompt is the most critical component. Small prompt changes can significantly shift emerged metrics.

### Agent Generation Prompt

Key requirements:
- Generate diverse personas that reflect real platform demographics
- Avoid homogeneity: explicitly instruct diversity in income, age, interests, city tier
- Include platform-specific behavioral traits (e.g., Xiaohongshu users: strong seeding/review mindset, trust KOL recommendations, visually driven)
- Each batch targets a different demographic segment

Anti-homogeneity techniques:
- Specify demographic distribution per batch (e.g., batch 1: age 18-22, batch 2: age 23-27, etc.)
- Include contrarian personas (users who dislike ads, budget-conscious users, brand-loyal users who won't switch)
- Vary engagement levels (heavy users, casual browsers, new users)

### Simulation Decision Prompt

Key requirements:
- Agent must stay in character based on their persona
- Decision must be grounded in persona attributes (not generic)
- Structured JSON output with reasoning at each funnel stage
- Prevent "pleasing bias": explicitly instruct that most users do NOT click ads

Anti-bias techniques:
- Include in system prompt: "In reality, the vast majority of users scroll past ads without noticing them. Be realistic."
- Provide platform context: "Average user sees 100+ content items per session, this ad is one of them"
- Temperature setting: 0.7-0.8 (enough variation without chaos)

### Prompt Evaluation

Before launch, validate prompts by:
- Running the same simulation 5 times, checking metric variance (target: <15% relative deviation)
- Comparing emerged metrics against known industry benchmarks for sanity check
- Testing with extreme inputs (¥10 product vs ¥10,000 product) to verify metrics move in the right direction

## Result Consistency

### Problem

LLM outputs are non-deterministic. The same simulation run twice may produce different metrics.

### Mitigation Strategy

| Approach | Description |
|----------|-------------|
| **Temperature control** | Set temperature to 0.7 for agent generation, 0.7 for simulation decisions |
| **Multiple runs averaging** | Default: run simulation 3 times, report average + range |
| **Confidence interval** | Report metrics as ranges (e.g., CTR: 1.0%-1.4%) rather than single numbers |
| **Minimum agent threshold** | 200 agents minimum. Below this, variance is too high. |

Output format reflects this:
```
CTR: 1.2% (range: 1.0% - 1.4% across 3 runs)
```

User can configure run count (1 = fast/cheap, 5 = stable/expensive).

## CPA Calculation

### Problem

200 agents is a sample, not actual reach. "7 out of 200 converted" doesn't directly map to real-world CPA.

### Calculation Method

```
1. Estimate total reach from budget:
   total_reach = budget / platform_CPM_benchmark
   (CPM benchmark is built-in per platform per category)

2. Apply emerged conversion rate:
   estimated_conversions = total_reach × overall_conversion_rate

3. Calculate CPA:
   CPA = budget / estimated_conversions
```

Example:
```
Budget: ¥200,000
Xiaohongshu beauty CPM benchmark: ¥35
Estimated reach: 200,000 / 35 × 1000 = 5,714,286 impressions
Emerged overall conversion rate: 3.5% (7/200)
Estimated conversions: 5,714,286 × 3.5% = 200,000
CPA: 200,000 / 200,000 = ¥1.0

Wait — this doesn't look right. The overall conversion rate includes
attention rate, which is modeled in the simulation but in reality
is determined by the platform's ad delivery algorithm.

Revised approach:
- Use simulation for: CTR (among those who see it) and CVR (among clickers)
- Use benchmark for: CPM (how many people see it)
- CPA = budget / (reach × CTR × CVR)
       = budget / ((budget/CPM×1000) × CTR × CVR)
       = CPM × 1000 / (CTR × CVR)

Example:
  CPM = ¥35, CTR = 20% (of noticed), CVR = 39% (of clicked)
  But we also need attention rate from simulation: 45%
  Effective CTR = attention_rate × CTR = 45% × 20% = 9%
  CPA = 35 / (9% × 39%) × 1000 / 1000 = 35 / 0.035 = ¥997

  Hmm, still needs refinement. This will be iterated during implementation.
```

**Decision**: CPA calculation formula will be finalized during implementation with real simulation data. MVP will clearly label CPA as "estimated" and show the full calculation breakdown for transparency.

## Error Handling

### LLM Call Failures

| Scenario | Strategy |
|----------|----------|
| API timeout | Retry up to 3 times with exponential backoff (1s, 3s, 9s) |
| Malformed JSON response | Retry once. If still malformed, mark agent as "failed" and skip |
| Rate limit (429) | Queue with backoff. Reduce concurrency dynamically |
| Partial simulation failure | Complete if ≥80% agents succeeded. Report actual count in results |
| Full failure | Mark simulation as "failed" with error message |

### Minimum Success Threshold

- **Agent generation**: All batches must succeed (retry on failure)
- **Simulation**: ≥80% of agents must complete (160/200). Below this, mark as "failed"
- **Results page**: Always show actual successful agent count: "Results based on 187/200 agents"

## Rate Limiting

LLM API rate limits vary by provider. Default conservative settings:

| Parameter | Default | Configurable |
|-----------|---------|-------------|
| Max concurrent LLM calls | 20 | Yes |
| Delay between batches | 100ms | Yes |
| Max retries per call | 3 | Yes |

```yaml
adsim:
  simulation:
    max-concurrency: 20
    batch-delay-ms: 100
    max-retries: 3
    min-success-rate: 0.8
    default-run-count: 3
```

## MVP Success Criteria

The MVP is considered validated if:

1. **Directional accuracy**: Emerged metrics move in the right direction for obvious cases
   - Cheap product (¥29) should have higher CVR than expensive product (¥2999)
   - Platform-audience match (beauty on Xiaohongshu) should outperform mismatch (industrial equipment on Xiaohongshu)

2. **Consistency**: Same input produces metrics within 15% relative deviation across multiple runs

3. **Explainability**: Users can trace any metric back to individual agent decisions and find the reasoning logical

4. **Usability**: End-to-end flow (input → wait → results → interview) completes in under 5 minutes

## Open Questions

1. **Agent count**: Is 200 enough for statistical significance? Need experimentation to find the sweet spot between cost and stability.
2. **Persona diversity**: How to ensure generated personas are representative of actual platform demographics? Consider sourcing from platform user reports.
3. **CPA formula**: The mapping from simulation ratios to absolute numbers needs iteration with real data.
4. **Validation methodology**: How to measure if emerged metrics correlate with real-world performance? Consider partnering with advertisers for A/B validation.
