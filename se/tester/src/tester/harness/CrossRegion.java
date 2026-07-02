package tester.harness;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

/**
 * Shared cross-region staging for the Folia combat suites ({@code AffinityAutogenSuite},
 * {@code CrossRegionTeleportSuite}): the block gap that guarantees two staged points own DISTINCT Folia regions,
 * and the reflective ownership probe those suites assert that staging with. One source so the two suites can
 * never drift apart on what "a different region" means.
 */
public final class CrossRegion {

    private CrossRegion() {
    }

    /**
     * {@value} blocks: far past Folia's region-merge radius, so two points this far apart own distinct regions.
     * 512 empirically collapsed into ONE region on a fresh Folia test world — the staging assertion caught it —
     * so both suites stage from this single, comfortably-larger source.
     */
    public static final int GAP = 8192;

    /** {@code null} = the region-ownership API is absent (pre-1.20 Paper); every Folia build has it. */
    public static Boolean ownedByCurrentRegion(Entity entity) {
        try {
            return (Boolean) Bukkit.class.getMethod("isOwnedByCurrentRegion", Entity.class).invoke(null, entity);
        } catch (ReflectiveOperationException absent) {
            return null;
        }
    }
}
