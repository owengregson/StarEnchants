package feature.mask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.Compiler;
import compile.MapSpecRegistry;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.MaskItemConfig;
import compile.load.SoundCue;
import feature.apply.FakeItemStateStore;
import feature.apply.GestureOutcome;
import feature.apply.ItemEnchanter;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ItemKeys;
import item.codec.MaskCodec;
import item.head.TexturedHeads;
import item.mint.ItemFactory;
import item.render.LoreRenderer;
import item.render.LoreStyle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import platform.item.ItemGroups;
import platform.lang.Messages;
import platform.text.Colors;
import schema.spec.D;
import schema.spec.ParamSpec;

/**
 * The mask service's mint token rendering + gesture outcome shapes (ADR-0053 §3, §6) over a real compiled
 * {@link Library}, real codecs on the in-memory store, and a fake {@link TexturedHeads} — no server. The
 * likeness templates are TEST-OWNED (the {@code {COLOR}}/{@code {NAME}}/{@code {DESCRIPTION}}/{@code {APPLIES}}
 * substitution is the algorithm under test, writing-tests Rule 1); expected messages come from the same
 * {@link Messages} instance the service formats with. Item stacks are mocks: name/lore land on a mocked
 * {@link ItemMeta}, identity on the identity-keyed store.
 */
@SuppressWarnings("deprecation") // setDisplayName/setLore: the floor-stable item-meta path production decorates with
class MaskServiceTest {

    private static final String MASK = "masks/midas";

    /** Test-owned likeness — distinctive shapes so a template/token mix-up cannot pass. */
    private static final MaskItemConfig LIKENESS = new MaskItemConfig(
            "{COLOR}[{NAME}]",
            List.of("{DESCRIPTION}", "applies {APPLIES}", "bonus {NAME_UPPER}"),
            "on {NAME}",
            true,
            new SoundCue("cue.apply", 1.0f, 1.0f),
            new SoundCue("cue.remove", 1.0f, 1.0f));

    /** A fake era seam that records the base64 it was asked for and returns a fixed head (or null). */
    private static final class FixedHeads implements TexturedHeads {
        final ItemStack head;
        String askedBase64;

        FixedHeads(ItemStack head) {
            this.head = head;
        }

        @Override
        public ItemStack head(String base64) {
            askedBase64 = base64;
            return head;
        }
    }

    @TempDir
    Path root;

    private final Messages messages = Messages.defaults();
    private FakeItemStateStore store;
    private MaskCodec maskCodec;
    private CombatCodec combatCodec;
    private ContentHolder holder;

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
        Library lib = LibraryLoader.load(root,
                Compiler.of(MapSpecRegistry.of(ParamSpec.of("HEAL").param("amount", D.DOUBLE.min(0)).build())), 1);
        holder = new ContentHolder(lib);
        store = new FakeItemStateStore();
        maskCodec = new MaskCodec(ItemKeys.of(), store);
        combatCodec = new CombatCodec("combat", store);
    }

    @AfterEach
    void restoreItemFactory() {
        ItemFactory.customItemResolver(null); // the inert default back, so other tests aren't perturbed
    }

    private MaskService service(TexturedHeads heads) {
        return service(heads, LIKENESS);
    }

    private MaskService service(TexturedHeads heads, MaskItemConfig cfg) {
        LoreRenderer lore = new LoreRenderer(
                LoreRenderer.Config.of(LoreStyle.DEFAULT, key -> holder.library().displayNameOf(key)), store);
        ItemEnchanter enchanter = new ItemEnchanter(combatCodec, lore, holder, ItemGroups.standard(),
                () -> ItemEnchanter.DEFAULT_BASE_SLOTS, () -> ItemEnchanter.DEFAULT_CRYSTAL_SLOTS,
                () -> ItemEnchanter.DEFAULT_MAX_MERGE, messages, item.mint.VanillaEnchants.NONE);
        return new MaskService(maskCodec, enchanter, holder, () -> cfg, heads, item.head.HeadEquip.NONE, messages);
    }

    /** A mocked stack with a mocked meta, so the decorate path (setDisplayName/setLore) is capturable. */
    private static ItemStack stackWithMeta(ItemMeta meta) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getItemMeta()).thenReturn(meta);
        return stack;
    }

    private static ItemStack gear(Material type, int amount) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        when(stack.getAmount()).thenReturn(amount);
        return stack;
    }

    /** A stamped mask cursor (amount 1) — what the apply gesture holds. */
    private ItemStack maskCursor() {
        ItemStack cursor = mock(ItemStack.class);
        when(cursor.getAmount()).thenReturn(1);
        maskCodec.stamp(cursor, MASK);
        return cursor;
    }

    @Test
    void mintRendersEveryLikenessToken() {
        ItemMeta meta = mock(ItemMeta.class);
        ItemStack head = stackWithMeta(meta);
        FixedHeads heads = new FixedHeads(head);

        ItemStack minted = service(heads).mint(MASK);

        assertSame(head, minted, "a textured server mints the era-seam head itself");
        assertEquals("aGVhZA==", heads.askedBase64, "the def's authored base64 feeds the seam");
        assertEquals(MASK, maskCodec.keyOf(minted), "identity stamped");
        // {COLOR}[{NAME}] over the authored def; {DESCRIPTION} line-expands; {APPLIES} is the HELMET kinds label;
        // {NAME_UPPER} upper-cases the bare display ("Midas" → "MIDAS", the SET-BONUS/pets header convention).
        verify(meta).setDisplayName(Colors.translate("&6[Midas]"));
        verify(meta).setLore(List.of(
                Colors.translate("&6&lMIDAS MASK"),
                Colors.translate("&6* Negates enemy heroic armor."),
                "applies " + ItemGroups.kindsLabel(List.of("HELMET")),
                "bonus MIDAS"));
    }

    @Test
    void mintFallsBackToTheDefsMaterialWhenHeadsAreUnsupported() {
        ItemStack fallback = stackWithMeta(mock(ItemMeta.class));
        when(fallback.clone()).thenReturn(fallback);
        List<String> askedTokens = new java.util.ArrayList<>();
        // The custom-item resolver hook short-circuits buildItem before any live Material registry touch
        // (the NametagStackGuardTest precedent), capturing the material TOKEN the fallback resolves.
        ItemFactory.customItemResolver(token -> {
            askedTokens.add(token);
            return fallback;
        });

        ItemStack minted = service(new FixedHeads(null)).mint(MASK);

        assertSame(fallback, minted);
        assertEquals(List.of("GOLDEN_HELMET"), askedTokens, "the def's material token, not PLAYER_HEAD");
        assertEquals(MASK, maskCodec.keyOf(minted), "identity stamped on the fallback too");
    }

    @Test
    void mintOfAnUnknownKeyIsNull() {
        assertNull(service(new FixedHeads(null)).mint("masks/ghost"));
    }

    @Test
    void applyStampsTheHelmetAndSpendsTheCursor() {
        MaskService service = service(new FixedHeads(stackWithMeta(null)));
        ItemStack cursor = maskCursor();
        ItemStack helmet = gear(Material.DIAMOND_HELMET, 1);

        GestureOutcome out = service.apply(cursor, helmet);

        assertTrue(out.commit());
        assertTrue(out.consumeCursor());
        assertSame(helmet, out.newTarget());
        assertNull(out.produced());
        assertEquals(LIKENESS.soundApply(), out.cue().sound());
        // the styled-name token: the def's colour + the universal bold join + its bare display
        assertEquals(messages.format("mask.apply-success", "MASK", "&6&lMidas"), out.message());
        verify(cursor).setAmount(0);
        assertEquals(MASK, combatCodec.read(helmet).maskKey());
    }

    @Test
    void ineligibleTargetsNoopAndNeverTouchTheCursor() {
        MaskService service = service(new FixedHeads(null));
        ItemStack cursor = maskCursor();

        // not a helmet
        GestureOutcome sword = service.apply(cursor, gear(Material.DIAMOND_SWORD, 1));
        assertFalse(sword.commit());
        assertFalse(sword.consumeCursor());
        assertEquals(messages.format("apply.not-applicable", "DISPLAY", "Midas"), sword.message());

        // a second mask
        ItemStack masked = gear(Material.DIAMOND_HELMET, 1);
        combatCodec.write(masked, CombatState.EMPTY.withMask(MASK));
        GestureOutcome already = service.apply(cursor, masked);
        assertFalse(already.commit());
        assertEquals(messages.format("mask.already"), already.message());

        verify(cursor, never()).setAmount(anyInt());
    }

    @Test
    void removeProducesTheMaskBackAndIsCursorless() {
        ItemStack head = stackWithMeta(mock(ItemMeta.class));
        MaskService service = service(new FixedHeads(head));
        ItemStack helmet = gear(Material.DIAMOND_HELMET, 1);
        combatCodec.write(helmet, CombatState.EMPTY.withMask(MASK));

        GestureOutcome out = service.remove(helmet);

        assertTrue(out.commit());
        assertFalse(out.consumeCursor(), "the remove gesture holds an EMPTY cursor — there is nothing to spend");
        assertSame(helmet, out.newTarget());
        assertSame(head, out.produced(), "the popped mask mints back as its item");
        assertEquals(MASK, maskCodec.keyOf(out.produced()));
        assertEquals(LIKENESS.soundRemove(), out.cue().sound());
        assertEquals(messages.format("mask.remove-success", "MASK", "&6&lMidas"), out.message());
        assertNull(combatCodec.read(helmet).maskKey(), "the helmet is cleaned");
    }

    @Test
    void removeOnAnUnmaskedHelmetNoops() {
        MaskService service = service(new FixedHeads(null));
        GestureOutcome out = service.remove(gear(Material.DIAMOND_HELMET, 1));
        assertFalse(out.commit());
        assertNull(out.produced());
        assertEquals(messages.format("mask.none"), out.message());
    }

    @Test
    void mutedSoundsProduceNoCue() {
        MaskItemConfig muted = new MaskItemConfig(LIKENESS.name(), LIKENESS.lore(), LIKENESS.loreWhileOnItem(),
                false, LIKENESS.soundApply(), LIKENESS.soundRemove());
        MaskService service = service(new FixedHeads(stackWithMeta(null)), muted);

        GestureOutcome out = service.apply(maskCursor(), gear(Material.DIAMOND_HELMET, 1));
        assertTrue(out.commit());
        assertNull(out.cue(), "sounds.enabled=false gates the cue entirely (the crystal rule)");
    }
}
