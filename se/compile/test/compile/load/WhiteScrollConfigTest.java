package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The white-scroll config (§I) — the applies-to default and its defensive copy. */
class WhiteScrollConfigTest {

    @Test
    void defaultAppliesToIsArmorWeaponTool() {
        assertEquals(List.of("ARMOR", "WEAPON", "TOOL"), WhiteScrollConfig.defaults().appliesTo(),
                "the white scroll protects armor, weapons, and tools");
    }

    @Test
    void appliesToIsADefensiveCopy() {
        List<String> mutable = new ArrayList<>(List.of("TOOL"));
        WhiteScrollConfig c = new WhiteScrollConfig("M", "n", List.of(), 100, 100, "&fP", mutable);
        mutable.add("WEAPON");
        assertEquals(List.of("TOOL"), c.appliesTo(), "the record copies applies-to, so later source mutation can't leak in");
    }
}
