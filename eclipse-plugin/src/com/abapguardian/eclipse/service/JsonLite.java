package com.abapguardian.eclipse.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer for the fixed gateway wire format.
 *
 * <p>Deliberately dependency-free so the OSGi bundle needs no third-party
 * libraries. It supports objects, arrays, strings, numbers, booleans and
 * null — exactly what the gateway exchanges.
 */
public final class JsonLite {

    private JsonLite() {
    }

    /** Mutable object wrapper with typed accessors used by the client. */
    public static final class Obj {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public Obj put(String key, Object value) {
            values.put(key, value);
            return this;
        }

        public String str(String key, String fallback) {
            Object v = values.get(key);
            return v instanceof String s ? s : fallback;
        }

        public String strOrNull(String key) {
            Object v = values.get(key);
            return v instanceof String s ? s : null;
        }

        public double num(String key, double fallback) {
            Object v = values.get(key);
            return v instanceof Number n ? n.doubleValue() : fallback;
        }

        public boolean bool(String key, boolean fallback) {
            Object v = values.get(key);
            return v instanceof Boolean b ? b : fallback;
        }

        @SuppressWarnings("unchecked")
        public List<Obj> arr(String key) {
            Object v = values.get(key);
            List<Obj> result = new ArrayList<>();
            if (v instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Obj o) {
                        result.add(o);
                    } else if (item instanceof Map<?, ?> m) {
                        Obj o = new Obj();
                        ((Map<String, Object>) m).forEach(o::put);
                        result.add(o);
                    }
                }
            }
            return result;
        }

        public List<String> strList(String key) {
            Object v = values.get(key);
            List<String> result = new ArrayList<>();
            if (v instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String s) {
                        result.add(s);
                    }
                }
            }
            return result;
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            writeValue(sb, values);
            return sb.toString();
        }
    }

    public static Obj parseObject(String json) {
        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!(value instanceof Obj obj)) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        return obj;
    }

    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else if (value instanceof Obj obj) {
            writeValue(sb, obj.values);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                writeValue(sb, list.get(i));
            }
            sb.append(']');
        } else {
            writeString(sb, String.valueOf(value));
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        Object parseValue() {
            skipWhitespace();
            char c = peek();
            return switch (c) {
                case '{' -> parseObj();
                case '[' -> parseArr();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        private Obj parseObj() {
            expect('{');
            Obj obj = new Obj();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return obj;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                obj.put(key, parseValue());
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    return obj;
                }
                if (c != ',') {
                    throw error("Expected ',' or '}'");
                }
            }
        }

        private List<Object> parseArr() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw error("Expected ',' or ']'");
                }
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char esc = next();
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            String hex = text.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw error("Invalid escape: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Boolean parseBoolean() {
            if (text.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw error("Invalid literal");
        }

        private Object parseNull() {
            if (text.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw error("Invalid literal");
        }

        private Number parseNumber() {
            int start = pos;
            while (pos < text.length() && "-+.eE0123456789".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
            String num = text.substring(start, pos);
            if (num.isEmpty()) {
                throw error("Invalid number");
            }
            return Double.parseDouble(num);
        }

        void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            if (pos >= text.length()) {
                throw error("Unexpected end of input");
            }
            return text.charAt(pos);
        }

        private char next() {
            char c = peek();
            pos++;
            return c;
        }

        private void expect(char expected) {
            char c = next();
            if (c != expected) {
                throw error("Expected '" + expected + "' but found '" + c + "'");
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + pos);
        }
    }
}
