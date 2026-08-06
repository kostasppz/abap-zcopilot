package com.abapguardian.core.rule;

/**
 * A concrete text replacement, expressed in 1-based line/column coordinates
 * of the analyzed source. End positions are inclusive of the last character.
 */
public record TextEdit(int startLine, int startColumn, int endLine, int endColumn, String replacement) {

    public TextEdit {
        if (startLine < 1 || startColumn < 1 || endLine < startLine) {
            throw new IllegalArgumentException("Invalid edit range");
        }
        if (replacement == null) {
            throw new IllegalArgumentException("Replacement must not be null");
        }
    }
}
