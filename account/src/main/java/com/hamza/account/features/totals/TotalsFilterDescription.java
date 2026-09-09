package com.hamza.account.features.totals;

import com.hamza.account.document.TotalsSearchCriteria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * States, in one line, which documents a report was run over.
 *
 * <p>A printed summary of "المبيعات" is worth nothing without it. The same report over
 * one month, over one customer, or over the whole history looks identical on paper, so a
 * page filed away or handed to an accountant carries no way of knowing what it counted -
 * and two of them that disagree cannot be told apart.</p>
 *
 * <p>It is built here, over a plain record, rather than read off the controls: what a
 * report describes has to be the criteria the query actually ran with, not the state of a
 * screen that may have been edited since.</p>
 */
public final class TotalsFilterDescription {

    private TotalsFilterDescription() {
    }

    /**
     * @param criteria  what the search ran with
     * @param translate a key and its arguments to a sentence in the reader's language
     * @return every active condition, joined - never empty, so the report always says
     *         something about its own scope
     */
    public static String describe(TotalsSearchCriteria criteria,
                                  BiFunction<String, Object[], String> translate) {
        List<String> parts = new ArrayList<>();

        LocalDate from = criteria.dateFrom();
        LocalDate to = criteria.dateTo();
        if (from != null && to != null) {
            parts.add(translate.apply("invoice.report.filter.period", new Object[]{from, to}));
        } else if (from != null) {
            parts.add(translate.apply("invoice.report.filter.since", new Object[]{from}));
        } else if (to != null) {
            parts.add(translate.apply("invoice.report.filter.until", new Object[]{to}));
        } else {
            parts.add(translate.apply("invoice.report.filter.all.dates", new Object[0]));
        }

        add(parts, translate, "invoice.report.filter.party", criteria.partyName());
        add(parts, translate, "invoice.report.filter.delegate", criteria.delegateName());
        add(parts, translate, "invoice.report.filter.entered.by", criteria.enteredByUsername());
        if (criteria.invoiceNumber() != null) {
            parts.add(translate.apply("invoice.report.filter.invoice.number",
                    new Object[]{criteria.invoiceNumber()}));
        }
        if (criteria.invoiceType() != null) {
            parts.add(translate.apply("invoice.report.filter.payment.type",
                    new Object[]{criteria.invoiceType().getType()}));
        }
        addRange(parts, translate, criteria.minTotal(), criteria.maxTotal());
        add(parts, translate, "invoice.report.filter.text", criteria.freeText());

        return String.join(translate.apply("invoice.report.filter.separator", new Object[0]), parts);
    }

    private static void add(List<String> parts, BiFunction<String, Object[], String> translate,
                            String key, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(translate.apply(key, new Object[]{value.trim()}));
        }
    }

    /** One bound or two - a floor on its own is as much a condition as a range is. */
    private static void addRange(List<String> parts, BiFunction<String, Object[], String> translate,
                                 BigDecimal minimum, BigDecimal maximum) {
        if (minimum != null && maximum != null) {
            parts.add(translate.apply("invoice.report.filter.total.range",
                    new Object[]{minimum.toPlainString(), maximum.toPlainString()}));
        } else if (minimum != null) {
            parts.add(translate.apply("invoice.report.filter.total.min",
                    new Object[]{minimum.toPlainString()}));
        } else if (maximum != null) {
            parts.add(translate.apply("invoice.report.filter.total.max",
                    new Object[]{maximum.toPlainString()}));
        }
    }
}
