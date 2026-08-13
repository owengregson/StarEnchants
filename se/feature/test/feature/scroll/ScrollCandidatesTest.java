package feature.scroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Pure black-scroll tier contracts: exact pack vocabularies, safe unknowns, and exact level removal input. */
class ScrollCandidatesTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("packPolicies")
    void packTierPoliciesIncludeTheirHigherTierButExcludeTheTopTier(
            String pack, String higherTier, String excludedTier) {
        Map<String, Integer> enchants = new LinkedHashMap<>();
        enchants.put("common", 1);
        enchants.put("uncommon", 2);
        enchants.put("rare", 3);
        enchants.put("epic", 4);
        enchants.put("legendary", 5);
        enchants.put("soul", 5);
        enchants.put("higher", 7);
        enchants.put("excluded", 9);
        Map<String, String> tiers = Map.of(
                "common", "common", "uncommon", "uncommon", "rare", "rare", "epic", "epic",
                "legendary", "legendary", "soul", "soul", "higher", higherTier, "excluded", excludedTier);
        List<String> normalPolicy = List.of("common", "uncommon", "rare", "epic", "legendary", "soul");
        Set<String> known = Set.of(
                "common", "uncommon", "rare", "epic", "legendary", "soul", higherTier, excludedTier);

        List<ScrollCandidates.Candidate> normal = ScrollCandidates.eligible(
                enchants, tiers::get, known, normalPolicy);
        List<ScrollCandidates.Candidate> higher = ScrollCandidates.eligible(
                enchants, tiers::get, known,
                Stream.concat(normalPolicy.stream(), Stream.of(higherTier)).toList());

        assertEquals(List.of("common", "uncommon", "rare", "epic", "legendary", "soul"), keys(normal));
        assertEquals(List.of("common", "uncommon", "rare", "epic", "legendary", "soul", "higher"),
                keys(higher));
        assertEquals(7, higher.get(6).level(), "selection carries the stored enchant level");
    }

    static Stream<Arguments> packPolicies() {
        return Stream.of(
                Arguments.of("signature", "mythic", "godly"),
                Arguments.of("cosmic", "heroic", "mastery"));
    }

    @Test
    void unknownOrMissingTiersAreExcludedEvenWithPermissiveConfig() {
        Map<String, Integer> enchants = new LinkedHashMap<>();
        enchants.put("known", 1);
        enchants.put("unknown", 4);
        enchants.put("missing", 8);
        Map<String, String> tiers = Map.of("known", "common", "unknown", "future");

        List<ScrollCandidates.Candidate> result = ScrollCandidates.eligible(
                enchants, tiers::get, Set.of("common"), List.of());

        assertEquals(List.of("known"), keys(result));
    }

    @Test
    void tierIdsAreExactAndCaseSensitive() {
        List<ScrollCandidates.Candidate> result = ScrollCandidates.eligible(
                Map.of("candidate", 3), key -> "mythic", Set.of("mythic"), List.of("MYTHIC"));

        assertTrue(result.isEmpty());
    }

    @Test
    void emptyCandidatesLeaveTheCallerFreeToPreserveBothItems() {
        List<ScrollCandidates.Candidate> result = ScrollCandidates.eligible(
                Map.of("higher", 3), key -> "mythic", Set.of("mythic"), List.of("common"));
        assertTrue(result.isEmpty());
    }

    @Test
    void removalDropsOnlyTheSelectedEnchant() {
        Map<String, Integer> enchants = new LinkedHashMap<>();
        enchants.put("first", 2);
        enchants.put("selected", 7);
        enchants.put("last", 4);

        Map<String, Integer> remaining = ScrollCandidates.remove(
                enchants, new ScrollCandidates.Candidate("selected", 7)).orElseThrow();

        assertEquals(Map.of("first", 2, "last", 4), remaining);
        assertEquals(7, enchants.get("selected"), "the caller's source state is unchanged until commit");
    }

    private static List<String> keys(List<ScrollCandidates.Candidate> candidates) {
        return candidates.stream().map(ScrollCandidates.Candidate::key).toList();
    }
}
