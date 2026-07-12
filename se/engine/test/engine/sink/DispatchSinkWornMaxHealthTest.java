package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import testfx.Envs;
import testfx.SyncSchedulerBackend;

/**
 * Pins the worn max-health modifier leaf (1.8.1 regression hunt): {@code applyWornMaxHealth} must SET the one
 * identity-keyed modifier through the plan/flush路 exactly like every other entity intent — replace-by-identity,
 * remove at zero, clamp current health down when the cap shrank. Mock-level (the real attribute write is the
 * live suite's); this pins the modern leaf's calls.
 */
class DispatchSinkWornMaxHealthTest {

    private RuntimeHandles handles;

    @BeforeEach
    void setUp() {
        handles = new RuntimeHandles(new RegistryResolvers());
        Scheduling.install(new SyncSchedulerBackend());
    }

    @Test
    void setsTheIdentityKeyedModifierAtFlush() {
        Player player = mock(Player.class);
        AttributeInstance instance = mock(AttributeInstance.class);
        when(player.getAttribute(any(Attribute.class))).thenReturn(instance);
        when(instance.getModifiers()).thenReturn(List.of());
        when(instance.getValue()).thenReturn(24.0);
        when(player.getHealth()).thenReturn(20.0);

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.applyWornMaxHealth(player, 4.0);
        verifyNoInteractions(player); // captured, never applied inline on the firing thread

        sink.flush();
        ArgumentCaptor<AttributeModifier> added = ArgumentCaptor.forClass(AttributeModifier.class);
        verify(instance).addModifier(added.capture());
        assertEquals(4.0, added.getValue().getAmount());
        assertEquals(AttributeModifier.Operation.ADD_NUMBER, added.getValue().getOperation());
    }

    @Test
    void replacesItsOwnStaleModifierByIdentityAndNeverTouchesOthers() {
        Player player = mock(Player.class);
        AttributeInstance instance = mock(AttributeInstance.class);
        when(player.getAttribute(any(Attribute.class))).thenReturn(instance);
        AttributeModifier stale = mock(AttributeModifier.class);
        when(stale.getUniqueId()).thenReturn(DispatchSinkBase.WORN_MAX_HEALTH_ID);
        AttributeModifier foreign = mock(AttributeModifier.class);
        when(foreign.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(instance.getModifiers()).thenReturn(List.of(stale, foreign));
        when(instance.getValue()).thenReturn(28.0);
        when(player.getHealth()).thenReturn(20.0);

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.applyWornMaxHealth(player, 8.0);
        sink.flush();

        verify(instance).removeModifier(stale);
        verify(instance, never()).removeModifier(foreign);
        ArgumentCaptor<AttributeModifier> added = ArgumentCaptor.forClass(AttributeModifier.class);
        verify(instance).addModifier(added.capture());
        assertEquals(8.0, added.getValue().getAmount());
    }

    @Test
    void zeroTotalRemovesAndClampsCurrentHealthDown() {
        Player player = mock(Player.class);
        AttributeInstance instance = mock(AttributeInstance.class);
        when(player.getAttribute(any(Attribute.class))).thenReturn(instance);
        AttributeModifier stale = mock(AttributeModifier.class);
        when(stale.getUniqueId()).thenReturn(DispatchSinkBase.WORN_MAX_HEALTH_ID);
        when(instance.getModifiers()).thenReturn(List.of(stale));
        when(instance.getValue()).thenReturn(20.0);
        when(player.getHealth()).thenReturn(24.0); // above the shrunk cap

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.applyWornMaxHealth(player, 0.0);
        sink.flush();

        verify(instance).removeModifier(stale);
        verify(instance, never()).addModifier(any());
        verify(player).setHealth(20.0);
    }

    /** The identity is text-derived and era-shared — pin it so a refactor cannot silently re-key old playerdata. */
    @Test
    void modifierIdentityIsStable() {
        assertTrue(DispatchSinkBase.WORN_MAX_HEALTH_NAME.contains("worn_max_health"));
        assertEquals(DispatchSinkBase.WORN_MAX_HEALTH_ID,
                java.util.UUID.nameUUIDFromBytes(
                        "starenchants:worn_max_health".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
