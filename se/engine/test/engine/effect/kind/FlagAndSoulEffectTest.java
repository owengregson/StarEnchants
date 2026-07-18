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
import java.util.function.Consumer;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import testfx.FakeEffectCtx;

/**
 * The no-target "inline read-back" flag kinds (CANCEL / IGNORE_ARMOR / SMELT / TELEPORT_DROPS / SEEK), the
 * console-command kind, and the soul-debit kind, collapsed from one file per kind (and from the old
 * ExoticEffectKindsTest, whose RemoveArmor/Teleblock/Immune rows moved to the Fan-out and Mode-dispatch
 * tables). The flag kinds each emit exactly one intent; REMOVE_SOULS guards the dupe risk — exactly one debit,
 * against the gem the target owns, and none at all out of soul mode or on a non-positive amount.
 */
class FlagAndSoulEffectTest {

    /** A no-target kind that emits exactly one intent. */
    private static DynamicTest flag(String label, EffectKind kind, Consumer<FakeEffectCtx> args,
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

    @TestFactory
    List<DynamicTest> flagAndCommandIntents() {
        return List.of(
                flag("CANCEL → cancelEvent", new CancelEffect(), c -> { }, s -> verify(s).cancelEvent()),
                flag("IGNORE_ARMOR → ignoreArmor", new IgnoreArmorEffect(), c -> { }, s -> verify(s).ignoreArmor()),
                flag("IGNORE_HEROIC → ignoreHeroic (ADR-0053)", new IgnoreHeroicEffect(), c -> { },
                        s -> verify(s).ignoreHeroic()),
                flag("SMELT → smelt", new SmeltEffect(), c -> { }, s -> verify(s).smelt()),
                flag("TELEPORT_DROPS → teleportDrops", new TeleportDropsEffect(), c -> { },
                        s -> verify(s).teleportDrops()),
                flag("SEEK → seek", new SeekEffect(), c -> { }, s -> verify(s).seek()),
                flag("ECHO_STRIKE → requestEchoStrike", new EchoStrikeEffect(), c -> { },
                        s -> verify(s).requestEchoStrike()),
                // 'as' is a compiler-defaulted param (console); FakeEffectCtx synthesizes no defaults, so feed it.
                flag("RUN_COMMAND → consoleCommand(command)", new RunCommandEffect(),
                        c -> c.with("command", "say hi").with("as", "console"),
                        s -> verify(s).consoleCommand("say hi")),
                // ADR-0061: DIG_HOME is a service-owned marker — the run MUST emit nothing (PetService arms
                // the home window from the compiled args; an engine intent here would double-arm it).
                flag("DIG_HOME → no intent (service-owned marker)", new DigHomeEffect(),
                        c -> c.with("window", 600).with("range", 50.0),
                        s -> { }),
                // ADR-0063: LIGHTNING_MOD is a worn channel — LightningBoost reads it at bolt emit; an
                // engine intent here would double-apply the boost.
                flag("LIGHTNING_MOD → no intent (worn channel)", new LightningModEffect(),
                        c -> c.with("amount", 10.0),
                        s -> { }),
                // ADR-0071 reforge markers (Plan B): the run MUST emit nothing — the reforge service arms each
                // machine from the compiled args at the activation success point; an engine intent would double-arm.
                flag("GRAVITY_WELL → no intent (service-owned marker)", new GravityWellEffect(), c -> { }, s -> { }),
                flag("GRAPPLE → no intent (service-owned marker)", new GrappleEffect(), c -> { }, s -> { }),
                flag("SWAP_POSITION → no intent (service-owned marker)", new SwapPositionEffect(), c -> { }, s -> { }),
                flag("JAVELIN → no intent (service-owned marker)", new JavelinEffect(), c -> { }, s -> { }));
    }

    /** An armed/combat-state kind targeting @Self: it emits exactly one intent per Player target. */
    private static DynamicTest armed(String label, EffectKind kind, Consumer<FakeEffectCtx> args,
            java.util.function.BiConsumer<Sink, Player> verify) {
        return dynamicTest(label, () -> {
            Player player = mock(Player.class);
            FakeEffectCtx ctx = FakeEffectCtx.create().targets("who", player);
            args.accept(ctx);
            Sink sink = mock(Sink.class);
            kind.run(ctx, sink);
            verify.accept(sink, player);
            verifyNoMoreInteractions(sink);
        });
    }

    @TestFactory
    List<DynamicTest> reforgeCombatStateArms() {
        return List.of(
                // ADR-0071 reforge combat-state kinds (Plan C): each arms exactly one Sink intent for a
                // Player target (distinct non-default values catch a transposition); a non-player target
                // arms nothing (the Player filter).
                armed("HIT_TEMPO → hitTempo(player, 77, MENTAL=0, 0.25, 0.5)", new HitTempoEffect(),
                        c -> c.with("duration", 77).with("model", "MENTAL")
                                .with("damage-percent", 25.0).with("attack-speed", 0.5),
                        (s, p) -> verify(s).hitTempo(p, 77, 0, 0.25, 0.5)),
                armed("BATTERY → armBattery(player, 0.15, 4)", new BatteryEffect(),
                        c -> c.with("bank-percent", 15.0).with("hits", 4),
                        (s, p) -> verify(s).armBattery(p, 0.15, 4)),
                armed("DISARM_SHUFFLE → armDisarmShuffle(player, 66, 0.35)", new DisarmShuffleEffect(),
                        c -> c.with("duration", 66).with("damage-malus", 35.0),
                        (s, p) -> verify(s).armDisarmShuffle(p, 66, 0.35)),
                armed("CONVERT_SUMMON → convertSummons(player, 9.5)", new ConvertSummonEffect(),
                        c -> c.with("radius", 9.5),
                        (s, p) -> verify(s).convertSummons(p, 9.5)),
                armed("TRAP_BREAK → breakTraps(player)", new TrapBreakEffect(),
                        c -> { },
                        (s, p) -> verify(s).breakTraps(p)),
                dynamicTest("HIT_TEMPO on a non-player target → no intent (the Player filter)", () -> {
                    LivingEntity cow = mock(LivingEntity.class); // not a Player
                    FakeEffectCtx ctx = FakeEffectCtx.create().targets("who", cow)
                            .with("duration", 100).with("model", "MENTAL")
                            .with("damage-percent", 33.3).with("attack-speed", 1.0);
                    Sink sink = mock(Sink.class);
                    new HitTempoEffect().run(ctx, sink);
                    verifyNoInteractions(sink);
                }));
    }

    @TestFactory
    List<DynamicTest> runCommandAsAndTokens() {
        return List.of(
                dynamicTest("RUN_COMMAND as:player → performs as the actor's entity", () -> {
                    Player actor = mock(Player.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create().actor(actor)
                            .with("command", "spawn").with("as", "player");
                    Sink sink = mock(Sink.class);
                    new RunCommandEffect().run(ctx, sink);
                    verify(sink).playerCommand(actor, "spawn"); // player path, not console
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("RUN_COMMAND fills {PLAYER}/{UUID}/{WORLD} from the actor", () -> {
                    UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000ab");
                    World world = mock(World.class);
                    when(world.getName()).thenReturn("nether");
                    Player actor = mock(Player.class);
                    when(actor.getName()).thenReturn("Steve");
                    when(actor.getUniqueId()).thenReturn(id);
                    when(actor.getWorld()).thenReturn(world);
                    FakeEffectCtx ctx = FakeEffectCtx.create().actor(actor)
                            .with("command", "tp {PLAYER} in {WORLD} [{UUID}]").with("as", "console");
                    Sink sink = mock(Sink.class);
                    new RunCommandEffect().run(ctx, sink);
                    verify(sink).consoleCommand("tp Steve in nether [" + id + "]"); // default as:console
                    verifyNoMoreInteractions(sink);
                }),
                // F34: a {PLAYER}-bearing template refuses to dispatch when the actor's name is non-standard —
                // never perturb a console command with a crafted offline-mode username. Refuse, don't strip.
                dynamicTest("RUN_COMMAND refuses a {PLAYER} template with a non-standard name (console + player)", () -> {
                    Player actor = mock(Player.class);
                    when(actor.getName()).thenReturn("a b\" ; op x");
                    for (String as : new String[] {"console", "player"}) {
                        FakeEffectCtx ctx = FakeEffectCtx.create().actor(actor)
                                .with("command", "eco give {PLAYER} 100").with("as", as);
                        Sink sink = mock(Sink.class);
                        new RunCommandEffect().run(ctx, sink);
                        verifyNoInteractions(sink); // neither the console nor the player arm dispatches
                    }
                }),
                dynamicTest("RUN_COMMAND with no token dispatches even for a hostile name (short-circuit stays actor-free)", () -> {
                    Player actor = mock(Player.class);
                    when(actor.getName()).thenReturn("bad name!");
                    FakeEffectCtx ctx = FakeEffectCtx.create().actor(actor)
                            .with("command", "save-all").with("as", "console");
                    Sink sink = mock(Sink.class);
                    new RunCommandEffect().run(ctx, sink);
                    verify(sink).consoleCommand("save-all"); // no '{' → never reads the name
                    verifyNoMoreInteractions(sink);
                }));
    }

    @TestFactory
    List<DynamicTest> runCommandSafeName() {
        return List.of(
                dynamicTest("safeName accepts the Mojang username charset (1-16, [A-Za-z0-9_])", () -> {
                    for (String ok : new String[] {"a", "Steve", "Notch_99", "ABCDEFGHIJKLMNOP" /* 16 */}) {
                        org.junit.jupiter.api.Assertions.assertTrue(RunCommandEffect.safeName(ok), ok);
                    }
                }),
                dynamicTest("safeName rejects empty, over-length, and out-of-charset names", () -> {
                    for (String bad : new String[] {"", "ABCDEFGHIJKLMNOPQ" /* 17 */, "a b", "Notch\"", "na-me", "über"}) {
                        org.junit.jupiter.api.Assertions.assertFalse(RunCommandEffect.safeName(bad), bad);
                    }
                }));
    }

    @TestFactory
    List<DynamicTest> removeSouls() {
        return List.of(
                dynamicTest("REMOVE_SOULS @Self → debit the activator's active gem (exactly once)", () -> {
                    UUID gemId = UUID.randomUUID();
                    Player holder = mock(Player.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .activeGem(gemId).actor(holder).with("amount", 5).targets("who", holder);
                    Sink sink = mock(Sink.class);
                    new RemoveSoulsEffect().run(ctx, sink);
                    verify(sink).removeSouls(holder, gemId, 5);
                    verifyNoMoreInteractions(sink); // never two debits
                }),
                dynamicTest("REMOVE_SOULS @Victim → drain the victim's OWN gem", () -> {
                    Player holder = mock(Player.class);
                    Player victim = mock(Player.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create()
                            .actor(holder).with("amount", 300).targets("who", victim);
                    Sink sink = mock(Sink.class);
                    new RemoveSoulsEffect().run(ctx, sink);
                    verify(sink).removeSoulsFrom(victim, 300); // the victim's gem, not the activator's
                    verifyNoMoreInteractions(sink);
                }),
                dynamicTest("REMOVE_SOULS not in soul mode → no-op", () -> {
                    Player holder = mock(Player.class);
                    FakeEffectCtx ctx = FakeEffectCtx.create() // activeGem null → not in soul mode
                            .actor(holder).with("amount", 5).targets("who", holder);
                    Sink sink = mock(Sink.class);
                    new RemoveSoulsEffect().run(ctx, sink);
                    verifyNoInteractions(sink);
                }),
                dynamicTest("REMOVE_SOULS non-positive amount → no-op", () -> {
                    FakeEffectCtx ctx = FakeEffectCtx.create().actor(mock(Player.class)).with("amount", 0);
                    Sink sink = mock(Sink.class);
                    new RemoveSoulsEffect().run(ctx, sink);
                    verifyNoInteractions(sink);
                }));
    }
}
