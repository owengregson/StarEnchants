# ADR 0071: The ten Weapon-Reforge engine surfaces

- **Status:** Accepted
- **Date:** 2026-07-18
- **Deciders:** project owner + agent
- **Companion to:** ADR-0070 (the Weapon Reforges family — socket, activation, item plumbing)
- **Extends:** ADR-0012/0050/0055 (the additive damage fold + same-hit rider economy),
  ADR-0043 (capture-at-dispatch), ADR-0044 (era seams), ADR-0060/0061 (service-owned marker kinds —
  the DIG_HOME/WATER_SPEED rule), ADR-0068 (bat-cloud swarm), ADR-0069 (the combo-DoT park ledger)
- **Relates to:** ADR-0042 (the `Regions` cross-region read guard), ADR-0051 (the dead stay dead)

## Context

ADR-0070 adds the reforge family (socket, activation, item plumbing). The ten reforges need ten new
engine surfaces. This ADR records each surface's shape and the cross-cutting rules they share.

## Shared rules

- Heads append to `BuiltinEffects` in the pinned order GRAVITY_WELL, GRAPPLE, BLINK, SWAP_POSITION,
  HIT_TEMPO, JAVELIN, BATTERY, DISARM_SHUFFLE, CONVERT_SUMMON, TRAP_BREAK.
- Two shapes: a REAL kind emits Sink intents; a SERVICE-OWNED MARKER (the DIG_HOME/WATER_SPEED
  rule) keeps its numbers in the ParamSpec and a `feature/reforge` service runs the machine from the
  compiled args at the reforge activation success point. Markers exist because per-tick machines
  need feature plumbing (fresh-sink visuals, boot-resolved cue layers, lang, `PlayerScoped`
  lifecycle) that the mutation boundary deliberately lacks — or, for GRAPPLE, because the runtime
  resolves ONE selector per effect and the mechanism needs two rays (a marker service may still
  emit a Sink intent for the mutation itself).
- Suppression: reforge abilities carry `SourceKind.REFORGE` stable keys that are members of no
  enchant group/type id set, so gate-5 suppression structurally cannot match them (the owner's
  works-through-Silence rule) — no suppression-system change.
- Cooldowns spend per attempt, never refund — including Castling's aborted channel (the countdown
  is the enemy's counterplay; a refund would price it at zero) and every whiffed raycast. This
  restates the cold-USE path's `spendCooldownOnChanceFail` economy.
- Attribution of machine damage uses the `ComboDotRelease` idiom: on the victim's thread,
  `Regions.ownedByCurrentRegion(attacker) && attacker.isValid()` gates the DamageSource, degrading
  to bare damage — never a cross-region dereference.

## GRAVITY_WELL (Singularity) — service-owned marker

Beam to the era-raycast block (range 12), a repeatingRegion star at block+rise for `duration`
ticks pulsing per-victim velocity toward the core (each write on the victim's scheduler), then a
single implosion: RAW health-space `damage` with linear falloff floored at `falloff-floor`.
Pulls and hurts the caster too (self-pull/self-damage, the authored downside). Wells are
location-anchored: an owner quit drops only attribution. Stop "gravity wells".

## GRAPPLE (Leviathan's Reach) — service-owned marker + one intent

Evidence rulings: `CraftRegionAccessor.createEntity` has no FishHook branch anywhere in
1.17.1→26.1.x (and 1.8 has no API), so a real hook entity is impossible — the hook is a dust-line
sim. And the runtime resolves ONE selector per effect (one reserved `who:` key, a single
`CompiledSelector`, first-slot-only executor binding), so the two rays cannot be authored as
selector slots — `GrappleService` (stateless, no store) resolves entity + block via the era raycast
at the activation success point; the CLOSER of entity-vs-block wins the ray (also compensates the
1.8 dot-scan's wall-blindness), and the service emits the one `grapple` sink intent through a
fresh `TriggerDispatch` sink. Entity mode: after a distance-priced flight delay, teleport-reel to
`reel-distance` in front of the caster (velocity zeroed, own facing kept) + brief Slowness, no
damage — a guaranteed reposition, because velocity physics whiff and this is a 20 s counter-pick.
Terrain mode: a capped computed velocity zip. Open air wastes the throw.

## BLINK — real kind + one intent

A 0.5-block-sampled walk along the YAW-ONLY look direction, cell-deduped, each cell tested with
the existing era `isSafeDestination(cell, null)` leaf (feet+head passable); land on the LAST open
cell before the FIRST blocked one, never scanning past a wall. A blocked cell first probes one
block up — a single rise is a step (walking would climb it), only a 2-high face is a wall. A
point-blank wall blinks zero and still spends (owner: walls stop it — a reposition, not an
escape).

**Amended post-1.10.0 (real-play evidence):** as shipped the ray was the full 3D look ("the
chorus fantasy"). A grounded player looks slightly down almost always, so the pitched ray entered
the floor cell within the first 0.5-block sample and every real-play blink zeroed with the
cooldown spent; the matrix stayed green because fake casters float at pitch 0. Pitch is now
stripped and the walk steps up single rises.

## SWAP_POSITION (Castling) — service-owned marker

Era-raycast crosshair acquisition (players AND mobs), then a `channel`-tick task on the CASTER's
scheduler: every `check-period` ticks re-validate liveness, world, range+slack and
`hasLineOfSight` (range-stable Bukkit API; victim reads `Regions`-guarded, faults abort). Audible
rising countdown cues + messages both sides each second. On completion the parties exchange
captured positions — victim hops on its thread, then the caster on its own — velocities zeroed,
each keeping their OWN facing (you inherit the spot, not the view). Aborts never refund
(shared-rules rationale). Stop "castling channels"; caster quit aborts.

## HIT_TEMPO — Quickening Fang

An armed holder window (`HitTempoStore`), consumed nowhere: while live, the combat dispatch taxes
every melee hit the holder lands through the fold's **self-malus channel** (`DamageFold.mulFinal`,
new in this ADR: a [0,1]-clamped multiplicative factor applied to the WHOLE committed hit — the
only shape that survives `combat.attack-scale` and the ADR-0055 rider bucket; effects cannot call
it, so ADR-0012's additive-buffs-only policy is intact), and each LANDED hit rewrites the victim's
i-frames at MONITOR: `noDamageTicks = max − W/2`, where `W` is the model's effective window —
`MENTAL` = `max/2` (the frozen StrikeSync `WindowJudge` gate, which coincides with NMS's own
full-hit boundary) or `VANILLA` = `max` (the 1.9+ swing-meter cadence, where a reconciled
`ADD_SCALAR` attack-speed modifier — resolver-chain, 1.21.3-rename-safe, TimedRevert-restored,
absent-attribute no-op on 1.8.9 — carries the halving). Third-party fairness is a LOWEST-priority
guard (`ReforgeTempoGuardListener`) over per-(victim, holder) stolen intervals: for `max/2` ticks
after each write, hits from attackers holding no live interval of their own are cancelled — they
experience exactly the i-frames the write erased, while concurrent Quickening holders each keep
their own cadence (per-holder keying; a single per-victim slot would cancel the other holder's
hits as third-party). The write is a state write, not a damage application: it mints no Mental
transaction and cannot break a combo (ADR-0069 cross-checked). Owner ruling R2: every melee hit
the holder lands during the 5 s window carries the 1/3 tax (the tradeoff reading — 2× cadence at
1/3 damage, not downside-free extra hits).

## JAVELIN — service-owned marker

A self-rescheduling one-tick chain re-keyed each step to the advancing tip's region (honest Folia
region-hopping): advance `speed` blocks/tick to `max-travel`; wall-check AND hit scan (at
`hit-radius`) are both `Regions.read`-guarded, because the advance can cross a region boundary
within a step — a guarded fault fails closed and the next re-keyed step re-covers the sliver.
Damage is priced AT THROW: WEAPON mode reads one swing via the `WeaponDamage` era seam (modern =
GENERIC_ATTACK_DAMAGE through the alias chain — the 1.21.3 rename; legacy = the 1.8 material
damage table with the empty-hand null guard). Impact on the victim's thread: attributed hurt,
knockback = flight direction × `knockback-base` × `knockback` (+ a small lift), then after
`lock-delay` a per-tick position+look pin on the victim's own scheduler for `lock` ticks (the
known-viable cross-version camera-lock; each re-assert rides the era teleport leaf — modern
`teleportAsync`, since Folia's sync teleport throws unconditionally; legacy sync), then — only
after the hold releases, the owner's "then" — Nausea via the `TriggerDispatch` potion passthrough.
Stop "javelin flights" releases flights AND holds.

## BATTERY — Supernova Core

`BatteryStore`: armed on USE; the next `hits` landed non-engine hits TAKEN each bank
`bank-percent` of `getFinalDamage()` (what the health pool actually lost); the holder's next
landed hit joins the whole bank to that hit via `DamageFold.addEffectiveDamage` — the ADR-0055
same-hit rider (one hurt, one immunity window, never attack-scaled) — and spends the core, **even
at zero banked** (owner-ruled). Consumption commits at MONITOR through `ReforgeStrikeRelay`
(keyed by event AND stores identity — the ADR-0069 parallel-triples lesson), so a Dodge/Inversion
cancel keeps the charge. Bank and discharge ride the SAME pvp/pve/friendly context gates: the
discharge consult inherits the dispatch's attacker-branch gate by placement, and the bank applies
the identical defense-side predicate — charge never banks in a context where it could never
discharge. No time expiry (the CD paces re-arms); cleared on death (a respawn never inherits
banked damage, ADR-0051) and on quit.

## DISARM_SHUFFLE — The Unhanding

`DisarmWindowStore`: a one-shot 4s window; the holder's next landed melee hit on a PLAYER carries
a −20% self-malus (`mulFinal(0.8)` — felt −20% at attack-scale 5.0 and 1.0 by construction; an
additive `addOutgoing(−0.2)` is scale-multiplied into −100%, the trap this channel exists for) and
at MONITOR swaps the victim's held stack with a uniformly-random OTHER hotbar slot (selection
unchanged — shuffled, not locked) followed by an explicit `WornStateStore.refresh` (no Bukkit
event fires on a same-slot content swap). Weapon-gated states interrupt structurally:
`rageLevelOf` reads the held resolution per hit, so the victim's next swing carries no rage until
they re-select. Mob victims pass through without consuming the window. Distinct from the existing
`DISARM` head (drop-to-ground).

## CONVERT_SUMMON — Summoner's Bell

Conversion is registry-driven — "enemy-summoned" means "tracked with a foreign owner", never a
heuristic: `GuardianCasts` (the universal ownership registry every owned spawn binds) rebinds to
the ringer (GUARDIAN_HURT follows), Tameables re-tame, targeting summons get
`setGuardTarget(formerOwner)` (the existing era leaf; suppressed for `noTarget` flags — a
converted sentry stays a sentry, its owner-agnostic fuse untouched — owner ruling R4 confirms this
attribution-only conversion: ownership/GUARDIAN_HURT/`/se why` rebind, the fuse and `noTarget` left
untouched), and bat clouds are **turned in place** (`SwarmClouds.turned`): the cloud permanently
publishes its own owner as the pillar target, so the untouched steer tasks converge on the former
owner — re-keying the cloud would be a cross-scheduler task migration for zero player-visible gain.
Turnable clouds are detected by MEMBERSHIP (`turnByBat` over the ringer's own nearby enumeration —
UUID matching only, no bat position reads: a per-bat cross-region read would fail-closed skip on
Folia and silently never turn an off-region swarm). Owner ruling R3: Rot-and-Decay's zombies bind
`owner: "activator"` (their SPAWN_ENTITY lines rebound from `owner: "none"`), so they carry the
activator's `GuardianCasts` identity at spawn exactly like Undead Ruse and the Bell converts them
with zero engine change — their "former owner" is the enchant's activator. Permanent = for the
summon's remaining life (TTLs unchanged).

## TRAP_BREAK — Turnkey (and the trap-structure grouping surface)

`TrapStructures` (a SinkEnv instance, the DotParkLedger pattern) groups CONFINING placements over
the coordinate-keyed `TempBlockLedger`: `cage()` (victims = both caged parties), entity-anchored
`tempBox` boxes, and at-entity `TEMP_BLOCK` POINTs (`dy >= 0`) register their placed tiles +
victims + an accumulated AABB; floors, trails, platforms and pillars never register — the
confinement decision is made at the call site, not inferred. Turnkey matches structures whose
victims contain the actor OR whose AABB contains them, then early-restores every tile through
`TempBlockLedger.reclaim` on each tile's owning region (the ledger's sanctioned early-restore:
all layers popped, true original back, pending reverts no-op on the entry-null guard — no
cancellation machinery). It is a block restore, not an ability negation — no suppression-system
participation, so it works under DOVAHKIIN/Silence by construction; the Fantasy trap's companion
Speed lock is a debuff, not confinement, and runs out its own clock.

## Suppression stance (all five combat-state kinds)

Reforge defs author no `group:`/`type:`; the reader lowers no `suppressKey`, so gate 5 passes
vacuously — reforge actives are structurally outside the suppression system (owner LAW: counters
work through Silence). Strike-side consumption reads stores at the hit site, outside the gate
walk entirely.

## Consequences

- The ten heads register in the pinned order; `regenDocs` regenerates the surface catalogue and the
  per-head spec docs from the ParamSpecs — never hand-edited.
- New stores (`HitTempoStore`, `BatteryStore`, `DisarmWindowStore`) ride the `EngineStores` sweep;
  the marker services (gravity wells, castling channels, javelin flights) declare their own stops;
  `TrapStructures` rides the `SinkEnv` and needs no stop.
- The `DamageFold.mulFinal` self-malus channel is new engine surface used ONLY by the strike-side
  reforge consults — effects cannot reach it, keeping ADR-0012's additive-only policy intact.
- No suppression-system change, no pipeline special-case: the counters work through Silence by
  construction.

## Amendments — the post-1.10.0-beta real-play audit

v1.10.0-beta shipped matrix-green and broke in real play: the suites stubbed both era rays,
bypassed the activation listener, and staged pitch-0 gravity-off fake casters on peaceful
servers — none of which resembles a real player. A four-surface adversarial audit produced these
ratified revisions (the BLINK amendment above is part of the same batch):

- **Era acquisition rays.** Modern `targetEntity` is a 0.3-inflated `rayTraceEntities` capped by a
  COLLIDER block pre-clip (`getTargetEntity`'s pickRadius-0 exact-hitbox ray whiffed most real
  attempts); modern `targetBlock` is COLLIDER-mode (OUTLINE collided with tall grass — Singularity
  self-nukes). Both eras scan living, non-armor-stand, non-spectator candidates only, and legacy
  `targetBlock` folds the 1.8 never-null air tail to null. The logic lives in
  `ModernTargets`/`LegacyTargets` (G1-b: bindings stay composition-only).
- **Every spent use is audible.** The never-refund economy stands, but a whiff must not be silent:
  GRAPPLE draws its line to the empty ray-end + a `reforge.grapple.whiff` fragment; CONVERT_SUMMON
  and TRAP_BREAK gained a `whiff-sound` param played low-pitched when the op finds no work.
- **GRAPPLE.** Mode pick measures both rays from the EYE (feet-distance flips zipped the caster
  into the enemy); the reel destination is standability-checked at emit on the caster's thread
  (step-up probe, caster-cell fallback); the line FLIES — one growing frame per flight tick,
  victim-tracking, first motes ~0.9 blocks clear of the first-person camera; a victim who changed
  worlds mid-flight is never yanked back.
- **GRAVITY_WELL.** No sighted block (the skyline aim — the dominant real-play outcome) anchors the
  core at eye + direction × range instead of wasting the throw; the victim scans are spherical
  (the box corners reached 1.7 × radius).
- **CONVERT_SUMMON.** The per-tick converted-target hold is RESTORED and permanent-by-window: anger
  auto-backing is a NeutralMob fact, and the shipped convertibles are Monsters whose goal
  revalidation drops unbacked targets (the v1.10.0 removal generalized golem-only suite evidence).
- **TRAP_BREAK.** A live freeze window is confinement (owner: "any active confinement on self") —
  Turnkey thaws it via `FrozenTargets.breakNow` on the actor's thread.
- **HIT_TEMPO.** The MENTAL window model (`W = max/2`) describes Mental's default profile only; the
  ct8c 1.8-feel bundle rewrites `max` to `min(attackDelay, 10)` with a full-window gate, where
  `max/2` degenerates to a no-op steal under a live 1/3 tax. An observed `max <= 10` now reads as
  that profile (`W = max`). And on 1.16.5–1.20.6 `CraftPlayer.setNoDamageTicks` also arms the
  respawn-invulnerability timer whose gate voids every subsequent hit — the steal made victims
  UNHITTABLE there; `platform.caps.SpawnInvulnerability.disarm` (self-verifying reflective
  companion write) zeroes it, a structural no-op on unaffected bands.
- **BATTERY.** The discharge consult requires a Player victim (the disarm gate's rule) — a stray
  mob swat must not dump the bank.
- **DISARM_SHUFFLE.** An empty-handed victim has nothing to unhand: the window stays armed and
  expires naturally (the shipped commit handed a fist-fighter a weapon).
- **JAVELIN / SWAP_POSITION.** Javelin flies yaw-only at eye height (the BLINK pitch class; the
  3 b/s felt-unit is untouched), scans victims before walls, context/friendly-gates its impact,
  thuds audibly on terrain, and the camera lock re-asserts only past 0.5 blocks of drift.
  Castling messages BOTH sides each second, honours the authored `cue-period`, zeroes velocity
  AFTER the hop, and authors the anvil cue once (the alias chain is bidirectional — twins doubled).
