# ADR 0069: Combo-DoT park & sync — SE never breaks a Mental combo with its own damage-over-time

- **Status:** Accepted
- **Date:** 2026-07-17
- **Deciders:** project owner + agent
- **Extends:** ADR-0026 (reflective Mental bridge), ADR-0027 (bundled soft integrations),
  ADR-0054/0055 (same-hit rider economy / the damage fold), ADR-0051 (the dead stay dead),
  ADR-0042 (the `Regions` cross-region read guard), ADR-0065 (the Ice Aspect freeze DoT)

## Context

The [Mental](0026-mental-knockback-coordination.md) combat engine runs a per-victim combo tracker
fed by shipped melee knocks: a chain becomes *active* at `min-hits` (default 2), and stays alive only
while a knock ships every ≤ `max-gap-ticks` (default 20). Anything that re-arms the victim's vanilla
hit-invulnerability window between the real hits starves that feed and ends the combo.

Every StarEnchants engine-issued damage application funnels through `DispatchSinkBase.hurt`
(`target.damage(amount, attacker)` when an attacker is in scope, else bare — ADR-0054). A **deferred
or periodic** hurt — a bleed WAIT tier, an Ice Aspect freeze tick (ADR-0065), a delayed strike, an
enchant lightning payload — lands mid-combo and breaks it two ways, both verified by reading Mental's
source:

- **Attributed tick** (`victim.damage(amount, attacker)`, cause `ENTITY_ATTACK`): Mental mints a
  `Vanilla("ENTITY_ATTACK")` transaction, classifies it as melee, computes a full era knockback, and
  ships it. So a single SE DoT tick is, to Mental, a real melee hit — it launches the comboed victim
  between the real hits, and if the DoT's attacker ≠ the combo holder it **abandons the holder's
  chain and restarts it on the DoT owner** (the switched-attacker takeover). The tick also re-arms the
  victim's vanilla window, so the holder's next click lands era-silently (difference-only, **no knock,
  no velocity event**) → the tracker starves → `ComboEnd(GROUNDED|EXPIRED)`.
- **Bare tick** (`victim.damage(amount)`): invisible to Mental's delivery, but it still re-arms the
  window. Mental names this exact failure — a "foreign-window-reject" from *"an SE proc's bare
  `damage()` in the freeze→apply sliver"* — and its boundary-adoption clamp explicitly refuses to
  rescue a foreign window. Same starvation cascade.

Mental performs **no** combo-aware shaping of any damage cause: it re-shapes armor reduction values
for non-entity DoTs (poison/wither/fire) but never cancels, delays, or window-shapes them. On a
genuine 1.8 era server, fire and poison break combos exactly the same way, and Mental accepts that as
era truth. So the only combo-breaker we can and should remove is **our own** issued damage.

## Decision

**SE never issues its own entity-damage application on a player while Mental holds an active combo on
them.** Every deferred/periodic engine hurt targeting a comboed player is **parked** — banked into a
per-(victim, attacker) ledger instead of applied — and released by exactly one of four triggers. All
triggers are hit- or event-driven; there are no timers or periodic sweeps.

1. **Flush-on-hit (primary).** The victim's next real hit drains the ledger buckets belonging to
   **that hit's attacker** (plus the unattributed bucket) and joins them to the hit's damage moment via
   `DamageFold.addEffectiveDamage` — the ADR-0054/0055 rider machinery: **one hurt, one immunity
   window, one knockback**. This is the owner's ask verbatim — the DoT's owed damage syncs up with the
   combo's own hits, so no stray knockback and no stolen i-frame window are ever created. Only *this
   attacker's* buckets join, so a third party's bleed is never credited to this attacker.

2. **Combo-end release.** `ComboEndEvent` (any reason) arms a paced release on the victim's entity
   scheduler: one bucket per hurt, each applied only when the victim's vanilla window is clear
   (`noDamageTicks <= maximumNoDamageTicks/2` — the same boundary Mental reads), oldest debt first,
   attributed to the bucket's original attacker via a standard `EngineDamage`-framed hurt. Attribution,
   kill credit, and Phoenix-style saves behave exactly as an un-parked DoT tick.

3. **Post-combo leftover kick.** On any processed hit, after the drain-join, if the ledger still holds
   buckets (third-party debt; a release loop a re-formed combo halted; stale TTL-expired state) **and
   no combo is active**, the paced release is armed for the remainder. There is **NO paced release
   while a combo is active** — an attributed release hurt is a melee to Mental, and a third-party
   bucket's release would ship a switched-attacker knock that ENDS the very combo this mechanism
   protects. Third-party buckets therefore WAIT (parked, delayed-not-lost) for combo end or the first
   post-combo hit. Boundedness comes from Mental's own combo structure — an active combo needs a
   shipped knock every ≤ `max-gap-ticks` or it ends, and the `COMBO_TTL_TICKS` belt hard-bounds a stuck
   state — not from a timer.

4. **Terminal paths.** Victim death → ledger cleared (the dead stay dead, ADR-0051; a respawn never
   inherits banked damage). Victim quit → cleared by the structural quit sweep (`PlayerScoped`) —
   banked damage is deliberately dropped, like the plugin-disable path. Plugin disable → the ledger
   evaporates with the plugin instance (mirrors `FrozenTargets` teardown: no damage applied during
   disable). Mental absent/disabled → the bridge is `ABSENT`/`DISABLED`, combo state is never written,
   `tryPark` always declines — byte-identical to today.

**What is parked: the damage number only, per (victim, attacker).** The DoT ability's other intents
(particles, sounds, slow, freeze visuals, teardown scheduling) run untouched at their authored cadence
— the freeze chain still claims its tick-budget slots so window accounting and teardown are
byte-identical. Buckets **sum** per attacker; the bucket count per victim is naturally bounded by the
distinct concurrent attackers.

Combo state comes from a **reflective bridge** (`MentalComboBridge`) shaped exactly like the shipped
`MentalKnockbackBridge` (`Class.forName` + `getMethod` verified at registration, `EventExecutor`
registration, `Path {BOUND, ABSENT, DISABLED}`, no compile-time Mental dependency). A 600-tick combo-
state TTL is the stuck-state belt for a Mental hard-removed without firing its balanced end events.

### Not parked (scope exclusions, each era-justified)

- **Zero-WAIT same-hit riders** — they already fold (ADR-0054) and never open a second window.
- **Vanilla-mechanism DoTs our effects cause indirectly** (IGNITE → fire ticks, POISON/WITHER potion
  periodic damage): **excluded.** Mental does not suppress or delay these causes mid-combo, so they
  break combos on a genuine era server too; intercepting vanilla's own damage events would *diverge*
  from the era combat Mental implements and would require cancel+re-mint of non-SE damage (fighting
  other plugins). A future config-gated interceptor remains possible without touching this design.
- **Summoned-mob melee** (SPAWN/SPAWN_SWARM mobs attacking the victim): real vanilla mob melee, the
  same era-authentic exclusion.

### Known limitation — combo FORMATION is not protected

Parking engages at `ComboStartEvent`, which Mental fires only when the chain reaches `min-hits`; the
developing chain before that is silent (a developing chain that dies "drops silently and events stay
balanced"), and Mental exposes **no** developing-chain signal. So a DoT tick landing in the gap
between qualifying hit 1 and hit 2 can still re-arm the victim's window, era-silently swallow hit 2,
and prevent the combo from ever forming. This increment ships **continuation-only** protection: a
combo that has STARTED is never broken by an SE DoT. Closing the formation gap requires either a
Mental-side developing-chain hook (Mental is read-only to us) or an SE-side heuristic formation window
(park DoTs for ~`max-gap-ticks` after any PvP melee on the victim while the bridge is BOUND — which
would defer DoT cadence in ordinary non-combo fighting too). That is an owner decision; the limitation
is documented here and in the CHANGELOG, never silently shipped. The `Appendix F` migration to
Mental's generation-3 API closes this gap via the `DEVELOPING` combo state.

### Constants

- `WINDOW_WAIT_CAP_TICKS = 40` — honest-effort bound on waiting for a clear window, 4× the vanilla
  half-window, so an external re-arm can't stall a release forever.
- `LOOP_MAX_TICKS = 200` — absolute release-loop lifetime; on expiry, drain all remaining eligible
  buckets back-to-back and stop (bounded execution beats a perfect window).
- `COMBO_TTL_TICKS = 600` — the stuck-state belt for a Mental hard-removed without its balanced end
  events; past it `comboActive` reads false so `tryPark` declines (the victim is never damage-immune).

### Attribution, threading, and the one foreign read

Each drained bucket is attributed to its original attacker, so kill credit follows the debt. The paced
release performs the **only** foreign-entity read in the design — `att.isValid()` on a handle whose
owner may be in another region — and it is committed unconditionally to the ADR-0042 guarded shape:
`Regions.read("combo-dot-release attacker", att::isValid, Boolean.FALSE)`. On Folia an off-region
fault silently yields `false` → the hurt degrades to bare (unattributed) — the same best-effort
nullability Mental itself accepts for its event attacker. The ledger is a `ConcurrentHashMap` with
per-entry `synchronized` mutation because region ownership migrates between ticks (same logical owner,
different OS thread), which thread-confinement alone cannot make safe. The single-release-per-victim
guard lives **in the ledger** (`beginRelease`/`endRelease`), so the one structural cleanup that always
runs — `clear(uuid)` from the quit sweep and the death listener — also drops it; on Folia a retired
entity task never runs its body again, so no cleanup owned by the release task could be trusted.

### Disable / death / quit semantics

Banked damage is delayed-not-lost only while the victim stays online. Death drops it (ADR-0051). Quit,
plugin-disable, and a stale-state-with-no-further-hits victim who then quits all drop it — the accepted
terminal drop, mirroring `FrozenTargets` teardown. A stale (TTL) combo whose victim is never hit again
holds its buckets until the next hit / death / quit; a DoT tick arriving after TTL expiry applies
normally, so the victim never becomes damage-immune.

## Consequences

- A started combo is never broken by an SE damage-over-time tick, with zero stray knockback and zero
  stolen invulnerability windows — the DoT's owed damage lands on the combo's own hits or is paced out
  the moment the combo ends.
- `SinkEnv` gains one per-boot component (`DotParkLedger`), threaded like the other shared ledgers;
  `DispatchSinkBase.hurt` splits into `hurtOrPark` (the four deferred/periodic sites) plus the shared
  `EngineDamage.hurt` application used by both the flush and the release.
- `CombatModule` gains a listener (`ComboDotSyncListener`) and a `.store` (the ledger joins the quit
  sweep); `ControlsModule` gains the `MentalComboBridge` install alongside the knockback bridge. The
  `integrations.named.mental-combo-sync` sub-toggle (default ON) turns parking off while keeping the
  knockback coordination; `mental: false` disables both.
- `/se damagedebug`'s "eff" readout now includes combo-banked DoT on a flushed hit (the fold's
  effective bucket) — no DamageDebug change, but testers should know.
- The per-hit cost is one `drainFor` CHM lookup plus one `comboActive`/`hasParked` on an already-cold
  feature-layer path; the engine inner gate walk is untouched, so the ArchUnit/JMH gates are unaffected.
- **Not covered:** the formation window (above), and a comboed victim's own REFLECT/RETALIATION proc
  (Mental reads it as the victim landing their own melee and ends the combo held on them — this
  mechanism parks only when the *attacker being damaged* is themselves combo-held).

## Alternatives considered

- **Health-space flush** (subtract the debt from the victim's health directly) — kills without a death
  event and produces half-dead ghosts; violates ADR-0051's kill-decision ordering. Rejected.
- **Post-hit re-hurt** (apply the debt as a fresh hurt right after the combo hit) — vanilla's own
  difference rule swallows it (the combo hit re-armed the window), so it lands era-silently or not at
  all; it also opens the second window the whole design exists to avoid. Rejected.
- **Mid-combo over-budget release** (a `BUDGET_TICKS` cap that pays out aged debt during the combo) —
  **rejected in adversarial review** because an attributed release hurt is a melee to Mental: a
  third-party bucket's knock switches the tracker and ENDS the active combo, and even a bare release
  re-arms the window — the release would *cause* the failure it exists to prevent. Boundedness comes
  instead from Mental's own combo lifetime + the TTL belt + the post-combo leftover kick. (The
  reviewer's narrower "release only the holder's own bucket mid-combo" is moot: the holder's bucket
  flushes on every holder hit — ≤ `max-gap-ticks` apart in a live combo — so it can never age to any
  cap.)
- **A periodic sweep / timer to force parked debt out** — over-claims a bound the hit pipeline already
  provides and adds Folia-fragile global timer state. All triggers stay hit- or event-driven.
