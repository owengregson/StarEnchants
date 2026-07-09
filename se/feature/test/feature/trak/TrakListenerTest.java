package feature.trak;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feature.compat.Hands;
import feature.compat.Sounds;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import platform.lang.Messages;

/**
 * Unit-pins {@link TrakListener}'s F19 kill-credit classification: the weapon that dealt the FATAL blow is
 * resolved from {@code getLastDamageCause()} — melee credits the killer's main hand, a projectile credits its
 * stamped launcher, and a lingering-getKiller environment death / potion / another shooter credits nothing.
 */
class TrakListenerTest {

    private final TrakService service = mock(TrakService.class);
    private final Hands hands = mock(Hands.class);
    private final ShotWeapons shots = mock(ShotWeapons.class);
    private final TrakListener listener =
            new TrakListener(service, mock(Messages.class), mock(Sounds.class), hands, shots);

    private static ItemStack weapon(Material type) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        when(stack.clone()).thenReturn(stack);
        return stack;
    }

    private EntityDeathEvent mobDeath(LivingEntity dead, Player killer, EntityDamageEvent cause) {
        when(dead.getKiller()).thenReturn(killer);
        when(dead.getLastDamageCause()).thenReturn(cause);
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(dead);
        return event;
    }

    @Test
    void meleeKillCreditsTheKillersMainHand() {
        LivingEntity mob = mock(LivingEntity.class);
        Player killer = mock(Player.class);
        ItemStack sword = weapon(Material.DIAMOND_SWORD);
        when(hands.mainHand(killer)).thenReturn(sword);
        EntityDamageByEntityEvent fatal = mock(EntityDamageByEntityEvent.class);
        when(fatal.getDamager()).thenReturn(killer);

        listener.onMobDeath(mobDeath(mob, killer, fatal));

        verify(service).trackKill(eq(killer), eq(false), eq(sword));
    }

    @Test
    void environmentDeathAfterAHitCreditsNothing() {
        LivingEntity mob = mock(LivingEntity.class);
        Player killer = mock(Player.class);
        EntityDamageEvent fall = mock(EntityDamageEvent.class); // not an EntityDamageByEntityEvent

        listener.onMobDeath(mobDeath(mob, killer, fall));

        verify(service, never()).trackKill(any(), anyBoolean(), any());
    }

    @Test
    void potionKillCreditsNothing() {
        LivingEntity mob = mock(LivingEntity.class);
        Player killer = mock(Player.class);
        Projectile potion = mock(Projectile.class);
        UUID id = UUID.randomUUID();
        when(potion.getUniqueId()).thenReturn(id);
        when(potion.getShooter()).thenReturn(killer);
        when(shots.claim(id)).thenReturn(null); // no launcher was stamped for a thrown potion
        EntityDamageByEntityEvent fatal = mock(EntityDamageByEntityEvent.class);
        when(fatal.getDamager()).thenReturn(potion);

        listener.onMobDeath(mobDeath(mob, killer, fatal));

        verify(service, never()).trackKill(any(), anyBoolean(), any());
    }

    @Test
    void arrowKillCreditsTheStampedBow() {
        LivingEntity mob = mock(LivingEntity.class);
        Player killer = mock(Player.class);
        Projectile arrow = mock(Projectile.class);
        UUID id = UUID.randomUUID();
        when(arrow.getUniqueId()).thenReturn(id);
        when(arrow.getShooter()).thenReturn(killer);
        ItemStack bow = weapon(Material.BOW);
        when(shots.claim(id)).thenReturn(bow);
        EntityDamageByEntityEvent fatal = mock(EntityDamageByEntityEvent.class);
        when(fatal.getDamager()).thenReturn(arrow);

        listener.onMobDeath(mobDeath(mob, killer, fatal));

        verify(service).trackKill(eq(killer), eq(false), eq(bow));
    }

    @Test
    void arrowFromAnotherShooterCreditsNothing() {
        LivingEntity mob = mock(LivingEntity.class);
        Player killer = mock(Player.class);
        Player someoneElse = mock(Player.class);
        Projectile arrow = mock(Projectile.class);
        when(arrow.getShooter()).thenReturn(someoneElse); // the killer's getKiller lingered, but they did not fire
        EntityDamageByEntityEvent fatal = mock(EntityDamageByEntityEvent.class);
        when(fatal.getDamager()).thenReturn(arrow);

        listener.onMobDeath(mobDeath(mob, killer, fatal));

        verify(service, never()).trackKill(any(), anyBoolean(), any());
    }

    @Test
    void fistKillCreditsNothing() {
        LivingEntity mob = mock(LivingEntity.class);
        Player killer = mock(Player.class);
        ItemStack fist = weapon(Material.AIR);
        when(hands.mainHand(killer)).thenReturn(fist);
        EntityDamageByEntityEvent fatal = mock(EntityDamageByEntityEvent.class);
        when(fatal.getDamager()).thenReturn(killer);

        listener.onMobDeath(mobDeath(mob, killer, fatal));

        verify(service, never()).trackKill(any(), anyBoolean(), any());
    }

    @Test
    void playerKillIsCreditedAsASoulKill() {
        Player victim = mock(Player.class);
        Player killer = mock(Player.class);
        ItemStack sword = weapon(Material.NETHERITE_SWORD);
        when(hands.mainHand(killer)).thenReturn(sword);
        when(victim.getKiller()).thenReturn(killer);
        EntityDamageByEntityEvent fatal = mock(EntityDamageByEntityEvent.class);
        when(fatal.getDamager()).thenReturn(killer);
        when(victim.getLastDamageCause()).thenReturn(fatal);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(victim);

        listener.onPlayerDeath(event);

        verify(service).trackKill(eq(killer), eq(true), eq(sword));
    }

    @Test
    void noKillerCreditsNothing() {
        LivingEntity mob = mock(LivingEntity.class);
        when(mob.getKiller()).thenReturn(null);
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(mob);

        listener.onMobDeath(event);

        verify(service, never()).trackKill(any(), anyBoolean(), any());
    }
}
