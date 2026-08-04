package engine.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.cond.VarBinding;
import compile.cond.VarKind;
import compile.model.FactMask;
import engine.selector.kind.Allies;
import engine.condition.BuiltinVars;
import engine.condition.FactBuffer;
import engine.condition.VarVocabulary;
import engine.sink.OwnerZones;
import engine.stores.EngineStores;
import engine.stores.VarStore;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

/**
 * Slots resolve from the SAME {@link BuiltinVars} vocabulary the populator uses, guarding against name/kind
 * drift between the extractor table and the declared vocabulary. Folia cross-region reads fail hard and
 * default that side — pinned here with a synthetic {@link RuntimeException}, proven end-to-end in {@code ConditionSuite}.
 */
class FactPopulatorTest {

    private static final VarVocabulary VOCAB = BuiltinVars.vocabulary();
    private final FactPopulator populator = FactPopulator.builtin(new ModernActorProbe());

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

    @SuppressWarnings("deprecation") // getMaxHealth()/isOnGround(): deprecated-not-removed cross-version accessors, stubbed on the mock.
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
        // actor.helditem now reads via the HeldItem seam (getEquipment().getItemInMainHand()).
        org.bukkit.inventory.EntityEquipment eq = mock(org.bukkit.inventory.EntityEquipment.class);
        lenient().when(eq.getItemInMainHand()).thenReturn(held);
        lenient().when(p.getEquipment()).thenReturn(eq);
        return p;
    }

    @Test
    void unknownTokenResolvesFromTheVarStoreThenFallsThroughToPapi() {
        UUID id = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        VarStore vars = new VarStore();
        vars.set(id, "rage", "1", 100L, 0); // a dynamic var SET_VAR wrote for this player
        FactPopulator pop = new FactPopulator(BuiltinVars.vocabulary(), vars, token -> "papi:" + token, new ModernActorProbe());

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
    @SuppressWarnings("deprecation") // getMaxHealth(): deprecated-not-removed cross-version accessor, stubbed on the mock.
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

    /** ADR-0039: an unreferenced derived fact never runs — the 8-block entity scan is skipped when masked out. */
    @Test
    void maskedOutNearbyEnemiesSkipsTheEntityScan() {
        Player actor = actor();
        populator.populate(new ActivationContext(actor, null, null, null), 0L, FactMask.NONE);
        verify(actor, never()).getNearbyEntities(anyDouble(), anyDouble(), anyDouble());
    }

    /** A masked-IN derived fact is computed and equals the eager (unmasked) path — gating never changes a referenced value. */
    @Test
    void maskedInNearbyEnemiesRunsTheScanWithTheEagerValue() {
        int slot = num(null, "nearbyenemies");
        Player masked = actor();
        lenient().when(masked.getNearbyEntities(8.0, 8.0, 8.0))
                .thenReturn(java.util.List.of(mock(LivingEntity.class), mock(LivingEntity.class)));
        double maskedValue = populator.populate(new ActivationContext(masked, null, null, null), 0L,
                new FactMask(1L << slot, 0L, 0L)).number(slot);

        Player eager = actor();
        lenient().when(eager.getNearbyEntities(8.0, 8.0, 8.0))
                .thenReturn(java.util.List.of(mock(LivingEntity.class), mock(LivingEntity.class)));
        double eagerValue = populator.populate(new ActivationContext(eager, null, null, null)).number(slot);

        verify(masked).getNearbyEntities(8.0, 8.0, 8.0);
        assertEquals(2.0, maskedValue);
        assertEquals(eagerValue, maskedValue);
    }

    /** Only the mask's slots are populated: a referenced fact keeps its eager value, an unreferenced sibling stays default. */
    @Test
    void onlyMaskedSlotsArePopulated() {
        int healthSlot = num("actor", "health");
        int maxSlot = num("actor", "maxhealth");
        FactBuffer f = populator.populate(new ActivationContext(actor(), null, null, null), 0L,
                new FactMask(1L << healthSlot, 0L, 0L));

        assertEquals(15.0, f.number(healthSlot)); // referenced → same value the eager path produces
        assertEquals(0.0, f.number(maxSlot));      // unreferenced → default, never read from the entity
    }

    // ── Relation facts: both read the ONE installed alliance predicate, so "ally" means the same thing here,
    // in @Aoe{filter=ALLIES}, and in the friendly-fire gate. The hook is static, so each test installs and resets.

    @Test
    void nearbyAlliesCountsOnlyAlliedPlayersAndSharesTheEnemyScan() {
        int alliesSlot = num(null, "nearbyallies");
        int enemiesSlot = num(null, "nearbyenemies");
        Player friend = mock(Player.class);
        Player foe = mock(Player.class);
        LivingEntity mob = mock(LivingEntity.class);
        Player a = actor();
        lenient().when(a.getNearbyEntities(8.0, 8.0, 8.0)).thenReturn(java.util.List.of(friend, foe, mob));
        Allies.resolver((self, other) -> other == friend);
        try {
            FactBuffer f = populator.populate(new ActivationContext(a, null, null, null), 0L,
                    new FactMask((1L << alliesSlot) | (1L << enemiesSlot), 0L, 0L));
            assertEquals(1.0, f.number(alliesSlot), "only the allied PLAYER counts");
            assertEquals(3.0, f.number(enemiesSlot), "the enemy count keeps its existing living-entity meaning");
            // One walk feeds both counts: asking for allies must not double the expensive entity scan.
            verify(a).getNearbyEntities(8.0, 8.0, 8.0);
        } finally {
            Allies.resolver(null);
        }
    }

    @Test
    void nearbyAlliesIsSkippedWhenUnmasked() {
        Player a = actor();
        populator.populate(new ActivationContext(a, null, null, null), 0L, FactMask.NONE);
        verify(a, org.mockito.Mockito.never()).getNearbyEntities(8.0, 8.0, 8.0);
    }

    @Test
    void victimRelationReadsTheInstalledPredicate() {
        int slot = str("victim", "relation");
        FactMask mask = new FactMask(0L, 0L, 1L << slot);
        Player ally = mock(Player.class);
        Player enemy = mock(Player.class);
        Allies.resolver((self, other) -> other == ally);
        try {
            assertEquals("ALLY", populator.populate(
                    new ActivationContext(actor(), ally, null, null), 0L, mask).string(slot));
            assertEquals("ENEMY", populator.populate(
                    new ActivationContext(actor(), enemy, null, null), 0L, mask).string(slot));
            // A mob has no alliance axis; NEUTRAL keeps ALLY/ENEMY meaning "another player".
            assertEquals("NEUTRAL", populator.populate(
                    new ActivationContext(actor(), mock(LivingEntity.class), null, null), 0L, mask).string(slot));
            assertEquals("", populator.populate(
                    new ActivationContext(actor(), null, null, null), 0L, mask).string(slot));
        } finally {
            Allies.resolver(null);
        }
    }

    @Test
    void victimRelationFallsBackToEnemyWithNoAlliancePluginInstalled() {
        // The shipped default: no team plugin means free-for-all PvP, the safe assumption for an AoE strike.
        int slot = str("victim", "relation");
        assertEquals("ENEMY", populator.populate(new ActivationContext(actor(), mock(Player.class), null, null),
                0L, new FactMask(0L, 0L, 1L << slot)).string(slot));
    }

    @Test
    void heldTicksCountsFromTheStampedSlotChange() {
        int slot = num(null, "heldticks");
        FactMask mask = new FactMask(1L << slot, 0L, 0L);
        UUID id = UUID.randomUUID();
        Player p = actor();
        when(p.getUniqueId()).thenReturn(id);
        EngineStores stores = EngineStores.fresh();
        FactPopulator pop = new FactPopulator(BuiltinVars.vocabulary(), stores.vars(), t -> null,
                new ModernActorProbe(), stores);

        // Never swapped this session: 0, the same default every absent fact reads.
        assertEquals(0.0, pop.populate(new ActivationContext(p, null, null, null), 500L, mask).number(slot));

        stores.heldSlots().changed(id, 100L);
        assertEquals(60.0, pop.populate(new ActivationContext(p, null, null, null), 160L, mask).number(slot));
        // A later swap restarts the count rather than extending it — the fact is "since the LAST change".
        stores.heldSlots().changed(id, 150L);
        assertEquals(10.0, pop.populate(new ActivationContext(p, null, null, null), 160L, mask).number(slot));
    }

    @Test
    void potionReadsAreBoundPerSideAndCostNothingUntilAsked() {
        // The keyed families own no fact slot, so they are BOUND, not populated: the probe must not be
        // consulted for an activation whose condition never mentions a potion.
        ActorProbe probe = mock(ActorProbe.class);
        Player a = actor();
        LivingEntity v = mock(LivingEntity.class);
        when(probe.potionLevel(a, 7)).thenReturn(3);
        when(probe.potionLevel(v, 7)).thenReturn(1);
        FactPopulator pop = new FactPopulator(BuiltinVars.vocabulary(), new VarStore(), t -> null, probe);

        FactBuffer f = pop.populate(new ActivationContext(a, v, null, null), 0L, FactMask.NONE);
        verify(probe, never()).potionLevel(any(), org.mockito.ArgumentMatchers.anyInt());

        assertEquals(3, f.actorPotionLevel(7));
        assertEquals(1, f.victimPotionLevel(7));
        assertEquals(0, f.actorPotionLevel(8), "an effect the actor lacks reads 0");

        // No victim on this activation: the victim side answers 0 rather than falling back to the actor's.
        FactBuffer solo = pop.populate(new ActivationContext(a, null, null, null), 0L, FactMask.NONE);
        assertEquals(0, solo.victimPotionLevel(7));
    }

    @Test
    void enchantLevelReadsAreBoundPerSideAndCostNothingUntilAsked() {
        // The keyed families own no fact slot, so they are BOUND, not populated: the installed source must not
        // be consulted for an activation whose condition never mentions an enchant level.
        UUID actorId = UUID.randomUUID();
        UUID victimId = UUID.randomUUID();
        Player a = actor();
        when(a.getUniqueId()).thenReturn(actorId);
        LivingEntity v = mock(LivingEntity.class);
        when(v.getUniqueId()).thenReturn(victimId);
        java.util.List<String> consulted = new java.util.ArrayList<>();
        FactPopulator.enchantLevelSource((entity, key) -> {
            consulted.add(key);
            if (entity.equals(actorId)) {
                return "solitude".equals(key) ? 3 : 0;
            }
            return entity.equals(victimId) && "metaphysical".equals(key) ? 1 : 0;
        });
        try {
            FactPopulator pop = FactPopulator.builtin(new ModernActorProbe());

            FactBuffer f = pop.populate(new ActivationContext(a, v, null, null), 0L, FactMask.NONE);
            assertTrue(consulted.isEmpty(), "an unread enchlevel family must never consult the source");

            assertEquals(3, f.actorEnchantLevel("solitude"));
            assertEquals(1, f.victimEnchantLevel("metaphysical"));
            assertEquals(0, f.actorEnchantLevel("metaphysical"), "an enchant the actor lacks reads 0");

            // No victim on this activation: the victim side answers 0 rather than falling back to the actor's.
            FactBuffer solo = pop.populate(new ActivationContext(a, null, null, null), 0L, FactMask.NONE);
            assertEquals(0, solo.victimEnchantLevel("solitude"));
        } finally {
            FactPopulator.enchantLevelSource(null); // restore the zero source so other tests aren't perturbed
        }
    }

    @Test
    void soulTotalsComeFromTheCachedStoreOnBothSides() {
        int actorSlot = num("actor", "souls");
        int victimSlot = num("victim", "souls");
        FactMask mask = new FactMask((1L << actorSlot) | (1L << victimSlot), 0L, 0L);
        UUID actorId = UUID.randomUUID();
        UUID victimId = UUID.randomUUID();
        Player a = actor();
        when(a.getUniqueId()).thenReturn(actorId);
        Player v = mock(Player.class);
        when(v.getUniqueId()).thenReturn(victimId);
        EngineStores stores = EngineStores.fresh();
        stores.soulTotals().set(actorId, 42);
        stores.soulTotals().set(victimId, 7);
        FactPopulator pop = new FactPopulator(BuiltinVars.vocabulary(), stores.vars(), t -> null,
                new ModernActorProbe(), stores);

        FactBuffer f = pop.populate(new ActivationContext(a, v, null, null), 0L, mask);
        assertEquals(42.0, f.number(actorSlot));
        assertEquals(7.0, f.number(victimSlot));

        // A mob carries no gems and has no entry — 0, and no inventory is ever walked to find that out.
        LivingEntity mob = mock(LivingEntity.class);
        when(mob.getUniqueId()).thenReturn(UUID.randomUUID());
        assertEquals(0.0, pop.populate(new ActivationContext(a, mob, null, null), 0L, mask).number(victimSlot));
    }

    @Test
    void victimFromSpawnerReadsTheEraProbe() {
        int slot = flag("victim.fromspawner");
        FactMask mask = new FactMask(0L, 1L << slot, 0L);
        LivingEntity farmed = mock(LivingEntity.class);
        when(farmed.fromMobSpawner()).thenReturn(true);
        LivingEntity wild = mock(LivingEntity.class);

        assertTrue(populator.populate(new ActivationContext(actor(), farmed, null, null), 0L, mask).flag(slot));
        assertFalse(populator.populate(new ActivationContext(actor(), wild, null, null), 0L, mask).flag(slot));
        // Unmasked: the probe is never consulted, so a spawner check costs a grinder-free server nothing.
        populator.populate(new ActivationContext(actor(), farmed, null, null), 0L, FactMask.NONE);
        verify(farmed, times(1)).fromMobSpawner();
    }

    // ── posthit.health: the actor's health once the pending hit lands, priced at the server's vanilla-final
    // damage and NOT the SE fold (whose contributions come from the death-save abilities this fact gates).

    @Test
    void postHitHealthSubtractsTheContextsVanillaFinalDamage() {
        int slot = num("posthit", "health");
        FactBuffer f = populator.populate(new ActivationContext(actor(), null, null, null, 0.0, null, 0,
                "ENTITY_ATTACK", false, 0, 0, 6.0, 0.0, ""), 0L, new FactMask(1L << slot, 0L, 0L));
        assertEquals(9.0, f.number(slot)); // actor() is at 15 health
    }

    @Test
    void postHitHealthStaysZeroWithNoPendingHit() {
        // Every non-DEFENSE context leaves the pending damage absent, and "absent" must not read as
        // "full health survives" — an Ender Shift gate would then fire on every mining swing.
        int slot = num("posthit", "health");
        assertEquals(0.0, populator.populate(new ActivationContext(actor(), null, null, null), 0L,
                new FactMask(1L << slot, 0L, 0L)).number(slot));
    }

    @Test
    void postHitHealthIsSkippedWhenUnmasked() {
        Player a = actor();
        populator.populate(new ActivationContext(a, null, null, null, 0.0, null, 0, "", false, 0, 0, 6.0, 0.0, ""),
                0L, FactMask.NONE);
        verify(a, never()).getHealth();
    }

    @Test
    void comboStaysUnsourcedAtZero() {
        // combo is declared so conditions referencing it compile, but no combat-streak tracker exists.
        FactBuffer f = populator.populate(new ActivationContext(actor(), null, null, null));
        assertEquals(0.0, f.number(num(null, "combo")));
    }

    @Test
    void victimInZoneIsTrueInsideAnActorOwnedZoneAndFalseOutside() {
        int slot = flag("victim.inzone");
        UUID actorId = UUID.randomUUID();
        UUID zoneWorld = UUID.randomUUID();
        Player actor = mock(Player.class);
        lenient().when(actor.getUniqueId()).thenReturn(actorId);
        lenient().when(actor.getWorld()).thenReturn(mock(World.class)); // a different world ref → the distance block skips
        World world = mock(World.class);
        lenient().when(world.getUID()).thenReturn(zoneWorld);
        LivingEntity victim = mock(LivingEntity.class);
        lenient().when(victim.getWorld()).thenReturn(world);

        OwnerZones.mark(actorId, zoneWorld, 0, 0, 4.0, 60_000L, null); // %victim.inzone% reads by owner, not victim
        try {
            when(victim.getLocation()).thenReturn(new Location(world, 1, 64, 1)); // dist 1.4 < 4 → inside
            assertTrue(populator.populate(new ActivationContext(actor, victim, null, null)).flag(slot));

            when(victim.getLocation()).thenReturn(new Location(world, 20, 64, 20)); // far outside the radius
            assertFalse(populator.populate(new ActivationContext(actor, victim, null, null)).flag(slot));
        } finally {
            OwnerZones.clearAll();
        }
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
    void projectileGeometryComesStraightOffTheContext() {
        int heightSlot = num(null, "impactheight");
        int kindSlot = str(null, "projectilekind");
        FactMask mask = new FactMask(1L << heightSlot, 0L, 1L << kindSlot);

        FactBuffer hit = populator.populate(new ActivationContext(actor(), null, null, null, 0.0, null, 0,
                "PROJECTILE", false, 0, 0, Double.NaN, 1.75, "ARROW"), 0L, mask);
        assertEquals(1.75, hit.number(heightSlot));
        assertEquals("ARROW", hit.string(kindSlot));

        // A melee swing carries no projectile: 0 and empty, so a headshot gate cannot fire off a sword hit.
        FactBuffer melee = populator.populate(new ActivationContext(actor(), null, null, null), 0L, mask);
        assertEquals(0.0, melee.number(heightSlot));
        assertEquals("", melee.string(kindSlot));
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
            b.flag("f" + i); // 80 flags: exceeds one 64-bit word, within the 128-flag ceiling
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

        assertEquals(0.0, f.number(num("actor", "health")));
        assertEquals(9.0, f.number(num("victim", "health")));
    }

    @Test
    @SuppressWarnings("deprecation") // getMaxHealth(): deprecated-not-removed cross-version accessor, stubbed on the mock.
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
