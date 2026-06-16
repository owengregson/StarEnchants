# ADR 0002: Target Paper 1.17.1–26.1.x + Folia from one universal jar

- **Status:** Accepted
- **Date:** 2026-06-15
- **Deciders:** owengregson

## Context

The plugin must run across a very wide Minecraft range and on Folia, from a
single artifact. The range spans the 1.20.5 spigot→mojang mapping flip, the
1.21.3 `Attribute`/`Sound` enum→interface flip, and Java 17→21 toolchains.

## Decision

One universal shaded jar. The common code path compiles against the **floor API
(Paper 1.17.1)** so it is binary-safe everywhere; newer-API features live behind
runtime capability detection. `api-version: 1.17`; declare `folia-supported: true`
once region-safe. Class-file target Java 17.

## Consequences

- Every version-volatile API must be reached through a resolver or a capability
  probe (see ADR 0008); never a hard constant reference.
- CI must boot the real version matrix on Paper **and** Folia (see ADR 0009 /
  the `matrix-gate` skill).

## Alternatives considered

- Per-version jars — rejected: distribution + maintenance burden.
- Targeting a higher floor — rejected: 1.17.1 support is required.
