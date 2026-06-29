package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * Internal grouping of the TRAK-gem family (§I) — three gems that, applied to gear, reveal a per-item lifetime
 * counter tracked in the background: BlockTrak (blocks broken with a tool), MobTrak (mobs slain with a
 * weapon), SoulTrak (players killed with a weapon). Each member is authored in its own {@code items/} file and
 * assembled into one {@code TraksConfig}, mirroring the scroll family.
 */
public record TraksConfig(Trak block, Trak mob, Trak soul, Trak fish) {

    public TraksConfig {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(soul, "soul");
        Objects.requireNonNull(fish, "fish");
    }

    /**
     * One trak gem. {@code lore} is the GEM's own description ({@code {KINDS}} expands to the grammatically-
     * joined applies-to label); {@code countFormat} is the line stamped onto the APPLIED item, with
     * {@code {COUNT}} the live (comma-grouped) count. {@code appliesTo} are item-group kinds (e.g. {@code TOOL},
     * {@code WEAPON}) the gem may be applied to and whose lifetime is tracked in the background.
     */
    public record Trak(String material, String name, List<String> lore, List<String> appliesTo,
                       String countFormat) {
        public Trak {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(countFormat, "countFormat");
            lore = List.copyOf(lore);
            appliesTo = List.copyOf(appliesTo);
        }
    }

    public static TraksConfig defaults() {
        return new TraksConfig(
                new Trak(
                        "SLIME_BALL",
                        "&a&lBlockTrak Gem",
                        List.of("&eDisplays the amount of blocks broken with the tool since it was forged.",
                                "",
                                "&eApplies to: &r&f&n{KINDS}",
                                "&7Drag n' Drop on an item to apply."),
                        List.of("TOOL"),
                        "&7Blocks Broken: &f{COUNT}"),
                new Trak(
                        "MAGMA_CREAM",
                        "&e&lMobTrak Gem",
                        List.of("&eDisplays the amount of mobs slain with the weapon since it was forged.",
                                "",
                                "&eApplies to: &r&f&n{KINDS}",
                                "&7Drag n' Drop on an item to apply."),
                        List.of("WEAPON"),
                        "&7Mobs Slain: &f{COUNT}"),
                new Trak(
                        "FIRE_CHARGE",
                        "&c&lSoulTrak Gem",
                        List.of("&eDisplays the amount of players killed with the weapon since it was forged.",
                                "",
                                "&eApplies to: &r&f&n{KINDS}",
                                "&7Drag n' Drop on an item to apply."),
                        List.of("WEAPON"),
                        "&7Players Killed: &f{COUNT}"),
                new Trak(
                        "CLAY_BALL",
                        "&3&lFishTrak Gem",
                        List.of("&eDisplays the amount of fish caught with the rod since it was forged.",
                                "",
                                "&eApplies to: &r&f&n{KINDS}",
                                "&7Drag n' Drop on an item to apply."),
                        List.of("FISHING_ROD"),
                        "&7Fish Caught: &f{COUNT}"));
    }
}
