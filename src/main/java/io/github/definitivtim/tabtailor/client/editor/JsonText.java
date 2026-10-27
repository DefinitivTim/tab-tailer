package io.github.definitivtim.tabtailor.client.editor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Small text editor for the lenient JSON files of Tab Tailor. It changes single top-level values in place, so
 * comments, formatting and every other key of a file stay exactly as the user wrote them.
 */
final class JsonText {
    /** Location of one top-level entry: the key (with quotes) starts at keyStart, the value spans valueStart..valueEnd. */
    record Entry(int keyStart, int valueStart, int valueEnd) {
    }

    private JsonText() {
    }

    /** Finds a top-level key, null if the file does not have it. */
    static @Nullable Entry find(String text, String key) {
        int i = skip(text, 0);
        if (i >= text.length() || text.charAt(i) != '{') {
            return null;
        }
        i++;
        while (true) {
            i = skip(text, i);
            if (i >= text.length() || text.charAt(i) == '}') {
                return null;
            }
            if (text.charAt(i) == ',') {
                i++;
                continue;
            }
            int keyStart = i;
            String name;
            if (text.charAt(i) == '"' || text.charAt(i) == '\'') {
                int end = stringEnd(text, i);
                name = unquote(text.substring(i, end));
                i = end;
            } else {
                int end = i;
                while (end < text.length() && !Character.isWhitespace(text.charAt(end)) && text.charAt(end) != ':') {
                    end++;
                }
                name = text.substring(i, end);
                i = end;
            }
            i = skip(text, i);
            if (i >= text.length() || text.charAt(i) != ':') {
                return null; // broken file, do not touch it
            }
            i = skip(text, i + 1);
            int valueStart = i;
            int valueEnd = valueEnd(text, i);
            if (name.equals(key)) {
                return new Entry(keyStart, valueStart, valueEnd);
            }
            i = valueEnd;
        }
    }

    /** Replaces the value of a top-level key, or adds the key right after the opening brace. */
    static String set(String text, String key, String jsonValue) {
        Entry entry = find(text, key);
        if (entry != null) {
            return text.substring(0, entry.valueStart()) + jsonValue + text.substring(entry.valueEnd());
        }
        Entry anchor = find(text, "tab");
        if (anchor != null) {
            // keep "tab" first, put the new key right below it
            return text.substring(0, anchor.valueEnd()) + ",\n  " + quote(key) + ": " + jsonValue + text.substring(anchor.valueEnd());
        }
        int brace = skip(text, 0);
        if (brace >= text.length() || text.charAt(brace) != '{') {
            throw new IllegalStateException("file does not start with {");
        }
        int next = skip(text, brace + 1);
        boolean empty = next < text.length() && text.charAt(next) == '}';
        String insert = "\n  " + quote(key) + ": " + jsonValue + (empty ? "\n" : ",");
        return text.substring(0, brace + 1) + insert + text.substring(brace + 1);
    }

    /** Removes a top-level key together with its comma. */
    static String remove(String text, String key) {
        Entry entry = find(text, key);
        if (entry == null) {
            return text;
        }
        int start = entry.keyStart();
        int end = entry.valueEnd();
        int after = skipSpaces(text, end);
        if (after < text.length() && text.charAt(after) == ',') {
            end = after + 1;
        } else {
            // last entry: take the comma before it instead
            int before = start - 1;
            while (before >= 0 && Character.isWhitespace(text.charAt(before))) {
                before--;
            }
            if (before >= 0 && text.charAt(before) == ',') {
                start = before;
            }
        }
        // swallow the rest of the line when it is empty
        int lineStart = start;
        while (lineStart > 0 && (text.charAt(lineStart - 1) == ' ' || text.charAt(lineStart - 1) == '\t')) {
            lineStart--;
        }
        if (lineStart == 0 || text.charAt(lineStart - 1) == '\n') {
            int lineEnd = end;
            while (lineEnd < text.length() && (text.charAt(lineEnd) == ' ' || text.charAt(lineEnd) == '\t')) {
                lineEnd++;
            }
            if (lineEnd < text.length() && text.charAt(lineEnd) == '\r') {
                lineEnd++;
            }
            if (lineEnd < text.length() && text.charAt(lineEnd) == '\n') {
                return text.substring(0, lineStart) + text.substring(lineEnd + 1);
            }
        }
        return text.substring(0, start) + text.substring(end);
    }

    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    /** A small object, one key per line, for "section_style", "sidebar" and "tab_style". */
    static String object(com.google.gson.JsonObject o) {
        if (o.size() == 0) {
            return "{ }";
        }
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (var entry : o.entrySet()) {
            sb.append("    ").append(quote(entry.getKey())).append(": ").append(new com.google.gson.Gson().toJson(entry.getValue()));
            sb.append(++i < o.size() ? ",\n" : "\n");
        }
        return sb.append("  }").toString();
    }

    /** A multi-line string array in the style of the default files. */
    static String stringArray(List<String> values) {
        if (values.isEmpty()) {
            return "[ ]";
        }
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < values.size(); i++) {
            sb.append("    ").append(quote(values.get(i)));
            if (i < values.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        return sb.append("  ]").toString();
    }

    // ------------------------------------------------------------------ scanning

    private static int skipSpaces(String t, int i) {
        while (i < t.length() && (t.charAt(i) == ' ' || t.charAt(i) == '\t')) {
            i++;
        }
        return i;
    }

    /** Skips whitespace and comments. */
    private static int skip(String t, int i) {
        while (i < t.length()) {
            char c = t.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '/' && i + 1 < t.length() && t.charAt(i + 1) == '/') {
                while (i < t.length() && t.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < t.length() && t.charAt(i + 1) == '*') {
                int end = t.indexOf("*/", i + 2);
                i = end < 0 ? t.length() : end + 2;
            } else if (c == '#') {
                while (i < t.length() && t.charAt(i) != '\n') {
                    i++;
                }
            } else {
                break;
            }
        }
        return i;
    }

    private static int stringEnd(String t, int i) {
        char quote = t.charAt(i);
        i++;
        while (i < t.length()) {
            char c = t.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                return i + 1;
            }
            i++;
        }
        return t.length();
    }

    private static int valueEnd(String t, int i) {
        if (i >= t.length()) {
            return i;
        }
        char c = t.charAt(i);
        if (c == '"' || c == '\'') {
            return stringEnd(t, i);
        }
        if (c == '{' || c == '[') {
            int depth = 0;
            while (i < t.length()) {
                int s = skip(t, i);
                if (s != i) {
                    i = s;
                    continue;
                }
                char d = t.charAt(i);
                if (d == '"' || d == '\'') {
                    i = stringEnd(t, i);
                    continue;
                }
                if (d == '{' || d == '[') {
                    depth++;
                } else if (d == '}' || d == ']') {
                    depth--;
                    if (depth == 0) {
                        return i + 1;
                    }
                }
                i++;
            }
            return t.length();
        }
        while (i < t.length()) {
            char d = t.charAt(i);
            if (d == ',' || d == '}' || d == ']' || Character.isWhitespace(d) || d == '/') {
                break;
            }
            i++;
        }
        return i;
    }

    private static String unquote(String quoted) {
        String inner = quoted.length() >= 2 ? quoted.substring(1, quoted.length() - 1) : quoted;
        return inner.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
