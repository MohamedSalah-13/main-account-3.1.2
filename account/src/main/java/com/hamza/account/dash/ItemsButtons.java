package com.hamza.account.dash;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.items.InventoryController;
import com.hamza.account.controller.items.ItemGroupManagerController;
import com.hamza.account.controller.items.MergeItemsController;
import com.hamza.account.controller.items.ItemsController;
import com.hamza.account.controller.items.StockCountController;
import com.hamza.account.controller.items.StockTransferController;
import com.hamza.account.controller.items.StocksController;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.view.AddItemApplication;
import com.hamza.account.view.PriceCheckApplication;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
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
                return LanguageManager.getInstance().getString("addItem");
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
                return LanguageManager.getInstance().getString("items");
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                ItemsController itemsController = new ItemsController(daoFactory, dataPublisher);
                addItemTab(tabPane, new OpenFxmlApplication(itemsController).getPane(), textName());
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    public ButtonWithPerm itemGroupManager() {
        return new ButtonWithPerm() {
            @Override public PermissionKey getPermissionType() { return AppPermissions.ITEMS_SHOW; }
            @Override public void action() { }
            @NotNull @Override public String textName() {
                return LanguageManager.getInstance().getString("item.group.manager.title");
            }
            @Override public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                addItemTab(tabPane, new OpenFxmlApplication(new ItemGroupManagerController()).getPane(), textName());
            }
            @Override public boolean showOnTapPane() { return true; }
        };
    }

    public ButtonWithPerm masterData() {
        return new MasterDataButton();
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
                return LanguageManager.getInstance().getString("nav.inventory.title");
            }


            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                addItemTab(tabPane, new OpenFxmlApplication(inventory).getPane(), textName());
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
                return LanguageManager.getInstance().getString("item.stockcount.title");
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                addItemTab(tabPane, new OpenFxmlApplication(stockCount).getPane(), textName());
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    public ButtonWithPerm stocks() throws Exception {
        return new ButtonWithPerm() {
            final StocksController controller = new StocksController();
            @Override public PermissionKey getPermissionType() { return AppPermissions.STOCK_SHOW; }
            @Override public void action() { }
            @NotNull @Override public String textName() { return LanguageManager.getInstance().getString("stocks.title"); }
            @Override public void actionAddPaneToTabPane(TabPane tabPane) throws Exception { addItemTab(tabPane, new OpenFxmlApplication(controller).getPane(), textName()); }
            @Override public boolean showOnTapPane() { return true; }
        };
    }
    public ButtonWithPerm stockTransfers() throws Exception {
        return new ButtonWithPerm() {
            final StockTransferController controller = new StockTransferController();
            @Override public PermissionKey getPermissionType() { return AppPermissions.STOCK_TRANSFER_POST; }
            @Override public void action() { }
            @NotNull @Override public String textName() { return LanguageManager.getInstance().getString("setting.store.transfers"); }
            @Override public void actionAddPaneToTabPane(TabPane tabPane) throws Exception { addItemTab(tabPane, new OpenFxmlApplication(controller).getPane(), textName()); }
            @Override public boolean showOnTapPane() { return true; }
        };
    }
    /**
     * Merging duplicate items. Behind its own permission, and opened as a tab beside the
     * item list it repairs - the work is comparing rows against each other, which needs
     * the room a tab has and a dialog does not.
     */
    public ButtonWithPerm mergeItems() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.ITEMS_MERGE;
            }

            @Override
            public void action() {
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("item.merge.title");
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                addItemTab(tabPane, new OpenFxmlApplication(new MergeItemsController()).getPane(),
                        textName());
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    /**
     * The wall-mounted price screen. A window of its own rather than a tab: it opens full
     * screen and locked, which a tab inside the main window cannot be - and the whole point
     * is that the application behind it is out of reach.
     */
    public ButtonWithPerm priceCheck() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.ITEMS_PRICE_CHECK;
            }

            @Override
            public void action() throws Exception {
                new PriceCheckApplication().start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("pricecheck.title");
            }
        };
    }

    private void addItemTab(TabPane host, Parent content, String title) {
        for (Tab existing : host.getTabs()) {
            if (title.equals(existing.getText())) { host.getSelectionModel().select(existing); return; }
        }
        Tab tab = new Tab(title, content);
        tab.setGraphic(AppIcon.ITEM.graphic());
        host.getTabs().add(tab);
        host.getSelectionModel().select(tab);
    }
}
