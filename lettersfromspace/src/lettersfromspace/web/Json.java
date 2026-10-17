// Letters from Space - English rewrite (2026) of the 2001 ESI neural network lab.
package lettersfromspace.web;

import java.util.List;
import java.util.Map;

/** A tiny JSON writer (maps, lists, arrays, strings, numbers, booleans) so the server needs no libraries. */
final class Json {

    private Json() {}

    static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            writeString(sb, s);
        } else if (v instanceof Character c) {
            writeString(sb, String.valueOf(c));
        } else if (v instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (v instanceof Double d) {
            sb.append(d.isNaN() || d.isInfinite() ? "null" : trimDouble(d));
        } else if (v instanceof Float f) {
            sb.append(f.isNaN() || f.isInfinite() ? "null" : trimDouble(f));
        } else if (v instanceof Number n) {
            sb.append(n);
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                write(sb, list.get(i));
            }
            sb.append(']');
        } else if (v instanceof double[] arr) {
            sb.append('[');
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(trimDouble(arr[i]));
            }
            sb.append(']');
        } else if (v instanceof int[] arr) {
            sb.append('[');
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(arr[i]);
            }
            sb.append(']');
        } else if (v instanceof Object[] arr) {
            write(sb, List.of(arr));
        } else {
            writeString(sb, v.toString());
        }
    }

    private static String trimDouble(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            return Long.toString((long) d);
        }
        // 6 significant decimals are plenty for a dashboard
        return String.format(java.util.Locale.ROOT, "%.6g", d).replaceAll("0+$", "").replaceAll("\\.$", "");
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
}
