package platform.resolve;

import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;
import schema.spec.HandleCategory;

/**
 * Legacy (1.8.9) counterpart of the modern {@link RegistrySupport} — same FQN, package-private, static,
 * selected into the build only on {@code -Pse.target=legacy}. 1.8 has no {@code Registry}/
 * {@code NamespacedKey}/{@code Particle}/{@code Attribute}, so handles resolve through the 1.8-era
 * {@code getByName}/{@code valueOf} APIs.
 *
 * <p>Names arrive here already in their 1.8 form: {@link HandleResolver} interns the alias KEY whose value
 * is the modern token (so {@code SHARPNESS}&rarr;{@code DAMAGE_ALL}, {@code NAUSEA}&rarr;{@code CONFUSION},
 * {@code HAPPY_VILLAGER}&rarr;{@code VILLAGER_HAPPY} via {@link Aliases}), and {@code RenameResolvers.nameOf}
 * later hands that same 1.8 name to the legacy {@code DispatchSink} for NMS resolution. Particles and
 * attributes have no Bukkit object on 1.8 (the legacy dispatcher resolves them to NMS by name), so they are
 * validated against fixed 1.8 name sets. Those sets are a STARTER set; completing them — and the per-name
 * NMS mapping — is the Gate-3 legacy-table hardening (docs/legacy-1.8.9-codeshare-design.md §6 R3, Phase 3).
 */
final class RegistrySupport {

    private RegistrySupport() {
    }

    /** 1.8 NMS {@code EnumParticle} names (Bukkit had no {@code Particle} type on 1.8). Starter set. */
    private static final Set<String> PARTICLES_1_8 = Set.of(
            "EXPLOSION_NORMAL", "EXPLOSION_LARGE", "EXPLOSION_HUGE", "FIREWORKS_SPARK", "WATER_BUBBLE",
            "WATER_SPLASH", "WATER_WAKE", "SUSPENDED", "SUSPENDED_DEPTH", "CRIT", "CRIT_MAGIC", "SMOKE_NORMAL",
            "SMOKE_LARGE", "SPELL", "SPELL_INSTANT", "SPELL_MOB", "SPELL_MOB_AMBIENT", "SPELL_WITCH",
            "DRIP_WATER", "DRIP_LAVA", "VILLAGER_ANGRY", "VILLAGER_HAPPY", "TOWN_AURA", "NOTE", "PORTAL",
            "ENCHANTMENT_TABLE", "FLAME", "LAVA", "FOOTSTEP", "CLOUD", "REDSTONE", "SNOWBALL", "SNOW_SHOVEL",
            "SLIME", "HEART", "BARRIER", "ITEM_CRACK", "BLOCK_CRACK", "BLOCK_DUST", "WATER_DROP", "MOB_APPEARANCE");

    /** 1.8 attributes reachable via NMS {@code GenericAttributes} (no {@code org.bukkit.attribute} on 1.8). Starter set. */
    private static final Set<String> ATTRIBUTES_1_8 = Set.of(
            "GENERIC_MAX_HEALTH", "GENERIC_MOVEMENT_SPEED", "GENERIC_ATTACK_DAMAGE",
            "GENERIC_KNOCKBACK_RESISTANCE", "GENERIC_FOLLOW_RANGE", "GENERIC_ATTACK_SPEED");

    /**
     * Lossy 1.8-only DEGRADATIONS merged on top of the {@link Aliases} renames at resolve time: a token with
     * no 1.8 equivalent maps to the closest 1.8 constant so the effect still fires (visibly degraded) instead
     * of being warn-skipped. Kept OUT of {@link Aliases} because these are not renames — the migrator must not
     * rewrite a modern config through them. {@code SOUL} (1.16) &rarr; the 1.8 large-smoke particle.
     */
    private static final java.util.Map<HandleCategory, java.util.Map<String, String>> FALLBACKS_1_8 =
            java.util.Map.of(HandleCategory.PARTICLE, java.util.Map.of("SOUL", "SMOKE_LARGE"));

    /** Whether {@code canonicalName} (1.8-era, upper-case) resolves for {@code category} on 1.8.9. */
    static boolean exists(HandleCategory category, String canonicalName) {
        return switch (category) {
            case PARTICLE -> PARTICLES_1_8.contains(canonicalName);
            case ATTRIBUTE -> ATTRIBUTES_1_8.contains(canonicalName);
            default -> lookup(category, canonicalName) != null;
        };
    }

    /** Legacy-only lossy fallbacks ({@link #FALLBACKS_1_8}) merged on top of {@link Aliases} for resolution. */
    static java.util.Map<String, String> fallbackAliases(HandleCategory category) {
        return FALLBACKS_1_8.getOrDefault(category, java.util.Map.of());
    }

    /** The live 1.8 Bukkit object {@code canonicalName} denotes, or {@code null} (particle/attribute → NMS-by-name). */
    static Object lookup(HandleCategory category, String canonicalName) {
        try {
            return switch (category) {
                case MATERIAL -> Material.getMaterial(canonicalName);
                case ENCHANTMENT -> Enchantment.getByName(canonicalName);
                case POTION_EFFECT -> PotionEffectType.getByName(canonicalName);
                case ENTITY_TYPE -> entityType(canonicalName);
                case SOUND -> sound(canonicalName);
                case PARTICLE, ATTRIBUTE -> null; // no Bukkit type on 1.8; resolved to NMS by name downstream
            };
        } catch (Throwable failedProbe) {
            return null; // any linkage/lookup failure means "not resolvable here" — caller warn-skips
        }
    }

    @SuppressWarnings("deprecation") // EntityType.fromName is the 1.8 lowercase-name lookup; deprecated but present
    private static Object entityType(String canonicalName) {
        try {
            return EntityType.valueOf(canonicalName);
        } catch (IllegalArgumentException notAnEnumConstant) {
            return EntityType.fromName(canonicalName.toLowerCase(Locale.ROOT));
        }
    }

    private static Object sound(String canonicalName) {
        try {
            return Sound.valueOf(canonicalName); // 1.8 Sound is a plain enum
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
