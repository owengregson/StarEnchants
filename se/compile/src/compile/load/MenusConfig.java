package compile.load;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import schema.diag.Diagnostic;

/**
 * Compiled snapshot of the {@code menus/} folder (§L), swapped by reference in the same atomic {@code /se reload}
 * transaction as content + items + config + lang. A menu with no {@code menus/<name>.yml} keeps its programmatic default.
 *
 * @param byMenu layout overrides keyed by lower-cased menu name
 */
public record MenusConfig(Map<String, MenuLayoutConfig> byMenu, List<Diagnostic> diagnostics) {

    public MenusConfig {
        byMenu = Map.copyOf(byMenu);
        diagnostics = List.copyOf(diagnostics);
    }

    public static MenusConfig empty() {
        return new MenusConfig(Map.of(), List.of());
    }

    /** Case-insensitive lookup. */
    public Optional<MenuLayoutConfig> forMenu(String menuName) {
        return menuName == null ? Optional.empty()
                : Optional.ofNullable(byMenu.get(menuName.toLowerCase(java.util.Locale.ROOT)));
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::blocking);
    }
}
