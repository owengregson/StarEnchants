package feature.apply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.MapSpecRegistry;
import compile.load.ContentHolder;
import compile.load.EnchantDef;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.SetDef;
import item.codec.CombatCodec;
import item.render.LoreRenderer;
import item.render.LoreStyle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import platform.item.ItemGroups;
import schema.spec.D;
import schema.spec.ParamSpec;

/**
 * The pure validation of {@link ItemEnchanter#checkEnchant} / {@link ItemEnchanter#checkCrystal} —
 * verified over a real compiled {@link Library} with no server (the apply mutation itself, which
 * touches {@code ItemStack}, is matrix-verified live). {@link Material} is a plain enum on the floor.
 */
class ItemEnchanterTest {

    private static Compiler compiler() {
        return Compiler.of(MapSpecRegistry.of(ParamSpec.of("HEAL").param("amount", D.DOUBLE.min(0)).build()));
    }

    private static ItemEnchanter over(Library lib) {
        return over(lib, new Random());
    }

    private static ItemEnchanter over(Library lib, Random random) {
        ContentHolder holder = new ContentHolder(lib);
        // checkEnchant/checkCrystal never read on-item state, so the injected store is an inert modern placeholder.
        CombatCodec codec = new CombatCodec("combat", new item.codec.PdcItemStateStore());
        LoreRenderer lore = new LoreRenderer(
                LoreRenderer.Config.of(LoreStyle.DEFAULT, key -> holder.library().displayNameOf(key)),
                new item.codec.PdcItemStateStore());
        return new ItemEnchanter(codec, lore, holder, ItemGroups.standard(),
                () -> ItemEnchanter.DEFAULT_BASE_SLOTS, () -> ItemEnchanter.DEFAULT_CRYSTAL_SLOTS,
                () -> ItemEnchanter.DEFAULT_MAX_MERGE,
                () -> compile.load.MasterConfig.ReforgesSection.defaults().weaponGroups(),
                platform.lang.Messages.defaults(), item.mint.VanillaEnchants.NONE, random);
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }

    @Test
    void validatesEnchantKeyLevelAndAppliesTo(@TempDir Path root) throws IOException {
        write(root, "enchants/blaze.yml", """
            display: "&cBlaze"
            applies-to: [SWORD]
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["HEAL:1"] }
              2: { chance: 100, effects: ["HEAL:2"] }
            """);
        ItemEnchanter e = over(LibraryLoader.load(root, compiler(), 1));

        assertTrue(e.checkEnchant(Material.DIAMOND_SWORD, "enchants/blaze", 1).ok());
        assertTrue(e.checkEnchant(Material.DIAMOND_SWORD, "enchants/blaze", 2).ok());
        assertFalse(e.checkEnchant(Material.DIAMOND_SWORD, "enchants/blaze", 3).ok(), "level above max");
        assertFalse(e.checkEnchant(Material.DIAMOND_SWORD, "enchants/blaze", 0).ok(), "level below 1");
        assertFalse(e.checkEnchant(Material.DIAMOND_HELMET, "enchants/blaze", 1).ok(), "applies-to SWORD only");
        assertFalse(e.checkEnchant(Material.DIAMOND_SWORD, "enchants/ghost", 1).ok(), "unknown enchant");
    }

    @Test
    void enchantSlotsCapNewEnchantsButAllowReapply(@TempDir Path root) throws IOException {
        ItemEnchanter e = over(LibraryLoader.load(root, compiler(), 1)); // catalog is irrelevant to slot math

        // Nine is the default capacity (§H): an item already holding nine distinct enchants has no free slot.
        java.util.Map<String, Integer> nine = new java.util.LinkedHashMap<>();
        for (int i = 0; i < ItemEnchanter.DEFAULT_BASE_SLOTS; i++) {
            nine.put("enchants/e" + i, 1);
        }
        item.codec.CombatState full = new item.codec.CombatState(nine, java.util.List.of());
        assertFalse(e.checkSlots(full, "enchants/new").ok(), "no free slot for a tenth enchant");
        assertTrue(e.checkSlots(full, "enchants/e0").ok(), "re-applying an existing enchant needs no new slot");

        item.codec.CombatState room = new item.codec.CombatState(java.util.Map.of("enchants/e0", 1), java.util.List.of());
        assertTrue(e.checkSlots(room, "enchants/new").ok(), "a near-empty item has free slots");
    }

    @Test
    void purchasedSlotsRaiseCapacity(@TempDir Path root) throws IOException {
        ItemEnchanter e = over(LibraryLoader.load(root, compiler(), 1));

        // Nine distinct enchants fill the base-9 capacity — but a purchased slot (§H added) makes room.
        java.util.Map<String, Integer> nine = new java.util.LinkedHashMap<>();
        for (int i = 0; i < ItemEnchanter.DEFAULT_BASE_SLOTS; i++) {
            nine.put("enchants/e" + i, 1);
        }
        item.codec.CombatState full = new item.codec.CombatState(nine, java.util.List.of());
        assertFalse(e.checkSlots(full, "enchants/new").ok(), "no free slot at base capacity");
        assertTrue(e.checkSlots(full.withAdded(1), "enchants/new").ok(), "a purchased slot makes room");
    }

    @Test
    void resolvesExactCosmicNearMaxRangeAndUpgradeReplacement(@TempDir Path root) throws IOException {
        writeEnchant(root, "base", 5, "");
        writeEnchant(root, "plain", 5, "");
        writeEnchant(root, "ability", 5, "");
        writeEnchant(root, "ranged", 5, "");
        writeEnchant(root, "upgrade", 3,
                "requires: [enchants/base]\nremoves-required: true\n");

        ItemEnchanter e = over(LibraryLoader.load(root, compiler(), 1),
                new ScriptedRandom(
                        new double[] {0, 0, 0, 0},
                        new int[] {0, 3, 2}));
        SetDef.Member member = new SetDef.Member("helmet", "DIAMOND_HELMET", "Test", List.of(), null,
                Map.of("enchants/ranged", 1),
                List.of(
                        new SetDef.EnchantRoll("enchants/plain", 100, SetDef.RollMode.PLAIN_NEAR_MAX, 1, 1),
                        new SetDef.EnchantRoll("enchants/ability", 100, SetDef.RollMode.ABILITY_NEAR_MAX, 1, 1),
                        new SetDef.EnchantRoll("enchants/ranged", 100, SetDef.RollMode.RANGE, 2, 4),
                        new SetDef.EnchantRoll("enchants/upgrade", 100, SetDef.RollMode.MAX, 1, 1)),
                List.of(), List.of(), SetDef.Heroic.NONE);

        Map<String, Integer> resolved = e.resolvedSetEnchants(Map.of("enchants/base", 4), member);

        assertEquals(3, resolved.get("enchants/plain"),
                "plain near-max is max-2 + nextInt(3)");
        assertEquals(2, resolved.get("enchants/ability"),
                "ability near-max for max>=4 is max-nextInt(4)");
        assertEquals(4, resolved.get("enchants/ranged"),
                "the later ranged roll overwrites the member's fixed level");
        assertEquals(3, resolved.get("enchants/upgrade"));
        assertFalse(resolved.containsKey("enchants/base"),
                "a heroic upgrade replaces its required base instead of double-firing");
    }

    @Test
    void resolvesCosmicPoolsWithoutReplacementAndWeightedNoOpBranches(@TempDir Path root) throws IOException {
        writeEnchant(root, "pool-a", 4, "");
        writeEnchant(root, "pool-b", 3, "");
        writeEnchant(root, "pool-c", 2, "");
        writeEnchant(root, "chosen", 1, "");

        SetDef.EnchantPool pool = new SetDef.EnchantPool(
                List.of("enchants/pool-a", "enchants/pool-b", "enchants/pool-c"),
                2, SetDef.RollMode.ABILITY_NEAR_MAX);
        SetDef.EnchantChoice choice = new SetDef.EnchantChoice(List.of(
                new SetDef.EnchantBranch(5, List.of(
                        new SetDef.EnchantRoll("enchants/chosen", 100, SetDef.RollMode.FIXED, 1, 1))),
                new SetDef.EnchantBranch(95, List.of())));

        SetDef.Member selectedMember = new SetDef.Member("helmet", "DIAMOND_HELMET", "Test", List.of(), null,
                Map.of(), List.of(), List.of(pool), List.of(choice), SetDef.Heroic.NONE);
        ItemEnchanter selected = over(LibraryLoader.load(root, compiler(), 1),
                new ScriptedRandom(
                        new double[] {0.01, 0},
                        new int[] {2, 1, 0, 2}));
        Map<String, Integer> first = selected.resolvedSetEnchants(Map.of(), selectedMember);
        assertEquals(Map.of(
                "enchants/pool-c", 1,
                "enchants/pool-a", 2,
                "enchants/chosen", 1), first,
                "pool selection is uniform without replacement, then the 5% branch runs");

        SetDef.Member noOpMember = new SetDef.Member("helmet", "DIAMOND_HELMET", "Test", List.of(), null,
                Map.of(), List.of(), List.of(),
                List.of(choice), SetDef.Heroic.NONE);
        ItemEnchanter noOp = over(LibraryLoader.load(root, compiler(), 1),
                new ScriptedRandom(new double[] {0.50}, new int[] {}));
        assertTrue(noOp.resolvedSetEnchants(Map.of(), noOpMember).isEmpty(),
                "the weighted 95% empty branch must remain a true no-op");
    }

    private static void writeEnchant(Path root, String id, int maxLevel, String extra) throws IOException {
        StringBuilder levels = new StringBuilder();
        for (int level = 1; level <= maxLevel; level++) {
            levels.append("  ").append(level).append(": { chance: 100, effects: [\"HEAL:1\"] }\n");
        }
        write(root, "enchants/" + id + ".yml", """
            display: Test
            applies-to: [ARMOR]
            trigger: PASSIVE
            %slevels:
            %s""".formatted(extra, levels));
    }

    private static final class ScriptedRandom extends Random {
        private static final long serialVersionUID = 1L;
        private final ArrayDeque<Double> doubles = new ArrayDeque<>();
        private final ArrayDeque<Integer> ints = new ArrayDeque<>();

        private ScriptedRandom(double[] doubles, int[] ints) {
            for (double value : doubles) {
                this.doubles.add(value);
            }
            for (int value : ints) {
                this.ints.add(value);
            }
        }

        @Override public double nextDouble() {
            if (doubles.isEmpty()) {
                throw new AssertionError("unexpected nextDouble call");
            }
            return doubles.removeFirst();
        }

        @Override public int nextInt(int bound) {
            if (ints.isEmpty()) {
                throw new AssertionError("unexpected nextInt(" + bound + ") call");
            }
            int value = ints.removeFirst();
            if (value < 0 || value >= bound) {
                throw new AssertionError("scripted nextInt value " + value + " outside bound " + bound);
            }
            return value;
        }
    }

    @Test
    void enforcesEnchantRelationships(@TempDir Path root) throws IOException {
        // base must exist for the upgrade's requires to resolve to a display name; the upgrade requires it
        // at level ≥ the applied level and removes it on success (net-zero slots); poison blacklists base.
        write(root, "enchants/base.yml", """
            display: "&7Base"
            applies-to: [SWORD]
            trigger: ATTACK
            blacklist: [enchants/poison]
            levels:
              1: { chance: 100, effects: ["HEAL:1"] }
              2: { chance: 100, effects: ["HEAL:1"] }
            """);
        write(root, "enchants/upgrade.yml", """
            display: "&6Upgrade"
            applies-to: [SWORD]
            trigger: ATTACK
            requires: [enchants/base]
            removes-required: true
            levels:
              1: { chance: 100, effects: ["HEAL:1"] }
            """);
        write(root, "enchants/poison.yml", """
            display: "&2Poison"
            applies-to: [SWORD]
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["HEAL:1"] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);
        ItemEnchanter e = over(lib);
        EnchantDef upgrade = defOf(lib, "enchants/upgrade");
        EnchantDef base = defOf(lib, "enchants/base");
        EnchantDef poison = defOf(lib, "enchants/poison");

        // requires: absent prerequisite blocks; present-but-too-low blocks; present at ≥ level allows.
        item.codec.CombatState none = new item.codec.CombatState(java.util.Map.of(), java.util.List.of());
        assertFalse(e.checkRelationships(none, upgrade, 1).ok(), "missing prerequisite blocks");
        item.codec.CombatState withBase1 = new item.codec.CombatState(java.util.Map.of("enchants/base", 1), java.util.List.of());
        assertTrue(e.checkRelationships(withBase1, upgrade, 1).ok(), "prerequisite at equal level allows");
        assertFalse(e.checkRelationships(withBase1, upgrade, 2).ok(), "prerequisite below applied level blocks");

        // blacklist is bidirectional: base↔poison cannot coexist either way round.
        item.codec.CombatState withPoison = new item.codec.CombatState(java.util.Map.of("enchants/poison", 1), java.util.List.of());
        assertFalse(e.checkRelationships(withPoison, base, 1).ok(), "base blacklists poison (forward)");
        assertFalse(e.checkRelationships(withBase1, poison, 1).ok(), "poison blocked while base present (reverse)");
    }

    private static EnchantDef defOf(Library lib, String key) {
        return lib.catalog().stream().filter(d -> d.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void validatesCrystalKeyAndAppliesTo(@TempDir Path root) throws IOException {
        write(root, "crystals/jolt.yml", """
            display: "&bJolt"
            applies-to: [WEAPON]
            trigger: ATTACK
            chance: 100
            effects: ["HEAL:1"]
            """);
        ItemEnchanter e = over(LibraryLoader.load(root, compiler(), 1));

        assertTrue(e.checkCrystal(Material.DIAMOND_SWORD, "crystals/jolt").ok());
        assertTrue(e.checkCrystal(Material.IRON_AXE, "crystals/jolt").ok(), "axe is a weapon");
        assertFalse(e.checkCrystal(Material.DIAMOND_HELMET, "crystals/jolt").ok(), "applies-to WEAPON only");
        assertFalse(e.checkCrystal(Material.DIAMOND_SWORD, "crystals/missing").ok(), "unknown crystal");
    }
}
