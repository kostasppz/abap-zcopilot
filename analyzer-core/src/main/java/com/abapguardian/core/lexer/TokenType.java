package com.abapguardian.core.lexer;

/** Classification of ABAP source tokens produced by {@link AbapTokenizer}. */
public enum TokenType {
    /** Word token: keyword, identifier, field name, etc. */
    WORD,
    /** Character literal enclosed in single quotes: 'foo'. */
    STRING_LITERAL,
    /** String literal enclosed in backquotes: `foo`. */
    BACKQUOTE_LITERAL,
    /** String template enclosed in pipes: |foo { bar }|. */
    STRING_TEMPLATE,
    /** Numeric literal. */
    NUMBER,
    /** Full-line comment (asterisk in column 1). */
    LINE_COMMENT,
    /** Inline comment starting with double quote. */
    INLINE_COMMENT,
    /** Pseudo comment: "#EC ... */
    PSEUDO_COMMENT,
    /** Pragma: ##NO_TEXT etc. */
    PRAGMA,
    /** Statement terminator '.'. */
    PERIOD,
    /** Chain colon ':'. */
    COLON,
    /** Chain separator ','. */
    COMMA,
    /** Any other punctuation or operator. */
    PUNCTUATION
}
