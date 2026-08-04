package engine.sink;

/**
 * Whose summons a {@code SUMMON_PURGE} sweep removes. The rungs are a monotone ladder of EXEMPTIONS, read
 * straight off the token: each one spares everything the rung below it spares, plus one more group. An owner
 * who is offline is by construction not an ONLINE ally, so the {@code -or-offline} rung is the only one that
 * lets an abandoned summon stand.
 */
public final class SummonPurgeFilter {

    /** Purge every attributable summon that is not the actor's — an ally's included. */
    public static final String NOT_OWN = "not-own";
    /** Also spare an ONLINE ally's; an offline owner has nobody to vouch for it, so its summon still goes. */
    public static final String NOT_OWN_OR_ALLY = "not-own-or-ally";
    /** Also spare an offline owner's — an abandoned summon is left to run out its own TTL. */
    public static final String NOT_OWN_OR_ALLY_OR_OFFLINE = "not-own-or-ally-or-offline";

    private SummonPurgeFilter() {
    }

    /** The rung vocabulary, weakest sweep last — the {@code SUMMON_PURGE} spec's own enum values. */
    public static String[] names() {
        return new String[] {NOT_OWN, NOT_OWN_OR_ALLY, NOT_OWN_OR_ALLY_OR_OFFLINE};
    }

    /**
     * Whether a tracked summon is purged, from the three facts a sweep can establish about its owner. Pure on
     * purpose — the caller resolves the owner and asks {@code Allies}, so the decision itself carries no
     * Bukkit and is hand-checkable. An unrecognised {@code filter} reads as {@link #NOT_OWN_OR_ALLY} (the
     * compiler's closed enum already rejects one from content).
     */
    public static boolean purges(String filter, boolean ownedByActor, boolean ownerOnline, boolean allied) {
        if (ownedByActor) {
            return false; // your own summons are never yours to lose
        }
        if (NOT_OWN.equalsIgnoreCase(filter)) {
            return true;
        }
        if (ownerOnline && allied) {
            return false;
        }
        return ownerOnline || !NOT_OWN_OR_ALLY_OR_OFFLINE.equalsIgnoreCase(filter);
    }
}
