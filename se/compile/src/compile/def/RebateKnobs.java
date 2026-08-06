package compile.def;

/**
 * The authored chance-rebate envelope (ADR-0076 part E), before lowering: a declared term subtracted from an
 * ability's chance, and the feedback the roll it ate is allowed to produce.
 *
 * <p>The whole point of declaring it is that gate 8 can then tell a roll the rebate blocked from an ordinary
 * miss. Buried inside a {@code chance:} expression the two are the same event, which is why the corpus's
 * blocked-proc lines had nowhere to hang.
 *
 * @param points         expression text subtracted from the base chance in PERCENTAGE POINTS; {@code null} = none
 * @param scale          expression text taken as a FRACTION of the base chance ({@code 0..1}); mutually
 *                       exclusive with {@link #points}
 * @param message        line shown when a roll lands in the rebated band; {@code null}/blank = none
 * @param messageToActor whether {@link #message} goes to the ACTIVATOR rather than the victim (the default)
 * @param sound          sound token played with {@link #message}; {@code null}/blank = none
 * @param spendsCooldown whether a rebated roll KEEPS gate 6's reservation, so the blocked attempt burns the
 *                       window (Guided Rocket Escape's measured behaviour); {@code false} = release it
 */
public record RebateKnobs(String points, String scale, String message, boolean messageToActor, String sound,
                          boolean spendsCooldown) {

    public static final RebateKnobs NONE = new RebateKnobs(null, null, null, false, null, false);

    /** Whether a rebate TERM was authored — the only thing that can produce the verdict the rest describes. */
    public boolean hasTerm() {
        return notBlank(points) || notBlank(scale);
    }

    /** Whether anything at all was authored, so an orphaned message or cue can be diagnosed rather than ignored. */
    public boolean authored() {
        return hasTerm() || notBlank(message) || notBlank(sound) || spendsCooldown;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
