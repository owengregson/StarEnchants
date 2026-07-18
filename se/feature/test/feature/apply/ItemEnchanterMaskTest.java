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
 * The mask eligibility ladder + mutation round-trip of {@link ItemEnchanter#checkMask} /
 * {@link ItemEnchanter#applyMask} / {@link ItemEnchanter#removeMask} (ADR-0053 §3), over a real compiled
 * {@link Library} and a real {@link CombatCodec} on the in-memory store — no server. Gear stacks are mocks
 * (state rides the identity-keyed store; the null item-meta makes the lore recompose a graceful no-op).
 * Expected messages are computed through the SAME {@link Messages} instance the enchanter formats with
 * (writing-tests Rule 1 — production's own output, never a re-typed string).
 */
class ItemEnchanterMaskTest {

    private static final String MASK = "masks/midas";

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
        Path file = root.resolve("masks/midas.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
            display: "Midas"
            color: "&6"
            head: "aGVhZA=="
            material: GOLDEN_HELMET
            description:
              - "&6&lMIDAS MASK"
              - "&6* Negates enemy heroic armor."
            trigger: ATTACK
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
                () -> compile.load.MasterConfig.ReforgesSection.defaults().weaponGroups(),
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
    void checkMaskFailureLadder() {
        // no gear at all
        assertEquals(messages.format("mask.on-item"), enchanter.checkMask(null, MASK).message());
        // the stack gate precedes applies-to: a stacked SWORD reports the stack, not the kind
        assertEquals(messages.format("mask.single-item"),
                enchanter.checkMask(gear(Material.DIAMOND_SWORD, 2), MASK).message());
        // unknown / uncompiled key
        assertEquals(messages.format("mask.no-such", "KEY", "masks/ghost"),
                enchanter.checkMask(gear(Material.DIAMOND_HELMET, 1), "masks/ghost").message());
        // masks are helmets-only by construction — a sword is rejected on the fixed HELMET group
        assertEquals(messages.format("apply.not-applicable", "DISPLAY", "Midas"),
                enchanter.checkMask(gear(Material.DIAMOND_SWORD, 1), MASK).message());
        // a second mask: one per helmet, a boolean occupancy
        org.bukkit.inventory.ItemStack masked = gear(Material.DIAMOND_HELMET, 1);
        codec.write(masked, CombatState.EMPTY.withMask(MASK));
        assertEquals(messages.format("mask.already"), enchanter.checkMask(masked, MASK).message());
        // and the clean helmet passes
        assertTrue(enchanter.checkMask(gear(Material.DIAMOND_HELMET, 1), MASK).ok());
    }

    @Test
    void applyRoundTripPreservesEveryOtherField() {
        org.bukkit.inventory.ItemStack helmet = gear(Material.DIAMOND_HELMET, 1);
        // enchants + crystal + set membership + heroic — the fields the multi-arg ctor trap once stripped
        CombatState seeded = new CombatState(Map.of("enchants/glow", 2), List.of("crystals/frost"),
                "sets/yeti", false, new HeroicStat(1.0, 2.0, 3.0));
        codec.write(helmet, seeded);

        assertTrue(enchanter.applyMask(helmet, MASK).ok());
        assertEquals(seeded.withMask(MASK), codec.read(helmet),
                "apply must ONLY set maskKey — enchants/crystals/set/heroic ride through untouched");
    }

    @Test
    void removeMaskReturnsTheKeyAndClearsOnlyTheMask() {
        org.bukkit.inventory.ItemStack helmet = gear(Material.DIAMOND_HELMET, 1);
        CombatState seeded = new CombatState(Map.of("enchants/glow", 2), List.of("crystals/frost"),
                "sets/yeti", false, new HeroicStat(1.0, 2.0, 3.0));
        codec.write(helmet, seeded);
        enchanter.applyMask(helmet, MASK);

        ExtractResult popped = enchanter.removeMask(helmet);
        assertTrue(popped.ok());
        assertEquals(MASK, popped.poppedEntry());
        assertEquals(seeded, codec.read(helmet), "remove must restore the pre-mask state exactly");

        // a second remove has nothing to pop
        ExtractResult again = enchanter.removeMask(helmet);
        assertFalse(again.ok());
        assertEquals(messages.format("mask.none"), again.message());
        assertNull(again.poppedEntry());
    }
}
