package tester.suite;

import compile.load.ScrollsConfig;
import feature.scroll.HolyScrollService;
import feature.scroll.NametagService;
import item.codec.ItemKeys;
import item.codec.ScrollCodec;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * Live checks for the §I player-bound scrolls that need a real {@link Player} inventory (a unit test
 * cannot supply one): the holy scroll's death-save scans storage + off-hand, rolls, and consumes exactly
 * one; the item nametag re-locates its target by IDENTITY (so it survives the item moving between the
 * click and the chat line), refunds a cancelled rename, and refuses a second rename while one is pending.
 * Uses a fake player; runs each assertion on that player's own region thread (Folia-correct).
 */
public final class ScrollPlayerSuite implements Harness.Scenario {

    private static final String[] KEYS = {
        "scroll.holy.savesAndConsumes", "scroll.holy.noScrollNoSave",
        "scroll.nametag.renamesByIdentity", "scroll.nametag.rejectsDoubleBegin", "scroll.nametag.refundsOnCancel",
    };

    private final Plugin plugin;

    public ScrollPlayerSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        for (String key : KEYS) {
            h.expect(key);
        }

        ItemKeys keys = ItemKeys.of(plugin);
        ScrollCodec scrollCodec = new ScrollCodec(keys.scroll());
        HolyScrollService holy = new HolyScrollService(scrollCodec, ScrollsConfig::defaults, new Random(1)); // 100%
        NametagService nametags = new NametagService(scrollCodec, ScrollsConfig::defaults);

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                Player player;
                try {
                    player = FakePlayers.spawn(world, "se_scroll_p");
                } catch (Throwable t) {
                    failAll(h, "fake player spawn: " + t);
                    return;
                }
                Scheduling.onEntity(player, () -> {
                    try {
                        player.getInventory().clear();

                        h.guard("scroll.holy.savesAndConsumes", () -> {
                            player.getInventory().clear();
                            player.getInventory().addItem(holy.mint());
                            String saved = holy.trySave(player); // default 100% → always saves
                            if (saved == null) {
                                throw new IllegalStateException("holy scroll did not save at 100% chance");
                            }
                            if (countHoly(holy, player) != 0) {
                                throw new IllegalStateException("the holy scroll was not consumed on the save");
                            }
                        });

                        h.guard("scroll.holy.noScrollNoSave", () -> {
                            player.getInventory().clear();
                            if (holy.trySave(player) != null) {
                                throw new IllegalStateException("a player with no holy scroll must not be saved");
                            }
                        });

                        h.guard("scroll.nametag.renamesByIdentity", () -> {
                            player.getInventory().clear();
                            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
                            player.getInventory().setItem(0, sword);
                            if (nametags.begin(player.getUniqueId(), sword) == null) {
                                throw new IllegalStateException("begin should start a rename on a fresh player");
                            }
                            // Move the target between the click and the chat line — identity must follow it.
                            player.getInventory().setItem(0, null);
                            player.getInventory().setItem(5, sword);
                            String message = nametags.complete(player, "Renamed Blade");
                            if (message == null) {
                                throw new IllegalStateException("complete returned null with a pending rename");
                            }
                            ItemStack moved = player.getInventory().getItem(5);
                            ItemMeta meta = moved == null ? null : moved.getItemMeta();
                            if (meta == null || !meta.hasDisplayName()
                                    || !meta.getDisplayName().equals("Renamed Blade")) {
                                throw new IllegalStateException("the moved item was not renamed by identity: "
                                        + (meta == null ? "no meta" : meta.getDisplayName()));
                            }
                        });

                        h.guard("scroll.nametag.rejectsDoubleBegin", () -> {
                            nametags.clear(player.getUniqueId());
                            ItemStack a = new ItemStack(Material.DIAMOND_HELMET);
                            ItemStack b = new ItemStack(Material.DIAMOND_BOOTS);
                            if (nametags.begin(player.getUniqueId(), a) == null) {
                                throw new IllegalStateException("first begin should succeed");
                            }
                            if (nametags.begin(player.getUniqueId(), b) != null) {
                                throw new IllegalStateException("a second begin must be rejected while one pends");
                            }
                            nametags.clear(player.getUniqueId());
                        });

                        h.guard("scroll.nametag.refundsOnCancel", () -> {
                            player.getInventory().clear();
                            nametags.clear(player.getUniqueId());
                            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
                            player.getInventory().setItem(0, sword);
                            nametags.begin(player.getUniqueId(), sword);
                            nametags.complete(player, "cancel"); // aborts → the spent nametag is refunded
                            if (!hasNametag(nametags, player)) {
                                throw new IllegalStateException("a cancelled rename should refund a nametag");
                            }
                        });
                    } finally {
                        FakePlayers.despawn(player);
                    }
                });
            });
        });
    }

    private static int countHoly(HolyScrollService holy, Player player) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && holy.isHolyScroll(stack)) {
                n += stack.getAmount();
            }
        }
        return n;
    }

    private static boolean hasNametag(NametagService nametags, Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && nametags.isNametag(stack)) {
                return true;
            }
        }
        return false;
    }

    private static void failAll(Harness h, String message) {
        for (String key : KEYS) {
            h.fail(key, message);
        }
    }
}
