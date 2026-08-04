package engine.sink;

import platform.text.Tokens;

/**
 * The {@code {OWNER}} token in a summon's custom name — the ONE token a summon name takes. A name is not a
 * MESSAGE: there is no recipient for {@code {SELF}}, no per-copy send loop, and no {@code tokens} expression
 * map, so {@link engine.effect.kind.MessageEffect}'s vocabulary stops at the send boundary and only the
 * summoner's own name crosses into a nameplate.
 */
final class SummonNames {

    static final String OWNER = "OWNER";

    private static final String TOKEN = "{" + OWNER + "}";

    private SummonNames() {
    }

    /** Whether {@code name} carries the token at all — the scan that keeps an untokened spawn from paying
     *  for the owner lookup {@link #fill} would otherwise need. */
    static boolean carriesOwner(String name) {
        return name != null && name.indexOf(TOKEN) >= 0;
    }

    /**
     * {@code name} with {@code {OWNER}} filled from {@code ownerName}; the same instance back when it carries
     * no token, so an untokened spawn allocates nothing. An absent owner substitutes EMPTY rather than leaving
     * the raw token visible — the choice MESSAGE already makes for an absent combat party, and a player should
     * never be shown the authoring syntax.
     */
    static String fill(String name, String ownerName) {
        if (!carriesOwner(name)) {
            return name;
        }
        return Tokens.sub(name, OWNER, ownerName == null ? "" : ownerName);
    }
}
