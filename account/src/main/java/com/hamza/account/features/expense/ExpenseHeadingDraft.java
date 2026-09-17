package com.hamza.account.features.expense;

/**
 * What the headings screen sends to be saved.
 * <p>
 * No {@code systemKey}: nothing a person types may give a heading one or take one away. A key is
 * written by a migration and read by the code that depends on it, and a screen able to move it would
 * be a screen able to point every wallet fee at a different heading.
 *
 * @param id              0 for a new heading
 * @param parentId        the main heading, or {@code null} to be one
 * @param employeePayment whether employees are paid under it
 */
public record ExpenseHeadingDraft(int id, String name, Integer parentId, boolean active,
                                  boolean employeePayment) {

    public ExpenseHeadingDraft {
        name = name == null ? "" : name.strip();
        parentId = parentId == null || parentId <= 0 ? null : parentId;
    }

    public boolean isNew() {
        return id <= 0;
    }
}
