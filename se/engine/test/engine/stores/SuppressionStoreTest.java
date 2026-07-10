package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.model.Ability;
import compile.model.ScopeKinds;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
}
