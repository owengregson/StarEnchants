# ADR 0026: Coordinate KNOCKBACK_CONTROL with the Mental knockback plugin

- **Status:** Accepted
- **Date:** 2026-06-24
- **Deciders:** project owner + agent
- **Relates to:** §N integrations (docs/v3-directives.md), ADR 0017 (integration packaging),
  the `KNOCKBACK_CONTROL` effect (task #36, `feature.combat.KnockbackListener`)

## Context

StarEnchants and the **Mental** knockback plugin (`me.vexmc.mental`) both modify a player's incoming
knockback, so on a server running both they collide. StarEnchants' `KNOCKBACK_CONTROL` effect writes a
short-TTL per-victim multiplier (`KnockbackControlStore`) that `feature.combat.KnockbackListener` reads
back on the vanilla knockback event (`EntityKnockbackEvent` modern / `EntityKnockbackByEntityEvent`
legacy) to scale or cancel the knockback.

Mental, however, *owns* player knockback. Its `KnockbackPipeline` serves the victim's
`PlayerVelocityEvent` at `HIGH` and `setVelocity`s its own residual-computed (1.7.10-model) vector,
**overwriting** whatever vanilla — and therefore StarEnchants' vanilla-event scaling — produced a few
priorities earlier. So with Mental installed, `KNOCKBACK_CONTROL` silently stops working for player
victims: the value SE scaled is discarded before the client ever sees it.

Mental anticipates exactly this kind of co-tenant and publishes a seam in its `api` module:

- `Mental.get()` → a `MentalApi` (with `moduleEnabled("knockback")`, knockback-profile control), and
- **`KnockbackApplyEvent`** — a `Cancellable` event fired *on the victim's owning thread immediately
  before Mental applies its computed vector*, exposing a mutable `velocity()`.

Mental's own `ocm-coexistence.md` documents the pattern it uses for the symmetric problem with
OldCombatMechanics: detect the other plugin **reflectively** (no hard dependency), and prefer
*deferring* a mechanic to fighting over it ("doubled knockback is worse than deferred knockback").

## Decision

**StarEnchants applies `KNOCKBACK_CONTROL` on Mental's `KnockbackApplyEvent` when Mental is installed**, so
the effect rides on Mental's vector instead of being overwritten. This lives in the core as a soft,
capability-probed combat edge (`feature.combat.MentalKnockbackBridge`) — the natural sibling of the
existing version-split `KnockbackListener`, *not* a separate add-on plugin — because the coordination must
read live in-process engine state (the `KnockbackControlStore`) at the instant Mental fires its event, which
the stateless SPI add-on model (protection/economy, ADR 0017) does not fit.

Concretely:

- **Reflective, no compile dependency.** SE references no Mental class; it hooks
  `me.vexmc.mental.api.event.KnockbackApplyEvent` reflectively via a Bukkit `EventExecutor` (the same
  mechanism `KnockbackListener` already uses for the modern Bukkit event), reading the victim and
  velocity through resolved `Method` handles. Mirrors Mental's own reflective OCM binding.
- **The flag maps to the velocity, not to a cancel.** Reading the shared store for the victim:
  no flag → leave Mental's vector untouched; `multiplier <= 0` → write a **zero** velocity (no knockback);
  otherwise → scale the vector. A non-positive multiplier deliberately does **not** cancel the apply
  event: cancelling tells Mental to "let vanilla velocity stand", which on a Mental-owned hit would leave
  the player with normal knockback — the opposite of `KNOCKBACK_CONTROL:0`.
- **Exactly one scaling survives per hit, with no skip logic.** SE reads the same store from both this
  hook and the vanilla `KnockbackListener`, but only one write reaches the client: when Mental owns a hit
  it overwrites the vanilla event (SE's vanilla scaling is discarded harmlessly) and this hook lands; when
  Mental yields a hit (OCM ownership, a full block, the module disabled) it fires no apply event and the
  vanilla path lands; mob victims (which Mental never touches) always take the vanilla path. The store
  read is idempotent (eviction is by TTL, not by read), so the redundant read never double-consumes.
- **Optional + togglable.** The bridge binds only when the event class is present, and
  `integrations.named.mental: false` switches it off (taking effect on the next server start, like SE's
  other boot-time integration toggles).
- **Folia-correct.** `KnockbackApplyEvent` fires on the victim's region thread and the store is concurrent
  and UUID-keyed; Mental runs on Folia, so this composes there too.

## Verification

- **Unit** — `MentalKnockbackBridgeTest` pins the pure flag→velocity decision (no-flag passthrough,
  zero-on-cancel, scale, input-immutability) against `KnockbackControlStore` semantics.
- **Compile** — the bridge is reflective, so it builds with no Mental on the classpath (CI's
  `./gradlew build`).
- **Store/effect side** — the existing `KnockbackControlStore`/`KnockbackControlEffect` unit and live
  suites already prove the producing half.
- **End-to-end with Mental installed** is verified on a server running both plugins, out of the live matrix
  (the matrix runs no Mental, exactly as the WorldGuard add-on is matrix-exempt under ADR 0017).

## Consequences

- `KNOCKBACK_CONTROL` keeps working unchanged whether or not Mental is installed; authors and content need
  no awareness of Mental.
- The core jar gains no Mental dependency, no maven repo, and no new public API surface — the bridge is a
  thin reflective edge alongside the existing knockback applier.
- **Known limitation:** if Mental's knockback module is enabled but *yields a specific hit to OCM*, that
  hit takes the vanilla velocity path which SE's vanilla listener does not override for players (it would
  otherwise be overwritten by Mental), so `KNOCKBACK_CONTROL` will not affect that OCM-owned hit. This is a
  niche tri-plugin (SE + Mental + OCM) corner; the common case (Mental owns knockback) is correct.

## Alternatives considered

- **A separate `addon-mental` plugin (the ADR-0017 pattern).** Rejected as the primary mechanism: the
  coordination needs an in-process read of the live `KnockbackControlStore` at Mental's event, which would
  force a new public `se-api` read seam exposing transient combat state purely for one external consumer —
  more surface for no benefit over the in-core reflective edge. (A future add-on bridging Mental's
  *profile* API — `setKnockbackProfile` — remains open as additive scope.)
- **Compiling against Mental's `api` jar.** Type-safe, but `api` is a sibling Gradle project not published
  to a maven repo SE resolves, and a hard/compile dependency on a co-tenant plugin is exactly what the
  reflective edge avoids. Reflection matches Mental's own approach to OCM.
- **Keeping only the vanilla `KnockbackListener`.** Leaves `KNOCKBACK_CONTROL` silently broken under
  Mental — the problem this ADR exists to fix.
