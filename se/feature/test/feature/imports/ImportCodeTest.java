package feature.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Test;

/**
 * The {@code SE1} codec contract (ADR-0029). The transform — base64url-nopad of zlib-deflated UTF-8 JSON —
 * is a wire contract shared byte-for-byte with the web creator, so these lock the exact pipeline (not just
 * "some round-trip"): the prefix, the url-safe alphabet, the standard zlib wrapper, key sanitization, and
 * the envelope shape.
 */
class ImportCodeTest {

    private static ImportCode.Envelope sampleEnvelope() {
        Map<String, Object> level = new LinkedHashMap<>();
        level.put("chance", 25);
        Map<String, Object> potion = new LinkedHashMap<>();
        potion.put("effect", "SLOWNESS");
        potion.put("level", 1);
        potion.put("who", "@Victim");
        level.put("effects", List.of(Map.of("POTION", potion)));
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("tier", "uncommon");
        content.put("display", "&bFrostbite");
        content.put("trigger", "ATTACK");
        content.put("applies-to", List.of("SWORD", "AXE"));
        content.put("levels", Map.of(1, level));
        return new ImportCode.Envelope(1, "enchant", "frostbite", content);
    }

    @Test
    void encodeThenDecodeRoundTrips() {
        ImportCode.Envelope original = sampleEnvelope();
        String code = ImportCode.encode(original);
        assertTrue(code.startsWith("SE1:"), "code carries the format prefix");
        assertTrue(code.substring(4).chars().noneMatch(c -> c == '+' || c == '/' || c == '='),
                "payload is base64URL without padding (chat/command safe)");

        ImportCode.Envelope decoded = ImportCode.decode(code);
        assertEquals(1, decoded.v());
        assertEquals("enchant", decoded.kind());
        assertEquals("frostbite", decoded.key());
        assertEquals("uncommon", decoded.content().get("tier"));
        assertEquals("ATTACK", decoded.content().get("trigger"));
        assertEquals(List.of("SWORD", "AXE"), decoded.content().get("applies-to"));
    }

    @Test
    void decodesAStandardZlibStreamProducedOutsideTheCodec() {
        // Proves we read the standard zlib wrapper (Deflater default = what pako.deflate emits), NOT raw
        // deflate — the single fact that makes the JS encoder and this decoder agree.
        String json = "{\"v\":1,\"kind\":\"enchant\",\"key\":\"abc\",\"content\":{\"tier\":\"common\"}}";
        Deflater deflater = new Deflater(); // default: zlib header + adler32
        deflater.setInput(json.getBytes(StandardCharsets.UTF_8));
        deflater.finish();
        byte[] buffer = new byte[256];
        int n = deflater.deflate(buffer);
        deflater.end();
        String code = "SE1:" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(java.util.Arrays.copyOf(buffer, n));

        ImportCode.Envelope decoded = ImportCode.decode(code);
        assertEquals("abc", decoded.key());
        assertEquals("common", decoded.content().get("tier"));
    }

    @Test
    void decodesAGoldenCodeFromTheWebCreator() {
        // A real SE1 code emitted by website/src/lib/se-codec.ts (pako.deflate). The definitive
        // JS-encoder ↔ Java-decoder cross-check: if the wire format ever diverges, this fails.
        String code = "SE1:eJxNUN9LwzAQ_lfCPfhUxQn6kCfLnCDKKrY4YewhTa5tWJqUJN0opf-7lw3BPBzJ9-Py3c1w"
                + "Ar7K4KitAg5oZSdsBAJwonfjXYi1jkiIdDYicXyGqNETO1rp-t5ZIpUOgxHJclO__jMpDNLrIWpScVh32"
                + "hgmrGLBuDOLHTK02E9sciML0esj3pGJLm17-SGvqnz9TpAYBqMx3EYHfA_lrvh6ITT_2cAhg9a7cSA1pa"
                + "lFCm_whCakpKtU0kwSgT88ZoBNgzISt5_hs6jeim1SXFFqUX4Uu-2mLP-aXJajRi-uEzzdZ3DuKAM8f2s"
                + "ZdQ_Lcljo_ALzC2o5";
        ImportCode.Envelope decoded = ImportCode.decode(code);
        assertEquals(1, decoded.v());
        assertEquals("enchant", decoded.kind());
        assertEquals("frostbite", decoded.key());
        assertEquals("uncommon", decoded.content().get("tier"));
        assertEquals("&bFrostbite", decoded.content().get("display"));
        assertEquals("ATTACK", decoded.content().get("trigger"));
        assertEquals(List.of("SWORD", "AXE"), decoded.content().get("applies-to"));
    }

    @Test
    void rejectsAWrongPrefix() {
        ImportCode.DecodeException e =
                assertThrows(ImportCode.DecodeException.class, () -> ImportCode.decode("SE2:whatever"));
        assertTrue(e.getMessage().contains("SE1:"));
    }

    @Test
    void rejectsCorruptBase64AndCorruptZlib() {
        assertThrows(ImportCode.DecodeException.class, () -> ImportCode.decode("SE1:!!!not base64!!!"));
        // valid base64url, but not a zlib stream
        String garbage = "SE1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {1, 2, 3, 4, 5});
        assertThrows(ImportCode.DecodeException.class, () -> ImportCode.decode(garbage));
    }

    @Test
    void keySanitizationRejectsTraversalAndAbsolutePaths() {
        assertThrows(ImportCode.DecodeException.class, () -> ImportCode.sanitizeKey("../evil"));
        assertThrows(ImportCode.DecodeException.class, () -> ImportCode.sanitizeKey("/etc/passwd"));
        assertThrows(ImportCode.DecodeException.class, () -> ImportCode.sanitizeKey("foo/bar"));
        assertThrows(ImportCode.DecodeException.class, () -> ImportCode.sanitizeKey("foo.bar"));
        assertThrows(ImportCode.DecodeException.class, () -> ImportCode.sanitizeKey(""));
        assertThrows(ImportCode.DecodeException.class, () -> ImportCode.sanitizeKey("with space"));
        // lowercased + accepted
        assertEquals("frostbite", ImportCode.sanitizeKey("Frostbite"));
        assertEquals("deep-freeze-2", ImportCode.sanitizeKey("deep-freeze-2"));
    }

    @Test
    void decodeRejectsAnUnsafeKeyEvenInsideAValidEnvelope() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("tier", "common");
        ImportCode.Envelope hostile = new ImportCode.Envelope(1, "enchant", "../../evil", content);
        // encode does not sanitize (round-trip fidelity); decode must catch the traversal key.
        String code = ImportCode.encode(hostile);
        assertThrows(ImportCode.DecodeException.class, () -> ImportCode.decode(code));
    }

    @Test
    void serializedYamlReparses() {
        // The on-disk write must be re-readable; round-trip the content map through the dumper + a plain load.
        ImportCode.Envelope envelope = sampleEnvelope();
        String yaml = ImportCode.toYaml(envelope.content());
        Object reparsed = new org.yaml.snakeyaml.Yaml().load(yaml);
        assertTrue(reparsed instanceof Map<?, ?>);
        assertEquals("uncommon", ((Map<?, ?>) reparsed).get("tier"));
    }
}
