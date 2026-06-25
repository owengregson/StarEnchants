# Rendering item tooltips & GUIs to images (README screenshots)

> **Status: BUILT.** The generator described below shipped as the tool-only
> `se/imagegen` module. Run it with `./gradlew :imagegen:renderImages`; output lands
> in `website/static/img/renders/`. The rest of this doc is the design rationale; the
> "Implemented" section at the end records what actually shipped and how to use it.

We want committable images for the README showing (a) item **tooltips/lore** as
they look after enchants/books/crystals are applied, and (b) the plugin's
**inventory GUIs/menus**. Two families were researched.

## Verdict

Build a small **Java2D (`BufferedImage`) generator** that consumes the plugin's
*own* rendered output — it reuses real code paths, runs deterministically in CI
across all versions, needs no server and no client, and is fully committable for
tooltips. A real-client capture is only worth it as a one-off manual step for a
couple of "hero" shots.

## Why not a real client

Driving an actual Minecraft client *can* render pixel-accurate tooltips/GUIs
headless — a **Fabric client game-test** with `useXVFB` + `takeScreenshot`,
rendered by Mesa **`llvmpipe`** (software OpenGL 4.5, no GPU; MC needs only GL
3.2). Mojang's [Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines)
permit screenshots for non-commercial use. **But it's impractical as maintained
CI for this repo:**

- The client-test harness exists only on a *moving subset* of recent versions —
  it does **not** cover our 1.17.1 floor and rides Yarn mappings + Mixins that
  break every version. A second cross-version treadmill, off-axis from a
  server-only plugin.
- It's an entirely separate toolchain (Fabric mod + Mixins + a multi-version
  preprocessor) divorced from our clientless harness (ADR 0018), plus account
  and determinism friction.
- `prismarine`/`mineflayer` is **disqualified** — no tooltip renderer, headless
  inventory export unimplemented. Replay Mod / MCEF / Carpet are the wrong tools.

**If pixel-perfect "hero" art is ever wanted:** one-off manual capture with a
real vanilla client (join a test server, `/se give …`, hover/open, F2). Trivial
full-version reach, zero new toolchain, but not reproducible automation.

## The recommended generator

A new **tool source set `se/imagegen`** (like `se/tester`, *not* shipped in the
plugin jar), depending on `se/item` and `se/feature`, run by a Gradle task. It
renders from the same code the plugin uses, so screenshots can't drift:

- **Tooltips** — `LoreRenderer.lines(CombatState)` already returns a server-free
  `List<String>` of legacy §-coded lines; `Colors` enumerates the valid codes.
  Parse §-runs → styled runs (rgb + bold/italic/underline/strike/obfuscated),
  measure & draw with the **OFL-1.1 `IdreesInc/Minecraft-Font`**, render the
  vanilla tooltip box (1px dark frame, the `#5000FF → #28007F` purple gradient
  border, dark fill), per-glyph `(+1,+1)` shadow, NEAREST upscale ×N → PNG.
  **Zero Mojang assets required — fully clean to commit.**
- **GUIs/menus** — `MenuLayout` is the base-9 grid (rows×9, filler, nav slots).
  Composite the standard `generic_54` chest background (176px wide, 18px slot
  pitch, 16×16 item sprites inset 1px) and blit each slot's icon; optionally
  overlay a hovered-item tooltip via the tooltip renderer.

### Inputs

Deterministic fixtures in-repo: item material + §display-name + `CombatState`
(enchants/crystals/heroic/set) for tooltips; `MenuLayout` + slot→(material,
§name, §lore) for menus.

### Licensing (decisive)

- **Fonts:** commit `IdreesInc/Minecraft-Font` (OFL-1.1) under its own dir +
  license file; GNU Unifont (GPL+FE / OFL) only if non-Latin-1 glyphs appear.
  Do **not** use Mojang's own `default`/Mojangles font (all-rights-reserved).
- **Item sprites / GUI background are Mojang-owned** — re-hosting mirrors do not
  grant redistribution. **Do not commit extracted textures.** Fetch them at
  generation time (a CDN or a local `$MC_ASSETS_DIR`) and commit only the
  *composite* PNG, which is screenshot-equivalent (permitted), with a
  **"NOT AN OFFICIAL MINECRAFT PRODUCT"** disclaimer in the README. A fully
  asset-free option: render GUI layouts with OFL-font labels + panes, no sprites.

### Determinism guards

Fixed seed (or a static masked glyph) for `§k` obfuscated; font pinned by hash;
antialiasing off (pixel-exact bitmap font); fixed canvas + scale; NEAREST
interpolation; a golden-image diff check in CI that fails if committed PNGs
drift.

### Caveat

Synthetic output is ~99% but not pixel-identical to a live client (subtle
kerning, exact gradient stops, no enchanted-glint/3D block-model lighting).
Block-material menu icons would need isometric compositing; flat sprites are
correct only for items. For the README's needs this is an acceptable trade for
committability, speed, all-version coverage, and reuse of real plugin output.

## Implemented

The generator shipped as **`se/imagegen`** — a tool-only module (like `se/tester`,
never shaded into the plugin jar). Run it:

```bash
./gradlew :imagegen:renderImages          # → website/static/img/renders/*.png
```

Layering (each piece is its own small, testable unit):

- **`Canvas` / `Argb`** — an explicit ARGB raster with alpha-over compositing and
  integer NEAREST upscale, so output is byte-deterministic (no Java2D colour
  management leaks in). Java2D is touched only for PNG encode.
- **`text/`** — `LegacyText` splits a `§`/`&`-coded line into styled runs; `McColors`
  holds the 16 colours + the 25% shadow.
- **`font/MinecraftFont`** — renders the **real** Minecraft `default` font from the
  game's own `font/ascii.png` glyph atlas (supplied by `VanillaAssets`), using
  Minecraft's own width rule (advance = rightmost lit column + 2px; space = 4). Glyph
  shapes and spacing match the game exactly. The atlas is fetched at generation time
  and never committed — same posture as the item textures.
- **`assets/VanillaAssets`** — resolves vanilla item/block models + textures from a
  local `MC_ASSETS_DIR` (a client jar or an unpacked `assets/`), else lazily downloads
  a pinned client jar (default `1.21.1`, override `-Dse.imagegen.mcVersion=`) into
  `build/imagegen/assets/` (gitignored). Walks the model parent chain → FLAT layers or
  a full-cube BLOCK.
- **`render/tooltip`** — the vanilla tooltip box (`0xF0100010` fill, `0x505000FF`→
  `0x5028007F` gradient border) with per-glyph `(+1,+1)` shadow.
- **`render/sprite`** + **`render/block`** — flat item sprites; isometric (2:1 dimetric)
  full-cube blocks with vanilla face shading (top 1.0 / sides 0.80 / 0.62).
- **`render/gui/ChestRenderer`** — the beveled chest panel + recessed slots drawn from
  Minecraft's inventory palette (so GUIs render right even with no assets), real item
  icons, stack counts, and the hovered-slot tooltip overlaid.
- **`fixture/Fixtures`** — the deterministic preview set. **Tooltips reuse the plugin's
  own `item.render.LoreRenderer`**, so a rendered tooltip is exactly the lore the live
  plugin stamps — the previews cannot drift from the engine.

### Licensing, as built

- **No Mojang assets are committed** — neither item/GUI textures nor the `font/ascii.png`
  glyph atlas. Everything is fetched at generation time from a vanilla client jar (cached
  under the gitignored `build/`), and only the *composite* PNGs (screenshot-equivalent,
  permitted for non-commercial use) are committed, each shown with a **"NOT AN OFFICIAL
  MINECRAFT PRODUCT"** disclaimer (see the docs intro page).

### Not wired into `./gradlew build`

`renderImages` is opt-in: accurate sprites need vanilla assets fetched at run time, so
making the core build depend on them would break hermeticity. The committed PNGs are the
artifact; regenerate them deliberately when content/display changes.
