package schema.grammar.expr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import schema.diag.Diagnostics;
import schema.diag.Source;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExprParserTest {

    private static final Source SRC = Source.of("c.yml", 12, 1);

    /** Parse, asserting no diagnostics, and return the tree. */
    private static Expr parseOk(String text) {
        Diagnostics diags = new Diagnostics();
        Optional<Expr> e = ExprParser.parse(text, SRC, diags);
        assertFalse(diags.hasErrors(), () -> "unexpected parse errors: " + diags.all());
        assertTrue(e.isPresent(), "expected a tree");
        return e.get();
    }

    /**
     * Render a tree to a parenthesized, Source-free S-expression so precedence and
     * associativity are hand-checkable in one string. Uses {@code instanceof}
     * patterns (standard in Java 16+) rather than switch patterns (a preview in 17).
     */
    private static String sexpr(Expr e) {
        if (e instanceof Expr.Or o) {
            return "(|| " + sexpr(o.left()) + " " + sexpr(o.right()) + ")";
        }
        if (e instanceof Expr.And a) {
            return "(&& " + sexpr(a.left()) + " " + sexpr(a.right()) + ")";
        }
        if (e instanceof Expr.Not n) {
            return "(! " + sexpr(n.operand()) + ")";
        }
        if (e instanceof Expr.Compare c) {
            return "(" + c.op().symbol() + " " + sexpr(c.left()) + " " + sexpr(c.right()) + ")";
        }
        if (e instanceof Expr.StringMatch m) {
            return "(" + m.op().symbol() + " " + sexpr(m.left()) + " " + sexpr(m.right()) + ")";
        }
        if (e instanceof Expr.VarRef v) {
            return v.scope() == null ? "%" + v.name() + "%" : "%" + v.scope() + "." + v.name() + "%";
        }
        if (e instanceof Expr.NumberLit nl) {
            return "#" + nl.raw();
        }
        if (e instanceof Expr.BoolLit bl) {
            return bl.value() ? "T" : "F";
        }
        if (e instanceof Expr.StringLit sl) {
            return "\"" + sl.value() + "\"";
        }
        throw new AssertionError("unhandled node: " + e);
    }

    @Test
    void stringContainsOperatorParsesAtComparisonPrecedence() {
        assertEquals("(contains %name% \"a|b\")", sexpr(parseOk("%name% contains \"a|b\"")));
        // && binds looser than the string operator, so each side is its own match.
        assertEquals("(&& (contains %a% \"x\") (matchesregex %b% \"y\"))",
                sexpr(parseOk("%a% contains \"x\" && %b% matchesregex \"y\"")));
    }

    @Test
    void matchesRegexOperatorParses() {
        assertEquals("(matchesregex %name% \"[a-z]+\")", sexpr(parseOk("%name% matchesregex \"[a-z]+\"")));
    }

    @Test
    void stringOperatorsAreNonAssociative() {
        Diagnostics diags = new Diagnostics();
        ExprParser.parse("%a% contains \"x\" contains \"y\"", SRC, diags);
        assertTrue(diags.hasErrors());
        assertEquals("E_PARSE", diags.all().get(0).code());
    }

    @Test
    void numberLiteral() {
        assertEquals("#3", sexpr(parseOk("3")));
        assertEquals("#1.5", sexpr(parseOk("1.5")));
    }

    @Test
    void booleanLiteralsCaseInsensitive() {
        assertEquals("T", sexpr(parseOk("true")));
        assertEquals("F", sexpr(parseOk("false")));
        assertEquals("T", sexpr(parseOk("TRUE")));
    }

    @Test
    void stringLiteral() {
        assertEquals("\"hi\"", sexpr(parseOk("\"hi\"")));
        assertEquals("\"hi\"", sexpr(parseOk("'hi'")));
    }

    @Test
    void scopedVariable() {
        Expr e = parseOk("%victim.health%");
        assertEquals("%victim.health%", sexpr(e));
        Expr.VarRef v = assertInstanceOf(Expr.VarRef.class, e);
        assertEquals("victim", v.scope());
        assertEquals("health", v.name());
    }

    @Test
    void bareVariableHasNullScope() {
        Expr.VarRef v = assertInstanceOf(Expr.VarRef.class, parseOk("%blocking%"));
        assertNull(v.scope());
        assertEquals("blocking", v.name());
    }

    @Test
    void onlyFirstDotSplitsScopeSoPapiTokensSurvive() {
        Expr.VarRef v = assertInstanceOf(Expr.VarRef.class, parseOk("%player.stats.kills%"));
        assertEquals("player", v.scope());
        assertEquals("stats.kills", v.name()); // PAPI body kept intact after the first dot
    }

    @Test
    void bareIdentifierBecomesUnscopedVariable() {
        // enum-ish operands must survive parsing to be resolved later, not rejected.
        Expr.VarRef v = assertInstanceOf(Expr.VarRef.class, parseOk("SNEAKING"));
        assertNull(v.scope());
        assertEquals("SNEAKING", v.name());
    }

    @Test
    void allComparators() {
        assertEquals("(== %a% #1)", sexpr(parseOk("%a% == 1")));
        assertEquals("(!= %a% #1)", sexpr(parseOk("%a% != 1")));
        assertEquals("(< %a% #1)", sexpr(parseOk("%a% < 1")));
        assertEquals("(<= %a% #1)", sexpr(parseOk("%a% <= 1")));
        assertEquals("(> %a% #1)", sexpr(parseOk("%a% > 1")));
        assertEquals("(>= %a% #1)", sexpr(parseOk("%a% >= 1")));
    }

    @Test
    void comparisonBetweenVariableAndString() {
        assertEquals("(== %pose% \"SNEAKING\")", sexpr(parseOk("%pose% == \"SNEAKING\"")));
    }

    @Test
    void unaryNot() {
        assertEquals("(! %blocking%)", sexpr(parseOk("!%blocking%")));
    }

    @Test
    void doubleNot() {
        assertEquals("(! (! %x%))", sexpr(parseOk("!!%x%")));
    }

    @Test
    void notBindsTighterThanComparator() {
        // !%a% == false  parses as  (== (! %a%) F), NOT  (! (== %a% F))
        assertEquals("(== (! %a%) F)", sexpr(parseOk("!%a% == false")));
    }

    @Test
    void andBindsTighterThanOr() {
        // a || b && c  ==  a || (b && c)
        assertEquals("(|| %a% (&& %b% %c%))", sexpr(parseOk("%a% || %b% && %c%")));
    }

    @Test
    void comparatorBindsTighterThanAnd() {
        // a < 1 && b > 2  ==  (a<1) && (b>2)
        assertEquals("(&& (< %a% #1) (> %b% #2))", sexpr(parseOk("%a% < 1 && %b% > 2")));
    }

    @Test
    void orIsLeftAssociative() {
        // a || b || c  ==  (a || b) || c
        assertEquals("(|| (|| %a% %b%) %c%)", sexpr(parseOk("%a% || %b% || %c%")));
    }

    @Test
    void andIsLeftAssociative() {
        assertEquals("(&& (&& %a% %b%) %c%)", sexpr(parseOk("%a% && %b% && %c%")));
    }

    @Test
    void parenthesesOverridePrecedence() {
        // (a || b) && c
        assertEquals("(&& (|| %a% %b%) %c%)", sexpr(parseOk("(%a% || %b%) && %c%")));
    }

    @Test
    void nestedParentheses() {
        assertEquals("(! (|| %a% %b%))", sexpr(parseOk("!((%a% || %b%))")));
    }

    @Test
    void realisticCompoundCondition() {
        // %victim.health% < 5 && (%self.sneaking% || !%blocking%)
        assertEquals("(&& (< %victim.health% #5) (|| %self.sneaking% (! %blocking%)))",
                sexpr(parseOk("%victim.health% < 5 && (%self.sneaking% || !%blocking%)")));
    }

    @Test
    void emptyInputYieldsEmptyOptional() {
        Diagnostics diags = new Diagnostics();
        assertTrue(ExprParser.parse("   ", SRC, diags).isEmpty());
        assertFalse(diags.hasErrors());
        assertTrue(ExprParser.parse(null, SRC, diags).isEmpty());
    }

    @Test
    void nodesCarrySourceColumns() {
        // "%a% < 5": %a% at col 1, the comparison node anchors at its left operand (col 1).
        Expr.Compare c = assertInstanceOf(Expr.Compare.class, parseOk("%a% < 5"));
        assertEquals(12, c.source().line());
        assertEquals(1, c.source().col());
        assertEquals(7, ((Expr.NumberLit) c.right()).source().col()); // '5' at index 6 -> col 7
    }

    @Test
    void notNodePointsAtTheBangToken() {
        Expr.Not n = assertInstanceOf(Expr.Not.class, parseOk("  !%x%"));
        assertEquals(3, n.source().col()); // '!' at index 2 -> col 3
    }

    @Test
    void bareExpressionIsNotAClause() {
        assertInstanceOf(Expr.Compare.class, parseOk("%victim.health% < 5"));
    }

    @Test
    void forceClauseWrapsTheTest() {
        Expr.Clause c = assertInstanceOf(Expr.Clause.class, parseOk("%victim.health% < 5 : %force%"));
        assertEquals(FlowKind.FORCE, c.flow());
        assertEquals(0.0, c.chanceDelta());
        assertEquals("(< %victim.health% #5)", sexpr(c.test()));
    }

    @Test
    void stopAllowContinueSentinels() {
        assertEquals(FlowKind.STOP, ((Expr.Clause) parseOk("%blocking% : %stop%")).flow());
        assertEquals(FlowKind.ALLOW, ((Expr.Clause) parseOk("%blocking% : %allow%")).flow());
        assertEquals(FlowKind.CONTINUE, ((Expr.Clause) parseOk("%blocking% : %continue%")).flow());
    }

    @Test
    void chanceClauseCarriesASignedDelta() {
        Expr.Clause plus = assertInstanceOf(Expr.Clause.class, parseOk("%sneaking% : +50 %chance%"));
        assertEquals(FlowKind.CONTINUE, plus.flow());
        assertEquals(50.0, plus.chanceDelta());
        Expr.Clause minus = assertInstanceOf(Expr.Clause.class, parseOk("%sneaking% : -10 %chance%"));
        assertEquals(-10.0, minus.chanceDelta());
        Expr.Clause unsigned = assertInstanceOf(Expr.Clause.class, parseOk("%sneaking% : 25 %chance%"));
        assertEquals(25.0, unsigned.chanceDelta()); // an unsigned delta is positive
    }

    @Test
    void aColonInsideAPlaceholderIsNotAClauseSeparator() {
        // The lexer reads a %...% body up to the next %, so a PAPI token's ':' stays inside the VarRef.
        Expr.Compare c = assertInstanceOf(Expr.Compare.class, parseOk("%server_tps_1:colored% == \"20.0\""));
        assertEquals("server_tps_1:colored", ((Expr.VarRef) c.left()).name());
    }

    @Test
    void aSecondClauseIsAParseError() {
        Diagnostics diags = new Diagnostics();
        ExprParser.parse("%a% : %stop% : %force%", SRC, diags);
        assertTrue(diags.hasErrors());
    }

    @Test
    void aMissingChanceSentinelIsAParseError() {
        Diagnostics diags = new Diagnostics();
        ExprParser.parse("%a% : +50", SRC, diags);
        assertTrue(diags.hasErrors());
    }

    @Test
    void anUnknownSentinelIsAParseError() {
        Diagnostics diags = new Diagnostics();
        ExprParser.parse("%a% : %nonsense%", SRC, diags);
        assertTrue(diags.hasErrors());
    }
}
