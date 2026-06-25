# Rendering item tooltips & GUIs to images (README screenshots)

> **Status: design proposal (not yet built).** This captures a fully-fleshed
> approach so it's durable; the generator itself lands in a dedicated follow-up.

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
