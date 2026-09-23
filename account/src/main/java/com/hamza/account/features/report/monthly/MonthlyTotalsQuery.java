package com.hamza.account.features.report.monthly;

/**
 * The one statement the monthly totals are read from: one row per day that holds a document of the side,
 * its invoices and its returns side by side, each grouped in the union rather than joined - joining a
 * day's invoices to its returns would multiply one by the other.
 *
 * <p>A row per day rather than per month, because "this year so far against the same days last year" is a
 * question about days: a month still running set against the whole of last year's is not a comparison.
 * The months are folded in Java ({@link MonthlyTotalsReport}), which is where the calendar is.</p>
 *
 * <p>It reads the header tables alone, not {@code document_profit}: the net here is the headers'
 * {@code total - discount}, the same arithmetic that view does, and that view groups every line in the
 * database before it can return one row.</p>
 */
public final class MonthlyTotalsQuery {

    private MonthlyTotalsQuery() {
    }

    /** Every day holding a document of the side, oldest first. No parameters. */
    public static String daysSql(MonthlySide side) {
        return """
                SELECT day,
                       SUM(invoices) AS invoices, SUM(gross) AS gross, SUM(discount) AS discount,
                       SUM(return_documents) AS return_documents, SUM(returns_gross) AS returns_gross,
                       SUM(returns_discount) AS returns_discount
                FROM (SELECT d.invoice_date AS day, 1 AS invoices, d.total AS gross, d.discount AS discount,
                             0 AS return_documents, 0 AS returns_gross, 0 AS returns_discount
                      FROM %1$s d
                      UNION ALL
                      SELECT r.invoice_date, 0, 0, 0, 1, r.total, r.discount
                      FROM %2$s r) documents
                GROUP BY day
                ORDER BY day""".formatted(side.documents(), side.returns());
    }
}
