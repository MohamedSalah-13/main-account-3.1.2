package com.hamza.account.model.base;

import com.hamza.account.model.domain.Users;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.controlsfx.button.api.ButtonColumnI;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Base of every row model, and the reason all ~20 of them still carry
 * {@code javafx.beans.property} - see rule ق-ج1 in {@code docs/new-code-rules.md}.
 * <p>
 * {@code buttonColumnName} names the button {@link ButtonColumnI} adds to a row.
 * {@code selectedRow} backs the row-selection checkbox column
 * ({@code ColumnSetting.addSelectedColumn}), which needs a real
 * {@code BooleanProperty} to bind to and is kept as a documented exception even
 * where the rest of a model's properties have been cleaned (§12.5 of
 * {@code docs/erp-roadmap.md}).
 * <p>
 * {@code users} is the "who created this row" every insert DAO reads via
 * {@code getUsers().getId()} - correct for this desktop app's one
 * process-wide signed-in user, and exactly the field ق-ج1 warns will break the
 * moment this runs behind a request-scoped session: a field initializer runs at
 * construction, not at insert, so on a server it would capture whichever
 * request happened to be active. Moving the read to each DAO's insert call is
 * the fix; not done here.
 *
 * @see ButtonColumnI#columnName()
 */

@Setter
@Getter
public abstract class DForColumnTable {

    private String buttonColumnName;
    private BooleanProperty selectedRow = new SimpleBooleanProperty();
    private Users users = CurrentUser.getOrNull();
    private LocalDateTime created_at;
    private LocalDateTime updated_at;

    public boolean isSelectedRow() {
        return selectedRow.get();
    }

    public void setSelectedRow(boolean selectedRow) {
        this.selectedRow.set(selectedRow);
    }

    public BooleanProperty selectedRowProperty() {
        return selectedRow;
    }
}
