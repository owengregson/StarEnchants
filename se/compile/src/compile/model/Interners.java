package compile.model;

/**
 * The bundle of name&harr;id tables a {@link Snapshot} is built against — one
 * {@link Interner} per interned namespace (docs/architecture.md §4.1, §8). Bit
 * positions in {@link Ability#triggerMask()} and {@link Ability#worldBlacklist()}
 * are ids drawn from {@link #triggers} and {@link #worlds} respectively, so the
 * snapshot must carry these tables to translate a runtime world/trigger back to its
 * id (and vice-versa) and to render ids in diagnostics.
 *
 * <p>Frozen after erasure: the eraser populates the interners, the snapshot holds
 * them read-only.
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
