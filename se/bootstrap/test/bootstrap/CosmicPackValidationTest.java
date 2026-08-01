package bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import compile.Compiler;
import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.load.EnchantDef;
import compile.load.ItemsConfig;
import compile.load.ItemsLoader;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.MasterConfig;
import compile.load.MasterConfigLoader;
import compile.load.MenusConfig;
import compile.load.MenusLoader;
import compile.resolve.PlatformResolvers;
import engine.boot.ContentCompiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import engine.trigger.BuiltinTriggers;
import platform.resolve.Aliases;
import platform.resolve.HandleResolver;
import schema.diag.Diagnostic;
import schema.spec.HandleCategory;

/**
 * The shipped {@code cosmic-pack} config pack (ADR-0023) must compile clean through the real
 * registries, like {@link CatalogValidationTest} guards the default catalog — so a broken pack port can
 * never ship.
 *
 * <p>Unlike the default-catalog test, handle tokens here resolve <em>strictly</em>: each material/sound/
 * particle/entity/attribute token must exist in the floor ({@code 1.17.1}) Bukkit enums — through the
 * production {@link HandleResolver} + {@link Aliases}, exactly as the runtime resolves them. This is what
 * turns "the EE port loaded an EE-only token that no server has" (e.g. the {@code BLEED} particle, the
 * pre-flattening {@code ENDERDRAGON_GROWL} sound) from a silent runtime {@code E_UNKNOWN_HANDLE} on every
 * enchant into an offline build failure. Floor enums are the strictest universe (shipped content must run on
 * the floor too), and they are plain enums on 1.17.1 so resolution needs no server. Registry-backed handles
 * (potion effects, enchantments) stay permissive offline — their existence is owned by the live matrix.
 */
class CosmicPackValidationTest {

    private static final PlatformResolvers STRICT = new PlatformResolvers() {
        @Override public OptionalInt material(String t) { return strict(HandleCategory.MATERIAL, t, n -> enumExists(Material.class, n)); }
        @Override public OptionalInt sound(String t) { return strict(HandleCategory.SOUND, t, n -> enumExists(Sound.class, n)); }
        @Override public OptionalInt particle(String t) { return strict(HandleCategory.PARTICLE, t, n -> enumExists(Particle.class, n)); }
        @Override public OptionalInt entityType(String t) { return strict(HandleCategory.ENTITY_TYPE, t, n -> enumExists(EntityType.class, n)); }
        @Override public OptionalInt attribute(String t) { return strict(HandleCategory.ATTRIBUTE, t, n -> enumExists(Attribute.class, n)); }
        // Registry-backed handles can't be enumerated without a live server → permissive offline, live-owned.
        @Override public OptionalInt potionEffect(String t) { return OptionalInt.of(0); }
        @Override public OptionalInt enchantment(String t) { return OptionalInt.of(0); }
    };

    /** Resolve {@code token} the way the runtime does, but against the given floor-enum existence test. */
    private static OptionalInt strict(HandleCategory category, String token, Predicate<String> exists) {
        return HandleResolver.resolve(token, Aliases.forCategory(category), exists).isPresent()
                ? OptionalInt.of(0)
                : OptionalInt.empty();
    }

    private static <E extends Enum<E>> boolean enumExists(Class<E> type, String name) {
        try {
            Enum.valueOf(type, name);
            return true;
        } catch (IllegalArgumentException notAConstant) {
            return false;
        }
    }

    private static final Path PACK = Path.of("packs-src/cosmic-pack");

    @Test
    void cosmicPackContentCompilesClean() {
        Path content = PACK.resolve("content");
        assertTrue(Files.isDirectory(content), "Cosmic pack content not found from " + Path.of("").toAbsolutePath());

        Compiler compiler = ContentCompiler.production(STRICT);
        Library library = LibraryLoader.load(content, compiler, 0);

        String blocking = library.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(library.hasErrors(), () -> "Cosmic pack content has blocking diagnostics:\n  " + blocking);
        assertTrue(library.catalog().size() == 194,
                () -> "expected exactly 194 registered Cosmic enchants, got " + library.catalog().size());
        assertSourceCatalogParity(library);
        assertCosmicEndTierGate(library, content);
        assertEquals(List.of("PICKAXE"), library.enchantDefOf("enchants/fuse").appliesTo(),
                "Fuse applies only to Cosmic's five pickaxes");
        assertEquals(List.of("SWORD", "AXE", "BOW"), library.enchantDefOf("enchants/rage").appliesTo(),
                "Rage uses Cosmic GeneralUtil.weapons, not StarEnchants' broader WEAPON group");
        assertEquals(List.of("SWORD"), library.enchantDefOf("enchants/solitude").appliesTo(),
                "Solitude applies only to swords");
        assertEquals(List.of("SWORD", "BOW"), library.enchantDefOf("enchants/silence").appliesTo(),
                "Silence uses Cosmic swords_and_bow without axes or modern weapon types");
        List<String> cosmicTools = List.of("PICKAXE", "HOE", "SHOVEL", "AXE", "FISHING_ROD");
        for (String enchant : List.of("auto-sell", "atomic-detonate", "detonate", "haste",
                "skilling", "oxygenate", "telepathy", "experience")) {
            assertEquals(cosmicTools, library.enchantDefOf("enchants/" + enchant).appliesTo(),
                    enchant + " uses Cosmic GeneralUtil.tools");
        }
        List<String> cosmicWeapons = List.of("SWORD", "AXE", "BOW");
        for (String enchant : List.of("rage", "obliterate", "training", "shackle")) {
            assertEquals(cosmicWeapons, library.enchantDefOf("enchants/" + enchant).appliesTo(),
                    enchant + " uses Cosmic GeneralUtil.weapons");
        }
        assertEquals(List.of("SWORD", "AXE", "BOW", "PICKAXE", "HOE", "SHOVEL"),
                library.enchantDefOf("enchants/reforged").appliesTo(),
                "Reforged uses Cosmic GeneralUtil.weapons_and_tools without fishing rods");
        assertTrue(library.sets().size() == 12,
                () -> "expected exactly 12 Cosmic armor sets, got " + library.sets().size());
        assertTrue(library.masks().size() == 27,
                () -> "expected exactly 27 Cosmic masks, got " + library.masks().size());
        assertTrue(library.pets().size() == 17,
                () -> "expected exactly 17 Cosmic pets, got " + library.pets().size());
        // 194 enchants × multiple levels — guard against a silent empty/partial load.
        assertTrue(library.snapshot().abilityCount() > 500,
                () -> "expected the full Cosmic catalog, got " + library.snapshot().abilityCount() + " abilities");

        Ability commander = library.snapshot().byStableKey("enchants/commander/1");
        assertEquals(160, commander.repeatTicks(), "Commander period");
        assertEquals(20, commander.repeatInitialDelayTicks(), "Commander source first delay");

        int[] destructionPeriods = {300, 150, 100, 75, 60};
        for (int level = 1; level <= destructionPeriods.length; level++) {
            Ability destruction = library.snapshot().byStableKey("enchants/destruction/" + level);
            assertEquals(destructionPeriods[level - 1], destruction.repeatTicks(),
                    "Destruction " + level + " intended period");
            assertEquals(destructionPeriods[level - 1], destruction.repeatInitialDelayTicks(),
                    "Destruction " + level + " intended first delay");
        }

        int[] implantFoodPeriods = {120, 60, 40};
        int[] implantHealthPeriods = {240, 120, 80};
        for (int level = 1; level <= implantFoodPeriods.length; level++) {
            Ability food = library.snapshot().byStableKey("enchants/implants/" + level);
            Ability health = library.snapshot().byStableKey("enchants/implants/" + level + "/a1");
            assertEquals(implantFoodPeriods[level - 1], food.repeatTicks(),
                    "Implants " + level + " intended food period");
            assertEquals(implantFoodPeriods[level - 1], food.repeatInitialDelayTicks(),
                    "Implants " + level + " intended food first delay");
            assertEquals(implantHealthPeriods[level - 1], health.repeatTicks(),
                    "Implants " + level + " intended health period");
            assertEquals(implantHealthPeriods[level - 1], health.repeatInitialDelayTicks(),
                    "Implants " + level + " intended health first delay");

            Ability alienFood = library.snapshot().byStableKey("enchants/alien-implants/" + level);
            Ability alienHealth = library.snapshot().byStableKey("enchants/alien-implants/" + level + "/a1");
            assertEquals(implantFoodPeriods[level - 1], alienFood.repeatTicks(),
                    "Alien Implants " + level + " intended food period");
            assertEquals(implantHealthPeriods[level - 1], alienHealth.repeatTicks(),
                    "Alien Implants " + level + " intended health period");
        }

        int[] protectionPeriods = {200, 100, 66, 50, 40};
        for (int level = 1; level <= protectionPeriods.length; level++) {
            Ability protection = library.snapshot().byStableKey("enchants/protection/" + level);
            assertEquals(protectionPeriods[level - 1], protection.repeatTicks(),
                    "Protection " + level + " intended period");
            assertEquals(protectionPeriods[level - 1], protection.repeatInitialDelayTicks(),
                    "Protection " + level + " intended first delay");
        }

        for (int level = 1; level <= 4; level++) {
            Ability immortal = library.snapshot().byStableKey("enchants/immortal/" + level);
            assertEquals(0, immortal.soulCost(), "Immortal's all-damage native listener owns soul spending");
            assertEquals(0, immortal.effects().length, "Immortal must not double-fire through DEFENSE YAML");
            assertEquals(0, immortal.noSoulEffects().length, "Immortal's native listener owns no-souls feedback");
        }

        int attack = BuiltinTriggers.registry().idOf("ATTACK").orElseThrow();
        int bow = BuiltinTriggers.registry().idOf("BOW").orElseThrow();
        int trident = BuiltinTriggers.registry().idOf("TRIDENT").orElseThrow();
        int repeating = BuiltinTriggers.registry().idOf("REPEATING").orElseThrow();
        for (int level = 1; level <= 5; level++) {
            Ability leadershipCount = library.snapshot().byStableKey("enchants/leadership/" + level);
            Ability leadershipFeedback = library.snapshot().byStableKey("enchants/leadership/" + level + "/a1");
            Ability leadershipNormal = library.snapshot().byStableKey("enchants/leadership/" + level + "/a2");
            Ability leadershipSpecial = library.snapshot().byStableKey("enchants/leadership/" + level + "/a3");
            assertEquals(120, leadershipCount.repeatTicks(), "Leadership " + level + " sample period");
            assertEquals(20, leadershipCount.repeatInitialDelayTicks(), "Leadership " + level + " first sample");
            assertTrue(leadershipCount.firesOn(repeating), "Leadership count must be repeating");
            assertEquals("COUNT_TARGETS", leadershipCount.effects()[0].head(), "Leadership count primitive");
            assertEquals("cosmic-leadership-allies", leadershipCount.effects()[0].args().str("name"));
            assertEquals(120, leadershipFeedback.repeatTicks(), "Leadership feedback cadence");
            assertEquals(20, leadershipFeedback.repeatInitialDelayTicks(), "Leadership feedback first pulse");
            assertEquals("BLOCK_BREAK_EFFECT", leadershipFeedback.effects()[0].head());
            assertTrue(leadershipFeedback.effects()[0].args().bool("once-at-actor"));
            for (Ability damage : List.of(leadershipNormal, leadershipSpecial)) {
                assertTrue(damage.firesOn(attack), "Leadership damage must fire on direct melee");
                assertFalse(damage.firesOn(bow), "Leadership source listener excludes projectiles");
                assertFalse(damage.firesOn(trident), "Leadership source listener excludes projectiles");
                assertEffectDouble(damage, "DAMAGE_MOD", "cap", 75.0);
            }
        }

        try {
            String destruction = Files.readString(content.resolve("enchants/destruction.yml"));
            String leadership = Files.readString(content.resolve("enchants/leadership.yml"));
            assertFalse(destruction.contains("filter=ENEMIES"),
                    "Destruction must scan enemy players only, not hostile mobs");
            assertTrue(destruction.contains("filter=ENEMY_PLAYERS"),
                    "Destruction must retain its enemy-player area selector");
            assertTrue(leadership.contains("matchesregex \"^cosmic-station.*\""),
                    "Leadership must preserve source startsWith(cosmic-station), not substring matching");
        } catch (java.io.IOException unreadable) {
            throw new AssertionError("could not read Cosmic area definitions", unreadable);
        }

        Ability sniperTwo = library.snapshot().byStableKey("enchants/sniper/2");
        assertEquals(50.0, sniperTwo.baseChance(), "Sniper II code-side chance");
        assertEffectDouble(sniperTwo, "DAMAGE_MOD", "amount", 1.8399999999999999);
        assertEquals("&c&l*** HEADSHOT [+1.8399999999999999x DMG] ***",
                effect(sniperTwo, "MESSAGE").args().str("text"), "Sniper II raw code-side message");
        Ability sniperFour = library.snapshot().byStableKey("enchants/sniper/4");
        assertEquals(64.99999999999999, sniperFour.baseChance(), "Sniper IV code-side chance");
        assertEffectDouble(sniperFour, "DAMAGE_MOD", "amount", 2.6799999999999997);
        assertEquals("&c&l*** HEADSHOT [+2.6799999999999997x DMG] ***",
                effect(sniperFour, "MESSAGE").args().str("text"), "Sniper IV raw code-side message");

        double[] masterBlacksmithMultipliers = {0.80, 0.85, 0.90, 0.95, 1.0};
        for (int level = 1; level <= masterBlacksmithMultipliers.length; level++) {
            Ability masterBlacksmith = library.snapshot().byStableKey("enchants/master-blacksmith/" + level);
            assertEquals(12.5 * level, masterBlacksmith.baseChance(),
                    "Master Blacksmith " + level + " code-side chance");
            assertEffectDouble(masterBlacksmith, "DAMAGE_MOD", "amount",
                    masterBlacksmithMultipliers[level - 1]);
        }

        int mine = BuiltinTriggers.registry().idOf("MINE").orElseThrow();
        int blockDamage = BuiltinTriggers.registry().idOf("BLOCK_DAMAGE").orElseThrow();
        for (int level = 1; level <= 3; level++) {
            Ability breakHaste = library.snapshot().byStableKey("enchants/haste/" + level);
            Ability damageHaste = library.snapshot().byStableKey("enchants/haste/" + level + "/a1");
            assertTrue(breakHaste.firesOn(mine), "Haste " + level + " must fire on block break");
            assertTrue(damageHaste.firesOn(blockDamage), "Haste " + level + " must fire on block damage");
            assertPotion(breakHaste, 40, level, true);
            assertPotion(damageHaste, 40, level, true);
        }
        assertPotion(library.snapshot().byStableKey("enchants/obsidianshield/1"),
                Integer.MAX_VALUE, 1, false);
        for (int level = 1; level <= 3; level++) {
            assertPotion(library.snapshot().byStableKey("enchants/overload/" + level),
                    Integer.MAX_VALUE, level, false);
            assertPotion(library.snapshot().byStableKey("enchants/godly-overload/" + level),
                    Integer.MAX_VALUE, level + 3, false);
            assertPotion(library.snapshot().byStableKey("enchants/springs/" + level),
                    Integer.MAX_VALUE, level, false);
        }
        for (int level = 1; level <= 2; level++) {
            assertDamageUnattributed(library.snapshot().byStableKey("enchants/cactus/" + level));
        }
        for (int level = 1; level <= 5; level++) {
            assertDamageUnattributed(library.snapshot().byStableKey("enchants/destruction/" + level));
        }
        for (int level = 1; level <= 3; level++) {
            Ability enderShift = library.snapshot().byStableKey("enchants/ender-shift/" + level);
            List<Boolean> forceFlags = java.util.Arrays.stream(enderShift.effects())
                    .filter(effect -> effect.head().equals("POTION"))
                    .map(effect -> effect.args().bool("force"))
                    .toList();
            assertEquals(List.of(false, true, true, false), forceFlags,
                    "Ender Shift " + level + " must only force-overwrite Speed and Jump");
        }

        try {
            String barbarian = Files.readString(content.resolve("enchants/barbarian.yml"));
            String tank = Files.readString(content.resolve("enchants/tank.yml"));
            assertFalse(barbarian.contains("contains \"_AXE\""), "Barbarian must not match pickaxes");
            assertFalse(tank.contains("contains \"_AXE\""), "Tank must not match pickaxes");
            assertTrue(barbarian.contains("WOOD_AXE") && barbarian.contains("WOODEN_AXE")
                    && barbarian.contains("NETHERITE_AXE"), "Barbarian must cover legacy and modern axes");
            assertTrue(tank.contains("WOOD_AXE") && tank.contains("WOODEN_AXE")
                    && tank.contains("NETHERITE_AXE"), "Tank must cover legacy and modern axes");
        } catch (java.io.IOException unreadable) {
            throw new AssertionError("could not read Cosmic axe definitions", unreadable);
        }

        assertPet(library, "lava-elemental", 10,
                List.of(2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000),
                java.util.Collections.nCopies(10, 6000));
        assertPet(library, "water-elemental", 10,
                List.of(1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000),
                java.util.Collections.nCopies(10, 6000));
        assertPet(library, "feign-death", 4, List.of(1500, 2250, 3000),
                java.util.Collections.nCopies(4, 12000));
        assertPet(library, "evolution", 10, java.util.Collections.nCopies(9, 1000),
                java.util.Collections.nCopies(10, 3456000));
        assertPet(library, "anti-teleblock", 10, java.util.Collections.nCopies(9, 1000),
                List.of(2400, 2280, 2160, 2040, 1920, 1800, 1680, 1560, 1440, 1320));
        assertPet(library, "banner", 10, java.util.Collections.nCopies(9, 1000),
                List.of(14400, 13200, 12000, 10800, 9600, 8400, 7200, 6000, 4800, 3600));
        assertPet(library, "xp-booster", 10,
                List.of(200, 300, 400, 500, 600, 700, 800, 900, 1000),
                java.util.Collections.nCopies(10, 72000));
        assertPet(library, "tesla", 10,
                List.of(3000, 4500, 6000, 7500, 9000, 10500, 12000, 13500, 15000),
                java.util.Collections.nCopies(10, 6000));
        assertPet(library, "blackscroll", 10,
                List.of(2250, 3250, 4250, 5250, 6250, 7250, 8250, 9250, 10250),
                java.util.Collections.nCopies(10, 18000));
        assertPet(library, "alchemist", 10,
                List.of(1500, 1750, 2000, 2250, 2500, 2750, 3000, 3250, 3500),
                java.util.Collections.nCopies(10, 864000));
        assertPet(library, "gaia", 4, List.of(2000, 2500, 3000),
                java.util.Collections.nCopies(4, 12000));
        assertPet(library, "enchanter", 10,
                List.of(2250, 3250, 4250, 5250, 6250, 7250, 8250, 9250, 10250),
                java.util.Collections.nCopies(10, 72000));
        assertPet(library, "stronghold-sell", 10, java.util.Collections.nCopies(9, 500),
                java.util.Collections.nCopies(10, 36000));
        assertPet(library, "raid-creeper", 5, List.of(5000, 7500, 10000, 12500),
                List.of(9600, 8400, 7200, 6000, 4800));
        assertPet(library, "vile-creeper", 5, List.of(5000, 7500, 10000, 12500),
                List.of(9600, 8400, 7200, 6000, 4800));
        assertPet(library, "smite", 5, List.of(2000, 3000, 4000, 5000),
                java.util.Collections.nCopies(5, 2400));
        assertPet(library, "world-destroyer", 4, List.of(2000, 2500, 3000),
                java.util.Collections.nCopies(4, 12000));

        assertMask(library, "headless-mask", "", List.of(
                "&7A terrifying Mask from the", "&7grave of the Headless Horseman.",
                "&6&l * &6October 2018"));
        assertMask(library, "purge-mask", "+2.5% DMG", List.of(
                "&c+2.5% DMG", "&7A great evil is contained within this",
                "&7horrifying mask. Who knows what inner", "&7demons it will unleash.",
                "&6&l * &6Halloween 2018"));
        assertMask(library, "pumpkin-monster", "", List.of(
                "&7The severed head of a fabled Halloween",
                "&7nightmare known as the Pumpkin Monster", "&6&l * &6Halloween Quests"));
        assertMask(library, "ghost-mask", "", List.of(
                "&7The deviant mask of the spectre,", "&7a metaphysical monstrosity.",
                "&6&l * &6Ghost Mastery Kit"));
        assertMask(library, "scarecrow-mask", "Infinite Food", List.of(
                "&cInfinite Food", "&7An empty husk of a", "&7once tortured soul left to rot.",
                "&6&l * &6November 2018 CC"));
        assertMask(library, "turkey-mask", "+2% Dodge", List.of(
                "&c+2% Dodge", "&7Stay nimble and fast, or the",
                "&7Pilgrims will catch and cook you!", "&6&l * &6Thanksgiving 2018"));
        assertMask(library, "pilgrim-mask", "+25% XP/Drops", List.of(
                "&c+25% XP/Drops", "&7tHiS iS oUr lAnD nOw!", "&6&l * &6Thanksgiving 2018"));
        assertMask(library, "monopoly-mask", "33% Holy White Scroll negation", List.of(
                "&c33% Holy White Scroll negation", "&c-5% ENEMY DMG",
                "&7The mask of a man who has it all,", "&7a truly powerful entity to contest with.",
                "&6&l * &6Black Friday 2018"));
        assertMask(library, "necromancer-mask", "Immune to Lifesteal", List.of(
                "&cImmune to Lifesteal", "&7An enchanted skull conjured from",
                "&7the depths of the underworld.", "&6&l * &6Necromancer Mastery Kit"));
        assertMask(library, "dragon-mask", "+5% DMG", List.of(
                "&c+5% DMG", "&cImmune to Fire and Lava damage",
                "&7The decapitated skull of a slain", "&7Timeless Dragon from the Ender Dimension.",
                "&6&l * &6Timeless Dragon Update"));
        assertMask(library, "santa", "+2 Max Hearts", List.of(
                "&c+2 Max Hearts", "&7An eerie mask imbued with", "&7Christmas Joy that knows",
                "&7who is naughty or nice.", "&6&l* &6Christmas 2018"));
        assertMask(library, "reindeer", "SPEED IV", List.of(
                "&cSPEED IV", "&cFlight regardless of rank", "&7The decapitated and stuffed",
                "&7head of one of Santa's", "&7magical reindeer... yikes!",
                "&6&l* &6Christmas 2018"));
        assertMask(library, "party-hat", "-5% ENEMY DMG", List.of(
                "&c-5% ENEMY DMG", "&c+4% DMG", "&7Everywhere you are is a party.",
                "&6&l* &6New Years 2018"));
        assertMask(library, "death-knight", "50% chance to negate enemy's Phoenix", List.of(
                "&c50% chance to negate enemy's Phoenix", "&c+2.5% DMG",
                "&7The cursed mask of runeforged", "&7Death Knight armor.",
                "&6&l* &6Death Knight Mastery Kit"));
        assertMask(library, "beanie", "", List.of(
                "&7A beanie to keep your head warm.", "&6&l * &6Snow Day Lootbox Release"));
        assertMask(library, "rift-mask", "50% Mastery Enchant Negation.", List.of(
                "&c50% Mastery Enchant Negation", "&7A mysterious mask with a strange",
                "&7aura of power found scattered among", "&7a select few random lootboxes.",
                "&6&l * &6Snow Day Lootbox Release"));
        assertMask(library, "lover-mask", "Immune to Mortal Coil", List.of(
                "&cImmune to Mortal Coil", "&7Make love, not Minecraft...",
                "&7or maybe its the other way around?", "&6&l * &6Valentine's Day 2019"));
        assertMask(library, "spectral-mask", "Zombie Auto-disguise at y>200 (in combat)", List.of(
                "&cZombie Auto-disguise at y>200 (in combat)", "&7As silent as the night, as mystic",
                "&7as the full moon: The Spectre", "&6&l * &6Baked Lootbox Release"));
        assertMask(library, "glitch-mask", "Immune to Teleblock, Bidirectional Teleport", List.of(
                "&cImmune to Teleblock, Bidirectional Teleport", "&7The aura around this mask",
                "&7is electrified and encoded.", "&6&l * &6St. Patrick's Day 2019"));
        assertMask(library, "zeus-mask", "Immune to Natures Wrath", List.of(
                "&cImmune to Natures Wrath", "&7Channel the powers of the",
                "&7King of the Greek Gods", "&6&l * &6April Showers Lootbox Release"));
        assertMask(library, "bunny-mask", "1.65x Mobs from Spawners in Chunk", List.of(
                "&c1.65x Mobs from Spawners in Chunk", "&7And so the gods declared to all",
                "&7the easter bunnies: be fruitful", "&7and multiply.", "&6&l * &6Easter 2019"));
        assertMask(library, "joker-mask", "Increase Combat Tag on players by 4s", List.of(
                "&c+4s Combat Tag on enemy players", "&c-3s Combat Tag on you",
                "&7Everyone takes everything so", "&7very seriously, you're just trying",
                "&7to.. haHahaHahaHahaHahaHahaHa!", "&6&l * &6Summer Savage Lootbox Release"));
        assertMask(library, "dungeon-mask", "10% chance to not use /dungeon key", List.of(
                "&c10% chance to not use /dungeon key", "&7You take dungeon running extremely",
                "&7seriously, you solo most dungeons", "&7faster than other groups start them.",
                "&6&l * &6Lit Lootbox Release"));
        assertMask(library, "outpost-mask", "Capture, destroy /outpost caps 2x faster", List.of(
                "&cCapture, destroy /outpost caps 2x faster",
                "&7Your very presence commands unquestioning,",
                "&7unwaivering admiration and respect.", "&6&l * &6July 2019"));
        assertMask(library, "multi-mask", "", List.of(""));
        assertMask(library, "stronghold-mask", "Capture, destroy /stronghold caps 2x faster", List.of(
                "&cCapture, destroy /stronghold caps 2x faster",
                "&7Your very presence terrifies all,", "&7who dare stand before you.",
                "&6&l * &6Sugar Daddy Lootbox Release"));
        assertMask(library, "boss-mask", "-25% incoming Boss DMG, +10% outgoing Boss DMG", List.of(
                "&c-25% incoming DMG, +10% outgoing DMG to Bosses",
                "&7A seasoned monster hunter, you pride",
                "&7yourself on your boss slaying record.", "&6&l * &6Sugar Daddy Lootbox Release"));
    }

    private static void assertPet(Library library, String key, int maxLevel,
                                  List<Integer> expThresholds, List<Integer> cooldowns) {
        compile.load.PetDef pet = library.petDefOf(key);
        assertTrue(pet != null, () -> "missing Cosmic pet " + key);
        assertEquals(maxLevel, pet.maxLevel(), key + " max level");
        assertEquals(expThresholds,
                java.util.stream.IntStream.range(1, maxLevel)
                        .mapToObj(level -> pet.expToNext(level, -1)).toList(),
                key + " XP curve");
        assertEquals(cooldowns, pet.brackets().stream()
                .map(compile.load.PetBracket::cooldownTicks).toList(), key + " cooldowns");
    }

    private static void assertSourceCatalogParity(Library library) {
        Path codex = Path.of("..", "..", "deobf", "cosmic", "codex", "09-enchants-engine-mechanics.md");
        assertTrue(Files.isRegularFile(codex), () -> "Cosmic enchant codex not found at " + codex.toAbsolutePath());

        Map<String, SourceEnchant> sourceByDisplay = new LinkedHashMap<>();
        boolean inAppendix = false;
        try {
            for (String line : Files.readAllLines(codex)) {
                if (line.startsWith("## Appendix A")) {
                    inAppendix = true;
                    continue;
                }
                if (line.startsWith("## Appendix B")) {
                    break;
                }
                if (!inAppendix || !line.matches("\\| \\d+ \\|.*")) {
                    continue;
                }

                String[] columns = line.split("\\|", -1);
                String display = columns[2].trim();
                String sourceClass = columns[3].trim().replace("`", "");
                int tier = Integer.parseInt(columns[5].trim());
                String rawMax = columns[6].trim();
                int maxLevel = rawMax.equals("-") ? inheritedHeroicMax(sourceClass)
                        : Integer.parseInt(rawMax);
                // The literal registration array contains Obsidian Destroyer twice. Cosmic's live
                // display-keyed registry overwrites the first with the identical second instance.
                sourceByDisplay.put(display, new SourceEnchant(sourceTier(tier), maxLevel));
            }
        } catch (java.io.IOException unreadable) {
            throw new AssertionError("could not read Cosmic enchant source catalog", unreadable);
        }

        assertEquals(194, sourceByDisplay.size(), "unique live Cosmic source registrations");
        Map<String, EnchantDef> packByDisplay = library.catalog().stream().collect(Collectors.toMap(
                EnchantDef::display, def -> def, (first, duplicate) -> {
                    throw new AssertionError("duplicate Cosmic pack display: " + first.display());
                }, LinkedHashMap::new));
        assertEquals(sourceByDisplay.keySet(), packByDisplay.keySet(),
                "Cosmic pack display catalog must exactly match the source registration array");

        sourceByDisplay.forEach((display, source) -> {
            EnchantDef pack = packByDisplay.get(display);
            assertEquals(source.tier(), pack.tier(), display + " tier");
            assertEquals(source.maxLevel(), pack.maxLevel(), display + " max level");
        });
    }

    private static void assertCosmicEndTierGate(Library library, Path content) {
        Set<String> suppressedTiers = Set.of("soul", "heroic", "mastery");
        int suppressed = 0;
        for (EnchantDef enchant : library.catalog()) {
            Path file = content.resolve(enchant.key() + ".yml");
            String yaml;
            try {
                yaml = Files.readString(file);
            } catch (java.io.IOException unreadable) {
                throw new AssertionError("could not read Cosmic enchant " + file, unreadable);
            }
            boolean shouldSuppress = suppressedTiers.contains(enchant.tier());
            assertEquals(shouldSuppress, yaml.contains("disabled-environments: [THE_END]"),
                    enchant.display() + " End-environment declaration");
            if (!shouldSuppress) {
                continue;
            }
            suppressed++;
            for (int level = 1; level <= enchant.maxLevel(); level++) {
                String levelKey = enchant.key() + "/" + level;
                Ability ability = library.snapshot().byStableKey(levelKey);
                assertTrue(ability != null && ability.condition() != null,
                        () -> levelKey + " must compile the tier>5 End gate");
                for (int index = 1; ; index++) {
                    Ability extra = library.snapshot().byStableKey(levelKey + "/a" + index);
                    if (extra == null) {
                        break;
                    }
                    assertTrue(extra.condition() != null,
                            levelKey + "/a" + index + " must compile the tier>5 End gate");
                }
            }
        }
        assertEquals(50, suppressed, "Cosmic tier 6-8 enchant count");
    }

    private static int inheritedHeroicMax(String sourceClass) {
        return switch (sourceClass) {
            case "HeroicArmored" -> 4;
            case "HeroicDodge" -> 5;
            case "HeroicCactus" -> 2;
            case "HeroicTank" -> 4;
            default -> throw new AssertionError("unresolved inherited heroic max for " + sourceClass);
        };
    }

    private static String sourceTier(int tier) {
        return switch (tier) {
            case 1 -> "simple";
            case 2 -> "unique";
            case 3 -> "elite";
            case 4 -> "ultimate";
            case 5 -> "legendary";
            case 6 -> "soul";
            case 7 -> "heroic";
            case 8 -> "mastery";
            default -> throw new AssertionError("unknown Cosmic source tier " + tier);
        };
    }

    private record SourceEnchant(String tier, int maxLevel) {}

    private static void assertPotion(Ability ability, int duration, int level, boolean force) {
        assertTrue(ability != null, "missing audited Cosmic ability");
        CompiledEffect potion = java.util.Arrays.stream(ability.effects())
                .filter(effect -> effect.head().equals("POTION"))
                .findFirst().orElseThrow();
        assertEquals(duration, potion.args().integer("duration"), "potion duration");
        assertEquals(level, potion.args().integer("level"), "authored potion level");
        assertEquals(force, potion.args().bool("force"), "potion force flag");
    }

    private static void assertDamageUnattributed(Ability ability) {
        assertTrue(ability != null, "missing audited Cosmic ability");
        CompiledEffect damage = java.util.Arrays.stream(ability.effects())
                .filter(effect -> effect.head().equals("DAMAGE"))
                .findFirst().orElseThrow();
        assertFalse(damage.args().bool("attributed"), "Cosmic direct damage must not inherit attacker attribution");
    }

    private static void assertEffectDouble(
            Ability ability, String effectHead, String argument, double expected) {
        assertEquals(expected, effect(ability, effectHead).args().dbl(argument),
                ability.level() + " " + effectHead + "." + argument);
    }

    private static CompiledEffect effect(Ability ability, String effectHead) {
        assertTrue(ability != null, "missing audited Cosmic ability");
        return java.util.Arrays.stream(ability.effects())
                .filter(candidate -> candidate.head().equals(effectHead))
                .findFirst().orElseThrow();
    }

    private static void assertMask(Library library, String stem, String summary, List<String> description) {
        compile.load.MaskDef mask = library.maskDefOf("masks/" + stem);
        assertTrue(mask != null, () -> "missing Cosmic mask " + stem);
        assertEquals(summary, mask.summary(), stem + " summary");
        assertEquals(description, mask.description(), stem + " lore");
    }

    @Test
    void cosmicPackItemsLoadClean() {
        Path items = PACK.resolve("items");
        assertTrue(Files.isDirectory(items), "Cosmic pack items not found");
        ItemsConfig config = ItemsLoader.load(items);
        String errors = config.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(config.hasErrors(), () -> "Cosmic pack items have blocking diagnostics:\n  " + errors);
        assertTrue(config.soulGem().isPresent(), "the Cosmic pack should carry a soul-gem likeness");
        compile.load.SoulGemConfig.Drain drain = config.soulGem().orElseThrow().drain();
        assertEquals(5, drain.periodTicks());
        assertEquals(4, drain.reserve());
        assertEquals(Map.of("divine-immolation", 5, "soul-trap", 2, "hero-killer", 1, "sabotage", 2),
                drain.heldEnchantCosts());
        assertEquals("EAT", drain.sound().name());
        assertEquals(0.4f, drain.sound().volume());
        assertEquals(0.2f, drain.sound().pitch());
        assertEquals("SPELL", drain.particle().type());
        assertEquals(65, drain.particle().amount());
        assertEquals(0.5, drain.particle().speed());
        assertEquals("ENCHANTMENT_TABLE", drain.idleParticle().type());
        assertEquals(80, drain.idleParticle().amount());
        assertEquals(1.5, drain.idleParticle().speed());

        compile.load.MaskItemConfig mask = config.maskOrDefault();
        assertEquals("{COLOR}&l{NAME}", mask.name());
        assertEquals(List.of(
                "{DESCRIPTION}", "",
                "&7&oAttach this mask to any helmet",
                "&7&oto give it a visual override!", "",
                "&7To equip, place this mask on a helmet.",
                "&7To remove, right-click helmet while attached."), mask.lore());
        assertEquals("&7&lATTACHED: {NAME}{SUMMARY_SECTION}", mask.loreWhileOnItem());
        assertEquals("&f&lMulti-Mask (&r{MASKS}&f&l)", mask.multiName());
        assertEquals(List.of("&7This mask contains the powers of:", "{COMPONENTS}"), mask.multiLore());
        assertEquals("&f&l* {COLOR}&l{NAME}", mask.multiComponentName());
        assertEquals("&f&l(&c{SUMMARY}&f&l)", mask.multiComponentAbility());
        assertEquals("&7&lATTACHED: &f&lMulti-Mask&f ({MASKS}&f)", mask.multiLoreWhileOnItem());
    }

    @Test
    void cosmicPackMenusLoadClean() {
        Path menus = PACK.resolve("menus");
        assertTrue(Files.isDirectory(menus), "Cosmic pack menus not found");
        MenusConfig config = MenusLoader.load(menus);
        String errors = config.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(config.hasErrors(), () -> "Cosmic pack menus have blocking diagnostics:\n  " + errors);
    }

    @Test
    void cosmicPackMasterConfigLoadsClean() {
        Path configFile = PACK.resolve("config.yml");
        assertTrue(Files.isRegularFile(configFile), "Cosmic pack config.yml not found");
        MasterConfig master = MasterConfigLoader.load(configFile);
        String errors = master.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(master.hasErrors(), () -> "Cosmic pack config.yml has blocking diagnostics:\n  " + errors);
    }

    // pack.yml is the ADR-0023 descriptor; the rest are the captured surface roots (pack.PackSurface FILES+DIRS).
    // cosmic-pack.zip is a BUILD output (se/bootstrap/build.gradle.kts packCosmicPack), never a source entry.
    private static final Set<String> ALLOWED_TOP_LEVEL = Set.of(
            "pack.yml", "config.yml", "lang.yml", "content", "items", "menus");

    @Test
    void cosmicPackHasOnlySurfaceRootsAtTopLevel() throws Exception {
        assertTrue(Files.isDirectory(PACK), "Cosmic pack source tree not found from " + Path.of("").toAbsolutePath());
        try (Stream<Path> top = Files.list(PACK)) {
            List<String> stray = top.map(p -> p.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .filter(name -> !ALLOWED_TOP_LEVEL.contains(name))
                    .sorted()
                    .toList();
            assertTrue(stray.isEmpty(),
                    () -> "cosmic-pack has top-level entries outside pack.yml + the surface roots: " + stray);
        }
    }
}
