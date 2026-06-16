package compile;

import schema.spec.ParamSpec;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A straightforward {@link SpecRegistry} backed by a case-insensitive map from
 * head name to {@link ParamSpec}.
 *
 * <p>Duplicate heads are a programming error (two kinds claiming the same name),
 * so construction fails fast rather than silently shadowing — the registry is
 * checked-in wiring, not runtime input.
 */
public final class MapSpecRegistry implements SpecRegistry {

    private final Map<String, ParamSpec> byHead;

    public MapSpecRegistry(Collection<ParamSpec> specs) {
        Map<String, ParamSpec> map = new LinkedHashMap<>();
        for (ParamSpec spec : specs) {
            String key = canonical(spec.head());
            ParamSpec prev = map.put(key, spec);
            if (prev != null) {
                throw new IllegalArgumentException("duplicate kind head '" + spec.head() + "'");
            }
        }
        this.byHead = Map.copyOf(map);
    }

    public static MapSpecRegistry of(ParamSpec... specs) {
        return new MapSpecRegistry(java.util.List.of(specs));
    }

    @Override
    public Optional<ParamSpec> lookup(String head) {
        return Optional.ofNullable(byHead.get(canonical(head)));
    }

    @Override
    public Set<String> heads() {
        return byHead.keySet();
    }

    private static String canonical(String head) {
        return head.trim().toUpperCase(Locale.ROOT);
    }
}
