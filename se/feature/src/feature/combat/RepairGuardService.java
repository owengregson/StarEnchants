package feature.combat;

import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import item.view.ItemViewCache;
import item.worn.EquipSource;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import platform.resolve.RegistryResolvers;

/**
 * Cosmic Repair Guard: when a sufficiently damaged enchanted armour piece is removed, grant the wearer
 * a short absorption window. The original's inverted/integer durability comparison was accidental; this
 * implementation compares real remaining durability against its intended 15/20/25 percent thresholds.
 */
public final class RepairGuardService implements Listener {

    static final String ENCHANT = "enchants/repair-guard";
    static final long COOLDOWN_TICKS = 600L;
    private static final int ARMOUR_SLOTS = 4;

    private final EquipSource equipment;
    private final ItemViewCache views;
    private final SinkFactory sinks;
    private final SinkEnv env;
    private final LongSupplier nowTicks;
    private final int absorption;
    private final Map<UUID, ItemStack[]> previousArmour = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> explicitUnequips = new ConcurrentHashMap<>();

    public RepairGuardService(EquipSource equipment, ItemViewCache views, SinkFactory sinks, SinkEnv env,
                              RegistryResolvers resolvers, LongSupplier nowTicks) {
        this.equipment = Objects.requireNonNull(equipment, "equipment");
        this.views = Objects.requireNonNull(views, "views");
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.absorption = Objects.requireNonNull(resolvers, "resolvers")
                .potionEffect("ABSORPTION").orElse(-1);
    }

    /** Modern event path: Paper supplies the exact old stack at the instant it leaves its armour slot. */
    public void onUnequip(Player player, ItemStack removed) {
        if (removed == null || removed.getType() == Material.AIR) {
            return;
        }
        explicitUnequips.put(player.getUniqueId(), nowTicks.getAsLong());
        activate(player, removed);
    }

    /**
     * Snapshot path used by the legacy gear poll (and as a safety net for event-less modern mutations).
     * A first refresh seeds state and never procs. Modern explicit events stamp this tick so the same change
     * cannot be consumed twice by the post-refresh comparison.
     */
    public void refresh(Player player) {
        UUID playerId = player.getUniqueId();
        ItemStack[] current = armourSnapshot(player);
        ItemStack[] previous = previousArmour.put(playerId, current);
        if (previous == null) {
            return;
        }
        Long explicitAt = explicitUnequips.remove(playerId);
        if (explicitAt != null && explicitAt == nowTicks.getAsLong()) {
            return;
        }
        for (int slot = 0; slot < ARMOUR_SLOTS; slot++) {
            ItemStack old = previous[slot];
            ItemStack replacement = current[slot];
            if (old == null || old.getType() == Material.AIR || sameStack(old, replacement)) {
                continue;
            }
            if (activate(player, old)) {
                break; // Repair Guard has one player-wide cooldown, exactly as the source enchant instance did.
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        previousArmour.remove(playerId);
        cooldowns.remove(playerId);
        explicitUnequips.remove(playerId);
    }

    private boolean activate(Player player, ItemStack removed) {
        int level = views.of(removed).combat().enchants().getOrDefault(ENCHANT, 0);
        if (level <= 0 || level > 3 || !belowThreshold(removed, level) || absorption < 0) {
            return false;
        }
        long now = nowTicks.getAsLong();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_TICKS) {
            return false;
        }
        cooldowns.put(player.getUniqueId(), now);
        SinkReadback sink = sinks.create(env);
        // Source code deliberately used amplifiers 3/4/5, durations 3/4/5 seconds, and force replacement.
        sink.potionForce(player, absorption, 2 + level, (2 + level) * 20);
        sink.flush();
        return true;
    }

    @SuppressWarnings("deprecation") // ItemStack durability is the common 1.8 + modern compatibility seam.
    static boolean belowThreshold(ItemStack item, int level) {
        int maximum = item == null ? 0 : item.getType().getMaxDurability();
        if (maximum <= 0) {
            return false;
        }
        int damage = Math.max(0, item.getDurability());
        double remaining = (maximum - Math.min(maximum, damage)) / (double) maximum;
        return remaining <= 0.10 + (0.05 * level);
    }

    private ItemStack[] armourSnapshot(Player player) {
        ItemStack[] all = equipment.snapshot(player);
        ItemStack[] armour = new ItemStack[ARMOUR_SLOTS];
        if (all == null) {
            return armour;
        }
        for (int slot = 0; slot < Math.min(ARMOUR_SLOTS, all.length); slot++) {
            ItemStack stack = all[slot];
            armour[slot] = stack == null ? null : stack.clone();
        }
        return armour;
    }

    private static boolean sameStack(ItemStack left, ItemStack right) {
        return right != null && left.isSimilar(right) && left.getAmount() == right.getAmount();
    }
}
