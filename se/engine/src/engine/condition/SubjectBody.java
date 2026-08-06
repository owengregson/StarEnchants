package engine.condition;

/**
 * The two subject-cursor facts that come off the BOUND BODY rather than a UUID-keyed store —
 * {@code %target.type%} and {@code %target.relation%} (ADR-0076).
 *
 * <p>Bukkit-free here by design: the implementation lives beside the executor, where the body handle is, and
 * answers only reads the selector's own {@code ENEMIES}/{@code ALLIES} filter already made for this body on
 * this thread. So the per-target pass still never pays a region hop, and this package still imports no Bukkit.
 *
 * <p>Lazy, not eager: a filter reading {@code %target.enchlevel.<key>%} must not be charged a type lookup and
 * an alliance-bridge call for every body it walks.
 */
public interface SubjectBody {

    /** No body bound: both reads are empty, which compares equal to nothing an author can write. */
    SubjectBody NONE = new SubjectBody() {
        @Override
        public String type() {
            return "";
        }

        @Override
        public String relation() {
            return "";
        }
    };

    /** The bound body's {@code EntityType} name. */
    String type();

    /** {@code ALLY} | {@code ENEMY} | {@code NEUTRAL} (a non-player body has no alliance axis). */
    String relation();
}
