package feature.combat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import compile.load.ContentHolder;
import engine.run.AbilityExecutor;
import engine.run.ActorProbe;
import engine.sink.EngineDamage;
import engine.sink.SinkFactory;
import engine.stores.ImmuneStore;
import feature.compat.Hands;
import feature.compat.ModernProjectiles;
import item.worn.WornStateStore;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import testfx.Envs;

/**
 * The ADR-0054 re-entrancy contract: engine-issued damage is now ATTRIBUTED (a real
 * {@code EntityDamageByEntityEvent} for downstream plugins), so SE's own combat listeners hear events the
 * old bare {@code hurt()} structurally hid from them ("no damager → no dispatch"). These pin that the
 * pipeline still stands down inside the {@link EngineDamage} frame — a reflect can never proc a reflect,
 * a DoT tick never advances rage — and that the IMMUNE applier keeps its exact pre-attribution semantics
 * for engine damage (blanket ALL cancels; the held-item SWORD/AXE typing never applies to proc
 * bookkeeping). The sink-side half — every engine hurt runs inside the frame — is pinned in
 * {@code DispatchSinkDamageFoldTest}.
 */
class EngineDamageReentryTest {

    @Test
    void combatDispatchStandsDownInsideTheEngineFrame() {
        SinkFactory sinkFactory = mock(SinkFactory.class);
        ContentHolder content = mock(ContentHolder.class);
        CombatDispatch dispatch = new CombatDispatch(mock(AbilityExecutor.class), sinkFactory,
                mock(ActorProbe.class), content, mock(WornStateStore.class), 0, 1, -1, -1,
                p -> Optional.empty(), Envs.sink().build(), CombatDispatch.Caps.unlimited(),
                new ModernProjectiles());
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);

        EngineDamage.frame(() -> dispatch.onDamage(event));

        // The guard precedes every read: no snapshot walk, no per-event sink, the event untouched —
        // without it an attributed reflect would re-walk this handler and could ping-pong forever.
        verifyNoInteractions(sinkFactory, content, event);
    }

    @Test
    void rageStacksNeverBuildOffEngineIssuedDamage() {
        RageStacksService service = mock(RageStacksService.class);
        RageStacksListener listener = new RageStacksListener(service);
        Player attacker = mock(Player.class);
        LivingEntity victim = mock(LivingEntity.class);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamager()).thenReturn(attacker);

        EngineDamage.frame(() -> listener.onDamage(event));
        verifyNoInteractions(service); // a DoT tick is not a melee swing

        listener.onDamage(event); // the same hit OUTSIDE the frame is a real swing
        verify(service).onHit(attacker, false);
    }

    @Test
    void immuneAllStillBlocksEngineIssuedDamage() {
        // Pre-attribution, engine damage arrived as a plain CUSTOM hurt cancellable only by a blanket
        // ALL immunity — the attributed shape must preserve exactly that.
        ImmuneStore store = new ImmuneStore();
        ImmuneListener listener = new ImmuneListener(store, () -> 0L, mock(Hands.class));
        Player victim = mock(Player.class);
        UUID victimId = UUID.randomUUID();
        when(victim.getUniqueId()).thenReturn(victimId);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(victim);
        store.immune(victimId, ImmuneStore.Type.ALL, 0L, 100);

        EngineDamage.frame(() -> listener.onDamageByEntity(event));
        verify(event).setCancelled(true);
    }

    @Test
    void heldItemTypingNeverAppliesToEngineIssuedDamage() {
        // The attributed damager's held item is proc bookkeeping, not a swung weapon: a SWORD immunity
        // must not start cancelling bleed ticks just because the bleeder holds a sword.
        ImmuneStore store = new ImmuneStore();
        Hands hands = mock(Hands.class);
        ImmuneListener listener = new ImmuneListener(store, () -> 0L, hands);
        Player victim = mock(Player.class);
        UUID victimId = UUID.randomUUID();
        when(victim.getUniqueId()).thenReturn(victimId);
        Player attacker = mock(Player.class);
        when(attacker.getType()).thenReturn(EntityType.PLAYER); // the fish-hook arm reads the damager type by name
        when(attacker.getEquipment()).thenReturn(mock(EntityEquipment.class));
        ItemStack sword = mock(ItemStack.class);
        when(sword.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(hands.mainHand((LivingEntity) attacker)).thenReturn(sword); // the listener's pattern binds LivingEntity
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamager()).thenReturn(attacker);
        store.immune(victimId, ImmuneStore.Type.SWORD, 0L, 100);

        EngineDamage.frame(() -> listener.onDamageByEntity(event));
        verify(event, never()).setCancelled(anyBoolean());

        listener.onDamageByEntity(event); // the same swing OUTSIDE the frame is a real sword hit
        verify(event).setCancelled(true);
    }
}
