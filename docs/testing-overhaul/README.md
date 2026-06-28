# Test-suite overhaul (in-progress task docs)

> **Status: proposal — awaiting maintainer decisions before execution.** This is a
> working set for an active task, kept here (like `docs/v3-directives.md` and the
> `legacy-1.8.9-*` design docs) rather than in `docs/dev/`, which holds *solidified*
> developer guides. Nothing here is final; the design moves to its permanent home
> once it is settled and the overhaul lands.

## The set

| Doc | Role | Eventual home (once solidified) |
| --- | --- | --- |
| [testing-architecture.md](testing-architecture.md) | Target testing design (layers, fixtures, live strategy) | Promote into `docs/` proper (e.g. a `docs/dev/` design guide) once the overhaul proves it out |
| [testing-rules.md](testing-rules.md) | Durable policy: when/if + how to write tests | Becomes a **skill** in `.claude/skills/` (e.g. `test-authoring`) so the rubric reaches the point of writing |
| [testing-overhaul-plan.md](testing-overhaul-plan.md) | The swarm execution plan to get there | Ephemeral — archive/remove once the overhaul completes |
| [testing-audit-findings.md](testing-audit-findings.md) | The 55-agent audit evidence the plan executes against | Ephemeral — archive/remove once the overhaul completes |

## The one-line why

A full audit of all 224 test files found the suite is **healthier than its size
suggests** but damaged in three localized ways — per-instance boilerplate
(~46 effect-kind files), production-string coupling (~72 spots), and sparse
load-bearing edges. The fix is **the right shape**, not more or fewer tests:
data-driven families, one source of truth for every string, and a thin but
complete live layer. Governing rule, proven by adversarial verification
(27 of 34 proposed deletions overturned): **collapse, never delete.**
