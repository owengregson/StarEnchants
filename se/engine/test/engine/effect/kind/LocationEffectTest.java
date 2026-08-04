package engine.effect.kind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.sink.SummonFlags;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockito.ArgumentCaptor;
import testfx.FakeEffectCtx;

/**
 * The world/block/spawn effect kinds — those that emit at a {@code location}, a resolved {@code targetLocations}
 * slot, or each target's own location — collapsed from one file per kind. Each kind keeps its no-op guard
 * (no location / no target → no intent) as its own row. Ctx is the strict {@link FakeEffectCtx}; a no-op row
 * still sets any arg the kind reads BEFORE its guard (e.g. BREAK_BLOCK reads {@code drops} before iterating),
 * matching the production read order rather than relying on a mock's silent default.
 */
class LocationEffectTest {

    /** A living target that reports {@code at} as its own location. */
    private static LivingEntity targetAt(Location at) {
        LivingEntity target = mock(LivingEntity.class);
        when(target.getLocation()).thenReturn(at);
        return target;
    }

    /** Emits at {@code ctx.location()}; the row verifies the single call against that location. */
    private static DynamicTest atLocation(String label, EffectKind kind, Consumer<FakeEffectCtx> args,
            BiConsumer<Sink, Location> verify) {
        return dynamicTest(label, () -> {
            Location loc = mock(Location.class);
            FakeEffectCtx ctx = FakeEffectCtx.create().location(loc);
            args.accept(ctx);
            Sink sink = mock(Sink.class);
            kind.run(ctx, sink);
            verify.accept(sink, loc);
            verifyNoMoreInteractions(sink);
        });
    }

    @TestFactory
    List<DynamicTest> singleLocationIntents() {
        return List.of(
                atLocation("FIREWORK → firework(power)", new FireworkEffectKind(),
                        c -> c.with("power", 2), (s, loc) -> verify(s).firework(loc, 2)),
                atLocation("DROP_ITEM → dropItem(material, count)", new DropItemEffect(),
                        c -> c.with("material", 11).with("count", 3), (s, loc) -> verify(s).dropItem(loc, 11, 3)),
                atLocation("SOUND → sound(id, volume, pitch)", new SoundEffect(),
                        c -> c.with("sound", 3).with("volume", 1.0).with("pitch", 1.0),
                        (s, loc) -> verify(s).sound(loc, 3, 1.0f, 1.0f)),
                atLocation("PARTICLE → particle(id, count, block=-1, spread on X/Z, spread-y on Y) at the activation location (no who)",
                        new ParticleEffect(),
                        c -> c.with("particle", 9).with("count", 20).with("spread", 1.5).with("spread-y", 2.0),
                        (s, loc) -> verify(s).particle(loc, 9, 20, -1, 1.5, 2.0, 1.5)));
    }

    @TestFactory
    List<DynamicTest> noLocationGuards() {
        return List.of(
                dynamicTest("FIREWORK with no location → no-op", () -> {
                    Sink sink = mock(Sink.class);
                    new FireworkEffectKind().run(FakeEffectCtx.create(), sink); // location() null
                    verifyNoInteractions(sink);
                }),
                dynamicTest("DROP_ITEM with no location → no-op", () -> {
                    Sink sink = mock(Sink.class);
                    new DropItemEffect().run(FakeEffectCtx.create(), sink);
                    verifyNoInteractions(sink);
                }),
                dynamicTest("GUARD with no location → no-op", () -> {
                    Sink sink = mock(Sink.class);
                    new GuardEffect().run(FakeEffectCtx.create(), sink);
                    verifyNoInteractions(sink);
                }),
                dynamicTest("SOUND with neither who nor a location → no-op", () -> {
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .with("sound", 3).with("volume", 1.0).with("pitch", 1.0); // read before the guard
                    Sink sink = mock(Sink.class);
                    new SoundEffect().run(ctx, sink);
                    verifyNoInteractions(sink);
                }));
    }

    @TestFactory
    List<DynamicTest> targetLocationIntents() {
        return List.of(
                dynamicTest("BREAK_BLOCK → breakBlock(drops) per target location", () -> {
                    Location a = mock(Location.class);
                    Location b = mock(Location.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create().locations("at", a, b).with("drops", false);
                    Sink sink = mock(Sink.class);
                    new BreakBlockEffect().run(ctx, sink);
                    // drops=false carries an EMPTY void list: the exception list only means anything against
                    // a yielding break, and passing it through would let a stray id read as "also void".
                    verify(sink).breakBlock(a, false, List.of());
                    verify(sink).breakBlock(b, false, List.of());
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("BREAK_BLOCK void-materials rides a YIELDING break as the per-block exception", () -> {
                    Location a = mock(Location.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create().locations("at", a)
                            .with("drops", true).with("void-materials", List.of(4, 11));
                    Sink sink = mock(Sink.class);
                    new BreakBlockEffect().run(ctx, sink);
                    verify(sink).breakBlock(a, true, List.of(4, 11));
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("BREAK_BLOCK with no target locations → no-op", () -> {
                    FakeEffectCtx ctx = FakeEffectCtx.create().with("drops", false); // drops read before the loop
                    Sink sink = mock(Sink.class);
                    new BreakBlockEffect().run(ctx, sink);
                    verifyNoInteractions(sink);
                }),
                dynamicTest("SET_BLOCK → blockChange(material) per target location", () -> {
                    Location a = mock(Location.class);
                    Location b = mock(Location.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create().locations("at", a, b).with("material", 7);
                    Sink sink = mock(Sink.class);
                    new SetBlockEffect().run(ctx, sink);
                    verify(sink).blockChange(a, 7);
                    verify(sink).blockChange(b, 7);
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("SET_BLOCK with no target locations → no-op", () -> {
                    FakeEffectCtx ctx = FakeEffectCtx.create().with("material", 7); // material read before the loop
                    Sink sink = mock(Sink.class);
                    new SetBlockEffect().run(ctx, sink);
                    verifyNoInteractions(sink);
                }));
    }

    @TestFactory
    List<DynamicTest> perTargetWorldIntents() {
        return List.of(
                dynamicTest("EXPLODE → explode(power, breakBlocks) at the target's location", () -> {
                    LivingEntity target = mock(LivingEntity.class);
                    Location loc = mock(Location.class);
                    when(target.getLocation()).thenReturn(loc);
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .with("power", 4.0).with("breakBlocks", false).targets("who", target);
                    Sink sink = mock(Sink.class);
                    new ExplodeEffect().run(ctx, sink);
                    verify(sink).explode(loc, 4.0, false);
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("EXPLODE skips a target whose location read faults → no-op (guarded)", () -> {
                    LivingEntity remote = mock(LivingEntity.class); // @Attacker on a DEFENSE trigger can be cross-region
                    when(remote.getLocation()).thenThrow(new IllegalStateException("wrong region"));
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .with("power", 4.0).with("breakBlocks", false).targets("who", remote);
                    Sink sink = mock(Sink.class);
                    new ExplodeEffect().run(ctx, sink); // the thrown remote read is swallowed, not propagated
                    verifyNoInteractions(sink);
                }),
                dynamicTest("PHANTOM_BLOCKS → phantomBlocks per target, ally/enemy materials in their own slots",
                        () -> {
                            World world = mock(World.class);
                            Location a = new Location(world, 10, 64, 10);
                            Location b = new Location(world, 30, 64, 30);
                            Player actor = mock(Player.class);
                            // Every scalar distinct, so a transposed material or a swapped radius/duration fails.
                            FakeEffectCtx ctx = FakeEffectCtx.create().actor(actor)
                                    .with("radius", 4).with("material-ally", 7).with("material-enemy", 11)
                                    .with("duration", 100)
                                    .targets("who", targetAt(a), targetAt(b));
                            Sink sink = mock(Sink.class);
                            new PhantomBlocksEffect().run(ctx, sink);
                            verify(sink).phantomBlocks(a, actor, 4, 7, 11, 100);
                            verify(sink).phantomBlocks(b, actor, 4, 7, 11, 100);
                            verifyNoMoreInteractions(sink);
                        }),
                dynamicTest("PHANTOM_BLOCKS skips a target whose location read faults → no-op (guarded)", () -> {
                    LivingEntity remote = mock(LivingEntity.class); // @Attacker on a DEFENSE trigger can be cross-region
                    when(remote.getLocation()).thenThrow(new IllegalStateException("wrong region"));
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .with("radius", 4).with("material-ally", 7).with("material-enemy", 11)
                            .with("duration", 100).targets("who", remote);
                    Sink sink = mock(Sink.class);
                    new PhantomBlocksEffect().run(ctx, sink);
                    verifyNoInteractions(sink);
                }),
                dynamicTest("PHANTOM_BLOCKS skips a world-less origin (nothing to scan a patch in)", () -> {
                    LivingEntity target = mock(LivingEntity.class);
                    when(target.getLocation()).thenReturn(new Location(null, 0, 64, 0));
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .with("radius", 4).with("material-ally", 7).with("material-enemy", 11)
                            .with("duration", 100).targets("who", target);
                    Sink sink = mock(Sink.class);
                    new PhantomBlocksEffect().run(ctx, sink);
                    verifyNoInteractions(sink);
                }),
                dynamicTest("GUARD → guard(attacker, at, type, count, ttl, name, owner) — no actor → null owner", () -> {
                    Location at = mock(Location.class);
                    LivingEntity attacker = mock(LivingEntity.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create().location(at)
                            .with("type", 42).with("count", 2).with("ttl", 200).with("name", "&bGuardian")
                            .with("health", 0.0).with("speed", 0.0).with("effects", List.<Integer>of())
                            .targets("who", attacker);
                    Sink sink = mock(Sink.class);
                    new GuardEffect().run(ctx, sink);
                    verify(sink).guard(attacker, at, 42, 2, 200, "&bGuardian", null, 0.0, 0.0, List.of());
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("GUARD → owner is the activation actor (ADR-0049 Blood Link binding)", () -> {
                    Location at = mock(Location.class);
                    LivingEntity attacker = mock(LivingEntity.class);
                    UUID ownerId = UUID.randomUUID();
                    Player owner = mock(Player.class);
                    when(owner.getUniqueId()).thenReturn(ownerId);
                    FakeEffectCtx ctx = FakeEffectCtx.create().location(at).actor(owner)
                            .with("type", 42).with("count", 1).with("ttl", 200).with("name", "&bGuardian")
                            .with("health", 0.0).with("speed", 0.0).with("effects", List.<Integer>of())
                            .targets("who", attacker);
                    Sink sink = mock(Sink.class);
                    new GuardEffect().run(ctx, sink);
                    verify(sink).guard(attacker, at, 42, 1, 200, "&bGuardian", ownerId, 0.0, 0.0, List.of());
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("GUARD stat params ride to the Sink (health, speed, potion loadout)", () -> {
                    Location at = mock(Location.class);
                    LivingEntity attacker = mock(LivingEntity.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create().location(at)
                            .with("type", 42).with("count", 1).with("ttl", 600).with("name", "&bGuardian")
                            .with("health", 120.0).with("speed", 1.4).with("effects", List.of(7, 11))
                            .targets("who", attacker);
                    Sink sink = mock(Sink.class);
                    new GuardEffect().run(ctx, sink);
                    verify(sink).guard(attacker, at, 42, 1, 600, "&bGuardian", null, 120.0, 1.4, List.of(7, 11));
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("SOUND with who → one entity-anchored play per resolved target", () -> {
                    LivingEntity a = mock(LivingEntity.class);
                    LivingEntity b = mock(LivingEntity.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create().targets("who", a, b)
                            .with("sound", 3).with("volume", 0.5).with("pitch", 1.5);
                    Sink sink = mock(Sink.class);
                    new SoundEffect().run(ctx, sink);
                    // Entity-anchored, not location-anchored: the Sink reads each body's position at dispatch,
                    // so a target that moved (or sits in another region) still gets its cue where it actually is.
                    verify(sink).sound(a, 3, 0.5f, 1.5f);
                    verify(sink).sound(b, 3, 0.5f, 1.5f);
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("SOUND with who wins over the activation location", () -> {
                    LivingEntity target = mock(LivingEntity.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create().location(mock(Location.class))
                            .targets("who", target).with("sound", 3).with("volume", 1.0).with("pitch", 1.0);
                    Sink sink = mock(Sink.class);
                    new SoundEffect().run(ctx, sink);
                    verify(sink).sound(target, 3, 1.0f, 1.0f);
                    verifyNoMoreInteractions(sink); // never BOTH — the who slot replaces the anchor, it does not add one
                }),
                dynamicTest("PROJECTILE → launchProjectile(actor, type, count, speed, yield, incendiary)", () -> {
                    Player actor = mock(Player.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create().actor(actor)
                            .with("type", 6).with("count", 3).with("speed", 1.5).with("yield", 2.0)
                            .with("incendiary", true);
                    Sink sink = mock(Sink.class);
                    new ProjectileEffect().run(ctx, sink);
                    verify(sink).launchProjectile(actor, 6, 3, 1.5, 2.0, true);
                    verifyNoMoreInteractions(sink);
                }));
    }

    /**
     * A ctx pre-filled from the kind's OWN declared defaults, so a row states only what it varies and a default
     * that silently shifts fails a behaviour assertion here instead of hiding behind a re-typed literal.
     */
    private static FakeEffectCtx spawnDefaults(engine.spec.EffectSpec spec) {
        return testfx.SpecDrivenCtx.defaults(spec).with("effects", List.<Integer>of());
    }

    @TestFactory
    List<DynamicTest> spawnEntity() {
        return List.of(
                dynamicTest("SPAWN_ENTITY → spawnEntity at each target's location (owner none)", () -> {
                    LivingEntity who = mock(LivingEntity.class);
                    Location loc = mock(Location.class);
                    when(who.getLocation()).thenReturn(loc);
                    FakeEffectCtx ctx = spawnDefaults(SpawnEntityEffect.SPEC)
                            .with("type", 5).with("count", 3).with("health", 20.0).targets("who", who);
                    Sink sink = mock(Sink.class);
                    new SpawnEntityEffect().run(ctx, sink);
                    // Every declared default together is still the plain, byte-stable spawn — the whole point
                    // of SummonFlags.none(), and what keeps existing content unchanged as params are appended.
                    verify(sink).spawnEntity(loc, 5, 3, 0, 20.0, null);
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("SPAWN_ENTITY owner=activator → tamed to the actor's id", () -> {
                    LivingEntity who = mock(LivingEntity.class);
                    Location loc = mock(Location.class);
                    when(who.getLocation()).thenReturn(loc);
                    UUID actorId = UUID.randomUUID();
                    Player actor = mock(Player.class);
                    when(actor.getUniqueId()).thenReturn(actorId);
                    FakeEffectCtx ctx = spawnDefaults(SpawnEntityEffect.SPEC)
                            .with("type", 9).with("owner", "activator").actor(actor).targets("who", who);
                    Sink sink = mock(Sink.class);
                    new SpawnEntityEffect().run(ctx, sink);
                    verify(sink).spawnEntity(loc, 9, 1, 0, 0.0, actorId);
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("SPAWN_ENTITY with no targets → falls back to the activation location", () -> {
                    Location loc = mock(Location.class);
                    FakeEffectCtx ctx = spawnDefaults(SpawnEntityEffect.SPEC)
                            .with("type", 7).with("ttl", 200).location(loc); // no "who" targets resolved
                    Sink sink = mock(Sink.class);
                    new SpawnEntityEffect().run(ctx, sink);
                    verify(sink).spawnEntity(loc, 7, 1, 200, 0.0, null);
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("SPAWN_ENTITY on the actor spawns at the origin snapshot", () -> {
                    Player self = mock(Player.class); // @Self resolves the actor into the "who" slot
                    Location loc = mock(Location.class);
                    FakeEffectCtx ctx = spawnDefaults(SpawnEntityEffect.SPEC)
                            .with("type", 5).actor(self).actorOrigin(loc).targets("who", self);
                    Sink sink = mock(Sink.class);
                    new SpawnEntityEffect().run(ctx, sink);
                    verify(sink).spawnEntity(loc, 5, 1, 0, 0.0, null);
                    verify(self, never()).getLocation(); // the snapshot is the sole actor read
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("SPAWN_ENTITY on the actor with no origin → activation-location fallback", () -> {
                    Player self = mock(Player.class);
                    Location fallback = mock(Location.class);
                    FakeEffectCtx ctx = spawnDefaults(SpawnEntityEffect.SPEC)
                            .with("type", 7).actor(self).targets("who", self).location(fallback);
                    Sink sink = mock(Sink.class);
                    new SpawnEntityEffect().run(ctx, sink); // actor target skipped (no origin) → any=false → fallback
                    verify(sink).spawnEntity(fallback, 7, 1, 0, 0.0, null);
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("SPAWN_ENTITY speed>0 → spawnSummon carries the movement-speed multiplier", () -> {
                    LivingEntity who = mock(LivingEntity.class);
                    Location loc = mock(Location.class);
                    when(who.getLocation()).thenReturn(loc);
                    FakeEffectCtx ctx = spawnDefaults(SpawnEntityEffect.SPEC)
                            .with("type", 5).with("speed", 2.0).targets("who", who);
                    Sink sink = mock(Sink.class);
                    new SpawnEntityEffect().run(ctx, sink);
                    // a set speed routes off the byte-stable plain path to the summon path, carrying the multiplier
                    ArgumentCaptor<SummonFlags> flags = ArgumentCaptor.forClass(SummonFlags.class);
                    verify(sink).spawnSummon(eq(loc), eq(5), eq(1), eq(0), eq(0.0), isNull(), isNull(),
                            flags.capture());
                    assertEquals(2.0, flags.getValue().speedMultiplier());
                    assertFalse(flags.getValue().payloadArmed());
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("SPAWN_ENTITY payload-phase → the summon path, tracked and armed", () -> {
                    LivingEntity who = mock(LivingEntity.class);
                    Location loc = mock(Location.class);
                    when(who.getLocation()).thenReturn(loc);
                    FakeEffectCtx ctx = spawnDefaults(SpawnEntityEffect.SPEC)
                            .with("type", 5).with("payload-phase", "detonate").with("payload-radius", 4.0)
                            .with("payload-height", 2.0).with("payload-filter", "ENEMIES")
                            .with("payload-max-targets", 3).with("scatter", 2).targets("who", who);
                    Sink sink = mock(Sink.class);
                    new SpawnEntityEffect().run(ctx, sink);
                    ArgumentCaptor<SummonFlags> flags = ArgumentCaptor.forClass(SummonFlags.class);
                    verify(sink).spawnSummon(eq(loc), eq(5), eq(1), eq(0), eq(0.0), isNull(), isNull(),
                            flags.capture());
                    SummonFlags armed = flags.getValue();
                    assertEquals("detonate", armed.payloadPhase());
                    assertEquals(4.0, armed.payloadRadius());
                    assertEquals(2.0, armed.payloadHeight());
                    assertEquals("ENEMIES", armed.payloadFilter());
                    assertEquals(3, armed.payloadMaxTargets());
                    assertEquals(2, armed.scatter());
                    // Only a tracked summon lands in PetSummons, which is where every phase looks it up.
                    assertTrue(armed.tracked());
                    verifyNoMoreInteractions(sink);
                }));
    }

    @TestFactory
    List<DynamicTest> spawnSwarm() {
        return List.of(
                dynamicTest("SPAWN_SWARM → one ring intent at the actor-origin snapshot", () -> {
                    Player self = mock(Player.class);
                    Location origin = mock(Location.class);
                    FakeEffectCtx ctx = spawnDefaults(SpawnSwarmEffect.SPEC)
                            .with("type", 5).with("count", 12).with("speed", 0.5).with("cloud", true)
                            .actor(self).actorOrigin(origin);
                    Sink sink = mock(Sink.class);
                    new SpawnSwarmEffect().run(ctx, sink);
                    verify(sink).spawnSwarm(origin, 5, 12, 0.5, 1.2, 300, 0.5, self, 16.0, "", List.of());
                    verify(self, never()).getLocation(); // the snapshot is the sole actor read (ADR-0043)
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("SPAWN_SWARM with no origin → activation-location fallback", () -> {
                    Player self = mock(Player.class);
                    Location fallback = mock(Location.class);
                    FakeEffectCtx ctx = spawnDefaults(SpawnSwarmEffect.SPEC)
                            .with("type", 7).with("count", 3).with("radius", 1.0).with("ttl", 60)
                            .actor(self).location(fallback);
                    Sink sink = mock(Sink.class);
                    new SpawnSwarmEffect().run(ctx, sink);
                    // cloud: false must pass a NULL owner even with an actor present
                    verify(sink).spawnSwarm(fallback, 7, 3, 1.0, 1.2, 60, 1.0, null, 16.0, "", List.of());
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("SPAWN_SWARM with no origin and no location → no intent", () -> {
                    FakeEffectCtx ctx = spawnDefaults(SpawnSwarmEffect.SPEC).with("type", 7).with("count", 3);
                    Sink sink = mock(Sink.class);
                    new SpawnSwarmEffect().run(ctx, sink);
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("SPAWN_SWARM cloud with no actor → null owner", () -> {
                    Location origin = mock(Location.class);
                    FakeEffectCtx ctx = spawnDefaults(SpawnSwarmEffect.SPEC)
                            .with("type", 5).with("count", 12).with("speed", 0.5).with("cloud", true)
                            .actorOrigin(origin);
                    Sink sink = mock(Sink.class);
                    new SpawnSwarmEffect().run(ctx, sink);
                    verify(sink).spawnSwarm(origin, 5, 12, 0.5, 1.2, 300, 0.5, null, 16.0, "", List.of());
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("SPAWN_SWARM name/effects reach the ring intent", () -> {
                    // Undead Ruse's ring is NAMED and carries a leveled self-buff; without these the third
                    // spawner could only produce anonymous, unbuffed minions.
                    Location origin = mock(Location.class);
                    FakeEffectCtx ctx = spawnDefaults(SpawnSwarmEffect.SPEC)
                            .with("type", 5).with("count", 4).with("name", "&cRuse")
                            .with("effects", List.of(11, 13)).actorOrigin(origin);
                    Sink sink = mock(Sink.class);
                    new SpawnSwarmEffect().run(ctx, sink);
                    verify(sink).spawnSwarm(origin, 5, 4, 0.5, 1.2, 300, 1.0, null, 16.0,
                            "&cRuse", List.of(11, 13));
                    verifyNoMoreInteractions(sink);
                }));
    }
}
