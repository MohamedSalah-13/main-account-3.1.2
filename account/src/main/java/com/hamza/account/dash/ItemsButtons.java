package com.hamza.account.dash;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.dataByName.OpenAddAreaApplication;
import com.hamza.account.controller.dataByName.impl.AreaImpl;
import com.hamza.account.controller.dataByName.impl.MainGroupImpl2;
import com.hamza.account.controller.items.InventoryController;
import com.hamza.account.controller.items.ItemsController;
import com.hamza.account.controller.items.StockCountController;
import com.hamza.account.controller.items.UnitsController;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.view.AddGroupApp;
import com.hamza.account.view.AddItemApplication;
import com.hamza.account.view.OpenApplication;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

public class ItemsButtons {

    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;

    public ItemsButtons(DaoFactory daoFactory, DataPublisher dataPublisher) {
        this.dataPublisher = dataPublisher;
        this.daoFactory = daoFactory;
    }

    public ButtonWithPerm addItem() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.ITEMS_SHOW;
            }

            @Override
            public void action() throws Exception {
                new AddItemApplication(0).start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_ADD_ITEM;
            }
        };
    }

    public ButtonWithPerm allItems() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.ITEMS_SHOW;
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
                ItemsController itemsController = new ItemsController(daoFactory, dataPublisher);
                addTape(tabPane, new OpenFxmlApplication(itemsController).getPane(), textName(), new Image_Setting().itemWhite);
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    public ButtonWithPerm units() {
        return new ButtonWithPerm() {

            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.UNITS_SHOW;
            }

            @Override
            public void action() throws Exception {
                new OpenApplication<>(new UnitsController(textName()));
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
            final InventoryController inventory = new InventoryController();

            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.INVENTORY_SHOW;
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

    /**
     * The physical count. Opened as a tab beside the inventory sheet it corrects, and
     * held behind its own permission - entering a count is clerical work, but the
     * screen is where posting one is reached from.
     */
    public ButtonWithPerm stockCount() throws Exception {
        return new ButtonWithPerm() {
            final StockCountController stockCount = new StockCountController();

            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.STOCK_COUNT_SHOW;
            }

            @Override
            public void action() {
            }

            @NotNull
            @Override
            public String textName() {
                return "الجرد الفعلي";
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                addTape(tabPane, new OpenFxmlApplication(stockCount).getPane(), textName(), new Image_Setting().itemWhite);
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    public ButtonWithPerm areasList() {

        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.ITEMS_SHOW;
            }

            @Override
            public void action() throws Exception {
                var area = new AreaImpl();
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
            public PermissionKey getPermissionType() {
                return AppPermissions.SUB_GROUP_SHOW;
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_SUB_G;
            }

            @Override
            public void action() throws Exception {
                new AddGroupApp();
            }
        };
    }

    public ButtonWithPerm addMainGroup() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.MAIN_GROUP_SHOW;
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_MAIN_G;
            }

            @Override
            public void action() throws Exception {
                new OpenAddAreaApplication<>(new MainGroupImpl2(), Setting_Language.WORD_MAIN_G);
            }
        };
    }
}
