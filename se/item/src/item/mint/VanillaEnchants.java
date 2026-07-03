package item.mint;

import java.util.Map;
import java.util.function.Function;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/**
 * Cross-version vanilla-enchant application (§6.6) as INSTANCE wiring (ADR-0047 — retires the
 * {@link ItemFactory} enchant-resolver static installer). Resolves a modern canonical enchant NAME
 * ({@code PROTECTION}, {@code UNBREAKING}, {@code SHARPNESS}) cross-version behind the era seam (the modern
 * overlay via the namespaced-key registry, the legacy overlay via the 1.8 names) and applies it, bypassing
 * the vanilla level cap. {@link #NONE} is the inert no-op every unit test / pure context uses so this stays
 * server-free.
 */
public final class VanillaEnchants {

    /** No resolver — every name misses, so {@link #apply} is a no-op. Keeps menu/mint unit tests server-free. */
    public static final VanillaEnchants NONE = new VanillaEnchants(name -> null);

    private final Function<String, Enchantment> resolver;

    public VanillaEnchants(Function<String, Enchantment> resolver) {
        this.resolver = resolver == null ? name -> null : resolver;
    }

    /**
     * Apply vanilla enchants by NAME ({@code name → level}) to {@code stack} in place — the cross-version mint
     * path for set-piece base enchants (Protection/Unbreaking/Sharpness, §6.6). Unknown names (resolver miss)
     * are skipped, never throwing. {@code addUnsafeEnchantment} bypasses the vanilla level cap.
     */
    public void apply(ItemStack stack, Map<String, Integer> nameToLevel) {
        if (stack == null || nameToLevel == null || nameToLevel.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Integer> entry : nameToLevel.entrySet()) {
            Enchantment enchant = resolver.apply(entry.getKey());
            if (enchant != null) {
                stack.addUnsafeEnchantment(enchant, Math.max(1, entry.getValue()));
            }
        }
    }
}
