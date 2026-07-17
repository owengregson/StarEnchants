# ADR 0062: {#RRGGBB} hex colour tokens on every authored surface

- **Status:** Accepted
- **Date:** 2026-07-16
- **Deciders:** project owner + agent
- **Extends:** ADR-0033 (lang unification — `platform.text.Colors` as the one translate home),
  ADR-0044 (era erasure — the `EraServices` binding seam)
- **Relates to:** ADR-0034/0052/0053 (the `{COLOR}` token surfaces), ADR-0042 (error policy)

## Context

Authored YML (names, lore/descriptions, `color:` fields, lang messages, menu chrome) accepted only the 16
legacy `&`-codes. A raw `&x&R&R&G&G&B&B` escape happened to pass through `Colors.translate` (the `x` is in
its code set) but was undocumented, unvalidated, mis-measured by the universal lore wrap, and rendered as
garbage on the true-1.8.9 lane (each trailing hex digit is itself a 1.8 colour code, so the last digit
"won"). `compile.load.ChatColorRgb` already read `&#RRGGBB` — but only to tint the set-equip dust.

## Decision

1. **The token.** `{#RRGGBB}` (case-insensitive) is accepted everywhere `&`-codes are accepted, plus the
   `&{#RRGGBB}` spelling (so the documented `&{COLOR}` idiom keeps working when a `color:` value is a hex
   token) and `&#RRGGBB` (parity with the dust-tint reader). Tokens compose with `&l`/`&n`/… after them.
2. **One home.** The parse lives in `platform.text.Colors.translate` — the single ADR-0033 translate seam
   every renderer and message path already routes through — so every YML surface gains the format by
   construction, at render-prep time, never on the combat hot path.
3. **Era-selected runtime form.** A `Colors.HexMode` (installed once at boot from the new
   `EraServices.hexMode()` binding): `MODERN` emits the 1.16+ `§x§R§R§G§G§B§B` legacy-hex escape (correct
   across the whole 1.17.1+ range on the legacy-string `ItemMeta`/`sendMessage` render path); the 1.8.9
   binding returns `NEAREST_LEGACY`, which degrades each hex — token or authored `§x`/`&x` escape run — to
   the nearest of the 16 legacy colours by squared Euclidean RGB distance (tie → lowest code), reusing the
   `ChatColorRgb` palette.
4. **Malformed tokens stay literal, silently** — the stray-`&` rule extended to braces. `Colors` is a pure
   text utility with no diagnostics channel; it colours strings the compiler never sees (lang fragments,
   user-typed rename input), so a warn would spam per render and a diagnostic cannot be total (ADR-0042's
   never-throw stance).
5. **Width honesty.** `TextWrap` measures every hex construct as zero-width and carries it onto wrapped
   continuation lines (via the one public matcher `Colors.hexSpan`); `MenuText.truncate` never leaves a
   partial `§x` run under the pre-1.20 32-char title cap.

## Consequences

- Pack authors get true-colour branding on modern servers with a sane classic-colour fallback on 1.8.9,
  from one authored string.
- A hex title costs 14 characters of the pre-1.20 32-char inventory-title cap (a protocol limit, counted
  post-translate as before).
- `ChatColorRgb.isMultiColor` now case-folds hex, so the same colour in two spellings no longer
  false-triggers the rainbow equip dust.
- Items name-stamped on a modern server keep the `§x` form in their NBT; the enchant-count suffix strip is
  already tolerant of arbitrary `§`-runs, but cross-era world reuse otherwise remains out of scope.
