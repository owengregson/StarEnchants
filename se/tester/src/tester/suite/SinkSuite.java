package tester.suite;

import engine.sink.ModernDispatchSink;
import java.util.OptionalInt;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import tester.harness.Harness;

/**
 * Affinity-routed {@link ModernDispatchSink}, live (§3.6): intents reach the right thread and mutate the world
 * across the cross-region hop only Folia exercises. Emitted from GLOBAL (a different thread than the targets'
 * regions on Folia) so the Sink must hop each deferred intent to its owning thread.
 */
public final class SinkSuite implements Harness.Scenario {

    private final Plugin plugin;

    public SinkSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("sink.entity.ignite.crossThread");
        h.expect("sink.entity.potion.handle");
        h.expect("sink.entity.disarm.crossThread");
        h.expect("sink.region.block.handle");
        h.expect("sink.wait.deferred");
        h.expect("sink.tempBlock.compound.original");
        h.expect("sink.tempBlock.compound.sharedEnv");

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        RegistryResolvers resolvers = new RegistryResolvers();
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                ArmorStand victim = spawnStand(world, at);
                EntityEquipment equipment = victim.getEquipment();
                if (equipment == null) {
                    h.fail("sink.entity.disarm.crossThread", "armor stand has no equipment on this version");
                    return;
                }
                equipment.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));

                // Emit + flush from GLOBAL — a different thread than the victim's region on Folia.
                Scheduling.onGlobal(() -> {
                    int slowId = resolveId(resolvers.potionEffect("SLOW"), "SLOW");
                    int glowstoneId = resolveId(resolvers.material("GLOWSTONE"), "GLOWSTONE");
                    Location blockAt = at.clone().add(0, 3, 0);

                    ModernDispatchSink sink = new ModernDispatchSink(handles, engine.sink.SinkEnv.of(
                            platform.economy.EconomyService.NONE, engine.sink.SoulDebit.NONE,
                            engine.stores.EngineStores.fresh(), () -> 0L));
                    sink.ignite(victim, 80);
                    sink.potion(victim, slowId, 0, 80);
                    sink.disarm(victim);
                    sink.blockChange(blockAt, glowstoneId);
                    sink.flush();

                    Scheduling.onEntityLater(victim, 4L, () -> {
                        h.guard("sink.entity.ignite.crossThread", () -> {
                            if (victim.getFireTicks() <= 0) {
                                throw new IllegalStateException(
                                        "cross-thread ignite did not reach the victim; fireTicks="
                                                + victim.getFireTicks());
                            }
                        });
                        h.guard("sink.entity.potion.handle", () -> {
                            PotionEffectType type = handles.potionEffect(slowId);
                            if (type == null) {
                                throw new IllegalStateException("SLOW did not resolve to a live PotionEffectType");
                            }
                            if (!victim.hasPotionEffect(type)) {
                                throw new IllegalStateException("potion handle intent did not apply to the victim");
                            }
                        });
                        h.guard("sink.entity.disarm.crossThread", () -> {
                            ItemStack held = equipment.getItemInMainHand();
                            if (held != null && !held.getType().isAir()) {
                                throw new IllegalStateException(
                                        "cross-thread disarm did not clear the main hand; held=" + held.getType());
                            }
                        });
                        victim.remove();
                    });

                    Scheduling.onRegionLater(blockAt, 4L, () -> h.guard("sink.region.block.handle", () -> {
                        Material expected = handles.material(glowstoneId);
                        Material actual = blockAt.getBlock().getType();
                        if (expected == null || actual != expected) {
                            throw new IllegalStateException(
                                    "block change intent did not apply; expected " + expected + " got " + actual);
                        }
                    }));

                    // WAIT (§3.6) end-to-end: a delay(10) region intent must NOT apply on flush, only after
                    // its tick count elapses — driving DispatchPlan's delayed dispatch over the real
                    // Scheduling.onRegionLater (the Folia region delayed scheduler included).
                    Location waitAt = at.clone().add(2, 3, 0); // distinct from blockAt; air on the flat spawn
                    ModernDispatchSink waitSink = new ModernDispatchSink(handles, engine.sink.SinkEnv.of(
                            platform.economy.EconomyService.NONE, engine.sink.SoulDebit.NONE,
                            engine.stores.EngineStores.fresh(), () -> 0L));
                    waitSink.delay(10);
                    waitSink.blockChange(waitAt, glowstoneId);
                    waitSink.flush();

                    java.util.concurrent.atomic.AtomicBoolean appliedEarly =
                            new java.util.concurrent.atomic.AtomicBoolean();
                    java.util.concurrent.atomic.AtomicInteger probeRan =
                            new java.util.concurrent.atomic.AtomicInteger();
                    Scheduling.onRegionLater(waitAt, 4L, () -> { // before WAIT:10 elapses — must still be pending
                        probeRan.incrementAndGet();
                        appliedEarly.set(waitAt.getBlock().getType() == handles.material(glowstoneId));
                    });
                    // +30 (not +16) so a multi-tick stall coalescing the probe, the deferred mutation, and the
                    // check into one catch-up burst can't reorder them.
                    Scheduling.onRegionLater(waitAt, 30L, () -> h.guard("sink.wait.deferred", () -> {
                        Material expected = handles.material(glowstoneId);
                        if (probeRan.get() == 0) {
                            throw new IllegalStateException("the +4 probe never ran — cannot trust the not-early check");
                        }
                        if (appliedEarly.get()) {
                            throw new IllegalStateException("WAIT:10 region intent applied before its delay elapsed");
                        }
                        if (waitAt.getBlock().getType() != expected) {
                            throw new IllegalStateException("WAIT:10 region intent never applied; got "
                                    + waitAt.getBlock().getType());
                        }
                        waitAt.getBlock().setType(Material.AIR);
                    }));

                    // Compounding temp blocks (the devil netherrack-trail-over-magma-floor bug): two overlapping
                    // tempBlock placements with different lifetimes at ONE tile must, after both revert, leave the
                    // TRUE original — not the intermediate temp block the closure-based revert stranded. Set STONE,
                    // layer a short GLOWSTONE then a longer DIAMOND_BLOCK over it (mode 2 = replace anything, one
                    // shared per-sink ledger), and after both deadlines assert STONE returned.
                    int stoneId = resolveId(resolvers.material("STONE"), "STONE");
                    int diamondId = resolveId(resolvers.material("DIAMOND_BLOCK"), "DIAMOND_BLOCK");
                    Location compoundAt = at.clone().add(4, 3, 0); // distinct from blockAt/waitAt; same forced chunk
                    ModernDispatchSink compoundSink = new ModernDispatchSink(handles, engine.sink.SinkEnv.of(
                            platform.economy.EconomyService.NONE, engine.sink.SoulDebit.NONE,
                            engine.stores.EngineStores.fresh(), () -> 0L));
                    compoundSink.blockChange(compoundAt, stoneId);              // the true original, set first
                    compoundSink.tempBlock(compoundAt, glowstoneId, 6, 2, false);  // short layer (reverts first, buried)
                    compoundSink.tempBlock(compoundAt, diamondId, 12, 2, false);   // longer layer on top (reverts last)
                    compoundSink.flush();

                    Scheduling.onRegionLater(compoundAt, 20L, () ->
                            h.guard("sink.tempBlock.compound.original", () -> {
                                Material expected = handles.material(stoneId);
                                Material actual = compoundAt.getBlock().getType();
                                if (expected == null || actual != expected) {
                                    throw new IllegalStateException("compounding temp blocks did not restore the "
                                            + "original; expected " + expected + " got " + actual);
                                }
                                compoundAt.getBlock().setType(Material.AIR);
                            }));

                    // The TRUE production shape: two SEPARATE activations sharing the ONE per-boot ledger (a
                    // DEFENSE magma floor + a REPEATING netherrack trail write the same tile). A per-event sink
                    // mints a fresh ledger and could NOT compound across activations — only the shared SinkEnv
                    // can. Build ONE env, two sinks over it; the floor sets STONE then layers a longer magma, the
                    // trail then layers a shorter netherrack on top (submission-ordered on the shared region, so
                    // magma is captured before netherrack). After both revert, STONE — the true original — must
                    // return, never a stranded temp block.
                    int magmaId = resolveId(resolvers.material("MAGMA_BLOCK"), "MAGMA_BLOCK");
                    int netherId = resolveId(resolvers.material("NETHERRACK"), "NETHERRACK");
                    Location sharedAt = at.clone().add(4, 6, 0); // distinct tile (compoundAt is +4,+3); same forced chunk
                    engine.sink.SinkEnv sharedEnv = engine.sink.SinkEnv.of(
                            platform.economy.EconomyService.NONE, engine.sink.SoulDebit.NONE,
                            engine.stores.EngineStores.fresh(), () -> 0L); // ONE env → ONE shared TempBlockLedger
                    ModernDispatchSink floorSink = new ModernDispatchSink(handles, sharedEnv); // activation A
                    ModernDispatchSink trailSink = new ModernDispatchSink(handles, sharedEnv); // activation B

                    floorSink.blockChange(sharedAt, stoneId);              // the true original, set first
                    floorSink.tempBlock(sharedAt, magmaId, 14, 2, false);  // A: magma floor, longer (reverts last)
                    floorSink.flush();
                    trailSink.tempBlock(sharedAt, netherId, 6, 2, false);  // B: netherrack trail on top, shorter (reverts first)
                    trailSink.flush();

                    Scheduling.onRegionLater(sharedAt, 24L, () ->
                            h.guard("sink.tempBlock.compound.sharedEnv", () -> {
                                Material expected = handles.material(stoneId);
                                Material actual = sharedAt.getBlock().getType();
                                if (expected == null || actual != expected) {
                                    throw new IllegalStateException("two activations over one shared ledger did not "
                                            + "restore the original; expected " + expected + " got " + actual);
                                }
                                sharedAt.getBlock().setType(Material.AIR);
                            }));
                });
            });
        });
    }

    private static ArmorStand spawnStand(World world, Location at) {
        ArmorStand stand = (ArmorStand) world.spawnEntity(at, EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setPersistent(true);
        return stand;
    }

    private static int resolveId(OptionalInt id, String token) {
        if (id.isEmpty()) {
            throw new IllegalStateException(token + " did not intern to a handle id on this version");
        }
        return id.getAsInt();
    }
}
