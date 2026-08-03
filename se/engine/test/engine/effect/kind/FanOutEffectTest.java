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
                entity("DAMAGE → damage (flat amount only, percent-of-max 0)", new DamageEffect(),
                        c -> c.with("amount", 6.0).with("percent-of-max", 0.0),
                        (s, t) -> verify(s).damage(t, 6.0, null)),
                entity("DAMAGE → damagePercentOfMax (percent-of-max only, amount 0)", new DamageEffect(),
                        c -> c.with("amount", 0.0).with("percent-of-max", 10.0),
                        (s, t) -> verify(s).damagePercentOfMax(t, 10.0, null)),
                entity("CURE (default ALL, unlimited) → cureByCategory(0, 0)", new CureEffect(),
                        c -> c.with("category", "ALL").with("count", 0),
                        (s, t) -> verify(s).cureByCategory(t, 0, 0)),
                entity("CURE category HARMFUL → cureByCategory(1, 0)", new CureEffect(),
                        c -> c.with("category", "HARMFUL").with("count", 0),
                        (s, t) -> verify(s).cureByCategory(t, 1, 0)),
                entity("CURE count 1 → cureByCategory carries the bound (single-debuff cleanse)", new CureEffect(),
                        c -> c.with("category", "HARMFUL").with("count", 1),
                        (s, t) -> verify(s).cureByCategory(t, 1, 1)),
                entity("DISARM → disarm", new DisarmEffect(),
                        c -> { }, (s, t) -> verify(s).disarm(t)),
                entity("EXTINGUISH → extinguish", new ExtinguishEffect(),
                        c -> { }, (s, t) -> verify(s).extinguish(t)),
                entity("FILL_OXYGEN (no amount) → fillAir(0) — the outright refill", new FillOxygenEffect(),
                        c -> c.with("amount", 0), (s, t) -> verify(s).fillAir(t, 0)),
                entity("FILL_OXYGEN amount → fillAir carries the incremental air ticks", new FillOxygenEffect(),
                        c -> c.with("amount", 60), (s, t) -> verify(s).fillAir(t, 60)),
                entity("KILL → kill", new KillEffect(),
                        c -> { }, (s, t) -> verify(s).kill(t)),
                entity("REMOVE_ARMOR → removeArmor", new RemoveArmorEffect(),
                        c -> { }, (s, t) -> verify(s).removeArmor(t)),
                entity("IGNITE → ignite(duration)", new IgniteEffect(),
                        c -> c.with("duration", 60), (s, t) -> verify(s).ignite(t, 60)),
                entity("INVINCIBLE → invincible(ticks)", new InvincibleEffect(),
                        c -> c.with("ticks", 100), (s, t) -> verify(s).invincible(t, 100)),
                entity("LIGHTNING → lightningAndDamage(damage)", new LightningEffect(),
                        c -> c.with("damage", 6.0), (s, t) -> verify(s).lightningAndDamage(t, 6.0, null)),
                entity("HEALTH → addMaxHealth(amount)", new HealthEffect(),
                        c -> c.with("amount", 4.0), (s, t) -> verify(s).addMaxHealth(t, 4.0)),
                entity("KNOCKBACK_CONTROL → controlKnockback(multiplier, duration)", new KnockbackControlEffect(),
                        c -> c.with("multiplier", 0.0).with("duration", 2),
                        (s, t) -> verify(s).controlKnockback(t, 0.0, 2)),
                entity("REMOVE_POTION → removePotion(effect)", new RemovePotionEffect(),
                        c -> c.with("effect", 5), (s, t) -> verify(s).removePotion(t, 5)),
                entity("POTION_LOCK → potionLock(effect, ticks)", new PotionLockEffect(),
                        c -> c.with("effect", 5).with("ticks", 100), (s, t) -> verify(s).potionLock(t, 5, 100)),
                // ADR-0065: distinct non-default args pin the param→intent wiring (a transposition fails).
                entity("FREEZE → freeze(duration, dot, dot-period, slow, neutralize, attribution)",
                        new FreezeEffect(),
                        c -> c.with("duration", 80).with("dot", 3.0).with("dot-period", 30)
                                .with("slow", 7.0).with("neutralize-frost-slow", false)
                                .with("breakout-chance", 0.0),
                        (s, t) -> verify(s).freeze(t, 80, 3.0, 30, 7.0, false, 0.0, null)),
                entity("FREEZE breakout-chance rides to the window (a root the victim can struggle out of)",
                        new FreezeEffect(),
                        c -> c.with("duration", 80).with("dot", 3.0).with("dot-period", 30)
                                .with("slow", 7.0).with("neutralize-frost-slow", false)
                                .with("breakout-chance", 22.5),
                        (s, t) -> verify(s).freeze(t, 80, 3.0, 30, 7.0, false, 22.5, null)),
                // §C: the authored 1-based level reaches the Sink as the 0-based Bukkit amplifier (level − 1).
                entity("POTION → potion(effect, level−1, duration)", new PotionEffect(),
                        c -> c.with("effect", 7).with("level", 2).with("duration", 100),
                        (s, t) -> verify(s).potion(t, 7, 1, 100)),
                // ADR-0054: DAMAGE/LIGHTNING attribute the activating player, so a deferred application
                // (a WAIT DoT tick, a bystander target) fires an attributed event downstream.
                dynamicTest("DAMAGE/LIGHTNING carry the activator as the attribution attacker", () -> {
                    Player actor = mock(Player.class);
                    LivingEntity a = mock(LivingEntity.class);
                    LivingEntity b = mock(LivingEntity.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create().actor(actor)
                            .with("amount", 6.0).with("percent-of-max", 10.0).targets("who", a, b);
                    Sink sink = mock(Sink.class);
                    new DamageEffect().run(ctx, sink);
                    verify(sink).damage(a, 6.0, actor);
                    verify(sink).damagePercentOfMax(a, 10.0, actor);
                    verify(sink).damage(b, 6.0, actor);
                    verify(sink).damagePercentOfMax(b, 10.0, actor);
                    verifyNoMoreInteractions(sink);
                    FakeEffectCtx bolt = FakeEffectCtx.create().actor(actor).with("damage", 5.0).targets("who", a);
                    Sink boltSink = mock(Sink.class);
                    new LightningEffect().run(bolt, boltSink);
                    verify(boltSink).lightningAndDamage(a, 5.0, actor);
                    verifyNoMoreInteractions(boltSink);
                }),
                // ADR-0065: FREEZE attributes the activator to every DoT tick — two targets catch a broken fan-out.
                dynamicTest("FREEZE carries the activator as the DoT attribution attacker", () -> {
                    Player actor = mock(Player.class);
                    LivingEntity a = mock(LivingEntity.class);
                    LivingEntity b = mock(LivingEntity.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create().actor(actor)
                            .with("duration", 80).with("dot", 3.0).with("dot-period", 30)
                            .with("slow", 7.0).with("neutralize-frost-slow", false)
                            .with("breakout-chance", 0.0).targets("who", a, b);
                    Sink sink = mock(Sink.class);
                    new FreezeEffect().run(ctx, sink);
                    verify(sink).freeze(a, 80, 3.0, 30, 7.0, false, 0.0, actor);
                    verify(sink).freeze(b, 80, 3.0, 30, 7.0, false, 0.0, actor);
                    verifyNoMoreInteractions(sink);
                }));
    }

    /** TARGET_VAR: a var carrier is any living entity, so both modes fan out like any entity intent. */
    @TestFactory
    List<DynamicTest> entityVarIntents() {
        return List.of(
                entity("SET_VAR op=set → setVarOn(name, value, ttl)", new SetVarEffect(),
                        c -> c.with("name", "rage").with("value", "1").with("ttl", 200)
                                .with("op", "set").with("step", 1).with("cap", 0),
                        (s, t) -> verify(s).setVarOn(t, "rage", "1", 200)),
                entity("SET_VAR op=increment → incrementVar(name, step, cap, ttl)", new SetVarEffect(),
                        c -> c.with("name", "bleedstacks").with("value", "").with("ttl", 200)
                                .with("op", "increment").with("step", 2).with("cap", 20),
                        (s, t) -> verify(s).incrementVar(t, "bleedstacks", 2, 20, 200)));
    }

    @TestFactory
    List<DynamicTest> playerTargetIntents() {
        return List.of(
                players("FLY → setFlight(ticks) per player", new FlyEffect(),
                        c -> c.with("ticks", 200).with("speed", 0.0),
                        (s, p) -> verify(s).setFlight(p, 200, 0.0)),
                playerOnly("FLY → skips a non-player target (no flight for mobs)", new FlyEffect(),
                        c -> c.with("ticks", 200).with("speed", 0.0),
                        (s, p) -> verify(s).setFlight(p, 200, 0.0)),
                players("FLY speed rides the same window as the flight", new FlyEffect(),
                        c -> c.with("ticks", 60).with("speed", 0.4),
                        (s, p) -> verify(s).setFlight(p, 60, 0.4)),
                playerOnly("KEEP_ON_DEATH → keepOnDeath(duration)", new KeepOnDeathEffect(),
                        c -> c.with("duration", 200), (s, p) -> verify(s).keepOnDeath(p, 200)),
                // Wave 1d.2: distinct non-default args pin the param→intent wiring (a transposition fails).
                playerOnly("OUTGOING_DEBUFF → outgoingDebuff(percent, duration, causeMask, feedback)",
                        new OutgoingDebuffEffect(),
                        c -> c.with("percent", 50.0).with("duration", 80).with("cause", "projectile")
                                .with("feedback", "unfocused"),
                        (s, p) -> verify(s).outgoingDebuff(p, 50.0, 80,
                                engine.stores.OutgoingDebuffStore.CAUSE_PROJECTILE, "unfocused")),
                playerOnly("DOT_AMPLIFY_MARK → dotAmplify(factor, causeMask, duration)",
                        new DotAmplifyMarkEffect(),
                        c -> c.with("causes", "wither").with("factor", 3.0).with("duration", 60),
                        (s, p) -> verify(s).dotAmplify(p, 3.0,
                                engine.stores.DotAmplifyStore.CAUSE_WITHER, 60)),
                playerOnly("HEAD_TROPHY → armHeadTrophy carries the templates unresolved", new HeadTrophyEffect(),
                        c -> c.with("name", "Skull of {VICTIM}").with("lore", "one|two"),
                        (s, p) -> verify(s).armHeadTrophy(p, "Skull of {VICTIM}", "one|two")),
                playerOnly("TELEBLOCK → teleblock(duration)", new TeleblockEffect(),
                        c -> c.with("duration", 400), (s, p) -> verify(s).teleblock(p, 400)),
                playerOnly("MOVEMENT_SPEED → movementSpeed(speed, ticks)", new MovementSpeedEffect(),
                        c -> c.with("speed", 0.4).with("ticks", 200),
                        (s, p) -> verify(s).movementSpeed(p, 0.4, 200)),
                playerOnly("GIVE_ITEM → giveItem(material, count)", new GiveItemEffect(),
                        c -> c.with("material", 4).with("count", 2), (s, p) -> verify(s).giveItem(p, 4, 2)),
                playerOnly("REMOVE_ITEM → removeItem(material, count)", new RemoveItemEffect(),
                        c -> c.with("material", 9).with("count", 5), (s, p) -> verify(s).removeItem(p, 9, 5)),
                playerOnly("INVERT_VAR → invertVar(name)", new InvertVarEffect(),
                        c -> c.with("name", "flag"), (s, p) -> verify(s).invertVar(p, "flag")),
                playerOnly("SUPPRESS timed → suppress(scope, key, duration, sourceDefId, nextHit=false, charges)",
                        new SuppressEffect(),
                        c -> c.with("scope", 1).with("key", 7).with("duration", 200).with("mode", 0).with("charges", 1)
                                .with("consumed-message-actor", "").with("consumed-message-victim", "")
                                .sourceDefId(88),
                        (s, p) -> verify(s).suppress(p, 1, 7, 200, 88, false, 1, null, "", "", -1)),
                playerOnly("SUPPRESS next-hit → suppress(..., nextHit=true, charges) — Neutralize one-shot",
                        new SuppressEffect(),
                        c -> c.with("scope", 1).with("key", 7).with("duration", 200).with("mode", 1).with("charges", 2)
                                .with("consumed-message-actor", "").with("consumed-message-victim", "")
                                .sourceDefId(88),
                        (s, p) -> verify(s).suppress(p, 1, 7, 200, 88, true, 2, null, "", "", -1)),
                playerOnly("SUPPRESS carries its consume-time lines to the window",
                        new SuppressEffect(),
                        c -> c.with("scope", 1).with("key", 7).with("duration", 200).with("mode", 0).with("charges", 1)
                                .with("consumed-message-actor", "blocked them")
                                .with("consumed-message-victim", "you are silenced")
                                .sourceDefId(88),
                        (s, p) -> verify(s).suppress(p, 1, 7, 200, 88, false, 1, null,
                                "blocked them", "you are silenced", -1)),
                playerOnly("REFLECT → reflectMark(percent, duration) — Hex, player-only", new ReflectEffect(),
                        c -> c.with("percent", 20.0).with("duration", 80).with("cap", 0.0).with("feedback", ""),
                        (s, p) -> verify(s).reflectMark(p, 20.0, 0.0, "", 80)),
                playerOnly("REFLECT carries its cap + feedback template to the window", new ReflectEffect(),
                        c -> c.with("percent", 100.0).with("duration", 40).with("cap", 6.0)
                                .with("feedback", "reflected {damage}"),
                        (s, p) -> verify(s).reflectMark(p, 100.0, 6.0, "reflected {damage}", 40)),
                playerOnly("WEAKEN → weaken(percent, duration) — Destruction, player-only", new WeakenEffect(),
                        c -> c.with("percent", 15.0).with("duration", 100),
                        (s, p) -> verify(s).weaken(p, 15.0, 100)),
                playerOnly("DAMAGE_CAP with feedback → armDamageCap carries the arming line", new DamageCapEffect(),
                        c -> c.with("factor", 0.5).with("reflect", false).with("duration", 100)
                                .with("feedback", "capped at {damage}"),
                        (s, p) -> verify(s).armDamageCap(p, 0.5, false, 100, "capped at {damage}")),
                playerOnly("DAMAGE_CAP → armDamageCap(factor, reflect, duration) — Diminish, player-only",
                        new DamageCapEffect(),
                        c -> c.with("factor", 0.5).with("reflect", true).with("duration", 100)
                                .with("feedback", ""),
                        (s, p) -> verify(s).armDamageCap(p, 0.5, true, 100, "")));
    }

    /** The wave-1d.2 kinds whose fan-out shape is neither plain-entity nor plain-player. */
    @TestFactory
    List<DynamicTest> waveOneD2Intents() {
        return List.of(
                dynamicTest("PERIODIC_DAMAGE carries the activator + the replaced DoTs to every target", () -> {
                    Player actor = mock(Player.class);
                    LivingEntity a = mock(LivingEntity.class);
                    LivingEntity b = mock(LivingEntity.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create().actor(actor)
                            .with("amount", 6.0).with("period", 20).with("duration", 120)
                            .with("replace", List.of(9)).with("feedback", "burning")
                            .targets("who", a, b);
                    Sink sink = mock(Sink.class);
                    new PeriodicDamageEffect().run(ctx, sink);
                    verify(sink).periodicDamage(a, 6.0, 20, 120, List.of(9), "burning", actor);
                    verify(sink).periodicDamage(b, 6.0, 20, 120, List.of(9), "burning", actor);
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("DESPAWN removes mobs and NEVER a player in the same target list", () -> {
                    LivingEntity mob = mock(LivingEntity.class);
                    LivingEntity other = mock(LivingEntity.class);
                    Player player = mock(Player.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create().targets("who", mob, player, other);
                    Sink sink = mock(Sink.class);
                    new DespawnEffect().run(ctx, sink);
                    verify(sink).despawn(mob);
                    verify(sink).despawn(other);
                    verifyNoMoreInteractions(sink); // the player contributes no intent — an AoE clear spares them
                }),
                dynamicTest("SUMMON_REBIND carries the activator as the ownership gate", () -> {
                    Player actor = mock(Player.class);
                    LivingEntity a = mock(LivingEntity.class);
                    LivingEntity b = mock(LivingEntity.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create().actor(actor)
                            .with("type", 42).with("ttl", 600).with("name", "&bGuardian")
                            .with("health", 90.0).with("speed", 1.2).with("effects", List.of(7))
                            .with("rise", 2.0).targets("who", a, b);
                    Sink sink = mock(Sink.class);
                    new SummonRebindEffect().run(ctx, sink);
                    verify(sink).rebindSummon(a, actor, 42, 600, "&bGuardian", 90.0, 1.2, List.of(7), 2.0);
                    verify(sink).rebindSummon(b, actor, 42, 600, "&bGuardian", 90.0, 1.2, List.of(7), 2.0);
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("SUMMON_REBIND with no activator emits nothing (ownership is the whole gate)", () -> {
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .with("type", 42).with("ttl", 600).with("name", "").with("health", 0.0)
                            .with("speed", 0.0).with("effects", List.of()).with("rise", 2.0)
                            .targets("who", mock(LivingEntity.class));
                    Sink sink = mock(Sink.class);
                    new SummonRebindEffect().run(ctx, sink);
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("VIEWER_HIDE viewer=attacker scopes the hide to that one attacker", () -> {
                    Player attacker = mock(Player.class);
                    Player subject = mock(Player.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create().attacker(attacker)
                            .with("duration", 60).with("viewer", "attacker").targets("who", subject);
                    Sink sink = mock(Sink.class);
                    new ViewerHideEffect().run(ctx, sink);
                    verify(sink).viewerHide(subject, attacker, 60);
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("VIEWER_HIDE viewer=all passes a null viewer (everyone), attacker or not", () -> {
                    Player subject = mock(Player.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .with("duration", 60).with("viewer", "all").targets("who", subject);
                    Sink sink = mock(Sink.class);
                    new ViewerHideEffect().run(ctx, sink);
                    verify(sink).viewerHide(subject, null, 60);
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("VIEWER_HIDE viewer=attacker with no attacker hides nobody (never falls back to all)",
                        () -> {
                            Player subject = mock(Player.class);
                            FakeEffectCtx ctx = FakeEffectCtx.create()
                                    .with("duration", 60).with("viewer", "attacker").targets("who", subject);
                            Sink sink = mock(Sink.class);
                            new ViewerHideEffect().run(ctx, sink);
                            verifyNoMoreInteractions(sink);
                        }),
                dynamicTest("PROJECTILE_DRESSING → dressProjectile(type, ttl, invulnerable, no-pickup)", () -> {
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .with("type", 42).with("ttl", 150).with("invulnerable", 40).with("no-pickup", true);
                    Sink sink = mock(Sink.class);
                    new ProjectileDressingEffect().run(ctx, sink);
                    verify(sink).dressProjectile(42, 150, 40, true);
                    verifyNoMoreInteractions(sink);
                }));
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
