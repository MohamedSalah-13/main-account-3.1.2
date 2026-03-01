package com.hamza.account.model.base;

import com.hamza.account.model.domain.Users;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.button.api.ButtonColumnI;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * @apiNote This Class used To add button to column
 * in tableView a {@link ButtonColumnI}
 * @see ButtonColumnI#columnName()
 */

@Setter
@Getter
public abstract class DForColumnTable {

    private String buttonColumnName;
    private BooleanProperty selectedRow = new SimpleBooleanProperty();
    private Users users = LogApplication.usersVo;
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
