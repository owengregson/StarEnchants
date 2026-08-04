package bootstrap.wire;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bootstrap.compat.EraServices;
import engine.run.AreaScan;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

/**
 * The block-volume selectors' material filter, which is the only thing standing between an excavation enchant
 * and the blocks a server cannot afford to lose. Both halves fail silently and catastrophically: an ignored
 * deny list means one swing eats bedrock, obsidian and spawners inside the volume; an over-eager one means the
 * enchant mines nothing and reads as broken. The empty-empty case is a performance contract as much as a
 * behavioural one — an unfiltered shape must not read the world once per candidate block.
 */
class AreaScanMaterialFilterTest {

    private static final int STONE_ID = 1;
    private static final int BEDROCK_ID = 2;
    private static final int DIRT_ID = 3;

    private World world;

    /** The seam under test, reading a world whose every block is {@code type}. */
    private AreaScan scanning(Material type) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        world = mock(World.class);
        when(world.getBlockAt(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(block);

        EraServices bindings = mock(EraServices.class);
        when(bindings.material(STONE_ID)).thenReturn(Material.STONE);
        when(bindings.material(BEDROCK_ID)).thenReturn(Material.BEDROCK);
        when(bindings.material(DIRT_ID)).thenReturn(Material.DIRT);
        return BootCore.areaScan(bindings);
    }

    private Location at() {
        return new Location(world, 0, 64, 0);
    }

    @Test
    void aDeniedBlockIsDroppedWithNoAllowListAtAll() {
        // The Detonate shape: no allow list is writable (every mineable block on every version), so the whole
        // guard is the deny list. If it were ignored the volume would break bedrock.
        AreaScan scan = scanning(Material.BEDROCK);

        assertFalse(scan.materialMatches(at(), List.of(), List.of(BEDROCK_ID)));
    }

    @Test
    void anUnlistedBlockPassesADenyOnlyFilter() {
        AreaScan scan = scanning(Material.STONE);

        assertTrue(scan.materialMatches(at(), List.of(), List.of(BEDROCK_ID)));
    }

    @Test
    void denyWinsOverAllow() {
        // A type on both lists is refused. The other reading — allow overrides — would let a broad allow list
        // silently re-admit the exact blocks the deny list exists to protect.
        AreaScan scan = scanning(Material.BEDROCK);

        assertFalse(scan.materialMatches(at(), List.of(BEDROCK_ID, STONE_ID), List.of(BEDROCK_ID)));
    }

    @Test
    void bothHalvesApplyTogether() {
        AreaScan scan = scanning(Material.DIRT);

        // In the deny list, absent from the allow list, and neither: only the last passes.
        assertFalse(scan.materialMatches(at(), List.of(STONE_ID, DIRT_ID), List.of(DIRT_ID)));
        assertFalse(scan.materialMatches(at(), List.of(STONE_ID), List.of(BEDROCK_ID)));
        assertTrue(scan.materialMatches(at(), List.of(STONE_ID, DIRT_ID), List.of(BEDROCK_ID)));
    }

    @Test
    void twoEmptyListsAdmitEverythingWithoutReadingTheWorld() {
        // An unfiltered @Bore is up to 343 candidate blocks; a world read per candidate for a filter nobody
        // authored is pure cost. Absence of the read is the contract, not just the answer.
        AreaScan scan = scanning(Material.BEDROCK);

        assertTrue(scan.materialMatches(at(), List.of(), List.of()));
        verify(world, never()).getBlockAt(org.mockito.ArgumentMatchers.any(Location.class));
    }
}
