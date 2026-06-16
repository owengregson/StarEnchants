---
name: reference-cache
description: Use when you need authoritative Paper or Folia API/behavior for a specific Minecraft version — points to the local cache of per-version server jars (for javap) and the cached Paper/Folia developer docs, instead of guessing or going to the network.
---

# Local Paper/Folia reference cache

A local, gitignored cache so version-specific facts are looked up, not guessed.

```
reference/
├── servers/
│   ├── paper/<version>/        paperclip jar + extracted versions/<v>/*.jar (javap)
│   └── folia/<version>/        same, for Folia
└── docs/
    ├── paper/*.md              cached Paper developer guides
    └── folia/*.md              cached Folia developer guides + threading model
```

Cached versions: Paper **1.17.1, 1.18.2, 1.19.4, 1.20.6, 1.21.4, 1.21.11,
26.1.1, 26.1.2**; Folia **1.19.4, 1.20.6, 1.21.4, 1.21.11, 26.1.2**. The current
ceiling is the **26.1** line (26.2 is still RC). Newer versions (26.x) live in the
PaperMC **Fill v3 API** (`fill.papermc.io/v3`); the legacy v2 API caps at 1.21.11
— `fetch-reference.sh` uses v3 with a v2 fallback.

## Populate / refresh

```bash
scripts/fetch-reference.sh      # downloads + extracts the server jars (re-runnable)
```

The docs markdown is cached separately. Both live under `reference/` (gitignored;
large + third-party — never committed).

## Using it

**Per-version API surface (javap)** — the extracted server jar is the truth:

```bash
# newer layout = versions/<v>/*.jar; older (1.17-era) = cache/patched_*.jar
jar=$(find reference/servers/paper/1.21.4 \( -path '*/versions/*.jar' -o -name 'patched_*.jar' \) | head -1)
javap -p -classpath "$jar" net.minecraft.world.item.ItemStack | grep -i <term>
```

Paper jars are **mojang-mapped from 1.20.5+** (javap names are real); **spigot-mapped
before** (1.17.1/1.18.2/1.19.4 → route names through a remapper, don't trust the
obfuscated names). See `nms-archaeology` for the disassembly procedure and
`cross-version-item-api` for the API rename/registry breaks.

**Conceptual how-to (PDC, scheduler, Folia regions, commands, components, data
components)** — grep the cached docs:

```bash
grep -rl "PersistentDataContainer" reference/docs/paper/
grep -rl "EntityScheduler" reference/docs/folia/
```

Each cached doc has its source URL + fetch date at the top. The docs are the
prose guides (mostly version-agnostic); for the exact API of a given version use
javap on that version's jar.

## When to reach for this

A version behaves unexpectedly, you need to confirm a method/field exists on a
specific version, you're choosing an API across the range, or you need the
authoritative Folia threading rules. Confirm against the cache before changing
code; encode findings as resolvers/capability probes (`paper-cross-version`),
never version-string branches.
