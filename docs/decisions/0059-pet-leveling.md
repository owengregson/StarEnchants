# ADR 0059: Pet leveling — level suffix, level-up cue, use-XP, passive inventory accrual

- **Status:** Accepted
- **Date:** 2026-07-16
- **Deciders:** project owner + agent
- **Extends:** ADR-0052 (pets)

## Context

ADR-0052 shipped a flat leveling economy: kill/XP credits and a whole-exp-per-minute hotbar drip. The owner
wants the level visible in the item NAME, an audible/visible level-up moment, a use-reward for ACTIVE pets,
and a slower, fractional passive economy that covers the whole player inventory and favours PASSIVE pets
held in the hotbar.

## Decision

1. **Level suffix in the universal name template.** `items/pet.yml` `name` gains the verbatim suffix
   `&7[Lvl. &f&n{LEVEL}&r&7]&r` (both shipped copies + `PetItemConfig.defaults()`). No renderer change:
   every level write already funnels through `commitProgress → render`, which substitutes `{LEVEL}` into the
   name — the suffix is single-sourced content, not code.
2. **Level-up cue.** New `pets:` knobs `level-up-sound` / `level-up-particle` (the unified bracket forms),
   played to the holder by `PetLevelCue` from `commitProgress` whenever a commit gains a level — one cue per
   GAIN EVENT from any source (a +10 Pet Food is one cue). Absent keys default in-family; a blank sound or
   empty particle is silent. All leveling paths already run on the holder's region thread, so playback adds
   no scheduler hops.
3. **Use-XP.** On `UseAttempt.activated()` (the full gate walk passed — cooldown gate included) an ACTIVE
   pet gains a uniform random `[expPerLevel/8, expPerLevel/5]` exp, min 1, rolled through the injected
   `Rolls`/`Random`. PASSIVE pets have no USE path. Stacked heads are skipped (the ADR-0052 dupe rule).
4. **Passive inventory accrual.** Replaces `exp-passive-per-minute`: any pet in the player's MAIN inventory
   (slots 0-35 — era-stable; never containers, never offline) earns `passive-levels-per-hour` (default 0.5);
   a PASSIVE-type pet in the hotbar earns `passive-hotbar-levels-per-hour` (default 1.0) instead. The
   per-minute pets sweep credits it in exact fixed-point: 60 000 units = 1 exp, one minute adds
   `round(rate×1000) × expPerLevel` units, the remainder persists on the item as the `petexpfrac` PDC int —
   accrual survives item moves and never drifts (rate quantized once, to milli-levels/hour). Accrual parks
   at the level cap (frac zeroed with the exp).
5. **Render gate.** A progress write recomposes name/lore only when a DISPLAYED number changed — the level
   or an exp-bar tenth. Carry-only and inside-a-tenth writes persist PDC without a recompose (the counters
   live outside the combat blob, so the `ItemView` cache is untouched either way).

## Consequences

- The old `exp-passive-per-minute` key is ignored if present (warn-free; the section falls back per-field).
- Heads minted before this ADR pick the suffix up on their next level write (render-from-state).
- Suppression never touches XP accrual (listeners + service math never enter the gate walk); the cue rides
  the commit, so both pet types get suffix + cue + XP identically.
