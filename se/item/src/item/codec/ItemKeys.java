package item.codec;

/**
 * The plugin's logical key names for on-item state (§4.2). Single key authority: these strings must
 * never drift, or items written under the old name stop resolving. They are <em>logical</em> names —
 * the {@link ItemStateStore} seam maps each to the platform's native key form
 * (a {@code starenchants:}-namespaced PDC key on modern; a raw NMS tag name on the 1.8 legacy fork),
 * so the codecs and this holder stay free of any version-specific key type (PDC {@code NamespacedKey}
 * does not exist on 1.8.9 — docs/legacy-1.8.9-codeshare-design.md §3.1).
 */
public final class ItemKeys {

    private final String combat;
    private final String soul;
    private final String carrier;
    private final String guarded;
    private final String crystalItem;
    private final String crystalExtractor;
    private final String heroicUpgrade;
    private final String slotItem;
    private final String slotSuccess;
    private final String scroll;
    private final String scrollConvert;
    private final String unopened;
    private final String godlyTransmog;
    private final String appliedSlot;
    private final String trakGem;
    private final String trakBlocks;
    private final String trakMobs;
    private final String trakSouls;
    private final String trakFish;
    private final String loreComposer;
    private final String useItem;
    private final String pet;
    private final String petLevel;
    private final String petExp;
    private final String petExpFrac;
    private final String petFood;
    private final String petXpGate;
    private final String maskItem;
    private final String illusion;
    private final String reforgeItem;
    private final String holyProtections;
    private final String claimant;
    private final String claimDate;

    private ItemKeys(String combat, String soul, String carrier, String guarded,
                     String crystalItem, String crystalExtractor, String heroicUpgrade,
                     String slotItem, String slotSuccess, String scroll, String scrollConvert,
                     String unopened, String godlyTransmog, String appliedSlot,
                     String trakGem, String trakBlocks, String trakMobs, String trakSouls, String trakFish,
                     String loreComposer, String useItem,
                     String pet, String petLevel, String petExp, String petExpFrac, String petFood,
                     String petXpGate,
                     String maskItem, String illusion, String reforgeItem, String holyProtections,
                     String claimant, String claimDate) {
        this.combat = combat;
        this.soul = soul;
        this.carrier = carrier;
        this.guarded = guarded;
        this.crystalItem = crystalItem;
        this.crystalExtractor = crystalExtractor;
        this.heroicUpgrade = heroicUpgrade;
        this.slotItem = slotItem;
        this.slotSuccess = slotSuccess;
        this.scroll = scroll;
        this.scrollConvert = scrollConvert;
        this.unopened = unopened;
        this.godlyTransmog = godlyTransmog;
        this.appliedSlot = appliedSlot;
        this.trakGem = trakGem;
        this.trakBlocks = trakBlocks;
        this.trakMobs = trakMobs;
        this.trakSouls = trakSouls;
        this.trakFish = trakFish;
        this.loreComposer = loreComposer;
        this.useItem = useItem;
        this.pet = pet;
        this.petLevel = petLevel;
        this.petExp = petExp;
        this.petExpFrac = petExpFrac;
        this.petFood = petFood;
        this.petXpGate = petXpGate;
        this.maskItem = maskItem;
        this.illusion = illusion;
        this.reforgeItem = reforgeItem;
        this.holyProtections = holyProtections;
        this.claimant = claimant;
        this.claimDate = claimDate;
    }

    public static ItemKeys of() {
        return new ItemKeys("combat", "soul", "carrier", "guarded", "crystalitem", "crystalextractor",
                "heroicupgrade", "slotitem", "slotsuccess", "scroll", "scrollconvert", "unopened",
                "godlytransmog", "appliedslot", "trakgem", "trakblocks", "trakmobs", "traksouls", "trakfish",
                "lorecomposer", "useitem", "pet", "petlevel", "petexp", "petexpfrac", "petfood", "petxpgate",
                "maskitem",
                "illusion", "reforgeitem", "holyprotections", "claimant", "claimdate");
    }

    public String combat() {
        return combat;
    }

    /** Separate from {@link #combat()}: souls change every spend/gain, which would thrash the content-hash cache (§5.2). */
    public String soul() {
        return soul;
    }

    /** Carrier (book/scroll/dust/gem) — separate from {@link #combat()} so it never decodes on the hot path (ADR-0016). */
    public String carrier() {
        return carrier;
    }

    /** Flags gear as guard-scroll protected; consumed on a failed apply to spare the item (white-scroll economy). */
    public String guarded() {
        return guarded;
    }

    public String crystalItem() {
        return crystalItem;
    }

    public String crystalExtractor() {
        return crystalExtractor;
    }

    public String heroicUpgrade() {
        return heroicUpgrade;
    }

    /** Slot-expander orb (§H); the granted slots persist in the gear's combat-blob {@code added} field, not here. */
    public String slotItem() {
        return slotItem;
    }

    /** The slot orb's per-item rolled success chance (§H); paired with {@link #slotItem()} on the orb. */
    public String slotSuccess() {
        return slotSuccess;
    }

    public String scroll() {
        return scroll;
    }

    /** The black scroll's rolled new-book conversion success rate (§I); paired with {@link #scroll()} on the scroll. */
    public String scrollConvert() {
        return scrollConvert;
    }

    public String unopened() {
        return unopened;
    }

    public String godlyTransmog() {
        return godlyTransmog;
    }

    /**
     * The single exclusive APPLIED-UTILITY slot (§I): an item may carry at most one of {white scroll, holy
     * white scroll, blocktrak, mobtrak, soultrak} at a time. Stores the occupant's kind; see {@link AppliedSlot}.
     */
    public String appliedSlot() {
        return appliedSlot;
    }

    /** Marks an UNAPPLIED trak gem + its kind (§I); distinct from the per-item lifetime counters below. */
    public String trakGem() {
        return trakGem;
    }

    /** Per-item lifetime blocks-broken counter (§I) — tracked in the background, separate from the combat blob. */
    public String trakBlocks() {
        return trakBlocks;
    }

    /** Per-item lifetime mobs-killed counter (§I). */
    public String trakMobs() {
        return trakMobs;
    }

    /** Per-item lifetime players-killed counter (§I). */
    public String trakSouls() {
        return trakSouls;
    }

    /** Per-item lifetime fish-caught counter (§I). */
    public String trakFish() {
        return trakFish;
    }

    /** Versioned marker stamped when {@link item.render.LoreRenderer} composes an item (ADR-0040 §migration); its
     *  absence flags pre-composer lore that the one-time legacy migration shim reconciles on first recompose. */
    public String loreComposer() {
        return loreComposer;
    }

    /** A right-click use-item (§3): the PDC string under this key is the use-item's def key. */
    public String useItem() {
        return useItem;
    }

    /** A pet head (ADR-0052): the PDC string under this key is the pet's def key. */
    public String pet() {
        return pet;
    }

    /** The pet's stored level — its OWN integer key (the trak rule: counters never ride the combat blob). */
    public String petLevel() {
        return petLevel;
    }

    /** The pet's exp toward the next level — its own integer key, mutated on every credit. */
    public String petExp() {
        return petExp;
    }

    /** The passive-accrual carry in 1/60000-exp units (ADR-0059) — rides the item so it survives moves. */
    public String petExpFrac() {
        return petExpFrac;
    }

    /** A Pet Food apply item (ADR-0052): the PDC integer under this key is its baked +levels amount. */
    public String petFood() {
        return petFood;
    }

    /** ITEM_XP_TRACK's per-item {@code window} stamp, in MINUTES since the epoch (see {@link PetCodec}). */
    public String petXpGate() {
        return petXpGate;
    }

    /** A mask head item (ADR-0053): the PDC string under this key is the mask's def key (its identity, pre-apply). */
    public String maskItem() {
        return maskItem;
    }

    /** A mask-illusion shown head (ADR-0064): payload = Base64 of the real helmet it dresses, {@code "-"} = none. */
    public String illusion() {
        return illusion;
    }

    /**
     * The set-piece CLAIM footer's claimant name (R-QC35c), or absent on an unclaimed piece. Its own key
     * rather than a {@code CombatState} component for the reason souls and the trak counters have their own:
     * a claim is written by an external event system on its own schedule, and folding it into the
     * content-hash blob would thrash the {@code ItemView} cache on every capture.
     */
    public String claimant() {
        return claimant;
    }

    /** The claim's date, epoch millis (R-QC35c). Present on a claimed AND an unclaimed piece — the footer's
     *  unclaimed form still names the date the piece was staked. */
    public String claimDate() {
        return claimDate;
    }

    /** A reforge catalogue item (ADR-0070): the PDC string under this key is the reforge's def key (pre-apply). */
    public String reforgeItem() {
        return reforgeItem;
    }

    /** Lifetime count of holy white scrolls this item has SPENT saving itself (§I corruption) — the trak-counter
     *  rule: its own integer key, never the combat blob, so a death bump cannot thrash the ItemView cache. */
    public String holyProtections() {
        return holyProtections;
    }
}
