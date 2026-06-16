package schema.grammar.sel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import schema.diag.Diagnostics;
import schema.diag.Source;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SelectorAstTest {

    private static final Source SRC = Source.of("enchants.yml", 3, 12);

    private static Optional<SelectorAst> parse(String raw, Diagnostics d) {
        return SelectorAst.parse(raw, SRC, d);
    }

    @Test
    void bareSelectorHasHeadAndNoArgs() {
        Diagnostics d = new Diagnostics();
        SelectorAst ast = parse("@Self", d).orElseThrow();
        assertFalse(d.hasErrors());
        assertEquals("Self", ast.head());
        assertTrue(ast.args().isEmpty());
    }

    @Test
    void selectorWithOneNamedArg() {
        Diagnostics d = new Diagnostics();
        SelectorAst ast = parse("@Aoe{r=4}", d).orElseThrow();
        assertFalse(d.hasErrors());
        assertEquals("Aoe", ast.head());
        assertEquals(Map.of("r", "4"), ast.args());
    }

    @Test
    void selectorWithMultipleArgsKeepsOrderAndTrimsWhitespace() {
        Diagnostics d = new Diagnostics();
        SelectorAst ast = parse("@Nearest{ r = 5 , team = red }", d).orElseThrow();
        assertFalse(d.hasErrors());
        assertEquals("Nearest", ast.head());
        assertEquals("5", ast.args().get("r"));
        assertEquals("red", ast.args().get("team"));
    }

    @Test
    void emptyBracesAreNoArgs() {
        Diagnostics d = new Diagnostics();
        SelectorAst ast = parse("@Aoe{}", d).orElseThrow();
        assertFalse(d.hasErrors());
        assertTrue(ast.args().isEmpty());
    }

    @Test
    void duplicateArgWarnsAndLastWins() {
        Diagnostics d = new Diagnostics();
        SelectorAst ast = parse("@Aoe{r=1,r=2}", d).orElseThrow();
        assertFalse(d.hasErrors()); // a duplicate is a warning, not an error
        assertEquals("2", ast.args().get("r"));
        assertEquals("W_SELECTOR_DUP_ARG", d.all().get(0).code());
    }

    @Test
    void missingAtSignIsASyntaxError() {
        Diagnostics d = new Diagnostics();
        assertTrue(parse("Aoe{r=4}", d).isEmpty());
        assertTrue(d.hasErrors());
        assertEquals("E_SELECTOR_SYNTAX", d.all().get(0).code());
    }

    @Test
    void unclosedBraceIsASyntaxError() {
        Diagnostics d = new Diagnostics();
        assertTrue(parse("@Aoe{r=4", d).isEmpty());
        assertEquals("E_SELECTOR_SYNTAX", d.all().get(0).code());
    }

    @Test
    void argWithoutEqualsIsASyntaxError() {
        Diagnostics d = new Diagnostics();
        assertTrue(parse("@Aoe{r}", d).isEmpty());
        assertEquals("E_SELECTOR_SYNTAX", d.all().get(0).code());
    }

    @Test
    void emptyArgNameIsASyntaxError() {
        Diagnostics d = new Diagnostics();
        assertTrue(parse("@Aoe{=4}", d).isEmpty());
        assertEquals("E_SELECTOR_SYNTAX", d.all().get(0).code());
    }

    @Test
    void emptyHeadIsASyntaxError() {
        Diagnostics d = new Diagnostics();
        assertTrue(parse("@{r=4}", d).isEmpty());
        assertEquals("E_SELECTOR_SYNTAX", d.all().get(0).code());
    }
}
