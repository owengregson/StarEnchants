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
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * §D soul ECONOMY over a real {@link Player} inventory + gem PDC: deposit-on-any-kill (deferred to the
 * killer's thread), combine, and {@code /se split}. Also pins two adversarial-review dupe/lose regressions:
 * a spend persists to the active gem wherever it sits (not only the main hand), and toggling soul mode OFF
 * flushes the authority to PDC before forgetting it (else the spend refunds). Fake player.
 */
public final class SoulEconomySuite implements Harness.Scenario {

    /** A throwaway balance for driving a ledger debit in a test — the durable write is asserted elsewhere. */
    private static final SoulLedger.Balance NOOP = new SoulLedger.Balance() {
        @Override
        public int souls() {
            return 0;
        }

        @Override
        public void setSouls(int souls) {
            // inert: the test asserts the SERVICE's own write-through, not this proxy
        }
    };

    private static final String[] KEYS = {
        "soul.depositOnAnyKill", "soul.combineSumsAndRetires", "soul.splitCarvesAndKeeps",
        "soul.spendPersistsByIdentity", "soul.toggleOffFlushesSpend",
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
        SoulLedger ledger = new SoulLedger();
        SoulService souls = new SoulService(ledger, new SoulModeStore(), codec, SoulGemConfig::defaults);

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
                        if (countGemsWithSouls(codec, player, 4) != 1) {
                            throw new IllegalStateException("the carved-off 4-soul gem was not produced");
                        }
                        SoulService.SplitResult tooMany = souls.split(player, 100);
                        if (tooMany.status() != SoulService.SplitResult.Status.TOO_MANY) {
                            throw new IllegalStateException("splitting everything away should be refused: " + tooMany);
                        }
                    });

                    // Finding #3: a spend must persist to the gem wherever it sits, not only the main hand.
                    // Seed via toggle-on, move the gem off-hand, debit the authority, then clear() (the quit
                    // flush, which calls the same persist() under test).
                    h.guard("soul.spendPersistsByIdentity", () -> {
                        player.getInventory().clear();
                        ItemStack g = gem(souls, codec, 10);
                        player.getInventory().setItemInMainHand(g);
                        UUID gid = codec.read(player.getInventory().getItemInMainHand()).gemId();
                        if (souls.toggle(player) != SoulService.Toggle.ENABLED) {
                            throw new IllegalStateException("toggle-on did not enable soul mode");
                        }
                        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
                        player.getInventory().setItem(18, g); // gem now in the bag, weapon in hand
                        if (!ledger.tryConsume(gid, NOOP, 3)) { // debit the in-memory authority 10 -> 7
                            throw new IllegalStateException("authority debit unexpectedly failed");
                        }
                        souls.clear(player); // flushes the authority to the gem by IDENTITY, then forgets it
                        SoulData after = codec.read(player.getInventory().getItem(18));
                        if (after == null || after.souls() != 7) {
                            throw new IllegalStateException("spend did not persist to the off-hand gem: "
                                    + (after == null ? "no gem" : after.souls()));
                        }
                    });

                    // Finding #1: toggling soul mode OFF must flush the just-spent balance to PDC before
                    // forgetting the authority, else the spend refunds (a dupe).
                    h.guard("soul.toggleOffFlushesSpend", () -> {
                        player.getInventory().clear();
                        ItemStack g = gem(souls, codec, 10);
                        player.getInventory().setItemInMainHand(g);
                        UUID gid = codec.read(player.getInventory().getItemInMainHand()).gemId();
                        souls.toggle(player); // ENABLED, seeds 10
                        if (!ledger.tryConsume(gid, NOOP, 3)) { // debit 10 -> 7 (durable not yet written)
                            throw new IllegalStateException("authority debit unexpectedly failed");
                        }
                        souls.toggle(player); // DISABLED — must flush 7 to PDC before forgetting
                        SoulData after = codec.read(player.getInventory().getItemInMainHand());
                        if (after == null || after.souls() != 7) {
                            throw new IllegalStateException("toggle-off did not flush the spent balance: "
                                    + (after == null ? "no gem" : after.souls()));
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

    private static int countGemsWithSouls(SoulCodec codec, Player player, int souls) {
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
