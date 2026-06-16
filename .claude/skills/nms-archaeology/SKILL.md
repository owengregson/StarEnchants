---
name: nms-archaeology
description: Use when server internals behave unexpectedly on a specific Minecraft version — a reflection lookup misses, a field seems renamed or relocated, a mechanic gates differently. Gives the javap-based procedure for reading the actual server instead of guessing.
---

# NMS archaeology: read the server, don't guess

When a version behaves unexpectedly, the answer is in the server's own class
files, already on disk and (1.20.5+) Mojang-mapped. Read them; do not trust
memory or the wiki.

```bash
# Server classes for a version (Mojang-mapped from 1.20.5+). Two sources:
#   the local reference cache (always available; scripts/fetch-reference.sh):
jar=$(find reference/servers/paper/<version> \( -path '*/versions/*.jar' -o -name 'patched_*.jar' \) | head -1)
#   or a live matrix run: run/paper-<version>/versions/<version>/paper-<version>.jar
# (Folia: reference/servers/folia/<version>/...) — see the reference-cache skill.

# 1. List a class's DECLARED members (superclass members need their own javap —
#    a "missing" member is often inherited):
javap -p -classpath "$jar" net.minecraft.world.item.ItemStack | grep -i <term>

# 2. Disassemble the gate you care about and read which fields it consults:
javap -c -p -classpath "$jar" <fully.qualified.Class> | grep -A12 "<method>"
```

Pre-1.20.5 runtimes are **spigot-mapped** — route names through a
reflection-remapper instead of javap'ing obfuscated jars (`paper-cross-version`).

## Lessons this procedure earns

- **Mechanics MOVE between classes across versions.** A grep on one class
  proves nothing about a mechanic; disassemble the CONSUMER to find the true
  backing state.
- **Check all candidate members**, not just name guesses
  (`grep -E "boolean|int |String "`) — Mojang renames freely between snapshots.
- **Bytecode beats wiki/memory.** `ifeq` chains reveal the exact boolean
  composition of a vanilla gate, including server-config checks.
- For Bukkit-API breaks (not NMS), prefer reading the **paper-api** jar /
  `Registry` over reflection — most StarEnchants version pain is API-level
  (enum→registry renames), handled by resolvers (`cross-version-item-api`), and
  needs javap only when a resolver still misses.

## When to reach for this

Reflection/remapper miss, a resolver returns null on one version, a feature
works on N but not N+1, or "this field stopped existing." Confirm with bytecode
before changing code; then encode the finding as a capability probe or resolver
fallback, never a version-string branch.
