package feature.scroll;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/** Pure tier filtering for black-scroll extraction; unknown or untiered entries are unsafe to extract. */
public final class ScrollCandidates {

    private ScrollCandidates() {
    }

    public record Candidate(String key, int level) {
    }

    /**
     * Returns the eligible entries in source order. An empty allow-list means every registered tier is allowed;
     * a non-empty list is an explicit allow-list. Tier IDs are exact, case-sensitive registry keys.
     */
    public static List<Candidate> eligible(Map<String, Integer> enchants,
                                            Function<String, String> tierOf,
                                            Set<String> knownTiers,
                                            List<String> allowedTiers) {
        if (enchants == null || enchants.isEmpty() || tierOf == null) {
            return List.of();
        }
        Set<String> known = exactSet(knownTiers);
        Set<String> allowed = exactSet(allowedTiers);
        List<Candidate> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            String tier = tierOf.apply(entry.getKey());
            // Missing/unregistered tiers are excluded even when the config uses the permissive empty allow-list.
            if (tier == null || tier.isBlank() || !known.contains(tier)
                    || (!allowed.isEmpty() && !allowed.contains(tier))) {
                continue;
            }
            out.add(new Candidate(entry.getKey(), entry.getValue() == null ? 1 : entry.getValue()));
        }
        return Collections.unmodifiableList(out);
    }

    public static Optional<Candidate> choose(Map<String, Integer> enchants,
                                              Function<String, String> tierOf,
                                              Set<String> knownTiers,
                                              List<String> allowedTiers,
                                              Random random) {
        List<Candidate> candidates = eligible(enchants, tierOf, knownTiers, allowedTiers);
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(random.nextInt(candidates.size())));
    }

    /** Removes exactly the selected key while retaining the source order and every other level. */
    public static Optional<Map<String, Integer>> remove(Map<String, Integer> enchants, Candidate selected) {
        if (enchants == null || selected == null || !enchants.containsKey(selected.key())) {
            return Optional.empty();
        }
        Map<String, Integer> remaining = new LinkedHashMap<>(enchants);
        remaining.remove(selected.key());
        return Optional.of(Collections.unmodifiableMap(remaining));
    }

    private static Set<String> exactSet(Iterable<String> values) {
        Set<String> out = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    out.add(value);
                }
            }
        }
        return out;
    }

}
