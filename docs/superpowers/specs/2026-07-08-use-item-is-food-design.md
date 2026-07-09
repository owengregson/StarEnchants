# Use-item `is-food` — design spec

**Date:** 2026-07-08
**Feature:** an `is-food` field on the right-click use-item family (ADR-0048) that
requires the player to *eat* the item (real eating animation) before its abilities
fire, and forces the item to be edible regardless of its material.
**Status:** approved; ready for implementation.

## Decisions (locked)

1. **Below 1.20.5, `is-food` is a no-op.** Forcing an arbitrary material to be
   edible needs the food/consumable data-component API, which is 1.20.5+. On
   1.17.1–1.20.4 and on 1.8.9 legacy, an `is-food` item behaves as an ordinary
   one-click use-item (abilities fire on the right-click, exactly like today).
   One rule: *the eat-gesture requires 1.20.5+.*
2. **Eating is a pure trigger gesture — no hunger/saturation.** The forced food
   component carries nutrition 0, saturation 0, and can-always-eat true (a
   full-hunger player can still trigger it). Vanilla food side effects are
   suppressed by cancelling the consume event; we control item consumption.

## Authoring surface

`content/use-items/<key>.yml` gains one optional key:

```yaml
is-food: true    # default false
```

- Kebab key, parsed with `ContentParse.boolOr(..., default=false, W_LOAD_BOOL)` —
  same shape as `consumable`/`shiny`; a bad value warns and falls back.
- Added to `UseItemDefReader.ROOT_KEYS` so it is not flagged unknown.
- Carried on `UseItemDef` as `boolean isFood`.

**Composition with `consumable`:** `is-food` decides *how you trigger* (eat vs
click); `consumable` decides *whether one item is removed on a successful use*.
So `is-food: true` + `consumable: false` is a reusable item you eat each time
(kept, re-eatable after cooldown); `is-food: true` + `consumable: true` removes
one item per successful eat.

## The edible modifier (version-gated seam)

New platform seam `EdibleItems`, boot-resolved, modelled on the graceful-degrade
precedent (`MenuIcons.glow` / `VanillaEnchants`):

- **Enabled** iff `Capabilities.atLeast(1, 20, 5)`. Otherwise every method is a
  no-op (covers 1.17.1–1.20.4 and 1.8 legacy).
- `void makeEdible(ItemStack)` — when enabled, sets the item's food/consumable
  component so the client plays the real eating animation and the server fires
  `PlayerItemConsumeEvent`: **nutrition 0, saturation 0, can-always-eat true**,
  default vanilla eat duration.
- `boolean enabled()` — the runtime uses this to decide whether an `is-food` def
  actually eats (≥1.20.5) or degrades to one-click (below).

### Cross-version implementation note (the one real risk)

Data components are 1.20.5+, above the 1.17.1 compile floor, so the component
calls are **reflective**, resolved once at boot and cached. There is a real fork
at **1.21.2**, where Mojang split the `consumable` component out of `food`:

- **1.20.5 – 1.21.1:** a `food` component with `can-always-eat = true` makes the
  item consumable (animation + consume event).
- **1.21.2+ (incl. 26.1.x):** the `consumable` component is what enables eating;
  `food` alone only provides nutrition. The modifier sets the consumable
  component here.

Pin the exact Bukkit `ItemMeta` method names / builder shapes per version band
against the reference cache (`nms-archaeology`, `reference-cache`) **before**
writing the reflective calls. Also confirm that cancelling
`PlayerItemConsumeEvent` reliably suppresses both the nutrition gain and the
vanilla stack decrement on every ≥1.20.5 band. If a band cannot be made to
force-eat a non-food material, `EdibleItems.enabled()` must report false for it
(degrade to one-click), never silently half-work.

## Runtime (the eat gate) — `se/feature/src/feature/useitem`

Shared tail: extract the current interact-path "run `service.use`, render the
outcome switch, consume one item on `ACTIVATED` when `consumable`" into ONE
method (e.g. `UseItemService.activate` / a shared listener helper) so both entry
points use a single copy.

- **`UseItemListener.onInteract` (existing, `PlayerInteractEvent`):** for an
  `is-food` def **when `EdibleItems.enabled()`**, return early *without*
  cancelling — let the vanilla eat begin (cancelling would abort it before the
  animation). All other cases keep today's behavior: non-food items, and
  `is-food` on a sub-1.20.5 server, still claim the right-click (cancel) and fire
  abilities immediately. This is where "no-op below 1.20.5" falls out.
- **`UseItemConsumeListener` (new, `PlayerItemConsumeEvent`, priority LOW):**
  1. Identify `event.getItem()` as a use-item via the codec; resolve its def;
     re-check `isFood` defensively and the permission.
  2. `event.setCancelled(true)` — suppress vanilla nutrition + stack decrement.
  3. Run the shared activation tail (same pipeline, same feedback, same
     consume-one-on-`ACTIVATED`-when-`consumable` logic as the interact path).
  4. Match/consume the eaten stack (main-hand parity with the existing
     `consumeMainHand`; identity by codec key so we never charge a swapped item).
  - **Folia:** the consume event fires on the player's own region thread and we
    touch only their held item — correct, same as the interact path.

Register the consume listener always; on a sub-1.20.5 server it simply never
fires for our items (they carry no component, so no eat starts, and the interact
path claims the gesture). No behavior leak across the version boundary.

## Tests

- `UseItemDefReaderTest`: `is-food` parses true/false, defaults false, warns on a
  bad value; `ROOT_KEYS` still rejects genuinely-unknown keys.
- Mint: with a faked `EdibleItems` (enabled), an `is-food` mint is made edible;
  with it disabled, it is not — asserted at the seam boundary (no live server).
- `UseItemSuite` (live, `matrix-gate`): on a modern server, right-clicking an
  `is-food` item starts an eat and only the completed consume fires the abilities
  and removes one (when `consumable`); a `consumable: false` `is-food` item is
  kept; cooldown is respected across eats.
- Legacy: an `is-food` item on 1.8 behaves as a one-click use (no-op field).

## Out of scope (YAGNI)

Configurable eat duration, any nutrition option, and a new lore token. All
deferrable behind future fields if ever wanted.
