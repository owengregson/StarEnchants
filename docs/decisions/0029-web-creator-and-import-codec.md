# ADR 0029: Web enchant creator + `SE1` paste-code import (`/se import`)

- **Status:** Accepted
- **Date:** 2026-06-25
- **Deciders:** project owner + agent
- **Relates to:** ADR 0028 (docs site + `catalog.json`), ADR 0014 (content loader & transactional
  reload), ADR 0023 (config packs), `compile.*` (the compiler + diagnostics), `/se reload`

## Context

The documentation site (ADR 0028) should let an operator **build or edit an enchant in a web GUI** and
then apply it to a live server with a single in-game command — "the same as if they had edited the YAML
and run `/se reload`." The site is static (GitHub Pages, no backend), so the result must travel as a
self-contained, copy-paste-safe **code**, not via a server call.

## Decision

**A React creator page on the docs site emits a versioned `SE1:` import code; in-game `/se import
<code>` decodes it, validates it through the existing compiler, and — only if clean — writes the
content file and hot-swaps the snapshot exactly like `/se reload`.**

### The creator (web)

- A form driven entirely by `catalog.json` (ADR 0028): pick tier (from `tiers.yml`'s set), display
  name, description, `applies-to`, `group`, `trigger`; add/reorder/remove effects, each rendering the
  right input per param (number with min/max, enum dropdown, handle autocomplete, bool, text) from the
  catalog's param schema; per-level `chance` / `cooldown` / `souls` / `condition`; multiple levels.
- **Edit an existing enchant**: paste an `SE1:` code (or load a starter template) → the form
  re-populates → modify → re-emit.
- Live **YAML preview** of the exact `content/enchants/<key>.yml` it will produce, plus the
  **`/se import …` code** with a copy button. For an unusually long code, also offer a **download
  `.yml`** fallback (drop-in + `/se reload`).
- Client-side validation against the catalog (types/ranges/enums/required) so emitted YAML is valid.

### The `SE1` code (the contract — both sides MUST match byte-for-byte)

```
SE1:<base64url-nopad( zlib-deflate( utf8( envelope ) ) )>
```

- **Prefix** `SE1:` — StarEnchants import, format **version 1**. An unknown prefix → friendly error.
- **Compression**: zlib (DEFLATE with the standard zlib header/adler32). JS: `pako.deflate` / Java:
  `java.util.zip.Deflater` default + `Inflater` default — these are wire-compatible.
- **base64url without padding** (`A–Z a–z 0–9 - _`, no `=`): command- and chat-safe. JS encodes
  url-safe; Java uses `Base64.getUrlEncoder().withoutPadding()` / `getUrlDecoder()`.
- **Envelope**: a JSON object (YAML is a JSON superset, so the plugin parses it with SnakeYAML — no new
  JSON dependency):

  ```json
  { "v": 1, "kind": "enchant", "key": "frostbite", "content": { "...": "the enchant def map" } }
  ```

  - `kind`: `enchant` for v1 (the format reserves `set` / `crystal` / `item` for later).
  - `key`: the content key → filename. Sanitized to `[a-z0-9-]+` on BOTH sides; a key that escapes is
    rejected (no path traversal).
  - `content`: the def map in the exact on-disk schema (`tier`, `display`, `description`, `trigger`,
    `applies-to`, `group`, `levels: { N: { chance, cooldown?, souls?, condition?, effects: [...] } }`).
    Effects use the named-arg map form (`{ POTION: { effect: SLOWNESS, level: 1, ... } }`).

### `/se import` (plugin)

1. Permission: same gate as the rest of `/se` (`starenchants.admin`, default op).
2. Decode: strip `SE1:` → base64url-decode → inflate → parse envelope. Any failure → a clear,
   actionable error (bad/old code), nothing written.
3. **Validate before touching disk**: compile a candidate snapshot that includes `content` (reusing the
   compiler + `Diagnostics`, the same path as `/se reload --dry-run`). On errors, report them and
   abort — disk untouched.
4. On clean diagnostics: serialize `content` to YAML and write `content/enchants/<key>.yml` (overwrite
   if present, so "modify an existing one" works), then hot-swap the snapshot by reference — identical
   to `/se reload`. Confirm with the key + level count.
5. Folia: the reload/hot-swap goes through the existing `ContentReloader` (off-thread build, global
   swap), no new scheduling.

Length note: deflate keeps a typical enchant a few hundred chars — within a paste into chat. For very
long codes the creator's `.yml` download is the fallback; a future `SE2`/book-paste GUI can lift the
limit without breaking `SE1`.

## Verification

- **Java unit test**: `SE1` round-trips (encode in test ↔ decode), key sanitization rejects traversal,
  an invalid `content` aborts with diagnostics and writes nothing.
- **JS unit test**: the creator's encoder produces a code the spec's decoder reads back identically
  (and a fixed `SE1:` vector decodes to the expected envelope — pins JS↔Java agreement).
- **Live suite**: import a known `SE1` code in-server, assert the file exists and the enchant is active
  (appliable, fires) after the auto-reload; a malformed code changes nothing.

## Consequences

- Authoring an enchant needs no YAML knowledge; the round-trip (`SE1` in ↔ out) makes the web GUI a
  real editor, not just a generator.
- `pako` is the only new site dependency. The plugin gains one command + one small codec class; no new
  Java dependency (SnakeYAML + `java.util.zip` already present).
- The codec is versioned, so the format can evolve without invalidating the command.

## Alternatives considered

- **Download `.yml` + `/se reload` only.** Simplest, zero codec — kept as the fallback, but it is a
  file drop, not the one-command import the owner asked for.
- **Share-link fetch (`/se import <id>` over HTTP).** Best for huge configs but needs a hosting
  endpoint + network + a fetch path on a security-sensitive command. Rejected for v1.
- **Raw (un-deflated) base64.** Simpler but ~2–3× longer codes; deflate is cheap and present on both
  sides.
