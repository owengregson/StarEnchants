# ADR-0049: Cosmic behavior alignment (description-claim audit resolutions)

- **Status:** accepted
- **Date:** 2026-07-09

## Context

A description audit compared every cosmic-pack enchant against its Cosmic Enchants-style
reference description and flagged 41 where the two claim different behavior (different
mechanic, or a condition/scope one side drops). The owner ratified a per-enchant resolution
matrix: some enchants adopt the reference behavior, some keep ours, and the description
copy across the whole pack is regenerated to match what actually ships.

## Decision — behavior matrix

Where a scale/chance was delegated ("you decide"), the chosen numbers are recorded here and
in the enchant YAML; they are balance knobs, not contracts.

### Adopt the reference behavior

| Enchant | Ships as |
| --- | --- |
| Bleed | Root DoT: 3 iterations, 1s apart (first at +1s); iteration i deals 1/5, 1/4, 1/3 of the proc'ing hit's damage, redstone block-crack particles on the victim, Slowness II 20t per iteration. |
| Deep Wounds | No longer bonus damage — a per-level bonus to the Bleed enchant's proc chance. |
| Bloody Deep Wounds | Larger Bleed-chance bonus + keeps its own bonus-damage strike (inherits, upgrades). |
| Hardened | Armor durability protection: per-level chance to cancel a durability-point loss. |
| Soul Hardened | Negates armor durability loss entirely at 1 soul per point saved; still blocks enemy Soul Trap. |
| Reforged | Tool durability protection (chance to cancel the point loss) — no repair. |
| Solitude / Perfect Solitude | Amplifies the wearer's Silence enchant: adds proc chance and extends the disable duration (Perfect roughly doubles Solitude's amplification). |
| Pacify | Arrows disable the target's Rage enchant for a fixed 3s. |
| Hex / Bewitched Hex | Marks the target: a portion of their outgoing damage is reflected back onto them for 4s / 8s. |
| Double Strike | The hit re-runs the attack activation once (enchants can re-proc); all damage folds into the one event. |
| Blood Link | Heals the owner when one of their summoned Guardians takes damage. |
| Cactus / Mighty Cactus | A stronger thorns: reflect damage when hit, own armor spared the durability loss; Mighty is the same, scaled up. |
| Rogue | Bonus damage only when striking the target's back. |
| Aegis / Holy Aegis | Gank shield: once more than (8−level) / 1 distinct attackers hit you in a short window, damage from attackers beyond that group is reduced. |
| Death Pact | Take level% less damage per 10% missing health; deal level% less damage per 10% missing health. |
| Chain Lifesteal | Heal scales with the number of enemies near the fight. |
| Clarity | Passive immunity to Blindness. |
| Ethereal Dodge | Dodge's proc rates +1%/level, Speed V on a successful dodge, and all fall damage disabled. |
| Vengeful Diminish | Diminish's cap, and damage above the cap is dealt back to the attacker. |
| Diminish | On proc, the NEXT attack against you caps at half the damage of the last attack taken. |
| Corrupt / Maliciously Corrupt | On proc, the target's next Inversion heal is negated (Maliciously: next two, higher chance). No bonus damage. |
| Hellfire | Every arrow becomes an explosive fireball (level scales the blast). |
| Destruction | When hit: damages + debuffs ALL nearby enemies, at lowered chances/damage, applying a non-stacking level% outgoing-damage nerf to each enemy affected. |
| Divine Immolation | The proc'ing hit lands on every player within 2 blocks of the target, with cosmetic lightning, 20t burning and 20t Wither I. |
| Anti Gank | Once more than (6−level) enemies hit you in a short window, outgoing damage scales with nearby enemies (capped ~1.5x). |
| Soul Trap | Disables the target's entire soul tier for level×4s. |
| Planetary Deathbringer | The guaranteed crit deals 2.5x (not 2x). |
| Neutralize | Now a weapon enchant: on proc, the target's defensive enchants are disabled for their next hit taken (one-shot, then clears). |
| Slayer | Instant-kill scoped to mobs only (never players). |
| Dodge | Proc chance doubles while sneaking. |
| PermaFrost | Keeps the terrain proc, plus reduced incoming damage while standing on snow/ice. |
| Natures Wrath | Keeps freeze+knockback, plus a flat 10% of each affected victim's max health as damage. |

### Keep ours (description regenerated to match)

Assassin / Shadow Assassin (no distance penalty), Unfocus, Spirits, Eagle Eye,
Reflective Block + Block (gate on blocking), Rot and Decay, Death God,
Mortal Coil (bonus damage REMOVED — heart-removal only).

### New content

**Soul Drinker** (soul tier): repeating every 20 ticks, consumes 2 souls per activation,
and suppresses all hunger loss while active.

### Mythic self-containment invariant

A mythic upgrade removes its base counterpart on apply (`removes-required`), so every
mythic's YAML must be SELF-CONTAINED — it carries the full upgraded behavior, never
assuming the base is still worn. Audited across all mythics in this change.

## Presentation decisions (whole pack)

- Enchant descriptions drop the per-level stat breakdown lines entirely.
- Description body text is `&e` (yellow), never the tier color.
- Every description is regenerated to accurately claim the shipped behavior, in the
  established medium/high-level style (no concrete numbers).
- The enchant book's applies line matches the item convention:
  `&eApplies to: &r&f&n{KINDS}` (items/enchant-book.yml), replacing
  `&7{KINDS} Enchantment`.

## Consequences

- New engine primitives are added only where the DSL cannot express a resolution
  (recorded in the implementing commits); everything expressible stays pure YAML.
- Books/menus render the new copy automatically (lore is rendered from state).
- The audit's raw side-by-side is reference material and stays out of the repo.
