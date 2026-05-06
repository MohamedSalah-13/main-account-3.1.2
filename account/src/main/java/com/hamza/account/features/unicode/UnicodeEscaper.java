package com.hamza.account.features.unicode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UnicodeEscaper {

    private UnicodeEscaper() { }

    /**
     * Converts a Java String to Java/Properties-style unicode escapes.
     * Example: "حفظ" -> "\\u062D\\u0641\\u0638"
     *
     * Note: keeps ASCII characters as-is, escapes others as \\uXXXX.
     * Also escapes backslash, tab/newline/carriage-return/formfeed for safety in .properties.
     */
    public static String toUnicodeEscapes(String input) {
        if (input == null) return null;

        StringBuilder sb = new StringBuilder(input.length() * 6);
        for (int i = 0; i < input.length(); ) {
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);

            if (cp == '\\') sb.append("\\\\");
            else if (cp == '\t') sb.append("\\t");
            else if (cp == '\n') sb.append("\\n");
            else if (cp == '\r') sb.append("\\r");
            else if (cp == '\f') sb.append("\\f");
            else if (cp >= 0x20 && cp <= 0x7E) {
                // Printable ASCII
                sb.append((char) cp);
            } else if (cp <= 0xFFFF) {
                sb.append(String.format("\\u%04X", cp));
            } else {
                // For code points beyond BMP, Java escapes need surrogate pairs
                char[] surrogates = Character.toChars(cp);
                sb.append(String.format("\\u%04X\\u%04X", (int) surrogates[0], (int) surrogates[1]));
            }
        }
        return sb.toString();
    }

    /**
     * Converts Java/Properties-style unicode escapes back to a normal Java String.
     * Example: "\u062D\u0641\u0638" -> "حفظ"
     */
    public static String fromUnicodeEscapes(String input) {
        if (input == null) return null;

        // First, handle \\uXXXX sequences
        Pattern p = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        Matcher m = p.matcher(input);
        StringBuffer out = new StringBuffer(input.length());
        while (m.find()) {
            int codeUnit = Integer.parseInt(m.group(1), 16);
            m.appendReplacement(out, Matcher.quoteReplacement(String.valueOf((char) codeUnit)));
        }
        m.appendTail(out);

        // Then unescape common sequences used in properties
        return out.toString()
                .replace("\\\\", "\\")
                .replace("\\t", "\t")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\f", "\f");
    }

    // Quick test
    public static void main(String[] args) {
        String arabic = "تعديل";
        String escaped = toUnicodeEscapes(arabic);
        System.out.println(escaped);
        System.out.println(fromUnicodeEscapes(escaped));
    }
}
