# ADR 0067: Mole cue rework — code-side layered cues, the range state machine, and the per-pet no-home line

- **Status:** Accepted
- **Date:** 2026-07-17
- **Deciders:** project owner + agent
- **Extends:** ADR-0061 (Mole dig-home pet + the world-visible-home amendment)
- **Relates to:** ADR-0066 (`CueOnce`) — explicitly NOT involved here (see below); the rage-break
  layered-cue precedent (commit `9c0b0910`)

## Context

The owner respecced the Mole's whole soundscape into five discrete cue groups — digging a home,
teleporting home, stepping OUT of the recall range, stepping back IN, and a home expiring unused —
plus a Mole-specific message for a plain click with no home dug, each with exact tokens, volumes and
pitches. Two of the specced sounds are absent on older servers (`BLOCK_BAMBOO_WOOD_DOOR_*` and
`BLOCK_NETHER_WOOD_DOOR_*` before 1.19.3, `BLOCK_VAULT_DEACTIVATE` before 1.20.5), and three of the
five groups (range exit/enter, expiry) have no content trigger at all — the recall bypasses the gate
walk by design (ADR-0061).

Two hard facts shape the design. First, an unresolvable **content** sound token is a BLOCKING
`E_UNKNOWN_HANDLE` diagnostic: the tester `CatalogSuite` fails the 1.17.1/1.18.2 matrix legs on any
blocking diagnostic, so a content-authored bamboo/vault token would refuse the whole pack on those
versions. Code-side resolution has no such gate — an absent token resolves to `-1` and
`TriggerDispatch.sound` skips it, playing the surviving layers (the sanctioned rage-cue degrade).
Second, ADR-0066's `CueOnce` dedupe is scoped to per-event engine `SOUND` emissions; all five Mole
groups play through `TriggerDispatch.sound`, which builds a fresh sink per call — cold-path discrete
cues, not per-hit walks — so `CueOnce` never touches them, and after this change the Mole authors no
content `SOUND` at all.

## Decision

1. **All five cue groups are owner-specced layered tables, code-side in `PetHomeVisuals`.** Each
   layer is a `Cue(interned sound id, volume, pitch)` resolved once at construction via
   `resolvers.sound(token).orElse(-1)`; `play` iterates a table and lets `dispatch.sound` skip any
   `id < 0`. The Mole's authored `BLOCK_GRASS_BREAK` `SOUND` op is removed from `mole.yml`. One home
   for every Mole cue keeps them greppable and lets the dig group use its bamboo layer on new
   versions without an `A|B` fallback chain forcing a different sound on old ones. Timing is
   unchanged: the dig cue fires at `armHome`, the exact moment the old authored `SOUND` did.
2. **The 10-tick home pulse gains a per-window IN/OUT range machine**, single-sourced on the new
   `PetHomeStore.Home.inRange` — the recall's exact boundary inverted (same world **and** `d² ≤ r²`;
   cross-world is OUT, the boundary itself is IN). State is an `AtomicBoolean` carried on the pulse
   `Task` record (initial IN — dug at the home, no enter cue on creation), edge-triggered on each
   sample: a crossing plays the exit/enter layers once, at the player. The tracer **line** is
   range-gated (the spec's "[particle effect line stops playing]"); the home **ring** keeps marking
   the block whenever same-world (the counterplay, ADR-0061 amendment). Cross-world still draws
   nothing but the machine still flags OUT.
3. **The expiry cue rides the existing generation-guarded expiry task** in `armHome`, which already
   fires exactly once for a window neither recalled, re-dug, nor torn down by death/quit. To make it
   (and the pre-existing ENDED message) reliable, the pulse switches from the evicting
   `PetHomeStore.get` to a new non-evicting `peek`, so the scheduled task is the sole authority on
   how an untouched window ends — closing a latent race where a pulse landing on the expiry tick
   evicted the entry first, making `clearIfGeneration` no-op and swallowing the message.
   `expiredCues` guards `player.isValid()` so a retired-but-run task on a gone owner is silent.
4. **A per-pet `message-on-no-home` schema field** (optional root key on `content/pets/<key>.yml`),
   not a lang key — this is digger-pet likeness copy ("sneak to dig"), and the pets family keeps
   likeness messages in config templates by rule. Blank/absent falls back to the universal fail
   line. `PetService.use`'s condition-fail tail is digger-aware — `dig != null` → `messenger.noHome`,
   every other pet unchanged. Gate-6 cooldown still precedes the gate-7 condition, so an on-cooldown
   click keeps the informative cooldown line; the recall's `feedback.out-of-range` path is untouched.

## Consequences

- Worst case (1.17.1/1.18.2) the dig/teleport/exit/enter groups keep 2 of 3 layers and expiry keeps
  3 of 5; every other version plays the full spec. No `Aliases.java` rows — all ten tokens are
  constant across 1.17.1 → 26.1.x (the 1.21.3 Sound enum→interface flip changes API shape, not
  names, and the resolvers already absorb it).
- **1.8.9 regression, accepted:** none of the ten tokens has a 1.8 equivalent, so the Mole is now
  fully silent on 1.8.9 (it previously played `BLOCK_GRASS_BREAK` via the `DIG_GRASS` alias on the
  dig and recall). Silent-where-absent is established policy; the shared sources still compile under
  the legacy overlay.
- No wiring or golden drift: no constructor signatures change, the cue tables are instance fields and
  the range state is an `AtomicBoolean` on the task record (no new mutable statics), and no
  kind/param/trigger/config/lang surface changes — so no `regenDocs` and the fingerprint-drift test
  passes unchanged.

## Alternatives considered

- **Author the tokens in content with an `A|B` fallback chain** (the druid/yeti precedent) — would
  force a DIFFERENT sound on the floor versions, violating the owner's exact spec, and still leaves
  the three trigger-less groups needing code.
- **A `feedback.no-home` lang key** — rejected: `feedback.*` keys are plugin-universal one-liners,
  but this line is pet likeness that only a digger renders; the pets family keeps such copy out of
  lang deliberately.
- **Range-gate the whole home visual (ring + line)** — the ring is the world-visible counterplay
  marker; only the line is the owner's guide home, and the spec gates the line alone.
