package schema.grammar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import schema.diag.Source;
import java.util.List;
import org.junit.jupiter.api.Test;

class LexerTest {

    private static List<String> texts(List<Tok> toks) {
        return toks.stream().map(Tok::text).toList();
    }

    @Test
    void splitsSimpleColonSeparatedWithColumns() {
        List<Tok> t = Lexer.splitTop("DAMAGE:3:5", ':');
        assertEquals(List.of("DAMAGE", "3", "5"), texts(t));
        assertEquals(1, t.get(0).col());
        assertEquals(8, t.get(1).col());
        assertEquals(10, t.get(2).col());
    }

    @Test
    void preservesDelimiterInsideAngleTags() {
        assertEquals(List.of("MSG", "<random 1:3>", "end"),
                texts(Lexer.splitTop("MSG:<random 1:3>:end", ':')));
    }

    @Test
    void preservesDelimiterInsideBracesAndQuotes() {
        assertEquals(List.of("X", "@Sel{a:b}", "\"c:d\""),
                texts(Lexer.splitTop("X:@Sel{a:b}:\"c:d\"", ':')));
    }

    @Test
    void keepsEmptySegments() {
        assertEquals(List.of("a", "", "b"), texts(Lexer.splitTop("a::b", ':')));
    }

    @Test
    void splitsAtTopLevelAfterBracketsClose() {
        // The colons before :g and :B are at depth zero (the (), [] already closed).
        assertEquals(List.of("A", "f(x:[1:2])", "g", "B"),
                texts(Lexer.splitTop("A:f(x:[1:2]):g:B", ':')));
    }

    @Test
    void effectLineParsesHeadAndArgsWithSourceColumns() {
        EffectLine line = EffectLine.parse("DAMAGE:3:5", Source.of("e.yml", 2, 1));
        assertEquals("DAMAGE", line.head());
        assertEquals(List.of("3", "5"), line.argTexts());
        assertEquals(2, line.argCount());
        assertEquals(2, line.sourceOfArg(0).line());
        assertEquals(8, line.sourceOfArg(0).col());
    }

    @Test
    void effectLineWithNoArguments() {
        EffectLine line = EffectLine.parse("CANCEL", Source.UNKNOWN);
        assertEquals("CANCEL", line.head());
        assertEquals(0, line.argCount());
        assertTrue(line.argTexts().isEmpty());
    }
}
