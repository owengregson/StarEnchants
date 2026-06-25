package imagegen.text;

/**
 * One run of same-styled text from a legacy-coded line (split at every colour/format change), the unit the tooltip
 * renderer lays out left-to-right. {@code rgb} is the resolved 24-bit colour; the flags are Minecraft's §l/§o/§n/§m/§k.
 */
public record StyledRun(String text, int rgb, boolean bold, boolean italic,
                        boolean underline, boolean strikethrough, boolean obfuscated) {
}
