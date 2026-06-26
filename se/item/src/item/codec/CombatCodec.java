package item.codec;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The single compact codec for an item's combat state (docs/architecture.md §5.1). The whole
 * {@link CombatState} is stored as <em>one</em> PDC entry — one read, one decode on the
 * combat-relevant victim miss path — never N keyed reads (§2.1). The on-item form is
 * stable-string-keyed and version-tagged so an item survives reloads and the nine-year version
 * range; an unrecognised version is treated as empty and re-written modern on next mutation
 * (lazy migration, §4.3), never a crash.
 *
 * <p>The blob is a pure self-delimiting string (unit-tested via {@link #encodeBlob}/{@link #decodeBlob});
 * the PDC/{@link ItemStack} round-trip is verified on a real server across the mapping flip, not assumed
 * (§11). Stored in PDC as a {@link PersistentDataType#STRING}.
 *
 * <p>Format: {@code v1 US <label> US <payload> US <label> US <payload> …} where {@code US} is the
 * unit separator and each list payload joins entries with the record separator {@code RS}. Labels:
 * {@code e} = enchants ({@code key:level} per entry), {@code c} = crystals ({@code key}),
 * {@code s} = armour-set key, {@code w} = weapon-set key (this item is that set's weapon, §6.6),
 * {@code o} = omni flag ({@code 1}), {@code h} = heroic flat stats ({@code damage:reduction:durability}),
 * {@code a} = purchased slot count (§H). Unknown labels are ignored so a newer field never breaks an
 * older reader (and an older blob lacking {@code s}/{@code w}/{@code o}/{@code h}/{@code a} decodes to no
 * set / no weapon-set / not-omni / no heroic / no added slots).
 */
public final class CombatCodec {

    private static final String VERSION = "v1";
    private static final char US = '\u001F'; // unit separator — between sections
    private static final char RS = '\u001E'; // record separator — between list entries
    private static final char KV = ':';      // key:level inside an enchant entry

    private final NamespacedKey combatKey;

    public CombatCodec(NamespacedKey combatKey) {
        this.combatKey = combatKey;
    }

    public CombatState read(ItemStack stack) {
        return decode(readBlob(stack));
    }

    /** The raw blob — the {@code ItemView} cache key, read without paying the decode the cache elides (§5.2). */
    public String readBlob(ItemStack stack) {
        return ItemBlobStore.read(stack, combatKey);
    }

    /** Clears the entry when {@code state} is empty (an empty {@code encode} returns {@code null} → remove). */
    public void write(ItemStack stack, CombatState state) {
        ItemBlobStore.write(stack, combatKey, encode(state));
    }

    public CombatState read(PersistentDataContainer pdc) {
        return decode(readBlob(pdc));
    }

    public String readBlob(PersistentDataContainer pdc) {
        return pdc.get(combatKey, PersistentDataType.STRING);
    }

    public CombatState decode(String blob) {
        return decodeBlob(blob);
    }

    public String encode(CombatState state) {
        return (state == null || state.isEmpty()) ? null : encodeBlob(state);
    }

    /** An empty state removes the entry entirely. */
    public void write(PersistentDataContainer pdc, CombatState state) {
        if (state == null || state.isEmpty()) {
            pdc.remove(combatKey);
        } else {
            pdc.set(combatKey, PersistentDataType.STRING, encodeBlob(state));
        }
    }

    static String encodeBlob(CombatState state) {
        StringBuilder sb = new StringBuilder(VERSION);
        StringBuilder ench = new StringBuilder();
        boolean firstE = true;
        for (Map.Entry<String, Integer> e : state.enchants().entrySet()) {
            if (!firstE) {
                ench.append(RS);
            }
            firstE = false;
            ench.append(e.getKey()).append(KV).append(e.getValue());
        }
        sb.append(US).append('e').append(US).append(ench);

        StringBuilder crys = new StringBuilder();
        for (int i = 0; i < state.crystals().size(); i++) {
            if (i > 0) {
                crys.append(RS);
            }
            crys.append(state.crystals().get(i));
        }
        sb.append(US).append('c').append(US).append(crys);

        if (state.setKey() != null) {
            sb.append(US).append('s').append(US).append(state.setKey());
        }
        if (state.setWeaponKey() != null) {
            sb.append(US).append('w').append(US).append(state.setWeaponKey());
        }
        if (state.omni()) {
            sb.append(US).append('o').append(US).append('1');
        }
        HeroicStat heroic = state.heroic();
        if (!heroic.isZero()) {
            sb.append(US).append('h').append(US)
                    .append(heroic.percentDamage()).append(KV)
                    .append(heroic.percentReduction()).append(KV)
                    .append(heroic.durability());
        }
        if (state.added() > 0) {
            sb.append(US).append('a').append(US).append(state.added());
        }
        return sb.toString();
    }

    static CombatState decodeBlob(String blob) {
        if (blob == null || blob.isEmpty()) {
            return CombatState.EMPTY;
        }
        String[] tokens = splitOn(blob, US);
        if (tokens.length == 0 || !VERSION.equals(tokens[0])) {
            return CombatState.EMPTY; // unknown/legacy format — migrated lazily on next write
        }
        Map<String, Integer> enchants = new LinkedHashMap<>();
        java.util.List<String> crystals = new java.util.ArrayList<>();
        String setKey = null;
        String setWeaponKey = null;
        boolean omni = false;
        HeroicStat heroic = HeroicStat.NONE;
        int added = 0;
        // Sections come in (label, payload) pairs after the version token.
        for (int i = 1; i + 1 < tokens.length; i += 2) {
            String label = tokens[i];
            String payload = tokens[i + 1];
            if ("e".equals(label)) {
                parseEnchants(payload, enchants);
            } else if ("c".equals(label)) {
                parseCrystals(payload, crystals);
            } else if ("s".equals(label)) {
                setKey = payload.isEmpty() ? null : payload;
            } else if ("w".equals(label)) {
                setWeaponKey = payload.isEmpty() ? null : payload;
            } else if ("o".equals(label)) {
                omni = "1".equals(payload);
            } else if ("h".equals(label)) {
                heroic = parseHeroic(payload);
            } else if ("a".equals(label)) {
                added = parseAdded(payload);
            }
            // any other label is a newer field this reader does not know — ignore it
        }
        return new CombatState(enchants, crystals, setKey, setWeaponKey, omni, heroic, added);
    }

    /** Malformed/negative → {@code 0}, never throws. */
    private static int parseAdded(String payload) {
        try {
            return Math.max(0, Integer.parseInt(payload.trim()));
        } catch (NumberFormatException bad) {
            return 0;
        }
    }

    /** A {@code damage:reduction:durability} payload; any malformed field → {@code NONE}, never throws. */
    private static HeroicStat parseHeroic(String payload) {
        String[] parts = payload.split(java.util.regex.Pattern.quote(String.valueOf(KV)), -1);
        if (parts.length != 3) {
            return HeroicStat.NONE; // malformed → no heroic, never throw
        }
        try {
            return new HeroicStat(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]));
        } catch (NumberFormatException bad) {
            return HeroicStat.NONE;
        }
    }

    private static void parseEnchants(String payload, Map<String, Integer> into) {
        if (payload.isEmpty()) {
            return;
        }
        for (String entry : splitOn(payload, RS)) {
            int kv = entry.indexOf(KV);
            if (kv <= 0 || kv == entry.length() - 1) {
                continue; // malformed entry → skip (warn-and-skip), never throw
            }
            String key = entry.substring(0, kv);
            try {
                into.put(key, Integer.parseInt(entry.substring(kv + 1)));
            } catch (NumberFormatException badLevel) {
                // skip this one enchant; a bad level never poisons the whole item
            }
        }
    }

    private static void parseCrystals(String payload, java.util.List<String> into) {
        if (payload.isEmpty()) {
            return;
        }
        for (String key : splitOn(payload, RS)) {
            if (!key.isEmpty()) {
                into.add(key);
            }
        }
    }

    /** Split keeping trailing empty fields (unlike {@code String.split}'s default trimming). */
    private static String[] splitOn(String s, char sep) {
        return s.split(java.util.regex.Pattern.quote(String.valueOf(sep)), -1);
    }
}
