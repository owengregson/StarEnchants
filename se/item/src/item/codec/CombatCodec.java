package item.codec;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
 * <p>The blob format is a pure, self-delimiting string and is exercised by unit tests
 * ({@link #encodeBlob}/{@link #decodeBlob}); the PDC/{@link ItemStack} adapters round-trip on a
 * real server across the mapping flip in the live matrix (PDC is stable Bukkit API since 1.14,
 * but the byte round-trip is verified, not assumed; §11). The blob lives in PDC as a
 * {@link PersistentDataType#STRING}.
 *
 * <p>Format: {@code v1 US <label> US <payload> US <label> US <payload> …} where {@code US} is the
 * unit separator and each list payload joins entries with the record separator {@code RS}. Labels:
 * {@code e} = enchants ({@code key:level} per entry), {@code c} = crystals ({@code key}),
 * {@code s} = armour-set key, {@code o} = omni flag ({@code 1}). Unknown labels are ignored so a
 * newer field never breaks an older reader (and an older blob lacking {@code s}/{@code o} decodes to
 * no set / not-omni).
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

    // ── ItemStack convenience (copy-on-write meta) ───────────────────────────────────────────

    /** Decode the combat state on {@code stack}, or {@link CombatState#EMPTY} if it has none. */
    public CombatState read(ItemStack stack) {
        return decode(readBlob(stack));
    }

    /**
     * The raw stored combat blob on {@code stack}, or {@code null} if it carries none. This is the
     * {@code ItemView} cache key — the full content, read without paying the decode (§5.2). Reading
     * the blob still clones the copy-on-write meta, but the parse it feeds is what the cache elides.
     */
    public String readBlob(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return readBlob(stack.getItemMeta().getPersistentDataContainer());
    }

    /** Write {@code state} onto {@code stack} (clearing the entry when empty). */
    public void write(ItemStack stack, CombatState state) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        write(meta.getPersistentDataContainer(), state);
        stack.setItemMeta(meta);
    }

    // ── PDC level ────────────────────────────────────────────────────────────────────────────

    /** Decode the combat state from a container, or {@link CombatState#EMPTY} if absent. */
    public CombatState read(PersistentDataContainer pdc) {
        return decode(readBlob(pdc));
    }

    /** The raw stored combat blob in {@code pdc}, or {@code null} if absent (the cache key). */
    public String readBlob(PersistentDataContainer pdc) {
        return pdc.get(combatKey, PersistentDataType.STRING);
    }

    /** Parse a raw combat blob to a {@link CombatState}; {@code null}/blank/unknown-version → EMPTY. */
    public CombatState decode(String blob) {
        return decodeBlob(blob);
    }

    /** Serialise {@code state} to its compact stable-key blob, or {@code null} for an empty state. */
    public String encode(CombatState state) {
        return (state == null || state.isEmpty()) ? null : encodeBlob(state);
    }

    /** Encode {@code state} into a container; an empty state removes the entry entirely. */
    public void write(PersistentDataContainer pdc, CombatState state) {
        if (state == null || state.isEmpty()) {
            pdc.remove(combatKey);
        } else {
            pdc.set(combatKey, PersistentDataType.STRING, encodeBlob(state));
        }
    }

    // ── Pure blob format (unit-tested; Bukkit-free) ──────────────────────────────────────────

    /** Serialise a non-empty {@link CombatState} to its compact stable-key blob. */
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
        if (state.omni()) {
            sb.append(US).append('o').append(US).append('1');
        }
        return sb.toString();
    }

    /** Parse a combat blob back to a {@link CombatState}; null/blank/unknown-version → EMPTY. */
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
        boolean omni = false;
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
            } else if ("o".equals(label)) {
                omni = "1".equals(payload);
            }
            // any other label is a newer field this reader does not know — ignore it
        }
        return new CombatState(enchants, crystals, setKey, omni);
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
