# Cosmic port — decomposition matrix

Working documents for the cosmic-suite port
(spec: `../../superpowers/specs/2026-08-01-cosmic-suite-port-redo-design.md`).
One matrix doc per codex category; one entry per ported item. The behavioral
authority is the local-only codex (gitignored); entries cite codex documents by
filename and never quote decompiled code.

## Entry format

```markdown
### <Display Name> (`<family>/<slug>`)
- **codex:** `<codex doc> § <entry>`
- **activation:** trigger + condition/selector mapping (existing vocabulary names)
- **decomposition:** ordered `KIND(param=value, …)` sequence using ONLY primitives
  present in `docs/reference/authoring-surface.txt` at HEAD
- **gaps:** `NAME — semantics; params; consumers` for capabilities the surface
  lacks. Names describe the capability, never an enchant (`ATTACKER_INDEX_WINDOW`,
  not `AEGIS_EFFECT`). Omit the line when fully expressible.
- **interactions:** stacking / suppression / cross-feature rules touched
- **strings:** verbatim display strings (placeholders → brace tokens only)
- **numbers:** per-level values; known-bug status with the as-intended value and a
  `deviations.md` row id when deviating from measured jar behavior
- **era:** 1.8.9 hazards (materials/sounds/particles/API), noted for the legacy sweep
```

## Files

| Doc | Source codex doc | Items |
| --- | --- | ---: |
| `matrix/01-enchants-armor-a-l.md` | 01 | 36 |
| `matrix/02-enchants-armor-m-z.md` | 02 | 31 |
| `matrix/03-enchants-swords.md` | 03 | 36 |
| `matrix/04-enchants-axes.md` | 04 | 21 |
| `matrix/05-enchants-bows.md` | 05 | 22 |
| `matrix/06-enchants-tools-heroic.md` | 06 | 36 |
| `matrix/07-enchants-mastery-soul.md` | 07 | 16 |
| `matrix/10-armor-sets.md` | 10 | 12 |
| `matrix/11-masks.md` | 11 | 27 |
| `matrix/12-pets.md` | 12 | 17 |

`proposed-primitives.md` clusters every gap into the minimal general primitive set
(spec §4 bar) and assigns each to engine wave 1 or 2.
