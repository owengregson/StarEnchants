package tester.fake;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Spawns a clientless, NMS-backed {@link Player} for the live suites (live-server-testing skill) —
 * the keystone that lets the matrix verify anything needing a real player (combat triggers, equipment,
 * GUIs) without a game client. The recipe follows docs research cached in the project memory: build a
 * {@code ServerPlayer} via its (version-stable) 4-arg constructor, give it a connection over a netty
 * channel that voids all outbound traffic, and register it through {@code PlayerList.placeNewPlayer}.
 *
 * <p><strong>Whole range (Paper 1.17.1 → 26.1.x + Folia).</strong> All NMS/CraftBukkit access is
 * reflective because the harness compiles against the paper-api floor. There are two paths, chosen once
 * by probing the runtime mapping (the deobf {@code ServerPlayer} class exists only on the mojang-mapped
 * 1.20.5+ runtime):
 *
 * <ul>
 *   <li><strong>Mojang-mapped (1.20.6 → 26.1.x).</strong> Construction signatures are near-identical
 *       across this range — the static {@code ClientInformation.createDefault} and
 *       {@code CommonListenerCookie.createInitial} factories absorb the ctor-arity changes — with ONE
 *       branch: Folia 1.20.6 regionized the join, so {@code placeNewPlayer} there is a 6-arg variant
 *       taking the spawn {@code Location} up front; the spawn step tries the 3-arg form and falls back.</li>
 *   <li><strong>Spigot-mapped floor (below the 1.20.5 flip: Paper 1.17.1 / 1.18.2 / 1.19.4 + Folia
 *       1.19.4).</strong> Classes follow the Mojang
 *       package layout but carry Spigot class names ({@code EntityPlayer}, {@code WorldServer},
 *       {@code NetworkManager}, {@code EnumProtocolDirection}) and obfuscated members. The shapes the
 *       harness needs are mostly UNIFORM across the floor: the
 *       {@code EntityPlayer(MinecraftServer, WorldServer, GameProfile)} ctor (no {@code ClientInformation}
 *       — that class postdates the floor) and {@code getBukkitEntity()} (CraftBukkit keeps the name).
 *       {@code placeNewPlayer} is the obfuscated 2-arg {@code a(NetworkManager, EntityPlayer)} on Paper, but
 *       Folia 1.19.4 regionized + un-obfuscated it to 5-arg {@code placeNewPlayer(NetworkManager,
 *       EntityPlayer, NBTTagCompound, String, Location)} (the spawn step tries 2-arg, falls back to 5-arg).
 *       The only per-version drift is the netty {@code channel}/{@code address} field names ({@code k}/{@code l}
 *       on 1.17.1, {@code m}/{@code n} on 1.18.2+), which are sidestepped by locating those fields BY TYPE
 *       rather than name; {@code SERVERBOUND} is taken as enum ordinal 0 (its stable position). This is
 *       the follow-up ADR 0015 deferred — now implemented (ADR 0018).</li>
 * </ul>
 *
 * <p>Only netty is referenced directly (a {@code compileOnly} dep, provided by the server) so the one
 * genuinely risky edge — the fake channel — is plain code, not reflection.
 *
 * <p><strong>Threading:</strong> {@link #spawn} registers the player server-wide, so it MUST be
 * called on the world's owning thread — the main thread on Paper, the global region on Folia (route
 * it through the {@code Scheduling} abstraction). Each reflective phase is wrapped with a descriptive
 * message so a matrix failure names the exact step and version that broke.
 */
public final class FakePlayers {

    private FakePlayers() {
    }

    /**
     * Whether the runtime is Mojang-mapped (1.20.5+). The deobf {@code ServerPlayer} class exists only
     * there; on the spigot-mapped floor (1.17.1–1.19.4) the class is {@code EntityPlayer}. Probed once.
     */
    private static final boolean MOJANG_MAPPED = classExists("net.minecraft.server.level.ServerPlayer");

    /**
     * Spawn a clientless fake player named {@code name} into {@code world} and return the live Bukkit
     * {@link Player}. Must be called on the world's owning thread (Paper main / Folia global region).
     * Dispatches to the mojang-mapped or spigot-mapped construction path by the runtime mapping.
     *
     * @throws IllegalStateException if any reflective construction step fails (message names the step)
     */
    public static Player spawn(World world, String name) {
        return MOJANG_MAPPED ? spawnMojang(world, name) : spawnSpigot(world, name);
    }

    /** The mojang-mapped construction path (Paper/Folia 1.20.6 → 26.1.x). */
    private static Player spawnMojang(World world, String name) {
        Object craftServer = Bukkit.getServer();
        Object mcServer = step("CraftServer.getServer()", () -> call(craftServer, "getServer"));
        Object playerList = step("CraftServer.getHandle()", () -> call(craftServer, "getHandle"));
        Object level = step("CraftWorld.getHandle()", () -> call(world, "getHandle"));

        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));

        Object serverPlayer = step("new ServerPlayer(...)", () -> {
            Class<?> gameProfile = Class.forName("com.mojang.authlib.GameProfile");
            Object profile = gameProfile.getConstructor(UUID.class, String.class).newInstance(uuid, name);
            Class<?> clientInfo = Class.forName("net.minecraft.server.level.ClientInformation");
            Object info = method(clientInfo, "createDefault").invoke(null);
            Class<?> mcServerClass = Class.forName("net.minecraft.server.MinecraftServer");
            Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            return serverPlayerClass.getConstructor(mcServerClass, levelClass, gameProfile, clientInfo)
                    .newInstance(mcServer, level, profile, info);
        });

        Object connection = step("new Connection(SERVERBOUND) + void channel", FakePlayers::newVoidConnection);

        step("PlayerList.placeNewPlayer(...)", () -> {
            Class<?> gameProfile = Class.forName("com.mojang.authlib.GameProfile");
            Object profile = gameProfile.getConstructor(UUID.class, String.class).newInstance(uuid, name);
            Class<?> cookieClass = Class.forName("net.minecraft.server.network.CommonListenerCookie");
            Object cookie = method(cookieClass, "createInitial", gameProfile, boolean.class)
                    .invoke(null, profile, false);
            Class<?> connectionClass = Class.forName("net.minecraft.network.Connection");
            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            try {
                // Paper 1.20.5+ and Folia 1.21+: placeNewPlayer(Connection, ServerPlayer, CommonListenerCookie).
                method(playerList.getClass(), "placeNewPlayer", connectionClass, serverPlayerClass, cookieClass)
                        .invoke(playerList, connection, serverPlayer, cookie);
            } catch (NoSuchMethodException regionizedJoin) {
                // Folia 1.20.6 regionized the join into a 6-arg variant that takes the spawn location up
                // front (the region the player joins is decided from it): placeNewPlayer(Connection,
                // ServerPlayer, CommonListenerCookie, Optional<CompoundTag> savedData, String, Location).
                method(playerList.getClass(), "placeNewPlayer", connectionClass, serverPlayerClass, cookieClass,
                        java.util.Optional.class, String.class, org.bukkit.Location.class)
                        .invoke(playerList, connection, serverPlayer, cookie,
                                java.util.Optional.empty(), name, world.getSpawnLocation());
            }
            return null;
        });

        clearSpawnProtection(craftServer);

        return (Player) step("ServerPlayer.getBukkitEntity()", () -> call(serverPlayer, "getBukkitEntity"));
    }

    /**
     * The spigot-mapped construction path (the floor: Paper 1.17.1 / 1.18.2 / 1.19.4). Spigot class names,
     * obfuscated members — but the shapes the harness needs are uniform across the three versions (see the
     * class javadoc + ADR 0018). The only per-version drift (the netty {@code channel}/{@code address}
     * field names) is handled by {@link #setFieldByType}.
     */
    private static Player spawnSpigot(World world, String name) {
        Object craftServer = Bukkit.getServer();
        Object mcServer = step("CraftServer.getServer()", () -> call(craftServer, "getServer"));
        Object playerList = step("CraftServer.getHandle()", () -> call(craftServer, "getHandle"));
        Object level = step("CraftWorld.getHandle()", () -> call(world, "getHandle"));

        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));

        Object entityPlayer = step("new EntityPlayer(...)", () -> {
            Class<?> gameProfile = Class.forName("com.mojang.authlib.GameProfile");
            Object profile = gameProfile.getConstructor(UUID.class, String.class).newInstance(uuid, name);
            Class<?> mcServerClass = Class.forName("net.minecraft.server.MinecraftServer");
            Class<?> worldServerClass = Class.forName("net.minecraft.server.level.WorldServer");
            Class<?> entityPlayerClass = Class.forName("net.minecraft.server.level.EntityPlayer");
            // Stable 3-arg ctor across 1.17.1–1.19.4 (no ClientInformation — that class postdates the floor).
            return entityPlayerClass.getConstructor(mcServerClass, worldServerClass, gameProfile)
                    .newInstance(mcServer, level, profile);
        });

        Object connection = step("new NetworkManager(SERVERBOUND) + void channel",
                FakePlayers::newVoidConnectionSpigot);

        // Paper 1.17.1/1.18.2 defer the join behind a spawn-chunk FULL future; the suite's
        // setChunkForceLoaded only marks the chunk (it does not block-load it), so block-load the spawn
        // chunk to FULL HERE — on the spawn region's thread — so placeNewPlayer's future is already complete
        // and its join callback (which sets PlayerConnection.playerJoinReady) runs INLINE. 1.19.4 joins
        // synchronously and does not consult this, so the load is harmless there.
        org.bukkit.Location spawnLoc = world.getSpawnLocation();
        step("block-load spawn chunk to FULL", () -> {
            world.getChunkAt(spawnLoc.getBlockX() >> 4, spawnLoc.getBlockZ() >> 4);
            return null;
        });

        step("PlayerList.placeNewPlayer(...)", () -> {
            Class<?> networkManagerClass = Class.forName("net.minecraft.network.NetworkManager");
            Class<?> entityPlayerClass = Class.forName("net.minecraft.server.level.EntityPlayer");
            try {
                // Paper floor (1.17.1/1.18.2/1.19.4): placeNewPlayer obfuscates to a(NetworkManager, EntityPlayer).
                method(playerList.getClass(), "a", networkManagerClass, entityPlayerClass)
                        .invoke(playerList, connection, entityPlayer);
            } catch (NoSuchMethodException foliaRegionized) {
                // Folia 1.19.4 regionized AND un-obfuscated the join, taking the spawn location up front:
                // placeNewPlayer(NetworkManager, EntityPlayer, NBTTagCompound saved, String, Location).
                Class<?> nbtClass = Class.forName("net.minecraft.nbt.NBTTagCompound");
                method(playerList.getClass(), "placeNewPlayer", networkManagerClass, entityPlayerClass,
                        nbtClass, String.class, org.bukkit.Location.class)
                        .invoke(playerList, connection, entityPlayer, null, name, world.getSpawnLocation());
            }
            return null;
        });

        // 1.17.1/1.18.2: complete the async pending-join deterministically. placeNewPlayer parked the player
        // in pendingPlayers and (chunk already FULL) set PlayerConnection.playerJoinReady — a Runnable that
        // moves the player into the live list. Its natural trigger is PlayerConnection.tick(), but our fake
        // connection is never registered with ServerConnection and so is never ticked; we run the callback
        // ourselves on this (region/main) thread. The field is absent on 1.19.4 (synchronous join) — a no-op.
        // Complete the join DETERMINISTICALLY. On 1.19.4 placeNewPlayer joins synchronously (already
        // online). On 1.17.1/1.18.2 it parks the player in pendingPlayers and defers the live registration
        // behind a chunk-FULL future whose completion callback sets PlayerConnection.playerJoinReady (a
        // Runnable) — but only when the ChunkProviderServer main-thread task processor is drained, and that
        // Runnable is normally invoked by PlayerConnection.tick(), which never fires for our unregistered
        // connection. So we do the work ourselves, on this (region/main) thread, in a bounded loop that does
        // NOT depend on tick timing: drain the processor (runs the future's callback ⇒ sets playerJoinReady),
        // run playerJoinReady, and re-check — with a brief yield as a safety net for any off-thread chunk
        // stage to post back. This was flaky under concurrent matrix load when it relied on a single drain.
        step("complete the join (online)", () -> {
            Class<?> playerConnectionClass = Class.forName("net.minecraft.server.network.PlayerConnection");
            for (int attempt = 0; attempt < 100; attempt++) {
                if (Bukkit.getServer().getPlayer(uuid) != null) {
                    return null; // live
                }
                drainChunkTasks(level);
                Object playerConnection = getFieldByType(entityPlayer, playerConnectionClass);
                Object joinReady = playerConnection == null ? null
                        : getFieldByName(playerConnection, "playerJoinReady");
                if (joinReady instanceof Runnable ready) {
                    setField(playerConnection, "playerJoinReady", null); // mirror tick()'s null-then-run
                    ready.run(); // -> postChunkLoadJoin -> player goes live
                } else {
                    sleepQuietly(2L); // let an off-thread chunk stage complete, then drain again
                }
            }
            return null; // still not live ⇒ the caller's online assertion fails with a clear message
        });

        clearSpawnProtection(craftServer);

        return (Player) step("EntityPlayer.getBukkitEntity()", () -> call(entityPlayer, "getBukkitEntity"));
    }

    /** Remove a fake player from the server; best-effort, never fails a test on teardown. */
    @SuppressWarnings("deprecation") // kickPlayer(String) is the cross-version clientless-despawn on the floor
    public static void despawn(Player player) {
        try {
            if (MOJANG_MAPPED) {
                Object serverPlayer = call(player, "getHandle"); // CraftPlayer.getHandle() -> ServerPlayer
                Object playerList = call(Bukkit.getServer(), "getHandle");
                Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
                method(playerList.getClass(), "remove", serverPlayerClass).invoke(playerList, serverPlayer);
            } else {
                // Floor: PlayerList.remove is obfuscated and ambiguous (several void(EntityPlayer) methods),
                // so use the Bukkit kick path — it disconnects + de-registers a clientless player without
                // guessing an obfuscated name (the outbound kick packet is swallowed by the void channel).
                player.kickPlayer("");
            }
        } catch (Throwable ignored) {
            // teardown is best-effort: the run is ending anyway, so a failed removal must not mask results
        }
    }

    /**
     * A {@code net.minecraft.network.Connection} wired to a netty {@link EmbeddedChannel} whose
     * outbound is voided — every packet released and its promise completed, so the server's
     * send/track/keep-alive paths neither leak buffers nor block on a clientless connection.
     */
    private static Object newVoidConnection() throws ReflectiveOperationException {
        Class<?> connectionClass = Class.forName("net.minecraft.network.Connection");
        Class<?> packetFlowClass = Class.forName("net.minecraft.network.protocol.PacketFlow");
        Object serverbound = packetFlowClass.getField("SERVERBOUND").get(null);
        Object connection = connectionClass.getConstructor(packetFlowClass).newInstance(serverbound);

        setField(connection, "channel", voidChannel());
        setField(connection, "address", new InetSocketAddress("127.0.0.1", 0));
        return connection;
    }

    /**
     * The floor variant: {@code net.minecraft.network.NetworkManager} (Spigot's name for
     * {@code Connection}) over the same void {@link EmbeddedChannel}. The direction enum and the
     * channel/address fields are obfuscated, so {@code SERVERBOUND} is taken as ordinal 0 (its stable
     * position in {@code {SERVERBOUND, CLIENTBOUND}}) and the fields are located by type, not name.
     */
    private static Object newVoidConnectionSpigot() throws ReflectiveOperationException {
        Class<?> networkManagerClass = Class.forName("net.minecraft.network.NetworkManager");
        Class<?> directionClass = Class.forName("net.minecraft.network.protocol.EnumProtocolDirection");
        Object serverbound = directionClass.getEnumConstants()[0];
        Object connection = networkManagerClass.getConstructor(directionClass).newInstance(serverbound);

        setFieldByType(connection, io.netty.channel.Channel.class, voidChannel());
        setFieldByType(connection, java.net.SocketAddress.class, new InetSocketAddress("127.0.0.1", 0));
        return connection;
    }

    /** A fresh {@link EmbeddedChannel} whose outbound is voided — every packet released and its promise
     * completed, so the server's send/track/keep-alive paths neither leak buffers nor block. */
    private static EmbeddedChannel voidChannel() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addFirst("se-void-outbound", new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                ReferenceCountUtil.release(msg); // drop the packet and release its buffers
                if (promise != null && !promise.isVoid()) {
                    promise.trySuccess(); // complete so the server never blocks waiting on the send
                }
            }

            @Override
            public void flush(ChannelHandlerContext ctx) {
                // swallow — nothing leaves a clientless connection
            }
        });
        return channel;
    }

    private static void clearSpawnProtection(Object craftServer) {
        try {
            method(craftServer.getClass(), "setSpawnRadius", int.class).invoke(craftServer, 0);
        } catch (Throwable ignored) {
            // best-effort: spawning and surviving does not require it; not all versions expose the setter
        }
    }

    // ── reflection helpers ───────────────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface Step {
        Object run() throws ReflectiveOperationException;
    }

    /** Run one reflective phase, tagging any failure with the step name and the root cause. */
    private static Object step(String name, Step body) {
        try {
            return body.run();
        } catch (Throwable t) {
            throw new IllegalStateException("fake-player spawn failed at [" + name + "]: " + rootMessage(t), t);
        }
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        return method(target.getClass(), name).invoke(target);
    }

    /** Find a method by name+params, preferring the public view, else walking the hierarchy. */
    private static Method method(Class<?> start, String name, Class<?>... params) throws NoSuchMethodException {
        try {
            return start.getMethod(name, params);
        } catch (NoSuchMethodException notPublic) {
            for (Class<?> c = start; c != null; c = c.getSuperclass()) {
                try {
                    Method m = c.getDeclaredMethod(name, params);
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException walk) {
                    // keep walking up
                }
            }
            throw notPublic;
        }
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException walk) {
                // keep walking up
            }
        }
        throw new NoSuchFieldException("no field '" + name + "' on " + target.getClass().getName());
    }

    /**
     * Set the UNIQUE field of EXACTLY {@code exactType} (walking up the hierarchy) — the version-robust way
     * to reach an obfuscated field whose name drifts but whose type is stable (the floor netty
     * channel/address fields: {@code k}/{@code l} on 1.17.1, {@code m}/{@code n} on 1.18.2+). Exact-type
     * match (not assignable) so a {@code SocketAddress} field is hit but a sibling {@code InetSocketAddress}
     * field (the virtual host) is not.
     */
    private static void setFieldByType(Object target, Class<?> exactType, Object value)
            throws ReflectiveOperationException {
        Field f = uniqueFieldOfType(target, exactType);
        if (f == null) {
            throw new NoSuchFieldException("no field of type " + exactType.getName()
                    + " on " + target.getClass().getName());
        }
        f.set(target, value);
    }

    /**
     * The single field of EXACTLY {@code exactType} on {@code target}'s hierarchy, or {@code null} if none.
     * FAILS LOUD on ambiguity (more than one field of that exact type) — the harness depends on these
     * lookups being unambiguous (each target class has exactly one), so a future version that adds a second
     * field of the same type surfaces as a tagged step failure here, never a silent wrong-field pick.
     */
    private static Field uniqueFieldOfType(Object target, Class<?> exactType) {
        Field found = null;
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() == exactType) {
                    if (found != null) {
                        throw new IllegalStateException("ambiguous: multiple fields of type "
                                + exactType.getName() + " on " + target.getClass().getName());
                    }
                    found = f;
                }
            }
        }
        if (found != null) {
            found.setAccessible(true);
        }
        return found;
    }

    /**
     * Drain the {@code ChunkProviderServer} main-thread task processor so a freshly-queued chunk-future
     * completion (placeNewPlayer's deferred join callback) runs NOW on this thread. Spigot-mapped 1.17.1
     * names the pump {@code runTasks()}, 1.18.2 obfuscates it to {@code d()} — both {@code boolean} no-arg,
     * returning whether work remained. Best-effort: if no known pump exists (e.g. a synchronous-join
     * version that never needs it), it quietly does nothing. Drains to empty, bounded against a runaway.
     */
    private static void drainChunkTasks(Object worldServer) throws ReflectiveOperationException {
        Object cps = getFieldByType(worldServer, Class.forName("net.minecraft.server.level.ChunkProviderServer"));
        if (cps == null) {
            return;
        }
        Method drain = null;
        try {
            // 1.17.1 keeps the readable name.
            drain = cps.getClass().getMethod("runTasks");
        } catch (NoSuchMethodException obfuscated) {
            // 1.18.2/1.19.4 obfuscate the pump to the no-arg boolean d(). Only trust that single-letter
            // name on a genuine ChunkProviderServer — pinned by the stable-named getChunkAtAsynchronously
            // marker — so a future re-obfuscation that maps d() to something else fails LOUD (no drain ⇒ a
            // clear "not online" timeout) rather than silently calling the wrong method.
            if (hasMethodNamed(cps.getClass(), "getChunkAtAsynchronously")) {
                try {
                    drain = cps.getClass().getMethod("d");
                } catch (NoSuchMethodException none) {
                    // no known pump
                }
            }
        }
        if (drain == null) {
            return; // a synchronous-join version that never needs draining
        }
        for (int i = 0; i < 256; i++) {
            if (Boolean.FALSE.equals(drain.invoke(cps))) {
                break; // queue empty
            }
        }
    }

    /** Sleep without surfacing interruption — a brief yield for an off-thread chunk stage to post back. */
    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Read the value of the UNIQUE field of EXACTLY {@code exactType} (walking up), or {@code null} if none
     * (fails loud on ambiguity — see {@link #uniqueFieldOfType}). */
    private static Object getFieldByType(Object target, Class<?> exactType) throws ReflectiveOperationException {
        Field f = uniqueFieldOfType(target, exactType);
        return f == null ? null : f.get(target);
    }

    /** Read the value of field {@code name} (walking up), or {@code null} if no such field exists. */
    private static Object getFieldByName(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException walk) {
                // keep walking up; absent entirely (e.g. 1.19.4 has no playerJoinReady) ⇒ null
            }
        }
        return null;
    }

    /** Whether {@code cls} exposes any public method named {@code name} (a stable-name marker check). */
    private static boolean hasMethodNamed(Class<?> cls, String name) {
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a class is present on the runtime — the mapping probe (no init, current loader). */
    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, FakePlayers.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
    }
}
