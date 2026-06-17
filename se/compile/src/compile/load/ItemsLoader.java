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
        Optional<ScrollsConfig> scrolls = Optional.empty();
        Optional<UnopenedBookConfig> unopenedBook = Optional.empty();
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
                case "scrolls", "scroll" -> {
                    if (scrolls.isPresent()) {
                        diags.warning("W_ITEM_DUP", "more than one scrolls config (" + name + "); keeping the first",
                                root.source());
                    } else {
                        scrolls = Optional.of(readScrolls(root, diags));
                    }
                }
                case "unopened-book", "unopened", "mystery-book" -> {
                    if (unopenedBook.isPresent()) {
                        diags.warning("W_ITEM_DUP", "more than one unopened-book config (" + name + "); keeping the first",
                                root.source());
                    } else {
                        unopenedBook = Optional.of(readUnopenedBook(root, diags));
                    }
                }
                default -> diags.warning("W_ITEM_TYPE", "unknown item type '" + type + "' in " + name, root.source());
            }
        }
        return new ItemsConfig(soulGem, crystal, heroic, slots, scrolls, unopenedBook, diags.all());
    }

    private static ScrollsConfig readScrolls(YamlNode root, Diagnostics diags) {
        ScrollsConfig d = ScrollsConfig.defaults();
        YamlNode black = root.child("black");
        YamlNode rand = root.child("randomizer");
        YamlNode trans = root.child("transmog");
        YamlNode holy = root.child("holy");
        YamlNode tag = root.child("nametag");
        ScrollsConfig.Black bd = d.black();
        ScrollsConfig.Randomizer rd = d.randomizer();
        ScrollsConfig.Transmog td = d.transmog();
        ScrollsConfig.Holy hd = d.holy();
        ScrollsConfig.Nametag nd = d.nametag();
        return new ScrollsConfig(
                new ScrollsConfig.Black(
                        orDefault(black.string("material"), bd.material()),
                        orDefault(black.string("name"), bd.name()),
                        black.has("lore") ? black.stringList("lore") : bd.lore(),
                        parseInt(black.string("success-chance"), bd.successChance(), root, diags),
                        orDefault(black.string("message-success"), bd.messageSuccess()),
                        orDefault(black.string("message-fail"), bd.messageFail()),
                        orDefault(black.string("message-no-enchants"), bd.messageNoEnchants())),
                new ScrollsConfig.Randomizer(
                        orDefault(rand.string("material"), rd.material()),
                        orDefault(rand.string("name"), rd.name()),
                        rand.has("lore") ? rand.stringList("lore") : rd.lore(),
                        parseInt(rand.string("min-percent"), rd.minPercent(), root, diags),
                        parseInt(rand.string("max-percent"), rd.maxPercent(), root, diags),
                        orDefault(rand.string("message-success"), rd.messageSuccess()),
                        orDefault(rand.string("message-not-book"), rd.messageNotBook())),
                new ScrollsConfig.Transmog(
                        orDefault(trans.string("material"), td.material()),
                        orDefault(trans.string("name"), td.name()),
                        trans.has("lore") ? trans.stringList("lore") : td.lore(),
                        orDefault(trans.string("name-suffix"), td.nameSuffix()),
                        orDefault(trans.string("message-success"), td.messageSuccess()),
                        orDefault(trans.string("message-no-enchants"), td.messageNoEnchants())),
                new ScrollsConfig.Holy(
                        orDefault(holy.string("material"), hd.material()),
                        orDefault(holy.string("name"), hd.name()),
                        holy.has("lore") ? holy.stringList("lore") : hd.lore(),
                        parseInt(holy.string("save-chance"), hd.saveChance(), root, diags),
                        orDefault(holy.string("message-saved"), hd.messageSaved())),
                new ScrollsConfig.Nametag(
                        orDefault(tag.string("material"), nd.material()),
                        orDefault(tag.string("name"), nd.name()),
                        tag.has("lore") ? tag.stringList("lore") : nd.lore(),
                        tag.has("blacklist") ? tag.stringList("blacklist") : nd.blacklist(),
                        orDefault(tag.string("message-prompt"), nd.messagePrompt()),
                        orDefault(tag.string("message-renamed"), nd.messageRenamed()),
                        orDefault(tag.string("message-blacklisted"), nd.messageBlacklisted()),
                        orDefault(tag.string("message-cancelled"), nd.messageCancelled())));
    }

    private static UnopenedBookConfig readUnopenedBook(YamlNode root, Diagnostics diags) {
        UnopenedBookConfig d = UnopenedBookConfig.defaults();
        return new UnopenedBookConfig(
                orDefault(root.string("material"), d.material()),
                orDefault(root.string("name"), d.name()),
                root.has("lore") ? root.stringList("lore") : d.lore(),
                parseInt(root.string("min-success"), d.minSuccess(), root, diags),
                parseInt(root.string("max-success"), d.maxSuccess(), root, diags),
                orDefault(root.string("message-open"), d.messageOpen()),
                orDefault(root.string("message-empty-tier"), d.messageEmptyTier()));
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
        YamlNode sounds = root.child("sounds");
        YamlNode particles = root.child("particles");
        return new SoulGemConfig(
                orDefault(root.string("material"), d.material()),
                orDefault(root.string("name"), d.name()),
                root.has("lore") ? root.stringList("lore") : d.lore(),
                parseInt(root.string("souls-per-kill"), d.soulsPerKill(), root, diags),
                readSoulsPerMob(root, diags),
                readColorTiers(root, diags, d.colorTiers()),
                orDefault(root.string("empty-soul-color"), d.emptyColor()),
                root.has("sounds") && sounds.has("enabled")
                        ? !"false".equalsIgnoreCase(sounds.string("enabled")) : d.sounds(),
                orDefault(sounds.string("activate"), d.soundActivate()),
                orDefault(sounds.string("deactivate"), d.soundDeactivate()),
                orDefault(sounds.string("combine"), d.soundCombine()),
                particles.has("active") ? particles.stringList("active") : d.particlesActive(),
                particles.has("on-activate") ? particles.stringList("on-activate") : d.particlesActivate(),
                particles.has("on-deactivate") ? particles.stringList("on-deactivate") : d.particlesDeactivate(),
                orDefault(root.string("message-activate"), d.messageActivate()),
                orDefault(root.string("message-deactivate"), d.messageDeactivate()),
                orDefault(root.string("message-soul-use"), d.messageSoulUse()));
    }

    /** Parse the optional {@code souls-per-mob:} map ({@code ENTITY_TYPE: amount}); a bad amount warns + skips. */
    private static Map<String, Integer> readSoulsPerMob(YamlNode root, Diagnostics diags) {
        if (!root.has("souls-per-mob")) {
            return Map.of();
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (YamlNode.Entry entry : root.entries("souls-per-mob")) {
            String raw = entry.value().scalar();
            if (raw == null) {
                continue;
            }
            try {
                out.put(entry.key(), Integer.parseInt(raw.trim()));
            } catch (NumberFormatException bad) {
                diags.warning("W_SOUL_MOB", "souls-per-mob[" + entry.key() + "] is not a number: " + raw,
                        entry.value().source());
            }
        }
        return out;
    }

    /** Parse the optional {@code soul-colors:} map ({@code min: &color}); empty/all-bad falls back to {@code fallback}. */
    private static List<SoulGemConfig.ColorTier> readColorTiers(YamlNode root, Diagnostics diags,
            List<SoulGemConfig.ColorTier> fallback) {
        if (!root.has("soul-colors")) {
            return fallback;
        }
        List<SoulGemConfig.ColorTier> out = new ArrayList<>();
        for (YamlNode.Entry entry : root.entries("soul-colors")) {
            String color = entry.value().scalar();
            if (color == null) {
                continue;
            }
            try {
                out.add(new SoulGemConfig.ColorTier(Integer.parseInt(entry.key().trim()), color));
            } catch (NumberFormatException bad) {
                diags.warning("W_SOUL_TIER", "soul-colors key is not a number: " + entry.key(),
                        entry.value().source());
            }
        }
        return out.isEmpty() ? fallback : out;
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
