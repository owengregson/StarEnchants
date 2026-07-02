# ADR 0044: Era erasure — seams and bindings replace same-FQN overlay twins

- **Status:** Accepted
- **Date:** 2026-07-02
- **Deciders:** project owner + agent
- **Extends:** ADR-0036 (legacy overlay + MRJAR — the mechanism this refines)
- **Supersedes:** the "same-FQN whole-file swap" seam style of ADR-0036 for everything
  except the two bindings twins; the hand-maintained `ALLOW_ERA_EXCLUSIVE`(`_PREFIX`)
  allowlists in `scripts/build-mega-jar.sh`; the three separate legacy gear polls
  (the ADR-0036 deferred dedup)

## Context

ADR-0036 shipped the srcDir overlay + Multi-Release jar and then hoisted ~1,000 era-neutral
lines into `DispatchSinkBase`/`EquipListenerBase`. What remained were 26 same-FQN twin pairs
(~3,400 lines) that hand-mirror each other: parity between a pair is enforced by no compiler
(nothing shared names both), drift is caught only by the merge-time class-set gate, and the
gate itself forced an empty legacy `NametagAnvil$PreviewListener` stub to exist purely to
balance the class sets. Meanwhile the tree already carried two proven NON-twinned era classes
(`LegacyNbt`, `RuntimeHandles`) — allowlisted by hand.

## Decision

**The unit of era variation is the seam, not the file.** Era variance is erased at the
composition point, the way effect sources are erased into `Ability`:

- `src/` holds only intersection-safe code — both era compiles include it, so 1.8-safety is
  proven by construction (the `-Pse.target=legacy` compile IS the gate). Shared code may
  reference seam **interfaces**, never era classes.
- `overlay/<era>/` holds era-exclusive classes under era-unique FQNs (`ModernHands` /
  `LegacyHands`, `ModernDispatchSink` / `LegacyDispatchSink`, `ModernParticleFx` /
  `LegacyParticleFx`, …) implementing seam interfaces that live in `src/` — parity is a
  per-era `javac` fact, not a convention.
- Exactly two same-FQN twins remain, both composition-only: `bootstrap.compat.EraBindings`
  (implements the shared `EraServices` — the whole era wiring manifest, absorbing the former
  Wiring/Bridges/Targets/Commands seams) and `platform.resolve.HandleLookups` (the one era
  choice constructed below the composition root). "Composition-only" is enforced by a
  build-time gate, not review: construction and one-line delegation, no control flow, no
  mutable fields. Real per-era logic is extracted to era-exclusive classes (`LegacyTargets`,
  `LegacyEnchantResolver`) so the bindings stay branch-free.
- Asymmetric "one era works, the other stubs" twins become a shared default
  (`VanillaStats.NONE`, `AnvilRename.UNSUPPORTED`) plus one era-exclusive override.
- The three legacy per-tick gear polls unify into one `LegacyGearPoll` with an explicit
  per-slot subscriber order (ITEM_DAMAGE before any restore → heroic save restore → equip
  refresh), preserving the shipped steady state exactly.
- The mega-jar soundness gate is **derived**: era-exclusivity from the overlay tree, module
  exclusion (`:integrate`) from the module set, cross-era unreachability by a constant-pool
  walk, bindings shadowing asserted in the merged jar, and the resource trees diffed — no
  allowlist (`scripts/tools/MegaJarGate.java`). A fast companion, `EraTreeGateTest`, runs in
  every `./gradlew build` so a broken seam fails locally, not on the legacy/matrix lanes.

A declarative, name-resolved event routing table was considered for the listener twins and
rejected: no twin pair shares handler logic across era events (the legacy side is a poll or
an NMS hook), and string-resolved event classes would surrender the dual-compile gate's
compile-time proof — the same reflection-bridge alternative ADR-0036 rejected. Name-resolved
`registerEvent` remains what it already was: a within-era range probe for above-floor events
(the modern knockback event).

Knockback is the one place the era selection is a registration, not a returned object: the
modern 1.20.6+ event is hooked reflectively (not a plain `Listener`), so `EraServices`
exposes `registerKnockback(...) -> Path` rather than a `Listener`-returning factory — the
modern bindings run the shared range probe, the legacy bindings register the
`NmsKnockbackApplier` directly (the Paper events are absent on 1.8, so the shared probe
would resolve NONE there).

## Consequences

- Mirrored surface drops from ~3,400 to ~200 lines (2 bindings twins); parity drift between
  eras is now a compile error inside each era lane.
- The MRJAR gate no longer needs maintenance when era code is added: a new era class is a
  file under one overlay dir; a new legacy-excluded module derives from the module set.
- ~1,000 formerly-twinned lines became shared and are therefore 1.8-compile-checked (the
  hoisted sink algorithms, `Sounds`, `MenuClicks`, `Causes`, the equip lifecycle).
- Cost: seam consumers are constructor-injected (the feature shells gained ctor params); the
  bindings twin is a wide but logic-free manifest; era impl class count rose (two files where
  one twin pair stood, plus an interface).

## Alternatives considered

- **Status quo (twins + hand allowlist)** — rejected: unchecked parity, stub hacks, and an
  asserted-not-proven gate.
- **Declarative event routing** — rejected as above.
- **Static facades over boot-installed era instances** — rejected: reintroduces the mutable
  static side channels the codebase has been removing, and hides construction from the
  composition root.
- **A bindings twin per module (five)** — rejected: only `bootstrap` and `platform` *need*
  one; the other three would exist only to mirror construction that already flows through the
  composition root.
