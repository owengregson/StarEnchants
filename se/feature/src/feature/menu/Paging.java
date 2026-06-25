package feature.menu;

/** Pure pagination arithmetic behind every paged menu (docs/v3-directives.md §K); server-free for unit tests. */
public final class Paging {

    private Paging() {
    }

    /** Number of pages needed to show {@code itemCount} items {@code perPage} at a time (at least 1). */
    public static int pageCount(int itemCount, int perPage) {
        if (perPage <= 0) {
            return 1;
        }
        return Math.max(1, (itemCount + perPage - 1) / perPage);
    }

    /** Wrap {@code page} into {@code [0, pages)} so a stale/over-large page index never throws or shows blank. */
    public static int clampPage(int page, int pages) {
        return Math.floorMod(page, Math.max(1, pages));
    }

    /** The catalog index shown in content {@code slot} of {@code page}, given {@code perPage} content slots. */
    public static int indexFor(int page, int slot, int perPage) {
        return page * perPage + slot;
    }
}
