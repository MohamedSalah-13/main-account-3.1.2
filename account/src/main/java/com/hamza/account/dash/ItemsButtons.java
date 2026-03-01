package com.hamza.account.dash;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.convert_stock.ConvertStockDataController;
import com.hamza.account.controller.dataByName.OpenAddAreaApplication;
import com.hamza.account.controller.dataByName.impl.AreaImpl;
import com.hamza.account.controller.dataByName.impl.MainGroupImpl2;
import com.hamza.account.controller.items.InventoryController;
import com.hamza.account.controller.items.ItemsController;
import com.hamza.account.controller.items.UnitsController;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.main.MainItems;
import com.hamza.account.controller.others.ImportDataFromExcelFileController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsMiniQuantity;
import com.hamza.account.notification.NotifyItemAlert;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.table.TableOpen;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.AddGroupApp;
import com.hamza.account.view.AddItemApplication;
import com.hamza.account.view.OpenApplication;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ItemsButtons extends LoadData {

    private final DaoFactory daoFactory;

    public ItemsButtons(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
        this.daoFactory = daoFactory;
    }

    public ButtonWithPerm addItem() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.ITEMS_SHOW;
            }

            @Override
            public void action() throws Exception {
                new AddItemApplication(0, dataPublisher, daoFactory).start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_ADD_ITEM;
            }
        };
    }

    public ButtonWithPerm allItems(MainItems mainItems) {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.ITEMS_SHOW;
            }

            @Override
            public void action() {
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_ITEMS;
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                ItemsController itemsController = new ItemsController(daoFactory, dataPublisher, mainItems);
                addTape(tabPane, new OpenFxmlApplication(itemsController).getPane(), textName(), new Image_Setting().itemWhite);
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    public ButtonWithPerm addItemsFromExcel() {
        return new ButtonWithPerm() {

            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.DISABLE_BUTTON;
            }

            @Override
            public void action() throws Exception {
                new OpenApplication<>(new ImportDataFromExcelFileController(itemsService, dataPublisher.getPublisherAddItem()));
            }

            @NotNull
            @Override
            public String textName() {
                return "إضافة من Excel";
            }

        };
    }

    public ButtonWithPerm units() {
        return new ButtonWithPerm() {

            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.UNITS_SHOW;
            }

            @Override
            public void action() throws Exception {
                new OpenApplication<>(new UnitsController(unitsService, dataPublisher, textName()));
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.UNITS;
            }
        };
    }

    public ButtonWithPerm inventory() throws Exception {
        return new ButtonWithPerm() {
            final InventoryController inventory = new InventoryController(daoFactory, dataPublisher);

            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.INVENTORY_SHOW;
            }

            @Override
            public void action() {
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.GARD;
            }


            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                addTape(tabPane, new OpenFxmlApplication(inventory).getPane(), textName(), new Image_Setting().itemWhite);
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    public ButtonWithPerm miniQuantityItems(List<ItemsMiniQuantity> itemsMiniQuantityList) {
        var notifyItemAlert = new NotifyItemAlert(itemsMiniQuantityList);
        return new ButtonWithPerm() {

            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.ITEMS_SHOW;
            }

            @Override
            public void action() throws Exception {
                notifyItemAlert.action();
            }

            @NotNull
            @Override
            public String textName() {
                return notifyItemAlert.runningOutItemsMessage;
            }

        };
    }

    public ButtonWithPerm convertStock() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.DISABLE_BUTTON;
            }

            @Override
            public void action() throws Exception {
                ConvertStockDataController itemsController = new ConvertStockDataController(daoFactory);
                new TableOpen<>(itemsController).start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.STORE_TRANSFERS;
            }

        };
    }

    public ButtonWithPerm areasList() {

        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.ITEMS_SHOW;
            }

            @Override
            public void action() throws Exception {
                var area = new AreaImpl(areaService, dataPublisher);
                new OpenAddAreaApplication<>(area, textName());
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.AREA;
            }
        };
    }

    public ButtonWithPerm addSubGroup() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.SUB_GROUP_SHOW;
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_SUB_G;
            }

            @Override
            public void action() throws Exception {
                new AddGroupApp(dataPublisher.getPublisherAddSubGroup(), daoFactory);
            }
        };
    }

    public ButtonWithPerm addMainGroup() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.MAIN_GROUP_SHOW;
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_MAIN_G;
            }

            @Override
            public void action() throws Exception {
                new OpenAddAreaApplication<>(new MainGroupImpl2(mainGroupService, dataPublisher), Setting_Language.WORD_MAIN_G);
            }
        };
    }
}
