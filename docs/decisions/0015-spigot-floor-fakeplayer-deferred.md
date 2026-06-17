# ADR 0015: Spigot-floor fake-player harness deferred; v1 floor coverage is the non-fake suites

- **Status:** Superseded by [ADR 0018](0018-spigot-floor-fakeplayer.md) — the deferred follow-up
  (a spigot-mapped fake-player path) is now implemented and the combat suites run floor-wide.
- **Date:** 2026-06-16
- **Deciders:** project owner + engine work

## Context

The live test matrix boots a real Paper/Folia server per version and runs in-server
suites (ADR 0008; `live-server-testing`). The end-to-end **combat-path** suites
(combat, protection, economy, crystal, set, heroic, soul, trigger, menu) need a
**fake player** — a clientless NMS `ServerPlayer` with a void netty connection —
because a player-driven hit/equip/click is the only faithful way to exercise the
runtime spine against the real game.

The fake-player harness (`se-tester/fake/FakePlayers`) is built by reflection against
**Mojang-mapped** runtime names (`net.minecraft.server.level.ServerPlayer`, …). Paper
is mojang-mapped at runtime only from **1.20.5**; the floor (1.17.1–1.19.4) is
**spigot-mapped** — classes follow the Mojang package layout (the 1.17 rename) but
**methods and fields are obfuscated**, and each of 1.17.1 / 1.18.2 / 1.19.4 carries a
*different* obfuscation map. A clientless fake player there needs per-version
obfuscated-member reflection (or a runtime remapper) plus the same fragile void-channel
construction the mojang harness already calls out as the highest-risk step. That is a
large, fragile NMS effort whose every iteration costs a floor-server boot.

## Decision

Ship v1 with the fake-player harness **mojang-only** (1.20.5+), and gate the combat-path
suites behind `Capabilities.mojangMapped()` (as today). The spigot-floor fake-player is
an explicitly **deferred** test-infrastructure follow-up, not a v1 blocker.

The floor's v1 coverage is the **non-fake suites**, which DO run on 1.17.1–1.19.4 and
exercise every cross-version edge the combat path depends on: the Material/Sound/
Particle/Enchantment/PotionEffect/Attribute/EntityType resolvers (`ResolverSuite`,
`RuntimeHandlesSuite`), the catalog compiled against **real** handles on the floor
(`CatalogSuite`), the PDC codec + `ItemView` across the spigot↔mojang flip
(`ItemCodecSuite`, `ItemViewSuite`, `RenderSuite`), the `Scheduling` abstraction
(`SchedulingSuite`), content load/reload, and the apply path (`ApplySuite`). The combat
dispatch itself is version-agnostic Java over those floor-verified pieces.

## Consequences

- **v1 verification posture:** the plugin LOADS and all runnable suites PASS across the
  whole range — Paper 1.17.1 / 1.18.2 / 1.19.4 / 1.20.6 / 1.21.11 / 26.1.2 and Folia
  1.20.6 / 1.21.11 / 26.1.2. End-to-end combat-path verification runs on the mojang
  servers (both Paper and Folia, so the region-thread behaviour is covered) and on the
  floor only indirectly via its verified dependencies.
- **Residual risk:** a floor-only end-to-end combat regression that is NOT caught by the
  resolver/codec/scheduling suites would go unverified on 1.17.1–1.19.4. Low, given the
  dispatch is version-agnostic and its volatile edges are floor-tested — but real.
- **Follow-up:** add a spigot-mapped fake-player path (per-version member reflection or a
  bundled remapper), then drop the `mojangMapped()` gate so the combat suites run floor-wide.

## Alternatives considered

- **Build the spigot-floor fake player now.** Rejected for v1: a fragile, multi-version
  obfuscated-NMS effort that cannot be iterated or verified cheaply, for coverage whose
  dependencies are already floor-tested. Shipping fragile, thinly-verified NMS reflection
  would be worse than an honest, documented gap.
- **Drop floor support below 1.20.5.** Rejected: contradicts ADR 0002 (one universal jar,
  1.17.1→26.1.x). The plugin must and does run on the floor; only the *test harness* is gated.
