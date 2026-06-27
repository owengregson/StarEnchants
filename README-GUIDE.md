# StarEnchants README — Modification Guide

This guide explains how the README is built and how to change any part of it.
It's written so a person **or a coding agent** can edit confidently without breaking things.

---

## 1. How this README works (read this first)

GitHub **strips `<style>`, CSS classes, and inline `style="…"`** from rendered READMEs.
So none of the visual styling can live in the Markdown. Instead:

- **All visuals are committed SVG files** in [`assets/`](assets/) — the hero banner, the
  section-header "pills", the feature icons, the buttons, the divider. Each SVG carries its
  own colors, gradients, shapes, and text.
- **`README.md` is plain Markdown + a little safe HTML** (`<p align>`, `<img>`, `<picture>`,
  `<table>`, `<a>`). It just places the images and holds the body copy.

> **Rule of thumb:** edit **words & structure** in `README.md`; edit **looks** in the `assets/*.svg`.

GitHub-safe HTML you may use in `README.md`: `<p align>`, `<div align>`, `<img>`, `<picture>`/`<source>`,
`<a>`, `<table>/<tr>/<td>`, `<b> <i> <sub> <sup> <br> <code> <kbd> <details>/<summary>`.
**Never** add `style=`, `class=`, `<style>`, or `<script>` — GitHub deletes them.

---

## 2. Asset manifest

| File | Size | Purpose | Theme-adaptive? |
| :-- | :-- | :-- | :-- |
| `assets/hero.svg` | 1200×448 | Main hero banner (light / default) | shown on light |
| `assets/hero-dark.svg` | 1200×448 | Hero banner tuned for GitHub dark | shown on dark |
| `assets/logo.svg` | 420×172 | Stand-alone logo lockup (icon + wordmark) | works on both |
| `assets/headers/*.svg` | ~210–436×72 | Section-header pills (one per section) | works on both |
| `assets/icons/*.svg` | 44×44 | Feature icons (gradient chip + glyph) | works on both |
| `assets/buttons/download.svg` | 234×52 | Gold "Download the JAR" button | works on both |
| `assets/buttons/releases.svg` | 181×52 | Lavender "All releases" button | works on both |
| `assets/divider.svg` | 220×24 | Footer sparkle divider | works on both |

`headers/` files: `features, installation, commands, configuration, compatibility, integrations, building, license`.
`icons/` files: `effect, economy, armor, gui, integrations, migrator`.

---

## 3. The most common edits

### ✏️ Change body text (features, steps, command table, config tree)

All of it is Markdown in `README.md`. Edit in place:

- **Features** → the `<table>` after the `features` header. Each cell is `icon` + `<b>Title</b>` + description.
- **Install steps** → the numbered list under the `installation` header.
- **Commands** → the Markdown table under the `commands` header (`| Command | What it does | Permission |`).
- **Config tree** → the ```` ```text ```` block under the `configuration` header.

### 🔗 Change the repo / download / version

The repo URL appears in `README.md` (buttons + links). The supported **version range** appears in the
`paper` badge of `assets/hero.svg` and `assets/hero-dark.svg` (text `1.8, 1.17.1-26.1.2`).

- Find-and-replace `owengregson/StarEnchants` everywhere to point at a different repo.
- To bump the version, edit the `v1.0.0-beta` `<text>` in **both** hero SVGs (see §5).

### 🏷️ Change a section title

Open `assets/headers/<name>.svg`. Near the bottom is one line:

```xml
<text x="72" y="37" … >Commands &amp; permissions</text>
```

Edit the text. **If the new title is noticeably longer/shorter**, also widen/narrow the pill so it
isn't clipped or over-padded: bump the three numbers that depend on width — the root `width=` /
`viewBox` last value, and the pill `<rect … width=>`. Pills are **balanced**: the gap from the pill's
left edge to the icon chip (12px) equals the gap from the title's right edge to the pill's right edge.
Widths come from the measured text-ink advance (Trebuchet MS Bold @25px) with the title at `x=72`.
Rough formula: `pillWidth ≈ 78 + (titleChars × 12.5)`, and `svgWidth ≈ pillWidth + 12`. (Or just
regenerate, §7.)

---

## 4. Color palette

Every asset uses this palette. To re-theme, change these hexes wherever they appear in the SVGs.

| Role | Hex(es) |
| :-- | :-- |
| Candy pink (primary) | `#FF9FCF` |
| Enchant violet (primary) | `#B98CFF` · `#C79BFF` |
| Star gold (accent) | `#FFD86B` · `#FFC75A` · `#FFE9A8` |
| Glint blue (accent) | `#9FE3FF` · `#8FD0FF` |
| Deep plum (titles) | `#5B3E8E` · `#4A2C7A` |
| Soft plum (body text) | `#7B6BA0` |
| Hero card gradient | `#6E3F9E` → `#4A2C7A` → `#2A1B4A` |
| Hero wordmark gradient | `#FFC4E6` → `#FFE9A8` → `#C9B6FF` |
| Header pill gradient | `#FFD9EC` → `#EAD9FF` |
| Icon-chip gradient | `#FF9FCF` → `#B98CFF` |

Gradients are defined inside each SVG's `<defs><linearGradient>` / `<radialGradient>` — edit the
`stop-color` values there.

---

## 5. Editing the hero

`assets/hero.svg` (and its near-identical twin `assets/hero-dark.svg`) contain, top to bottom:

1. **`<defs>`** — the card gradient (`card`), wordmark gradient (`wm`), and emblem gradients (`h…`).
2. **The card** — `<rect … fill="url(#card)">` plus two thin frame rects.
3. **Corner & scatter sparkles** — small `<path>` stars.
4. **The emblem** — a `<g>` of star paths.
5. **Wordmark** — `<text … fill="url(#wm)">StarEnchants</text>`.
6. **Tagline / sub-tagline** — three `<text>` lines.
7. **Badge row** — four pills, each a white `<rect>` + a colored left `<path>` + two `<text>` labels
   (`paper 1.8, 1.17.1-26.1.2`, `folia supported`, `java 8, 17+`, `license AGPL 3.0`).

Edit `<text>` values to change wording; edit fills to recolor. The badge pills are **positioned by
hand** (x coordinates run left→right), so changing a label's length will misalign the row — for badge
changes it's easiest to **regenerate** (§7). Keep `hero.svg` and `hero-dark.svg` in sync; the only
differences are the dark version's brighter gold frame and a soft outer glow ring.

---

## 6. Changing an icon

Each `assets/icons/<name>.svg` is a 44×44 gradient chip with a white glyph drawn in a 24-unit grid,
scaled and centered by a `<g transform>`. To swap a glyph, replace the inner `<path>`/`<circle>` markup
with any 24×24 line icon (keep `stroke="#fff"` / `fill="#fff"`). To recolor the chip, edit the two
`stop-color`s in its `<linearGradient>`.

The section-header pills embed the **same** glyph set (the `<g transform>` block inside each header SVG).

---

## 7. Regenerating the templated assets (optional)

The headers, feature icons, buttons, and hero were produced from small scripts so they stay
consistent. You don't need them for small text/color edits — just edit the SVG. But if you're adding
many sections or changing the system, regenerate from these source-of-truth lists:

```js
// Section headers:  [id, title, iconName]
const headers = [
  ['features','Features','sparkle'], ['installation','Installation','download'],
  ['commands','Commands & permissions','terminal'], ['configuration','Configuration','gear'],
  ['compatibility','Cross-version & Folia','layers'], ['integrations','Integrations','link'],
  ['building','Building from source','code'], ['license','License','scroll'],
];
// Feature icons:    [id, iconName, gradientFrom, gradientTo]
const feats = [
  ['effect','effect','#FF9FCF','#B98CFF'], ['economy','gem','#FFD06A','#FF9FCF'],
  ['armor','shield','#B98CFF','#8FD0FF'], ['gui','window','#8FD0FF','#FFB0D9'],
  ['integrations','link','#FF9FB6','#FFD06A'], ['migrator','import','#A98CFF','#FF9FCF'],
];
```

Each header pill = rounded-rect (gradient `#FFD9EC→#EAD9FF`) + 40px icon chip (`#FF9FCF→#B98CFF`) +
title (`#5B3E8E`, bold, 25px). Each icon = 40px rounded chip + white 24-grid glyph.

---

## 8. Adding a new section

1. Create a header pill: copy an existing `assets/headers/*.svg`, rename it, change the `<text>` title
   (and width per §3), and swap the glyph if you like.
2. In `README.md`, add:

   ```html
   <p align="center"><img src="assets/headers/your-section.svg" height="54" alt="Your section"></p>
   ```

   then your Markdown body beneath it. Add `<br>` before the header for spacing, matching the others.

---

## 9. Light / dark behavior

- The **hero** swaps automatically via `<picture>` in `README.md`:

  ```html
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/hero-dark.svg">
    <img src="assets/hero.svg" width="860" alt="…">
  </picture>
  ```

- **Everything else is theme-agnostic**: the pills, icons, and buttons are self-contained colored
  shapes on a transparent background, so they look right on both GitHub themes without a swap.
- Want a dark variant of any other asset? Make `name-dark.svg` and wrap that `<img>` in the same
  `<picture>` pattern.

---

## 10. Fonts

SVGs rendered as `<img>` on GitHub use the **viewer's system fonts**, so the assets use a friendly
sans-serif stack (`Trebuchet MS → Segoe UI → system-ui → …`) with `Georgia` italic for the tagline.
The brand display fonts in the original design are **Fredoka** (wordmark) and **Quicksand** (titles).
For pixel-perfect brand type you'd embed the font as base64 `@font-face` inside each SVG, or convert
the headline text to vector outlines — not required, and it bloats the files, so the system stack is
the sensible default.

---

## 11. Swapping in live badges (optional)

The hero's badge row is baked-in (static, on-theme). If you'd rather have **live** badges (auto
version, build status, download counts), replace the row with [shields.io](https://shields.io) images
in `README.md`, e.g.:

```md
![release](https://img.shields.io/github/v/release/owengregson/StarEnchants?color=C79BFF)
```

Pass `?color=` / `&labelColor=` with the palette hexes (no `#`) to keep them on-theme. You can then
remove the badge `<rect>`/`<text>` block from the hero SVGs.

---

## 12. Gotchas

- ✅ Use relative asset paths (`assets/…`) — they resolve on the default branch.
- ❌ No `style=`, `class`, `<style>`, `<script>` in `README.md` (GitHub removes them).
- 🔤 In **HTML** parts (`<p>`, `<td>`, `alt="…"`) write `&amp;` for an ampersand. In **Markdown** tables,
  lists, and code, a plain `&` is fine.
- 🖼️ Inline `<svg>` in Markdown is stripped — SVGs must be referenced via `<img src>`.
- 🔁 Keep `hero.svg` and `hero-dark.svg` in sync when editing hero content.
