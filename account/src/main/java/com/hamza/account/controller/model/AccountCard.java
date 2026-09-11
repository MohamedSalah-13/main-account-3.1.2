package com.hamza.account.controller.model;

import com.hamza.account.features.party.statement.PartyMovementKind;
import lombok.Getter;
import lombok.Setter;

/**
 * One row of the statement tree, and of the printed statement.
 * <p>
 * A view row: {@code information} is a translated label for display and {@code details}
 * is the running balance — the names are what {@code account-statement-A4.jrxml} and the
 * tree's column definitions already bind to, so they stay.
 * <p>
 * <b>{@code kind} is what behaviour is decided by, never {@code information}.</b> The
 * screen used to ask whether a row could be expanded into its invoice lines by comparing
 * {@code information} against the Arabic literals {@code "المبيعات"} and
 * {@code "مرتجع المبيعات"}, and coloured its rows by the same comparison — so translating
 * either side, in an application that ships an English bundle, silently disabled both.
 * That is the {@code MovementLabel} lesson from the treasury side. The label is for
 * reading; the enum is for deciding.
 */
@Setter
@Getter
public class AccountCard {

    private int id;
    private String name;
    private String date;
    private double purchase;
    private double paid;
    private double details;
    private String notes;
    private String information;

    /** What this row is. Null on the synthetic total row and on a lazy-load placeholder. */
    private PartyMovementKind kind;

    public AccountCard() {
    }

    public AccountCard(int id, String name, String date, double purchase, double paid, double details, String notes, String information) {
        this.id = id;
        this.name = name;
        this.date = date;
        this.purchase = purchase;
        this.paid = paid;
        this.details = details;
        this.notes = notes;
        this.information = information;
    }

    /** Whether this row stands for a document whose lines can be shown beneath it. */
    public boolean hasDocumentLines() {
        return kind != null && kind.hasDocumentLines();
    }
}
