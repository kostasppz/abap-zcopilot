package com.abapguardian.core.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbapParserTest {

    private final AbapParser parser = new AbapParser();

    @Test
    void splitsStatementsAtPeriods() {
        AbapSource src = parser.parse("DATA lv TYPE i. lv = 1. WRITE lv.");
        assertEquals(3, src.getStatements().size());
        assertEquals("DATA", src.getStatements().get(0).getFirstKeyword());
        assertEquals("WRITE", src.getStatements().get(2).getFirstKeyword());
    }

    @Test
    void expandsColonChains() {
        AbapSource src = parser.parse("WRITE: / lv_a, lv_b, lv_c.");
        assertEquals(3, src.getStatements().size());
        for (AbapStatement st : src.getStatements()) {
            assertEquals("WRITE", st.getFirstKeyword());
        }
    }

    @Test
    void chainedDataDeclarations() {
        AbapSource src = parser.parse("DATA: lv_a TYPE i,\n      lv_b TYPE string.");
        assertEquals(2, src.getStatements().size());
        assertTrue(src.getStatements().get(1).containsWord("LV_B"));
    }

    @Test
    void recognizesNestedBlocks() {
        String code = """
                LOOP AT lt_outer INTO DATA(ls_outer).
                  IF ls_outer-flag = abap_true.
                    LOOP AT lt_inner INTO DATA(ls_inner).
                      WRITE ls_inner-field.
                    ENDLOOP.
                  ENDIF.
                ENDLOOP.
                """;
        AbapSource src = parser.parse(code);
        AbapStatement write = src.getStatements().stream()
                .filter(s -> "WRITE".equals(s.getFirstKeyword())).findFirst().orElseThrow();
        AbapBlock block = write.getBlock();
        assertEquals(BlockType.LOOP, block.getType());
        assertEquals(BlockType.IF, block.getParent().getType());
        assertEquals(BlockType.LOOP, block.getParent().getParent().getType());
        assertEquals(BlockType.ROOT, block.getParent().getParent().getParent().getType());
    }

    @Test
    void selectEndselectFormsALoopBlock() {
        String code = """
                SELECT pernr FROM pa0002 INTO lv_pernr WHERE begda > lv_date.
                  WRITE lv_pernr.
                ENDSELECT.
                """;
        AbapSource src = parser.parse(code);
        AbapStatement write = src.getStatements().stream()
                .filter(s -> "WRITE".equals(s.getFirstKeyword())).findFirst().orElseThrow();
        assertEquals(BlockType.SELECT_LOOP, write.getBlock().getType());
    }

    @Test
    void selectIntoTableIsNotALoopBlock() {
        String code = """
                SELECT pernr FROM pa0002 INTO TABLE lt_pernr WHERE begda > lv_date.
                WRITE 'done'.
                """;
        AbapSource src = parser.parse(code);
        AbapStatement write = src.getStatements().stream()
                .filter(s -> "WRITE".equals(s.getFirstKeyword())).findFirst().orElseThrow();
        assertEquals(BlockType.ROOT, write.getBlock().getType());
    }

    @Test
    void statementLineNumbersAreAccurate() {
        String code = "DATA lv TYPE i.\n\nLOOP AT lt INTO ls.\n  WRITE ls-f.\nENDLOOP.";
        AbapSource src = parser.parse(code);
        List<AbapStatement> sts = src.getStatements();
        assertEquals(1, sts.get(0).getStartLine());
        assertEquals(3, sts.get(1).getStartLine());
        assertEquals(4, sts.get(2).getStartLine());
        assertEquals(3, sts.get(2).getStartColumn());
        assertEquals(5, sts.get(3).getStartLine());
    }

    @Test
    void commentsDoNotBecomeStatements() {
        String code = "* full line comment with LOOP AT keywords.\nWRITE 'x'. \" trailing SELECT comment";
        AbapSource src = parser.parse(code);
        assertEquals(1, src.getStatements().size());
        assertEquals("WRITE", src.getStatements().get(0).getFirstKeyword());
    }

    @Test
    void methodAndClassBlocks() {
        String code = """
                CLASS zcl_test IMPLEMENTATION.
                  METHOD run.
                    WRITE 'hi'.
                  ENDMETHOD.
                ENDCLASS.
                """;
        AbapSource src = parser.parse(code);
        AbapStatement write = src.getStatements().stream()
                .filter(s -> "WRITE".equals(s.getFirstKeyword())).findFirst().orElseThrow();
        assertEquals(BlockType.METHOD, write.getBlock().getType());
        assertEquals(BlockType.CLASS_IMPLEMENTATION, write.getBlock().getParent().getType());
    }

    @Test
    void caseWhenBlocks() {
        String code = """
                CASE lv_type.
                  WHEN 'A'.
                    WRITE 'a'.
                  WHEN 'B'.
                    WRITE 'b'.
                ENDCASE.
                WRITE 'after'.
                """;
        AbapSource src = parser.parse(code);
        AbapStatement after = src.getStatements().get(src.getStatements().size() - 1);
        assertEquals(BlockType.ROOT, after.getBlock().getType());
        assertNotNull(after.getBlock());
    }

    @Test
    void tryCatchBlocks() {
        String code = """
                TRY.
                    lv = 1 / 0.
                  CATCH cx_sy_zerodivide.
                    WRITE 'div0'.
                ENDTRY.
                WRITE 'after'.
                """;
        AbapSource src = parser.parse(code);
        AbapStatement after = src.getStatements().get(src.getStatements().size() - 1);
        assertEquals(BlockType.ROOT, after.getBlock().getType());
    }
}
