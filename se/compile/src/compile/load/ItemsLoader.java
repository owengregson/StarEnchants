package compile.load;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import schema.diag.Diagnostics;
import schema.diag.Source;

/**
 * Loads the top-level {@code items/} config folder into an immutable {@link ItemsConfig} (docs/v3-directives.md
 * §L). Mirrors {@link LibraryLoader}'s per-file pattern but produces config (no abilities, never reaches the
 * compiler): each {@code items/*.yml} is composed with {@link YamlNode} and dispatched by its {@code type}
 * field to the matching reader. Reuses the content YAML/diagnostics machinery, so {@code /se reload --dry-run}
 * surfaces items-config faults the same way. Never throws — a missing folder yields {@link ItemsConfig#empty()},
 * an unreadable/malformed file yields a diagnostic and is skipped (the runtime falls back to built-in defaults).
 */
public final class ItemsLoader {

    private ItemsLoader() {
    }

    /** Load {@code items/} (or an empty config if the folder is absent). */
    public static ItemsConfig load(Path itemsRoot) {
        Diagnostics diags = new Diagnostics();
        Optional<SoulGemConfig> soulGem = Optional.empty();
        Optional<CrystalConfig> crystal = Optional.empty();
        Optional<HeroicConfig> heroic = Optional.empty();
        Optional<SlotConfig> slots = Optional.empty();
        if (itemsRoot == null || !Files.isDirectory(itemsRoot)) {
            return ItemsConfig.empty();
        }
        for (Path file : configFiles(itemsRoot)) {
            String name = "items/" + itemsRoot.relativize(file);
            String yaml;
            try {
                yaml = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                diags.error("E_ITEM_IO", "could not read " + name + ": " + e.getMessage(), Source.ofFile(name));
                continue;
            }
            YamlNode root = YamlNode.compose(name, yaml, diags);
            if (!root.isMapping()) {
                diags.error("E_ITEM_SHAPE", name + " is not a YAML mapping", Source.ofFile(name));
                continue;
            }
            String type = blankToNull(root.string("type"));
            if (type == null) {
                diags.error("E_ITEM_TYPE", name + " is missing a 'type'", root.source(),
                        "add e.g. 'type: soul-gem'");
                continue;
            }
            switch (type.toLowerCase(Locale.ROOT)) {
                case "soul-gem", "soul_gem", "soulgem" -> {
                    if (soulGem.isPresent()) {
                        diags.warning("W_ITEM_DUP", "more than one soul-gem config (" + name + "); keeping the first",
                                root.source());
                    } else {
                        soulGem = Optional.of(readSoulGem(root, diags));
                    }
                }
                case "crystal" -> {
                    if (crystal.isPresent()) {
                        diags.warning("W_ITEM_DUP", "more than one crystal config (" + name + "); keeping the first",
                                root.source());
                    } else {
                        crystal = Optional.of(readCrystal(root, diags));
                    }
                }
                case "heroic" -> {
                    if (heroic.isPresent()) {
                        diags.warning("W_ITEM_DUP", "more than one heroic config (" + name + "); keeping the first",
                                root.source());
                    } else {
                        heroic = Optional.of(readHeroic(root, diags));
                    }
                }
                case "slots", "slot", "slot-expander" -> {
                    if (slots.isPresent()) {
                        diags.warning("W_ITEM_DUP", "more than one slots config (" + name + "); keeping the first",
                                root.source());
                    } else {
                        slots = Optional.of(readSlots(root, diags));
                    }
                }
                default -> diags.warning("W_ITEM_TYPE", "unknown item type '" + type + "' in " + name, root.source());
            }
        }
        return new ItemsConfig(soulGem, crystal, heroic, slots, diags.all());
    }

    private static SlotConfig readSlots(YamlNode root, Diagnostics diags) {
        SlotConfig d = SlotConfig.defaults();
        return new SlotConfig(
                orDefault(root.string("orb-material"), d.orbMaterial()),
                orDefault(root.string("orb-name"), d.orbName()),
                root.has("orb-lore") ? root.stringList("orb-lore") : d.orbLore(),
                parseInt(root.string("orb-amount"), d.orbAmount(), root, diags),
                orDefault(root.string("gem-material"), d.gemMaterial()),
                orDefault(root.string("gem-name"), d.gemName()),
                root.has("gem-lore") ? root.stringList("gem-lore") : d.gemLore(),
                parseInt(root.string("hard-cap"), d.hardCap(), root, diags),
                orDefault(root.string("message-apply"), d.messageApply()),
                orDefault(root.string("message-at-cap"), d.messageAtCap()));
    }

    private static HeroicConfig readHeroic(YamlNode root, Diagnostics diags) {
        HeroicConfig d = HeroicConfig.defaults();
        Map<String, String> upgrades = new LinkedHashMap<>();
        for (YamlNode.Entry e : root.entries("material-upgrades")) {
            String to = e.value().scalar();
            if (to != null && !to.isBlank()) {
                upgrades.put(e.key().toUpperCase(Locale.ROOT), to.trim().toUpperCase(Locale.ROOT));
            }
        }
        return new HeroicConfig(
                orDefault(root.string("material"), d.material()),
                orDefault(root.string("name"), d.name()),
                root.has("lore") ? root.stringList("lore") : d.lore(),
                parseInt(root.string("success-min"), d.successMin(), root, diags),
                parseInt(root.string("success-max"), d.successMax(), root, diags),
                parseDouble(root.string("percent-damage"), d.percentDamage(), root, diags),
                parseDouble(root.string("percent-reduction"), d.percentReduction(), root, diags),
                parseDouble(root.string("durability"), d.durability(), root, diags),
                upgrades.isEmpty() ? d.materialUpgrades() : upgrades,
                orDefault(root.string("reduction-scope"), d.reductionScope()),
                orDefault(root.string("message-success"), d.messageSuccess()),
                orDefault(root.string("message-fail"), d.messageFail()));
    }

    private static double parseDouble(String raw, double fallback, YamlNode root, Diagnostics diags) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            diags.warning("W_ITEM_NUM", "invalid number '" + raw + "', using " + fallback, root.source());
            return fallback;
        }
    }

    private static CrystalConfig readCrystal(YamlNode root, Diagnostics diags) {
        CrystalConfig d = CrystalConfig.defaults();
        return new CrystalConfig(
                orDefault(root.string("material"), d.material()),
                orDefault(root.string("name"), d.name()),
                root.has("lore") ? root.stringList("lore") : d.lore(),
                parseInt(root.string("success-chance"), d.successChance(), root, diags),
                root.has("consume-on-fail")
                        ? "true".equalsIgnoreCase(root.string("consume-on-fail")) : d.consumeOnFail(),
                orDefault(root.string("message-apply-success"), d.messageApplySuccess()),
                orDefault(root.string("message-apply-fail"), d.messageApplyFail()),
                orDefault(root.string("message-no-slots"), d.messageNoSlots()),
                orDefault(root.string("message-merge"), d.messageMerge()));
    }

    private static SoulGemConfig readSoulGem(YamlNode root, Diagnostics diags) {
        SoulGemConfig d = SoulGemConfig.defaults();
        return new SoulGemConfig(
                orDefault(root.string("material"), d.material()),
                orDefault(root.string("name"), d.name()),
                root.has("lore") ? root.stringList("lore") : d.lore(),
                parseInt(root.string("souls-per-kill"), d.soulsPerKill(), root, diags),
                orDefault(root.string("message-activate"), d.messageActivate()),
                orDefault(root.string("message-deactivate"), d.messageDeactivate()),
                orDefault(root.string("message-soul-use"), d.messageSoulUse()));
    }

    private static List<Path> configFiles(Path root) {
        try (Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(p -> Files.isRegularFile(p))
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".yml") || n.endsWith(".yaml");
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return new ArrayList<>(); // the directory existed but became unreadable — load nothing, never throw
        }
    }

    private static int parseInt(String raw, int fallback, YamlNode root, Diagnostics diags) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            diags.warning("W_ITEM_NUM", "invalid number '" + raw + "', using " + fallback, root.source());
            return fallback;
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
