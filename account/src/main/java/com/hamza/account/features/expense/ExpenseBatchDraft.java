package com.hamza.account.features.expense;

import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The unsaved lines of a batch of expenses, and every decision about them.
 * <p>
 * A batch is a screen convenience, not a shape of the data - decision ق-٦. Each line is saved as the
 * ordinary expense it is, all of them in one transaction. What lives here is what the screen would
 * otherwise decide inline and nothing could check: what a line is, what the batch comes to, what each
 * till is asked for, and when the batch is too large to be one person's stack of receipts.
 */
public final class ExpenseBatchDraft {

    /**
     * A ceiling on one batch. Past it the save is one very long transaction holding a lock on every till
     * named, on a screen nobody can check line by line - and it is far beyond a month of bills.
     */
    public static final int MAX_LINES = 200;

    /** One line as the table shows it: the entry, and the two names read when it was added. */
    public record Line(ExpenseEntry entry, String headingPath, String treasuryName) {
    }

    private final List<Line> lines = new ArrayList<>();

    /** Adds a parsed entry. The entry already passed {@link ExpenseEntry#parse}; this checks the batch. */
    public void add(ExpenseEntry entry, String headingPath, String treasuryName) throws UserValidationException {
        if (entry == null) {
            throw new IllegalArgumentException("entry");
        }
        if (!entry.isNew()) {
            throw new IllegalArgumentException("a batch only holds new expenses");
        }
        if (lines.size() >= MAX_LINES) {
            throw new UserValidationException("expense.batch.error.too.many");
        }
        lines.add(new Line(entry, headingPath, treasuryName));
    }

    /** Removes the line at a zero-based position; a position that is not there removes nothing. */
    public void remove(int index) {
        if (index >= 0 && index < lines.size()) {
            lines.remove(index);
        }
    }

    public void clear() {
        lines.clear();
    }

    public List<Line> lines() {
        return Collections.unmodifiableList(lines);
    }

    /** What is handed to the service, in the order the lines were entered - the order a refusal counts. */
    public List<ExpenseEntry> entries() {
        return lines.stream().map(Line::entry).toList();
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public int size() {
        return lines.size();
    }

    public BigDecimal total() {
        return lines.stream().map(line -> line.entry().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * What the batch takes out of each till, in the order the tills first appear. The balance warning
     * (م-١) is asked per till with these - three bills of 400 against a till holding 1,000 fit one at a
     * time and do not fit together.
     */
    public Map<Integer, BigDecimal> totalsByTreasury() {
        Map<Integer, BigDecimal> totals = new LinkedHashMap<>();
        for (Line line : lines) {
            totals.merge(line.entry().treasuryId(), line.entry().amount(), BigDecimal::add);
        }
        return totals;
    }
}
