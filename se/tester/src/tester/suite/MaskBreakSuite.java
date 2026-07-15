package tester.suite;

import feature.mask.MaskBreakSalvage;
import feature.mask.MaskDurabilityBreakListener;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.HeroicStat;
import item.codec.ItemKeys;
import item.codec.MaskCodec;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.Plugin;
import platform.sched.Scheduling;
import tester.harness.CombatRig;
import tester.harness.Harness;

/**
 * Masked-helmet durability break (ADR-0053 follow-up), live: a masked helmet worn to zero durability must break
 * like vanilla, popping the mask back off intact into the wearer's inventory (else dropped), instead of leaving
 * a spent helmet with the mask stuck on it. Wires the production {@link MaskDurabilityBreakListener} +
 * {@link MaskBreakSalvage} exactly as the composition root does (HeroicSuite's own-wiring pattern; the tester
 * hosts the classes but does not run the plugin's boot), with a lean minter that stamps a paper mask so a
 * salvage is detectable. The clientless harness cannot wear armour through combat, so the break is exercised
 * through a real {@code PlayerItemDamageEvent} and asserted on server-side state — the emptied helmet slot and
 * the salvaged mask, never client motion.
 *
 * <p>Two wearers pin both edges: one at max-1 durability (the wear breaks it → mask salvaged), one with plenty
 * left (the same wear is a no-op → helmet kept, nothing popped), so the break fires ONLY at zero durability.
 */
public final class MaskBreakSuite implements Harness.Scenario {

    private static final String MASK_KEY = "masks/blaze";

    private final Plugin plugin;

    public MaskBreakSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("maskbreak.spentHelmetBreaks");
        h.expect("maskbreak.maskSalvagedToInventory");
        h.expect("maskbreak.durableHelmetKept");

        CombatCodec codec = new CombatCodec(ItemKeys.of().combat(), Stores.state());
        MaskCodec maskCodec = new MaskCodec(ItemKeys.of(), Stores.state());
        int max = Material.DIAMOND_HELMET.getMaxDurability();

        // Lean minter (production passes MaskService::mint): a paper stack stamped with the mask identity, so the
        // salvaged item is recognised by MaskCodec exactly as a real mask would be.
        MaskBreakSalvage salvage = new MaskBreakSalvage(codec, key -> {
            ItemStack mask = new ItemStack(Material.PAPER);
            maskCodec.stamp(mask, key);
            return mask;
        });

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;
        CombatRig rig = new CombatRig(plugin);
        rig.listen(new MaskDurabilityBreakListener(codec, salvage)); // the production listener, wired here

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                Player breakGuy;
                Player keepGuy;
                try {
                    breakGuy = rig.spawnFake(world, "se_mb_break");
                    keepGuy = rig.spawnFake(world, "se_mb_keep");
                } catch (Throwable t) {
                    h.fail("maskbreak.spentHelmetBreaks", "fake-player spawn: " + t);
                    h.fail("maskbreak.maskSalvagedToInventory", "fake-player spawn: " + t);
                    h.fail("maskbreak.durableHelmetKept", "fake-player spawn: " + t);
                    return;
                }

                Scheduling.onEntity(breakGuy, () -> {
                    breakGuy.getInventory().setHelmet(maskedHelmet(codec, max - 1)); // one wear from breaking
                    ItemStack worn = breakGuy.getInventory().getHelmet();
                    plugin.getServer().getPluginManager().callEvent(new PlayerItemDamageEvent(breakGuy, worn, 1));
                });
                Scheduling.onEntity(keepGuy, () -> {
                    keepGuy.getInventory().setHelmet(maskedHelmet(codec, max - 300)); // plenty left
                    ItemStack worn = keepGuy.getInventory().getHelmet();
                    plugin.getServer().getPluginManager().callEvent(new PlayerItemDamageEvent(keepGuy, worn, 1));
                });

                Scheduling.onEntityLater(breakGuy, 5L, () -> {
                    h.guard("maskbreak.spentHelmetBreaks", () -> {
                        if (!broke(breakGuy)) {
                            throw new IllegalStateException("spent masked helmet was not broken at zero durability — "
                                    + describe(breakGuy));
                        }
                    });
                    h.guard("maskbreak.maskSalvagedToInventory", () -> {
                        String salvaged = firstMaskKey(breakGuy, maskCodec);
                        if (salvaged == null) {
                            throw new IllegalStateException("the mask was not popped into the wearer's inventory when "
                                    + "the helmet broke — it was lost with the helmet");
                        }
                        if (!MASK_KEY.equals(salvaged)) {
                            throw new IllegalStateException("salvaged the wrong mask: " + salvaged);
                        }
                    });
                });
                Scheduling.onEntityLater(keepGuy, 5L, () -> {
                    h.guard("maskbreak.durableHelmetKept", () -> {
                        if (broke(keepGuy)) {
                            throw new IllegalStateException("a masked helmet with durability to spare was broken by a "
                                    + "single wear point — the break must fire only at zero durability");
                        }
                        if (firstMaskKey(keepGuy, maskCodec) != null) {
                            throw new IllegalStateException("a mask was popped off a helmet that had not broken");
                        }
                    });
                    rig.teardown();
                });
            });
        });
    }

    /** A DIAMOND_HELMET carrying {@code damage}, with the mask stamped into its combat blob as ItemEnchanter does. */
    private static ItemStack maskedHelmet(CombatCodec codec, int damage) {
        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
        Damageable meta = (Damageable) helmet.getItemMeta();
        meta.setDamage(damage);
        helmet.setItemMeta(meta);
        codec.write(helmet, new CombatState(Map.of(), List.of(), null, null, false, HeroicStat.NONE, 0, MASK_KEY));
        return helmet;
    }

    /** Whether {@code player}'s helmet slot is now empty — the break removed the spent piece. */
    private static boolean broke(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        return helmet == null || helmet.getType() == Material.AIR;
    }

    /** The mask key of the first mask item anywhere in {@code player}'s inventory, or {@code null} if none. */
    private static String firstMaskKey(Player player, MaskCodec maskCodec) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && maskCodec.isMask(item)) {
                return maskCodec.keyOf(item);
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation") // getDurability: floor-stable damage read, for the diagnostic only
    private static String describe(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null) {
            return "helmet slot empty";
        }
        return "helmet=" + helmet.getType() + " durabilityDamage=" + helmet.getDurability()
                + "/" + helmet.getType().getMaxDurability();
    }
}
