package feature.menu;

/**
 * What a single menu button does when clicked (docs/v3-directives.md §K: "Button = item + click-action").
 * A {@link Menu} registers one of these per occupied slot when it {@linkplain Menu#render renders} into a
 * {@link MenuHolder}; the shared {@link MenuListener} looks the action up by raw slot and invokes it. An
 * empty/decorative slot (filler, an icon with no behaviour) simply has no action and the click is swallowed.
 *
 * <p>The callback runs INLINE on the click event's thread — which on Folia is the clicking player's own
 * region thread (the click event fires there) — so it may freely read/write that player's inventory. Any
 * work touching a <em>different</em> entity or a world location must hop through the
 * {@code platform.sched.Scheduling} abstraction itself (folia-scheduling).
 */
@FunctionalInterface
public interface ClickAction {

    void onClick(MenuClick click);
}
