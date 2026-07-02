package platform.resolve;

import java.util.Map;
import schema.spec.HandleCategory;

/**
 * The live, cross-version handle-lookup seam behind {@link RegistryResolvers} and {@code RuntimeHandles}
 * (docs/architecture.md §9; cross-version-item-api). Resolving a version-volatile token (Material, Enchantment,
 * Sound, Particle, Attribute, …) to its live Bukkit object is the platform edge: the modern range shifts the API
 * under it (enums became registry-backed interfaces; the attribute {@code generic.} prefix dropped) and 1.8.9 has
 * no {@code Registry}/{@code NamespacedKey} at all. Two era-exclusive impls back it — {@code ModernHandleLookup}
 * (overlay/modern) and {@code LegacyHandleLookup} (overlay/legacy) — chosen by the {@code HandleLookups} bindings
 * twin so a {@code new RegistryResolvers()} below the composition root still resolves era-correctly (ADR-0044).
 */
public interface HandleLookup {

    /** Whether {@code canonicalName} (upper-case Bukkit style) resolves for {@code category} on this version. */
    boolean exists(HandleCategory category, String canonicalName);

    /** Era-only lossy fallbacks merged on top of the {@code Aliases} renames at resolve time (empty on the floor). */
    Map<String, String> fallbackAliases(HandleCategory category);

    /** The live Bukkit object {@code canonicalName} denotes for {@code category}, or {@code null} (never throws). */
    Object lookup(HandleCategory category, String canonicalName);
}
