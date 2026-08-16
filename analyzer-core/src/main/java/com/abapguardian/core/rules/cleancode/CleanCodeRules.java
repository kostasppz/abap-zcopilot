package com.abapguardian.core.rules.cleancode;

import com.abapguardian.core.lexer.AbapToken;
import com.abapguardian.core.lexer.TokenType;
import com.abapguardian.core.model.AbapBlock;
import com.abapguardian.core.model.AbapStatement;
import com.abapguardian.core.model.BlockType;
import com.abapguardian.core.rule.AbapRule;
import com.abapguardian.core.rule.AnalysisContext;
import com.abapguardian.core.rule.Finding;
import com.abapguardian.core.rule.RuleCategory;
import com.abapguardian.core.rule.RuleSeverity;
import com.abapguardian.core.rules.AbstractRule;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.abapguardian.core.rules.RuleSupport.findingAt;

/** Deterministic Clean ABAP checks with concrete modernization suggestions. */
public final class CleanCodeRules {

    private CleanCodeRules() {
    }

    public static List<AbapRule> all() {
        return List.of(
                new MoveStatement(),
                new ComputeStatement(),
                new FormRoutine(),
                new CallMethodStatement(),
                new DeepNesting(),
                new BooleanLiteral());
    }

    static final class MoveStatement extends AbstractRule {
        MoveStatement() {
            super("CLEAN_MOVE_STATEMENT", RuleCategory.CLEAN_CODE, RuleSeverity.LOW);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement statement : context.getSource().getStatements()) {
                if (!"MOVE".equals(statement.getFirstKeyword()) || !statement.containsWord("TO")) {
                    continue;
                }
                AbapToken source = statement.wordAfter("MOVE");
                AbapToken target = statement.wordAfter("TO");
                String suggestion = source == null || target == null ? "<target> = <source>."
                        : target.getText() + " = " + source.getText() + ".";
                findings.add(findingAt(statement, getRuleId(), getCategory(), getDefaultSeverity())
                        .confidence(0.99)
                        .title("Prefer assignment operator over MOVE")
                        .explanation("MOVE is an older, more verbose assignment form. Direct assignment is "
                                + "shorter and makes the data flow easier to scan.")
                        .recommendation("Replace MOVE source TO target with target = source.")
                        .suggestedCode(suggestion)
                        .addDocumentationReference("Clean ABAP: prefer functional and concise language constructs")
                        .build());
            }
            return findings;
        }
    }

    static final class ComputeStatement extends AbstractRule {
        ComputeStatement() {
            super("CLEAN_COMPUTE_STATEMENT", RuleCategory.CLEAN_CODE, RuleSeverity.LOW);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement statement : context.getSource().getStatements()) {
                if (!"COMPUTE".equals(statement.getFirstKeyword())) {
                    continue;
                }
                String directAssignment = statement.toSourceText()
                        .replaceFirst("(?i)^COMPUTE\\s+", "");
                findings.add(findingAt(statement, getRuleId(), getCategory(), getDefaultSeverity())
                        .confidence(0.99)
                        .title("Prefer direct calculation assignment")
                        .explanation("COMPUTE adds no meaning to a modern ABAP calculation and makes the "
                                + "statement more verbose.")
                        .recommendation("Use a direct assignment expression.")
                        .suggestedCode(directAssignment)
                        .addDocumentationReference("Clean ABAP: keep statements concise")
                        .build());
            }
            return findings;
        }
    }

    static final class FormRoutine extends AbstractRule {
        FormRoutine() {
            super("CLEAN_FORM_ROUTINE", RuleCategory.CLEAN_CODE, RuleSeverity.MEDIUM);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement statement : context.getSource().getStatements()) {
                if (!"FORM".equals(statement.getFirstKeyword())) {
                    continue;
                }
                AbapToken name = statement.wordAfter("FORM");
                String method = name == null ? "execute" : name.getText().toLowerCase(Locale.ROOT);
                findings.add(findingAt(statement, getRuleId(), getCategory(), getDefaultSeverity())
                        .confidence(0.9)
                        .requiresHumanReview(true)
                        .title("Procedural FORM routine")
                        .explanation("FORM/PERFORM routines rely on global program context and make contracts, "
                                + "dependencies and automated tests harder to understand.")
                        .recommendation("Move the routine into a small class method with explicit IMPORTING, "
                                + "RETURNING or CHANGING parameters.")
                        .suggestedCode("METHOD " + method + ".\n  \" implementation\nENDMETHOD.")
                        .addDocumentationReference("Clean ABAP: prefer methods to procedural routines")
                        .build());
            }
            return findings;
        }
    }

    static final class CallMethodStatement extends AbstractRule {
        CallMethodStatement() {
            super("CLEAN_CALL_METHOD_STATEMENT", RuleCategory.CLEAN_CODE, RuleSeverity.LOW);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement statement : context.getSource().getStatements()) {
                if (!"CALL METHOD".equals(statement.getLeadingWords(2))) {
                    continue;
                }
                AbapToken method = statement.wordAfter("METHOD");
                String call = method == null ? "object->method( )."
                        : method.getText() + "( ).";
                findings.add(findingAt(statement, getRuleId(), getCategory(), getDefaultSeverity())
                        .confidence(0.9)
                        .requiresHumanReview(true)
                        .title("Verbose CALL METHOD syntax")
                        .explanation("The standalone method-call syntax is easier to read and composes better "
                                + "with modern ABAP expressions.")
                        .recommendation("Use object->method( ... ) or class=>method( ... ) and preserve the "
                                + "existing parameter bindings.")
                        .suggestedCode(call)
                        .addDocumentationReference("Clean ABAP: prefer functional method calls")
                        .build());
            }
            return findings;
        }
    }

    private static final Set<BlockType> CONTROL_BLOCKS = EnumSet.of(
            BlockType.IF, BlockType.ELSEIF, BlockType.ELSE, BlockType.CASE, BlockType.WHEN,
            BlockType.LOOP, BlockType.DO, BlockType.WHILE, BlockType.SELECT_LOOP,
            BlockType.TRY, BlockType.CATCH, BlockType.CLEANUP, BlockType.PROVIDE);

    static final class DeepNesting extends AbstractRule {
        DeepNesting() {
            super("CLEAN_DEEP_NESTING", RuleCategory.CLEAN_CODE, RuleSeverity.MEDIUM);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement statement : context.getSource().getStatements()) {
                if (!opensControlBlock(statement) || controlDepth(statement.getBlock()) < 3) {
                    continue;
                }
                findings.add(findingAt(statement, getRuleId(), getCategory(), getDefaultSeverity())
                        .confidence(0.9)
                        .requiresHumanReview(true)
                        .title("Control flow nested more than three levels")
                        .explanation("Deep nesting increases cognitive load and makes the main business path "
                                + "difficult to identify.")
                        .recommendation("Use guard clauses, CHECK/CONTINUE/RETURN, or extract the nested "
                                + "operation into a well-named method.")
                        .suggestedCode("IF <guard condition> IS NOT SATISFIED.\n  RETURN.\nENDIF.\n\n"
                                + "<main business path>")
                        .addDocumentationReference("Clean ABAP: keep nesting depth low")
                        .build());
            }
            return findings;
        }

        private boolean opensControlBlock(AbapStatement statement) {
            String keyword = statement.getFirstKeyword();
            return Set.of("IF", "CASE", "LOOP", "DO", "WHILE", "SELECT", "TRY", "PROVIDE")
                    .contains(keyword);
        }

        private int controlDepth(AbapBlock block) {
            int depth = 0;
            for (AbapBlock current = block; current != null; current = current.getParent()) {
                if (CONTROL_BLOCKS.contains(current.getType())) {
                    depth++;
                }
            }
            return depth;
        }
    }

    static final class BooleanLiteral extends AbstractRule {
        BooleanLiteral() {
            super("CLEAN_BOOLEAN_LITERAL", RuleCategory.CLEAN_CODE, RuleSeverity.LOW);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement statement : context.getSource().getStatements()) {
                if (!Set.of("IF", "ELSEIF", "CHECK", "ASSERT", "WHILE")
                        .contains(statement.getFirstKeyword())) {
                    continue;
                }
                boolean flagLiteral = false;
                for (AbapToken literal : statement.literalTokens()) {
                    if (literal.getType() == TokenType.STRING_LITERAL
                            && ("'X'".equalsIgnoreCase(literal.getText())
                            || "' '".equals(literal.getText()))) {
                        flagLiteral = true;
                        break;
                    }
                }
                if (!flagLiteral) {
                    continue;
                }
                findings.add(findingAt(statement, getRuleId(), getCategory(), getDefaultSeverity())
                        .confidence(0.8)
                        .requiresHumanReview(true)
                        .title("Boolean condition uses character literal")
                        .explanation("Character literals such as 'X' and space hide the intent of a boolean "
                                + "condition and are easy to mistype.")
                        .recommendation("Use abap_true/abap_false or xsdbool( ) and give the flag a "
                                + "positive, intention-revealing name.")
                        .suggestedCode(statement.toSourceText()
                                .replace("'X'", "abap_true")
                                .replace("'x'", "abap_true")
                                .replace("' '", "abap_false"))
                        .addDocumentationReference("Clean ABAP: use ABAP booleans")
                        .build());
            }
            return findings;
        }
    }
}
