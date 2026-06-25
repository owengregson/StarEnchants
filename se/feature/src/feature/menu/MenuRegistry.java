package feature.menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The name → {@link Menu} table {@code /se menu <name>} opens menus through (docs/v3-directives.md §K, §J).
 * {@link LinkedHashMap} preserves registration order so completion/help listing is declaration-ordered.
 */
public final class MenuRegistry {

    private final Map<String, Menu> byName = new LinkedHashMap<>();

    /** Register {@code menu} under its (lower-cased) {@link Menu#name()}; a duplicate name replaces the prior. */
    public MenuRegistry register(Menu menu) {
        Objects.requireNonNull(menu, "menu");
        byName.put(menu.name().toLowerCase(Locale.ROOT), menu);
        return this;
    }

    /** The menu registered under {@code name} (case-insensitive), if any. */
    public Optional<Menu> get(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byName.get(name.toLowerCase(Locale.ROOT)));
    }

    /** Every registered menu name, in registration order (for tab-completion and help text). */
    public List<String> names() {
        return new ArrayList<>(byName.keySet());
    }
}
