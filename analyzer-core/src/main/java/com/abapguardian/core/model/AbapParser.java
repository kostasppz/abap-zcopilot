package com.abapguardian.core.model;

import com.abapguardian.core.lexer.AbapToken;
import com.abapguardian.core.lexer.AbapTokenizer;
import com.abapguardian.core.lexer.TokenType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Builds the lightweight statement and block model from tokens.
 *
 * <p>Statements are recognized through the tokenizer output (never through
 * regular expressions): a statement ends at a PERIOD token; colon chains are
 * expanded into individual statements sharing the chain prefix tokens.
 */
public final class AbapParser {

    private static final Map<String, BlockType> OPENERS = Map.ofEntries(
            Map.entry("LOOP", BlockType.LOOP),
            Map.entry("DO", BlockType.DO),
            Map.entry("WHILE", BlockType.WHILE),
            Map.entry("IF", BlockType.IF),
            Map.entry("CASE", BlockType.CASE),
            Map.entry("FORM", BlockType.FORM),
            Map.entry("METHOD", BlockType.METHOD),
            Map.entry("FUNCTION", BlockType.FUNCTION),
            Map.entry("MODULE", BlockType.MODULE),
            Map.entry("TRY", BlockType.TRY),
            Map.entry("PROVIDE", BlockType.PROVIDE),
            Map.entry("INTERFACE", BlockType.INTERFACE));

    private static final Map<String, String> CLOSER_FOR_OPENER = Map.ofEntries(
            Map.entry("LOOP", "ENDLOOP"),
            Map.entry("DO", "ENDDO"),
            Map.entry("WHILE", "ENDWHILE"),
            Map.entry("IF", "ENDIF"),
            Map.entry("CASE", "ENDCASE"),
            Map.entry("FORM", "ENDFORM"),
            Map.entry("METHOD", "ENDMETHOD"),
            Map.entry("FUNCTION", "ENDFUNCTION"),
            Map.entry("MODULE", "ENDMODULE"),
            Map.entry("TRY", "ENDTRY"),
            Map.entry("PROVIDE", "ENDPROVIDE"),
            Map.entry("INTERFACE", "ENDINTERFACE"),
            Map.entry("CLASS", "ENDCLASS"),
            Map.entry("SELECT", "ENDSELECT"),
            Map.entry("AT", "ENDAT"));

    private static final List<String> CLOSERS = List.of(
            "ENDLOOP", "ENDDO", "ENDWHILE", "ENDIF", "ENDCASE", "ENDFORM", "ENDMETHOD",
            "ENDFUNCTION", "ENDMODULE", "ENDTRY", "ENDPROVIDE", "ENDINTERFACE", "ENDCLASS",
            "ENDSELECT", "ENDAT");

    private final AbapTokenizer tokenizer = new AbapTokenizer();

    public AbapSource parse(String source) {
        List<AbapToken> tokens = tokenizer.tokenize(source == null ? "" : source);
        List<AbapStatement> statements = splitStatements(tokens);
        for (int i = 0; i < statements.size(); i++) {
            statements.get(i).setIndex(i);
        }
        AbapBlock root = buildBlocks(statements);
        List<String> lines = Arrays.asList((source == null ? "" : source).split("\n", -1));
        return new AbapSource(source == null ? "" : source, lines, statements, root);
    }

    private List<AbapStatement> splitStatements(List<AbapToken> tokens) {
        List<AbapStatement> statements = new ArrayList<>();
        List<AbapToken> chainPrefix = null;
        List<AbapToken> current = new ArrayList<>();
        List<AbapToken> comments = new ArrayList<>();
        boolean inChain = false;
        int parenDepth = 0;

        for (AbapToken token : tokens) {
            switch (token.getType()) {
                case LINE_COMMENT, INLINE_COMMENT, PSEUDO_COMMENT -> comments.add(token);
                case COLON -> {
                    if (!inChain) {
                        inChain = true;
                        chainPrefix = new ArrayList<>(current);
                        current = new ArrayList<>();
                    } else {
                        current.add(token);
                    }
                }
                case COMMA -> {
                    if (inChain && parenDepth == 0) {
                        statements.add(buildChained(chainPrefix, current, comments));
                        current = new ArrayList<>();
                        comments = new ArrayList<>();
                    } else {
                        current.add(token);
                    }
                }
                case PERIOD -> {
                    if (inChain) {
                        statements.add(buildChained(chainPrefix, current, comments));
                        inChain = false;
                        chainPrefix = null;
                    } else if (!current.isEmpty()) {
                        statements.add(new AbapStatement(current, comments));
                    } else if (!comments.isEmpty()) {
                        // comment-only "statement" is dropped, comments carry to next
                    }
                    if (!current.isEmpty() || inChain) {
                        comments = new ArrayList<>();
                    }
                    current = new ArrayList<>();
                    comments = new ArrayList<>(comments);
                }
                default -> {
                    if (token.getType() == TokenType.PUNCTUATION) {
                        if ("(".equals(token.getText())) {
                            parenDepth++;
                        } else if (")".equals(token.getText())) {
                            parenDepth = Math.max(0, parenDepth - 1);
                        }
                    }
                    current.add(token);
                }
            }
        }
        if (!current.isEmpty()) {
            statements.add(new AbapStatement(current, comments));
        }
        return statements;
    }

    private AbapStatement buildChained(List<AbapToken> prefix, List<AbapToken> segment, List<AbapToken> comments) {
        List<AbapToken> combined = new ArrayList<>();
        if (prefix != null) {
            combined.addAll(prefix);
        }
        combined.addAll(segment);
        return new AbapStatement(combined, comments);
    }

    private AbapBlock buildBlocks(List<AbapStatement> statements) {
        AbapBlock root = new AbapBlock(BlockType.ROOT, null, null);
        AbapBlock current = root;
        for (int i = 0; i < statements.size(); i++) {
            AbapStatement st = statements.get(i);
            String kw = st.getFirstKeyword();
            if (kw.isEmpty()) {
                current.addStatement(st);
                st.setBlock(current);
                continue;
            }
            if (CLOSERS.contains(kw)) {
                st.setBlock(current);
                current.addStatement(st);
                // WHEN blocks close implicitly at ENDCASE, CATCH at ENDTRY, etc.
                current = closeUpTo(current, kw);
                continue;
            }
            if ("ELSEIF".equals(kw) || "ELSE".equals(kw)) {
                if (current.getType() == BlockType.IF || current.getType() == BlockType.ELSEIF
                        || current.getType() == BlockType.ELSE) {
                    current.setClosingStatement(st);
                    current = current.getParent();
                }
                st.setBlock(current);
                current.addStatement(st);
                current = new AbapBlock("ELSE".equals(kw) ? BlockType.ELSE : BlockType.ELSEIF, st, current);
                continue;
            }
            if ("WHEN".equals(kw)) {
                if (current.getType() == BlockType.WHEN) {
                    current.setClosingStatement(st);
                    current = current.getParent();
                }
                st.setBlock(current);
                current.addStatement(st);
                current = new AbapBlock(BlockType.WHEN, st, current);
                continue;
            }
            if ("CATCH".equals(kw) || "CLEANUP".equals(kw)) {
                if (current.getType() == BlockType.CATCH || current.getType() == BlockType.CLEANUP) {
                    current.setClosingStatement(st);
                    current = current.getParent();
                }
                st.setBlock(current);
                current.addStatement(st);
                current = new AbapBlock("CATCH".equals(kw) ? BlockType.CATCH : BlockType.CLEANUP, st, current);
                continue;
            }
            if ("CLASS".equals(kw)) {
                st.setBlock(current);
                current.addStatement(st);
                if (st.containsWord("DEFINITION") && !st.containsWord("DEFERRED") && !st.containsWord("LOAD")) {
                    current = new AbapBlock(BlockType.CLASS_DEFINITION, st, current);
                } else if (st.containsWord("IMPLEMENTATION")) {
                    current = new AbapBlock(BlockType.CLASS_IMPLEMENTATION, st, current);
                }
                continue;
            }
            if ("AT".equals(kw) && (st.containsPhrase("AT", "NEW") || st.containsPhrase("AT", "END")
                    || st.containsPhrase("AT", "FIRST") || st.containsPhrase("AT", "LAST"))) {
                st.setBlock(current);
                current.addStatement(st);
                current = new AbapBlock(BlockType.AT, st, current);
                continue;
            }
            if ("SELECT".equals(kw)) {
                st.setBlock(current);
                current.addStatement(st);
                if (isSelectLoop(statements, i)) {
                    current = new AbapBlock(BlockType.SELECT_LOOP, st, current);
                }
                continue;
            }
            BlockType opener = OPENERS.get(kw);
            if (opener != null && !isNonBlockUsage(kw, st)) {
                st.setBlock(current);
                current.addStatement(st);
                current = new AbapBlock(opener, st, current);
                continue;
            }
            st.setBlock(current);
            current.addStatement(st);
        }
        return root;
    }

    /** METHOD in "METHODS:" style declarations etc. never open blocks; guard obvious cases. */
    private boolean isNonBlockUsage(String kw, AbapStatement st) {
        if ("MODULE".equals(kw)) {
            // MODULE xyz INPUT/OUTPUT opens; MODULE inside CALL chains does not occur as first keyword.
            return false;
        }
        if ("FUNCTION" .equals(kw)) {
            return false;
        }
        if ("IF".equals(kw)) {
            return false;
        }
        return false;
    }

    /**
     * Determines whether a SELECT statement opens a SELECT..ENDSELECT loop by
     * scanning forward for a matching ENDSELECT at the same nesting depth.
     * SELECT ... INTO TABLE / SELECT SINGLE / aggregate-only selects terminate
     * immediately and never have an ENDSELECT.
     */
    private boolean isSelectLoop(List<AbapStatement> statements, int selectIdx) {
        AbapStatement select = statements.get(selectIdx);
        if (select.containsWord("SINGLE")) {
            return false;
        }
        if (select.containsPhrase("INTO", "TABLE") || select.containsPhrase("APPENDING", "TABLE")
                || select.containsPhrase("INTO", "CORRESPONDING") && select.containsWord("TABLE")) {
            return false;
        }
        int depth = 0;
        for (int i = selectIdx + 1; i < statements.size(); i++) {
            String kw = statements.get(i).getFirstKeyword();
            if ("SELECT".equals(kw) && !statements.get(i).containsWord("SINGLE")
                    && !statements.get(i).containsPhrase("INTO", "TABLE")
                    && !statements.get(i).containsPhrase("APPENDING", "TABLE")) {
                depth++;
            } else if ("ENDSELECT".equals(kw)) {
                if (depth == 0) {
                    return true;
                }
                depth--;
            } else if (CLOSERS.contains(kw) && !"ENDSELECT".equals(kw)) {
                // A structural closer for an outer block before ENDSELECT means no loop.
                return false;
            }
        }
        return false;
    }

    /**
     * Close blocks until (and including) the block whose closer keyword matches.
     * Implicit blocks (WHEN/CATCH/ELSE/ELSEIF/CLEANUP) in between are closed too.
     */
    private AbapBlock closeUpTo(AbapBlock current, String closerKw) {
        AbapBlock b = current;
        while (b != null && b.getType() != BlockType.ROOT) {
            String expected = expectedCloser(b.getType());
            AbapBlock parent = b.getParent();
            if (isImplicit(b.getType())) {
                b.setClosingStatement(lastStatement(b));
                b = parent;
                continue;
            }
            if (closerKw.equals(expected)) {
                b.setClosingStatement(lastStatement(b));
                return parent;
            }
            // Mismatched closer: tolerate malformed source by closing anyway.
            b.setClosingStatement(lastStatement(b));
            b = parent;
        }
        return b == null ? current : b;
    }

    private static AbapStatement lastStatement(AbapBlock b) {
        List<AbapStatement> sts = b.getStatements();
        return sts.isEmpty() ? b.getOpeningStatement() : sts.get(sts.size() - 1);
    }

    private static boolean isImplicit(BlockType type) {
        return type == BlockType.WHEN || type == BlockType.CATCH || type == BlockType.CLEANUP
                || type == BlockType.ELSE || type == BlockType.ELSEIF || type == BlockType.AT;
    }

    private static String expectedCloser(BlockType type) {
        return switch (type) {
            case LOOP -> "ENDLOOP";
            case DO -> "ENDDO";
            case WHILE -> "ENDWHILE";
            case IF, ELSEIF, ELSE -> "ENDIF";
            case CASE, WHEN -> "ENDCASE";
            case FORM -> "ENDFORM";
            case METHOD -> "ENDMETHOD";
            case FUNCTION -> "ENDFUNCTION";
            case MODULE -> "ENDMODULE";
            case TRY, CATCH, CLEANUP -> "ENDTRY";
            case PROVIDE -> "ENDPROVIDE";
            case INTERFACE -> "ENDINTERFACE";
            case CLASS_DEFINITION, CLASS_IMPLEMENTATION -> "ENDCLASS";
            case SELECT_LOOP -> "ENDSELECT";
            case AT -> "ENDAT";
            case ROOT -> "";
        };
    }
}
