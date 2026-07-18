package feature.apply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.Compiler;
import compile.MapSpecRegistry;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.MasterConfig;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.HeroicStat;
import item.render.LoreRenderer;
import item.render.LoreStyle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import platform.item.ItemGroups;
import platform.lang.Messages;
import schema.spec.D;
import schema.spec.ParamSpec;

/**
 * The reforge eligibility ladder + mutation round-trip of {@link ItemEnchanter#checkReforge} /
 * {@link ItemEnchanter#applyReforge} / {@link ItemEnchanter#extractReforge} (ADR-0070), over a real compiled
 * {@link Library} and a real {@link CombatCodec} on the in-memory store — no server (the mask-test clone).
 * The enchanter reads its weapon-groups from {@link MasterConfig.ReforgesSection#defaults()} (SWORD + AXE).
 * Expected messages are computed through the SAME {@link Messages} instance the enchanter formats with
 * (writing-tests Rule 1 — production's own output, never a re-typed string).
 */
class ItemEnchanterReforgeTest {

    private static final String REFORGE = "reforges/testforge";

    @TempDir
    Path root;

    private final Messages messages = Messages.defaults();
    private ItemEnchanter enchanter;
    private CombatCodec codec;

    private static Compiler compiler() {
        return Compiler.of(MapSpecRegistry.of(ParamSpec.of("HEAL").param("amount", D.DOUBLE.min(0)).build()));
    }

    @BeforeEach
    void build() throws IOException {
        Path file = root.resolve("reforges/testforge.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
            display: "Testforge"
            color: "&6"
            icon: SUGAR
            description:
              - "&6&lTESTFORGE"
              - "&6* A test reforge."
            cooldown: 200
            effects: [{ HEAL: { amount: 1 } }]
            """, StandardCharsets.UTF_8);
        Library lib = LibraryLoader.load(root, compiler(), 1);
        ContentHolder holder = new ContentHolder(lib);
        FakeItemStateStore store = new FakeItemStateStore();
        codec = new CombatCodec("combat", store);
        LoreRenderer lore = new LoreRenderer(
                LoreRenderer.Config.of(LoreStyle.DEFAULT, key -> holder.library().displayNameOf(key)), store);
        enchanter = new ItemEnchanter(codec, lore, holder, ItemGroups.standard(),
                () -> ItemEnchanter.DEFAULT_BASE_SLOTS, () -> ItemEnchanter.DEFAULT_CRYSTAL_SLOTS,
                () -> ItemEnchanter.DEFAULT_MAX_MERGE,
                () -> MasterConfig.ReforgesSection.defaults().weaponGroups(),
                messages, item.mint.VanillaEnchants.NONE);
    }

    /** A mocked gear stack: type/amount stubbed, meta absent (the lore recompose no-ops on it). */
    private static org.bukkit.inventory.ItemStack gear(Material type, int amount) {
        org.bukkit.inventory.ItemStack stack = mock(org.bukkit.inventory.ItemStack.class);
        when(stack.getType()).thenReturn(type);
        when(stack.getAmount()).thenReturn(amount);
        return stack;
    }

    @Test
    void checkReforgeFailureLadder() {
        // no gear at all
        assertEquals(messages.format("reforge.on-item"), enchanter.checkReforge(null, REFORGE).message());
        // the stack gate precedes applies-to: a stacked SWORD reports the stack, not the kind
        assertEquals(messages.format("reforge.single-item"),
                enchanter.checkReforge(gear(Material.DIAMOND_SWORD, 2), REFORGE).message());
        // unknown key
        assertEquals(messages.format("reforge.no-such", "KEY", "reforges/ghost"),
                enchanter.checkReforge(gear(Material.DIAMOND_SWORD, 1), "reforges/ghost").message());
        // reforges are weapon-only — a helmet is rejected on the weapon-groups set
        assertEquals(messages.format("reforge.not-weapon", "DISPLAY", "Testforge"),
                enchanter.checkReforge(gear(Material.DIAMOND_HELMET, 1), REFORGE).message());
        // a second reforge: one socket per weapon, a boolean occupancy
        org.bukkit.inventory.ItemStack reforged = gear(Material.DIAMOND_SWORD, 1);
        codec.write(reforged, CombatState.EMPTY.withReforge(REFORGE));
        assertEquals(messages.format("reforge.occupied"), enchanter.checkReforge(reforged, REFORGE).message());
        // and both default weapon kinds pass clean
        assertTrue(enchanter.checkReforge(gear(Material.DIAMOND_SWORD, 1), REFORGE).ok());
        assertTrue(enchanter.checkReforge(gear(Material.DIAMOND_AXE, 1), REFORGE).ok());
    }

    @Test
    void applyThenExtractRoundTrip() {
        org.bukkit.inventory.ItemStack sword = gear(Material.DIAMOND_SWORD, 1);
        // enchants + crystal + set-weapon membership + a mask — every other field the reforge must ride through
        CombatState seeded = new CombatState(Map.of("enchants/glow", 2), List.of("crystals/frost"), null,
                "sets/yeti", false, new HeroicStat(1.0, 2.0, 3.0), 0, "masks/midas", null);
        codec.write(sword, seeded);

        assertTrue(enchanter.applyReforge(sword, REFORGE).ok());
        assertEquals(seeded.withReforge(REFORGE), codec.read(sword),
                "apply must ONLY set reforgeKey — enchants/crystals/set-weapon/mask ride through untouched");

        ExtractResult popped = enchanter.extractReforge(sword);
        assertTrue(popped.ok());
        assertEquals(REFORGE, popped.poppedEntry());
        assertEquals(seeded, codec.read(sword), "extract must restore the pre-reforge state exactly");
    }

    @Test
    void extractOnBareWeaponFails() {
        org.bukkit.inventory.ItemStack sword = gear(Material.DIAMOND_SWORD, 1);
        ExtractResult out = enchanter.extractReforge(sword);
        assertFalse(out.ok());
        assertEquals(messages.format("reforge.none"), out.message());
        assertNull(out.poppedEntry());
    }
}
