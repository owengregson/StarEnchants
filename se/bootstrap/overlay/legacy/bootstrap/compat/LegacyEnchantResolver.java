package bootstrap.compat;

import java.util.Locale;
import java.util.function.Function;
import org.bukkit.enchantments.Enchantment;

/**
 * Legacy (1.8.9) enchant-name resolver (ADR-0044) — the era-exclusive {@code overlay/legacy} name mapping,
 * extracted from the bindings so {@code EraBindings} stays construction/delegation only. 1.8 uses the pre-1.13
 * spelling ({@code PROTECTION} → {@code PROTECTION_ENVIRONMENTAL}, {@code UNBREAKING} → {@code DURABILITY},
 * {@code SHARPNESS} → {@code DAMAGE_ALL}), so the modern canonical name is mapped before the {@code getByName}
 * lookup; a miss falls back to the raw name, else {@code null}.
 */
public final class LegacyEnchantResolver implements Function<String, Enchantment> {

    @Override
    @SuppressWarnings("deprecation") // Enchantment.getByName is the 1.8 lookup (deprecated-not-removed)
    public Enchantment apply(String name) {
        String n = name == null ? "" : name.toUpperCase(Locale.ROOT);
        String legacy = switch (n) {
            case "PROTECTION" -> "PROTECTION_ENVIRONMENTAL";
            case "UNBREAKING" -> "DURABILITY";
            case "SHARPNESS" -> "DAMAGE_ALL";
            default -> n;
        };
        Enchantment byLegacy = Enchantment.getByName(legacy);
        return byLegacy != null ? byLegacy : Enchantment.getByName(n);
    }
}
