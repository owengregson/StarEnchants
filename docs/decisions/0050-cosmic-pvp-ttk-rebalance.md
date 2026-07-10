# ADR-0050: Cosmic-pack PvP TTK rebalance (6–10 hit god-kit fights)

- **Status:** accepted
- **Date:** 2026-07-10
- **Relates to:** ADR-0012 (fully-additive damage fold), ADR-0037 (heroic percents in the
  fold), ADR-0049 (behavior alignment)

## Problem

The imported cosmic-pack combat values were carried 1:1 from their sources and never
re-budgeted for OUR fold. Three structural errors made god-kit PvP degenerate:

1. **Per-item budgeting on a per-player fold.** The additive fold (ADR-0012) sums every
   worn source, but values were authored as if each stood alone. Heroic −10%/piece reads
   sanely per ingot and sums to −40% on a full set; a defensive enchant's per-piece chance
   quadruples across four armor pieces (4 × 11% dodge ≈ 37% full-negate per swing);
   an always-on set bonus adds another −20%. Summed reduction routinely crossed 60–100% —
   `combat.max-bonus-reduction` shipped **uncapped** — so god armor was near-invincible.
2. **Rage was the only counter.** `%ragestacks% × 40` = +240% sustained, which blows
   through any defense — fights were either unkillable or instant.
3. **Incoherent variance.** Signature offense sat on lottery odds (Execute: 1.25–10%
   chance, 6s cooldown, for +12% against a ≤2.5-heart target — EV ≈ nothing) while other
   procs one-shot. Whoever rolled first won.

## Decision

Rebalance the entire pack around a **time-to-kill target: 6–10 melee hits between two
maxed god kits** (full heroic diamond set + set bonus + 9-slot enchanted armor vs a maxed
god sword), on both eras. Not preserved from the reference: only the fight pacing is
redesigned; identities, triggers, and content stay.

### The quantitative anchor

Vanilla god kit (Sharp V diamond vs Prot IV diamond) lands ≈ 1.05–1.10 hp per swing on
both eras → **≈ 18.5–19 hits** vanilla TTK. Both eras converge, so ONE value set serves
1.8.9 and modern. For a mid-band 8-hit fight the customs must contribute a net multiplier

```
M = (1 + Σ attack) × (1 − Σ reduction) ≈ 2.3
```

### The budgets (per PLAYER, not per item — worst legal stack, all sources summed)

| Bucket | Budget | Decomposition |
| --- | --- | --- |
| Attacker steady-state EV | **+170–200%** | set weapon bonus 20–30 + heroic weapon 10 + weapon crystal ≤5 + sword enchants ≈ 120–150 (Rage sustained ≈ 60–90 of that, finisher/conditionals the rest) |
| Defender steady-state EV | **≤ 30%** | heroic 4 × 3% = 12 + set armor bonus 5–15 + always-on conditional enchant line ≤ 12 (×4 pieces) |
| Net | M ≈ 1.8–2.1 steady | ≈ 9–10.5 hits steady; DoTs, finishers (Execute below 2.5 hearts) and 30s spike actives (Deathbringer, Double Strike) pull real fights to 7–9 |

Every defensive value is authored **4×-aware**: an `applies-to: [ARMOR]` enchant is
budgeted at four simultaneous copies (cooldown-scoped abilities share the enchant-scope
cooldown, so only chance-gated cooldown-0 contributions truly stack ×4).

### Structural changes (not just numbers)

- **`combat.max-bonus-reduction: 0.60`** — the pack config caps summed custom reduction at
  60%; no combination of sets, heroic, crystals and enchants can cross it. The cap is a
  combination-proofing backstop, not a balancing tool: designed steady-state sits near 30%.
- **`combat.max-bonus-damage: 3.5`** (from 5.0) — burst ceiling; converts a Double-Strike
  echo or a stacked-proc spike into ≈ 1.5× hits instead of one-shots.
- **Full negates become `CANCEL`, not `DAMAGE_MOD defense 100`.** Dodge-family "fully
  negate" effects cancel the damage event outright, so the reduction cap cannot clamp
  their identity and they stop inflating the reduction bucket. Their combined 4-piece
  proc rate is budgeted ≈ 4–11% with a real cooldown: the acceptable "lucky save".
- **Heroic**: `percent-reduction` 0.10 → 0.03 (armor stacks ×4 → 12% total);
  `percent-damage` stays 0.10 (a weapon is singular). The lore tokens render from config,
  so the ingot advertises the new numbers automatically.
- **Hidden always-on power is surfaced and gated.** Beyond Rage, the audit found permanent
  every-hit Wither II / Blindness II on set weapons, a 100%-refresh 20-HP absorption
  shield (Stellar), ungated debuff immunity (Ender Walker), +24 max HP (Godly Overload),
  fold-bypassing flat damage (Sniper's 8 hp), and a self-damage-reducing Dominate import
  bug (defense-side contribution on the wielder's own attack walk — now `WEAKEN`). Each is
  either chance/cooldown-gated or rescaled into the budgets above.

### Variance philosophy (the proc-rate rework)

- **Conditional/consistent damage** (narrow always-true conditions): chance → 75–100%,
  cooldown → 0–2s, amounts small (max-level +8–20%). Frequent and reliable, not swingy.
- **Burst procs**: chance 20–35%, amounts ≤ +35% at max level, cooldown 4–8s;
  per-enchant sustained EV ≤ +10%.
- **Finishers** (Execute family): reworked from lottery to reliable — 100% chance below
  the health threshold, +15–25%, short cooldown. Finishers END fights (they support the
  pacing target); they cannot start them.
- **Defensive constants** (cooldown-0 reductions): per-copy max ≈ 3–5% (×4 pieces = 12–20%
  ceiling); windowed (cooldown > 0) reductions 10–15% since only one copy fires per window.
- **CC procs**: short durations (1.5–3s), chance 10–20%, cooldowns ≥ 5s — no stunlock.
- **DoT families**: total EV ≤ ~20% of direct damage; they accelerate the endgame.
- **Sustain** (lifesteal family): capped per-proc heals; a god kit's sustain may add ≈ 1–2
  effective hits, never stall the band.

### QOL cue policy (same PR)

- **REPEATING** effects lose their `SOUND` lines (the particle may stay) — a repeating
  chime is noise, not information. Exception: effects that **consume souls** on
  activation keep their sound (the player is paying; the cue is the receipt).
- **PASSIVE** effects that apply once when gear is equipped lose `SOUND` and `PARTICLE`
  lines — the equip moment already has the universal set/equip cues.

## Consequences

- Set lore, enchant descriptions and `docs/cosmic-set-abilities.md` carry numeric claims;
  they are updated in lockstep with the values they describe (drift is a review-blocker).
- The budget table above is the contract for FUTURE content: a new enchant/set/crystal
  claims room inside the buckets, not on top of them.
- `max-bonus-reduction` now binds: any future "immunity"-flavored content must be a
  `CANCEL`/`SUPPRESS` mechanic, not a ≥100% reduction contribution.
