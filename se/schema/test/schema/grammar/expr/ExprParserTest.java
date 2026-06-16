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

    // ----- literals & variables -----

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

    // ----- comparators -----

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

    // ----- unary not -----

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

    // ----- precedence & associativity -----

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

    // ----- source threading -----

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
}
