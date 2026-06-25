package feature.apply;

/** The outcome of an {@link ItemEnchanter} apply attempt — ok-or-not plus a message for the command to relay. */
public record ApplyResult(boolean ok, String message) {

    public static ApplyResult ok(String message) {
        return new ApplyResult(true, message);
    }

    public static ApplyResult fail(String message) {
        return new ApplyResult(false, message);
    }
}
