---
name: config-and-migration
description: Use when working on the config schema/YAML, the DSL and ParamSpec, the compiler (resolve/typecheck/lower/erase/snapshot), diagnostics, transactional reload, validateContent, or the EE/EA/AE and legacy-item migrator.
---

# Config + migration: content is a compiled program

Authored YAML+DSL is **compiled**, not interpreted (§10). `se-compile` turns the
whole library into one immutable `Snapshot`, published by a single `AtomicReference`
(§0). The hot path never sees a string; see **effect-engine** for what runs the
compiled `Ability[]`.

## Use when / not

Use for the schema, the DSL grammar + `ParamSpec`, any compiler phase,
diagnostics, `/se reload`, `validateContent`, or the migrator. **Not** for the
runtime engine (**effect-engine**), PDC layout (**item-data-model**), or per-hit
cost (**performance-hot-paths**).

## The pipeline (`se-schema` → `se-compile`, both PURE, zero Bukkit)

| Phase | Does | Source |
|---|---|---|
| grammar | tokenize → **untyped AST** (effect lines, condition expr `&& \|\| ()`, selectors, inline tags) | §2 |
| resolve | names + cross-version handles via an **injected `PlatformResolvers` facade** (tests pass a fake) | §2, §9 |
| typecheck | `ParamSpec`-driven arity/type/range/enum/cross-ref → Diagnostics; **NEVER throws** | §7, §10 |
| lower | AST → `CompiledEffect`/`CompiledCondition` (typed args, pre-built AST); `WAIT` → **cumulative `waitTicks`** | §3.2 |
| erase | **SOURCE ERASURE**: every source → ONE `Ability[]`; assigns dense ids + the **stable-key↔id map** | §4.1, §5.3 |
| snapshot | immutable `Snapshot`: `Ability[]`, per-trigger index, suppression + damage plans, interners | §2 |

## Hard rules

- **Cross-version resolution is a COMPILE phase** (§9): legacy tokens
  (`CONFUSION`→nausea, `GENERIC_MAX_HEALTH`→max_health) become resolved handles
  interned into the effect; the runtime can never touch a renamed constant.
  Unknown token → file/line diagnostic, **warn-and-skip that one op**, load never
  crashes. See **cross-version-item-api** for the alias maps.
- **Diagnostics carry `Source(file,line,col)`** from SnakeYAML Marks through
  compile (§2 diag, §10). Surfaced at load, on `/se reload`, via `/se problems`.
  Never an exception.
- **Transactional, all-or-nothing reload** (§10): build off-thread; **fatal
  diagnostics keep the OLD `Snapshot` live** — a bad edit never takes the server
  down. Clean → swap the `AtomicReference` on the global thread.
  `/se reload --dry-run` compiles + reports without swapping.
- **`./gradlew validateContent`** compiles the whole bundled library against a
  **fake `PlatformResolvers`**, failing on fatal diagnostics — hundreds of enchants
  audited as a reviewed diff, not a live-server gamble (§10).
- **Migrator emits commented, reviewable YAML with inline TODOs** (not opaque
  transforms), reusing the same `ParamSpec` + alias maps (§10). It reads legacy
  item NBT **lazily, losslessly** into the modern PDC record on first touch (§4.3)
  — see **item-data-model**.

## ParamSpec — one declaration, four uses (§7)

One declaration drives typecheck/range, tab-completion, `/se docs`, and migration:

```java
    .param("chance",   D.DOUBLE.min(0).max(100))   // range → completion → docs → migrate
    .param("cooldown", D.INT.min(0).def(0))
    .affinity(Affinity.AOE)                          // folded to ability level at erase (§3.6)
```

See **effect-engine** for the full `EffectKind` class shape. A malformed line is a
`file:line:col` diagnostic at load, never a `NumberFormatException` mid-combat (§7).
Registration is an **explicit, greppable, checked-in registry** — not
annotation-processor codegen (§7, §13 #2).

## Boundaries (§2)

`se-schema` = the DSL as a typed language. `se-compile` = the compiler (Bukkit-free
via the injected facade). `se-migrate` = legacy NBT reader + EE/EA/AE importer.
PDC stores stable string keys; dense `Ability.id` is a per-run accelerator only —
items resolve by stable key after any reorder (§5.3).
