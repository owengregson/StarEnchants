package engine.effect.kind;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import engine.effect.EffectKind;
import engine.sink.Sink;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import testfx.FakeEffectCtx;

/**
 * The "iterate the resolved targets, emit one intent each" effect kinds, collapsed from one file per kind into
 * one table (the per-kind files were identical but for the scalar args read and the single Sink call emitted).
 * Every row keeps the original's two-target fan-out + {@code verifyNoMoreInteractions} (no stray intent), and
 * each ctx is a {@link FakeEffectCtx} so a kind reading a param the row never set fails loudly, not vacuously.
 *
 * <p>Three shapes: {@link #entity} (a call per living target), {@link #players} (a call per player target), and
 * {@link #playerOnly} (a player target acted on, a non-player target in the SAME list skipped — the kind's
 * player-gate, which a uniform mock would pass over). Mode-branching, location, and flag/soul kinds live in
 * their own tables (ModeDispatch/Location/FlagAndSoul EffectTest).
 */
class FanOutEffectTest {

    /** A living-target fan-out: BOTH of two resolved targets receive the intent. */
    private static DynamicTest entity(String label, EffectKind kind,
            Consumer<FakeEffectCtx> args, BiConsumer<Sink, LivingEntity> perTarget) {
        return dynamicTest(label, () -> {
            LivingEntity a = mock(LivingEntity.class);
            LivingEntity b = mock(LivingEntity.class);
            FakeEffectCtx ctx = FakeEffectCtx.create().targets("who", a, b);
            args.accept(ctx);
            Sink sink = mock(Sink.class);
            kind.run(ctx, sink);
            perTarget.accept(sink, a);
            perTarget.accept(sink, b);
            verifyNoMoreInteractions(sink);
        });
    }

    /** A player-target fan-out: BOTH of two resolved player targets receive the intent. */
    private static DynamicTest players(String label, EffectKind kind,
            Consumer<FakeEffectCtx> args, BiConsumer<Sink, Player> perPlayer) {
        return dynamicTest(label, () -> {
            Player a = mock(Player.class);
            Player b = mock(Player.class);
            FakeEffectCtx ctx = FakeEffectCtx.create().targets("who", a, b);
            args.accept(ctx);
            Sink sink = mock(Sink.class);
            kind.run(ctx, sink);
            perPlayer.accept(sink, a);
            perPlayer.accept(sink, b);
            verifyNoMoreInteractions(sink);
        });
    }

    /** The player-gate: the player target is acted on; a non-player living target in the same list is skipped. */
    private static DynamicTest playerOnly(String label, EffectKind kind,
            Consumer<FakeEffectCtx> args, BiConsumer<Sink, Player> perPlayer) {
        return dynamicTest(label, () -> {
            Player p = mock(Player.class);
            LivingEntity mob = mock(LivingEntity.class);
            FakeEffectCtx ctx = FakeEffectCtx.create().targets("who", p, mob);
            args.accept(ctx);
            Sink sink = mock(Sink.class);
            kind.run(ctx, sink);
            perPlayer.accept(sink, p);
            verifyNoMoreInteractions(sink); // the non-player target contributes no intent
        });
    }

    @TestFactory
    List<DynamicTest> livingTargetIntents() {
        return List.of(
                entity("DAMAGE → damage", new DamageEffect(),
                        c -> c.with("amount", 6.0), (s, t) -> verify(s).damage(t, 6.0)),
                entity("CURE → cure", new CureEffect(),
                        c -> { }, (s, t) -> verify(s).cure(t)),
                entity("DISARM → disarm", new DisarmEffect(),
                        c -> { }, (s, t) -> verify(s).disarm(t)),
                entity("EXTINGUISH → extinguish", new ExtinguishEffect(),
                        c -> { }, (s, t) -> verify(s).extinguish(t)),
                entity("FILL_OXYGEN → fillAir", new FillOxygenEffect(),
                        c -> { }, (s, t) -> verify(s).fillAir(t)),
                entity("KILL → kill", new KillEffect(),
                        c -> { }, (s, t) -> verify(s).kill(t)),
                entity("REMOVE_ARMOR → removeArmor", new RemoveArmorEffect(),
                        c -> { }, (s, t) -> verify(s).removeArmor(t)),
                entity("IGNITE → ignite(duration)", new IgniteEffect(),
                        c -> c.with("duration", 60), (s, t) -> verify(s).ignite(t, 60)),
                entity("INVINCIBLE → invincible(ticks)", new InvincibleEffect(),
                        c -> c.with("ticks", 100), (s, t) -> verify(s).invincible(t, 100)),
                entity("LIGHTNING → lightningAndDamage(damage)", new LightningEffect(),
                        c -> c.with("damage", 6.0), (s, t) -> verify(s).lightningAndDamage(t, 6.0)),
                entity("HEALTH → addMaxHealth(amount)", new HealthEffect(),
                        c -> c.with("amount", 4.0), (s, t) -> verify(s).addMaxHealth(t, 4.0)),
                entity("KNOCKBACK_CONTROL → controlKnockback(multiplier, duration)", new KnockbackControlEffect(),
                        c -> c.with("multiplier", 0.0).with("duration", 2),
                        (s, t) -> verify(s).controlKnockback(t, 0.0, 2)),
                entity("REMOVE_POTION → removePotion(effect)", new RemovePotionEffect(),
                        c -> c.with("effect", 5), (s, t) -> verify(s).removePotion(t, 5)),
                // §C: the authored 1-based level reaches the Sink as the 0-based Bukkit amplifier (level − 1).
                entity("POTION → potion(effect, level−1, duration)", new PotionEffect(),
                        c -> c.with("effect", 7).with("level", 2).with("duration", 100),
                        (s, t) -> verify(s).potion(t, 7, 1, 100)));
    }

    @TestFactory
    List<DynamicTest> playerTargetIntents() {
        return List.of(
                players("FLY → setFlight(ticks)", new FlyEffect(),
                        c -> c.with("ticks", 200), (s, p) -> verify(s).setFlight(p, 200)),
                playerOnly("KEEP_ON_DEATH → keepOnDeath(duration)", new KeepOnDeathEffect(),
                        c -> c.with("duration", 200), (s, p) -> verify(s).keepOnDeath(p, 200)),
                playerOnly("TELEBLOCK → teleblock(duration)", new TeleblockEffect(),
                        c -> c.with("duration", 400), (s, p) -> verify(s).teleblock(p, 400)),
                playerOnly("MOVEMENT_SPEED → movementSpeed(speed, ticks)", new MovementSpeedEffect(),
                        c -> c.with("speed", 0.4).with("ticks", 200),
                        (s, p) -> verify(s).movementSpeed(p, 0.4, 200)),
                playerOnly("GIVE_ITEM → giveItem(material, count)", new GiveItemEffect(),
                        c -> c.with("material", 4).with("count", 2), (s, p) -> verify(s).giveItem(p, 4, 2)),
                playerOnly("REMOVE_ITEM → removeItem(material, count)", new RemoveItemEffect(),
                        c -> c.with("material", 9).with("count", 5), (s, p) -> verify(s).removeItem(p, 9, 5)),
                playerOnly("SET_VAR → setVar(name, value, ttl)", new SetVarEffect(),
                        c -> c.with("name", "rage").with("value", "1").with("ttl", 200),
                        (s, p) -> verify(s).setVar(p, "rage", "1", 200)),
                playerOnly("INVERT_VAR → invertVar(name)", new InvertVarEffect(),
                        c -> c.with("name", "flag"), (s, p) -> verify(s).invertVar(p, "flag")),
                playerOnly("SUPPRESS → suppress(scope, key, duration)", new SuppressEffect(),
                        c -> c.with("scope", 1).with("key", 7).with("duration", 200),
                        (s, p) -> verify(s).suppress(p, 1, 7, 200)));
    }

    /** POTION's §B/ADR-0022 lifecycle teardown: on unequip, {@code stop} emits the exact inverse and nothing else. */
    @TestFactory
    List<DynamicTest> lifecycleTeardown() {
        return List.of(
                dynamicTest("POTION.stop → removePotion(effect) per target", () -> {
                    LivingEntity a = mock(LivingEntity.class);
                    LivingEntity b = mock(LivingEntity.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create().with("effect", 7).targets("who", a, b);
                    Sink sink = mock(Sink.class);
                    new PotionEffect().stop(ctx, sink);
                    verify(sink).removePotion(a, 7);
                    verify(sink).removePotion(b, 7);
                    verifyNoMoreInteractions(sink);
                }));
    }
}
