package com.hamza.account.features.export;

import java.util.List;

/**
 * The company as a report's head prints it: its name, the lines under the name, and its picture.
 * <p>
 * Every value arrives already written, as a {@link DocumentPdfPage}'s do; the renderer decides where
 * the text goes and nothing about what it says.
 *
 * @param lines the address, the telephone - each already labelled, blank ones left out
 * @param logo  the picture, or null to print the name alone
 */
public record ReportLetterhead(String name, List<String> lines, byte[] logo) {

    public static final ReportLetterhead EMPTY = new ReportLetterhead("", List.of(), null);

    public ReportLetterhead {
        name = name == null ? "" : name.strip();
        lines = lines == null ? List.of() : lines.stream()
                .filter(line -> line != null && !line.isBlank())
                .map(String::strip)
                .toList();
        logo = logo == null || logo.length == 0 ? null : logo;
    }

    /** Nothing to print: a company row nobody filled in prints no empty band above the report. */
    public boolean isEmpty() {
        return name.isEmpty() && lines.isEmpty() && logo == null;
    }
}
