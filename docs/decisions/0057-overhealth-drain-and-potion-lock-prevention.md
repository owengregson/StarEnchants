# ADR 0057: Overhealth drain removes EVERY source; potion-lock genuinely prevents, not just re-strips

- **Status:** Accepted
- **Date:** 2026-07-12
- **Deciders:** project owner + agent
- **Extends:** ADR-0044 (era seams), ADR-0052 (pets — Grim's overhealth trade), ADR-0053 follow-up
  (`applyWornMaxHealth` — worn max-health rides ONE reconciled modifier)
- **Relates to:** ADR-0051 (heal/death coherence), the F07/F08 timed-revert quit closure

## Context

"Overhealth" — max health above the vanilla 20 — is granted in this pack three mechanically distinct ways,
and two features remove it: the Grim pet's ACTIVE trade (`fraction: 1.0`, "your overhealth vanishes for the
window") and Cupid's Lovestruck (`fraction: 0.5`, "strip half the victim's overhealth"). Both go through the
`MAX_HEALTH_DRAIN` effect → `Sink.drainMaxHealth`. Two defects, reported live:

1. **The drain only saw BASE overhealth.** `drainMaxHealth` measured overhealth from the max-health attribute's
   BASE value (`getBaseValue()`) and reduced the base. But every real overhealth source in this pack lives in
   an attribute **MODIFIER**, not the base: a `HEALTH_BOOST` potion (Overload / Godly Overload enchants, the
   Nature Crystal) is a vanilla max-health modifier, and the worn `HEALTH` channel (ADR-0053 follow-up,
   "santa-hat-style" +hearts) is a named reconciled modifier via `applyWornMaxHealth`. With `base` sitting at
   20, `overhealth = base − 20 = 0`, so the drain was a **no-op against modifier-sourced hearts** — Grim and
   Lovestruck played their sound, particles, and cooldown but the hearts stayed. Worse, base reduction floors
   at 1 (`max(1, base − drain)`), so it could not fully offset a large `HEALTH_BOOST` even in the base case.

2. **Potion-lock re-stripped, it never prevented.** `POTION_LOCK` (druid's Speed lock, fantasy's
   Speed-while-webbed, clarity's Blindness, and Grim's Overload lock) stripped the effect and re-stripped it
   every tick. But a PASSIVE buff is maintained permanent-while-worn by `PassiveEffectDriver`, which re-asserts
   it on its own cadence — so the effect **flashed in on the tick the driver re-applied it and out on the next
   re-strip**: "the overhealth comes back glitching in sometimes and then goes away."

## Decision

**Overhealth is measured and removed on the EFFECTIVE max health, source-agnostically; a locked potion is
DENIED at application, not chased with a re-strip.**

1. **`drainMaxHealth` takes effective overhealth via a temporary negative modifier.** It now reads
   `overhealth = maxHealth(target) [getValue(), base + modifiers] − baseline`, clamps the drain so effective
   never falls below 1, and removes it by applying ONE **negative max-health `AttributeModifier`** of a unique
   per-drain identity (never a base write). A negative additive modifier lowers the effective cap by exactly
   the drain whatever the source — base, `HEALTH_BOOST` pool, or the worn reconciled modifier — so it removes
   **all** overhealth uniformly. The removal is restored after the window via the existing `TimedRevert`
   (F07-quit-safe: a logout mid-window drops the modifier before the playerdata save, so the reduction can
   neither be made permanent by logging out nor leak a permanent modifier onto disk). Two new era leaves,
   `addMaxHealthModifier` / `removeMaxHealthModifier`, implement it on both overlays (modern Bukkit
   `AttributeInstance.addModifier` + remove-by-`getUniqueId()` since the 1.17.1 floor has no
   `removeModifier(UUID)`; 1.8.9 NMS `b(mod)` / `c(mod)` / `a(UUID)`, op `0` = `ADD_NUMBER`, javap-verified).
   A UNIQUE identity per drain keeps overlapping drains (two Lovestruck hits) independently reverting — it is
   deliberately a DIFFERENT identity from the worn `applyWornMaxHealth` modifier, so the two never collide and
   the worn reconciler never fights the drain.

2. **Grim drops its `POTION_LOCK`.** With the drain now source-complete, `MAX_HEALTH_DRAIN fraction: 1.0`
   removes the `HEALTH_BOOST` overhealth too; keeping the `POTION_LOCK` beside it would double-count (both
   would try to remove the same pool, driving effective below the baseline). Grim's ability is now a single
   `MAX_HEALTH_DRAIN`. Cupid's Lovestruck config is unchanged — it simply removes modifier overhealth now.

3. **`potionLock` registers a denial and a modern event cancels re-application.** The Sink strips now, records
   the (entity, type-name) in the static `LockedPotions` registry for the window, and a modern-overlay
   `PotionLockListener` (ADR-0044 seam via `EraServices.potionLockGuard`) CANCELS the
   `EntityPotionEffectEvent` (`ADDED` / `CHANGED`) of a locked type — the passive driver's re-application never
   lands, so there is no flash. The per-tick re-strip is kept as a **backstop** and is the **sole enforcer on
   1.8.9** (no such event; the legacy binding is the inert `NoopListener`). Keyed by the version-stable
   `PotionEffectType.getName()` so the writer (an int id → type) and the reader (the event's type) can never
   disagree across the range.

## Consequences

- Grim and Lovestruck now remove overhealth from `HEALTH_BOOST` potions, the worn `HEALTH` +hearts modifier,
  and base shifts alike — the reported "hearts stay" bug is closed. The drain never drives a target below one
  half-heart of max, and its give-back is exact and idempotent (F07 quit-safe).
- A locked potion is genuinely denied for its window on modern; the "glitches back in" flicker is gone. All
  `POTION_LOCK` users benefit (druid, fantasy, clarity), not just Grim.
- One new always-on modern listener (`PotionLockListener`) folded through the controls module — the golden
  listener sequence (`RegistryWiringTest`) drifts by one name (`PotionLockGuard`). One new engine store
  (`LockedPotions`, the `DamageMarks` static-registry shape) and two new Sink era leaves.
- **Crash exposure parity.** The drain modifier has a random per-drain identity (needed for overlap-safety), so
  unlike the fixed-identity worn modifier it is not reconcilable on rejoin — a HARD crash (not a clean quit)
  mid-window would leak it. This is the same exposure the old base-reduction had (a hard crash leaked the base
  reduction too); `TimedRevertListener` covers every clean quit. The modifier's name is a recognizable
  `starenchants:` prefix should a future stray-modifier sweep want it.

## Alternatives considered

- **Keep reducing the base, but read effective overhealth.** Reducing the base by `effective − baseline` does
  lower effective to the baseline — but it floors at 1, so a large `HEALTH_BOOST` (e.g. Godly Overload level 8,
  +16 hearts on a 20 base) cannot be fully removed, and it fights the worn base restore. The negative modifier
  has neither limit.
- **Keep the `POTION_LOCK` re-strip alone, at a tighter cadence.** No cadence wins the race deterministically —
  the driver and the strip interleave; only denying the application at the event removes the flash. The
  re-strip is retained only as the 1.8.9 backstop.
- **Suppress the overhealth-granting abilities (the `DISABLE_*` / `SuppressionStore` path).** Ability-scoped and
  not source-agnostic — it cannot catch a `HEALTH_BOOST` from an arbitrary source or a raw attribute modifier.
  The effective-overhealth clamp is source-blind by construction.
