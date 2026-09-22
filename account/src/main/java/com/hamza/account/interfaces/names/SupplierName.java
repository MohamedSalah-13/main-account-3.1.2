package com.hamza.account.interfaces.names;

import com.hamza.account.controller.name_account.PartyProfileController;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.interfaces.api.NameData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Area;
import com.hamza.account.model.domain.SelPriceTypeModel;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.view.OpenApplication;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SupplierName implements NameData<Suppliers> {
    @Override
    public @NotNull List<TableColumn<Suppliers, ?>> columns() {
        return List.of();
    }

    @Override
    public Suppliers objectT(String name, String tel, String address, String notes, double limit, double firstBalance
            , SelPriceTypeModel priceTypeModel, Area area) {
        Suppliers suppliers = new Suppliers();
        suppliers.setName(name);
        suppliers.setTel(tel);
        suppliers.setAddress(address);
        suppliers.setNotes(notes);
        suppliers.setFirst_balance(firstBalance);
        suppliers.setArea(area);
        return suppliers;
    }

    @Override
    public void addColumns(TableView<Suppliers> tableView) {
        TableColumn<Suppliers, String> tableColumnArea = addColumn(Setting_Language.AREA
                , f -> new SimpleStringProperty(f.getValue().areaProperty().get().getArea_name()));
        tableView.getColumns().add(tableColumnArea);
    }

    @Override
    public String getFrom() {
        return "suppliers";
    }

    /**
     * The row's "show": the party's profile - what it took, when, and what it stopped taking.
     * It replaced a window of raw invoice lines with no period, no units and no returns.
     */
    @Override
    public void actionColumnShow(Suppliers suppliers, DaoFactory daoFactory) throws Exception {
        new OpenApplication<>(new PartyProfileController(PartyKind.SUPPLIER, suppliers.getId(), suppliers.getName()));
    }
}
