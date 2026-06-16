# Glossary

One name per concept, used identically in code, config, and docs. Pulled from
the merged EliteEnchantments + EliteArmor domain.

## Engine

- **Ability** — a single configured behavior that can activate: its triggers,
  conditions, cost, and effects. The unit the engine runs, regardless of where it
  came from.
- **Effect source** — anything that contributes abilities/modifiers to an entity:
  a custom enchant, an armor set, a weapon, a crystal/modifier, or Heroic stats.
- **Effect** — a single action an ability performs (deal damage, apply potion,
  spawn entity, suppress an enchant, …). Identified by name, takes typed args.
- **Trigger** — the game event that can activate an ability (attack, defense,
  mine, bow-fire, held, passive/equip, …).
- **Condition** — a boolean expression gating activation; may also adjust chance
  or flow (STOP/FORCE/CONTINUE).
- **Target selector** — names which entity/entities an effect acts on (self,
  victim, attacker, aoe, nearest, …).
- **Variable** — a `%name%` value resolved at activation (e.g. `%victim_health%`),
  usable in conditions and effect args; PlaceholderAPI passthrough supported.
- **Activation pipeline** — the ordered gate that decides if/how an ability runs:
  region/protection → cooldown → conditions(+chance Δ) → chance → cancellable
  pre-activate → cost → run effects.

## Enchants

- **Custom enchant** — a config-defined enchant with levels; not a vanilla
  enchantment. Lives on an item's data, renders into lore.
- **Group / rarity** — a named tier (e.g. COMMON…MYTHIC); order defines rarity.
- **Target / applies** — the set of item types an enchant can be applied to
  (e.g. SWORDS, ARMOR), composable via parents.
- **Slot** — an item's capacity to hold custom enchants; raised by orbs/gems.
- **Soul** — currency stored on a soul gem; gates "god-tier" ability activations.

## Items & economy

- **Enchant book** — applies one enchant at a success/destroy chance.
- **Scroll** — white (protect from destroy), holy-white (keep on death), black
  (extract an enchant to a book), transmog/godly (reorder enchant lore),
  randomizer (reroll a book's rates).
- **Dust** — success-rate booster (secret/magic/omni/mystery).

## Armor

- **Set** — a group of armor pieces (+optional weapon) that grants a **set bonus**
  when enough pieces are worn (**Required-Items**).
- **Crystal / modifier** — an item that applies a set's effects to a single piece;
  **synergy** crystals combine two sets.
- **Omni** — a wildcard piece that counts toward every set's completion.
- **Heroic** — an upgraded "stronger-than-diamond" variant with flat stat bonuses.

## Platform

- **Resolver** — boot-time name→value lookup (with legacy aliases) for a
  version-volatile API (Material/Sound/Particle/Enchantment/PotionEffectType/
  Attribute/EntityType).
- **Scheduling abstraction** — the only sanctioned way to touch
  entities/blocks/world/timers; region-correct on Folia, main-thread on Paper.
- **Capability probe** — a load-time check for a newer API before using it.
- **Snapshot** — the immutable, atomically-swapped set of compiled definitions
  the running plugin reads.
- **Item-data service** — the single owner of item state in PDC.
