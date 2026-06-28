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
 * Loads the top-level {@code items/} folder into an immutable {@link ItemsConfig} — each {@code items/*.yml}
 * dispatched by its {@code type} field to the matching reader. Config only, never reaches the compiler. Never
 * throws: a missing/unreadable/malformed input is empty or skipped (runtime falls back to built-in defaults).
 */
public final class ItemsLoader {

    private ItemsLoader() {
    }

    public static ItemsConfig load(Path itemsRoot) {
        Diagnostics diags = new Diagnostics();
        Optional<SoulGemConfig> soulGem = Optional.empty();
        Optional<CrystalConfig> crystal = Optional.empty();
        Optional<HeroicConfig> heroic = Optional.empty();
        Optional<SlotConfig> slots = Optional.empty();
        Optional<UnopenedBookConfig> unopenedBook = Optional.empty();
        Optional<EnchantBookConfig> enchantBook = Optional.empty();
        Optional<DustConfig> dust = Optional.empty();
        Optional<WhiteScrollConfig> whiteScroll = Optional.empty();
        // Scroll family: each member is its own file, assembled into one ScrollsConfig below (§I).
        Optional<ScrollsConfig.Black> black = Optional.empty();
        Optional<ScrollsConfig.Randomizer> randomizer = Optional.empty();
        Optional<ScrollsConfig.Transmog> transmog = Optional.empty();
        Optional<ScrollsConfig.Holy> holy = Optional.empty();
        Optional<ScrollsConfig.Nametag> nametag = Optional.empty();
        Optional<ScrollsConfig.Godly> godly = Optional.empty();
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
                case "slot-orb", "slots", "slot", "slot-expander" -> {
                    if (slots.isPresent()) {
                        diags.warning("W_ITEM_DUP", "more than one slots config (" + name + "); keeping the first",
                                root.source());
                    } else {
                        slots = Optional.of(readSlots(root, diags));
                    }
                }
                case "black-scroll", "black" -> {
                    if (black.isPresent()) {
                        diags.warning("W_ITEM_DUP", "more than one black-scroll config (" + name + "); keeping the first",
                                root.source());
                    } else {
                        black = Optional.of(readBlack(root, diags));
                    }
                }
                case "randomizer-scroll", "randomizer" -> {
                    if (randomizer.isPresent()) {
                        diags.warning("W_ITEM_DUP", "more than one randomizer-scroll config (" + name + "); keeping the first",
                                root.source());
                    } else {
                        randomizer = Optional.of(readRandomizer(root, diags));
                    }
                }
                case "transmog-scroll", "transmog" -> {
                    if (transmog.isPresent()) {
                        diags.warning("W_ITEM_DUP", "more than one transmog-scroll config (" + name + "); keeping the first",
                                root.source());
                    } else {
                        transmog = Optional.of(readTransmog(root, diags));
                    }
                }
                case "holy-white-scroll", "holy-scroll", "holy" -> {
                    if (holy.isPresent()) {
                        diags.warning("W_ITEM_DUP", "more than one holy-white-scroll config (" + name + "); keeping the first",
                                root.source());
                    } else {
                        holy = Optional.of(readHoly(root, diags));
                    }
                }
                case "nametag", "item-nametag" -> {
                    if (nametag.isPresent()) {
                        diags.warning("W_ITEM_DUP", "more than one nametag config (" + name + "); keeping the first",
                                root.source());
                    } else {
                        nametag = Optional.of(readNametag(root, diags));
                    }
                }
                case "godly-transmog", "godly" -> {
                    if (godly.isPresent()) {
                        diags.warning("W_ITEM_DUP", "more than one godly-transmog config (" + name + "); keeping the first",
                                root.source());
                    } else {
                        godly = Optional.of(readGodly(root, diags));
                    }
                }
                case "dust", "success-dust" -> {
                    if (dust.isPresent()) {
                        diags.warning("W_ITEM_DUP", "more than one dust config (" + name + "); keeping the first",
                                root.source());
                    } else {
                        dust = Optional.of(readDust(root, diags));
                    }
                }
                case "white-scroll", "protect-scroll", "protect" -> {
                    if (whiteScroll.isPresent()) {
                        diags.warning("W_ITEM_DUP", "more than one white-scroll config (" + name + "); keeping the first",
                                root.source());
                    } else {
                        whiteScroll = Optional.of(readWhiteScroll(root, diags));
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
                case "enchant-book", "book" -> {
                    if (enchantBook.isPresent()) {
                        diags.warning("W_ITEM_DUP", "more than one enchant-book config (" + name + "); keeping the first",
                                root.source());
                    } else {
                        enchantBook = Optional.of(readEnchantBook(root, diags));
                    }
                }
                default -> diags.warning("W_ITEM_TYPE", "unknown item type '" + type + "' in " + name, root.source());
            }
        }
        // Present iff at least one scroll-family file was read; absent members fall back to ScrollsConfig.defaults().
        ScrollsConfig sd = ScrollsConfig.defaults();
        Optional<ScrollsConfig> scrolls;
        if (black.isPresent() || randomizer.isPresent() || transmog.isPresent()
                || holy.isPresent() || nametag.isPresent() || godly.isPresent()) {
            scrolls = Optional.of(new ScrollsConfig(
                    black.orElseGet(sd::black), randomizer.orElseGet(sd::randomizer),
                    transmog.orElseGet(sd::transmog), holy.orElseGet(sd::holy),
                    nametag.orElseGet(sd::nametag), godly.orElseGet(sd::godly)));
        } else {
            scrolls = Optional.empty();
        }
        return new ItemsConfig(soulGem, crystal, heroic, slots, scrolls, unopenedBook, enchantBook,
                dust, whiteScroll, diags.all());
    }

    private static ScrollsConfig.Black readBlack(YamlNode root, Diagnostics diags) {
        ScrollsConfig.Black d = ScrollsConfig.defaults().black();
        return new ScrollsConfig.Black(
                orDefault(root.string("material"), d.material()),
                orDefault(root.string("name"), d.name()),
                root.has("lore") ? root.stringList("lore") : d.lore(),
                parseInt(root.string("min-convert"), d.minConvert(), root, diags),
                parseInt(root.string("max-convert"), d.maxConvert(), root, diags));
    }

    private static ScrollsConfig.Randomizer readRandomizer(YamlNode root, Diagnostics diags) {
        ScrollsConfig.Randomizer d = ScrollsConfig.defaults().randomizer();
        return new ScrollsConfig.Randomizer(
                orDefault(root.string("material"), d.material()),
                orDefault(root.string("name"), d.name()),
                root.has("lore") ? root.stringList("lore") : d.lore(),
                parseInt(root.string("min-percent"), d.minPercent(), root, diags),
                parseInt(root.string("max-percent"), d.maxPercent(), root, diags));
    }

    private static ScrollsConfig.Transmog readTransmog(YamlNode root, Diagnostics diags) {
        ScrollsConfig.Transmog d = ScrollsConfig.defaults().transmog();
        return new ScrollsConfig.Transmog(
                orDefault(root.string("material"), d.material()),
                orDefault(root.string("name"), d.name()),
                root.has("lore") ? root.stringList("lore") : d.lore(),
                orDefault(root.string("name-suffix"), d.nameSuffix()));
    }

    private static ScrollsConfig.Holy readHoly(YamlNode root, Diagnostics diags) {
        ScrollsConfig.Holy d = ScrollsConfig.defaults().holy();
        return new ScrollsConfig.Holy(
                orDefault(root.string("material"), d.material()),
                orDefault(root.string("name"), d.name()),
                root.has("lore") ? root.stringList("lore") : d.lore(),
                parseInt(root.string("min-success"), d.minSuccess(), root, diags),
                parseInt(root.string("max-success"), d.maxSuccess(), root, diags));
    }

    private static ScrollsConfig.Nametag readNametag(YamlNode root, Diagnostics diags) {
        ScrollsConfig.Nametag d = ScrollsConfig.defaults().nametag();
        return new ScrollsConfig.Nametag(
                orDefault(root.string("material"), d.material()),
                orDefault(root.string("name"), d.name()),
                root.has("lore") ? root.stringList("lore") : d.lore(),
                root.has("blacklist") ? root.stringList("blacklist") : d.blacklist());
    }

    private static ScrollsConfig.Godly readGodly(YamlNode root, Diagnostics diags) {
        ScrollsConfig.Godly d = ScrollsConfig.defaults().godly();
        return new ScrollsConfig.Godly(
                orDefault(root.string("material"), d.material()),
                orDefault(root.string("name"), d.name()),
                root.has("lore") ? root.stringList("lore") : d.lore());
    }

    private static DustConfig readDust(YamlNode root, Diagnostics diags) {
        DustConfig d = DustConfig.defaults();
        // `success-bonus` is a shorthand for a FIXED dust (min == max); explicit min-bonus/max-bonus override it.
        int legacy = parseInt(root.string("success-bonus"), -1, root, diags);
        int min = root.has("min-bonus") ? parseInt(root.string("min-bonus"), d.minBonus(), root, diags)
                : (legacy >= 0 ? legacy : d.minBonus());
        int max = root.has("max-bonus") ? parseInt(root.string("max-bonus"), d.maxBonus(), root, diags)
                : (legacy >= 0 ? legacy : d.maxBonus());
        return new DustConfig(
                orDefault(root.string("material"), d.material()),
                orDefault(root.string("name"), d.name()),
                root.has("lore") ? root.stringList("lore") : d.lore(),
                min, max,
                orDefault(root.string("sound"), d.sound()),
                root.has("particles") ? root.stringList("particles") : d.particles());
    }

    private static WhiteScrollConfig readWhiteScroll(YamlNode root, Diagnostics diags) {
        WhiteScrollConfig d = WhiteScrollConfig.defaults();
        return new WhiteScrollConfig(
                orDefault(root.string("material"), d.material()),
                orDefault(root.string("name"), d.name()),
                root.has("lore") ? root.stringList("lore") : d.lore(),
                parseInt(root.string("min-success"), d.minSuccess(), root, diags),
                parseInt(root.string("max-success"), d.maxSuccess(), root, diags));
    }

    private static EnchantBookConfig readEnchantBook(YamlNode root, Diagnostics diags) {
        EnchantBookConfig d = EnchantBookConfig.defaults();
        return new EnchantBookConfig(
                orDefault(root.string("material"), d.material()),
                orDefault(root.string("name"), d.name()),
                root.has("lore") ? root.stringList("lore") : d.lore(),
                root.has("success-lore") ? root.stringList("success-lore") : d.successLore(),
                root.has("destroy-on-fail")
                        ? "true".equalsIgnoreCase(root.string("destroy-on-fail")) : d.destroyOnFail(),
                parseInt(root.string("wrap"), d.wrap(), root, diags)); // {DESCRIPTION} word-wrap width
    }

    private static UnopenedBookConfig readUnopenedBook(YamlNode root, Diagnostics diags) {
        UnopenedBookConfig d = UnopenedBookConfig.defaults();
        return new UnopenedBookConfig(
                orDefault(root.string("material"), d.material()),
                orDefault(root.string("name"), d.name()),
                root.has("lore") ? root.stringList("lore") : d.lore(),
                parseInt(root.string("min-success"), d.minSuccess(), root, diags),
                parseInt(root.string("max-success"), d.maxSuccess(), root, diags));
    }

    private static SlotConfig readSlots(YamlNode root, Diagnostics diags) {
        SlotConfig d = SlotConfig.defaults();
        return new SlotConfig(
                orDefault(root.string("orb-material"), d.orbMaterial()),
                orDefault(root.string("orb-name"), d.orbName()),
                root.has("orb-lore") ? root.stringList("orb-lore") : d.orbLore(),
                parseInt(root.string("orb-amount"), d.orbAmount(), root, diags),
                parseInt(root.string("hard-cap"), d.hardCap(), root, diags),
                parseInt(root.string("min-success"), d.minSuccess(), root, diags),
                parseInt(root.string("max-success"), d.maxSuccess(), root, diags));
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
                orDefault(root.string("reduction-scope"), d.reductionScope()));
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
        YamlNode sounds = root.child("sounds");
        YamlNode extractor = root.child("extractor");
        return new CrystalConfig(
                orDefault(root.string("material"), d.material()),
                orDefault(root.string("name"), d.name()),
                root.has("lore") ? root.stringList("lore") : d.lore(),
                parseInt(root.string("success-chance"), d.successChance(), root, diags),
                root.has("consume-on-fail")
                        ? "true".equalsIgnoreCase(root.string("consume-on-fail")) : d.consumeOnFail(),
                root.has("sounds") && sounds.has("enabled")
                        ? !"false".equalsIgnoreCase(sounds.string("enabled")) : d.sounds(),
                orDefault(sounds.string("apply"), d.soundApply()),
                orDefault(sounds.string("remove"), d.soundRemove()),
                orDefault(extractor.string("material"), d.extractorMaterial()),
                orDefault(extractor.string("name"), d.extractorName()),
                extractor.has("lore") ? extractor.stringList("lore") : d.extractorLore());
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
                particles.has("on-deactivate") ? particles.stringList("on-deactivate") : d.particlesDeactivate());
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
