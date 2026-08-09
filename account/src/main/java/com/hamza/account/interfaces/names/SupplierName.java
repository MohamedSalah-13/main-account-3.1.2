package com.hamza.account.interfaces.names;

import com.hamza.account.interfaces.CustomerPurchaseInterface;
import com.hamza.account.interfaces.api.NameData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Area;
import com.hamza.account.model.domain.CustomerPurchasedItem;
import com.hamza.account.model.domain.SelPriceTypeModel;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.view.CustomerPurchasedItemsApplication;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SupplierName implements NameData<Suppliers> {
    @Override
    public @NotNull Class<? super Suppliers> classForColumn() {
        return Suppliers.class;
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

    @Override
    public void actionColumnShow(Suppliers suppliers, DaoFactory daoFactory) throws Exception {
        var app = new CustomerPurchasedItemsApplication(daoFactory
                , suppliers.getId(), suppliers.getName(), new CustomerPurchaseInterface() {
            @Override
            public List<CustomerPurchasedItem> getPurchasedItemsByCustomerId(int customerId) throws DaoException {
                return daoFactory.suppliersSalesItemDao().findBySupplierId(customerId);
            }

            @Override
            public String title() {
                return "الأصناف المشتراة من المورد";
            }
        });
        app.start(new javafx.stage.Stage());
    }
}
