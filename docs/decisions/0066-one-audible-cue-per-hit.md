# ADR 0066: One audible cue per sound per hit

- **Status:** Accepted
- **Date:** 2026-07-16
- **Deciders:** project owner + agent
- **Relates to:** ADR-0049 (the ECHO_STRIKE kind), ADR-0050 (the R3 combat-noise/cue policy), the §3.7
  once-per-hit re-hit guard (`ReHitGuard`)

## Context

The owner kept hearing "the enchant activate sound" double-play on hits after the §3.7 re-hit guard shipped
(and `ReHitOnceSuite` proves that lane suppressed). Tracing every path from a proc to an audible
`playSound` showed the doubles never needed a second damage event — ONE event walk can legitimately emit the
same sound twice:

1. **Sibling enchants share a likeness cue.** The v1.6.0 activation-cue pass gave families identical
   sounds: Lifesteal (WEAPON, 20%) and Chain Lifesteal (ARMOR ×4 pieces, ≈48%+/swing across the four
   independent rolls) both author `ENTITY_WITCH_DRINK` vol 1.0 pitch 1.0; Demonic Lifesteal is the same
   sound at pitch 0.8; Deathbringer/Planetary share `ENTITY_WITHER_SHOOT`; Rot And Decay/Undead Ruse share
   `ENTITY_ZOMBIE_INFECT`. On a god kit the co-proc lands every few seconds of sustained combat — the same
   cue twice at the victim, same tick. The re-hit guard can never see this: it is one event.
2. **ECHO_STRIKE re-fires cooldown-0 cues.** The Double Strike echo re-walk (by design — "your enchants can
   strike with it too") re-activates any cooldown-0 ability: Execute (cooldown-0, condition-only, fires on
   every hit vs a ≤25%-health victim) plays its `ENTITY_PLAYER_ATTACK_CRIT` in BOTH passes of the one swing.
   Damage riding twice is the echo's intent; the duplicated cue is noise.
3. **Worn multiplicity** (an ARMOR enchant on several pieces) is cooldown-deduped today because the ENCHANT
   cooldown scope is the base key — but any future cooldown-0 armor enchant with a SOUND would N-plicate its
   cue per hit.
4. **Thundering Blow authored the very sound its own bolt plays.** `LIGHTNING damage>0` summons a REAL
   `strikeLightning`, which plays vanilla thunder itself; the authored `ENTITY_LIGHTNING_BOLT_THUNDER` line
   on top double-played the identical cue on every proc.

## Decision

1. **One audible cue per sound per hit.** The `SOUND` kind dedupes at the emission seam: a sound id plays at
   most once into one per-event sink (`CueOnce`, consulted by `SoundEffect.run`). The bracket is the sink's
   identity — every dispatcher builds one sink per event and runs its whole walk (attack + echo + defense)
   synchronously on the firing thread, so the guard is thread-confined exactly like `EngineDamage`. A
   different sound on the same hit still plays; the same sound on the next hit (a fresh sink) still plays.
   Volume/pitch variants of one sound id count as the same cue (one witch-drink per hit, whichever sibling
   rolled it).
2. **An effect kind that intrinsically sounds is its own cue.** Content must not author a `SOUND` line
   duplicating a sound the effect already produces: Thundering Blow drops its authored thunder (the real
   strike is the cue). Vanilla-produced sounds are outside the engine dedupe, so this half is a content rule.

## Consequences

- The same-cue co-proc, the echo re-cue, and worn-multi cue N-plication all collapse to one audible cue per
  hit; damage/potion/etc. effects are untouched (the echo still folds twice — intent preserved).
- An intentional same-sound double-tap inside one hit (e.g. the same sound at two WAIT tiers) would be
  collapsed too; no shipped content authors one (verified: no level authors two SOUND lines at all).
- Pinned by `SoundCueOnceTest` (same-sink dedupe incl. pitch variants, different-sound pass-through,
  fresh-sink reopen). The `LocationEffectTest` SOUND row still covers the emission shape.
