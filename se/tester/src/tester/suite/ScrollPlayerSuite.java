package tester.suite;

import compile.load.ScrollsConfig;
import feature.scroll.HolyScrollService;
import feature.scroll.NametagService;
import feature.scroll.ScrollResult;
import item.codec.AppliedSlot;
import item.codec.ItemKeys;
import item.codec.ScrollCodec;
import java.util.List;
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
 * §I player-bound scrolls needing a real {@link Player} inventory: holy-scroll apply + death-keep, and nametag
 * that re-locates its target by IDENTITY (survives the item moving between click and chat line), refunds a
 * cancelled rename, refuses a second concurrent one. Fake player; assertions on its own region thread.
 */
public final class ScrollPlayerSuite implements Harness.Scenario {

    private static final String[] KEYS = {
        "scroll.holy.appliesAndOccupiesSlot", "scroll.holy.keepsMarkedItemOnDeath",
        "scroll.nametag.renamesByIdentity", "scroll.nametag.rejectsDoubleBegin", "scroll.nametag.refundsOnCancel",
    };

    private final Plugin plugin;

    public ScrollPlayerSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    @SuppressWarnings("deprecation") // getDisplayName(): the floor-stable String item-meta API the suite asserts against (Component name is Adventure-only).
    public void accept(Harness h) {
        for (String key : KEYS) {
            h.expect(key);
        }

        ItemKeys keys = ItemKeys.of();
        ScrollCodec scrollCodec = new ScrollCodec(keys.scroll());
        AppliedSlot slot = new AppliedSlot(keys.appliedSlot());
        HolyScrollService holy = new HolyScrollService(scrollCodec, slot, ScrollsConfig::defaults, new Random(1)); // 100%
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

                        h.guard("scroll.holy.appliesAndOccupiesSlot", () -> {
                            ItemStack scroll = holy.mint();
                            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
                            ScrollResult result = holy.applyTo(scroll, sword); // default 100% → always applies
                            if (!result.commit()) {
                                throw new IllegalStateException("holy apply did not commit at 100%: " + result);
                            }
                            if (scroll.getAmount() != 0) {
                                throw new IllegalStateException("the holy scroll was not consumed on apply");
                            }
                            if (!slot.holds(sword, AppliedSlot.HOLY)) {
                                throw new IllegalStateException("the sword's applied-slot is not HOLY after apply");
                            }
                        });

                        h.guard("scroll.holy.keepsMarkedItemOnDeath", () -> {
                            ItemStack marked = new ItemStack(Material.DIAMOND_CHESTPLATE);
                            slot.occupy(marked, AppliedSlot.HOLY);
                            ItemStack plain = new ItemStack(Material.DIRT);
                            List<ItemStack> drops = new java.util.ArrayList<>(List.of(marked, plain));
                            List<ItemStack> kept = holy.keepFromDrops(drops);
                            if (kept.size() != 1 || kept.get(0).getType() != Material.DIAMOND_CHESTPLATE) {
                                throw new IllegalStateException("the marked item was not kept: " + kept);
                            }
                            if (drops.contains(marked)) {
                                throw new IllegalStateException("the kept item was not removed from drops");
                            }
                            if (slot.holds(kept.get(0), AppliedSlot.HOLY)) {
                                throw new IllegalStateException("the keep marker was not consumed on death");
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
