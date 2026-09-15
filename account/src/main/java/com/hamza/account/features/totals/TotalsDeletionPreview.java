package com.hamza.account.features.totals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

/**
 * What the operator is shown before documents are deleted: which ones, and how much they come to.
 * <p>
 * The confirmation used to be the same one-line question whether it was about one invoice or fifty,
 * so a tick left on a row from an earlier look, or a "select the page" pressed by mistake, went
 * through on the same click as a deliberate delete. A list of numbers, dates and names is what lets
 * somebody notice the invoice that should not be there.
 *
 * @param documents   the documents about to go, in the order the screen shows them
 * @param total       their totals added up
 * @param earliest    the oldest date among them, or null when none could be read
 * @param latest      the newest date among them, or null when none could be read
 * @param unseenPages how many other pages of the same result the selection does not reach - a
 *                    tick is per page, and "the whole result" is not what is being deleted
 */
public record TotalsDeletionPreview(List<Document> documents, BigDecimal total,
                                    LocalDate earliest, LocalDate latest, int unseenPages) {

    /** One document as the confirmation lists it. */
    public record Document(int id, String date, String partyName, int itemCount, BigDecimal total) {
        public Document {
            total = total == null ? BigDecimal.ZERO : total;
        }
    }

    public TotalsDeletionPreview {
        documents = List.copyOf(documents);
        Objects.requireNonNull(total, "total");
        if (unseenPages < 0) {
            throw new IllegalArgumentException("unseen pages " + unseenPages);
        }
    }

    /**
     * @param fromTickedRows true when the documents are the ticked rows of a page, false for a single
     *                       row deleted from its own button or the Delete key
     * @param pageCount      how many pages the current result has
     */
    public static TotalsDeletionPreview of(List<Document> documents, boolean fromTickedRows, int pageCount) {
        BigDecimal total = BigDecimal.ZERO;
        LocalDate earliest = null;
        LocalDate latest = null;
        for (Document document : documents) {
            total = total.add(document.total());
            LocalDate date = parse(document.date());
            if (date == null) {
                continue;
            }
            if (earliest == null || date.isBefore(earliest)) earliest = date;
            if (latest == null || date.isAfter(latest)) latest = date;
        }
        int unseen = fromTickedRows ? Math.max(0, pageCount - 1) : 0;
        return new TotalsDeletionPreview(documents, total, earliest, latest, unseen);
    }

    public int count() {
        return documents.size();
    }

    /** One date shown as a range would read "from 5 May to 5 May". */
    public boolean singleDay() {
        return earliest != null && earliest.equals(latest);
    }

    /** A date the database wrote is ISO; anything else is left out of the range rather than guessed. */
    private static LocalDate parse(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
