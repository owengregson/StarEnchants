package bootstrap.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bootstrap.compat.EraServices;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

/**
 * The area seam's radius contract. {@code getNearbyEntities} is a cube matched by bounding-box overlap, so
 * without a cut an {@code @Aoe{r}} reaches r·√3 at the corners and a body's height BELOW the centre — which
 * leaks damage between floors and makes every authored radius wider than it reads.
 */
class AreaScanNearbyLivingTest {

    private final World world = mock(World.class);

    private LivingEntity at(double x, double y, double z) {
        LivingEntity e = mock(LivingEntity.class);
        when(e.getLocation()).thenReturn(new Location(world, x, y, z));
        return e;
    }

    @Test
    void onlyEntitiesInsideTheRadiusSurviveTheCubeBroadPhase() {
        Location center = new Location(world, 0, 64, 0);
        LivingEntity inside = at(0, 64, 3.9);
        LivingEntity cubeCorner = at(3.5, 64, 3.5); // inside the r=4 cube, 4.95 blocks out
        LivingEntity floorBelow = at(0, 58.5, 0);   // a tall body whose box overlaps but whose feet are 5.5 down
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.<Entity>of(inside, cubeCorner, floorBelow));

        Iterable<LivingEntity> found = BootCore.areaScan(mock(EraServices.class)).nearbyLiving(center, 4.0);

        List<LivingEntity> out = new ArrayList<>();
        found.forEach(out::add);
        assertEquals(List.of(inside), out);
    }
}
