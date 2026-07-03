package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bootstrap.wire.ModuleFold;
import bootstrap.wire.Toggle;
import java.util.List;
import platform.lang.Messages;
import org.junit.jupiter.api.Test;

/** Golden lines for {@code /se modules} (ADR-0047): toggle depth/state rendering + the omit-when-empty axis lines. */
class ModulesRenderTest {

    private static final Messages MESSAGES = Messages.defaults();

    private static ModuleFold.ModuleReport report(String name, Toggle toggle, boolean wired,
            List<String> listeners, List<String> skipped, List<String> commands, List<String> mintTypes,
            List<String> menuNames, int playerStores, List<String> stops) {
        return new ModuleFold.ModuleReport(name, toggle, wired, listeners, List.of(), skipped, commands,
                mintTypes, menuNames, playerStores, stops, List.of());
    }

    private static boolean anyContains(List<String> lines, String needle) {
        return lines.stream().anyMatch(line -> line.contains(needle));
    }

    @Test
    void headerCountsTheModulesAndAlwaysOnRendersAsSuch() {
        List<String> lines = ModulesRender.lines(new ModuleFold.Report(List.of(
                report("combat", Toggle.ALWAYS, true, List.of("CombatListener"), List.of(), List.of(), List.of(),
                        List.of(), 0, List.of()))), MESSAGES);
        assertTrue(lines.get(0).contains("1 feature modules"), lines.get(0));
        assertTrue(anyContains(lines, "always on"), lines.toString());
        assertTrue(anyContains(lines, "CombatListener"), lines.toString());
        // Header + the module's name line + its one listeners line — every empty axis line is omitted.
        assertEquals(3, lines.size(), lines.toString());
    }

    @Test
    void bootGateShowsTheWireTimeVerdictAndSkippedWires() {
        List<String> lines = ModulesRender.lines(new ModuleFold.Report(List.of(
                report("souls", Toggle.boot("features.souls", () -> false, "souls disabled"), false,
                        List.of(), List.of("SoulListener", "SoulInteractListener"), List.of("splitsouls"),
                        List.of("gem"), List.of(), 1, List.of("soul aura task")))), MESSAGES);
        assertTrue(anyContains(lines, "features.souls"), lines.toString());
        assertTrue(anyContains(lines, "(boot gate)"), lines.toString());
        assertTrue(anyContains(lines, "off"), lines.toString());       // wire-time verdict: not wired
        assertTrue(anyContains(lines, "skipped (toggle off)"), lines.toString());
        assertTrue(anyContains(lines, "SoulListener, SoulInteractListener"), lines.toString());
        assertTrue(anyContains(lines, "splitsouls"), lines.toString());
        assertTrue(anyContains(lines, "gem"), lines.toString());
        assertTrue(anyContains(lines, "player stores swept on quit"), lines.toString());
        assertTrue(anyContains(lines, "soul aura task"), lines.toString());
    }

    @Test
    void liveGateReadsItsSupplierNowAndRendersMintAliases() {
        List<String> on = ModulesRender.lines(new ModuleFold.Report(List.of(
                report("heroic", Toggle.live("features.heroic", () -> true), true, List.of("HeroicListener"),
                        List.of(), List.of(), List.of("heroic", "upgrade"), List.of(), 0, List.of()))), MESSAGES);
        assertTrue(anyContains(on, "(live, per worn-resolve)"), on.toString());
        assertTrue(anyContains(on, "features.heroic"), on.toString());
        assertTrue(anyContains(on, "heroic, upgrade"), on.toString()); // mint types + aliases

        // LIVE state is read at render time — flip the supplier and the state flips (unlike a BOOT wire-time verdict).
        List<String> off = ModulesRender.lines(new ModuleFold.Report(List.of(
                report("heroic", Toggle.live("features.heroic", () -> false), true, List.of("HeroicListener"),
                        List.of(), List.of(), List.of(), List.of(), 0, List.of()))), MESSAGES);
        assertTrue(off.stream().anyMatch(line -> line.contains("features.heroic") && line.contains("off")),
                off.toString());
    }
}
