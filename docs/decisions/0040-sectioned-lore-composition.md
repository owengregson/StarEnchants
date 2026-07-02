# ADR 0040: Sectioned lore composition in one place

- **Status:** Accepted
- **Date:** 2026-07-02
- **Deciders:** StarEnchants maintainers

## Context

Lore is rendered from state, never parsed back (§4.2, ADR-0005). But the *composition*
of a finished item's lore was spread across four writers that coordinated through
**rendered-text prefix classifiers**:

- `LoreRenderer.apply` rendered the enchant body, set lore, orb slots line, crystal
  line(s), heroic line, and applied-scroll PROTECTED lines from state — then scanned
  the existing lore for **trak count lines** (matched by the count format's visible
  prefix) and re-appended them.
- `TrakService.renderTraks` re-stamped trak lines by dropping every line matching
  `isCountLine` and re-appending the applied ones.
- `SoulService.reRenderGem` re-rendered a gem's body from config while preserving any
  line matching a "protection-or-trak" predicate.
- `ScrollService` / `CarrierService` / `HolyScrollService` toggled the PROTECTED
  line(s) via `ProtectionLoreRefresh`, which dropped protection lines and preserved
  trak lines — again by prefix classifiers.

The classifiers were wired as closures in the composition root (`trakLineP`,
`protectionLineP`, `protectionLinesFn`, `protectionRefresh`) and handed to each
writer. The consequence: **ownership of a line was encoded in its visible text**, so
every template string became silently load-bearing — an operator who re-themed the
trak count format could break the classifier that identifies (and thus preserves) it.
Adding a new lore-bearing feature meant adding another writer plus another classifier.

There is also a `LoreRenderer` constructor ladder — twelve overloads chaining
positional template arguments — where the argument *order* is the contract and a new
section means a new rung.

## Decision

**Composition is a single, ordered, state-driven pass in one place.**

1. **`item.render.LoreComposer`** is the one composition entry point. It renders the
   full ordered lore for a piece of gear from `CombatState` + the injected templates,
   in a fixed order (the order **is** the contract): enchant body → set lore → orb
   slots line → crystal line(s) → heroic line → protection lines → trak lines. Each
   section is a small pure step reading state; `compose` takes the material kind, the
   already-computed protection lines, and the existing lore as plain values, so the
   whole composition is unit-testable with hand-built state (no server).
   `LoreRenderer` is the thin Bukkit shell that feeds the composer and writes the
   result back (plus the enchant-count name suffix).

2. **`LoreRenderer.Config`** — a record with **named fields** and a `Config.of(style,
   displayNameOf)` factory + fluent `with*` setters — replaces the twelve-constructor
   ladder. Every optional section defaults to a safe no-op. The composition root and
   the imagegen renderer build one `Config`; the single source of section defaults is
   the `Config.of` factory (used by unit tests and tools alike).

3. **Adding a lore-bearing feature is now local**: add a section step to the composer
   and a field to the state it reads — no new writer, no new classifier.

Output stability is non-negotiable: the composer was built by **extracting** the
existing rendering code, so for every item state expressible today the composed lore
is byte-identical (content and order). The live `RenderSuite` pins (e.g. `§7Venom
§fIII`) and the render goldens are unchanged.

## Consequences

**The composition foundation.** The full ordered composition lives in
`LoreComposer.compose`; `LoreRenderer.lines` delegates to `LoreComposer.body`; the
twelve-constructor ladder is replaced by a single `LoreRenderer.Config` record; all
call sites (composition root, imagegen, tests) build a `Config`. Every section —
including the applied-scroll PROTECTED lines and the applied-trak count lines — is
rendered from marker/counter **state** (`withProtectionLines` / `withTrakLines`), never
preserved by matching visible text. Byte-identity is pinned by `LoreComposerTest`
(body → heroic → protection → traks, in order) and the unchanged `LoreRendererTest` /
`RenderSuite`.

**The classifier retirement (landed).** `TrakService`, `SoulService`, and the white /
holy scroll services no longer mutate `ItemMeta` lore: they mutate PDC state and call
the one recompose seam (`LoreRenderer#apply`, wired as a `Consumer<ItemStack>` in the
composition root). `TrakService.countLines` renders the trak section from state and is
injected as the composer's `trakLines` provider; the trak/scroll writers' hand-stamped
lore edits (and `ProtectionLoreRefresh`) are gone. The load-bearing-template hazard is
removed: re-theming a count/protection format can no longer break a classifier on the
permanent path, because there is no classifier on the permanent path.

**The migration shim (one-release-scoped).** A **versioned composer marker**
(`ComposerMark`, a PDC integer via the `ItemKeys`/`ItemFlagStore` idiom) is stamped on
every `apply`. Its absence flags lore written by a pre-composer build; on such an
item's **first** recompose the legacy prefix classifiers run **once** inside
`LegacyLoreShim` (drop the recognised protection/trak lines and any line the fresh
render reproduces, keep genuine authored head above the composed sections), then the
marker is stamped. A marked item never consults the classifiers again — which is what
makes the shim **bounded** (marker-gated) and **non-looping** (the stamp is written
after compose). The classifiers (`ProtectionLore.isProtectionLine`,
`TrakService.isCountLine`) survive **only** as this shim's recogniser, injected as the
migration-only `legacyLoreLine` predicate. The shim exists solely to migrate
pre-composer lore and is removed a release after the marker ships (bump
`ComposerMark.VERSION` only if a future layout change needs a fresh pass).

## Alternatives considered

- **Keep the classifiers, just centralise the strings.** Rejected: the coupling
  (visible text = ownership) survives; re-theming a format still risks a mis-classify.
- **Store rendered ownership in the lore itself (hidden markers).** Rejected: lore is a
  projection of state (§4.2); a hidden marker is state smuggled into the projection, and
  Bukkit's `ItemMeta` colour normalisation already proved such markers fragile.
- **A builder instead of a record for the wiring.** The record + `with*` withers give
  named fields, immutability, and null-checked construction with less ceremony; the
  `Config.of` factory is the single source of defaults.
