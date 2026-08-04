package feature.crystal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.load.ContentHolder;
import feature.apply.ExtractResult;
import feature.apply.GestureOutcome;
import feature.apply.ItemEnchanter;
import item.codec.CrystalExtractorCodec;
import item.codec.CrystalItemCodec;
import item.codec.CrystalItemData;
import java.util.List;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.lang.Messages;

/**
 * The extractor's branch ORDER on the ONE Item Extractor (ADR-0070/0074): a crystal-bearing ITEM splits first,
 * then a COMPOSITE MASK item, then a gear's REFORGE (the {@link ReforgeExtractor} hook) outranks its crystal
 * stack, then the last crystal entry. Pure routing over mocked codecs/enchanter and mock hooks — the contract is
 * which branch runs, not the downstream mint (covered by the service tests).
 */
class CrystalServiceExtractRoutingTest {

    private CrystalItemCodec codec;
    private ItemEnchanter enchanter;
    private ReforgeExtractor reforges;
    private MaskSplitter masks;
    private CrystalService service;

    @BeforeEach
    void build() {
        codec = mock(CrystalItemCodec.class);
        CrystalExtractorCodec extractorCodec = mock(CrystalExtractorCodec.class);
        enchanter = mock(ItemEnchanter.class);
        ContentHolder content = mock(ContentHolder.class);
        reforges = mock(ReforgeExtractor.class);
        masks = mock(MaskSplitter.class);
        service = new CrystalService(codec, extractorCodec, enchanter, content,
                () -> null, () -> 3, reforges, masks, Messages.defaults());
    }

    @Test
    void reforgeOutranksCrystalOnGear() {
        ItemStack cursor = mock(ItemStack.class);
        ItemStack gear = mock(ItemStack.class);
        when(codec.read(gear)).thenReturn(null); // gear is not a crystal ITEM
        when(reforges.carries(gear)).thenReturn(true);
        GestureOutcome sentinel = GestureOutcome.noop("REFORGE-HOOK");
        when(reforges.extract(cursor, gear)).thenReturn(sentinel);

        assertSame(sentinel, service.extract(cursor, gear), "the reforge hook handles a reforged weapon first");
        verify(enchanter, never()).extractCrystal(any()); // the crystal path never ran
    }

    @Test
    void crystalItemTargetIsHandledBeforeTheHook() {
        ItemStack cursor = mock(ItemStack.class);
        ItemStack crystalItem = mock(ItemStack.class);
        when(crystalItem.getAmount()).thenReturn(1);
        when(codec.read(crystalItem)).thenReturn(new CrystalItemData(List.of("crystals/frost"))); // a crystal ITEM

        GestureOutcome out = service.extract(cursor, crystalItem);

        assertFalse(out.commit()); // a single crystal item has nothing to split — an early noop, no minting
        verify(reforges, never()).carries(any());  // the crystal-item branch precedes the reforge hook
        verify(reforges, never()).extract(any(), any());
        verify(enchanter, never()).extractCrystal(any());
    }

    @Test
    void aCompositeMaskItemIsSplitBeforeTheReforgeHookAndSpendsTheCursor() {
        // ADR-0074: the mask side owns no cursor of its own, so the extractor is spent HERE — and only when the
        // split actually committed, or a refused split would silently eat the extractor.
        ItemStack cursor = mock(ItemStack.class);
        when(cursor.getAmount()).thenReturn(1);
        ItemStack maskItem = mock(ItemStack.class);
        when(codec.read(maskItem)).thenReturn(null); // not a crystal ITEM
        when(masks.carriesComposite(maskItem)).thenReturn(true);
        when(masks.split(maskItem)).thenReturn(GestureOutcome.committed(maskItem, "SPLIT"));

        GestureOutcome out = service.extract(cursor, maskItem);

        assertSame("SPLIT", out.message());
        verify(cursor).setAmount(0); // one extractor spent
        verify(reforges, never()).carries(any()); // the mask branch precedes the reforge hook
        verify(enchanter, never()).extractCrystal(any());
    }

    @Test
    void aRefusedMaskSplitSpendsNoExtractor() {
        ItemStack cursor = mock(ItemStack.class);
        ItemStack maskItem = mock(ItemStack.class);
        when(codec.read(maskItem)).thenReturn(null);
        when(masks.carriesComposite(maskItem)).thenReturn(true);
        when(masks.split(maskItem)).thenReturn(GestureOutcome.noop("nothing to split"));

        assertFalse(service.extract(cursor, maskItem).commit());
        verify(cursor, never()).setAmount(anyInt());
    }

    @Test
    void aPlainMaskIsNotClaimedAndFallsThrough() {
        // Only a FOLDED mask is the extractor's business; a plain one falls through to the ordinary gear path,
        // which then reports it carries no crystal — the same answer it gave before composites existed.
        ItemStack cursor = mock(ItemStack.class);
        ItemStack maskItem = mock(ItemStack.class);
        when(codec.read(maskItem)).thenReturn(null);
        when(masks.carriesComposite(maskItem)).thenReturn(false);
        when(reforges.carries(maskItem)).thenReturn(false);
        when(enchanter.extractCrystal(maskItem)).thenReturn(ExtractResult.fail("no crystals"));

        service.extract(cursor, maskItem);

        verify(masks, never()).split(any());
        verify(enchanter).extractCrystal(maskItem);
    }

    @Test
    void noReforgeFallsThroughToCrystals() {
        ItemStack cursor = mock(ItemStack.class);
        ItemStack gear = mock(ItemStack.class);
        when(codec.read(gear)).thenReturn(null);
        when(reforges.carries(gear)).thenReturn(false);
        when(enchanter.extractCrystal(gear)).thenReturn(ExtractResult.fail("no crystals"));

        GestureOutcome out = service.extract(cursor, gear);

        assertFalse(out.commit());
        verify(reforges, never()).extract(any(), any()); // the hook declined — it must not also extract
        verify(enchanter).extractCrystal(gear);           // the crystal fall-through ran
    }
}
