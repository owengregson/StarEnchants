## What & why
<!-- What does this PR change, and why? Link any issues (Closes #123). -->

## How
<!-- Key implementation notes and decisions. -->

## Cross-version & Folia

- [ ] Touches version-volatile APIs (Material/Sound/Particle/Enchantment/Attribute/PotionEffectType)? Resolved by name via the platform resolver — no hard enum references.
- [ ] Touches entities / world / inventory / timers? Goes through the scheduling abstraction (correct on Paper **and** Folia).
- [ ] N/A — no platform/threading surface touched.

## Testing

- [ ] Unit tests (pure logic) added/updated.
- [ ] Live-server suite covers the behavior on **Paper**.
- [ ] Live-server suite covers the behavior on **Folia**.
- [ ] Verified a fresh PASS (not just a green banner).

## Hygiene

- [ ] Conventional-commit messages; granular, rebased on `main`.
- [ ] Follows the project skills (`.claude/skills/`).
- [ ] Docs / CHANGELOG updated if user-facing.
