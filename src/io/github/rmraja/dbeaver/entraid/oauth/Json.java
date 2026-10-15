package io.github.rmraja.dbeaver.entraid.oauth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader, enough for OAuth token endpoint responses.
 * Objects become {@link Map}, arrays {@link List}, numbers {@link Double}.
 */
final class Json {

    private final String s;
    private int pos;

    private Json(String s) {
        this.s = s;
    }

    static Map<String, Object> parseObject(String text) throws java.io.IOException {
        Json p = new Json(text);
        p.skipWs();
        Object v = p.readValue();
        if (!(v instanceof Map)) {
            throw new java.io.IOException("Expected JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) v;
        return map;
    }

    private Object readValue() throws java.io.IOException {
        skipWs();
        if (pos >= s.length()) throw error("Unexpected end of JSON");
        char c = s.charAt(pos);
        switch (c) {
            case '{': return readObject();
            case '[': return readArray();
            case '"': return readString();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default: return readNumber();
        }
    }

    private Map<String, Object> readObject() throws java.io.IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWs();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWs();
            String key = readString();
            skipWs();
            if (s.charAt(pos++) != ':') throw error("Expected ':'");
            map.put(key, readValue());
            skipWs();
            char c = s.charAt(pos++);
            if (c == '}') return map;
            if (c != ',') throw error("Expected ',' or '}'");
        }
    }

    private List<Object> readArray() throws java.io.IOException {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWs();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(readValue());
            skipWs();
            char c = s.charAt(pos++);
            if (c == ']') return list;
            if (c != ',') throw error("Expected ',' or ']'");
        }
    }

    private String readString() throws java.io.IOException {
        if (s.charAt(pos) != '"') throw error("Expected string");
        pos++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = s.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = s.charAt(pos++);
            switch (e) {
                case '"': case '\\': case '/': sb.append(e); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default: throw error("Bad escape");
            }
        }
    }

    private Double readNumber() throws java.io.IOException {
        int start = pos;
        while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
        if (start == pos) throw error("Unexpected character");
        return Double.valueOf(s.substring(start, pos));
    }

    private void expect(String word) throws java.io.IOException {
        if (!s.startsWith(word, pos)) throw error("Expected " + word);
        pos += word.length();
    }

    private char peek() {
        return pos < s.length() ? s.charAt(pos) : 0;
    }

    private void skipWs() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
    }

    private java.io.IOException error(String msg) {
        return new java.io.IOException(msg + " at position " + pos);
    }
}
