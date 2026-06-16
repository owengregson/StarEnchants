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
 * <p><strong>Mojang-mapped range only (Paper/Folia 1.20.6 → 26.1.x).</strong> All NMS/CraftBukkit
 * access is reflective because the harness compiles against the paper-api floor; the construction
 * signatures are identical across this range (the static {@code ClientInformation.createDefault} and
 * {@code CommonListenerCookie.createInitial} factories absorb the only ctor-arity changes), so there
 * is nothing to branch on. The spigot-mapped floor (1.17.1–1.19.4) is obfuscated and is a separate
 * follow-up. Only netty is referenced directly (a {@code compileOnly} dep, provided by the server) so
 * the one genuinely risky edge — the fake channel — is plain code, not reflection.
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
     * Spawn a clientless fake player named {@code name} into {@code world} and return the live Bukkit
     * {@link Player}. Must be called on the world's owning thread (Paper main / Folia global region).
     *
     * @throws IllegalStateException if any reflective construction step fails (message names the step)
     */
    public static Player spawn(World world, String name) {
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
            method(playerList.getClass(), "placeNewPlayer", connectionClass, serverPlayerClass, cookieClass)
                    .invoke(playerList, connection, serverPlayer, cookie);
            return null;
        });

        clearSpawnProtection(craftServer);

        return (Player) step("ServerPlayer.getBukkitEntity()", () -> call(serverPlayer, "getBukkitEntity"));
    }

    /** Remove a fake player from the server; best-effort, never fails a test on teardown. */
    public static void despawn(Player player) {
        try {
            Object serverPlayer = call(player, "getHandle"); // CraftPlayer.getHandle() -> ServerPlayer
            Object playerList = call(Bukkit.getServer(), "getHandle");
            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            method(playerList.getClass(), "remove", serverPlayerClass).invoke(playerList, serverPlayer);
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

        setField(connection, "channel", channel);
        setField(connection, "address", new InetSocketAddress("127.0.0.1", 0));
        return connection;
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

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
    }
}
