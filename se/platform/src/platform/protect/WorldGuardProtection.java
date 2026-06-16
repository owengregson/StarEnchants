package platform.protect;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * A best-effort {@link ProtectionProvider} bridge to WorldGuard 7.x (the build supported across MC
 * 1.13+, i.e. our whole 1.17.1→26.1.x range). Entirely reflective — StarEnchants has NO compile
 * dependency on WorldGuard — so it loads on a server without WG (the adapter is simply never created)
 * and never links WG classes unless WG is actually present.
 *
 * <p>The check mirrors WorldGuard's own build query: a player with region bypass is always allowed,
 * otherwise the region's {@code BUILD} flag at the location decides. All reflective handles are
 * resolved once in {@link #tryCreate}; if WG's API shape does not match (a future major rework), the
 * adapter is not created and a warning is logged, rather than silently mis-guarding. A per-query
 * reflective failure surfaces to {@link ProtectionService}, which treats it as "allow".
 *
 * <p>This bridge is verified only for graceful absence (no WG → not created); behaviour against a live
 * WorldGuard is not exercised by the test matrix. The {@link ProtectionProvider} SPI is the supported
 * extension point for other region plugins and for servers wanting an authoritative custom check.
 */
public final class WorldGuardProtection implements ProtectionProvider {

    private final Object regionContainer;   // RegionContainer
    private final Object sessionManager;    // SessionManager
    private final Object buildFlag;         // StateFlag (Flags.BUILD)
    private final Method wrapPlayer;        // WorldGuardPlugin#wrapPlayer(Player) -> LocalPlayer
    private final Method adaptLocation;     // BukkitAdapter#adapt(org.bukkit.Location) -> WE Location
    private final Method adaptWorld;        // BukkitAdapter#adapt(org.bukkit.World) -> WE World
    private final Method createQuery;       // RegionContainer#createQuery() -> RegionQuery
    private final Method testState;         // RegionQuery#testState(WE Location, RegionAssociable, StateFlag...)
    private final Method hasBypass;         // SessionManager#hasBypass(LocalPlayer, WE World)
    private final Object wgPlugin;          // WorldGuardPlugin instance
    private final Class<?> stateFlagType;   // com.sk89q.worldguard.protection.flags.StateFlag

    private WorldGuardProtection(Object regionContainer, Object sessionManager, Object buildFlag,
                                 Method wrapPlayer, Method adaptLocation, Method adaptWorld,
                                 Method createQuery, Method testState, Method hasBypass,
                                 Object wgPlugin, Class<?> stateFlagType) {
        this.regionContainer = regionContainer;
        this.sessionManager = sessionManager;
        this.buildFlag = buildFlag;
        this.wrapPlayer = wrapPlayer;
        this.adaptLocation = adaptLocation;
        this.adaptWorld = adaptWorld;
        this.createQuery = createQuery;
        this.testState = testState;
        this.hasBypass = hasBypass;
        this.wgPlugin = wgPlugin;
        this.stateFlagType = stateFlagType;
    }

    /**
     * Build the adapter if WorldGuard 7.x is present and its API matches; otherwise return {@code null}
     * (absent plugin, or an incompatible API — the latter is logged so an operator can see why the
     * bridge did not engage). Never throws.
     */
    public static WorldGuardProtection tryCreate(System.Logger log) {
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
        } catch (ClassNotFoundException absent) {
            return null; // WorldGuard not installed — nothing to bridge
        }
        try {
            Class<?> worldGuard = Class.forName("com.sk89q.worldguard.WorldGuard");
            Class<?> wgPluginType = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Class<?> weLocation = Class.forName("com.sk89q.worldedit.util.Location");
            Class<?> weWorld = Class.forName("com.sk89q.worldedit.world.World");
            Class<?> regionAssociable = Class.forName("com.sk89q.worldguard.protection.association.RegionAssociable");
            Class<?> localPlayerType = Class.forName("com.sk89q.worldguard.LocalPlayer");
            Class<?> stateFlagType = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Class<?> regionContainerType = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer");
            Class<?> regionQueryType = Class.forName("com.sk89q.worldguard.protection.regions.RegionQuery");

            Object wgInstance = worldGuard.getMethod("getInstance").invoke(null);
            Object platform = worldGuard.getMethod("getPlatform").invoke(wgInstance);
            Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object sessionManager = platform.getClass().getMethod("getSessionManager").invoke(platform);
            Object wgPlugin = wgPluginType.getMethod("inst").invoke(null);
            Object buildFlag = Class.forName("com.sk89q.worldguard.protection.flags.Flags")
                    .getField("BUILD").get(null);

            Method wrapPlayer = wgPluginType.getMethod("wrapPlayer", Player.class);
            Method adaptLocation = bukkitAdapter.getMethod("adapt", Location.class);
            Method adaptWorld = bukkitAdapter.getMethod("adapt", org.bukkit.World.class);
            Method createQuery = regionContainerType.getMethod("createQuery");
            // testState(Location, RegionAssociable, StateFlag...) — varargs reflects as a StateFlag[] param
            Method testState = regionQueryType.getMethod("testState", weLocation, regionAssociable,
                    Array.newInstance(stateFlagType, 0).getClass());
            Method hasBypass = sessionManager.getClass().getMethod("hasBypass", localPlayerType, weWorld);

            return new WorldGuardProtection(regionContainer, sessionManager, buildFlag, wrapPlayer,
                    adaptLocation, adaptWorld, createQuery, testState, hasBypass, wgPlugin, stateFlagType);
        } catch (ReflectiveOperationException | LinkageError mismatch) {
            log.log(System.Logger.Level.WARNING,
                    "WorldGuard is present but its API did not match the expected 7.x shape; "
                            + "the WorldGuard protection bridge is disabled (register a custom "
                            + "ProtectionProvider instead): " + mismatch);
            return null;
        }
    }

    @Override
    public boolean allows(Player actor, Location where) {
        try {
            Object localPlayer = wrapPlayer.invoke(wgPlugin, actor);
            Object weWorld = adaptWorld.invoke(null, where.getWorld());
            if ((boolean) hasBypass.invoke(sessionManager, localPlayer, weWorld)) {
                return true; // region-bypass players (admins) are never gated
            }
            Object weLoc = adaptLocation.invoke(null, where);
            Object flagArray = Array.newInstance(stateFlagType, 1);
            Array.set(flagArray, 0, buildFlag);
            Object query = createQuery.invoke(regionContainer);
            return (boolean) testState.invoke(query, weLoc, localPlayer, flagArray);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failed) {
            // Surface to ProtectionService's per-provider guard, which logs once and treats as allow.
            throw new IllegalStateException("WorldGuard query failed", failed);
        }
    }

    @Override
    public String name() {
        return "WorldGuard";
    }
}
