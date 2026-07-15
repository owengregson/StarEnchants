package feature.mask;

import feature.heroic.ModernVanillaStats;
import item.codec.CombatCodec;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * Modern (1.9+) masked-helmet break (ADR-0053 follow-up) — the era-exclusive {@code overlay/modern} source. A
 * masked helmet is an ordinary helmet plus the mask's PDC, so it wears like any helmet; when the wear about to
 * land would spend its last durability point, this takes over the break so the mask is popped back off intact
 * ({@link MaskBreakSalvage}) rather than destroyed with the helmet — the helmet still breaks whole, like vanilla.
 *
 * <p>Runs at {@code HIGHEST} after the {@code HIGH} heroic save with {@code ignoreCancelled}: a heroic helmet's
 * wear is cancelled (saved) upstream, so this fires only on a wear heroic lets through — a heroic piece keeps its
 * effective-higher durability and both tiers break at zero durability points alike. 1.8.9 has no
 * {@code PlayerItemDamageEvent}; the gear poll carries the break-salvage there (docs/legacy-1.8.9-codeshare-design.md §6).
 * Folia-correct: the event fires on the wearer's own region thread.
 */
public final class MaskDurabilityBreakListener implements Listener {

    private final CombatCodec codec;
    private final MaskBreakSalvage salvage;

    public MaskDurabilityBreakListener(CombatCodec codec, MaskBreakSalvage salvage) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.salvage = Objects.requireNonNull(salvage, "salvage");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (codec.read(item).maskKey() == null) {
            return; // not a masked helmet — the fast no-op (EMPTY blob → null)
        }
        if (!(item.getItemMeta() instanceof Damageable damage)) {
            return;
        }
        int max = ModernVanillaStats.effectiveMaxDurability(item); // heroic's real diamond max, else material max
        if (max <= 0 || damage.getDamage() + event.getDamage() < max) {
            return; // still has durability — let vanilla apply the wear
        }
        // The wear would spend the helmet: cancel vanilla's ambiguous destroy and break-and-salvage deterministically.
        event.setCancelled(true);
        salvage.breakAndSalvage(event.getPlayer(), item);
    }
}
