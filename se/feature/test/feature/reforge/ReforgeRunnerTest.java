package feature.reforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.Compiler;
import compile.MapSpecRegistry;
import compile.load.ContentHolder;
import compile.load.Lang;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.MasterConfig;
import compile.load.ReforgeDef;
import engine.run.UseAttempt;
import engine.stores.EngineStores;
import feature.trigger.TriggerDispatch;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import platform.lang.Messages;
import platform.text.Colors;
import platform.text.TimeFormat;
import platform.text.Tokens;
import schema.spec.D;
import schema.spec.ParamSpec;

/**
 * The reforge activation tail (ADR-0070, identity rework): {@link ReforgeRunner#activate} collapses the
 * {@link UseAttempt} outcome to the universal pets-convention messages ({@link ReforgeMessenger} over the
 * live {@code reforges:} templates) and — only on ACTIVATED, after the success line — runs the §B0.4
 * cross-plan machines hook. {@link TriggerDispatch} is mocked (its outcomes are the axis under test);
 * expected lines render through the SAME template/token chain the messenger uses (writing-tests Rule 1).
 */
class ReforgeRunnerTest {

    private static final String KEY = "reforges/testforge";
    private static final MasterConfig.ReforgesSection SECTION = MasterConfig.ReforgesSection.defaults();

    @TempDir
    Path root;

    private final Messages messages = new Messages(Lang::defaults);
    private TriggerDispatch dispatch;
    private ReforgeDef def;
    private ReforgeRunner runner;
    private final List<Player> activatedPlayers = new ArrayList<>();
    private final List<ReforgeDef> activatedDefs = new ArrayList<>();

    @BeforeEach
    void build() throws IOException {
        Path file = root.resolve("reforges/testforge.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
            display: "Testforge"
            color: "&6"
            icon: SUGAR
            cooldown: 200
            condition: "%test%"
            effects: [{ HEAL: { amount: 1 } }]
            """, StandardCharsets.UTF_8);
        Library lib = LibraryLoader.load(root,
                Compiler.of(MapSpecRegistry.of(ParamSpec.of("HEAL").param("amount", D.DOUBLE.min(0)).build())), 1);
        ContentHolder holder = new ContentHolder(lib);
        def = holder.library().reforgeDefOf(KEY);
        dispatch = mock(TriggerDispatch.class);
        runner = new ReforgeRunner(holder, dispatch, new ReforgeMessenger(messages, () -> SECTION), (p, d) -> {
            activatedPlayers.add(p);
            activatedDefs.add(d);
        }, EngineStores.fresh(), () -> 0L);
    }

    /** The messenger's own render chain over the defaults section — never a re-typed literal (Rule 1). */
    private String expected(String template, boolean uppercase, long remainingTicks) {
        String name = uppercase ? def.display().toUpperCase(Locale.ROOT) : def.display();
        return Colors.translate(Tokens.sub(Tokens.colorTolerant(template),
                "COLOR", def.color(), "NAME", name, "TIME_FORMATTED", TimeFormat.hmsFromTicks(remainingTicks)));
    }

    @Test
    void activatedSpeaksTheUniversalLineAndFiresTheMachinesHook() {
        Player player = mock(Player.class);
        when(dispatch.fireUse(any(), any())).thenReturn(new UseAttempt(true, false, 0, -1, false));

        runner.activate(player, KEY);

        verify(dispatch).fireUse(player, def.useStableKeys()); // the def's USE candidates, verbatim
        verify(player).sendMessage(expected(SECTION.messageOnActivate(), SECTION.uppercase(), 0));
        assertEquals(List.of(player), activatedPlayers, "the §B0.4 hook fires exactly on ACTIVATED");
        assertEquals(List.of(def), activatedDefs);
    }

    @Test
    void onCooldownSendsTheRemainingTime() {
        Player player = mock(Player.class);
        when(dispatch.fireUse(any(), any())).thenReturn(new UseAttempt(false, true, 40, -1, false));

        runner.activate(player, KEY);

        verify(player).sendMessage(expected(SECTION.messageOnCooldown(), false, 40));
        assertTrue(activatedDefs.isEmpty(), "a blocked use never runs the machines hook");
    }

    @Test
    void conditionFailSendsTheFailLine() {
        Player player = mock(Player.class);
        when(dispatch.fireUse(any(), any())).thenReturn(new UseAttempt(false, false, 0, 0, false));

        runner.activate(player, KEY);

        verify(player).sendMessage(expected(SECTION.messageOnFail(), false, 0));
        assertTrue(activatedDefs.isEmpty());
    }

    @Test
    void staleKeyIsASilentNoop() {
        Player player = mock(Player.class);

        runner.activate(player, "reforges/ghost");

        verify(dispatch, never()).fireUse(any(), any()); // no def → nothing to fire, nothing to name
        verify(player, never()).sendMessage(anyString());
        assertTrue(activatedDefs.isEmpty());
    }

    @Test
    void chanceFailIsSilent() {
        Player player = mock(Player.class);
        when(dispatch.fireUse(any(), any())).thenReturn(new UseAttempt(false, false, 0, -1, true));

        runner.activate(player, KEY);

        verify(player, never()).sendMessage(anyString());
        assertTrue(activatedDefs.isEmpty());
    }
}
