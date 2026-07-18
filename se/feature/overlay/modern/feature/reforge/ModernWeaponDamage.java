package feature.reforge;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/**
 * Modern (1.17.1 → 26.1.x) weapon-swing reader (ADR-0071 Javelin). One swing = the live
 * GENERIC_ATTACK_DAMAGE attribute value, which already folds in the held weapon's modifier on 1.9+.
 * The attribute is resolved by REGISTRY KEY through the alias chain — the key dropped its
 * {@code generic.} prefix at 1.21 (the masks worn-hearts lesson: {@code resolveByName} alone would
 * skip that chain and read dead on 1.21.3+), so try the modern key then the legacy one. An
 * unresolvable attribute or a holder with no instance degrades to the 1.0 fist base.
 */
public final class ModernWeaponDamage implements WeaponDamage {

    /** Resolved ONCE: attack_damage by registry key (modern first, then the pre-1.21 {@code generic.} key). */
    private static final Attribute ATTACK_DAMAGE = byKey("attack_damage", "generic.attack_damage");

    public ModernWeaponDamage() {
    }

    @Override
    public double swingDamage(Player holder) {
        if (ATTACK_DAMAGE == null) {
            return 1.0;
        }
        AttributeInstance instance = holder.getAttribute(ATTACK_DAMAGE);
        return instance == null ? 1.0 : instance.getValue();
    }

    /** Resolve an attribute by registry key, trying the modern key then the pre-1.21 {@code generic.} key. */
    private static Attribute byKey(String modernKey, String legacyKey) {
        Attribute resolved = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(modernKey));
        return resolved != null ? resolved : Registry.ATTRIBUTE.get(NamespacedKey.minecraft(legacyKey));
    }
}
