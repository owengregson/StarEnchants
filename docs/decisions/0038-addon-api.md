# ADR 0038: Add-on API — a curated public SPI, not an engine re-export

- **Status:** Accepted
- **Date:** 2026-07-01
- **Deciders:** project owner + engine work
- **Relates to:** ADR-0014 (composition root, transactional reload), ADR-0022 (HELD/PASSIVE lifecycle),
  docs/architecture.md §2 (module boundaries), §7 (the SPI / `ParamSpec` four-ways single source)

## Context

`:api` shipped as a thin module whose only real content was the two Bukkit events
(`EnchantActivateEvent`, `StarEnchantsReloadEvent`) plus a blanket `api(project(":engine"))`
re-export — so an add-on compiling against `:api` transitively saw the ENTIRE engine: the 81-method
internal `Sink`, `EffectKind`, `EffectCtx`, `EffectRegistry`, every hot-path type. That is not a public
surface; it is the implementation leaking through a façade. Any refactor of an internal type would be a
breaking change for add-ons, and there was no curated, documented, stable contract to build against.

We want third-party plugins to be able to (1) contribute new effect kinds authorable in content YAML like
any built-in, and (2) read StarEnchants item state (enchants/crystals/set/slots) and trigger reloads —
without ever touching an internal type, and without `:api` depending on anything but the pure DSL language.

## Decision

**1. `:api` becomes a real, curated public surface depending only on `:schema`.** It drops
`api(project(":engine"))` and adds `api(project(":schema"))` (the DSL language definition — `ParamSpec` /
`ParamType` / `D`, the documented four-ways single source) plus the floor Paper API `compileOnly`. `:api`
depends on **nothing else in the repo**; `ApiBoundaryArchTest` fails the build if an `api.*` class ever
depends on anything outside `api.*` / `schema.*` / `java.*` / `org.bukkit.*`.

**2. A hand-curated `api.spi` package**, mirrors of the engine's internals adapted at the boundary:
`AddonEffect` (same stateless/hot-path contract as `EffectKind`), `AddonSpec` (built on the schema
`ParamSpec`, with an `api`-local `AddonAffinity` mirror + target slots since `Affinity`/`TargetSpec` live
outside `:schema`), `AddonEffectCtx` (typed arg/target/actor reads), and `AddonSink` — a **curated ~16
intents** chosen from the real 81-method `Sink` (damage delta, direct damage, potion, sound, particle,
message/actionbar, spawn, lightning, teleport, cancel, block change, velocity, item give, exp). Curation is
the point: the small surface stays stable while the engine evolves behind it.

**3. `api.StarEnchantsApi`** is the looked-up service: `registerEffect(AddonEffect)` (any time; triggers a
transactional reload so the new head becomes compilable) plus read-only queries (`enchantsOf`, `crystalsOf`,
`setOf`, `slotsOf`, `enchantKeys`) and `reloadContent()`.

**4. The bootstrap adapts.** `AddonBridge` wraps an `AddonEffect` as an engine `EffectKind` (translating
`AddonSpec` → `EffectSpec`, `AddonAffinity` → `Affinity`, and wrapping the engine `Sink`/`EffectCtx` in the
facades per activation). `ApiService` implements `StarEnchantsApi` over the live `ItemViewCache` /
`ContentHolder` / `ContentReloader`. Registered add-on kinds live in a concurrent list the composition root
folds into the effect registry on **every** build — the initial compiler, the executor, and each reload's
rebuilt compiler — so an add-on head survives `/se reload`. The executor picks up newly registered kinds via
`AbilityExecutor.bindEffects`, rebound on each reload swap (the same per-snapshot rebinding pattern as the
fault quarantine).

**5. Discovery is Bukkit's `ServicesManager`, not `ServiceLoader`.** `ServiceLoader` was considered and
**rejected**: it is unreliable across Bukkit's per-plugin classloaders (each plugin is isolated, and a
`META-INF/services` file in the StarEnchants jar cannot see an add-on's implementation class, nor vice
versa). The Bukkit-idiomatic equivalent is the shipped mechanism: an add-on declares
`depend: [StarEnchants]`, looks `StarEnchantsApi` up from the `ServicesManager` in its own `onEnable`, and
registers; StarEnchants registers the service at `ServicePriority.Normal`.

## Consequences

- Add-ons compile against a small, documented, stable surface; internal engine refactors no longer break
  them, and the boundary is CI-locked.
- `registerEffect` costs a transactional reload (off-thread build + atomic swap). That is deliberate: it is
  how a new head becomes compilable, and add-on registration is a rare startup-time event.
- The compiler is now rebuilt per reload (previously constant). Safe because the **resolver** instance is
  reused, so the §9 handle interning round-trip is preserved; only the effect spec set changes.
- The per-activation `AddonBridge` allocates two thin facade views. Acceptable: add-on effects are an
  opt-in path off the built-in combat spine the JMH gate guards.
- Add-ons contribute **effect kinds** only for now; conditions/selectors/triggers remain internal (a future
  ADR can extend the SPI symmetrically).

## Alternatives considered

- **Keep `api(":engine")`.** Rejected — it is not a public surface; it couples add-ons to every internal
  type and forbids refactoring.
- **`ServiceLoader` discovery.** Rejected — unreliable across Bukkit plugin classloaders (see §5).
- **Expose the full `Sink`.** Rejected — 81 intents, many niche/experimental; the value of a public API is
  the curation, and a small facade is what stays stable.
- **A live `Supplier<EffectRegistry>` constructor on `AbilityExecutor`.** Rejected in favour of the
  volatile `bindEffects` rebind, which matches the existing quarantine pattern and needed no change to the
  ~13 existing executor call sites.
