package com.hamza.account.features.party.rfm;

/**
 * Which figure the table is listed by. Sorting a column on screen sorts only the page in front of
 * it, so the order that decides which customers reach the first page is chosen here and applied in
 * SQL. Every order ends on the customer's id, so two equal figures always come in the same order and
 * a page boundary cannot show one customer twice or drop another.
 */
public enum CustomerRfmOrder {

    /** The three scores added up, best first; then the value. */
    SCORE("party.rfm.order.score", "r_score + f_score + m_score DESC, net DESC, party_id"),
    /** The most recent buyer first. */
    RECENCY("party.rfm.order.recency", "recency_days ASC, party_id"),
    /** The most invoices in the period first. */
    FREQUENCY("party.rfm.order.frequency", "documents DESC, net DESC, party_id"),
    /** The most bought in the period, net of returns, first. */
    VALUE("party.rfm.order.value", "net DESC, party_id");

    private final String messageKey;
    private final String orderBy;

    CustomerRfmOrder(String messageKey, String orderBy) {
        this.messageKey = messageKey;
        this.orderBy = orderBy;
    }

    public String messageKey() {
        return messageKey;
    }

    /** The {@code ORDER BY} list - this enum's own text, never anything a user typed. */
    String orderBy() {
        return orderBy;
    }
}
