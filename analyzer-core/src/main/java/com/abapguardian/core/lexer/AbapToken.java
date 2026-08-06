package com.abapguardian.core.lexer;

import java.util.Objects;

/**
 * A single ABAP token with accurate 1-based line and column tracking.
 * Columns count characters, starting at 1.
 */
public final class AbapToken {

    private final TokenType type;
    private final String text;
    private final int line;
    private final int column;
    private final int endLine;
    private final int endColumn;

    public AbapToken(TokenType type, String text, int line, int column, int endLine, int endColumn) {
        this.type = Objects.requireNonNull(type);
        this.text = Objects.requireNonNull(text);
        this.line = line;
        this.column = column;
        this.endLine = endLine;
        this.endColumn = endColumn;
    }

    public TokenType getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    /** Uppercased token text; convenient for case-insensitive ABAP keyword checks. */
    public String getUpperText() {
        return text.toUpperCase();
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public int getEndLine() {
        return endLine;
    }

    public int getEndColumn() {
        return endColumn;
    }

    public boolean isWord() {
        return type == TokenType.WORD;
    }

    public boolean isComment() {
        return type == TokenType.LINE_COMMENT || type == TokenType.INLINE_COMMENT
                || type == TokenType.PSEUDO_COMMENT;
    }

    /** Case-insensitive text comparison. */
    public boolean matches(String upperText) {
        return getUpperText().equals(upperText);
    }

    @Override
    public String toString() {
        return type + "(" + text + "@" + line + ":" + column + ")";
    }
}
