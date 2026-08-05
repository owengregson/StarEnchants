package engine.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.Compiler;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.model.Ability;
import compile.model.Snapshot;
import engine.effect.kind.BuiltinEffects;
import engine.interact.SoulSpender;
import engine.pipeline.Activation;
import engine.pipeline.ActivationPipeline;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.run.AreaScan;
import engine.selector.kind.BuiltinSelectors;
import engine.sink.ModernDispatchSink;
import engine.stores.CooldownStore;
import engine.stores.EngineStores;
import engine.stores.SuppressionStore;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import testfx.Envs;
import testfx.PermissiveResolvers;
import testfx.SyncSchedulerBackend;

/**
 * R-QC3 (ADR-0075) end to end: a {@code SUPPRESS { scope: TYPE, key: DEFENSE }} window authored in YAML must
 * actually park a victim's DEFENSE procs. Only a loader→compiler→gate walk can prove it — the window and the
 * abilities it silences meet at ONE interned id, and the whole reason the 26 shipped TYPE windows were dead is
 * that the two ends of that bridge were compiled by different code with nothing joining them: the SUPPRESS key
 * interned a name no ability carried, and every layer's own test passed.
 *
 * <p>The negative half matters as much: the eight maintained-passive enchants that carry
 * {@code suppress-immune: true} justify it on "silence never dropped worn passives", and that only holds
 * because a PASSIVE ability belongs to no combat direction and so is stamped with no type at all.
 */
class TypeSuppressionEndToEndTest {

    private static final Compiler COMPILER = ContentCompiler.production(PermissiveResolvers.INSTANCE);
    private static final TriggerRegistry TRIGGERS = BuiltinTriggers.registry();

    private final UUID attackerId = UUID.randomUUID();
    private final UUID victimId = UUID.randomUUID();

    @TempDir
    Path root;

    private RuntimeHandles handles;
    private EngineStores stores;
    private AbilityExecutor executor;

    @BeforeEach
    void setUp() {
        handles = new RuntimeHandles(new RegistryResolvers());
        Scheduling.install(new SyncSchedulerBackend());
        stores = EngineStores.fresh();
        executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE, stores.suppression(),
                        ActivationPipeline.Guard.ALLOW, ActivationPipeline.Guard.ALLOW),
                AreaScan.NONE);
    }

    @Test
    void aTypeDefenseWindowParksTheVictimsDefenceProcsAndLeavesTheirPassivesAlone() throws Exception {
        Snapshot snap = library();

        assertEquals(1, run(snap, "enchants/silence/1", "ATTACK", attacker(), victim()),
                "the silence proc itself walks");
        assertTrue(stores.suppression().isSuppressed(victimId,
                        CooldownStore.key(compile.model.ScopeKinds.TYPE,
                                snap.interners().cooldownScopes().idOf("DEFENSE")), 0L),
                "the window is keyed on the same interned TYPE id an ability carries");

        assertEquals(0, run(snap, "enchants/thorns/1", "DEFENSE", victim(), attacker()),
                "the victim's DEFENSE proc is parked — the whole point of Silence");
        assertEquals(0, run(snap, "enchants/scorch/1", "HURT", victim(), attacker()),
                "and so is HURT, the all-cause half of the same defender side");
        assertEquals(1, run(snap, "enchants/springs/1", "PASSIVE", victim(), null),
                "a worn passive belongs to no combat side, so nothing types it and the window misses it");
        assertEquals(1, run(snap, "enchants/lifesteal/1", "ATTACK", victim(), attacker()),
                "and their own ATTACK side is untouched: DEFENSE names one direction, not the player");
    }

    @Test
    void anAuthoredSuppressTypeReplacesTheImpliedOne() throws Exception {
        Snapshot snap = library();

        Ability thorns = snap.byStableKey("enchants/thorns/1");
        Ability warded = snap.byStableKey("enchants/warded/1");
        assertNotNull(thorns);
        assertNotNull(warded);
        assertEquals(snap.interners().cooldownScopes().idOf("DEFENSE"), thorns.cdScopeType(),
                "unauthored: the trigger's combat direction");
        assertEquals(snap.interners().cooldownScopes().idOf("MASTERY"), warded.cdScopeType(),
                "authored suppress-type wins, case-folded so `mastery` and `MASTERY` are one scope");

        // …and the authored type is what a TYPE window then has to name: the DEFENSE window misses it.
        run(snap, "enchants/silence/1", "ATTACK", attacker(), victim());
        assertEquals(1, run(snap, "enchants/warded/1", "DEFENSE", victim(), attacker()),
                "one interned slot names ONE type — declaring `mastery` gives up the implicit DEFENSE stamp");
    }

    /** Runs the named ability on its own trigger and reports how many activated (0 = a gate parked it). */
    private int run(Snapshot snap, String stableKey, String trigger, Player actor, Player victim) {
        Ability ability = snap.byStableKey(stableKey);
        assertNotNull(ability, stableKey + " compiled to an ability");
        ModernDispatchSink sink = new ModernDispatchSink(handles,
                Envs.sink().stores(stores).nowTicks(() -> 0L).build());
        int triggerId = TRIGGERS.idOf(trigger).orElseThrow();
        int activated = executor.run(snap.abilities(), new int[] {ability.id()},
                Activation.builder(actor.getUniqueId(), 0, triggerId, 0L)
                        .victimId(victim == null ? null : victim.getUniqueId()).build(),
                new ActivationContext(actor, victim, null, null), sink, snap.stableKeys());
        sink.flush();
        return activated;
    }

    private Player attacker() {
        return player(attackerId);
    }

    private Player victim() {
        return player(victimId);
    }

    private Player player(UUID id) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(id);
        return p;
    }

    /**
     * Six probe enchants through the REAL loader: the silencer, one ability per defender-side trigger, a worn
     * passive, an attack proc, and one that declares a {@code suppress-type} of its own.
     */
    private Snapshot library() throws Exception {
        Path enchants = Files.createDirectories(root.resolve("content/enchants"));
        write(enchants, "silence", """
                display: "Silence"
                trigger: "ATTACK"
                levels:
                  1: { effects: [{ SUPPRESS: { scope: "TYPE", key: "DEFENSE", duration: 100, who: "@Victim" } }] }
                """);
        write(enchants, "thorns", defenceProbe("DEFENSE", ""));
        write(enchants, "scorch", defenceProbe("HURT", ""));
        write(enchants, "springs", defenceProbe("PASSIVE", ""));
        write(enchants, "lifesteal", defenceProbe("ATTACK", ""));
        // Lower-case on purpose: the TYPE vocabulary case-folds, so this must reach the same scope as MASTERY.
        write(enchants, "warded", defenceProbe("DEFENSE", "suppress-type: \"mastery\"\n"));
        Library lib = LibraryLoader.load(root.resolve("content"), COMPILER, 0);
        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        return lib.snapshot();
    }

    private static String defenceProbe(String trigger, String extra) {
        return """
                display: "Probe"
                trigger: "%s"
                %slevels:
                  1: { effects: [{ HEALTH: { amount: 1 } }] }
                """.formatted(trigger, extra);
    }

    private static void write(Path dir, String key, String body) throws Exception {
        Files.writeString(dir.resolve(key + ".yml"), body, StandardCharsets.UTF_8);
    }
}
