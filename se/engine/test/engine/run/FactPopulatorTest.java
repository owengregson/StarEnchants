package engine.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.cond.VarBinding;
import compile.cond.VarKind;
import engine.condition.BuiltinVars;
import engine.condition.FactBuffer;
import engine.condition.VarVocabulary;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

/**
 * Unit-pins the runtime half of the condition variable system: mapping a live {@link ActivationContext}
 * to the dense {@link FactBuffer} slots the compiler lowered against. Slots are resolved from the SAME
 * {@link BuiltinVars} vocabulary the populator uses, so this also guards against name/kind drift between
 * the populator's extractor table and the declared vocabulary. The Folia cross-region behaviour (a
 * wrong-region read fails hard, leaving that side defaulted) is pinned against a synthetic
 * {@link RuntimeException}; the end-to-end gate is proven live in {@code ConditionSuite}.
 */
class FactPopulatorTest {

    private static final VarVocabulary VOCAB = BuiltinVars.vocabulary();
    private final FactPopulator populator = FactPopulator.builtin();

    private static int num(String scope, String name) {
        return slot(scope, name, VarKind.NUM);
    }

    private static int flag(String name) {
        return slot(null, name, VarKind.BOOL);
    }

    private static int str(String scope, String name) {
        return slot(scope, name, VarKind.STR);
    }

    private static int slot(String scope, String name, VarKind kind) {
        VarBinding b = VOCAB.lookup(scope, name).orElseThrow(() -> new AssertionError("no var " + scope + "." + name));
        assertEquals(kind, b.kind());
        return b.slot();
    }

    /** A fully-stubbed firing player covering every actor-sourced fact. */
    private static Player actor() {
        Player p = mock(Player.class);
        lenient().when(p.getHealth()).thenReturn(15.0);
        lenient().when(p.getMaxHealth()).thenReturn(20.0);
        lenient().when(p.getFoodLevel()).thenReturn(8);
        lenient().when(p.getLevel()).thenReturn(30);
        lenient().when(p.getTotalExperience()).thenReturn(1234);
        lenient().when(p.isSneaking()).thenReturn(true);
        lenient().when(p.isBlocking()).thenReturn(false);
        lenient().when(p.isFlying()).thenReturn(true);
        lenient().when(p.isSprinting()).thenReturn(true);
        lenient().when(p.isSwimming()).thenReturn(false);
        lenient().when(p.isGliding()).thenReturn(false);
        World world = mock(World.class);
        lenient().when(world.getName()).thenReturn("world_nether");
        lenient().when(p.getWorld()).thenReturn(world);
        lenient().when(p.getGameMode()).thenReturn(GameMode.SURVIVAL);
        PlayerInventory inv = mock(PlayerInventory.class);
        ItemStack held = mock(ItemStack.class);
        lenient().when(held.getType()).thenReturn(Material.DIAMOND_SWORD);
        lenient().when(inv.getItemInMainHand()).thenReturn(held);
        lenient().when(p.getInventory()).thenReturn(inv);
        return p;
    }

    @Test
    void populatesEveryActorFact() {
        FactBuffer f = populator.populate(new ActivationContext(actor(), null, null, null));

        assertEquals(15.0, f.number(num("actor", "health")));
        assertEquals(20.0, f.number(num("actor", "maxhealth")));
        assertEquals(8.0, f.number(num("actor", "food")));
        assertEquals(30.0, f.number(num("actor", "level")));
        assertEquals(1234.0, f.number(num("actor", "totalexp")));
        assertTrue(f.flag(flag("sneaking")));
        assertFalse(f.flag(flag("blocking")));
        assertTrue(f.flag(flag("flying")));
        assertTrue(f.flag(flag("sprinting")));
        assertFalse(f.flag(flag("swimming")));
        assertFalse(f.flag(flag("gliding")));
        assertEquals("world_nether", f.string(str("actor", "world")));
        assertEquals("SURVIVAL", f.string(str("actor", "gamemode")));
        assertEquals("DIAMOND_SWORD", f.string(str("actor", "helditem")));
    }

    @Test
    void populatesVictimFactsIncludingPlayerPose() {
        Player victim = actor(); // a player victim → its pose flags are meaningful
        when(victim.getHealth()).thenReturn(7.0);
        when(victim.getMaxHealth()).thenReturn(20.0);
        when(victim.getType()).thenReturn(EntityType.PLAYER);
        when(victim.isSneaking()).thenReturn(true);
        when(victim.isBlocking()).thenReturn(true);
        when(victim.isFlying()).thenReturn(false);

        FactBuffer f = populator.populate(new ActivationContext(mock(Player.class), victim, null, null));

        assertEquals(7.0, f.number(num("victim", "health")));
        assertEquals(20.0, f.number(num("victim", "maxhealth")));
        assertEquals("PLAYER", f.string(str("victim", "type")));
        assertTrue(f.flag(flag("victim.sneaking")));
        assertTrue(f.flag(flag("victim.blocking")));
        assertFalse(f.flag(flag("victim.flying")));
    }

    @Test
    void nonPlayerVictimHasNoPoseFlagsButHasTypeAndHealth() {
        LivingEntity cow = mock(LivingEntity.class);
        when(cow.getHealth()).thenReturn(10.0);
        when(cow.getType()).thenReturn(EntityType.COW);

        FactBuffer f = populator.populate(new ActivationContext(mock(Player.class), cow, null, null));

        assertEquals(10.0, f.number(num("victim", "health")));
        assertEquals("COW", f.string(str("victim", "type")));
        assertFalse(f.flag(flag("victim.sneaking"))); // not a player → false, no crash
        assertFalse(f.flag(flag("victim.blocking")));
        assertFalse(f.flag(flag("victim.flying")));
    }

    @Test
    void unsourcedFactsKeepTheirDefault() {
        FactBuffer f = populator.populate(new ActivationContext(actor(), null, null, null));
        assertEquals(0.0, f.number(num(null, "damage"))); // declared but not yet sourced
        assertEquals(0.0, f.number(num(null, "combo")));
    }

    @Test
    void nullContextAndMissingEntitiesLeaveDefaults() {
        FactBuffer fromNull = populator.populate(null);
        assertEquals(0.0, fromNull.number(num("actor", "health")));

        FactBuffer noEntities = populator.populate(new ActivationContext(null, null, null, null));
        assertEquals(0.0, noEntities.number(num("actor", "health")));
        assertEquals(0.0, noEntities.number(num("victim", "health")));
        assertFalse(noEntities.flag(flag("sneaking")));
        assertEquals(null, noEntities.string(str("victim", "type")));
    }

    @Test
    void crossRegionActorReadIsGuardedAndDefaultsThatSideOnly() {
        // Folia fails a cross-region access with IllegalStateException. The actor side defaults, but the
        // victim side (a region-owned entity) is still populated — the guard is per side, not all-or-nothing.
        Player actor = mock(Player.class);
        when(actor.getHealth()).thenThrow(new IllegalStateException("Accessing entity from wrong region"));
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getHealth()).thenReturn(9.0);
        when(victim.getType()).thenReturn(EntityType.COW);

        FactBuffer f = populator.populate(new ActivationContext(actor, victim, null, null));

        assertEquals(0.0, f.number(num("actor", "health"))); // actor side defaulted
        assertEquals(9.0, f.number(num("victim", "health"))); // victim side still read
    }

    @Test
    void guardCoversTheWholeActorBlockNotJustItsFirstRead() {
        // A throw on a later actor read must not lose the facts read before it, nor propagate.
        Player actor = mock(Player.class);
        when(actor.getHealth()).thenReturn(12.0);
        when(actor.getMaxHealth()).thenReturn(20.0);
        lenient().when(actor.getFoodLevel()).thenReturn(5);
        lenient().when(actor.getLevel()).thenReturn(1);
        lenient().when(actor.getTotalExperience()).thenReturn(0);
        when(actor.isSneaking()).thenReturn(true);
        when(actor.isBlocking()).thenThrow(new IllegalStateException("wrong region"));

        FactBuffer f = populator.populate(new ActivationContext(actor, null, null, null));

        assertEquals(12.0, f.number(num("actor", "health"))); // numeric facts (read first) survive
        assertTrue(f.flag(flag("sneaking")));                 // set before the throw
        assertFalse(f.flag(flag("flying")));                  // the throw skips the rest of the flags
    }
}
