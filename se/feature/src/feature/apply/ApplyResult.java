package feature.apply;

/**
 * The outcome of an {@link ItemEnchanter} apply attempt — ok-or-not plus a message for the caller to relay.
 * Crystal wording is now single-sourced in the {@code crystal.*} lang family (ADR-0041), so there is no
 * structural-reason branch to carry: a caller reports {@link #message()} verbatim.
 */
public record ApplyResult(boolean ok, String message) {

    public static ApplyResult ok(String message) {
        return new ApplyResult(true, message);
    }

    public static ApplyResult fail(String message) {
        return new ApplyResult(false, message);
    }
}
