package tqq;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tiny JSON reader/writer (enough for Ollama replies and the history files). */
public final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        return p.value();
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value() {
        ws();
        char c = s.charAt(i);
        switch (c) {
            case '{': return obj();
            case '[': return arr();
            case '"': return str();
            case 't': i += 4; return Boolean.TRUE;
            case 'f': i += 5; return Boolean.FALSE;
            case 'n': i += 4; return null;
            default: return num();
        }
    }

    private Map<String, Object> obj() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            String k = str();
            ws();
            i++; // ':'
            m.put(k, value());
            ws();
            if (s.charAt(i++) == '}') return m;
        }
    }

    private List<Object> arr() {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (s.charAt(i) == ']') { i++; return l; }
        while (true) {
            l.add(value());
            ws();
            if (s.charAt(i++) == ']') return l;
        }
    }

    private String str() {
        StringBuilder b = new StringBuilder();
        i++;
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            char e = s.charAt(i++);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'r': b.append('\r'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                default: b.append(e);
            }
        }
    }

    private Number num() {
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        String t = s.substring(st, i);
        if (t.contains(".") || t.contains("e") || t.contains("E")) return Double.parseDouble(t);
        return Long.parseLong(t);
    }

    // ---------------------------------------------------------------- writer

    public static String write(Object o) {
        StringBuilder b = new StringBuilder();
        write(o, b, -1);
        return b.toString();
    }

    /** Pretty version (2-space indent), used for the history files. */
    public static String pretty(Object o) {
        StringBuilder b = new StringBuilder();
        write(o, b, 0);
        return b.toString();
    }

    private static void nl(StringBuilder b, int depth) {
        if (depth < 0) return;
        b.append('\n');
        b.append("  ".repeat(depth));
    }

    @SuppressWarnings("unchecked")
    private static void write(Object o, StringBuilder b, int depth) {
        int next = depth < 0 ? -1 : depth + 1;
        if (o == null) {
            b.append("null");
        } else if (o instanceof String str) {
            quote(str, b);
        } else if (o instanceof Number || o instanceof Boolean) {
            b.append(o);
        } else if (o instanceof Map<?, ?> m) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) b.append(',');
                first = false;
                nl(b, next);
                quote(String.valueOf(e.getKey()), b);
                b.append(depth < 0 ? ":" : ": ");
                write(e.getValue(), b, next);
            }
            if (!m.isEmpty()) nl(b, depth);
            b.append('}');
        } else if (o instanceof List<?> l) {
            b.append('[');
            for (int k = 0; k < l.size(); k++) {
                if (k > 0) b.append(',');
                nl(b, next);
                write(l.get(k), b, next);
            }
            if (!l.isEmpty()) nl(b, depth);
            b.append(']');
        } else {
            quote(o.toString(), b);
        }
    }

    private static void quote(String s, StringBuilder b) {
        b.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }
}
