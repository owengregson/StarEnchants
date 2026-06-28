package feature.trak;

/** Outcome of a trak-gem apply gesture, for {@link TrakListener} to commit (§I). */
public record TrakResult(boolean commit, String message) {

    static TrakResult noop(String message) {
        return new TrakResult(false, message);
    }

    static TrakResult committed(String message) {
        return new TrakResult(true, message);
    }
}
