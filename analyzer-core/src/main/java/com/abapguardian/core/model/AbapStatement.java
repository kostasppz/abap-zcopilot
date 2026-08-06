package com.abapguardian.core.model;

import com.abapguardian.core.lexer.AbapToken;
import com.abapguardian.core.lexer.TokenType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One logical ABAP statement. Chained statements (colon syntax) are expanded,
 * i.e. {@code WRITE: a, b.} becomes two statements each beginning with WRITE.
 */
public final class AbapStatement {

    private final List<AbapToken> tokens;
    private final List<AbapToken> comments;
    private AbapBlock block;
    private int index = -1;

    public AbapStatement(List<AbapToken> tokens, List<AbapToken> comments) {
        this.tokens = List.copyOf(tokens);
        this.comments = List.copyOf(comments);
    }

    /** Non-comment tokens of this statement. */
    public List<AbapToken> getTokens() {
        return tokens;
    }

    /** Comment tokens (inline / pseudo comments) attached to this statement. */
    public List<AbapToken> getComments() {
        return comments;
    }

    public boolean isEmpty() {
        return tokens.isEmpty();
    }

    /** Uppercased first word, or empty string. */
    public String getFirstKeyword() {
        for (AbapToken t : tokens) {
            if (t.isWord()) {
                return t.getUpperText();
            }
        }
        return "";
    }

    /** Uppercase text of the first two words joined by a space (e.g. "CALL FUNCTION"). */
    public String getLeadingWords(int count) {
        StringBuilder sb = new StringBuilder();
        int found = 0;
        for (AbapToken t : tokens) {
            if (t.isWord()) {
                if (found > 0) {
                    sb.append(' ');
                }
                sb.append(t.getUpperText());
                found++;
                if (found == count) {
                    break;
                }
            }
        }
        return sb.toString();
    }

    public int getStartLine() {
        return tokens.isEmpty() ? 0 : tokens.get(0).getLine();
    }

    public int getStartColumn() {
        return tokens.isEmpty() ? 0 : tokens.get(0).getColumn();
    }

    public int getEndLine() {
        return tokens.isEmpty() ? 0 : tokens.get(tokens.size() - 1).getEndLine();
    }

    public int getEndColumn() {
        return tokens.isEmpty() ? 0 : tokens.get(tokens.size() - 1).getEndColumn();
    }

    /** Enclosing block; never null after parsing (top level is the ROOT block). */
    public AbapBlock getBlock() {
        return block;
    }

    void setBlock(AbapBlock block) {
        this.block = block;
    }

    /** Position of this statement within the full statement list. */
    public int getIndex() {
        return index;
    }

    void setIndex(int index) {
        this.index = index;
    }

    /** True if a WORD token with the given uppercase text exists. */
    public boolean containsWord(String upperWord) {
        for (AbapToken t : tokens) {
            if (t.isWord() && t.getUpperText().equals(upperWord)) {
                return true;
            }
        }
        return false;
    }

    /** True if the given uppercase words appear consecutively (WORD tokens only). */
    public boolean containsPhrase(String... upperWords) {
        List<AbapToken> words = wordTokens();
        outer:
        for (int i = 0; i + upperWords.length <= words.size(); i++) {
            for (int j = 0; j < upperWords.length; j++) {
                if (!words.get(i + j).getUpperText().equals(upperWords[j])) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    public List<AbapToken> wordTokens() {
        return tokens.stream().filter(AbapToken::isWord).collect(Collectors.toList());
    }

    /** First WORD token following the given uppercase word, or null. */
    public AbapToken wordAfter(String upperWord) {
        List<AbapToken> words = wordTokens();
        for (int i = 0; i < words.size() - 1; i++) {
            if (words.get(i).getUpperText().equals(upperWord)) {
                return words.get(i + 1);
            }
        }
        return null;
    }

    /** Token (any type, excluding comments) directly after the given uppercase word, or null. */
    public AbapToken tokenAfterWord(String upperWord) {
        for (int i = 0; i < tokens.size() - 1; i++) {
            AbapToken t = tokens.get(i);
            if (t.isWord() && t.getUpperText().equals(upperWord)) {
                return tokens.get(i + 1);
            }
        }
        return null;
    }

    /** All string-ish literal tokens in this statement. */
    public List<AbapToken> literalTokens() {
        List<AbapToken> result = new ArrayList<>();
        for (AbapToken t : tokens) {
            if (t.getType() == TokenType.STRING_LITERAL
                    || t.getType() == TokenType.BACKQUOTE_LITERAL
                    || t.getType() == TokenType.STRING_TEMPLATE) {
                result.add(t);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Reconstructed source-ish text of the statement (single spaces between tokens). */
    public String toSourceText() {
        return tokens.stream().map(AbapToken::getText).collect(Collectors.joining(" "));
    }

    @Override
    public String toString() {
        return "Statement[" + getStartLine() + ":" + getStartColumn() + " " + toSourceText() + "]";
    }
}
