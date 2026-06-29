# ADR 0030: GUI overhaul — themed menu chrome, hubs, and GUI-first management

- **Status:** Accepted
- **Date:** 2026-06-29
- **Deciders:** project owner + agent
- **Relates to:** docs/v3-directives.md §K (shared menu framework + full menu set), §L (atomic
  config: `menus/`), ADR 0027 (custom-item tokens — nav buttons resolve through the same resolver),
  ADR 0028 (docs-site surface drift)

## Context

The §K menu framework shipped functional but plain: a single filler row on the bottom, bare
`ARROW`/`OAK_DOOR`/`BARRIER` nav buttons named "Previous/Next/Back/Close", and content packed from slot 0.
Only two of ten menus had a `menus/<name>.yml` override, and the only tunable surface was geometry
(rows, title, filler, four nav slots). Crucially, `/se` is `starenchants.admin`-gated, so the
"user-facing" merchant benches (Enchanter, Alchemist, Tinkerer) had **no path a normal player could
reach** — there was no user entry point at all.

The goal: make every menu a hand-crafted, highly configurable surface; make the GUI the **primary** way
to manage the plugin (mint anything, browse everything, drill into armour sets and mint each piece);
and give users their own clean entry to the actions they own.

## Decision

### 1. Themed, bordered, configurable chrome (one framework, every menu inherits it)

The decorative chrome is separated from geometry and made first-class:

- **`MenuLayout`** keeps geometry (rows, title, filler, nav slots) and gains a **`Frame`** (`NONE` /
  `BOTTOM` / `BORDER`). The frame decides which cells are decorative panes vs. paged content, so content
  slots are no longer "0..perPage-1" — `MenuLayout.contentSlotCount()` / `contentSlot(i)` are the single
  source of the content-cell mapping (a `BORDER` insets content inside a one-cell perimeter; the bottom
  row always hosts navigation). Tests and live suites read content position from here, never a literal.
- **`MenuTheme`** owns the look-only chrome: the four nav **`NavButton`**s (prev / next / back / close)
  and an info button, each a `(material token, name, lore)` resolved through `ItemFactory` — so a pack can
  even theme them with ItemsAdder/Oraxen custom-item tokens (ADR 0027). Defaults are tasteful
  (`« Previous Page`, `Next Page »`, `← Go Back`, `✖ Close`) with page numbers in the prev/next lore.
- **`MenuLayoutConfig`** (the `menus/<name>.yml` shape) gains optional theme fields (`frame`, per-button
  `material`/`name`, info-pane material/name). Every field stays optional and merges onto the programmatic
  default via `MenuLayout.from` / `MenuTheme.from`, preserving the atomic-reload, default-wins contract.

The **programmatic defaults are the beautiful defaults** — what a player sees out of the box, with no
config. The shipped `menus/*.yml` document every knob (one well-commented file per menu) so the surface is
"highly configurable" and self-explaining; an omitted file or field keeps the gorgeous default.

`ItemFactory` gains a conservative, cross-version **glow** (resolve a vanilla enchant by name via the
existing boot-wired `enchantResolver`, hide it with the floor-stable `ItemFlag.HIDE_ENCHANTS`) and
attribute-hiding for icons — opt-in, never throwing, dormant when the resolver misses.

### 2. Two hubs + an operator console (GUI-first management)

- **User Hub** (`hub`, no permission) — a player landing reachable by the new `/enchants` command:
  tiles for the Enchanter, Alchemist, Tinkerer benches, Godly Transmog, and read-only browsers
  (Enchants / Armour Sets / Crystals). Navigation between menus is in-GUI clicks (each user menu's
  `permission()` is `null`), so the admin gate on `/se` never blocks a user mid-flow.
- **Operator Console** (`console`, `starenchants.admin`) — the everything-hub reached by `/se menu`
  (the new no-arg default): Grant Books (admin browser), **Mint Items**, Armour Sets (drill + mint
  pieces), Crystals (mint), the Apply-Enchant tool, the DSL Reference, and a Reload button (right-click
  for a dry-run).
- **Mint Items** (`mint`, admin) — a curated grid of every mintable item (gem, orb, heroic, extractor,
  the scroll family, dust, white scroll, the four trak gems, unopened books per tier); a click mints to
  self. Data-driven (`MintCatalog`), so a new mintable kind is one row.

### 3. Armour sets and crystals become actionable

`SetsBrowserMenu` becomes a two-level drill: sets → pieces (`SetDef.armorMembers()` + `weapon()`), each
piece minted via `ItemEnchanter.mintSetPiece`. `CrystalsBrowserMenu` mints a crystal item on click.
Both stay openable by anyone (browsing is informational) but **mint only for `starenchants.admin`**,
checked at click time on `click.player()` — a non-admin sees the same rich preview without the grant.

### 4. Commands remain as aliases

Every `/se` give/mint/menu subcommand stays. The GUI is additive: it is the primary surface, the commands
the scriptable fallback. Heavy, destructive operations (pack apply, migrate, import) stay command-only by
design — surfaced as guidance, not buttons.

## Consequences

- The live `MenuSuite`/`GuiSuite` read content/input positions from the menu (`contentSlot`,
  `inputSlots`) instead of hardcoded slots, so the bordered re-layout cannot silently break them.
- `website/src/data/surface.json` is regenerated (the `menu` command description changed); the merchant
  benches gain a real user path via `/enchants` (permission `starenchants.use`, default true).
- Out of scope (unchanged): the item-nametag chat-capture flow, GKits/crates/crafting (§K exclusions).
