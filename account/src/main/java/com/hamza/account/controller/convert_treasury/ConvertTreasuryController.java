package com.hamza.account.controller.convert_treasury;

import com.hamza.account.interfaces.api.DataTable;
import com.hamza.account.model.domain.TreasuryTransferModel;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.table.ActionButtonToolBar;
import com.hamza.account.table.TableInterface;
import com.hamza.account.type.UserPermissionType;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import javafx.beans.property.BooleanProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.TableView;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Log4j2
public class ConvertTreasuryController implements TableInterface<TreasuryTransferModel> {

    private final Publisher<String> publisher = new Publisher<>();
    private TableView<TreasuryTransferModel> tableView;

    public ConvertTreasuryController() {
        publisher.addObserver(message -> tableView.setItems(FXCollections.observableArrayList(getTreasuryTransferModelList())));
    }

    @NotNull
    private List<TreasuryTransferModel> getTreasuryTransferModelList() {
        return List.of();
    }

    @Override
    public String titleName() {
        return Setting_Language.TREASURY;
    }

    @Override
    public ActionButtonToolBar<TreasuryTransferModel> actionButton() {
        return new ActionButtonToolBar<>() {
            @Override
            public void openNew() throws Exception {
                new AddForAllApplication(0, new AddConvertTreasuryController(0, publisher));
            }

            @Override
            public int delete(TreasuryTransferModel treasuryTransferModel) throws Exception {
                return 0;
            }

            @Override
            public void afterDelete() {
                publisher.notifyObservers();
            }
        };

    }

    @Override
    public DataTable<TreasuryTransferModel> table_data() {
        return new DataTable<>() {
            @Override
            public void getTable(TableView<TreasuryTransferModel> tableView) {
                DataTable.super.getTable(tableView);
                ConvertTreasuryController.this.tableView = tableView;
            }

            @Override
            public List<TreasuryTransferModel> dataList() throws Exception {
                return getTreasuryTransferModelList();
            }

            @Override
            public @NotNull Class<? super TreasuryTransferModel> classForColumn() {
                return TreasuryTransferModel.class;
            }
        };
    }

    @Override
    public BooleanProperty getColumnSelected(TreasuryTransferModel treasuryTransferModel) {
        return treasuryTransferModel.getSelectedRow();
    }

    @Override
    public Publisher<String> publisherTable() {
        return publisher;
    }

    @Override
    public UserPermissionType permAdd() {
        return UserPermissionType.TREASURY_SHOW;
    }

    @Override
    public UserPermissionType permUpdate() {
        return UserPermissionType.TREASURY_UPDATE;
    }

    @Override
    public UserPermissionType permDelete() {
        return UserPermissionType.TREASURY_DELETE;
    }

    @Override
    public List<TreasuryTransferModel> getProducts(int rowsPerPage, int offset) throws Exception {
        return List.of();
    }

    @Override
    public List<TreasuryTransferModel> getFilterItems(String newValue) {
        return null;
    }

    @Override
    public int getCountItems() {
        return 0;
    }
}
