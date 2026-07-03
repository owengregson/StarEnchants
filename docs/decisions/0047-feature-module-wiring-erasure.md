# ADR 0047: Feature-module wiring erasure — one FeatureModule record folds the composition root

- **Status:** Accepted
- **Date:** 2026-07-02
- **Deciders:** project owner + agent
- **Extends:** ADR-0014 (the composition root + transactional reload), ADR-0044 (era erasure —
  the sibling axis: era erasure answers "which era", this answers "which feature")
- **Relates to:** ADR-0030 (menus — now module-declared), ADR-0038 (add-on registry — untouched
  substrate, constructed in the reload module), ADR-0041 (gesture listeners — unchanged, now
  module-declared), ADR-0042 (log/error policy — the fold keeps the exact strings), ADR-0045 /
  ADR-0046 (their wiring lowers into `BootCore` / the commands module)

## Context

The composition root had regrown a 592-line hand fold. `onEnable` carried 28 `registerEvents`
sites, a **triplicated** mint surface (`MintCatalog.entries()`, `SeCommand.give()`'s per-type
helpers, and 14 `/se <type>` self-mint arms — three hand-lists naming the same item types), a
12-term hand-OR'd plugin-item guard, boot-vs-live feature toggles distinguishable only by reading
*where* the boolean happened to be read, a 10-entry `onDisable` hand-list, and quit cleanup split
across three feature-local `PlayerQuitEvent` handlers. "Adding a feature is local" held everywhere
EXCEPT the root: a new feature edited `onEnable` in ≥3 places plus `SeCommand` plus `MintCatalog`.

Era erasure (ADR-0044) had already proven the pattern for the version axis: erase an axis at the
composition point behind ONE record + ONE structural gate. The feature axis is the twin problem.

## Decision

1. **One `FeatureModule` record** (11 axes: `toggle`+depth, ordered `wires`, dynamic `commands`,
   `mintables`, rank-ordered `menus`, `playerStores`, the `pluginItem` predicate, `langRoots`,
   `boots`, `stops`) is one feature's complete wiring manifest.

2. **One ordered registry** — `Modules` constructs the 19 modules explicitly in dependency order
   (each ctor takes `BootCore` plus the earlier modules it needs — no service locator, no
   classpath scan) and exposes `registry()` as the fold order. Modules consume era services only
   as root-fed `EraServices` seams; the era axis stays inside `EraBindings` (the ADR-0044
   boundary is intact — modules never construct an era class).

3. **One fold** — `ModuleFold.wire(...)` iterates the registry once per axis. Its wire pass
   reproduces the shipped listener registration sequence: `HandlerList` order per (event,
   priority) is the only persistent ordering `onEnable` creates, so the concatenation of every
   enabled module's `Events`/`PluginItemGuard`/`QuitSweep` wires IS that sequence. The **reordering
   lemma** — no event is delivered and no scheduled task runs during enable — makes every other
   move (construction, task starts, PAPI/ServicesManager registration, boot log lines) free. Two
   listeners move, each proven order-insensitive against every listener it crosses:
   `EngineStoreListener` (PlayerQuitEvent only) after `ImmuneListener`, and the heroic durability
   save (item-damage only) right after `HeroicListener`.

4. **Derived surfaces** — the operator mint menu, `/se give <type>`, and the `/se <type>`
   self-mint rows derive from `Mintable`; the vanilla guard from the OR of module contributions;
   the quit sweep from declared `playerStores`; `onDisable` from declared `stops`.

5. **`BootCore`** — the feature-neutral substrate (platform probe, the era seam, config sources,
   the item read path, the engine spine) extracted construction-only, so a module reads `core.*`
   rather than rebuilding it. The engine spine carries `soulService` because the pipeline's
   gate-10 spend, the sink's soul debit and both dispatchers' binding lookup consume it.

6. **Two static installers become instance wiring** — the anti-cheat movement exemption rides
   `SinkEnv`; cross-version vanilla-enchant application becomes an `item.mint.VanillaEnchants`
   instance. The remaining four (`friendlyFire`, `entityTypeResolver`, `customItemResolver`,
   `itemWrapWidth`) are gate-ratcheted, not dissolved.

7. **Structural gates** — `ModuleTreeGateTest` (lexical: registration monopoly, module
   membership, the static-installer ratchet, the disable monopoly, the mint monopoly) makes
   off-registry wiring a build failure; the semantic goldens (listener/stop/menu order) are
   pinned by a real boot and the fold's mechanics tests.

## Consequences

- `onEnable` 592 → ~20 lines; `onDisable` is `fold.stop()`; `registerEvents` sites in the plugin
  class 28 → 1 (the `PluginRegistrar` edge). ~108 hand-wired points become one ordered registry
  of 19 small module files. Mint: 3 hand-lists → 1 declaration set with 3 derived views. Mutable
  static installers 6 → 4 (frozen by the gate).
- Adding a feature = one module file + one registry line (+ its COMMANDS rows). Toggle depth is a
  stated, introspectable property (`/se modules`), not an accident of code position.
- Boot order becomes a reviewed artifact (a golden diff). Cost: two goldens to regen on feature
  addition, a rank field preserving the curated tile/menu orders, and a wider — but logic-free —
  module layer.
- `onDisable` exceptions no longer abort the remaining stops (a strict improvement; invisible in
  the no-throw case). Two accepted micro-deltas: the derived `/se give` tab-suggestion order
  (clients sort suggestions) and boot log-line ordering shifts within `onEnable` (same lines,
  same tick).

## Alternatives considered

- **A class-keyed service locator** — hides construction; rejected on the same grounds as
  ADR-0044's static-facade rejection.
- **Annotation / classpath-scanned module discovery** — unprovable order, reflection cost, the
  gate degrades to a heuristic; rejected.
- **Per-listener priority re-derivation instead of order preservation** — unverifiable against
  Bukkit's same-priority-registration-order semantics; rejected.
- **Folding lore sections as a module axis** — inverts the ADR-0040 construction order (services
  take `lore` in their ctors) for zero wiring-bug surface; rejected — sections are content
  policy, not per-feature registration.
- **Incremental partial erasure** — the intermediate states are not cleanly separable (a partial
  fold leaves the guard OR incomplete and the still-inline reload callback referencing drivers
  that have moved into modules); ratified as one composition-root swap.
