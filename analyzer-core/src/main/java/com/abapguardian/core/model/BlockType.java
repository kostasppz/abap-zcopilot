package com.abapguardian.core.model;

/** Kinds of ABAP nesting blocks tracked by the statement model. */
public enum BlockType {
    ROOT(false),
    LOOP(true),
    DO(true),
    WHILE(true),
    SELECT_LOOP(true),
    IF(false),
    ELSEIF(false),
    ELSE(false),
    CASE(false),
    WHEN(false),
    FORM(false),
    METHOD(false),
    FUNCTION(false),
    MODULE(false),
    TRY(false),
    CATCH(false),
    CLEANUP(false),
    CLASS_DEFINITION(false),
    CLASS_IMPLEMENTATION(false),
    INTERFACE(false),
    AT(false),
    PROVIDE(true);

    private final boolean loop;

    BlockType(boolean loop) {
        this.loop = loop;
    }

    /** True if statements inside this block are executed repeatedly. */
    public boolean isLoop() {
        return loop;
    }
}
