package testfx;

import compile.def.AbilityDef;
import compile.model.Affinity;
import compile.model.CompiledCondition;
import compile.model.CompiledEffect;
import compile.model.SourceKind;
import compile.stage.LoweredAbility;
import schema.diag.Source;
import schema.grammar.EffectLine;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builders for the compiler's pre/mid-compilation shapes — {@link AbilityDef} (lower-stage input) and
 * {@link LoweredAbility} (erase-stage input). Each many-field record constructor was re-declared as a private
 * {@code def(...)}/{@code lowered(...)} helper in every stage test, so a new record field forced an edit across
 * all of them; routing construction through one builder makes a record-arity change a one-place change. Each
 * setter overrides a sensible default, so a test states only what it cares about.
 */
public final class Defs {

    private Defs() {
    }

    /** A pre-lowering {@link AbilityDef} (ENCHANT, level 1, 100% chance, single ATTACK trigger, no cooldown). */
    public static AbilityBuilder ability() {
        return new AbilityBuilder();
    }

    /** A post-lowering {@link LoweredAbility} (ENCHANT, level 0, no triggers/worlds, CONTEXT_LOCAL affinity). */
    public static LoweredBuilder lowered() {
        return new LoweredBuilder();
    }

    public static final class AbilityBuilder {
        private SourceKind sourceKind = SourceKind.ENCHANT;
        private String stableKey = "enchants/test";
        private int defId = 1;
        private int level = 1;
        private double baseChance = 100.0;
        private String chanceExpr = null;
        private int cooldownTicks = 0;
        private int soulCost = 0;
        private List<String> triggers = new ArrayList<>(List.of("ATTACK"));
        private List<String> worldBlacklist = new ArrayList<>();
        private String conditionExpr = null;
        private List<EffectLine> effects = new ArrayList<>();
        private List<String> rawEffects = null;
        private String suppressKey = null;
        private String cdScopeEnchant = null;
        private String cdScopeGroup = null;
        private String cdScopeType = null;
        private String sourceGroup = null; // null = the family group scopes IMPACT (R-QC40)
        private int repeatTicks = 0;
        private int repeatDelayTicks = -1;
        private Source source = Source.UNKNOWN;
        private int setPieces = 0;
        private boolean suppressImmune = false;
        private String noSoulsMessage = null;
        private boolean soulCostCarried = false;
        private String noSoulsSound = null;
        private String noSoulsParticle = null;
        private double soulCostGrowth = 1.0;
        private int soulCostCap = 0;
        private int soulCostDecayPeriod = 0;
        private boolean cooldownPerVictim = false;

        public AbilityBuilder sourceKind(SourceKind sourceKind) {
            this.sourceKind = sourceKind;
            return this;
        }

        public AbilityBuilder stableKey(String stableKey) {
            this.stableKey = stableKey;
            return this;
        }

        public AbilityBuilder defId(int defId) {
            this.defId = defId;
            return this;
        }

        public AbilityBuilder level(int level) {
            this.level = level;
            return this;
        }

        public AbilityBuilder chance(double baseChance) {
            this.baseChance = baseChance;
            return this;
        }

        /** Raw expression text for {@code chance:}; {@code null} keeps the constant fast path. */
        public AbilityBuilder chanceExpr(String chanceExpr) {
            this.chanceExpr = chanceExpr;
            return this;
        }

        public AbilityBuilder cooldown(int cooldownTicks) {
            this.cooldownTicks = cooldownTicks;
            return this;
        }

        public AbilityBuilder soulCost(int soulCost) {
            this.soulCost = soulCost;
            return this;
        }

        public AbilityBuilder triggers(String... triggers) {
            this.triggers = new ArrayList<>(List.of(triggers));
            return this;
        }

        public AbilityBuilder triggers(List<String> triggers) {
            this.triggers = new ArrayList<>(triggers);
            return this;
        }

        public AbilityBuilder worldBlacklist(String... worlds) {
            this.worldBlacklist = new ArrayList<>(List.of(worlds));
            return this;
        }

        public AbilityBuilder worldBlacklist(List<String> worlds) {
            this.worldBlacklist = new ArrayList<>(worlds);
            return this;
        }

        public AbilityBuilder condition(String conditionExpr) {
            this.conditionExpr = conditionExpr;
            return this;
        }

        /** Pre-parsed effect lines. */
        public AbilityBuilder effects(EffectLine... effects) {
            this.effects = new ArrayList<>(List.of(effects));
            this.rawEffects = null;
            return this;
        }

        /** Pre-parsed effect lines. */
        public AbilityBuilder effects(List<EffectLine> effects) {
            this.effects = new ArrayList<>(effects);
            this.rawEffects = null;
            return this;
        }

        /** Raw effect-line text, lexed against {@link #source(Source)} at {@link #build()}. */
        public AbilityBuilder effectLines(String... raw) {
            this.rawEffects = new ArrayList<>(List.of(raw));
            return this;
        }

        public AbilityBuilder suppressKey(String suppressKey) {
            this.suppressKey = suppressKey;
            return this;
        }

        public AbilityBuilder cooldownScope(String enchant, String group, String type) {
            this.cdScopeEnchant = enchant;
            this.cdScopeGroup = group;
            this.cdScopeType = type;
            return this;
        }

        /** Narrow this ability's IMPACT source scope away from its family group (the R-QC40 per-ability override). */
        public AbilityBuilder sourceGroup(String sourceGroup) {
            this.sourceGroup = sourceGroup;
            return this;
        }

        public AbilityBuilder repeatTicks(int repeatTicks) {
            this.repeatTicks = repeatTicks;
            return this;
        }

        public AbilityBuilder source(Source source) {
            this.source = source;
            return this;
        }

        public AbilityBuilder setPieces(int setPieces) {
            this.setPieces = setPieces;
            return this;
        }

        public AbilityBuilder suppressImmune(boolean suppressImmune) {
            this.suppressImmune = suppressImmune;
            return this;
        }


        /** Charge the soul cost against CARRIED gems with no gem active ({@code soul-cost-carried}). */
        public AbilityBuilder soulCostCarried(boolean soulCostCarried) {
            this.soulCostCarried = soulCostCarried;
            return this;
        }

        public AbilityBuilder noSoulsSound(String noSoulsSound) {
            this.noSoulsSound = noSoulsSound;
            return this;
        }

        public AbilityBuilder noSoulsParticle(String noSoulsParticle) {
            this.noSoulsParticle = noSoulsParticle;
            return this;
        }

        public AbilityBuilder noSoulsMessage(String noSoulsMessage) {
            this.noSoulsMessage = noSoulsMessage;
            return this;
        }

        public AbilityBuilder soulCostGrowth(double soulCostGrowth) {
            this.soulCostGrowth = soulCostGrowth;
            return this;
        }

        public AbilityBuilder soulCostCap(int soulCostCap) {
            this.soulCostCap = soulCostCap;
            return this;
        }

        public AbilityBuilder soulCostDecayPeriod(int soulCostDecayPeriod) {
            this.soulCostDecayPeriod = soulCostDecayPeriod;
            return this;
        }

        /** Key the cooldown on the victim rather than the coarse target bucket ({@code cooldown-per-victim}). */
        public AbilityBuilder cooldownPerVictim(boolean cooldownPerVictim) {
            this.cooldownPerVictim = cooldownPerVictim;
            return this;
        }

        public AbilityDef build() {
            List<EffectLine> lines = effects;
            if (rawEffects != null) {
                lines = new ArrayList<>();
                for (String raw : rawEffects) {
                    lines.add(EffectLine.parse(raw, source));
                }
            }
            return new AbilityDef(sourceKind, stableKey, defId, level, baseChance, cooldownTicks, soulCost,
                    triggers, worldBlacklist, conditionExpr, lines, suppressKey, cdScopeEnchant, cdScopeGroup,
                    cdScopeType, repeatTicks, source, setPieces, suppressImmune, chanceExpr, noSoulsMessage,
                    soulCostCarried, noSoulsSound, noSoulsParticle,
                    soulCostGrowth, soulCostCap, soulCostDecayPeriod, cooldownPerVictim, repeatDelayTicks,
                    sourceGroup);
        }
    }

    /** Builds a {@link LoweredAbility} — the erase stage's input, with effects/condition already compiled. */
    public static final class LoweredBuilder {
        private SourceKind sourceKind = SourceKind.ENCHANT;
        private String stableKey = "enchants/test";
        private int defId = 1;
        private int level = 0;
        private double baseChance = 0.0;
        private compile.model.cond.NumExpr loweredChanceExpr = null;
        private int cooldownTicks = 0;
        private int soulCost = 0;
        private List<String> triggers = new ArrayList<>();
        private List<String> worldBlacklist = new ArrayList<>();
        private CompiledCondition condition = null;
        private List<CompiledEffect> effects = new ArrayList<>();
        private String suppressKey = null;
        private String cdScopeEnchant = null;
        private String cdScopeGroup = null;
        private String cdScopeType = null;
        private String sourceGroup = null; // null = the family group scopes IMPACT (R-QC40)
        private int repeatTicks = 0;
        private int repeatDelayTicks = -1;
        private Affinity affinity = Affinity.CONTEXT_LOCAL;
        private Source source = Source.UNKNOWN;
        private int setPieces = 0;
        private boolean suppressImmune = false;
        private String noSoulsMessage = null;
        private boolean soulCostCarried = false;
        private int noSoulsSound = -1;
        private int noSoulsParticle = -1;
        private double soulCostGrowth = 1.0;
        private int soulCostCap = 0;
        private int soulCostDecayPeriod = 0;
        private boolean cooldownPerVictim = false;

        public LoweredBuilder sourceKind(SourceKind sourceKind) {
            this.sourceKind = sourceKind;
            return this;
        }

        public LoweredBuilder stableKey(String stableKey) {
            this.stableKey = stableKey;
            return this;
        }

        public LoweredBuilder defId(int defId) {
            this.defId = defId;
            return this;
        }

        public LoweredBuilder level(int level) {
            this.level = level;
            return this;
        }

        public LoweredBuilder chance(double baseChance) {
            this.baseChance = baseChance;
            return this;
        }

        /** The lowered expression evaluated at the chance gate; {@code null} keeps the constant fast path. */
        public LoweredBuilder chanceExpr(compile.model.cond.NumExpr chanceExpr) {
            this.loweredChanceExpr = chanceExpr;
            return this;
        }

        public LoweredBuilder cooldown(int cooldownTicks) {
            this.cooldownTicks = cooldownTicks;
            return this;
        }

        public LoweredBuilder soulCost(int soulCost) {
            this.soulCost = soulCost;
            return this;
        }

        public LoweredBuilder triggers(String... triggers) {
            this.triggers = new ArrayList<>(List.of(triggers));
            return this;
        }

        public LoweredBuilder triggers(List<String> triggers) {
            this.triggers = new ArrayList<>(triggers);
            return this;
        }

        public LoweredBuilder worldBlacklist(String... worlds) {
            this.worldBlacklist = new ArrayList<>(List.of(worlds));
            return this;
        }

        public LoweredBuilder worldBlacklist(List<String> worlds) {
            this.worldBlacklist = new ArrayList<>(worlds);
            return this;
        }

        public LoweredBuilder condition(CompiledCondition condition) {
            this.condition = condition;
            return this;
        }

        public LoweredBuilder effects(CompiledEffect... effects) {
            this.effects = new ArrayList<>(List.of(effects));
            return this;
        }

        public LoweredBuilder effects(List<CompiledEffect> effects) {
            this.effects = new ArrayList<>(effects);
            return this;
        }

        public LoweredBuilder suppressKey(String suppressKey) {
            this.suppressKey = suppressKey;
            return this;
        }

        public LoweredBuilder cooldownScope(String enchant, String group, String type) {
            this.cdScopeEnchant = enchant;
            this.cdScopeGroup = group;
            this.cdScopeType = type;
            return this;
        }

        /** Narrow this ability's IMPACT source scope away from its family group (the R-QC40 per-ability override). */
        public LoweredBuilder sourceGroup(String sourceGroup) {
            this.sourceGroup = sourceGroup;
            return this;
        }

        public LoweredBuilder repeatTicks(int repeatTicks) {
            this.repeatTicks = repeatTicks;
            return this;
        }

        public LoweredBuilder affinity(Affinity affinity) {
            this.affinity = affinity;
            return this;
        }

        public LoweredBuilder source(Source source) {
            this.source = source;
            return this;
        }

        public LoweredBuilder setPieces(int setPieces) {
            this.setPieces = setPieces;
            return this;
        }

        public LoweredBuilder suppressImmune(boolean suppressImmune) {
            this.suppressImmune = suppressImmune;
            return this;
        }


        /** Charge the soul cost against CARRIED gems with no gem active ({@code soul-cost-carried}). */
        public LoweredBuilder soulCostCarried(boolean soulCostCarried) {
            this.soulCostCarried = soulCostCarried;
            return this;
        }

        public LoweredBuilder noSoulsSound(int noSoulsSound) {
            this.noSoulsSound = noSoulsSound;
            return this;
        }

        public LoweredBuilder noSoulsParticle(int noSoulsParticle) {
            this.noSoulsParticle = noSoulsParticle;
            return this;
        }

        public LoweredBuilder noSoulsMessage(String noSoulsMessage) {
            this.noSoulsMessage = noSoulsMessage;
            return this;
        }

        public LoweredBuilder soulCostGrowth(double soulCostGrowth) {
            this.soulCostGrowth = soulCostGrowth;
            return this;
        }

        public LoweredBuilder soulCostCap(int soulCostCap) {
            this.soulCostCap = soulCostCap;
            return this;
        }

        public LoweredBuilder soulCostDecayPeriod(int soulCostDecayPeriod) {
            this.soulCostDecayPeriod = soulCostDecayPeriod;
            return this;
        }

        /** Key the cooldown on the victim rather than the coarse target bucket ({@code cooldown-per-victim}). */
        public LoweredBuilder cooldownPerVictim(boolean cooldownPerVictim) {
            this.cooldownPerVictim = cooldownPerVictim;
            return this;
        }

        public LoweredAbility build() {
            return new LoweredAbility(sourceKind, stableKey, defId, level, baseChance, cooldownTicks, soulCost,
                    triggers, worldBlacklist, condition, effects, suppressKey, cdScopeEnchant, cdScopeGroup,
                    cdScopeType, repeatTicks, affinity, source, setPieces, suppressImmune, loweredChanceExpr,
                    noSoulsMessage, soulCostCarried, noSoulsSound, noSoulsParticle,
                    soulCostGrowth, soulCostCap, soulCostDecayPeriod, cooldownPerVictim, repeatDelayTicks,
                    sourceGroup);
        }
    }
}
