package feature.menu;

/**
 * What a menu button does when clicked (§K). Runs inline on the click event's thread — the clicking player's
 * own region thread on Folia — so it may touch that player's inventory freely, but any work on a different
 * entity or a world location must hop through {@code platform.sched.Scheduling} (folia-scheduling).
 */
@FunctionalInterface
public interface ClickAction {

    void onClick(MenuClick click);
}
