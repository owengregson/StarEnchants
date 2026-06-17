# ADR 0018: Spigot-floor fake-player harness — combat suites now run floor-wide

- **Status:** Accepted
- **Date:** 2026-06-16
- **Deciders:** project owner + engine work
- **Supersedes:** ADR 0015 (the deferral; the follow-up it described is implemented here)

## Context

ADR 0015 shipped the clientless fake-player harness (`se-tester/fake/FakePlayers`) **mojang-mapped
only** (1.20.5+) and gated the end-to-end combat-path suites (combat, protection, economy, crystal,
set, heroic, soul, trigger, menu, and `FakePlayerSuite` itself) behind `Capabilities.mojangMapped()`.
The spigot-mapped floor (Paper 1.17.1 / 1.18.2 / 1.19.4 and Folia 1.19.4) was left as a documented gap:
those servers use Mojang's package layout but Spigot class names with obfuscated members, a different
obfuscation map per version, and a `ServerPlayer`/`placeNewPlayer` shape that predates
`ClientInformation`/`CommonListenerCookie`. ADR 0015 called this "a large, fragile NMS effort."

On investigation (disassembling the cached floor jars) it turned out far more tractable than feared —
the shapes the harness needs are largely uniform across the floor — so the follow-up is now done.

## Decision

**Implement a spigot-mapped construction path in `FakePlayers`, chosen at runtime, and drop the
`mojangMapped()` gate so the combat-path suites run on the whole range** (Paper 1.17.1 → 26.1.x and
Folia 1.19.4 → 26.1.x).

`FakePlayers.spawn` probes the mapping once (the deobf `ServerPlayer` class exists only on 1.20.5+) and
dispatches to `spawnMojang` (unchanged) or the new `spawnSpigot`. Key facts that made the floor path
small and robust:

- **Stable class + ctor.** `EntityPlayer(MinecraftServer, WorldServer, GameProfile)` is the same 3-arg
  ctor on all three floor versions (no `ClientInformation`); `getBukkitEntity()` keeps its name.
- **`placeNewPlayer`.** Obfuscates to `a(NetworkManager, EntityPlayer)` on Paper 1.17.1/1.18.2/1.19.4;
  Folia 1.19.4 regionized AND un-obfuscated it to `placeNewPlayer(NetworkManager, EntityPlayer,
  NBTTagCompound, String, Location)` — the spawn step tries the 2-arg form and falls back to the 5-arg.
- **Per-version field drift handled by TYPE, not name.** The netty `channel`/`address` fields on
  `NetworkManager` (`k`/`l` on 1.17.1, `m`/`n` on 1.18.2+), the `PlayerConnection` field on
  `EntityPlayer`, and the `ChunkProviderServer` on `WorldServer` are all located by their (stable) field
  TYPE. `SERVERBOUND` is taken as enum ordinal 0.
- **The async pending-join (the real difficulty).** On Paper 1.17.1/1.18.2 the join is deferred:
  `placeNewPlayer` parks the player in `pendingPlayers` and only registers it live when a spawn-chunk
  FULL future completes (which sets `PlayerConnection.playerJoinReady`, a `Runnable` normally invoked by
  `PlayerConnection.tick()`). Our fake connection is never registered with `ServerConnection`, so it is
  never ticked. The harness therefore completes the join **itself, deterministically**, on the spawn
  region thread: block-load the spawn chunk to FULL (`world.getChunkAt`), then a bounded loop that drains
  the `ChunkProviderServer` main-thread task processor (`runTasks()` on 1.17.1 / `d()` on 1.18.2) so the
  future's callback runs and sets `playerJoinReady`, then runs `playerJoinReady`. 1.19.4 (Paper and
  Folia) joins synchronously and the loop returns on its first iteration. A single drain was flaky under
  concurrent matrix load; the bounded drain-and-recheck loop (with a brief yield as a safety net) is not.

## Verification

The combat-path suites now run on the floor. Confirmed by the live matrix: Paper 1.17.1 / 1.18.2 /
1.19.4 each PASS (79 checks), proven deterministic across three back-to-back concurrent stress runs;
Folia 1.19.4 PASS (79 checks). The mojang path is unchanged and still green.

## Consequences

- The residual-risk gap ADR 0015 accepted (no floor-wide end-to-end combat verification) is closed:
  combat, protection, economy, crystals, sets, heroic, souls, triggers and the apply GUI are now
  end-to-end on real 1.17.1 / 1.18.2 / 1.19.4 servers (Paper) and Folia 1.19.4.
- `FakePlayers` carries spigot-mapped reflection. It is **test-only** (the `tester` module), never
  shipped, and every reflective step is tagged so a future-version break names the exact step + version.
- The drain/`playerJoinReady` completion is specific to Paper 1.17.1/1.18.2's async-join; it self-skips
  on synchronous-join versions, so it is inert everywhere it is not needed.
