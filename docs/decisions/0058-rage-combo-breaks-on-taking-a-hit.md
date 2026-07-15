# ADR 0058: Taking a hit breaks the rage combo; direct player menu commands

- **Status:** Accepted
- **Date:** 2026-07-14
- **Deciders:** project owner + agent
- **Extends:** the 1.8.6-beta rage fix (rage stacks count only rage-weapon hits, PR #249), ADR-0030 (the GUI
  framework + `/enchants`)
- **Relates to:** ADR-0050 (PvP rebalance — the `%ragestacks%` DAMAGE_MOD economy)

## Context

**Rage stacks.** Rage stacks are the combat feedback + damage scale the Rage enchant owns: `%ragestacks%` = the
current run count, read by the rage `DAMAGE_MOD` and driven up an audio/action-bar ladder by
`RageStacksService`. A run builds one stack per consecutive rage-weapon hit on the same target (capped at the
rage level) and breaks on a target switch or the combo window elapsing.

Reported live: **"rage stacks aren't being broken properly — when the enemy player hits you the rage stack
should be reset to zero and built again."** The intended design is that **rage is a combo you keep by NOT being
hit**: any hit you take should drop it. But the run was only ever broken by the holder's OWN actions (switching
target, or letting the combo window lapse) — taking a hit did nothing, so a player could ride a high rage combo
straight through the blows they were trading. The break condition was missing its most important trigger.

**Menu commands.** `/se` is `starenchants.admin`-gated, so `/enchants` (the one player-facing command, ADR-0030)
opened the **hub** — a launcher that tiles into every player bench/browser. Players wanted a direct command per
bench instead of navigating the hub.

## Decision

**1. Taking a hit breaks the rage run (`RageStacksService.onHitTaken`).** `RageStacksListener` now feeds BOTH
sides of one `EntityDamageByEntityEvent`: a direct player damager builds that attacker's run (`onHit`, unchanged),
and a **player victim** has its own run broken (`onHitTaken`) — from ANY damager (a player, a mob, a projectile),
because the rule is "keep the combo by not getting hit." A meaningful run (more than one stack) flashes the
existing BROKEN cue + action bar; a lone stack drops silently. Environmental damage (fall, fire) is not an
`EntityDamageByEntityEvent` and does not count; engine-issued damage (DoT ticks, reflects) is skipped by the
existing `EngineDamage` frame guard, so a bleed tick does not reset rage. The build path, the `%combo%`-derived
count, and the window-expiry probe are all unchanged — this adds the missing break trigger and nothing else.

**2. Direct player menu commands.** `UserMenuCommand` is generalised to open any registry menu by NAME (the
command label may differ from the target), and `MenusModule` registers one `starenchants.use` command per
player surface: `/enchants` and `/enchanter` → the enchanter bench, `/pets`, `/masks`, `/tinkerer`, `/alchemist`,
`/sets` → their menus, and `/crystals` + `/catalogue` (the `enchants` Enchant Catalogue, whose natural name is
taken by the bench) so the two hub-only browsers stay reachable once the `/enchants`→hub launcher is retired.
Each target resolves at execute time, so a shortcut whose family is disabled reports gracefully.

## Consequences

- Rage now behaves as intended: a player who takes any hit loses their rage combo and must rebuild it, so the
  bonus rewards landing an uninterrupted string of hits rather than winning a straight trade. This is a
  meaningful nerf to rage uptime in even PvP exchanges (both parties reset each other), which is the point.
- The change is minimal and local: `RageStacksListener` gains the defense side, `RageStacksService` gains
  `onHitTaken`; the store, the `%combo%` coupling, and the wiring are untouched.
- Nine player-facing menu commands replace the single `/enchants`→hub entry; every hub-linked bench/browser
  keeps a direct command, so nothing a player could reach before is lost. The hub menu still exists (operator
  `/se menu hub`) and the godly-transmog editor still opens from its scroll gesture.

## Alternatives considered

- **Keep the run alive while taking hits (the first draft of this ADR).** The initial reading of the report was
  the opposite — that a defensive phase was wrongly *dropping* stacks and they should PERSIST through getting
  hit. The owner corrected it: getting hit must RESET to zero. That draft (decoupling the run from `%combo%` to
  give it a window any combat activity refreshed) was discarded wholesale.
- **Reset only on a player's hit, not a mob's.** The owner specified "from ANYONE," and gating on the damager
  type adds a shooter-resolution branch for no benefit; a single unconditional break is simpler and matches the
  stated rule.
- **Keep `/enchants`→hub and only add `/enchanter`.** The owner asked specifically for `/enchants` to open the
  enchanter bench; the two orphaned browsers are covered by `/crystals` + `/catalogue` instead.
