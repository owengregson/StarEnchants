package feature.mask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.doubleThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.effect.kind.ActiveMasks;
import engine.sink.SinkEnv;
import engine.sink.SinkReadback;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import platform.resolve.RegistryResolvers;

class CosmicMaskMechanicsListenerTest {

    private final Map<UUID, Set<String>> worn = new HashMap<>();

    CosmicMaskMechanicsListenerTest() {
        ActiveMasks.resolver((player, key) -> worn.getOrDefault(player.getUniqueId(), Set.of()).contains(key));
    }

    @AfterEach
    void clearResolver() {
        ActiveMasks.resolver(null);
    }

    @Test
    void appliesExactDirectPlayerOutgoingMultipliersButNeverProjectileShooterMultipliers() {
        Player purge = player("masks/purge-mask");
        LivingEntity mob = mock(LivingEntity.class);
        EntityDamageByEntityEvent direct = event(purge, mob, 40.0);
        listener(1.0, mock(SinkReadback.class)).onPlayerDamageOther(direct);
        verify(direct).setDamage(41.0);

        Player shooter = player("masks/dragon-mask");
        Projectile projectile = mock(Projectile.class);
        when(projectile.getShooter()).thenReturn(shooter);
        EntityDamageByEntityEvent ranged = event(projectile, mob, 40.0);
        listener(1.0, mock(SinkReadback.class)).onPlayerDamageOther(ranged);
        verify(ranged, never()).setDamage(anyDouble());
    }

    @Test
    void partyHatComposesExactOutgoingAndIncomingMultipliersOnTheSameEntityHit() {
        Player attacker = player("masks/party-hat");
        Player victim = player("masks/party-hat");
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(attacker);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamage()).thenReturn(100.0, 104.0);

        listener(1.0, mock(SinkReadback.class)).onPlayerDamageOther(event);

        verify(event).setDamage(104.0);
        verify(event).setDamage(98.8);
    }

    @Test
    void multiMaskComposesEveryDistinctOutgoingAndIncomingLikeness() {
        Player attacker = player("masks/purge-mask", "masks/death-knight", "masks/dragon-mask", "masks/party-hat");
        Player victim = player("masks/monopoly-mask", "masks/party-hat");
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(attacker);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamage()).thenReturn(100.0, 102.5, 105.0625, 110.315625, 114.72825, 108.9918375);

        listener(1.0, mock(SinkReadback.class)).onPlayerDamageOther(event);

        verify(event).setDamage(doubleThat(value -> Math.abs(value - 102.5) < 1.0e-9));
        verify(event).setDamage(doubleThat(value -> Math.abs(value - 105.0625) < 1.0e-9));
        verify(event).setDamage(doubleThat(value -> Math.abs(value - 110.315625) < 1.0e-9));
        verify(event).setDamage(doubleThat(value -> Math.abs(value - 114.72825) < 1.0e-9));
        verify(event).setDamage(doubleThat(value -> Math.abs(value - 108.9918375) < 1.0e-9));
        verify(event).setDamage(doubleThat(value -> Math.abs(value - 103.542245625) < 1.0e-9));
    }

    @Test
    void monopolyReductionIsEntityOnlyAndExactlyFivePercent() {
        Entity damager = mock(Entity.class);
        Player victim = player("masks/monopoly-mask");
        EntityDamageByEntityEvent event = event(damager, victim, 80.0);

        listener(1.0, mock(SinkReadback.class)).onPlayerDamageOther(event);

        verify(event).setDamage(76.0);
    }

    @Test
    void turkeyInclusiveTwoPercentRollCancelsAndEmitsExactFeedback() {
        Entity damager = mock(Entity.class);
        Player victim = player("masks/turkey-mask");
        Location at = mock(Location.class);
        when(victim.getLocation()).thenReturn(at);
        when(at.add(0.0, 0.5, 0.0)).thenReturn(at);
        EntityDamageByEntityEvent event = event(damager, victim, 20.0);
        SinkReadback sink = mock(SinkReadback.class);

        listener(0.02, sink).onPlayerDamageOther(event);

        verify(event).setCancelled(true);
        verify(sink).message(victim, "&e&l* DODGED [&7Turkey Mask&e&l]");
        verify(sink).particle(at, 23, 10, -1, 0.2, 0.2, 0.2, 0.1);
        verify(sink).flush();
        verify(event, never()).setDamage(anyDouble());
    }

    @Test
    void bossMaskResolvesProjectileShooterAndUsesExactOutgoingMultiplier() {
        Player attacker = player("masks/boss-mask");
        Projectile projectile = mock(Projectile.class);
        when(projectile.getShooter()).thenReturn(attacker);
        LivingEntity boss = mock(LivingEntity.class);
        when(boss.hasMetadata("boss")).thenReturn(true);
        EntityDamageByEntityEvent event = event(projectile, boss, 50.0);

        listener(1.0, mock(SinkReadback.class)).onBossDamage(event);

        verify(event).setDamage(55.00000000000001);
    }

    @Test
    void disabledFeatureDoesNothing() {
        Player attacker = player("masks/purge-mask");
        EntityDamageByEntityEvent event = event(attacker, mock(LivingEntity.class), 40.0);
        SinkReadback sink = mock(SinkReadback.class);

        listener(false, 0.0, sink).onPlayerDamageOther(event);

        verify(event, never()).setDamage(anyDouble());
        verify(event, never()).setCancelled(true);
        verify(sink, never()).flush();
    }

    @Test
    void jokerCombatTagDurationsMatchAdvertisedAsymmetricValuesAndCompose() {
        assertEquals(12_000L, CosmicMaskMechanicsListener.jokerTagDuration(true, false));
        assertEquals(19_000L, CosmicMaskMechanicsListener.jokerTagDuration(false, true));
        assertEquals(16_000L, CosmicMaskMechanicsListener.jokerTagDuration(true, true));
        assertEquals(15_000L, CosmicMaskMechanicsListener.jokerTagDuration(false, false));
    }

    private CosmicMaskMechanicsListener listener(double roll, SinkReadback sink) {
        return listener(true, roll, sink);
    }

    private CosmicMaskMechanicsListener listener(boolean enabled, double roll, SinkReadback sink) {
        RegistryResolvers resolvers = mock(RegistryResolvers.class);
        when(resolvers.particle("CLOUD")).thenReturn(OptionalInt.of(23));
        SinkEnv env = mock(SinkEnv.class);
        return new CosmicMaskMechanicsListener(() -> enabled, ignored -> sink, env, resolvers, () -> roll);
    }

    private Player player(String... masks) {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        worn.put(id, Set.of(masks));
        return player;
    }

    private static EntityDamageByEntityEvent event(Entity damager, Entity victim, double damage) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(damager);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamage()).thenReturn(damage);
        return event;
    }
}