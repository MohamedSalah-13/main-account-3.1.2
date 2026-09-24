package com.hamza.account.features.export;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ReportStyle} as the one line of text it is stored as, and back.
 * <p>
 * <b>One setting rather than nineteen</b>, because the style is one choice: nineteen shared keys would
 * be nineteen writes to {@code app_setting} for one change on the settings screen, and another till
 * could read half an old style and half a new one between them.
 * <p>
 * The text is {@code name=value} lines. Reading is forgiving on purpose, since the value comes from a
 * database another build may have written: a name it does not know is passed over, a value it cannot
 * read keeps the default, and a missing line keeps the default - so a style stored before a setting
 * existed reads as that setting's default, and nothing about a stored style can stop a report from
 * printing. {@code \} and a line break inside a value are escaped, though {@link ReportStyle} keeps its
 * one free-text value on one line anyway.
 */
public final class ReportStyleCodec {

    private static final int VERSION = 1;

    private ReportStyleCodec() {
    }

    public static String encode(ReportStyle style) {
        StringBuilder text = new StringBuilder();
        line(text, "v", VERSION);
        line(text, "title", style.titleSize());
        line(text, "subtitle", style.subtitleSize());
        line(text, "header", style.headerSize());
        line(text, "body", style.bodySize());
        line(text, "totals", style.totalsSize());
        line(text, "small", style.smallSize());
        line(text, "compact", style.compactRows());
        line(text, "numbering", style.pageNumbering().name());
        line(text, "letterhead", style.showLetterhead());
        line(text, "showTitle", style.showTitle());
        line(text, "showSubtitle", style.showSubtitle());
        line(text, "printedAt", style.showPrintedAt());
        line(text, "printedBy", style.showPrintedBy());
        line(text, "footer", style.showFooter());
        line(text, "footerText", style.footerText());
        line(text, "palette", style.palette().name());
        line(text, "inkSaver", style.inkSaver());
        line(text, "docLetterhead", style.showDocumentLetterhead());
        line(text, "docTopSpace", style.documentTopSpaceMm());
        return text.toString();
    }

    /** The stored text back; blank or unreadable text is {@link ReportStyle#DEFAULT}. */
    public static ReportStyle decode(String stored) {
        if (stored == null || stored.isBlank()) {
            return ReportStyle.DEFAULT;
        }
        Map<String, String> values = new HashMap<>();
        for (String line : stored.split("\n")) {
            int equals = line.indexOf('=');
            if (equals > 0) {
                values.put(line.substring(0, equals).strip(), unescape(line.substring(equals + 1)));
            }
        }
        ReportStyle d = ReportStyle.DEFAULT;
        return d.toBuilder()
                .titleSize(number(values, "title", d.titleSize()))
                .subtitleSize(number(values, "subtitle", d.subtitleSize()))
                .headerSize(number(values, "header", d.headerSize()))
                .bodySize(number(values, "body", d.bodySize()))
                .totalsSize(number(values, "totals", d.totalsSize()))
                .smallSize(number(values, "small", d.smallSize()))
                .compactRows(flag(values, "compact", d.compactRows()))
                .pageNumbering(values.containsKey("numbering")
                        ? PageNumbering.fromStoredValue(values.get("numbering")) : d.pageNumbering())
                .showLetterhead(flag(values, "letterhead", d.showLetterhead()))
                .showTitle(flag(values, "showTitle", d.showTitle()))
                .showSubtitle(flag(values, "showSubtitle", d.showSubtitle()))
                .showPrintedAt(flag(values, "printedAt", d.showPrintedAt()))
                .showPrintedBy(flag(values, "printedBy", d.showPrintedBy()))
                .showFooter(flag(values, "footer", d.showFooter()))
                .footerText(values.getOrDefault("footerText", d.footerText()))
                .palette(values.containsKey("palette")
                        ? ReportPalette.fromStoredValue(values.get("palette")) : d.palette())
                .inkSaver(flag(values, "inkSaver", d.inkSaver()))
                .showDocumentLetterhead(flag(values, "docLetterhead", d.showDocumentLetterhead()))
                .documentTopSpaceMm(number(values, "docTopSpace", d.documentTopSpaceMm()))
                .build();
    }

    private static void line(StringBuilder text, String name, Object value) {
        if (!text.isEmpty()) {
            text.append('\n');
        }
        text.append(name).append('=').append(escape(String.valueOf(value)));
    }

    private static int number(Map<String, String> values, String name, int fallback) {
        String value = values.get(name);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Only the two words {@link #encode} writes; anything else is not a decision anybody took. */
    private static boolean flag(Map<String, String> values, String name, boolean fallback) {
        String value = values.get(name);
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        return fallback;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                out.append(switch (next) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    default -> next;
                });
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
