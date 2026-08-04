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
            "{COLOR}<{NAME}>", // the composite name template — a distinct frame, so a mixed-up branch shows
            List.of("{DESCRIPTION}", "applies {APPLIES}", "bonus {NAME_UPPER}"),
            "on {NAME}",
            "on many {NAME}",
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

    /** A seam handing back a FRESH stack per call — a split mints TWO items, and one shared mock would let the
     *  second stamp overwrite the first, hiding a real mix-up behind a fixture artefact. */
    private static final class FreshHeads implements TexturedHeads {
        @Override
        public ItemStack head(String base64) {
            return stackWithMeta(mock(ItemMeta.class));
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
        // A SECOND mask, so a composite has two real children with distinct colours, displays and blocks —
        // enough that a fold rendering only one of them, or reading them in one colour, fails visibly.
        Path second = root.resolve("masks/blaze.yml");
        Files.writeString(second, """
            display: "Blaze"
            color: "&c"
            head: "YmxhemU="
            material: GOLDEN_HELMET
            description:
              - "&c&lBLAZE MASK"
            trigger: ATTACK
            effects: [{ HEAL: { amount: 2 } }]
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
                () -> ItemEnchanter.DEFAULT_MAX_MERGE,
                () -> compile.load.MasterConfig.ReforgesSection.defaults().weaponGroups(),
                messages, item.mint.VanillaEnchants.NONE);
        return new MaskService(maskCodec, enchanter, holder, () -> cfg, () -> 2, heads,
                item.head.HeadEquip.NONE, messages);
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

    // ── ADR-0074 composites: mask-onto-mask folds, the extractor splits, and the fold wears one face ──

    /** A stamped mask cursor (amount 1) carrying {@code entry} — a plain key or a folded one. */
    private ItemStack maskItem(String entry) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getAmount()).thenReturn(1);
        maskCodec.stamp(stack, entry);
        return stack;
    }

    @Test
    void maskOntoMaskFoldsWithTheCursorOnTopAndKeepsTheTargetsFace() {
        FixedHeads heads = new FixedHeads(stackWithMeta(mock(ItemMeta.class)));
        MaskService service = service(heads);

        GestureOutcome out = service.interact(maskItem("masks/blaze"), maskItem(MASK));

        assertTrue(out.commit());
        assertTrue(out.consumeCursor(), "the folded-in mask is spent, like a merged crystal");
        assertEquals("masks/midas+masks/blaze", maskCodec.keyOf(out.newTarget()),
                "the TARGET keeps its place and the cursor lands on top");
        // The composite wears the FIRST child's face (owner ruling): folding Blaze onto Midas must not repaint
        // the wearer as Blaze, or the gesture would silently change what they look like.
        assertEquals("aGVhZA==", heads.askedBase64);
        assertEquals(LIKENESS.soundApply(), out.cue().sound());
    }

    @Test
    void aFoldPastTheCapIsRefusedAndSpendsNothing() {
        MaskService service = service(new FixedHeads(stackWithMeta(mock(ItemMeta.class))));
        ItemStack cursor = maskItem("masks/blaze");

        GestureOutcome out = service.interact(cursor, maskItem("masks/midas+masks/blaze")); // 2 + 1 > cap 2

        assertFalse(out.commit());
        assertFalse(out.consumeCursor(), "a refused fold must never eat the mask it refused");
        assertEquals(messages.format("mask.merge-cap", "MAX", 2), out.message());
    }

    @Test
    void aStackedFoldTargetIsRefused() {
        MaskService service = service(new FixedHeads(stackWithMeta(mock(ItemMeta.class))));
        ItemStack stacked = maskItem("masks/midas");
        when(stacked.getAmount()).thenReturn(3); // which of the three would the fold land on?

        GestureOutcome out = service.interact(maskItem("masks/blaze"), stacked);

        assertFalse(out.commit());
        assertEquals(messages.format("mask.merge-single"), out.message());
    }

    @Test
    void theExtractorSplitsTheTopmostChildBackOff() {
        // Folding is a 100%-commit gesture that spends the cursor, so it has to be undoable — otherwise a
        // mis-fold is permanent. The Multi Crystal split (ADR-0035 §3), for masks.
        MaskService service = service(new FreshHeads());

        GestureOutcome out = service.split(maskItem("masks/midas+masks/blaze"));

        assertTrue(out.commit());
        assertEquals("masks/midas", maskCodec.keyOf(out.newTarget()), "the item becomes the remainder");
        assertEquals("masks/blaze", maskCodec.keyOf(out.produced()), "the topmost child comes back");
    }

    @Test
    void theExtractorDeclinesAPlainMask() {
        MaskService service = service(new FixedHeads(stackWithMeta(mock(ItemMeta.class))));
        assertFalse(service.carriesComposite(maskItem(MASK)), "one child is nothing to split");
        assertTrue(service.carriesComposite(maskItem("masks/midas+masks/blaze")));

        GestureOutcome out = service.split(maskItem(MASK));
        assertFalse(out.commit());
        assertEquals(messages.format("mask.split-not-multi"), out.message());
    }

    @Test
    void applyingACompositeStampsTheWholeEntryAndPopsItBackWhole() {
        MaskService service = service(new FixedHeads(stackWithMeta(mock(ItemMeta.class))));
        ItemStack helmet = gear(Material.DIAMOND_HELMET, 1);

        GestureOutcome applied = service.interact(maskItem("masks/midas+masks/blaze"), helmet);
        assertTrue(applied.commit());
        assertEquals("masks/midas+masks/blaze", combatCodec.read(helmet).maskKey(),
                "one socket, one entry — a composite is still ONE occupancy");

        // The whole-entry convention: a composite comes back as ONE composite, never N loose masks to re-fold.
        GestureOutcome popped = service.remove(helmet);
        assertTrue(popped.commit());
        assertEquals("masks/midas+masks/blaze", maskCodec.keyOf(popped.produced()));
        assertNull(combatCodec.read(helmet).maskKey());
    }

    @Test
    void aCompositeRendersEveryChildInItsOwnColour() {
        ItemMeta meta = mock(ItemMeta.class);
        MaskService service = service(new FixedHeads(stackWithMeta(meta)));

        service.mint("masks/midas+masks/blaze");

        // The composite template ({COLOR}<{NAME}>), {COLOR} from the FIRST child, {NAME} reading both children
        // each in its own colour — one {COLOR} cannot style N names, which is why the multi path differs.
        // The template opens with {COLOR}, not a literal &-code, so StyledNames finds no leading run and the
        // gap is a bare ", " — each child then supplies its own colour, which is the whole point.
        verify(meta).setDisplayName(Colors.translate("&6<&6&lMidas, &c&lBlaze>"));
        verify(meta).setLore(List.of(
                Colors.translate("&6&lMIDAS MASK"),
                Colors.translate("&6* Negates enemy heroic armor."),
                "",                                   // one blank line between the stacked blocks
                Colors.translate("&c&lBLAZE MASK"),
                "applies " + ItemGroups.kindsLabel(List.of("HELMET")),
                // NAME_UPPER upper-cases each child's WORDS, never the colour codes around them.
                Colors.translate("bonus &6&lMIDAS, &c&lBLAZE")));
    }

    @Test
    void applyingACompositeWithAStaleChildIsRefusedWhole() {
        // Half a composite is not a smaller composite: applying one whose second child no longer compiles would
        // silently drop an ability the wearer paid for, so the whole apply is refused.
        MaskService service = service(new FixedHeads(stackWithMeta(mock(ItemMeta.class))));
        ItemStack helmet = gear(Material.DIAMOND_HELMET, 1);

        GestureOutcome out = service.interact(maskItem("masks/midas+masks/gone"), helmet);

        assertFalse(out.commit());
        assertEquals(messages.format("mask.no-such", "KEY", "masks/gone"), out.message());
        assertNull(combatCodec.read(helmet).maskKey());
    }

    @Test
    void mutedSoundsProduceNoCue() {
        MaskItemConfig muted = new MaskItemConfig(LIKENESS.name(), LIKENESS.nameMulti(), LIKENESS.lore(),
                LIKENESS.loreWhileOnItem(), LIKENESS.loreWhileOnItemMulti(),
                false, LIKENESS.soundApply(), LIKENESS.soundRemove());
        MaskService service = service(new FixedHeads(stackWithMeta(null)), muted);

        GestureOutcome out = service.apply(maskCursor(), gear(Material.DIAMOND_HELMET, 1));
        assertTrue(out.commit());
        assertNull(out.cue(), "sounds.enabled=false gates the cue entirely (the crystal rule)");
    }
}
