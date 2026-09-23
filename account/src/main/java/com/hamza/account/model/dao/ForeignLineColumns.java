package com.hamza.account.model.dao;

/**
 * The two columns a document line carries in the document's currency, beside its base price and
 * discount (V83, docs/currency-plan.md §15 ق-د٣). Named once for the four line DAOs' mappers, which
 * read them off the line views - the columns themselves are declared in {@code DocumentTableSpec}.
 */
final class ForeignLineColumns {

    static final String PRICE = "price_foreign";
    static final String DISCOUNT = "discount_foreign";

    private ForeignLineColumns() {
    }
}
