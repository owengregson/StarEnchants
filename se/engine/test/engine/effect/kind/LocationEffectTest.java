package engine.effect.kind;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectKind;
import engine.sink.Sink;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import testfx.FakeEffectCtx;

/**
 * The world/block/spawn effect kinds — those that emit at a {@code location}, a resolved {@code targetLocations}
 * slot, or each target's own location — collapsed from one file per kind. Each kind keeps its no-op guard
 * (no location / no target → no intent) as its own row. Ctx is the strict {@link FakeEffectCtx}; a no-op row
 * still sets any arg the kind reads BEFORE its guard (e.g. BREAK_BLOCK reads {@code drops} before iterating),
 * matching the production read order rather than relying on a mock's silent default.
 */
class LocationEffectTest {

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
                atLocation("PARTICLE → particle(id, count)", new ParticleEffect(),
                        c -> c.with("particle", 9).with("count", 20), (s, loc) -> verify(s).particle(loc, 9, 20)));
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
                    verify(sink).breakBlock(a, false);
                    verify(sink).breakBlock(b, false);
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
                dynamicTest("GUARD → guard(attacker, at, type, count, ttl, name)", () -> {
                    Location at = mock(Location.class);
                    LivingEntity attacker = mock(LivingEntity.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create().location(at)
                            .with("type", 42).with("count", 2).with("ttl", 200).with("name", "&bGuardian")
                            .targets("who", attacker);
                    Sink sink = mock(Sink.class);
                    new GuardEffect().run(ctx, sink);
                    verify(sink).guard(attacker, at, 42, 2, 200, "&bGuardian");
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("PROJECTILE → launchProjectile(actor, type, count, speed)", () -> {
                    Player actor = mock(Player.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create().actor(actor)
                            .with("type", 6).with("count", 3).with("speed", 1.5);
                    Sink sink = mock(Sink.class);
                    new ProjectileEffect().run(ctx, sink);
                    verify(sink).launchProjectile(actor, 6, 3, 1.5);
                    verifyNoMoreInteractions(sink);
                }));
    }

    @TestFactory
    List<DynamicTest> spawnEntity() {
        return List.of(
                dynamicTest("SPAWN_ENTITY → spawnEntity at each target's location (owner none)", () -> {
                    LivingEntity who = mock(LivingEntity.class);
                    Location loc = mock(Location.class);
                    when(who.getLocation()).thenReturn(loc);
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .with("type", 5).with("count", 3).with("ttl", 0).with("health", 20.0)
                            .with("owner", "none").targets("who", who);
                    Sink sink = mock(Sink.class);
                    new SpawnEntityEffect().run(ctx, sink);
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
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .with("type", 9).with("count", 1).with("ttl", 0).with("health", 0.0)
                            .with("owner", "activator").actor(actor).targets("who", who);
                    Sink sink = mock(Sink.class);
                    new SpawnEntityEffect().run(ctx, sink);
                    verify(sink).spawnEntity(loc, 9, 1, 0, 0.0, actorId);
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("SPAWN_ENTITY with no targets → falls back to the activation location", () -> {
                    Location loc = mock(Location.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .with("type", 7).with("count", 1).with("ttl", 200).with("health", 0.0)
                            .with("owner", "none").location(loc); // no "who" targets resolved
                    Sink sink = mock(Sink.class);
                    new SpawnEntityEffect().run(ctx, sink);
                    verify(sink).spawnEntity(loc, 7, 1, 200, 0.0, null);
                    verifyNoMoreInteractions(sink);
                }));
    }
}
