package com.abapguardian.core.lexer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbapTokenizerTest {

    private final AbapTokenizer tokenizer = new AbapTokenizer();

    @Test
    void tracksLineAndColumnAccurately() {
        String source = "DATA lv_x TYPE i.\n  WRITE lv_x.";
        List<AbapToken> tokens = tokenizer.tokenize(source);
        AbapToken data = tokens.get(0);
        assertEquals("DATA", data.getText());
        assertEquals(1, data.getLine());
        assertEquals(1, data.getColumn());

        AbapToken write = tokens.stream().filter(t -> t.getText().equals("WRITE")).findFirst().orElseThrow();
        assertEquals(2, write.getLine());
        assertEquals(3, write.getColumn());
    }

    @Test
    void fullLineCommentIsSingleToken() {
        List<AbapToken> tokens = tokenizer.tokenize("* SELECT * FROM pa0002.\nWRITE 'x'.");
        assertEquals(TokenType.LINE_COMMENT, tokens.get(0).getType());
        // No SELECT word token exists.
        assertTrue(tokens.stream().noneMatch(t -> t.isWord() && t.getText().equals("SELECT")));
    }

    @Test
    void inlineCommentConsumesRestOfLine() {
        List<AbapToken> tokens = tokenizer.tokenize("WRITE lv_x. \" SELECT * FROM pa0002");
        assertTrue(tokens.stream().noneMatch(t -> t.isWord() && t.getText().equals("SELECT")));
        assertTrue(tokens.stream().anyMatch(t -> t.getType() == TokenType.INLINE_COMMENT));
    }

    @Test
    void pseudoCommentIsRecognized() {
        List<AbapToken> tokens = tokenizer.tokenize(
                "WRITE lv_x. \"#EC ABAP_GUARDIAN: X reason=\"why\"");
        assertTrue(tokens.stream().anyMatch(t -> t.getType() == TokenType.PSEUDO_COMMENT));
    }

    @Test
    void stringLiteralsKeepAbapKeywordsOutOfWordTokens() {
        List<AbapToken> tokens = tokenizer.tokenize("lv_text = 'LOOP AT SELECT ENDLOOP'.");
        assertTrue(tokens.stream().noneMatch(t -> t.isWord() && t.getText().equals("SELECT")));
        AbapToken literal = tokens.stream().filter(t -> t.getType() == TokenType.STRING_LITERAL)
                .findFirst().orElseThrow();
        assertEquals("'LOOP AT SELECT ENDLOOP'", literal.getText());
    }

    @Test
    void doubledQuoteEscapeInsideLiteral() {
        List<AbapToken> tokens = tokenizer.tokenize("lv = 'it''s'.");
        AbapToken literal = tokens.stream().filter(t -> t.getType() == TokenType.STRING_LITERAL)
                .findFirst().orElseThrow();
        assertEquals("'it''s'", literal.getText());
    }

    @Test
    void stringTemplateWithEmbeddedExpression() {
        List<AbapToken> tokens = tokenizer.tokenize("lv = |Name: { ls_p-nachn }|.");
        AbapToken template = tokens.stream().filter(t -> t.getType() == TokenType.STRING_TEMPLATE)
                .findFirst().orElseThrow();
        assertTrue(template.getText().contains("ls_p-nachn"));
    }

    @Test
    void pragmaToken() {
        List<AbapToken> tokens = tokenizer.tokenize("DATA lv TYPE string ##NO_TEXT.");
        assertTrue(tokens.stream().anyMatch(t -> t.getType() == TokenType.PRAGMA
                && t.getText().equals("##NO_TEXT")));
    }

    @Test
    void endColumnIsInclusive() {
        List<AbapToken> tokens = tokenizer.tokenize("WRITE.");
        AbapToken write = tokens.get(0);
        assertEquals(1, write.getColumn());
        assertEquals(5, write.getEndColumn());
    }
}
