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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import testfx.FakeEffectCtx;

/**
 * The effect kinds that branch on a {@code mode}/{@code channel}/{@code type}/{@code side} arg to different
 * Sink calls, collapsed from one file per kind. Each row pins one branch: its args in, its Sink call out, and
 * {@code verifyNoMoreInteractions} so a branch never emits a stray intent. Ctx is the strict
 * {@link FakeEffectCtx}. The "skip" rows assert a target the branch should ignore produces no interaction.
 */
class ModeDispatchEffectTest {

    /** One living target under "who"; the row verifies the single resulting call. */
    private static DynamicTest living(String label, EffectKind kind, Consumer<FakeEffectCtx> args,
            BiConsumer<Sink, LivingEntity> verify) {
        return dynamicTest(label, () -> {
            LivingEntity t = mock(LivingEntity.class);
            FakeEffectCtx ctx = FakeEffectCtx.create().targets("who", t);
            args.accept(ctx);
            Sink sink = mock(Sink.class);
            kind.run(ctx, sink);
            verify.accept(sink, t);
            verifyNoMoreInteractions(sink);
        });
    }

    /** One player target under "who"; the row verifies the resulting call(s). */
    private static DynamicTest player(String label, EffectKind kind, Consumer<FakeEffectCtx> args,
            BiConsumer<Sink, Player> verify) {
        return dynamicTest(label, () -> {
            Player p = mock(Player.class);
            FakeEffectCtx ctx = FakeEffectCtx.create().targets("who", p);
            args.accept(ctx);
            Sink sink = mock(Sink.class);
            kind.run(ctx, sink);
            verify.accept(sink, p);
            verifyNoMoreInteractions(sink);
        });
    }

    /** No target — a fold/flag contribution; the row verifies the single contribution. */
    private static DynamicTest noTarget(String label, EffectKind kind, Consumer<FakeEffectCtx> args,
            Consumer<Sink> verify) {
        return dynamicTest(label, () -> {
            FakeEffectCtx ctx = FakeEffectCtx.create();
            args.accept(ctx);
            Sink sink = mock(Sink.class);
            kind.run(ctx, sink);
            verify.accept(sink);
            verifyNoMoreInteractions(sink);
        });
    }

    /** Actor-sourced feedback (no target list); the row verifies the call against the actor. */
    private static DynamicTest actorOnly(String label, EffectKind kind, Consumer<FakeEffectCtx> args,
            BiConsumer<Sink, Player> verify) {
        return dynamicTest(label, () -> {
            Player actor = mock(Player.class);
            FakeEffectCtx ctx = FakeEffectCtx.create().actor(actor);
            args.accept(ctx);
            Sink sink = mock(Sink.class);
            kind.run(ctx, sink);
            verify.accept(sink, actor);
            verifyNoMoreInteractions(sink);
        });
    }

    @TestFactory
    List<DynamicTest> healthMod() {
        return List.of(
                living("HEALTH_MOD give → heal", new HealthModEffect(),
                        c -> c.with("amount", 4.0).with("mode", "give"), (s, t) -> verify(s).heal(t, 4.0)),
                living("HEALTH_MOD take → damage", new HealthModEffect(),
                        c -> c.with("amount", 6.0).with("mode", "take"), (s, t) -> verify(s).damage(t, 6.0)),
                dynamicTest("HEALTH_MOD transfer → damage victim + heal actor (lifesteal)", () -> {
                    LivingEntity victim = mock(LivingEntity.class);
                    Player actor = mock(Player.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .with("amount", 5.0).with("mode", "transfer").targets("who", victim).actor(actor);
                    Sink sink = mock(Sink.class);
                    new HealthModEffect().run(ctx, sink);
                    verify(sink).damage(victim, 5.0);
                    verify(sink).heal(actor, 5.0); // actor gains exactly what was drained
                    verifyNoMoreInteractions(sink);
                }));
    }

    @TestFactory
    List<DynamicTest> damageMod() {
        // percent modes divide by 100; flat modes pass through. Each (side, mode) routes to one fold bucket.
        return List.of(
                noTarget("DAMAGE_MOD attack/add → addOutgoingDamage(%/100)", new DamageModEffect(),
                        c -> c.with("side", "attack").with("mode", "add").with("amount", 25.0),
                        s -> verify(s).addOutgoingDamage(0.25)),
                noTarget("DAMAGE_MOD defense/add → addDamageReduction(%/100)", new DamageModEffect(),
                        c -> c.with("side", "defense").with("mode", "add").with("amount", 15.0),
                        s -> verify(s).addDamageReduction(0.15)),
                noTarget("DAMAGE_MOD attack/flat → addFlatDamage", new DamageModEffect(),
                        c -> c.with("side", "attack").with("mode", "flat").with("amount", 2.0),
                        s -> verify(s).addFlatDamage(2.0)),
                noTarget("DAMAGE_MOD defense/flat → addFlatReduction", new DamageModEffect(),
                        c -> c.with("side", "defense").with("mode", "flat").with("amount", 3.0),
                        s -> verify(s).addFlatReduction(3.0)));
    }

    @TestFactory
    List<DynamicTest> money() {
        return List.of(
                player("MONEY give → giveMoney", new MoneyEffect(),
                        c -> c.with("amount", 100.0).with("mode", "give"), (s, p) -> verify(s).giveMoney(p, 100.0)),
                player("MONEY take → takeMoney", new MoneyEffect(),
                        c -> c.with("amount", 50.0).with("mode", "take"), (s, p) -> verify(s).takeMoney(p, 50.0)),
                dynamicTest("MONEY transfer → take victim + give actor (non-player target skipped)", () -> {
                    Player victim = mock(Player.class);
                    Player actor = mock(Player.class);
                    LivingEntity mob = mock(LivingEntity.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .with("amount", 25.0).with("mode", "transfer").targets("who", victim, mob).actor(actor);
                    Sink sink = mock(Sink.class);
                    new MoneyEffect().run(ctx, sink);
                    verify(sink).takeMoney(victim, 25.0);
                    verify(sink).giveMoney(actor, 25.0);
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("MONEY steal_percent → fraction of victim's balance to the actor", () -> {
                    Player victim = mock(Player.class);
                    Player actor = mock(Player.class);
                    LivingEntity mob = mock(LivingEntity.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .with("amount", 50.0).with("mode", "steal_percent").targets("who", victim, mob).actor(actor);
                    Sink sink = mock(Sink.class);
                    new MoneyEffect().run(ctx, sink);
                    verify(sink).stealMoneyPercent(victim, actor, 0.5); // amount is a percent → 0.5 fraction
                    verifyNoMoreInteractions(sink);
                }));
    }

    @TestFactory
    List<DynamicTest> exp() {
        return List.of(
                player("EXP give → giveExp", new ExpEffect(),
                        c -> c.with("amount", 50).with("mode", "give"), (s, p) -> verify(s).giveExp(p, 50)),
                player("EXP take → takeExp", new ExpEffect(),
                        c -> c.with("amount", 20).with("mode", "take"), (s, p) -> verify(s).takeExp(p, 20)),
                dynamicTest("EXP transfer → take victim + give actor (non-player target skipped)", () -> {
                    Player victim = mock(Player.class);
                    Player actor = mock(Player.class);
                    LivingEntity mob = mock(LivingEntity.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .with("amount", 25).with("mode", "transfer").targets("who", victim, mob).actor(actor);
                    Sink sink = mock(Sink.class);
                    new ExpEffect().run(ctx, sink);
                    verify(sink).takeExp(victim, 25);
                    verify(sink).giveExp(actor, 25);
                    verifyNoMoreInteractions(sink);
                }));
    }

    @TestFactory
    List<DynamicTest> food() {
        return List.of(
                player("FOOD give → feed", new FoodEffect(),
                        c -> c.with("amount", 6).with("mode", "give"), (s, p) -> verify(s).feed(p, 6)),
                player("FOOD take → takeFood", new FoodEffect(),
                        c -> c.with("amount", 4).with("mode", "take"), (s, p) -> verify(s).takeFood(p, 4)),
                living("FOOD on a non-player → skipped (no hunger bar)", new FoodEffect(),
                        c -> c.with("amount", 6).with("mode", "give"), (s, t) -> { }));
    }

    @TestFactory
    List<DynamicTest> durability() {
        // asymmetry: restore is player-only; armor damage hits any living target.
        return List.of(
                player("DURABILITY item/restore → repairHand", new DurabilityEffect(),
                        c -> c.with("amount", -1).with("target", "item").with("mode", "restore"),
                        (s, p) -> verify(s).repairHand(p, -1)),
                player("DURABILITY armor/restore → repairArmor", new DurabilityEffect(),
                        c -> c.with("amount", 200).with("target", "armor").with("mode", "restore"),
                        (s, p) -> verify(s).repairArmor(p, 200)),
                living("DURABILITY armor/damage → damageArmor (any living)", new DurabilityEffect(),
                        c -> c.with("amount", 50).with("target", "armor").with("mode", "damage"),
                        (s, t) -> verify(s).damageArmor(t, 50)),
                player("DURABILITY item/damage → damageHand", new DurabilityEffect(),
                        c -> c.with("amount", 10).with("target", "item").with("mode", "damage"),
                        (s, p) -> verify(s).damageHand(p, 10)),
                player("DURABILITY all/restore → repairHand + repairArmor", new DurabilityEffect(),
                        c -> c.with("amount", -1).with("target", "all").with("mode", "restore"),
                        (s, p) -> {
                            verify(s).repairHand(p, -1);
                            verify(s).repairArmor(p, -1);
                        }),
                living("DURABILITY restore on a non-player → skipped", new DurabilityEffect(),
                        c -> c.with("amount", -1).with("target", "item").with("mode", "restore"), (s, t) -> { }));
    }

    @TestFactory
    List<DynamicTest> message() {
        // MESSAGE routes by channel, collapsing the deleted ACTIONBAR and TITLE kinds.
        return List.of(
                actorOnly("MESSAGE chat → message", new MessageEffect(),
                        c -> c.with("channel", "chat").with("text", "hi"), (s, a) -> verify(s).message(a, "hi")),
                actorOnly("MESSAGE actionbar → actionBar", new MessageEffect(),
                        c -> c.with("channel", "actionbar").with("text", "charged"),
                        (s, a) -> verify(s).actionBar(a, "charged")),
                actorOnly("MESSAGE title → title(text, subtitle, timings)", new MessageEffect(),
                        c -> c.with("channel", "title").with("text", "&cCRITICAL").with("subtitle", "&7you struck hard")
                                .with("fadeIn", 10).with("stay", 40).with("fadeOut", 10),
                        (s, a) -> verify(s).title(a, "&cCRITICAL", "&7you struck hard", 10, 40, 10)));
    }

    @TestFactory
    List<DynamicTest> immune() {
        // every type token maps to the ImmuneStore ordinal the Sink expects (sword=0..all=4); player-only.
        return List.of(
                player("IMMUNE sword → 0", new ImmuneEffect(),
                        c -> c.with("type", "sword").with("duration", 100), (s, p) -> verify(s).immune(p, 0, 100)),
                player("IMMUNE axe → 1", new ImmuneEffect(),
                        c -> c.with("type", "axe").with("duration", 100), (s, p) -> verify(s).immune(p, 1, 100)),
                player("IMMUNE projectile → 2", new ImmuneEffect(),
                        c -> c.with("type", "projectile").with("duration", 100), (s, p) -> verify(s).immune(p, 2, 100)),
                player("IMMUNE potion → 3", new ImmuneEffect(),
                        c -> c.with("type", "potion").with("duration", 100), (s, p) -> verify(s).immune(p, 3, 100)),
                player("IMMUNE all → 4", new ImmuneEffect(),
                        c -> c.with("type", "all").with("duration", 100), (s, p) -> verify(s).immune(p, 4, 100)));
    }

    @TestFactory
    List<DynamicTest> velocity() {
        return List.of(
                dynamicTest("VELOCITY add → launch(x, y, z) per target", () -> {
                    LivingEntity a = mock(LivingEntity.class);
                    LivingEntity b = mock(LivingEntity.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .with("mode", "add").with("x", 0.0).with("y", 1.2).with("z", 0.0).targets("who", a, b);
                    Sink sink = mock(Sink.class);
                    new VelocityEffect().run(ctx, sink);
                    verify(sink).launch(a, 0.0, 1.2, 0.0);
                    verify(sink).launch(b, 0.0, 1.2, 0.0);
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("VELOCITY away → knockback from the actor", () -> {
                    LivingEntity a = mock(LivingEntity.class);
                    Player actor = mock(Player.class);
                    Location loc = mock(Location.class);
                    when(actor.getLocation()).thenReturn(loc);
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .with("mode", "away").with("strength", 1.5).actor(actor).targets("who", a);
                    Sink sink = mock(Sink.class);
                    new VelocityEffect().run(ctx, sink);
                    verify(sink).knockback(a, loc, 1.5);
                    verifyNoMoreInteractions(sink);
                }));
    }

    @TestFactory
    List<DynamicTest> teleport() {
        return List.of(
                dynamicTest("TELEPORT to VICTIM → each target to the victim's location", () -> {
                    LivingEntity victim = mock(LivingEntity.class);
                    Location victimLoc = mock(Location.class);
                    when(victim.getLocation()).thenReturn(victimLoc);
                    LivingEntity mover = mock(LivingEntity.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .with("to", "VICTIM").victim(victim).targets("who", mover);
                    Sink sink = mock(Sink.class);
                    new TeleportEffect().run(ctx, sink);
                    verify(sink).teleport(mover, victimLoc);
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("TELEPORT to ACTOR → each target to the actor's location", () -> {
                    Player actor = mock(Player.class);
                    Location actorLoc = mock(Location.class);
                    when(actor.getLocation()).thenReturn(actorLoc);
                    LivingEntity mover = mock(LivingEntity.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .with("to", "ACTOR").actor(actor).targets("who", mover);
                    Sink sink = mock(Sink.class);
                    new TeleportEffect().run(ctx, sink);
                    verify(sink).teleport(mover, actorLoc);
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("TELEPORT to VICTIM with no victim → no-op", () -> {
                    FakeEffectCtx ctx = FakeEffectCtx.create().with("to", "VICTIM"); // non-combat: victim() null
                    Sink sink = mock(Sink.class);
                    new TeleportEffect().run(ctx, sink);
                    verifyNoInteractions(sink);
                }));
    }

    @TestFactory
    List<DynamicTest> walker() {
        // the `replace` enum maps to the Sink's 0/1/2 replace-mode.
        return List.of(
                walkerRow("WALKER AIR_ONLY → mode 0", "AIR_ONLY", 0),
                walkerRow("WALKER REPLACEABLE → mode 1", "REPLACEABLE", 1),
                walkerRow("WALKER ANY → mode 2", "ANY", 2));
    }

    private static DynamicTest walkerRow(String label, String replace, int mode) {
        return dynamicTest(label, () -> {
            LivingEntity who = mock(LivingEntity.class);
            Location loc = mock(Location.class);
            when(who.getLocation()).thenReturn(loc);
            FakeEffectCtx ctx = FakeEffectCtx.create()
                    .with("material", 42).with("ticks", 80).with("radius", 1).with("replace", replace)
                    .targets("who", who);
            Sink sink = mock(Sink.class);
            new WalkerEffect().run(ctx, sink);
            verify(sink).tempPlatform(loc, 42, 1, 80, mode);
            verifyNoMoreInteractions(sink);
        });
    }
}
