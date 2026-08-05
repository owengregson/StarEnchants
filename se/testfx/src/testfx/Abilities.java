package testfx;

import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledCondition;
import compile.model.CompiledEffect;
import compile.model.FactMask;
import compile.model.SourceKind;

/**
 * Fluent builder for the runtime {@link Ability} record, mirroring {@link Defs}' idiom for the compiled shape.
 * The 19-field record grew with ADR-0039 (kindId/factMask), so re-hand-rolling it per test made a field add a
 * cross-file edit; a test now states only the fields it exercises.
 *
 * <p>ADR-0039 defaults: {@link FactMask#ALL} (populate everything) and an empty effect array — a test that
 * builds its own effects supplies kindId-tagged {@link CompiledEffect}s explicitly.
 */
public final class Abilities {

    private Abilities() {
    }

    /** An {@link Ability} (ENCHANT, level 1, 100% chance, no triggers, CONTEXT_LOCAL, FactMask.ALL). */
    public static Builder ability() {
        return new Builder();
    }

    public static final class Builder {
        private int id = 0;
        private int defId = 0;
        private SourceKind sourceKind = SourceKind.ENCHANT;
        private int triggerMask = 0;
        private int level = 1;
        private double baseChance = 100.0;
        private compile.model.cond.NumExpr chanceExpr = null;
        private int cooldownTicks = 0;
        private int soulCost = 0;
        private long worldBlacklist = 0L;
        private CompiledCondition condition = null;
        private CompiledEffect[] effects = new CompiledEffect[0];
        private int repeatTicks = 0;
        private int repeatDelayTicks = -1;
        private Affinity affinity = Affinity.CONTEXT_LOCAL;
        private int cdScopeEnchant = -1;
        private int cdScopeGroup = -1;
        private int cdScopeType = -1;
        private Integer sourceGroup = null; // null = mirror cdScopeGroup, the no-override shape (R-QC40)
        private int suppressKey = -1;
        private int setPieces = 0;
        private boolean suppressImmune = false;
        private FactMask factMask = FactMask.ALL;
        private String noSoulsMessage = null;
        private boolean soulCostCarried = false;
        private int noSoulsSound = -1;
        private int noSoulsParticle = -1;
        private double soulCostGrowth = 1.0;
        private int soulCostCap = 0;
        private int soulCostDecayPeriod = 0;
        private boolean cooldownPerVictim = false;

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder defId(int defId) {
            this.defId = defId;
            return this;
        }

        public Builder sourceKind(SourceKind sourceKind) {
            this.sourceKind = sourceKind;
            return this;
        }

        /** Add trigger {@code triggerId} to the mask ({@code mask |= 1 << triggerId}). */
        public Builder trigger(int triggerId) {
            this.triggerMask |= 1 << triggerId;
            return this;
        }

        public Builder triggerMask(int triggerMask) {
            this.triggerMask = triggerMask;
            return this;
        }

        public Builder level(int level) {
            this.level = level;
            return this;
        }

        public Builder chance(double baseChance) {
            this.baseChance = baseChance;
            return this;
        }

        /** An expression-valued {@code chance:}; {@code null} keeps the constant fast path. */
        public Builder chanceExpr(compile.model.cond.NumExpr chanceExpr) {
            this.chanceExpr = chanceExpr;
            return this;
        }

        public Builder cooldown(int cooldownTicks) {
            this.cooldownTicks = cooldownTicks;
            return this;
        }

        public Builder soulCost(int soulCost) {
            this.soulCost = soulCost;
            return this;
        }

        public Builder worldBlacklist(long worldBlacklist) {
            this.worldBlacklist = worldBlacklist;
            return this;
        }

        public Builder condition(CompiledCondition condition) {
            this.condition = condition;
            return this;
        }

        public Builder effects(CompiledEffect... effects) {
            this.effects = effects;
            return this;
        }

        public Builder repeatTicks(int repeatTicks) {
            this.repeatTicks = repeatTicks;
            return this;
        }

        public Builder affinity(Affinity affinity) {
            this.affinity = affinity;
            return this;
        }

        public Builder cooldownScope(int enchant, int group, int type) {
            this.cdScopeEnchant = enchant;
            this.cdScopeGroup = group;
            this.cdScopeType = type;
            return this;
        }

        /** Narrow this ability's IMPACT source scope away from its family group (the R-QC40 per-ability override). */
        public Builder sourceGroup(int sourceGroup) {
            this.sourceGroup = sourceGroup;
            return this;
        }

        public Builder suppressKey(int suppressKey) {
            this.suppressKey = suppressKey;
            return this;
        }

        public Builder setPieces(int setPieces) {
            this.setPieces = setPieces;
            return this;
        }

        public Builder suppressImmune(boolean suppressImmune) {
            this.suppressImmune = suppressImmune;
            return this;
        }

        public Builder factMask(FactMask factMask) {
            this.factMask = factMask;
            return this;
        }


        /** Charge the soul cost against CARRIED gems with no gem active ({@code soul-cost-carried}). */
        public Builder soulCostCarried(boolean soulCostCarried) {
            this.soulCostCarried = soulCostCarried;
            return this;
        }

        public Builder noSoulsSound(int noSoulsSound) {
            this.noSoulsSound = noSoulsSound;
            return this;
        }

        public Builder noSoulsParticle(int noSoulsParticle) {
            this.noSoulsParticle = noSoulsParticle;
            return this;
        }

        public Builder noSoulsMessage(String noSoulsMessage) {
            this.noSoulsMessage = noSoulsMessage;
            return this;
        }

        public Builder soulCostGrowth(double soulCostGrowth) {
            this.soulCostGrowth = soulCostGrowth;
            return this;
        }

        public Builder soulCostCap(int soulCostCap) {
            this.soulCostCap = soulCostCap;
            return this;
        }

        public Builder soulCostDecayPeriod(int soulCostDecayPeriod) {
            this.soulCostDecayPeriod = soulCostDecayPeriod;
            return this;
        }

        /** Ticks before a REPEATING ability's first run; {@code -1} (the default) is one full period. */
        public Builder repeatDelay(int repeatDelayTicks) {
            this.repeatDelayTicks = repeatDelayTicks;
            return this;
        }

        /** Key the cooldown on the victim rather than the coarse target bucket ({@code cooldown-per-victim}). */
        public Builder cooldownPerVictim(boolean cooldownPerVictim) {
            this.cooldownPerVictim = cooldownPerVictim;
            return this;
        }

        public Ability build() {
            return new Ability(id, defId, sourceKind, triggerMask, level, baseChance, cooldownTicks, soulCost,
                    worldBlacklist, condition, effects, repeatTicks, affinity, cdScopeEnchant, cdScopeGroup,
                    cdScopeType, suppressKey, setPieces, suppressImmune, factMask, chanceExpr, noSoulsMessage,
                    soulCostCarried, noSoulsSound, noSoulsParticle,
                    soulCostGrowth, soulCostCap, soulCostDecayPeriod, cooldownPerVictim, repeatDelayTicks,
                    sourceGroup == null ? cdScopeGroup : sourceGroup);
        }
    }
}
