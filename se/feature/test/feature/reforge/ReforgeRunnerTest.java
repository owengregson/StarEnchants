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
import compile.load.ReforgeDef;
import engine.run.UseAttempt;
import feature.trigger.TriggerDispatch;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import platform.lang.Messages;
import platform.text.Colors;
import platform.text.TimeFormat;
import schema.spec.D;
import schema.spec.ParamSpec;

/**
 * The reforge activation tail (ADR-0070): {@link ReforgeRunner#activate} collapses the {@link UseAttempt}
 * outcome to the universal prefix-free feedback and — only on ACTIVATED, after the success line — runs the
 * §B0.4 cross-plan machines hook. {@link TriggerDispatch} is mocked (its outcomes are the axis under test);
 * expected lines come from the SAME {@link Messages} instance the runner renders with (writing-tests Rule 1).
 */
class ReforgeRunnerTest {

    private static final String KEY = "reforges/testforge";

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
        runner = new ReforgeRunner(holder, dispatch, messages, (p, d) -> {
            activatedPlayers.add(p);
            activatedDefs.add(d);
        });
    }

    private String name() {
        return def.color() + "&l" + def.display();
    }

    @Test
    void activatedFiresTheMachinesHookAndStaysSilent() {
        Player player = mock(Player.class);
        when(dispatch.fireUse(any(), any())).thenReturn(new UseAttempt(true, false, 0, -1, false));

        runner.activate(player, KEY);

        verify(dispatch).fireUse(player, def.useStableKeys()); // the def's USE candidates, verbatim
        assertEquals(List.of(player), activatedPlayers, "the §B0.4 hook fires exactly on ACTIVATED");
        assertEquals(List.of(def), activatedDefs);
        verify(player, never()).sendMessage(anyString()); // blank reforge.success = silent
    }

    @Test
    void onCooldownSendsTheRemainingTime() {
        Player player = mock(Player.class);
        when(dispatch.fireUse(any(), any())).thenReturn(new UseAttempt(false, true, 40, -1, false));

        runner.activate(player, KEY);

        verify(player).sendMessage(Colors.translate(messages.fragment("reforge.cooldown",
                "NAME", name(), "TIME_FORMATTED", TimeFormat.hmsFromTicks(40))));
        assertTrue(activatedDefs.isEmpty(), "a blocked use never runs the machines hook");
    }

    @Test
    void conditionFailSendsTheFailLine() {
        Player player = mock(Player.class);
        when(dispatch.fireUse(any(), any())).thenReturn(new UseAttempt(false, false, 0, 0, false));

        runner.activate(player, KEY);

        verify(player).sendMessage(Colors.translate(messages.fragment("reforge.fail",
                "NAME", name(), "CONDITION", def.conditionSources().get(0))));
        assertTrue(activatedDefs.isEmpty());
    }

    @Test
    void staleKeySendsFailWithoutFiring() {
        Player player = mock(Player.class);

        runner.activate(player, "reforges/ghost");

        verify(dispatch, never()).fireUse(any(), any()); // no def → nothing to fire
        verify(player).sendMessage(Colors.translate(messages.fragment("reforge.fail",
                "NAME", "", "CONDITION", "")));
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
