package testfx;

import compile.model.FactMask;
import item.codec.HeroicStat;
import item.worn.WornState;
import java.util.Arrays;
import java.util.BitSet;

/**
 * Fluent builder for the runtime {@link WornState} record. Defaults produce an empty state; a test states only
 * the sources it exercises. {@code triggerFactMask} defaults to {@code null} — the ADR-0039 "populate
 * everything" default {@link WornState#factMask} reports as {@link FactMask#ALL}.
 */
public final class WornStates {

    private WornStates() {
    }

    /** An empty {@link WornState} (gen 0, no sets/crystals, HeroicStat.NONE, no triggers, null factMask). */
    public static Builder worn() {
        return new Builder();
    }

    public static final class Builder {
        private int gen = 0;
        private BitSet activeSets = new BitSet();
        private int[] crystalAbilityIds = new int[0];
        private HeroicStat heroic = HeroicStat.NONE;
        private int[][] byTrigger = new int[0][];
        private int[] combatAttack = new int[0];
        private int[] combatDefense = new int[0];
        private FactMask[] triggerFactMask = null;

        public Builder gen(int gen) {
            this.gen = gen;
            return this;
        }

        /** Mark {@code setId} active. */
        public Builder activeSet(int setId) {
            this.activeSets.set(setId);
            return this;
        }

        public Builder activeSets(BitSet activeSets) {
            this.activeSets = activeSets;
            return this;
        }

        public Builder crystalAbilityIds(int... ids) {
            this.crystalAbilityIds = ids;
            return this;
        }

        public Builder heroic(HeroicStat heroic) {
            this.heroic = heroic;
            return this;
        }

        public Builder byTrigger(int[][] byTrigger) {
            this.byTrigger = byTrigger;
            return this;
        }

        /** Set the ability ids for {@code triggerId}, auto-growing the {@code int[][]} (unset rows stay empty). */
        public Builder byTrigger(int triggerId, int... ids) {
            if (triggerId >= byTrigger.length) {
                int[][] grown = Arrays.copyOf(byTrigger, triggerId + 1);
                for (int i = 0; i < grown.length; i++) {
                    if (grown[i] == null) {
                        grown[i] = new int[0];
                    }
                }
                byTrigger = grown;
            }
            byTrigger[triggerId] = ids;
            return this;
        }

        public Builder combatAttack(int... combatAttack) {
            this.combatAttack = combatAttack;
            return this;
        }

        public Builder combatDefense(int... combatDefense) {
            this.combatDefense = combatDefense;
            return this;
        }

        public Builder triggerFactMask(FactMask[] triggerFactMask) {
            this.triggerFactMask = triggerFactMask;
            return this;
        }

        public WornState build() {
            return new WornState(gen, activeSets, crystalAbilityIds, heroic, byTrigger, combatAttack,
                    combatDefense, triggerFactMask);
        }
    }
}
