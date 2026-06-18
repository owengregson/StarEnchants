package compile.load;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import schema.diag.Diagnostic;

/**
 * The compiled snapshot of the top-level {@code menus/} folder (docs/v3-directives.md §L) — per-GUI layout
 * keyed by menu name, loaded as a parallel immutable reference and swapped in the same atomic
 * {@code /se reload} transaction as content + items + config + lang. A menu with no {@code menus/<name>.yml}
 * (or an unset field) keeps its programmatic default; readers always see a fully-built snapshot.
 *
 * @param byMenu      layout overrides by lower-cased menu name ({@code apply}, {@code enchanter}, …)
 * @param diagnostics every diagnostic raised loading {@code menus/}
 */
public record MenusConfig(Map<String, MenuLayoutConfig> byMenu, List<Diagnostic> diagnostics) {

    public MenusConfig {
        byMenu = Map.copyOf(byMenu);
        diagnostics = List.copyOf(diagnostics);
    }

    /** An empty config (no menus/ files) — every menu uses its programmatic default layout. */
    public static MenusConfig empty() {
        return new MenusConfig(Map.of(), List.of());
    }

    /** The layout override for {@code menuName} (case-insensitive), or empty when none is configured. */
    public Optional<MenuLayoutConfig> forMenu(String menuName) {
        return menuName == null ? Optional.empty()
                : Optional.ofNullable(byMenu.get(menuName.toLowerCase(java.util.Locale.ROOT)));
    }

    /** Whether any blocking diagnostic was raised loading {@code menus/}. */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::blocking);
    }
}
