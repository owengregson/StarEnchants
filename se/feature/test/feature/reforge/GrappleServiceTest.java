package feature.reforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.model.Affinity;
import compile.model.CompiledEffect;
import feature.trigger.TriggerDispatch;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import platform.lang.Messages;
import schema.spec.Args;

/**
 * GrappleService's mode pick (ADR-0071): the service resolves BOTH rays via the era raycast refs and emits
 * ONE {@code grapple} intent for the closer of entity-vs-block — reel an in-sight enemy, or zip to terrain —
 * with both distances measured from the EYE, where the rays cast. An open-air whiff draws the throw line and
 * speaks (cooldown stays spent). Origin at a block centre (0.5, 64.5, 0.5) facing +X (yaw -90) so the
 * geometry is hand-checkable. A wall between caster and enemy shows up as a nearer block, so the closer-of
 * rule IS the 1.8 wall-blindness fix.
 */
class GrappleServiceTest {

    private static final UUID ACTOR_ID = UUID.randomUUID();
    private final World world = mock(World.class);
    private final Location origin = new Location(world, 0.5, 64.5, 0.5, -90f, 0f);
    private final Location eye = new Location(world, 0.5, 65.0, 0.5, -90f, 0f);
    private final TriggerDispatch dispatch = mock(TriggerDispatch.class);
    private final Messages messages = mock(Messages.class);

    private Player actor() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(ACTOR_ID);
        when(p.getLocation()).thenReturn(origin);
        when(p.getEyeLocation()).thenReturn(eye);
        return p;
    }

    private LivingEntity victimAt(double x) {
        LivingEntity v = mock(LivingEntity.class);
        when(v.getUniqueId()).thenReturn(UUID.randomUUID());
        when(v.getLocation()).thenReturn(new Location(world, x, 64.5, 0.5));
        return v;
    }

    private Block blockAt(double x) {
        Block block = mock(Block.class);
        when(block.getLocation()).thenReturn(new Location(world, x, 64, 0));
        return block;
    }

    private static CompiledEffect grappleFx() {
        Args args = Args.empty()
                .with("range", 14.0).with("hook-speed", 2.0).with("reel-distance", 2.0)
                .with("slow-effect", 5).with("slow-level", 2).with("slow-duration", 60)
                .with("zip-strength", 0.34).with("zip-cap", 3.2).with("zip-rise", 0.25)
                .with("particle", 7).with("r", 200).with("g", 220).with("b", 255)
                .with("size", 1.0).with("density", 3.0);
        return new CompiledEffect("GRAPPLE", args, null, 0, Affinity.CONTEXT_LOCAL);
    }

    private GrappleService service(BiFunction<Player, Integer, Entity> entity,
                                   BiFunction<Player, Integer, Block> block) {
        return new GrappleService(dispatch, entity, block, messages);
    }

    @Test
    void entityCloserThanBlockReels() {
        LivingEntity victim = victimAt(4.5); // dVictim = 16
        Player actor = actor();
        service((p, d) -> victim, (p, d) -> blockAt(9)).start(actor, grappleFx()); // dBlock = 81

        ArgumentCaptor<Location> reel = ArgumentCaptor.forClass(Location.class);
        verify(dispatch).grapple(eq(actor), eq(eye), eq(victim), reel.capture(), isNull(),
                eq(2), eq(5), eq(1), eq(60), isNull(), eq(7), eq(200), eq(220), eq(255), eq(1.0f), eq(3.0));
        Location reelTo = reel.getValue(); // origin + facing(+X) × reel-distance(2)
        assertEquals(2.5, reelTo.getX(), 1.0e-6);
        assertEquals(64.5, reelTo.getY(), 1.0e-6);
        assertEquals(0.5, reelTo.getZ(), 1.0e-6);
    }

    @Test
    void blockCloserThanEntityZips() {
        LivingEntity victim = victimAt(9.5); // dVictim = 81
        Player actor = actor();
        service((p, d) -> victim, (p, d) -> blockAt(4)).start(actor, grappleFx()); // blockCenter x=4.5, dBlock = 16

        ArgumentCaptor<Vector> zip = ArgumentCaptor.forClass(Vector.class);
        verify(dispatch).grapple(eq(actor), eq(eye), isNull(), isNull(), any(Location.class),
                eq(2), eq(0), eq(0), eq(0), zip.capture(), eq(7), eq(200), eq(220), eq(255), eq(1.0f), eq(3.0));
        Vector v = zip.getValue(); // normalize((4,0,0)) × min(3.2, 0.34×4=1.36) + (0,0.25,0)
        assertEquals(1.36, v.getX(), 1.0e-6);
        assertEquals(0.25, v.getY(), 1.0e-6);
        assertEquals(0.0, v.getZ(), 1.0e-6);
    }

    @Test
    void wallBetweenPicksTerrain() {
        // A wall between caster and enemy: the raycast block (the wall) is nearer than the shielded enemy, so
        // the closer-of rule zips to the wall — the same code path that compensates the 1.8 dot-scan's wall-blindness.
        LivingEntity shielded = victimAt(9.5); // enemy behind the wall
        Player actor = actor();
        service((p, d) -> shielded, (p, d) -> blockAt(4)).start(actor, grappleFx()); // wall at 4

        verify(dispatch).grapple(eq(actor), eq(eye), isNull(), isNull(), any(Location.class),
                anyInt(), eq(0), eq(0), eq(0), any(Vector.class), anyInt(), anyInt(), anyInt(), anyInt(),
                eq(1.0f), eq(3.0));
    }

    @Test
    void modePickMeasuresFromTheEyeNotTheFeet() {
        // Victim head-high (feet at eye Y): eye-measured 16 vs the block's 16.25 → entity mode. Feet-measured
        // the ranking flips (16.25 vs 16) and the caster would zip INTO the enemy.
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
        when(victim.getLocation()).thenReturn(new Location(world, 4.5, 65.0, 0.5));
        Player actor = actor();
        service((p, d) -> victim, (p, d) -> blockAt(4)).start(actor, grappleFx());

        verify(dispatch).grapple(eq(actor), eq(eye), eq(victim), any(Location.class), isNull(),
                anyInt(), eq(5), eq(1), eq(60), isNull(), eq(7), eq(200), eq(220), eq(255), eq(1.0f), eq(3.0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void openAirWhiffDrawsTheThrowAndSpeaks() {
        Player actor = actor();
        service((p, d) -> null, (p, d) -> null).start(actor, grappleFx());

        verify(dispatch, never()).grapple(any(), any(), any(), any(), any(), anyInt(), anyInt(), anyInt(),
                anyInt(), any(), anyInt(), anyInt(), anyInt(), anyInt(), org.mockito.ArgumentMatchers.anyFloat(),
                org.mockito.ArgumentMatchers.anyDouble());
        ArgumentCaptor<List<Location>> line = ArgumentCaptor.forClass(List.class);
        verify(dispatch).dust(line.capture(), eq(7), eq(200), eq(220), eq(255), eq(1.0f));
        List<Location> points = line.getValue(); // eye→(eye + facing(+X) × 14) at 3 points/block → 43 samples
        assertEquals(43, points.size());
        assertEquals(0.5, points.get(0).getX(), 1.0e-6);
        assertEquals(65.0, points.get(0).getY(), 1.0e-6);
        assertEquals(14.5, points.get(points.size() - 1).getX(), 1.0e-6);
        assertEquals(65.0, points.get(points.size() - 1).getY(), 1.0e-6);
        assertEquals(0.5, points.get(points.size() - 1).getZ(), 1.0e-6);
        verify(messages).fragment("reforge.grapple.whiff", "RANGE", 14);
    }

    @Test
    void selfHitRejected() {
        Player actor = actor(); // the entity ray returns the caster itself
        service((p, d) -> actor, (p, d) -> null).start(actor, grappleFx());
        verify(dispatch, never()).grapple(any(), any(), any(), any(), any(), anyInt(), anyInt(), anyInt(),
                anyInt(), any(), anyInt(), anyInt(), anyInt(), anyInt(), org.mockito.ArgumentMatchers.anyFloat(),
                org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void crossRegionVictimReadFallsBackToTerrain() {
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
        when(victim.getLocation()).thenThrow(new IllegalStateException("cross-region read")); // Folia fault
        Player actor = actor();
        service((p, d) -> victim, (p, d) -> blockAt(4)).start(actor, grappleFx());

        // Guarded read fails closed → no entity hit → terrain mode (victim arg null).
        verify(dispatch).grapple(eq(actor), eq(eye), isNull(), isNull(), any(Location.class),
                anyInt(), eq(0), eq(0), eq(0), any(Vector.class), anyInt(), anyInt(), anyInt(), anyInt(),
                eq(1.0f), eq(3.0));
    }
}
