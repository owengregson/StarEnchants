# ADR 0032: Heroic cross-plugin combat parity (amends 0031's display slice)

- **Status:** Accepted
- **Date:** 2026-06-30
- **Deciders:** project owner + engine work
- **Relates to:** ADR 0031 (heroic vanilla-stats) — this amends its DISPLAY slice and extends it to weapons;
  ADR 0021 (heroic diamond-equivalence); ADR 0026 (Mental knockback coordination);
  spec `docs/superpowers/specs/2026-06-30-heroic-mental-combat-parity.md`; the companion Mental change
  (StrikeSync `combat:effective_material` contract)

## Context

ADR 0031 made a heroic ARMOUR piece carry real `GENERIC_ARMOR`+toughness modifiers, set `HIDE_ATTRIBUTES`,
and set a diamond `max_damage` component. Live use surfaced four gaps (root causes confirmed by reading
Mental's source, not guessed):

1. **Armour tooltip vanished** — `HIDE_ATTRIBUTES` hid the "+N Armor" line the owner wants shown.
2. **Weapon showed gold** — no attack attribute was written, so the tooltip rendered the gold material's
   attack.
3. **Weapon *dealt* gold on a Mental 1.8 server** — Mental's `DamageCalculator.legacyAttackDamage` keys off
   `weapon.getType()` (gold), ignoring the attribute entirely on that path.
4. **Weapon *broke* at gold-32 on a Mental 1.8 server** — Mental's `WeaponDurability` compared against the
   material max, ignoring the diamond `max_damage` component.

The owner asked that SE not hand-write attribute lore — the display should be **vanilla-rendered from real
attributes** — and stated the governing principle: *SE makes the gold piece a diamond of whatever the
current era is; **Mental** alone decides whether values are legacy, gated on its own restore modules.*

## Decision

**StarEnchants (era-agnostic):** when `vanilla-stats` is on, a heroic piece carries **real *modern* diamond
attribute modifiers** — armour-point+toughness for armour, `GENERIC_ATTACK_DAMAGE` (sword +6⇒7, axe +8⇒9)
for a sub-diamond weapon — a diamond `max_damage` component (1.20.5+), and the **vendor-neutral
`combat:effective_material` PDC marker** (STRING = the `DIAMOND_<kind>` it stands in for). `HIDE_ATTRIBUTES`
is **removed**: an explicit modifier set suppresses the display material's defaults, so the vanilla tooltip
renders exactly the diamond values (and drops the speed line for weapons). SE writes modern diamond
unconditionally and holds **no** era logic. The plugin-maths flat delta is dropped for a piece whose real
attribute was written (never double-counts). The marker + attributes are modern-only (1.8.9 has no PDC/
attribute API and no Mental; it keeps the plugin-maths approximation).

**Mental (companion, StrikeSync):** honours the marker only where its own restore modules elect it — reads
the effective material for legacy weapon damage (`legacy-tool-damage`), re-values the weapon `attack_damage`
tooltip to the era number and strips the toughness line (`old-armour-strength`) on the outgoing packet, and
breaks weapon durability against the `max_damage` component. Unmarked items / disabled modules are zero-touch.

## Consequences

- Display is **vanilla-rendered from real attributes** — no SE-authored attribute lore, no `HIDE_ATTRIBUTES`.
  Correct diamond-modern on vanilla + Mental-non-legacy; **live** era-correct on Mental-1.8 (no re-forge on a
  config change) because Mental transforms at packet time.
- A **vendor-neutral, documented contract** (`combat:effective_material`) any display-swapping plugin can
  stamp and any combat plugin can honour.
- **Two repos, two releases:** Mental ships first so its servers are fully correct the moment SE updates;
  SE is standalone-correct (diamond-modern) even before the Mental release.
- **Caveats (no silent caps):** the diamond `max_damage` component is 1.20.5+ — below it, durability keeps
  the wear-cancel scaling, which Mental's weapon fast path bypasses (documented gap). The heroic
  `percent-durability` +% *buff* does not apply to weapons on Mental's fast path (which bypasses
  `PlayerItemDamageEvent`); the component still guarantees diamond longevity there.

## Alternatives considered

- **SE writes era-matched attributes (SE owns the era).** SE auto-detects Mental's era and writes 1.8-shaped
  attributes directly. Rejected: bakes the era into the item at forge time (needs a re-forge when the server
  config changes), puts a Mental dependency/heuristic in SE, and shows a stale value if the config flips.
  The chosen split keeps SE trivial and the display live.
- **Keep `HIDE_ATTRIBUTES` + SE-authored lore lines.** Rejected by the owner: a vanilla-rendered tooltip from
  real modifiers is the single source of truth and avoids a second place to maintain the numbers.
