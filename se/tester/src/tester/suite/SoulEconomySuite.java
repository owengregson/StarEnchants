package tester.suite;

import compile.load.SoulGemConfig;
import engine.interact.SoulLedger;
import engine.stores.SoulModeStore;
import feature.soul.SoulService;
import item.codec.ItemKeys;
import item.codec.SoulCodec;
import item.codec.SoulData;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * Live checks for the §D soul ECONOMY that mutates a real {@link Player} inventory + real gem PDC (a unit
 * test cannot): a kill deposits souls into a carried gem ANYWHERE in the inventory regardless of soul mode
 * ({@link SoulService#onKill}, deferred to the killer's thread); dragging two gems together sums their souls
 * into a fresh gem with a new identity ({@link SoulService#combine}); and {@code /se split} carves a count
 * off the held gem into a new gem, keeping the remainder and refusing to split everything away
 * ({@link SoulService#split}). Uses a fake player; every assertion runs on that player's own region thread.
 */
public final class SoulEconomySuite implements Harness.Scenario {

    private static final String[] KEYS = {
        "soul.depositOnAnyKill", "soul.combineSumsAndRetires", "soul.splitCarvesAndKeeps",
    };

    private final Plugin plugin;

    public SoulEconomySuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        for (String key : KEYS) {
            h.expect(key);
        }

        ItemKeys keys = ItemKeys.of(plugin);
        SoulCodec codec = new SoulCodec(keys.soul());
        SoulService souls = new SoulService(new SoulLedger(), new SoulModeStore(), codec, SoulGemConfig::defaults);

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                Player player;
                try {
                    player = FakePlayers.spawn(world, "se_soul_eco");
                } catch (Throwable t) {
                    failAll(h, "fake player spawn: " + t);
                    return;
                }
                Scheduling.onEntity(player, () -> {
                    // Combine + split are synchronous on this thread; run them first.
                    h.guard("soul.combineSumsAndRetires", () -> {
                        player.getInventory().clear();
                        ItemStack a = gem(souls, codec, 5);
                        ItemStack b = gem(souls, codec, 3);
                        UUID idA = codec.read(a).gemId();
                        UUID idB = codec.read(b).gemId();
                        ItemStack merged = souls.combine(player, a, b);
                        SoulData md = merged == null ? null : codec.read(merged);
                        if (md == null || md.souls() != 8) {
                            throw new IllegalStateException("combine did not sum to 8: "
                                    + (md == null ? "null" : md.souls()));
                        }
                        if (md.gemId().equals(idA) || md.gemId().equals(idB)) {
                            throw new IllegalStateException("combine reused a source identity");
                        }
                    });

                    h.guard("soul.splitCarvesAndKeeps", () -> {
                        player.getInventory().clear();
                        player.getInventory().setItemInMainHand(gem(souls, codec, 10));
                        SoulService.SplitResult r = souls.split(player, 4);
                        if (!r.ok() || r.moved() != 4 || r.remaining() != 6) {
                            throw new IllegalStateException("split outcome wrong: " + r);
                        }
                        SoulData held = codec.read(player.getInventory().getItemInMainHand());
                        if (held == null || held.souls() != 6) {
                            throw new IllegalStateException("held gem did not keep 6: "
                                    + (held == null ? "null" : held.souls()));
                        }
                        if (countGemsWithSouls(souls, codec, player, 4) != 1) {
                            throw new IllegalStateException("the carved-off 4-soul gem was not produced");
                        }
                        SoulService.SplitResult tooMany = souls.split(player, 100);
                        if (tooMany.status() != SoulService.SplitResult.Status.TOO_MANY) {
                            throw new IllegalStateException("splitting everything away should be refused: " + tooMany);
                        }
                    });

                    // Deposit-on-any-kill: onKill DEFERS to the killer's thread, so assert after a few ticks.
                    player.getInventory().clear();
                    player.getInventory().setItem(20, souls.mintGem()); // a gem far from the main hand, 0 souls
                    souls.onKill(player, EntityType.ZOMBIE); // soul mode OFF — must still deposit
                    Scheduling.onEntityLater(player, 5L, () -> {
                        try {
                            h.guard("soul.depositOnAnyKill", () -> {
                                SoulData after = codec.read(player.getInventory().getItem(20));
                                if (after == null || after.souls() != 1) {
                                    throw new IllegalStateException("a kill did not deposit into the carried gem: "
                                            + (after == null ? "no gem" : after.souls()));
                                }
                            });
                        } finally {
                            FakePlayers.despawn(player);
                        }
                    });
                });
            });
        });
    }

    /** A gem ITEM carrying exactly {@code souls} (mint, then stamp the count onto its identity). */
    private static ItemStack gem(SoulService service, SoulCodec codec, int souls) {
        ItemStack stack = service.mintGem();
        codec.write(stack, new SoulData(codec.read(stack).gemId(), souls));
        return stack;
    }

    private static int countGemsWithSouls(SoulService service, SoulCodec codec, Player player, int souls) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            SoulData data = codec.read(stack);
            if (data != null && data.souls() == souls) {
                n++;
            }
        }
        return n;
    }

    private static void failAll(Harness h, String message) {
        for (String key : KEYS) {
            h.fail(key, message);
        }
    }
}
