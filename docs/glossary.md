# Glossary

One name per concept, used identically in code, config, and docs. Drawn from
the combined custom-enchant + armor-set domain.

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
- **Variable / fact** — a `%scope.name%` value resolved at activation (e.g.
  `%victim.health%`, `%actor.world%`), usable in conditions and effect args;
  PlaceholderAPI passthrough supported. The built-in vocabulary is declared in
  `engine/condition/BuiltinVars` and populated by `engine/run/FactPopulator`; a
  new fact is added there, not by writing a condition class.
- **Activation pipeline** — the ordered gate that decides if/how an ability runs:
  region/protection → cooldown → conditions(+chance Δ) → chance → cancellable
  pre-activate → cost → run effects.
- **FactBuffer** — the reusable, thread-local struct of primitive facts one
  activation reads by compiled slot index (zero boxing, zero string parsing on
  the hot path).
- **Sink** — the single mutation boundary: effects emit *intents* into it and
  never touch entities/schedulers directly; it routes work by declared Affinity.
- **Affinity** — an effect's declared thread locality
  (`CONTEXT_LOCAL / TARGET_ENTITY / REGION / AOE / GLOBAL / ASYNC`), folded to the
  ability level so the engine (not the author) decides scheduling — the Folia
  correctness axis.
- **Arbiter** — a contribute-then-resolve interaction authority in
  `engine/interact` (DamageFold, SuppressionSet, SlotLedger, the soul pool):
  effects contribute, the arbiter commits once, so order-dependent bugs can't form.
- **Snapshot (compiled)** — see Platform › Snapshot.

## Enchants

- **Custom enchant** — a config-defined enchant with levels; not a vanilla
  enchantment. Lives on an item's data, renders into lore.
- **Group** — a named enchant grouping used as the cooldown-group scope and for
  display; distinct from Tier.
- **Tier** — a rarity level (e.g. `common`…`mythic`) declared per enchant/crystal
  via an in-file `tier:` field and defined in `content/tiers.yml` (colour, glint,
  GUI sort weight). Presentation/gating metadata only — **never** part of an
  item's stable key, so re-tiering never changes stamped gear (ADR-0016).
- **Target / applies** — the set of item types an enchant can be applied to
  (e.g. SWORDS, ARMOR), composable via parents.
- **Slot** — an item's capacity to hold custom enchants; raised by orbs/gems.
- **Soul** — currency stored on a soul gem; gates "god-tier" ability activations.

## Items & economy

- **Enchant book** — applies one enchant at a success/destroy chance.
- **Scroll** — white (protect from destroy), holy-white (keep on death), black
  (extract an enchant to a book), transmog/godly (reorder enchant lore),
  randomizer (reroll a book's rates).
- **Dust** — Success Dust: a carrier item dragged onto an enchant book to raise
  its success chance by a bonus (a random `[min-bonus, max-bonus]`, or a fixed
  value via `/se dust <percent>`), clamped to `books.max-success` (ADR-0019).
- **Trak** — a StatTrak-style gem (BlockTrak / MobTrak / SoulTrak / FishTrak)
  applied to a tool/weapon/rod to reveal a lifetime counter tracked in the
  background from first use; several traks coexist on one item (v1.1.5,
  `feature/trak`).

## Armor

- **Set** — a group of armor pieces (+optional weapon) that grants a **set bonus**
  when enough pieces are worn (**Required-Items**).
- **Crystal** — an authored, multi-ability item applied to a single piece of gear
  (at 100%) and stored as a list, so several coexist. Identical crystals merge
  into one stacked entry up to `crystals.max-merge`; a crystal may be marked
  non-`stackable`; a merged crystal renders as a "Multi Crystal" and extracts as
  one whole entry (ADR-0034/0035). There is no compile-time "synergy" crystal
  that combines two sets.
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
- **ParamSpec** — one typed declaration per effect/condition/selector/trigger
  (name, type, required, default, range, doc, example), used four ways: validate,
  tab-complete, `/se docs`, and migrate — the single source of truth for a kind.
- **Item-data service** — the single owner of item state in PDC.
- **ItemView** — an immutable decoded snapshot of one item's combat state, served
  by `ItemViewCache` keyed on the item's full raw PDC blob within the current
  generation (decode once, then map lookups; reset on reload).
- **WornState** — the event-driven, per-player, pre-flattened immutable snapshot
  of everything worn (all sources merged, multi-set), resolved once per equipment
  change and read on the combat hot path instead of rescanning gear.
- **Config pack** — a swappable whole-config preset (config.yml, lang.yml,
  content/, items/, menus/ + a pack.yml manifest), stored/applied as a unit by
  `se/pack` (ADR-0023).
- **Legacy overlay / mega-jar** — the 1.8.9 build mechanism: a
  `-Pse.target=legacy` srcDir overlay of same-FQN whole-file swaps compiled
  against a real 1.8.8 jar and lowered 61→52, merged with the modern tree into
  ONE Multi-Release release jar (soundness-gated), so one download runs on both
  eras (ADR-0036).
