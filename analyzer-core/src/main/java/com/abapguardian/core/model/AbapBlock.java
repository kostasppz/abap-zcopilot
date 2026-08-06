package com.abapguardian.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A nesting block (LOOP..ENDLOOP, IF..ENDIF, METHOD..ENDMETHOD, ...) in the
 * lightweight statement model.
 */
public final class AbapBlock {

    private final BlockType type;
    private final AbapStatement openingStatement;
    private final AbapBlock parent;
    private final List<AbapBlock> children = new ArrayList<>();
    private final List<AbapStatement> statements = new ArrayList<>();
    private AbapStatement closingStatement;

    AbapBlock(BlockType type, AbapStatement openingStatement, AbapBlock parent) {
        this.type = type;
        this.openingStatement = openingStatement;
        this.parent = parent;
        if (parent != null) {
            parent.children.add(this);
        }
    }

    public BlockType getType() {
        return type;
    }

    /** Statement that opened this block (null for ROOT). */
    public AbapStatement getOpeningStatement() {
        return openingStatement;
    }

    /** Statement that closed this block (may be null for unterminated input). */
    public AbapStatement getClosingStatement() {
        return closingStatement;
    }

    void setClosingStatement(AbapStatement closingStatement) {
        this.closingStatement = closingStatement;
    }

    public AbapBlock getParent() {
        return parent;
    }

    public List<AbapBlock> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /** Statements directly inside this block (not in nested blocks). */
    public List<AbapStatement> getStatements() {
        return Collections.unmodifiableList(statements);
    }

    void addStatement(AbapStatement statement) {
        statements.add(statement);
    }

    /** True if this block or any ancestor is a loop construct. */
    public boolean isInsideLoop() {
        AbapBlock b = this;
        while (b != null) {
            if (b.type.isLoop()) {
                return true;
            }
            b = b.parent;
        }
        return false;
    }

    /** Nearest enclosing loop block including this one, or null. */
    public AbapBlock nearestLoop() {
        AbapBlock b = this;
        while (b != null) {
            if (b.type.isLoop()) {
                return b;
            }
            b = b.parent;
        }
        return null;
    }

    /** Ancestors from this block up to ROOT (inclusive of this block). */
    public List<AbapBlock> selfAndAncestors() {
        List<AbapBlock> result = new ArrayList<>();
        AbapBlock b = this;
        while (b != null) {
            result.add(b);
            b = b.parent;
        }
        return result;
    }

    @Override
    public String toString() {
        return "Block[" + type + (openingStatement != null ? "@" + openingStatement.getStartLine() : "") + "]";
    }
}
