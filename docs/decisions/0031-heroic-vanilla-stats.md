# ADR 0031: Heroic armour/durability as real vanilla stats (amends 0021's armour slice)

- **Status:** Accepted
- **Date:** 2026-06-30
- **Deciders:** project owner + engine work
- **Relates to:** ADR 0021 (heroic diamond-equivalence via plugin maths) — this overrides it for the ARMOUR +
  DURABILITY base stats; ADR 0026 (Mental knockback coordination); docs/v3-directives.md §F

## Context

ADR 0021 makes a heroic piece *function* as diamond while *displaying* as a weaker material (a gold twin) by
folding the diamond base-stat delta into the plugin's OWN combat maths (`HeroicDiamond` flat damage/reduction +
a wear-cancel that scales so a gold piece lasts like diamond). It deliberately uses **no item-attribute API** —
version-uniform, and invisible to the vanilla `GENERIC_ARMOR` attribute and the item's real max durability.

That invisibility breaks when another combat plugin recomputes from vanilla values. The concrete report:
**Mental** (the 1.8-combat plugin we already coordinate knockback with, ADR 0026) has a "restore 1.8
armour/durability" mode that recomputes damage from the player's **vanilla armour points** and item **max
durability**. A heroic gold piece carries gold's points (low) and gold's max (32), so under Mental it protects
and lasts like gold — our plugin-maths diamond-equivalence lives only inside our own damage path, which Mental's
recompute bypasses. The same invisibility shows the wrong armour on the HUD for everyone, Mental or not.

## Decision

When `vanilla-stats` is on (heroic.yml, default **true**), a heroic **ARMOUR** piece carries **REAL vanilla
armour-point + toughness attribute modifiers** (diamond values for its slot, replacing the weak display
material's defaults) and — where the platform supports it (Minecraft **1.20.5+**, `Damageable.setMaxDamage`) — a
**real diamond max durability**. The plugin-maths flat reduction is then **dropped** for that piece (the writer
returns whether it applied, and `HeroicService` zeroes the fold contribution so the two never double-count), and
the durability listener scales its wear-cancel off the item's **effective** max, so once a real diamond max is
set the scaling collapses to the base heroic buff.

- **Single source of truth = vanilla values.** Correct on the HUD, under vanilla armour, under Mental's 1.8
  restore, and for any plugin that reads vanilla armour/durability.
- **Armour-only.** The heroic **weapon** outgoing stays plugin-maths — an armour/durability restore does not
  touch attack damage, so there is nothing to reconcile there, and keeping it avoids re-specifying a weapon's
  full attack-damage + attack-speed default set just to add one modifier.
- **Toggle, default on.** `vanilla-stats: false` keeps the pure ADR-0021 behaviour. Best behaviour by default
  (correct armour points), with a documented escape hatch — the project's "divergences are opt-in" invariant.

## Cross-version edges (verified against the cached reference jars, never guessed)

- **Attribute resolve by registry key**, trying the modern key then the pre-1.21 `generic.` key
  (`armor`/`generic.armor`) — the key dropped its prefix at 1.21; `Registry.ATTRIBUTE` is on the 1.17.1 floor.
- **Modifier ctor** `AttributeModifier(UUID, name, amount, op, slot)` — undeprecated on the floor and still
  present at the ceiling (confirmed on 26.1.2), so it binds against the floor and runs across the whole range.
- **`setMaxDamage`/`hasMaxDamage`/`getMaxDamage` are 1.20.5+** (absent on the floor) → reflected behind a
  capability probe. On 1.17–1.20.4 there is no custom-max API, so durability falls back to the ADR-0021
  wear-cancel scaling (documented limitation; Mental's 1.8-durability restore is overwhelmingly a 1.20.5+
  server feature).
- **1.8.9 fork:** no Bukkit attribute API and no custom-max component → the writer is a same-FQN **no-op**, so
  1.8.9 keeps the plugin-maths fold + poll-restore. Era-specific degrade, per the legacy code-share design.

## Consequences

- Heroic armour points are now correct everywhere, resolving the Mental conflict and the wrong-HUD-armour issue.
- We re-introduce the item-attribute API that ADR 0021 avoided — but only for the armour base stats, only on
  platforms that have it, behind resolvers/capability probes (the project's "version-specific edges" rule), and
  only at forge time (not the hot path). The attribute lines are hidden (`HIDE_ATTRIBUTES`); the HEROIC lore
  stays canonical while the modifiers silently drive the HUD bar.
- Items already forged before the toggle flips keep whatever was baked in (attribute state lives on the item,
  not regenerated from PDC) — re-forge to change it. Acceptable and documented.
- Verified live on the Paper+Folia matrix (`HeroicVanillaStatsSuite`): a forged gold chestplate carries diamond
  armour + toughness on every version, and a diamond max durability on 1.20.5+.
