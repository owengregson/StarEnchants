# ADR-0055: Same-hit riders land in effective units; the combat economy's three scaling classes

- **Status:** accepted
- **Date:** 2026-07-12
- **Relates to:** ADR-0012 (fully-additive damage fold), ADR-0050 (TTK rebalance, R2's
  `combat.attack-scale`), ADR-0053 (masks), ADR-0054 (same-hit fold and attribution — **superseded in
  part**: the folded-rider unit semantics only; every other 0054 decision stands)

## Problem

ADR-0054 fixed the same-hit rider defects (the second immunity window, the missing attribution) by
routing zero-WAIT victim-aimed `DAMAGE`/`MODIFY_HEALTH take` intents into the damage fold's **flat
bucket**. But that bucket is part of the ADR-0050 R2 scaled economy: `combat.attack-scale` (5.0 in the
cosmic pack, the armor-pipeline adapter) multiplies it. The pre-0054 bare hurt bypassed the fold — and
therefore the scale — entirely, so folding silently made every same-hit rider land **~5× harder** than
the meta it was tuned in. 0054's "Owner-confirmed … folded riders deliver post-multiplier EFFECTIVE
damage" sentence misrecorded the intent behind the owner's words: "the effective damage as
StarEnchants delivers it" means **authored = delivered** — a rider authored 6 lands as 6 — not
"whatever the multiplied fold happens to produce."

The owner has now clarified the tuning ground truth: **the pre-1.8.2 numbers WERE the balanced
meta.** The tooltips-vs-effective discrepancy the ×5 "fixed" was, in gameplay reality, the baseline
the whole economy was calibrated against (ADR-0050 R4's D-space budgets priced bare-hurt riders as
PvE-tuned and *excluded from the PvP budget* precisely because they bypassed the fold).

## Decision

1. **A same-hit rider's authored amount is EFFECTIVE damage: authored = delivered, pre-armor.** The
   fold gains a distinct rider bucket (`DamageFold.addEffectiveDamage`) that the same-hit routing
   uses. It joins AFTER the scaled-and-mitigated custom economy:

   ```
   final = max(0, max(0, (base × (1 + Σout% × scale) + Σflat × scale)
                          × (1 − Σred%) − ΣflatRed) + Σeffective)
   ```

   Never multiplied by `attack-scale`, never priced by the defense terms, never absorbed by flat
   reduction — the pre-1.8.2 bare hurt was a separate event that neither the attack economy nor the
   defense walk ever saw, and the restored bucket keeps exactly that shape. Amounts are NOT divided
   by the scale (brittle under config changes) and `attack-scale` itself is untouched — it governs
   the percent/flat economy, which was always scaled and is part of the good meta.
2. **The scaled flat bucket keeps its semantics.** `DAMAGE_MOD`/`DAMAGE_SCALE` `mode: flat` and the
   heroic diamond base-attack delta are the ADR-0050 R3/R4 "flat-forward" economy — authored
   normalized, adapted by the scale, by design. Nothing shipped ever reached the flat bucket through
   the rider path except via 0054's five-day routing; that routing now feeds the rider bucket.
3. **Everything else ADR-0054 decided stands:** one hurt, one immunity window; a rider on a
   dodged/cancelled hit dies with its hit; separate procs (WAIT tiers, bystanders, bolts, reflects)
   are attributed applications behind the `EngineDamage` frame; `ImmuneListener`/rage semantics.
4. **The three scaling classes are the classification contract for all combat content:**
   - **(a) percent-economy modifiers** — fold percents and scaled flats (`DAMAGE_MOD`,
     `DAMAGE_SCALE`, heroic percents/flats, marks, WEAKEN). Ride `attack-scale` by design; authored
     on the normalized human scale (ADR-0050 R2).
   - **(b) effective-unit riders** — zero-WAIT victim-aimed `DAMAGE`/`MODIFY_HEALTH take` on a
     combat-dispatched hit (ATTACK/BOW/TRIDENT/DEFENSE). Authored = delivered pre-armor, post-scale.
   - **(c) separate attributed damage instances** — WAIT DoT ticks, AoE/bystander `DAMAGE`,
     `LIGHTNING`, thorns/reflect retaliations, Vengeful Diminish overflow, and every non-combat
     dispatcher (IMPACT, GUARDIAN_HURT, REPEATING — none declares an event entity). Their own
     vanilla pipeline, never scaled, never folded.

   A future value claims a class explicitly; an ambiguous one is a review-blocker.

## The 1.8.3 audit (category × path × class × verdict)

Baseline: the v1.8.1-beta tag (the balanced meta). The only engine reroute between 1.8.1 and 1.8.2
was ADR-0054's (DamageEffect/HealthModEffect/LightningEffect routing + dispatcher retaliations +
listener frames); pack content was untouched, so every fold-side value below is bit-identical to
1.8.1 unless marked.

| Category | Path | Class | Pre-1.8.2 → 1.8.2 → now | Verdict |
| --- | --- | --- | --- | --- |
| enchants | sniper / lethal-sniper (BOW, zero-WAIT `DAMAGE @Victim` 2–4 / 3–5) | b | bare hurt N → folded **N×5** → folds N effective | **FIXED** (the ×5 regression; restored) |
| enchants | bleed / deep-wounds / deep-bleed / bloody-deep-wounds DoT ticks (WAIT 20/40/60, `%damage%/8` or `/6`) | c | bare hurt → attributed hurt, same amounts | IN LINE (attribution ratified by 0054) |
| enchants | cleave / mighty-cleave / divine-immolation / destruction / natureswrath (`@Aoe`, incl. percent-of-max 4–5) | c | bare hurt → attributed hurt, same amounts | IN LINE |
| enchants | block / reflective-block / cactus / mighty-cactus (DEFENSE, `DAMAGE @Attacker`) | c | bare hurt → attributed hurt, same amounts (+vanilla source knockback where no knockback owner runs — 0054-ratified) | IN LINE |
| enchants | thundering-blow / thor set / divine-immolation / natureswrath / yijki (`LIGHTNING`, damage 0–2 / `%damage%×0.4`) | c | bolt + bare extra hurt → bolt + attributed extra hurt, same amounts | IN LINE |
| enchants | `DAMAGE_MOD`/`DAMAGE_SCALE` percent + flat lines (33 files: Rage %+flat rider, Shadow Assassin, Assassin, Execute, Deathbringer/Planetary, Enrage/Furious, Insanity/Extreme, Barbarian, Rogue, Bloody Deep Wounds flat, Berserk, Piercing/Lethal Sniper %, Anti Gank, blacksmiths' negatives; defense adders Armored/Tank/Heavy/Aegis/Block families) — the R3/R4 flat-forward economy | a | untouched by 1.8.2 | IN LINE (the scaled flat bucket keeps its semantics — it is heavily shipped) |
| enchants | lifesteal family / enlightened / phoenix / death-god / ender-walker / implants (`MODIFY_HEALTH give` heals), mortal-coil (`mode: set`, ADR-0051 write). `mode: take`/`transfer` ship in NO content — the class-b routing for them is pinned by tests only | — (sustain, not damage) | untouched by 1.8.2 | IN LINE |
| enchants | Hex/Bewitched-Hex reflect, Vengeful Diminish overflow (dispatcher retaliations off the committed value); Diminish cap (defense) | c (cap: defense economy, unscaled) | bare hurt → victim-attributed hurt, same amounts | IN LINE |
| enchants | vanilla-pipeline dealers: Hellfire `PROJECTILE` fireball, Self-Destruct TNT, Undead Ruse / Rot-and-Decay zombies, Guardians/Spirits `GUARD` mobs, Molten/Divine-Immolation `IGNITE` fire ticks, Slayer `KILL`, Disarmor (amplifier) | c | untouched by 1.8.2 | IN LINE |
| enchants | Dominate/Unfocus/Voodoo/Destruction `WEAKEN`, Neutralize/Silence/Solitude/… `SUPPRESS`, dodge-family `CANCEL`, Double-Strike `ECHO_STRIKE` (one extra walk over the SAME fold) | a / gates | untouched by 1.8.2 | IN LINE |
| masks | knight (defense +5%), monopoly (attack +1 ≈ +5% at scale 5.0, authored scale-aware — in-file CALIBRATION note), midas `IGNORE_HEROIC` (drops victim heroic buckets, ADR-0053), blaze FIRE `CANCEL`, fisherman fish-hook `IMMUNE`, wards (mob-target/invsee/near/splash-heal — heal intensity only, never damage), chef KIND-scope `SUPPRESS` | a / defense economy / gates | untouched by 1.8.2 | IN LINE |
| pets | `DAMAGE_MOD` lines (grim +20 ≈ ×2.0 window, eagle 4–7, enderman 5–20, shield defense 15–25 — attack values authored scale-aware, in-file CALIBRATION notes) | a | untouched by 1.8.2 | IN LINE |
| pets | creeper-pet detonation (`PetSummonListener.onHit` → `createExplosion 6.0f`, entity-damage only), summon invincibility zeroing, GuardianCasts→GUARDIAN_HURT (read-only trigger); no pet-sourced `DAMAGE` rows ship | c where damage exists | hit-gating now sees attributed engine hits (0054-intended: an AoE proc on a tracked summon IS a player hit) | IN LINE |
| sets | druid Terrablender (IMPACT, `DAMAGE @Victim` `0.75×%damage%`) | c (IMPACT declares no event entity — never folds) | bare hurt → attributed hurt, same amount | IN LINE |
| sets | thor `LIGHTNING` `%damage%×0.4`, yijki cosmetic bolt + `CANCEL`, set `DAMAGE_MOD` bonus lines (attack +20–30%, defense 5–15%), koth `DAMAGE_SCALE`, reaper `MARK` +25%/`WEAKEN`, devil `MARK_ZONE`, cupid `MAX_HEALTH_DRAIN` | c / a / control | untouched by 1.8.2 (bolt attribution aside) | IN LINE |
| crystals | `DAMAGE_MOD` percents only (dark/ender/flame/frost/light/water attack 1–10; chaos/dark/nature defense 3–10); nature `HEALTH` +4 max | a | untouched by 1.8.2 | IN LINE |
| heroic | percent-damage 0.20 / diamond flat delta (plain attack adders), percent-reduction 0.02/piece / flat delta (heroic-tagged buckets, ADR-0053); `TriggerRunner` is the verified SOLE fold path | a | untouched by 1.8.2 | IN LINE |
| addon SPI | `AddonSink` two-arg `damage` → three-arg seam → same-hit fold when aimed at the current victim | b | inherits the rider bucket | FIXED with the core fix |

No FLAGGED-ambiguous rows: every enumerated path either kept its pre-1.8.2 numbers bit-identically,
was ratified by ADR-0054's attribution decision (same amounts, attributed events), or is the
restored rider class. `IGNORE_ARMOR` and `EXPLODE` effect kinds ship in no content at all.

**One documented edge (ratified, not a regression):** a rider rides the ONE event, so everything
that consumes the event consumes the rider — a dodge/`CANCEL` (0054-ratified) and, new in kind, the
Diminish `DAMAGE_CAP`, which clamps the committed value including riders (pre-1.8.2 it could only
cap the melee; the separate rider hurt sailed past it). This is the same-fate principle applied
consistently — Vengeful's overflow stays coherent off one committed value — and it is
defense-favorable only inside Diminish's one-hit window. Re-tuning Diminish for it would be a
balance change, not a restoration, so the values stand.

## Consequences

- Sniper/lethal-sniper (and any future authored rider) deliver their authored numbers again; the
  percent/flat economy, the caps, and `attack-scale` are byte-identical.
- `/se damagedebug` prints the rider bucket separately (`eff`), so a live calibration read can never
  mistake riders for scaled flat.
- ADR-0054's unit-semantics sentence is superseded; its routing, attribution, immunity-window and
  re-entrancy decisions are unchanged and still pinned by their suites.
- Pinned by the `DamageFold` corpus (a rider authored 6 moves the result by exactly 6 under
  attack-scale 5.0, beside untouched percent/flat/cap rows) and the sink routing suite
  (rider → effective bucket; WAIT/bystander/no-event-entity negatives guard both buckets).
