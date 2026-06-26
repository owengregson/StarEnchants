package engine.run;

import org.bukkit.entity.LivingEntity;

/**
 * Modern (1.9+) main-hand read for {@link FactPopulator} — the one off-/main-hand API call pulled out of
 * shared {@code main} so the fact core stays version-agnostic (docs/legacy-1.8.9-codeshare-design.md §3.3).
 * Same-FQN counterpart to the {@code overlay/legacy} {@code getItemInHand()} impl.
 */
public final class HeldItem {

    private HeldItem() {
    }

    /** The entity's main-hand item type name, or {@code null} if it has no equipment. */
    public static String mainHandTypeName(LivingEntity entity) {
        return entity.getEquipment() == null ? null
                : entity.getEquipment().getItemInMainHand().getType().name();
    }
}
