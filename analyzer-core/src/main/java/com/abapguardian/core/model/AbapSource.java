package com.abapguardian.core.model;

import java.util.Collections;
import java.util.List;

/** Parsed representation of one ABAP source unit: statements plus block structure. */
public final class AbapSource {

    private final String sourceText;
    private final List<String> lines;
    private final List<AbapStatement> statements;
    private final AbapBlock rootBlock;

    AbapSource(String sourceText, List<String> lines, List<AbapStatement> statements, AbapBlock rootBlock) {
        this.sourceText = sourceText;
        this.lines = List.copyOf(lines);
        this.statements = List.copyOf(statements);
        this.rootBlock = rootBlock;
    }

    public String getSourceText() {
        return sourceText;
    }

    /** Original source lines (1-based access via {@link #getLine(int)}). */
    public List<String> getLines() {
        return lines;
    }

    /** 1-based line access; returns empty string when out of range. */
    public String getLine(int lineNo) {
        if (lineNo < 1 || lineNo > lines.size()) {
            return "";
        }
        return lines.get(lineNo - 1);
    }

    public List<AbapStatement> getStatements() {
        return statements;
    }

    public AbapBlock getRootBlock() {
        return rootBlock;
    }

    /** Statements after the given one, in source order. */
    public List<AbapStatement> statementsAfter(AbapStatement statement) {
        int idx = statement.getIndex();
        if (idx < 0 || idx + 1 >= statements.size()) {
            return Collections.emptyList();
        }
        return statements.subList(idx + 1, statements.size());
    }

    /** Statements before the given one, in source order. */
    public List<AbapStatement> statementsBefore(AbapStatement statement) {
        int idx = statement.getIndex();
        if (idx <= 0) {
            return Collections.emptyList();
        }
        return statements.subList(0, idx);
    }
}
