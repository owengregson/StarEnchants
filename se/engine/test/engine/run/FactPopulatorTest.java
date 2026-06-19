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
import engine.stores.VarStore;
import java.util.UUID;
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
        lenient().when(p.getFireTicks()).thenReturn(20);
        lenient().when(p.isOnGround()).thenReturn(true);
        lenient().when(p.getType()).thenReturn(EntityType.PLAYER);
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
    void unknownTokenResolvesFromTheVarStoreThenFallsThroughToPapi() {
        UUID id = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        VarStore vars = new VarStore();
        vars.set(id, "rage", "1", 100L, 0); // a dynamic var SET_VAR wrote for this player
        FactPopulator pop = new FactPopulator(BuiltinVars.vocabulary(), vars, token -> "papi:" + token);

        FactBuffer f = pop.populate(new ActivationContext(player, null, null, null), 100L);

        assertEquals("1", f.resolvePapi("rage"));        // dynamic var wins over PAPI
        assertEquals("papi:miss", f.resolvePapi("miss")); // store miss → falls through to the PAPI delegate
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
        assertTrue(f.flag(flag("onfire")));   // fireTicks 20 > 0
        assertTrue(f.flag(flag("onground")));
        assertEquals(75.0, f.number(num("actor", "healthpercent"))); // 15 / 20 * 100
        assertEquals("world_nether", f.string(str("actor", "world")));
        assertEquals("SURVIVAL", f.string(str("actor", "gamemode")));
        assertEquals("DIAMOND_SWORD", f.string(str("actor", "helditem")));
        assertEquals("PLAYER", f.string(str("actor", "type")));
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
        org.bukkit.inventory.EntityEquipment eq = mock(org.bukkit.inventory.EntityEquipment.class);
        ItemStack vHeld = mock(ItemStack.class);
        lenient().when(vHeld.getType()).thenReturn(Material.SHIELD);
        lenient().when(eq.getItemInMainHand()).thenReturn(vHeld);
        lenient().when(victim.getEquipment()).thenReturn(eq);

        FactBuffer f = populator.populate(new ActivationContext(mock(Player.class), victim, null, null));

        assertEquals(7.0, f.number(num("victim", "health")));
        assertEquals(20.0, f.number(num("victim", "maxhealth")));
        assertEquals(35.0, f.number(num("victim", "healthpercent"))); // 7 / 20 * 100
        assertEquals(8.0, f.number(num("victim", "food")));           // actor()'s food level
        assertEquals("PLAYER", f.string(str("victim", "type")));
        assertEquals("SHIELD", f.string(str("victim", "helditem")));
        assertTrue(f.flag(flag("victim.sneaking")));
        assertTrue(f.flag(flag("victim.blocking")));
        assertFalse(f.flag(flag("victim.flying")));
        assertTrue(f.flag(flag("victim.sprinting"))); // actor() is sprinting
        assertFalse(f.flag(flag("victim.swimming")));
        assertFalse(f.flag(flag("victim.gliding")));
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
    void comboStaysUnsourcedAtZero() {
        // combo is declared so conditions referencing it compile, but no combat-streak tracker exists.
        FactBuffer f = populator.populate(new ActivationContext(actor(), null, null, null));
        assertEquals(0.0, f.number(num(null, "combo")));
    }

    @Test
    void populatesContextFactsFromTheEventPayload() {
        Player actor = actor();
        World world = actor.getWorld();
        lenient().when(world.hasStorm()).thenReturn(true);
        lenient().when(world.isThundering()).thenReturn(false);
        lenient().when(world.getTime()).thenReturn(6000L);
        org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
        when(block.getType()).thenReturn(Material.DIAMOND_ORE);

        FactBuffer f = populator.populate(new ActivationContext(actor, null, null, null, 7.5, block));

        assertEquals(7.5, f.number(num(null, "damage")));        // sourced from the event payload
        assertEquals("DIAMOND_ORE", f.string(str("block", "type")));
        assertTrue(f.flag(flag("isblock")));
        assertTrue(f.flag(flag("world.raining")));
        assertFalse(f.flag(flag("world.thundering")));
        assertEquals(6000.0, f.number(num("world", "time")));
    }

    @Test
    void noBlockContextLeavesBlockFactsDefaulted() {
        FactBuffer f = populator.populate(new ActivationContext(actor(), null, null, null));
        assertEquals(null, f.string(str("block", "type")));
        assertFalse(f.flag(flag("isblock")));
    }

    @Test
    void factBufferSupportsFlagsAcrossBothWords() {
        FactBuffer f = new FactBuffer(0, FactBuffer.MAX_FLAGS, 0);
        f.setFlag(0, true);
        f.setFlag(63, true);
        f.setFlag(64, true);   // first bit of the second word
        f.setFlag(127, true);
        assertTrue(f.flag(0));
        assertTrue(f.flag(63));
        assertTrue(f.flag(64));
        assertTrue(f.flag(127));
        assertFalse(f.flag(1));
        assertFalse(f.flag(65)); // words are independent
        f.clear();
        assertFalse(f.flag(0));
        assertFalse(f.flag(64));
    }

    @Test
    void vocabularyAcceptsMoreThan64Flags() {
        VarVocabulary.Builder b = VarVocabulary.builder();
        for (int i = 0; i < 80; i++) {
            b.flag("f" + i); // 80 > the old 64-flag ceiling, under the new 128
        }
        assertEquals(80, b.build().flagSlots());
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
