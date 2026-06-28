package engine.effect.kind;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import engine.effect.EffectKind;
import engine.sink.Sink;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
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
                flag("SMELT → smelt", new SmeltEffect(), c -> { }, s -> verify(s).smelt()),
                flag("TELEPORT_DROPS → teleportDrops", new TeleportDropsEffect(), c -> { },
                        s -> verify(s).teleportDrops()),
                flag("SEEK → seek", new SeekEffect(), c -> { }, s -> verify(s).seek()),
                flag("RUN_COMMAND → consoleCommand(command)", new RunCommandEffect(),
                        c -> c.with("command", "say hi"), s -> verify(s).consoleCommand("say hi")));
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
