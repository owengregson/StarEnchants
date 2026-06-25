# ADR 0028: GitBook-style documentation site, generated catalog, GitHub Pages

- **Status:** Accepted
- **Date:** 2026-06-25
- **Deciders:** project owner + agent
- **Supersedes:** the *human-facing* role of `docs/reference/dsl-reference.md` (that file stays,
  generated and drift-guarded, as the machine feed — see Decision)
- **Relates to:** ADR 0029 (web enchant creator + `/se import`), `engine.doc.ReferenceDoc` (§M
  drift guard), the in-game `ReferenceCatalog`

## Context

Server operators configure StarEnchants by hand-editing YAML — effects, triggers, selectors,
conditions, variables, tiers, items, sets, crystals. The only reference today is
`docs/reference/dsl-reference.md`: one long, terse, auto-generated page aimed at someone who already
knows the engine. There is no friendly, navigable, example-driven guide, and nothing an operator
would call "documentation." We want a real **GitBook-style documentation website** — left-nav,
search, per-page TOC — that explains the configurable surface in plain language, auto-publishes from
this repo, and never drifts from the engine.

## Decision

**Build a Docusaurus (TypeScript) site under `website/`, auto-deployed to GitHub Pages by its own CI,
with the DSL surface driven by a generated `catalog.json` so the docs cannot drift from the code.**

- **Toolchain: Docusaurus classic (TS).** Gives the GitBook-like UX out of the box (sidebar, search,
  right-rail TOC, dark mode) and — decisively — lets the interactive enchant creator (ADR 0029) be a
  first-class React page in the same site. Lives at repo-root `website/`; its authored content is
  `website/docs/**` (MDX). Repo-root `docs/` keeps the *internal* ADRs / architecture / dev docs —
  the two never mix.
- **Single source of truth: `website/src/data/catalog.json`.** A new `engine.doc.ReferenceCatalogJson`
  renders the five runtime vocabularies (effects, selectors, triggers, conditions, variables) to
  structured JSON — every effect/selector with its full param schema (kind, label, required, default,
  min/max, enum values, handle category, doc), targets, affinity, usage, example. It is **drift-guarded
  exactly like `dsl-reference.md`** (`ReferenceCatalogDriftTest`, regen with `-Dse.doc.regen=true`), so
  a newly-registered or changed kind fails `./gradlew build` until the catalog is regenerated. The
  reference MDX pages and the creator both read this one file.
- **`dsl-reference.md` stays** — still generated, still drift-guarded — as the machine/in-repo feed and
  a fallback. The website *supersedes it for humans*; we do not hand-maintain reference prose.
- **CI: `.github/workflows/docs.yml`.** On push to `main` touching `website/**` (and on manual
  dispatch): `npm ci` → `npm run build` → deploy with `actions/upload-pages-artifact` +
  `actions/deploy-pages` (`permissions: pages: write, id-token: write`). No `gh-pages` branch; uses the
  native Pages artifact flow. PRs build (no deploy) so a broken site fails the check.
- **Brand.** The site theme matches the existing pink→purple identity (`#FF9FCF`→`#B98CFF`, ink
  `#5B3E8E`); it reuses the `assets/` SVGs and expands that icon set to cover the documentation
  dictionary (per effect family, trigger, selector, item, concept) in the same visual language.

### Information architecture (sidebar)

1. **Introduction** — what StarEnchants is; install; your first enchant; `/se reload`.
2. **Concepts** — enchants, armor sets, crystals, heroic, souls, slots, tiers, groups (non-technical).
3. **Configuring content** — the YAML layout; defining an enchant (tier, display, applies-to, group,
   trigger, levels: chance/cooldown/souls/condition/effects); sets; crystals; items.
4. **DSL reference (friendly, catalog-driven)** — Effects (grouped: combat, movement, blocks, economy,
   utility, cosmetic), Triggers, Selectors/Targets, Conditions & Variables — each with plain-language
   description, params, and copy-paste examples.
5. **Items & economy** — books, scrolls, dust, soul gems, slot orbs, nametags, crystals.
6. **Commands & permissions** — the `/se` surface.
7. **Migrating from Cosmic Enchants** — `/se migrate`.
8. **Integrations** — WorldGuard/Towny/Vault/PAPI/… (all bundled, all optional).
9. **Enchantment Creator** — the interactive builder (ADR 0029).
10. **Cookbook** — complete worked examples.

## Verification

- `docs.yml` builds the site on every PR (deploy only from `main`); a build break fails the check.
- `ReferenceCatalogDriftTest` keeps `catalog.json` in lock-step with the engine (`./gradlew build`).
- `npm run build` is warning-clean (broken links fail Docusaurus' build by default).

## Consequences

- Operators get real, navigable, example-driven docs; the reference can never lie about the engine
  because it is generated from it.
- `node_modules/` and `website/build/` are gitignored; `package-lock.json` is committed (CI uses
  `npm ci`).
- Adding/altering an effect now has a third artifact to regenerate (catalog.json) alongside
  dsl-reference.md — both behind the same `-Dse.doc.regen=true` flag and the same drift gate.

## Alternatives considered

- **VitePress / Honkit / mdBook.** All give the GitBook look; Docusaurus won for first-class React
  (the creator) + mature Pages deploy. Honkit/mdBook are weak for a complex interactive page.
- **Parse `dsl-reference.md` into the site at build time.** Fragile (markdown scraping). A structured
  `catalog.json` generated from the same registries is exact and reusable by the creator.
- **Real GitBook.com (hosted SaaS).** Off-repo, not CI-from-source, not free-form interactive.
