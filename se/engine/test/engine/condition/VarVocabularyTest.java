package engine.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.cond.VarBinding;
import compile.cond.VarKind;
import org.junit.jupiter.api.Test;

class VarVocabularyTest {

    @Test
    void assignsDensePerKindSlotsInOrder() {
        VarVocabulary v = VarVocabulary.builder()
                .number("a").number("b")
                .flag("f1").flag("f2").flag("f3")
                .string("s")
                .build();
        assertEquals(2, v.numberSlots());
        assertEquals(3, v.flagSlots());
        assertEquals(1, v.stringSlots());
        assertEquals(new VarBinding(VarKind.NUM, 0), v.lookup(null, "a").orElseThrow());
        assertEquals(new VarBinding(VarKind.NUM, 1), v.lookup(null, "b").orElseThrow());
        assertEquals(new VarBinding(VarKind.BOOL, 2), v.lookup(null, "f3").orElseThrow());
        assertEquals(new VarBinding(VarKind.STR, 0), v.lookup(null, "s").orElseThrow());
    }

    @Test
    void lookupIsCaseInsensitiveAndScopeAware() {
        VarVocabulary v = VarVocabulary.builder().number("victim.health").flag("sneaking").build();
        assertTrue(v.lookup("victim", "health").isPresent());
        assertTrue(v.lookup("VICTIM", "HEALTH").isPresent());
        assertTrue(v.lookup(null, "SNEAKING").isPresent());
        assertTrue(v.lookup(null, "unknown").isEmpty());
    }

    @Test
    void builtinsHaveTheExpectedShape() {
        VarVocabulary v = BuiltinVars.vocabulary();
        // Slot counts are load-bearing: the FactBuffer is sized to them. Breakdown justifying 19/20/10 lives in
        // v3.1 §A (numeric/flag base + exotic-effect port), v3.7 §N (victim.mobtype string), the signature pack
        // sets (victim.inzone flag — devil's hellfire zone), ADR-0035 (actor.groundblock string — Frost "on ice"),
        // ADR-0049 (recentattackers/attackerindex numbers, behindvictim/itemdamage.armor flags, damagecause string),
        // §3 (ragestacks number — the rage-stack fact), ADR-0052 (actor.belowvictim number — the Eagle posture),
        // wave 1b.2 (nearbyallies number + victim.relation string, both off the one alliance predicate), and
        // wave 1b.3 (posthit.health + heldticks + actor/victim.souls + impactheight numbers,
        // victim.fromspawner flag, projectilekind string), wave 1c (equipchange string — the EQUIP_CHANGE
        // direction), wave 2b (item.durabilitypercent number — the ITEM_DAMAGE wear read), and wave 2c
        // (victim.heroicpieces number — the worn heroic-armour piece count), and wave 2d
        // (actor.heroicpieces + actor.y numbers — the actor-side piece tally and the absolute feet height —
        // plus the status.teleblock flag, STATUS_CLEAR's paired guard), and wave 2d.2 (actor.ownedground
        // flag — whether the temp block under the actor's feet is one the actor placed, plus
        // bookrate.generate/bookrate.apply — BOOK_RATE_MODIFIER's paired per-site armed guards), and wave 2e
        // (actor.setweapon flag — the held-set-weapon identity `on: weapon` gates a whole bonus on and no
        // chance expression could read), and wave 2e.2 (status.freeze flag — STATUS_CLEAR's FREEZE rung
        // paired guard, the teleblock flag's sibling), and wave 3a (soulcost number — gate 10's resolved price,
        // the one fact written from inside the gate walk rather than by the populator, R-QC2), and wave 3b-3
        // (proximityevent string — WHICH nearby event fired a PROXIMITY_EVENT, so an ally-death watcher and an
        // ally-bleeding watcher stop firing on each other's, R-QC46).
        assertEquals(30, v.numberSlots());
        assertEquals(27, v.flagSlots());
        assertEquals(14, v.stringSlots());
        assertEquals(VarKind.NUM, v.lookup("victim", "health").orElseThrow().kind());
        assertEquals(VarKind.NUM, v.lookup("actor", "maxhealth").orElseThrow().kind());
        assertEquals(VarKind.NUM, v.lookup("world", "time").orElseThrow().kind());
        assertEquals(VarKind.BOOL, v.lookup(null, "blocking").orElseThrow().kind());
        assertEquals(VarKind.BOOL, v.lookup("victim", "sneaking").orElseThrow().kind());
        assertEquals(VarKind.BOOL, v.lookup(null, "isblock").orElseThrow().kind());
        assertEquals(VarKind.STR, v.lookup("actor", "world").orElseThrow().kind());
        assertEquals(VarKind.STR, v.lookup("victim", "type").orElseThrow().kind());
        assertEquals(VarKind.STR, v.lookup("block", "type").orElseThrow().kind());
        assertEquals(VarKind.STR, v.lookup("actor", "groundblock").orElseThrow().kind()); // ADR-0035 "on ice"
        assertTrue(v.flagSlots() <= FactBuffer.MAX_FLAGS); // must fit the flag bitset
    }

    @Test
    void newFactBufferIsSizedToTheVocabulary() {
        VarVocabulary v = BuiltinVars.vocabulary();
        FactBuffer f = v.newFactBuffer();
        int healthSlot = v.lookup("victim", "health").orElseThrow().slot();
        f.setNumber(healthSlot, 12.0); // would AIOOBE if undersized
        assertEquals(12.0, f.number(healthSlot));
    }

    @Test
    void duplicateVariableFailsFast() {
        VarVocabulary.Builder b = VarVocabulary.builder().number("x");
        assertThrows(IllegalArgumentException.class, () -> b.flag("X")); // case-insensitive clash
    }
}
