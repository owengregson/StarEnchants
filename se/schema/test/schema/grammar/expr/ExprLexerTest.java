package schema.grammar.expr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import schema.diag.Diagnostics;
import schema.diag.Source;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExprLexerTest {

    private static final Source SRC = Source.of("c.yml", 7, 1);

    private static List<ExprTok> lex(String text, Diagnostics diags) {
        return ExprLexer.tokenize(text, SRC, diags);
    }

    private static List<ExprTok> lexOk(String text) {
        Diagnostics diags = new Diagnostics();
        List<ExprTok> toks = lex(text, diags);
        assertFalse(diags.hasErrors(), () -> "unexpected lex errors: " + diags.all());
        return toks;
    }

    private static List<ExprTok.Kind> kinds(List<ExprTok> toks) {
        return toks.stream().map(ExprTok::kind).toList();
    }

    @Test
    void tokenizesOperatorsAndDelimiters() {
        List<ExprTok> t = lexOk("&& || ! ( ) , == != < <= > >=");
        assertEquals(List.of(
                ExprTok.Kind.AND, ExprTok.Kind.OR, ExprTok.Kind.BANG,
                ExprTok.Kind.LPAREN, ExprTok.Kind.RPAREN, ExprTok.Kind.COMMA,
                ExprTok.Kind.EQ, ExprTok.Kind.NE,
                ExprTok.Kind.LT, ExprTok.Kind.LE, ExprTok.Kind.GT, ExprTok.Kind.GE,
                ExprTok.Kind.EOF), kinds(t));
    }

    @Test
    void distinguishesBangFromNotEqual() {
        assertEquals(List.of(ExprTok.Kind.BANG, ExprTok.Kind.NE, ExprTok.Kind.EOF),
                kinds(lexOk("! !=")));
    }

    @Test
    void distinguishesLtFromLe() {
        assertEquals(List.of(ExprTok.Kind.LT, ExprTok.Kind.LE, ExprTok.Kind.EOF),
                kinds(lexOk("< <=")));
    }

    @Test
    void tokenizesNumbersIncludingDecimals() {
        List<ExprTok> t = lexOk("3 1.5");
        assertEquals(ExprTok.Kind.NUMBER, t.get(0).kind());
        assertEquals("3", t.get(0).text());
        assertEquals("1.5", t.get(1).text());
    }

    @Test
    void tokenizesVariableInnerBody() {
        List<ExprTok> t = lexOk("%victim.health%");
        assertEquals(ExprTok.Kind.VAR, t.get(0).kind());
        assertEquals("victim.health", t.get(0).text()); // inner body, no % delimiters
    }

    @Test
    void tokenizesQuotedStringsUnescaped() {
        List<ExprTok> dbl = lexOk("\"hello world\"");
        assertEquals(ExprTok.Kind.STRING, dbl.get(0).kind());
        assertEquals("hello world", dbl.get(0).text());

        List<ExprTok> single = lexOk("'a'");
        assertEquals("a", single.get(0).text());
    }

    @Test
    void stringEscapesAreUnescaped() {
        // "a\"b\\c" -> a"b\c
        List<ExprTok> t = lexOk("\"a\\\"b\\\\c\"");
        assertEquals("a\"b\\c", t.get(0).text());
    }

    @Test
    void tokenizesIdentifiers() {
        List<ExprTok> t = lexOk("true false SNEAKING");
        assertEquals(ExprTok.Kind.IDENT, t.get(0).kind());
        assertEquals("true", t.get(0).text());
        assertEquals("false", t.get(1).text());
        assertEquals("SNEAKING", t.get(2).text());
    }

    @Test
    void tokenizesStringOperatorsAsReservedWords() {
        assertEquals(List.of(ExprTok.Kind.CONTAINS, ExprTok.Kind.MATCHES_REGEX, ExprTok.Kind.EOF),
                kinds(lexOk("contains matchesregex")));
        // Reserved words are recognised case-insensitively.
        assertEquals(List.of(ExprTok.Kind.CONTAINS, ExprTok.Kind.MATCHES_REGEX, ExprTok.Kind.EOF),
                kinds(lexOk("CONTAINS MatchesRegex")));
        // A word that merely starts with an operator's text is still an ordinary identifier.
        assertEquals(ExprTok.Kind.IDENT, lexOk("containsx").get(0).kind());
    }

    @Test
    void tracksColumnsOneBasedOnTheLine() {
        // index 0 -> col 1; "%a%" starts at 0, "<" at 4, "5" at 6
        List<ExprTok> t = lexOk("%a% < 5");
        assertEquals(1, t.get(0).col()); // %a%
        assertEquals(5, t.get(1).col()); // <
        assertEquals(7, t.get(2).col()); // 5
        assertEquals(8, t.get(3).col()); // EOF, just past the input
    }

    @Test
    void whitespaceIsSkipped() {
        assertEquals(List.of(ExprTok.Kind.NUMBER, ExprTok.Kind.EOF), kinds(lexOk("   3   ")));
    }

    @Test
    void emptyInputIsJustEof() {
        assertEquals(List.of(ExprTok.Kind.EOF), kinds(lexOk("")));
    }

    @Test
    void unterminatedStringReportsErrorButStillTokenizes() {
        Diagnostics diags = new Diagnostics();
        List<ExprTok> t = lex("\"oops", diags);
        assertTrue(diags.hasErrors());
        assertEquals("E_PARSE", diags.all().get(0).code());
        assertEquals(ExprTok.Kind.STRING, t.get(0).kind());
        assertEquals("oops", t.get(0).text()); // best-effort body
    }

    @Test
    void unterminatedVariableReportsErrorButStillTokenizes() {
        Diagnostics diags = new Diagnostics();
        List<ExprTok> t = lex("%victim.health", diags);
        assertTrue(diags.hasErrors());
        assertEquals(ExprTok.Kind.VAR, t.get(0).kind());
        assertEquals("victim.health", t.get(0).text());
    }

    @Test
    void emptyVariableReportsError() {
        Diagnostics diags = new Diagnostics();
        lex("%%", diags);
        assertTrue(diags.hasErrors());
        assertEquals("E_PARSE", diags.all().get(0).code());
    }

    @Test
    void loneAmpersandReportsErrorButYieldsAndToken() {
        Diagnostics diags = new Diagnostics();
        List<ExprTok> t = lex("&", diags);
        assertTrue(diags.hasErrors());
        assertEquals(ExprTok.Kind.AND, t.get(0).kind());
    }

    @Test
    void strayCharacterIsReportedAndSkipped() {
        Diagnostics diags = new Diagnostics();
        List<ExprTok> t = lex("3 @ 5", diags);
        assertTrue(diags.hasErrors());
        assertEquals("E_PARSE", diags.all().get(0).code());
        // The '@' is skipped; 3 and 5 still tokenize.
        assertEquals(List.of(ExprTok.Kind.NUMBER, ExprTok.Kind.NUMBER, ExprTok.Kind.EOF),
                kinds(t));
    }

    @Test
    void strayCharacterDiagnosticPointsAtTheRightColumn() {
        Diagnostics diags = new Diagnostics();
        lex("3 @ 5", diags); // '@' is at index 2 -> col 3
        assertEquals(3, diags.all().get(0).source().col());
        assertEquals(7, diags.all().get(0).source().line());
    }
}
