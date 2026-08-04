# Proposed primitives — clustered from the decomposition matrix

Every `gaps:` declaration across the 10 matrix docs (~98 raw names), clustered into
the minimal general set under the spec §4 bar. Each entry lists the raw names it
absorbs and its consumers; a name in *absorbs* is retired — matrix docs keep their
local names, and this file is the canonical mapping. Wave rule: **wave 1** lands
before the enchant content batches for docs 01–06; **wave 2** lands before the
mastery+soul batch (doc 07) and the set/mask/pet batches — doc 07 is the last
enchant category in the PR sequence, so no batch ever waits on a primitive from a
later wave.

Dispositions: **EXTEND** grows an existing kind's params (no new kind);
**VAR/FACT**, **TRIGGER**, **EFFECT**, **SELECTOR** are new surface;
**COMPILER** extends the authoring grammar; **INTEGRATION** is soft-depend-gated;
**PROVISIONAL** ships only if its batch confirms the need.

## Compiler / grammar (wave 1) — the biggest single lever

### EXPR_PARAMS (COMPILER)

Numeric effect params, chance envelopes, and damage-fold contributions accept
expressions over the condition-var vocabulary, extended with `min/max/clamp/floor`
and uniform ranges `rand(lo, hi)`, evaluated at activation.
**Absorbs:** `EXPR_FUNCS` (04), `VAR_SCALED_PARAM` (01, 07), `VAR_SCALED_DAMAGE`
(03), `VAR_SCALED_CHANCE` (05), `EVENT_DAMAGE_FRACTION` (01 — `%damage%` is
already a fact; a fraction-of-event-damage amount is just `0.1 * %damage%` with a
cap), `RANDOM_PARAM` (03), `RANDOM_AMOUNT` (07), `RANDOM_RANGE_PARAM` (05).
**Consumers (13+):** Enrage cap, Bleed stack curve, Blood Lust heal, Creeper
Armor heal, Ender Walker heal, Deep Wounds/Rage folds, Feign Death chance,
Poison/Inversion rows, Wasteful Soul drains, missing-health mods, Pummel impulse.
**Bar:** one grammar extension replaces eight independently-minted gaps — the
strongest possible multi-consumer justification. NumExpr already exists; this
widens where it may appear and what it may reference.

### MULTI_ABILITY_ENCHANT (COMPILER)

An enchant definition carries several ability blocks, each with its own
trigger/condition/chance/cooldown (crystals already compile multi-ability;
enchants don't).
**Consumers (10+):** Phoenix, Rocket Escape, Protection, Spirit Link (02); most
bow entries (05, abilities A–D); Blessed dual abilities (06); Feign Death (07).
**Bar:** structural authoring need; without it every multi-hook enchant would be
forced back into bespoke listeners — the exact attempt-1 failure.

## Entity-scoped state (wave 1)

### TARGET_VAR (EXTEND: SET_VAR + condition facts)

`SET_VAR` gains a target scope (store on any entity, players and mobs), a bounded
counter mode (`op=increment`, `step`, `cap`, clear hooks), and conditions gain
cross-entity reads `%victim.var.<name>%`.
**Absorbs:** `TARGET_VAR_FACT` (03), `VICTIM_VAR_FACT` (04), `TARGET_SCOPED_VAR`
(06), `STACK_COUNTER` (04), `COUNTER_VAR` (03).
**Consumers (12+):** Rage/Execute exclusion, Bleed stacks + Devour/Deep
Wounds/Corrupt/Hex readers, Soul Trap re-proc gates, Inquisitive kill read-back,
Mark of the Beast payout marks, mask/pet shared windows.
**Bar:** three docs independently minted the same capability under three names —
the canonical corpus-wide dedup this file exists for.

## New condition facts (wave 1, one vars batch)

| Fact | Absorbs | Consumers | Note |
| --- | --- | --- | --- |
| `%posthit.health%` — actor health minus pending post-mitigation damage (DEFENSE scope) | `POST_HIT_HEALTH_VAR` (01) | Death God, Ender Shift, Lifebloom, Lucky, Phoenix-class saves (02) | replaces the jar's broken lethality predictor (ledgered) |
| `%actor.potion.<effect>%` / `%victim.potion.<effect>%` (BOOL/amplifier) | `POTION_STATE_FACT` (03) | Featherweight, Ice Aspect, Wither-family gates | |
| `%victim.fromspawner%` | `SPAWN_ORIGIN_FACT` (03) | Shackle, grinder-gated enchants | |
| `%heldticks%` — ticks since held-slot change | `HELD_SWAP_GATE` (04) | Boss Slayer, Hero Killer, Soul Trap, Sabotage, Divine Immolation | anti-hot-swap |
| `%victim.relation%` (ALLY/MEMBER/ENEMY/NEUTRAL, duel-aware) | `RELATION_VAR` (06), `TARGET_RELATION_FACT` (05) | ally-arrow cancels, relation-branched effects | selector filters can't branch conditions |
| `%nearbyallies%` (radius-scoped) | `ALLY_COUNT_FACT` (02) | Leadership, soul-charged auras | `%nearbyenemies%` exists; symmetric |
| `%impactheight%` — projectile Y minus victim feet Y | `IMPACT_HEIGHT_VAR` (06), `PROJECTILE_HIT_HEIGHT` (05) | headshot conditions (Lethal Sniper, Sniper) | two docs, one geometry fact |
| `%projectilekind%` (arrow/fireball/thrown/other) | `PROJECTILE_KIND_VAR` (10) | Supreme drawback; bow-family cause filters | confirmed 2+ consumers |
| `%actor.souls%` / `%victim.souls%` + soul-mode BOOL | `SOUL_COUNT_VAR` (07), `SOUL_STATE_FACTS` (04) | Hero Killer, Boss Slayer, soul family | facts only; the gate is below |

`POSITION_VARS` (`%actor.y%`, 11) moves to **wave 2** — its only consumer is the
spectral mask. **SHIPPED in wave 2d**, alongside the actor-side
`%actor.heroicpieces%` (the twin of wave 2c's victim fact) and
`%status.teleblock%` (`STATUS_CLEAR`'s paired guard).

## New triggers (wave 1)

### HURT (TRIGGER)

Targetless DEFENSE-direction trigger on every damage-taken event, any cause,
`%damagecause%` bound, null-attacker safe.
**Absorbs:** `HURT_TRIGGER` (03), `ANY_DAMAGE_TRIGGER` (06).
**Consumers:** Inversion, Guided Rocket Escape lethal check, cross-doc
death-saves, Nutrition-class sustain.

### PROXIMITY_EVENT (TRIGGER)

Fires on wearers when a specified event (player-death | tagged-effect-application)
happens to ANOTHER entity within range, with a relation filter.
**Consumers:** Avenging Angel, Blood Lust ally leech (01).
**Bar:** no existing trigger observes another entity's event; two consumers.

### EQUIP_CHANGE (TRIGGER)

Fires on equip/unequip of the carrying piece (direction param).
**Absorbs:** `EQUIP_CHANGE_TRIGGER` (02). **Consumers:** last-stand absorption
(02), mask transition hooks (11). **Bar:** WornState already tracks the
transitions; this exposes them to abilities.

### PROJECTILE_LAND (TRIGGER)

Fires at a fired projectile's landing point (no victim), position bound for AoE
anchoring. **Consumers:** landing-AoE bow abilities (05: several entries), web
fields. **Bar:** BOW fires at hit-entity; nothing observes ground impact.

## Kind extensions (wave 1, mechanical param additions)

| Extension | Absorbs | Consumers |
| --- | --- | --- |
| `DURABILITY` + `select` (slot\|most-damaged\|least-damaged\|random\|all) + `skip-undamaged` | `ARMOR_SLOT_DURABILITY` (03), `DURABILITY_PIECE_SELECT` (04, 05) | Demonforged, armor-repair procs, KOTH axe strips |
| `DURABILITY` + percent mode | `DURABILITY_PERCENT` (07) | Soul Siphon family |
| `CURE` + `count` (remove exactly N) | `CURE_LIMIT` (04), `CURE_COUNT_PARAM` (06) | single-debuff cleanses (Blessed both docs) |
| `REFLECT` + flat `cap` + feedback template | `REFLECT_CAP` (04) | capped self-reflect debuffs |
| `FREEZE` + `breakout-chance` (per-blocked-action shatter) | `FREEZE_BREAKOUT` (02) | struggle-out roots |
| `VELOCITY` + `anchor` (activator\|attacker\|victim) + `mode=toward` with expression magnitude | `VELOCITY_ANCHOR` (02), `PULL_IMPULSE` (06 — `clamp(distance²/50, 1, cap)` via EXPR_PARAMS) | self-launch procs, harpoon pulls |
| `FILL_OXYGEN` + `amount` (partial restore, clamp) | `AIR_TICKS_RESTORE` (06) | incremental breath effects |
| `FLY` + `speed` | `FLY_SPEED_PARAM` (06) | Rocket Escape flight |
| `MODIFY_FOOD` + `mode=scale-gain` (EAT events) and `mode=cancel-drain` (armed flag) | `FOOD_GAIN_SCALE` (02), `FOOD_DRAIN_CANCEL` (06) | hunger amplifiers, sustain wearables |
| `GUARD`/`SPAWN_ENTITY` param unification: `health`, `effects`, `name`, `speed` on both | `GUARD_STAT_PARAMS` (01), `SUMMON_STYLE` (07) | Guardians, Spirits, named styled summons |
| `SUPPRESS`/`MARK` + consume-time feedback (actor/victim messages, sound) | `MARK_CONSUME_FEEDBACK` (05), `SUPPRESS_CONSUME_CUE` (11) | Unfocus spam (measured), death-knight, zeus |
| `TEMP_BLOCK` + `fill-chance` (per-column %) and a revert hook carrying an effect list | `TEMP_BLOCK_FILL_CHANCE`, `TEMP_BLOCK_REVERT_HOOK` (05) | web fields (05), floor fields (07) |
| `TEMP_BLOCK` FOOTPRINT radius cap 4 → 5 | `TEMP_BLOCK_EXTENT` (07) | Permafrost L3+ (config cap bump; no semantics) |

## New effects (wave 1)

| Effect | Absorbs | Semantics / consumers |
| --- | --- | --- |
| `PERIODIC_DAMAGE` | (03) | actor-attributed DoT: amount/period/duration, optional vanilla-DoT replacement, per-tick feedback. Consumers: Divine Immolation, Bleed chains, Wither conversions |
| `HEAD_TROPHY` | `HEAD_TROPHY_DROP` (03), `PLAYER_HEAD_DROP` (04) | arm an on-death templated player-head drop ({VICTIM}/{KILLER}/{DATE}/{X}/{Y}/{Z}/{ITEM}); Decapitation both variants |
| `DESPAWN` | (02) | silent entity removal (no drops/XP/death event); AoE mob-clears |
| `PROC_REBOUND` | `ACTIVATION_REBOUND` (02), `PROC_REFLECT` (06) | chance to re-execute the incoming enchant ability with roles swapped, gated by tier-max + level ≥ incoming; rebound wearables both docs |
| `VIEWER_HIDE` | (02) | hide target from scoped viewer(s) (attacker\|all) for duration — packet-level, armor included; Undead Ruse; wave-2 `VANISH` builds on it |
| `SOUL_COST` | `SOUL_COST_GATE` (01) | ability-level charge-or-abort soul cost + out-of-souls branch; Immortal (01), soul family (07). Facts above; escalating variant is wave 2 |
| `PROJECTILE_DRESSING` | (05) | rider/dressing on a fired projectile (entity rider, ttl, invulnerable window); novelty bows |
| `OUTGOING_DEBUFF` | `OUTGOING_DEBUFF_CAUSE_FILTER` (05) | timed outgoing-damage debuff on a target with damage-cause filter (projectile-only); Unfocus |
| `DOT_AMPLIFY_MARK` | (05) | mark amplifying named DoT causes (WITHER/POISON) by factor for duration; Toxic Arrows |
| `SUMMON_REBIND` | `SUMMON_REBIND_UPGRADE` (05) | replace an owned summon with a fresh upgraded one (no death event); Hijack |

## New selectors / filters (wave 1)

| Item | Absorbs | Consumers |
| --- | --- | --- |
| Filter conjunction (`ENEMIES ∧ PLAYERS`) on AOE/NEAREST | `FILTER_COMPOSE` (02) | player-only hostile payloads (02), Plague Carrier, Smoke Bomb |
| Block-volume face-oriented box (w×h×depth from struck face) | `FACE_ORIENTED_BOX_SELECTOR` (06) | tunneling tools (Trench-class) |
| Block material filter on block selectors | `BLOCK_MATERIAL_FILTER` (06) | selective break/convert tools |

## Integration-gated (wave 1 timing, off by default)

- `EXTERNAL_SKILL_XP_MULTIPLIER` (06) — soft-depend hook multiplying a registered
  third-party skill system's XP awards, category-filtered. Ships behind the
  integrations discovery (like WorldGuard); no-op without the plugin.

## Wave 2 (lands before the doc 07 batch and sets/masks/pets)

**Summon family:** `SUMMON_PAYLOAD` (02 — phase-attached effect payloads:
detonate/death/pulse), `SUMMON_PURGE` (07), `SUMMON_STRIKE_PAYLOAD` (07),
`TURRET_RING` (07 — + `initial-delay`, the codex 30 t arming delay). Consumers
span Necromancer/Ghost sets, mastery fields, ancestral summons (02 consumers get
authored in the doc 07/sets batches; no earlier batch blocks on these). **The
last three SHIPPED in wave 2d.2** — the family is drained.
`SUMMON_STRIKE_PAYLOAD` is a 5th `PHASE_*` rung rather than a head, since it is
the same payload concept observed at a different moment, and it fires the
OWNER's `IMPACT` rather than `SUMMON_PAYLOAD`. `TURRET_RING` asks
`ProtectionProvider.allows` per SITE (owner ruling R-iv) and re-arms each volley
on the turret's own entity scheduler, because the period is jittered per volley
and a region task anchored at the caster cannot reach a turret across a Folia
boundary.

**Soul family:** `ESCALATING_SOUL_COST` (02 — growth/decay params on
`SOUL_COST`), `SOUL_TRANSFER` (07), `SOUL_MODE_DISABLE` (04), `SOUL_COST_EXEMPT`
(12 — timed exemption window; check against the engine's existing cost-free
concept before minting). **All four SHIPPED**, the last in wave 2d.2: the check
confirmed no exemption concept existed, so it is a store consulted at gate 10 —
the single choke point every debit passes — plus the effect-side `REMOVE_SOULS`
path, which never reaches that gate and would otherwise have made "no soul
costs" mean "except the ones authored as an effect".

**Visibility family:** `VANISH` + decoy (`VANISH` 07, `VANISH_DECOY` 12 — shared
state confirmed by both docs), `MOB_DISGUISE` (11 — spectral; irreducible: no
primitive alters other-client rendering), `PHANTOM_BLOCKS` (07 — per-viewer block
overlay). **Wave 2d STOPPED on all three; wave 2d.2 drained two of them.**
`PHANTOM_BLOCKS` shipped as the `BlockVisibility` seam minted on the
`PlayerVisibility` chain (owner ruling R-ii) — material-token shaped, because 1.8
has no `BlockData`, and carrying a captured APPEARANCE rather than a material for
the revert, because granite reverted by material would come back as plain stone.
`VANISH` shipped HIDE-ONLY (owner ruling R-i): the hide, the early break after N
landed outgoing hits, the maintained var and the join re-sync, all on a
store-backed window with one shared restore closure. What is left is the DECOY
and `MOB_DISGUISE`, now one **polish decision — packet seam vs roster-marker** —
recorded in `deferred-content.md` § Engine follow-up pool. Both need the same
greenfield packet work the modern lane has no path to today, so they are one
question, not two.

**Field family:** `STACKING_DOT` (07), `OWNED_GROUND` fact (07),
`DELAYED_STRIKE_FIELD` (10 — Yijki strike points), `BLOCK_FIELD_PROFILE` (10 —
extends FALLING_BLOCK: layers, per-position probability, palette). The last two
**SHIPPED in wave 2d.2**: `DELAYED_STRIKE_FIELD` as its own head, and
`BLOCK_FIELD_PROFILE` as new (inert-by-default) params on `FALLING_BLOCK` rather
than a second head, since it only ever extends the one shape. Its phase chains
are flat cue/payload params, not a nested effect list — the DSL has no
effect-chain param type, and `PERIODIC_DAMAGE`'s `tick-*` cues are the precedent.

**Combat marks:** `VULNERABILITY` (07), `POTION_AMP_REDUCE` (07),
`DEFENDER_KEYED_SUPPRESSION` (11 — the incoming-direction complement of the
SuppressionStore). `VULNERABILITY` and `DEFENDER_KEYED_SUPPRESSION` **SHIPPED in
wave 2d** (the latter as the head `SUPPRESS_INCOMING`, which names the authored
act beside `SUPPRESS`/`SUPPRESS_IMMUNE` rather than the mechanism), refined in
wave 2d.2 to a per-TARGET-APPLICATION consult so a chain hop onto a protected
bystander is skipped too. `POTION_AMP_REDUCE` **SHIPPED in wave 2d.2** with no era
seam: its enforcement is the per-tick re-assert on both lanes, because
`EntityPotionEffectEvent` can only cancel an application, never rewrite its
amplifier, and a cancel is the full strip the primitive exists to avoid.

**Small effects:** `FACING_SET` (07 — irreducible in-place yaw/pitch set),
`FALL_SHIELD` (07 — one-shot fall cancel on an arbitrary player),
`STATUS_CLEAR` (12 — remove a named engine status window, TELEBLOCK first) — all
three **SHIPPED in wave 2d**; `FACING_SET`'s reference is an `anchor` enum rather
than a second selector slot, because the DSL carries exactly one target slot per
effect,
`BOOK_RATE_MODIFIER` (12 — one-shot book success modifier; irreducible),
`SPAWNER_YIELD` (11 — bunny; irreducible: no spawner event in the vocabulary),
`WORN_COMPOSITE` (11 — multi-mask; irreducible core mask feature),
`INVENTORY_CONVERT` (12 — lava + water pets), `ITEM_XP_TRACK` (12 — the pet
XP/level infrastructure; every pet entry consumes it), `%actor.y%` position fact
(11). **All SHIPPED — the pool is drained: every item but `WORN_COMPOSITE` by wave 2d.2,
and that one in wave 2e.2.** Three carry a design
call worth reading before authoring against them. `SPAWNER_YIELD` is a worn
CHANNEL, not an activation — nothing procs a spawner firing, and the intended
per-spawn wearer test is precisely what an armed window would turn back into the
cached check the ledger rejects. `ITEM_XP_TRACK`'s `window` is in MINUTES, not
the recorded milliseconds, because the item store has no 64-bit surface and a
seconds epoch overflows an int in 2038. `INVENTORY_CONVERT` ships the INTENDED
cap-straddling direction, not the measured inversion. **`WORN_COMPOSITE` STOPPED
in wave 2d.2** on a contradiction inside its own matrix entry — def-declared
children versus per-item children — and was **RULED PER-ITEM and SHIPPED in wave
2e.2** (ADR-0074), built as the Multi Crystal shape: the children pack into the
helmet's existing `maskKey` as one `"a+b"` entry, so the codec is untouched and
there is no container def at all. The 2d.2 costing assumed per-item children
needed a new `CombatState` component; the crystal precedent had already answered
that, which is what collapsed the option's price.

## Excluded by owner ruling R9 (2026-08-02): economy is note-only

Cosmic behaviors that depend on the cosmic suite's server economy are NOT given
effect surface — the affected content ships with verbatim identity plus an
authored YAML note stating the behavior is unimplemented. No price tables, no
currency crediting beyond the existing static `MODIFY_MONEY` (which is
unaffected and remains available).

- `SHOP_SELL` (07) — **withdrawn**. Auto Sell ships inert with a note; the doc
  06 tool enchants' selling components (Auto Smelt / Detonate / Atomic Detonate
  / Telepathy ordering rules) are moot; their non-economy behavior ships
  normally. Ledger rows D-07-2 / D-06-10 annotated.
- Supreme set `+200% Clout (Flight Enabled)` (10) — lore verbatim + note; no
  clout/flight economy behavior.

## Provisional / dropped

- `LETHAL_CANCEL` (07) — chain-kill cancels the source hit. Single consumer,
  author-flagged as a drop candidate; **provisional**: the doc 07 batch decides,
  and dropping it takes a deviation-ledger row (felt-unit review).

## Self-review against the §4 bar

- Every entry above is parameterized and item-name-free; absorbed raw names map
  1-N→1 here (this file is the rename authority).
- Multi-consumer justification: all wave-1 entries have ≥2 consumers except
  irreducibility-annotated ones (`FILL_OXYGEN.amount`-class param extensions
  inherit their base kind's generality).
- Interaction logic (Dragon Slayer blocks, Soul Trap suppression, Blessed/Deep
  Wounds exclusion) stayed in matrix `interactions:` lines as YAML-condition
  rules — none leaked into a primitive contract.
- Rejected shapes: no per-enchant effects, no Sink routines, no primitive whose
  name or semantics references a specific item.
