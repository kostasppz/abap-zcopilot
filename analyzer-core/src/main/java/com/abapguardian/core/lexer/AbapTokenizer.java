package com.abapguardian.core.lexer;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizer for ABAP source code.
 *
 * <p>Handles:
 * <ul>
 *   <li>Full-line comments (asterisk in column 1)</li>
 *   <li>Inline comments (double quote) and pseudo comments ("#EC ...)</li>
 *   <li>Character literals ('...') with doubled-quote escapes</li>
 *   <li>Backquote string literals (`...`)</li>
 *   <li>String templates (|...|) including embedded expressions in braces</li>
 *   <li>Pragmas (##...)</li>
 *   <li>Statement terminators, chain colons and chain commas</li>
 * </ul>
 *
 * <p>Line and column numbers are 1-based and refer to the original source.
 */
public final class AbapTokenizer {

    /**
     * Tokenize the given source. The returned list contains all tokens
     * including comments; downstream consumers filter as needed.
     */
    public List<AbapToken> tokenize(String source) {
        List<AbapToken> tokens = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return tokens;
        }
        String[] lines = source.split("\n", -1);
        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            String line = stripCr(lines[lineIdx]);
            int lineNo = lineIdx + 1;
            if (!line.isEmpty() && line.charAt(0) == '*') {
                tokens.add(new AbapToken(TokenType.LINE_COMMENT, line, lineNo, 1, lineNo, line.length()));
                continue;
            }
            tokenizeLine(line, lineNo, tokens);
        }
        return tokens;
    }

    private static String stripCr(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private void tokenizeLine(String line, int lineNo, List<AbapToken> tokens) {
        int i = 0;
        int len = line.length();
        while (i < len) {
            char c = line.charAt(i);
            if (c == ' ' || c == '\t') {
                i++;
                continue;
            }
            if (c == '"') {
                // Inline comment to end of line. Pseudo comments start with "#
                String text = line.substring(i);
                TokenType type = text.startsWith("\"#") ? TokenType.PSEUDO_COMMENT : TokenType.INLINE_COMMENT;
                tokens.add(new AbapToken(type, text, lineNo, i + 1, lineNo, len));
                return;
            }
            if (c == '\'') {
                i = readQuoted(line, lineNo, i, '\'', TokenType.STRING_LITERAL, tokens);
                continue;
            }
            if (c == '`') {
                i = readQuoted(line, lineNo, i, '`', TokenType.BACKQUOTE_LITERAL, tokens);
                continue;
            }
            if (c == '|') {
                i = readTemplate(line, lineNo, i, tokens);
                continue;
            }
            if (c == '#' && i + 1 < len && line.charAt(i + 1) == '#') {
                int start = i;
                i += 2;
                while (i < len && (Character.isLetterOrDigit(line.charAt(i)) || line.charAt(i) == '_')) {
                    i++;
                }
                tokens.add(new AbapToken(TokenType.PRAGMA, line.substring(start, i), lineNo, start + 1, lineNo, i));
                continue;
            }
            if (Character.isDigit(c)) {
                int start = i;
                while (i < len && (Character.isDigit(line.charAt(i)))) {
                    i++;
                }
                tokens.add(new AbapToken(TokenType.NUMBER, line.substring(start, i), lineNo, start + 1, lineNo, i));
                continue;
            }
            if (isWordChar(c)) {
                int start = i;
                while (i < len && isWordChar(line.charAt(i))) {
                    i++;
                }
                tokens.add(new AbapToken(TokenType.WORD, line.substring(start, i), lineNo, start + 1, lineNo, i));
                continue;
            }
            TokenType type = switch (c) {
                case '.' -> TokenType.PERIOD;
                case ':' -> TokenType.COLON;
                case ',' -> TokenType.COMMA;
                default -> TokenType.PUNCTUATION;
            };
            tokens.add(new AbapToken(type, String.valueOf(c), lineNo, i + 1, lineNo, i + 1));
            i++;
        }
    }

    /**
     * ABAP identifiers may contain letters, digits, underscore, slash (namespaces),
     * hyphen (structure components) and tilde (dynpro fields, alias components).
     * We keep hyphenated component paths (e.g. ls_pa0002-pernr) as one WORD token
     * so rules can inspect structure component access easily. We also keep the
     * '@' host-variable prefix and '>' from -&gt; / =&gt; access out of words.
     */
    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '/' || c == '-';
    }

    private int readQuoted(String line, int lineNo, int start, char quote, TokenType type, List<AbapToken> tokens) {
        int i = start + 1;
        int len = line.length();
        StringBuilder sb = new StringBuilder();
        sb.append(quote);
        while (i < len) {
            char c = line.charAt(i);
            sb.append(c);
            if (c == quote) {
                if (i + 1 < len && line.charAt(i + 1) == quote) {
                    sb.append(quote);
                    i += 2;
                    continue;
                }
                i++;
                tokens.add(new AbapToken(type, sb.toString(), lineNo, start + 1, lineNo, i));
                return i;
            }
            i++;
        }
        // Unterminated literal: consume to end of line.
        tokens.add(new AbapToken(type, sb.toString(), lineNo, start + 1, lineNo, len));
        return len;
    }

    private int readTemplate(String line, int lineNo, int start, List<AbapToken> tokens) {
        int i = start + 1;
        int len = line.length();
        int braceDepth = 0;
        StringBuilder sb = new StringBuilder("|");
        while (i < len) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < len) {
                sb.append(c).append(line.charAt(i + 1));
                i += 2;
                continue;
            }
            sb.append(c);
            if (c == '{') {
                braceDepth++;
            } else if (c == '}') {
                braceDepth = Math.max(0, braceDepth - 1);
            } else if (c == '|' && braceDepth == 0) {
                i++;
                tokens.add(new AbapToken(TokenType.STRING_TEMPLATE, sb.toString(), lineNo, start + 1, lineNo, i));
                return i;
            }
            i++;
        }
        tokens.add(new AbapToken(TokenType.STRING_TEMPLATE, sb.toString(), lineNo, start + 1, lineNo, len));
        return len;
    }
}
