package com.hamza.account.features.itemreports;

import com.hamza.controlsfx.database.DaoException;

/**
 * One question that can be asked of the item catalogue.
 * <p>
 * A report is a class implementing this and a line in {@link ItemReportCatalog}. It gets
 * no screen of its own, declares no FXML, and is never named by the controller - which is
 * the whole design: the reports screen lists whatever the catalogue holds and draws
 * whatever {@link ItemReportResult} comes back, so adding the fifth report is one file.
 * <p>
 * <b>A report runs off the JavaFX thread, always.</b> {@link #run} may take seconds over a
 * large catalogue, and the screen calls it from a background task. Nothing here may touch
 * a control, and nothing here may cache a connection - the repository borrows one per call
 * the way the rest of the application does.
 */
public interface ItemReport {

    /**
     * A stable identifier, used as the preferences key for this report's saved column
     * widths and as what the screen remembers between openings. It never reaches a user,
     * so it is not translated - and it must not change once shipped, or every operator's
     * remembered choice quietly resets.
     */
    String id();

    /** The i18n key for the name in the report list. */
    String titleKey();

    /** The i18n key for the sentence under the name that says what the report answers. */
    String descriptionKey();

    /**
     * Which of the request's inputs this report actually reads, so the screen can show
     * only the controls that change the answer. A date box on a report that ignores dates
     * is worse than no date box: the operator sets it and believes the result.
     */
    default boolean usesDateRange() {
        return false;
    }

    /**
     * What the date box is called on the report that uses one.
     * <p>
     * "From" and "until" are opposite questions and the answer changes completely between
     * them: the unused report asks what has not moved <em>since</em> a date, the expiry
     * report asks what runs out <em>by</em> one. A box labelled with the wrong preposition
     * is worse than no box, because the operator sets it and believes the result.
     */
    default String dateLabelKey() {
        return "itemreport.date.from";
    }

    ItemReportResult run(ItemReportRequest request) throws DaoException;
}
