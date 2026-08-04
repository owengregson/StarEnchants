package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import compile.model.ScopeKinds;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import schema.spec.Args;
import testfx.Abilities;

class SuppressionStoreTest {

    private final SuppressionStore store = new SuppressionStore();
    private final UUID p = UUID.randomUUID();

    /** An ability with the given three cooldown-scope ids (-1 = no scope). */
    private static Ability scoped(int enchant, int group, int type) {
        Ability ability = mock(Ability.class);
        when(ability.cdScopeEnchant()).thenReturn(enchant);
        when(ability.cdScopeGroup()).thenReturn(group);
        when(ability.cdScopeType()).thenReturn(type);
        return ability;
    }

    /** An ability carrying one stamped effect per given dense kindId (no cooldown scopes) — the KIND match input. */
    private static Ability withKinds(int... kindIds) {
        CompiledEffect[] effects = new CompiledEffect[kindIds.length];
        for (int i = 0; i < kindIds.length; i++) {
            effects[i] = new CompiledEffect("K" + kindIds[i], Args.empty(), CompiledSelector.SELF, 0,
                    Affinity.CONTEXT_LOCAL, kindIds[i]);
        }
        return Abilities.ability().effects(effects).build();
    }

    @Test
    void unsuppressedIdIsNotSuppressed() {
        assertFalse(store.isSuppressed(p, 1, 0L));
    }

    @Test
    void suppressedIdIsSuppressedUntilExpiry() {
        store.suppress(p, 1, 100L, 40);
        assertTrue(store.isSuppressed(p, 1, 100L));
        assertTrue(store.isSuppressed(p, 1, 139L));
        assertFalse(store.isSuppressed(p, 1, 140L));
    }

    @Test
    void idsAreIndependent() {
        store.suppress(p, 7, 0L, 100);
        assertTrue(store.isSuppressed(p, 7, 0L));
        assertFalse(store.isSuppressed(p, 8, 0L));
    }

    @Test
    void playersAreIndependent() {
        UUID q = UUID.randomUUID();
        store.suppress(p, 1, 0L, 100);
        assertTrue(store.isSuppressed(p, 1, 0L));
        assertFalse(store.isSuppressed(q, 1, 0L));
    }

    @Test
    void zeroOrNegativeDurationSuppressesNothing() {
        store.suppress(p, 1, 0L, 0);
        store.suppress(p, 1, 0L, -5);
        assertFalse(store.isSuppressed(p, 1, 0L));
    }

    @Test
    void reSuppressingExtendsButNeverShortens() {
        store.suppress(p, 1, 0L, 100);
        store.suppress(p, 1, 10L, 20); // a shorter re-suppress (would expire at 30) must NOT shorten the live window
        assertTrue(store.isSuppressed(p, 1, 50L));
        assertFalse(store.isSuppressed(p, 1, 100L)); // the original expiry still governs

        store.suppress(p, 1, 0L, 100);
        store.suppress(p, 1, 0L, 200); // a longer re-suppress extends the window
        assertTrue(store.isSuppressed(p, 1, 150L));
        assertFalse(store.isSuppressed(p, 1, 200L));
    }

    @Test
    void elapsedSuppressionIsEvictedLazily() {
        store.suppress(p, 1, 0L, 10);
        assertFalse(store.isSuppressed(p, 1, 10L)); // elapsed read evicts the entry
        assertFalse(store.isSuppressed(p, 1, 100L));
        store.suppress(p, 1, 100L, 5); // a fresh suppress after eviction still arms cleanly
        assertTrue(store.isSuppressed(p, 1, 100L));
    }

    @Test
    void immunePlayerCannotBeSuppressedAndArmingClearsExistingSuppression() {
        store.suppress(p, 1, 0L, 100);
        assertTrue(store.isSuppressed(p, 1, 0L)); // suppressed before immunity
        store.setImmune(p, 100);
        assertTrue(store.isImmune(p));
        assertFalse(store.isSuppressed(p, 1, 0L)); // arming immunity dropped the existing suppression
        store.suppress(p, 1, 0L, 100);             // and a fresh suppress is vetoed at the write
        assertFalse(store.isSuppressed(p, 1, 0L));
    }

    @Test
    void partialImmunityRollsPerSuppressionAndIsNotAbsolute() {
        store.setImmune(p, 50); // 50% chance to ignore each suppression (ADR-0034 crystal "ignore Silence")
        assertFalse(store.isImmune(p), "a partial chance is not ABSOLUTE immunity");
        int landed = 0;
        for (int id = 0; id < 600; id++) {         // distinct ids so each is an independent roll, not an extend
            store.suppress(p, id, 0L, 100);
            if (store.isSuppressed(p, id, 0L)) {
                landed++;
            }
        }
        // The roll actually varies — a "never vetoes" (0) or "always vetoes" (600) bug is caught. ~300 expected;
        // the wide band never flakes.
        int total = landed;
        assertTrue(total > 120 && total < 480, () -> "landed=" + total);
    }

    @Test
    void partialImmunityDoesNotClearExistingSuppression() {
        store.suppress(p, 1, 0L, 100);
        store.setImmune(p, 50);                    // only ABSOLUTE (>=100) immunity drops a live suppression
        assertTrue(store.isSuppressed(p, 1, 0L));
    }

    @Test
    void liftingImmunityRestoresSuppressibility() {
        store.setImmune(p, 100);
        store.suppress(p, 1, 0L, 100);
        assertFalse(store.isSuppressed(p, 1, 0L)); // vetoed while immune
        store.setImmune(p, 0);
        assertFalse(store.isImmune(p));
        store.suppress(p, 1, 0L, 100);
        assertTrue(store.isSuppressed(p, 1, 0L));   // suppressible again
    }

    @Test
    void clearAndClearAllLiftImmunity() {
        store.setImmune(p, 100);
        store.clear(p);
        assertFalse(store.isImmune(p));
        store.setImmune(p, 100);
        store.clearAll();
        assertFalse(store.isImmune(p));
    }

    @Test
    void suppressesAnyChecksTheEnchantScope() {
        store.suppress(p, CooldownStore.key(ScopeKinds.ENCHANT, 7), 0L, 200);
        assertTrue(store.suppressesAny(scoped(7, -1, -1), p, 50L));
        assertFalse(store.suppressesAny(scoped(8, -1, -1), p, 50L), "a different scope id is not suppressed");
    }

    @Test
    void suppressesAnyIsFalseWhenNoScopeIsSet() {
        store.suppress(p, CooldownStore.key(ScopeKinds.ENCHANT, 0), 0L, 200);
        assertFalse(store.suppressesAny(scoped(-1, -1, -1), p, 50L), "an ability with no scopes is never suppressed");
    }

    @Test
    void suppressesAnyChecksTheGroupScope() {
        store.suppress(p, CooldownStore.key(ScopeKinds.GROUP, 3), 0L, 200);
        assertTrue(store.suppressesAny(scoped(-1, 3, -1), p, 50L), "group-scope suppression flips the group-scoped ability");
        assertFalse(store.suppressesAny(scoped(3, -1, -1), p, 50L), "the same id under the ENCHANT kind is a different key");
    }

    @Test
    void aSuppressImmuneAbilityIsNeverSuppressed() {
        // suppress-immune: true (permanent buffs vs Silence & derivatives): the flagged enchant survives a DISABLE
        // window that silences a same-group NORMAL enchant — the buff lives while OTHER enchants are still silenced.
        store.suppress(p, CooldownStore.key(ScopeKinds.GROUP, 3), 0L, 200); // a Silence-style GROUP disable
        assertTrue(store.suppressesAny(scoped(-1, 3, -1), p, 50L), "a normal group-3 enchant is silenced");
        Ability immune = scoped(-1, 3, -1);
        when(immune.suppressImmune()).thenReturn(true);
        assertFalse(store.suppressesAny(immune, p, 50L), "but a suppress-immune group-3 enchant survives the same window");
    }

    @Test
    void blockedDetailReportsTheArmedScopeAndSuppressorDefId() {
        store.suppress(p, CooldownStore.key(ScopeKinds.GROUP, 3), 0L, 200, 42);
        long d = store.blockedDetail(scoped(-1, 3, -1), p, 50L);
        assertEquals(ScopeKinds.GROUP, SuppressionStore.detailScopeKind(d));
        assertEquals(3, SuppressionStore.detailScopeId(d));
        assertEquals(42, SuppressionStore.detailByDefId(d));
    }

    @Test
    void blockedDetailIsZeroWhenNoScopeIsBlocked() {
        assertEquals(0L, store.blockedDetail(scoped(7, -1, -1), p, 0L));
    }

    @Test
    void blockedDetailIsZeroOnceTheWindowExpires() {
        store.suppress(p, CooldownStore.key(ScopeKinds.ENCHANT, 5), 0L, 40, 9);
        assertTrue(store.blockedDetail(scoped(5, -1, -1), p, 0L) != 0L); // active
        assertEquals(0L, store.blockedDetail(scoped(5, -1, -1), p, 40L)); // half-open: expired at 40
    }

    @Test
    void fourArgSuppressIsUnattributed() {
        store.suppress(p, CooldownStore.key(ScopeKinds.TYPE, 2), 0L, 100); // no byDefId
        long d = store.blockedDetail(scoped(-1, -1, 2), p, 10L);
        assertEquals(ScopeKinds.TYPE, SuppressionStore.detailScopeKind(d));
        assertEquals(2, SuppressionStore.detailScopeId(d));
        assertEquals(-1, SuppressionStore.detailByDefId(d));
    }

    // ── ADR-0049 Neutralize one-shot (SUPPRESS mode next-hit): event-scoped, never timed ──

    @Test
    void armedOneShotBlocksAtGateFiveAndClearsAfterOneEvent() {
        long key = CooldownStore.key(ScopeKinds.ENCHANT, 7);
        store.armOneShot(p, key, 1, 42);
        assertTrue(store.suppressesAny(scoped(7, -1, -1), p, 0L), "an armed one-shot blocks at gate 5");
        assertTrue(store.suppressesAny(scoped(7, -1, -1), p, 5L), "gate 5 is a pure read — it does not consume the charge");
        store.consumeEventScoped(p);
        assertFalse(store.suppressesAny(scoped(7, -1, -1), p, 10L), "one event consumes the single charge");
    }

    @Test
    void armedOneShotWithTwoChargesSurvivesOneEvent() {
        long key = CooldownStore.key(ScopeKinds.GROUP, 3);
        store.armOneShot(p, key, 2, 9);
        store.consumeEventScoped(p);
        assertTrue(store.suppressesAny(scoped(-1, 3, -1), p, 0L), "charges=2 survives one event");
        store.consumeEventScoped(p);
        assertFalse(store.suppressesAny(scoped(-1, 3, -1), p, 0L), "and clears after the second");
    }

    @Test
    void armedOneShotNeverExpiresByTime() {
        store.armOneShot(p, CooldownStore.key(ScopeKinds.TYPE, 2), 1, -1);
        // Event-scoped, not timed: even a far-future read still blocks — only consumeEventScoped clears it.
        assertTrue(store.suppressesAny(scoped(-1, -1, 2), p, 1_000_000L));
    }

    @Test
    void blockedDetailAttributesTheOneShotSuppressor() {
        store.armOneShot(p, CooldownStore.key(ScopeKinds.ENCHANT, 5), 1, 77);
        long d = store.blockedDetail(scoped(5, -1, -1), p, 0L);
        assertEquals(ScopeKinds.ENCHANT, SuppressionStore.detailScopeKind(d));
        assertEquals(5, SuppressionStore.detailScopeId(d));
        assertEquals(77, SuppressionStore.detailByDefId(d));
    }

    @Test
    void laterExpiryMergeAdoptsTheLaterWindowsDefId() {
        long key = CooldownStore.key(ScopeKinds.ENCHANT, 1);
        store.suppress(p, key, 0L, 50, 11);   // expiry 50, by 11
        store.suppress(p, key, 0L, 200, 22);  // longer → adopts by 22
        assertEquals(22, SuppressionStore.detailByDefId(store.blockedDetail(scoped(1, -1, -1), p, 60L)));

        store.suppress(p, key, 0L, 20, 33);   // shorter (expiry 20) → the live 200-window stands
        assertEquals(22, SuppressionStore.detailByDefId(store.blockedDetail(scoped(1, -1, -1), p, 60L)));
    }

    @Test
    void clearForgetsOnePlayerAndClearAllForgetsEveryone() {
        UUID q = UUID.randomUUID();
        store.suppress(p, 1, 0L, 100);
        store.suppress(q, 1, 0L, 100);
        store.clear(p);
        assertFalse(store.isSuppressed(p, 1, 0L));
        assertTrue(store.isSuppressed(q, 1, 0L));
        store.clearAll();
        assertFalse(store.isSuppressed(q, 1, 0L));
    }

    @Test
    void clearImmunityLiftsImmunityButLeavesWindows() {
        store.suppress(p, 1, 0L, 100);
        store.setImmune(p, 50); // partial, so it doesn't drop the live window
        store.clearImmunity(p);
        assertTrue(store.isSuppressed(p, 1, 50L), "the suppression window survives the immunity clear");

        // absolute immunity, then clearImmunity, lifts it fully
        store.setImmune(p, 100);
        assertTrue(store.isImmune(p));
        store.clearImmunity(p);
        assertFalse(store.isImmune(p));
    }

    @Test
    void evictElapsedDropsElapsedWindowsButKeepsLiveOnes() {
        store.suppress(p, 1, 0L, 40);   // expires at 40
        store.suppress(p, 2, 0L, 200);  // expires at 200
        store.evictElapsed(p, 100L);
        assertFalse(store.isSuppressed(p, 1, 100L)); // elapsed dropped
        assertTrue(store.isSuppressed(p, 2, 100L));  // live kept
    }

    @Test
    void evictElapsedLeavesImmunityUntouched() {
        store.setImmune(p, 100); // windows only — immunity is quit-volatile, cleared separately
        store.evictElapsed(p, 100L);
        assertTrue(store.isImmune(p), "the sweep evicts elapsed windows, never immunity");
    }

    @Test
    void evictElapsedAllPlayersSweepsElapsedButKeepsLive() {
        UUID q = UUID.randomUUID();
        store.suppress(p, 1, 0L, 40);   // expires at 40
        store.suppress(q, 1, 0L, 200);  // expires at 200
        store.evictElapsed(100L);
        assertFalse(store.isSuppressed(p, 1, 100L));
        assertTrue(store.isSuppressed(q, 1, 100L));
    }

    // ── ADR-0053 KIND scope: suppression by effect kindId, matched against the ability's compiled effects ──

    @Test
    void kindWindowSuppressesAbilitiesCarryingThatEffectKindUntilExpiry() {
        store.suppressKind(p, 7, 100L, 40, -1);
        assertTrue(store.suppressesAny(withKinds(7), p, 100L));
        assertTrue(store.suppressesAny(withKinds(3, 7), p, 139L), "any one matching effect kind suffices");
        assertFalse(store.suppressesAny(withKinds(7), p, 140L), "half-open: the expiry tick is free");
        assertFalse(store.suppressesAny(withKinds(8), p, 100L), "a different effect kind is not suppressed");
    }

    @Test
    void kindSuppressionIsPerPlayer() {
        UUID q = UUID.randomUUID();
        store.suppressKind(p, 7, 0L, 100, -1);
        assertTrue(store.suppressesAny(withKinds(7), p, 0L));
        assertFalse(store.suppressesAny(withKinds(7), q, 0L));
    }

    @Test
    void kindFastPathNeverWalksEffectsWhileNoKindWindowExists() {
        // Fast-path contract: with no KIND state anywhere, an ability whose effects would NPE is never
        // walked — scoped() mocks return null effects(), so a missing fast-path fails this test loudly.
        assertFalse(store.suppressesAny(scoped(-1, -1, -1), p, 0L));
        store.suppress(p, CooldownStore.key(ScopeKinds.ENCHANT, 7), 0L, 200); // non-KIND state must not open the walk
        assertFalse(store.suppressesAny(scoped(8, -1, -1), p, 50L));
    }

    @Test
    void unstampedEffectsNeverMatchAKindWindow() {
        store.suppressKind(p, 7, 0L, 100, -1);
        assertFalse(store.suppressesAny(withKinds(-1), p, 0L), "kindId -1 (head-fallback path) never matches");
        assertFalse(store.suppressesAny(withKinds(), p, 0L), "no effects, nothing to match");
    }

    @Test
    void isKindSuppressedReadsOneKindAndEvictsLazily() {
        store.suppressKind(p, 5, 0L, 40, -1);
        assertTrue(store.isKindSuppressed(p, 5, 0L));
        assertFalse(store.isKindSuppressed(p, 5, 40L)); // elapsed read evicts the entry
        store.suppressKind(p, 5, 40L, 10, -1);          // a fresh arm after eviction still lands
        assertTrue(store.isKindSuppressed(p, 5, 40L));
    }

    @Test
    void kindReArmExtendsButNeverShortens() {
        store.suppressKind(p, 7, 0L, 100, -1);
        store.suppressKind(p, 7, 10L, 20, -1); // would expire at 30 — must not shorten
        assertTrue(store.suppressesAny(withKinds(7), p, 50L));
        assertFalse(store.suppressesAny(withKinds(7), p, 100L));
    }

    @Test
    void kindOneShotBlocksAtGateFiveAndBurnsPerEvent() {
        store.armOneShotKind(p, 7, 1, 42);
        assertTrue(store.suppressesAny(withKinds(7), p, 0L), "an armed KIND one-shot blocks at gate 5");
        assertTrue(store.suppressesAny(withKinds(7), p, 1_000_000L), "event-scoped, never timed");
        store.consumeEventScoped(p);
        assertFalse(store.suppressesAny(withKinds(7), p, 0L), "one event consumes the single charge");
    }

    @Test
    void blockedDetailReportsTheKindScopeAndSuppressor() {
        store.suppressKind(p, 9, 0L, 200, 42);
        long d = store.blockedDetail(withKinds(3, 9), p, 50L);
        assertEquals(ScopeKinds.KIND, SuppressionStore.detailScopeKind(d));
        assertEquals(9, SuppressionStore.detailScopeId(d));
        assertEquals(42, SuppressionStore.detailByDefId(d));
    }

    @Test
    void absoluteImmunityVetoesKindSuppressionAndClearsExistingWindows() {
        store.suppressKind(p, 7, 0L, 100, -1);
        assertTrue(store.suppressesAny(withKinds(7), p, 0L));
        store.setImmune(p, 100);
        assertFalse(store.suppressesAny(withKinds(7), p, 0L), "arming immunity drops the existing KIND window");
        store.suppressKind(p, 7, 0L, 100, -1);
        assertFalse(store.suppressesAny(withKinds(7), p, 0L), "and a fresh KIND suppress is vetoed at the write");
    }

    @Test
    void clearAndClearAllDropKindState() {
        UUID q = UUID.randomUUID();
        store.suppressKind(p, 7, 0L, 100, -1);
        store.armOneShotKind(q, 8, 1, -1);
        store.clear(p);
        assertFalse(store.suppressesAny(withKinds(7), p, 0L));
        assertTrue(store.suppressesAny(withKinds(8), q, 0L));
        store.clearAll();
        assertFalse(store.suppressesAny(withKinds(8), q, 0L));
    }

    @Test
    void evictElapsedSweepsElapsedKindWindowsButKeepsLive() {
        store.suppressKind(p, 1, 0L, 40, -1);  // expires at 40
        store.suppressKind(p, 2, 0L, 200, -1); // expires at 200
        store.evictElapsed(p, 100L);
        assertFalse(store.isKindSuppressed(p, 1, 100L));
        assertTrue(store.isKindSuppressed(p, 2, 100L));
    }

    /** A defender-keyed consult that never fails its draw, so a call sees exactly what is live. */
    private static java.util.function.DoubleSupplier neverFails() {
        return () -> -1.0;
    }

    @Test
    void aSecondArmOnOneDefenderKeyNeverWeakensWhatIsAlreadyLive() {
        // The shipped case: a set arms (ENCHANT, ice-aspect) at an absolute 100 and its OWN crystal re-arms the
        // same triple at 10, from two independent abilities on the same repeat cadence. Keeping the later
        // record whole left a wearer who completed the set AND carried its crystal permanently worse off than
        // one wearing the set alone — 100% freeze immunity degraded to 10% — decided purely by arm order.
        Ability incoming = scoped(4, -1, -1);
        SuppressionStore.Feedback setLine =
                new SuppressionStore.Feedback(p, "actor", "victim", SuppressionStore.Feedback.NO_SOUND);
        long key = CooldownStore.key(ScopeKinds.ENCHANT, 4);

        store.defend(p, key, 0L, 60, 100, 11, setLine);
        store.defend(p, key, 0L, 60, 10, 22, null);
        SuppressionStore.Matched governing = store.defenderBlocks(incoming, p, 20L, neverFails());
        assertEquals(11, governing.byDefId(), "the stronger chance keeps its own attribution");
        assertEquals(setLine, governing.feedback(), "and its own block line");

        // …and the other arm order gives the identical answer, which is the whole point.
        SuppressionStore reversed = new SuppressionStore();
        reversed.defend(p, key, 0L, 60, 10, 22, null);
        reversed.defend(p, key, 0L, 60, 100, 11, setLine);
        SuppressionStore.Matched other = reversed.defenderBlocks(incoming, p, 20L, neverFails());
        assertEquals(11, other.byDefId());
        assertEquals(setLine, other.feedback());
    }

    @Test
    void aStrongerButShorterArmKeepsTheLongerWindowItLandsOn() {
        // Never-weaken is about BOTH axes: adopting the stronger chance must not shorten the live window, and
        // extending it must not silently hand the extension the weaker chance.
        Ability incoming = scoped(4, -1, -1);
        long key = CooldownStore.key(ScopeKinds.ENCHANT, 4);
        store.defend(p, key, 0L, 1000, 10, 22, null);
        store.defend(p, key, 0L, 40, 100, 11, null);

        assertEquals(11, store.defenderBlocks(incoming, p, 20L, neverFails()).byDefId());
        assertEquals(11, store.defenderBlocks(incoming, p, 500L, neverFails()).byDefId(),
                "the longer window survives the stronger arm");
        assertEquals(null, store.defenderBlocks(incoming, p, 1000L, neverFails()),
                "and still expires when the longest arm says it does");
    }

    @Test
    void theSameNeverWeakenMergeGovernsAKindKeyedWindow() {
        store.defendKind(p, 8, 0L, 60, 10, 22, null);
        store.defendKind(p, 8, 0L, 60, 100, 11, null);
        assertEquals(11, store.defenderBlocks(withKinds(8), p, 20L, neverFails()).byDefId());
    }

    @Test
    void kindSuppressNotifiesTheMaintainedBuffListener() {
        // The PassiveEffectDriver mirror relies on onSuppress to drop a KIND-suppressed maintained POTION
        // immediately (ADR-0053) — a silent KIND write would leave the buff up until the next sweep.
        int[] notified = {0};
        store.onSuppress((player, durationTicks) -> notified[0]++);
        store.suppressKind(p, 7, 0L, 100, -1);
        store.armOneShotKind(p, 8, 1, -1);
        assertEquals(2, notified[0]);
    }
}
