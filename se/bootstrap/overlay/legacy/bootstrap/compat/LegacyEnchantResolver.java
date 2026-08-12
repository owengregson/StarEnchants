package bootstrap.compat;

import java.util.Map;
import java.util.function.Function;
import org.bukkit.enchantments.Enchantment;
import platform.resolve.Aliases;
import schema.spec.HandleCategory;

/**
 * Legacy (1.8.9) enchant-name resolver (ADR-0044) — the era-exclusive {@code overlay/legacy} name mapping,
 * extracted from the bindings so {@code EraBindings} stays construction/delegation only. 1.8 keys
 * {@code Enchantment.getByName} on the pre-1.13 spellings, so a modern-authored token
 * ({@code POWER}, {@code UNBREAKING}, …) is mapped back through {@link Aliases}, the single home for that
 * knowledge — the modern lane walks the same table via {@code HandleResolver}.
 */
public final class LegacyEnchantResolver implements Function<String, Enchantment> {

    @Override
    @SuppressWarnings("deprecation") // Enchantment.getByName is the 1.8 lookup (deprecated-not-removed)
    public Enchantment apply(String name) {
        String token = Aliases.normalize(name);
        Enchantment direct = Enchantment.getByName(token);
        if (direct != null) {
            return direct;
        }
        // Aliases is keyed era-name → modern-name, so a modern token needs the reverse scan (values are distinct).
        for (Map.Entry<String, String> alias : Aliases.forCategory(HandleCategory.ENCHANTMENT).entrySet()) {
            if (alias.getValue().equals(token)) {
                Enchantment era = Enchantment.getByName(alias.getKey());
                if (era != null) {
                    return era;
                }
            }
        }
        return null;
    }
}
