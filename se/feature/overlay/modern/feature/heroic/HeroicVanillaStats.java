package feature.heroic;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Modern (1.17.1 → 26.1.x) heroic vanilla-stats writer (ADR-0031). When {@code vanilla-stats} is on, a heroic
 * ARMOUR piece carries REAL vanilla armour-point + toughness attribute modifiers (diamond values, replacing the
 * weak display material's defaults) and — where the platform supports it (Minecraft 1.20.5+) — a custom diamond
 * max durability. So the armour points and durability are correct on the HUD and read by other combat plugins
 * that recompute from vanilla armour/durability — e.g. Mental's "restore 1.8 armour/durability", which a heroic
 * gold piece otherwise defeats (it sees gold's points). A sub-diamond WEAPON likewise carries a real diamond
 * {@code attack_damage} modifier (ADR 0032). No {@code HIDE_ATTRIBUTES}: an explicit modifier set suppresses the
 * display material's defaults, so the vanilla tooltip renders exactly the diamond values. Every heroic piece also
 * carries the vendor-neutral {@code combat:effective_material} marker naming the diamond it stands in for, so an
 * era-combat plugin (Mental) presents/computes the legacy form on its own servers (docs/…/effective-material-contract).
 *
 * <p>Cross-version edges (verified against the cached reference jars, never guessed): the attribute is resolved
 * by REGISTRY KEY (the key dropped its {@code generic.} prefix at 1.21 — try modern then legacy), the modifier is
 * built with the {@code (UUID, name, amount, op, slot)} ctor (present floor → ceiling), and {@code setMaxDamage}
 * is reflected (it is 1.20.5+, absent on the floor). The 1.8.9 fork has its own no-op counterpart (same FQN).
 */
public final class HeroicVanillaStats {

    /** Resolved ONCE: the armour / toughness / attack-damage attributes by registry key (modern first, then ≤1.20.6). */
    private static final Attribute ARMOUR = byKey("armor", "generic.armor");
    private static final Attribute TOUGHNESS = byKey("armor_toughness", "generic.armor_toughness");
    private static final Attribute ATTACK_DAMAGE = byKey("attack_damage", "generic.attack_damage");

    /** {@code Damageable.setMaxDamage} (1.20.5+) reflected once; {@code null} on an older platform (no override). */
    private static final Method SET_MAX_DAMAGE = probeSetMaxDamage();
    /** {@code Damageable.hasMaxDamage}/{@code getMaxDamage} (1.20.5+), reflected once for the effective-max read. */
    private static final Method HAS_MAX_DAMAGE = probeNoArg("hasMaxDamage");
    private static final Method GET_MAX_DAMAGE = probeNoArg("getMaxDamage");

    private HeroicVanillaStats() {
    }

    /**
     * The item's EFFECTIVE max durability — the custom {@code max_damage} (1.20.5+) if this piece carries one (a
     * vanilla-stats heroic piece does: diamond's max), else the display material's max. The durability listener
     * scales its wear-cancel off THIS, so once a real diamond max is set the scaling stops (the real bar is the
     * ledger and a 1.8-durability restore reads it); on an older platform with no override it is the material max,
     * preserving the pre-ADR-0031 scaling behaviour.
     */
    public static int effectiveMaxDurability(ItemStack stack) {
        int materialMax = stack.getType().getMaxDurability();
        if (HAS_MAX_DAMAGE == null || GET_MAX_DAMAGE == null) {
            return materialMax;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable)) {
            return materialMax;
        }
        try {
            if (Boolean.TRUE.equals(HAS_MAX_DAMAGE.invoke(meta))) {
                return ((Number) GET_MAX_DAMAGE.invoke(meta)).intValue();
            }
        } catch (ReflectiveOperationException ignored) {
            // verified present at probe time; swallow and fall back to the material max
        }
        return materialMax;
    }

    /**
     * Write the heroic vanilla stats onto {@code stack} in place: real diamond modifiers — armour-point +
     * toughness for a sub-diamond ARMOUR piece, attack-damage for a sub-diamond WEAPON — plus a diamond max
     * durability for any gear (where supported) and the vendor-neutral {@code combat:effective_material} marker.
     * NO {@code HIDE_ATTRIBUTES}: an explicit modifier set suppresses the display material's defaults, so the
     * vanilla tooltip renders exactly the diamond values (Mental re-values / strips lines per its era modules).
     * Returns {@code true} iff real attributes were written for THIS piece (armour when armour, weapon when
     * weapon) — the caller then drops the matching plugin-maths flat delta so the two never double-count. A
     * diamond/netherite display returns {@code false}.
     */
    public static boolean apply(ItemStack stack, boolean weapon) {
        if (stack == null) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Material type = stack.getType();
        boolean realAttrs = false;
        try {
            if (!weapon && ARMOUR != null && HeroicDiamond.displayBelowDiamondArmour(type)) {
                realAttrs = writeArmour(meta, type);
            } else if (weapon && ATTACK_DAMAGE != null && isSubDiamondWeapon(type)) {
                realAttrs = writeWeapon(meta, type);
            }
            stampEffectiveMaterial(meta, type); // neutral cross-plugin marker (armour + weapon)
            applyMaxDurability(meta, type);      // both armour and weapon
        } catch (RuntimeException unexpected) {
            // Never let an attribute/durability hiccup break the forge — fall back to plugin-maths for this piece.
            return false;
        }
        stack.setItemMeta(meta);
        return realAttrs;
    }

    /** Replace the piece's armour defaults with diamond armour + toughness on its slot (explicit ⇒ defaults suppressed). */
    private static boolean writeArmour(ItemMeta meta, Material type) {
        EquipmentSlot slot = slotOf(type);
        if (slot == null) {
            return false;
        }
        addModifier(meta, ARMOUR, "armor", slot, HeroicDiamond.diamondArmourPoints(type));
        if (TOUGHNESS != null) {
            addModifier(meta, TOUGHNESS, "toughness", slot, HeroicDiamond.diamondArmourToughness(type));
        }
        // No HIDE_ATTRIBUTES (ADR 0032): the explicit set suppresses the gold defaults, so the vanilla tooltip
        // shows exactly these diamond values; Mental strips the toughness line on a 1.8 server.
        return true;
    }

    /** Replace a sub-diamond weapon's attack with diamond's — modifier = diamond total − base 1.0, on the hand. */
    private static boolean writeWeapon(ItemMeta meta, Material type) {
        addModifier(meta, ATTACK_DAMAGE, "attack_damage", EquipmentSlot.HAND,
                HeroicDiamond.diamondAttackDamage(type) - 1.0);
        return true;
    }

    /** A sub-diamond melee weapon (sword/axe below diamond) — the signal to write a real diamond attack modifier. */
    private static boolean isSubDiamondWeapon(Material type) {
        return HeroicDiamond.diamondAttackDamage(type) > 0.0 && HeroicDiamond.diamondMaterialName(type) != null;
    }

    /**
     * Stamp the vendor-neutral {@code combat:effective_material} marker with the diamond this sub-diamond piece
     * stands in for, so an era-combat plugin (Mental) treats the gold display as diamond for legacy stats. No-op
     * once the piece is already ≥ diamond or isn't heroic gear. Modern-only — 1.8.9 has no PDC and no Mental.
     */
    private static void stampEffectiveMaterial(ItemMeta meta, Material type) {
        String trueMaterial = HeroicDiamond.diamondMaterialName(type);
        if (trueMaterial == null) {
            return;
        }
        NamespacedKey key = NamespacedKey.fromString("combat:effective_material");
        if (key != null) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, trueMaterial);
        }
    }

    private static void addModifier(ItemMeta meta, Attribute attr, String tag, EquipmentSlot slot, double amount) {
        // The (UUID, name, amount, op, slot) ctor is undeprecated on the 1.17.1 floor this compiles against and is
        // still present at the ceiling (verified on 26.1.2), so it binds and runs across the whole range.
        // A stable per-(stat, slot) id so a piece carries exactly one of each (and a re-apply is idempotent, not a dup).
        UUID id = UUID.nameUUIDFromBytes(("starenchants:heroic:" + tag + ":" + slot.name()).getBytes(StandardCharsets.UTF_8));
        meta.removeAttributeModifier(attr); // clear any prior modifiers for this attribute on this piece first
        meta.addAttributeModifier(attr,
                new AttributeModifier(id, "starenchants.heroic." + tag, amount, AttributeModifier.Operation.ADD_NUMBER, slot));
    }

    /** Set a diamond max durability where the platform supports {@code setMaxDamage} (1.20.5+) and it is an upgrade. */
    private static void applyMaxDurability(ItemMeta meta, Material type) {
        if (SET_MAX_DAMAGE == null || !(meta instanceof Damageable)) {
            return;
        }
        int diamondMax = HeroicDiamond.diamondDurability(type);
        if (diamondMax <= 0 || type.getMaxDurability() >= diamondMax) {
            return; // not gear, or the display material is already at least diamond-durable
        }
        try {
            SET_MAX_DAMAGE.invoke(meta, Integer.valueOf(diamondMax));
        } catch (ReflectiveOperationException ignored) {
            // verified present at probe time; swallow so a reflective slip never breaks the forge
        }
    }

    private static EquipmentSlot slotOf(Material type) {
        String name = type.name();
        if (name.endsWith("_HELMET")) {
            return EquipmentSlot.HEAD;
        }
        if (name.endsWith("_CHESTPLATE")) {
            return EquipmentSlot.CHEST;
        }
        if (name.endsWith("_LEGGINGS")) {
            return EquipmentSlot.LEGS;
        }
        if (name.endsWith("_BOOTS")) {
            return EquipmentSlot.FEET;
        }
        return null;
    }

    /** Resolve an attribute by registry key, trying the modern key then the pre-1.21 {@code generic.} key. */
    private static Attribute byKey(String modernKey, String legacyKey) {
        Attribute resolved = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(modernKey));
        return resolved != null ? resolved : Registry.ATTRIBUTE.get(NamespacedKey.minecraft(legacyKey));
    }

    /** Probe {@code Damageable.setMaxDamage} once — {@code Integer} (current) or {@code int} (early 1.20.5) overload. */
    private static Method probeSetMaxDamage() {
        try {
            return Damageable.class.getMethod("setMaxDamage", Integer.class);
        } catch (NoSuchMethodException notInteger) {
            try {
                return Damageable.class.getMethod("setMaxDamage", int.class);
            } catch (NoSuchMethodException absent) {
                return null; // pre-1.20.5: no custom max durability — the wear-cancel scaling remains the path
            }
        }
    }

    /** Probe a no-arg {@code Damageable} method (1.20.5+); {@code null} on an older platform. */
    private static Method probeNoArg(String name) {
        try {
            return Damageable.class.getMethod(name);
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }
}
