package compile.model;

/**
 * The name&harr;id tables a {@link Snapshot} is built against — one {@link Interner} per namespace
 * (docs/architecture.md §4.1, §8); the snapshot carries them to translate runtime worlds/triggers and
 * render ids in diagnostics. Frozen after erasure.
 *
 * @param worlds         world name &harr; id (bit position in {@code worldBlacklist}; cap 64)
 * @param triggers       trigger name &harr; id (bit position in {@code triggerMask}; cap 32)
 * @param suppress       suppression key &harr; id ({@code DISABLE_*} matching, §6.2)
 * @param cooldownScopes cooldown-scope name &harr; id (enchant/group/type scopes)
 */
public record Interners(
        Interner worlds,
        Interner triggers,
        Interner suppress,
        Interner cooldownScopes) {
}
