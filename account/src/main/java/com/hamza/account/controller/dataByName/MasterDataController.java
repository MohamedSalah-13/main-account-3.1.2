package com.hamza.account.controller.dataByName;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.*;
import com.hamza.account.features.masterdata.*;
import com.hamza.account.features.notification.EmptyGroupsSource;
import com.hamza.account.features.notification.NotificationBootstrap;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.service.*;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static com.hamza.account.controller.dataByName.MasterDataPane.text;

@FxmlPath(pathFile = "master-data.fxml")
public final class MasterDataController {
    private static final String TAB_ID = "master-data-hub";
    private static Stage window;
    private static MasterDataController windowController;
    private final MasterDataService service;
    private final EventBus eventBus;
    private final List<MasterDataPane> panes = new ArrayList<>();
    private final Subscriptions subscriptions = new Subscriptions();
    @FXML private TabPane sections;
    @FXML private Tab groupsTab;
    @FXML private Tab areasTab;
    @FXML private Tab unitsTab;
    private MasterDataPane mains, subs, areas, units;

    public MasterDataController() {
        this(new MasterDataService(new JdbcMasterDataRepository(), ServiceRegistry.get(MainGroupService.class),
                ServiceRegistry.get(SupGroupService.class), ServiceRegistry.get(AreaService.class),
                ServiceRegistry.get(UnitsService.class)), ServiceRegistry.get(EventBus.class));
    }

    /** Constructor also allows the actual FXML to be exercised without a database. */
    public MasterDataController(MasterDataService service, EventBus eventBus) {
        this.service = service;
        this.eventBus = eventBus;
    }

    @FXML public void initialize() {
        boolean mainAllowed = AuthorizationGuard.isGranted(MasterDataKind.MAIN.show);
        boolean subAllowed = AuthorizationGuard.isGranted(MasterDataKind.SUB.show);
        groupsTab.setGraphic(AppIcon.MAIN_GROUP.graphic());
        areasTab.setGraphic(AppIcon.TREE.graphic());
        unitsTab.setGraphic(AppIcon.ITEM.graphic());
        if (mainAllowed || subAllowed) {
            mains = pane(MasterDataKind.MAIN);
            HBox groups = new HBox(16, mains);
            HBox.setHgrow(mains, Priority.ALWAYS);
            if (subAllowed) {
                subs = pane(MasterDataKind.SUB);
                HBox.setHgrow(subs, Priority.ALWAYS);
                mains.prefWidthProperty().bind(groups.widthProperty().multiply(0.44));
                subs.prefWidthProperty().bind(groups.widthProperty().multiply(0.56));
                mains.onSelected(subs::setParent);
                groups.getChildren().add(subs);
            }
            groupsTab.setContent(groups);
        } else sections.getTabs().remove(groupsTab);
        if (AuthorizationGuard.isGranted(MasterDataKind.AREA.show)) {
            areas = pane(MasterDataKind.AREA); areasTab.setContent(areas);
        } else sections.getTabs().remove(areasTab);
        if (AuthorizationGuard.isGranted(MasterDataKind.UNIT.show)) {
            units = pane(MasterDataKind.UNIT); unitsTab.setContent(units);
        } else sections.getTabs().remove(unitsTab);
        sections.getSelectionModel().selectedItemProperty().addListener((o, old, tab) -> loadSelected());
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(GroupsChanged.class, event -> refreshGroupContents(true)));
            subscriptions.add(eventBus.subscribe(ItemSaved.class, event -> refreshGroupContents(false)));
            subscriptions.add(eventBus.subscribe(ItemsChanged.class, event -> refreshGroupContents(false)));
            subscriptions.disposeWith(sections);
        }
        loadSelected();
    }

    private void refreshGroupContents(boolean groupsChanged) {
        if (groupsChanged && mains != null) mains.reload();
        if (subs != null) subs.reload();
        var notifications = NotificationBootstrap.getIfStarted();
        if (notifications != null) notifications.getScheduler().runNow(EmptyGroupsSource.ID);
    }

    private MasterDataPane pane(MasterDataKind kind) {
        MasterDataPane pane = new MasterDataPane(kind, service, () -> {
            if (eventBus != null) eventBus.publish(switch (kind) {
                case MAIN -> new GroupsChanged(GroupLevel.MAIN);
                case SUB -> new GroupsChanged(GroupLevel.SUB);
                case AREA -> new AreasChanged();
                case UNIT -> new UnitsChanged();
            });
        });
        panes.add(pane);
        return pane;
    }

    private void loadSelected() {
        Tab selected = sections.getSelectionModel().getSelectedItem();
        if (selected == groupsTab && mains != null) { mains.ensureLoaded(); if (subs != null) subs.ensureLoaded(); }
        else if (selected == areasTab && areas != null) areas.ensureLoaded();
        else if (selected == unitsTab && units != null) units.ensureLoaded();
    }

    public void select(MasterDataKind kind) {
        Tab requested = switch (kind) { case MAIN, SUB -> groupsTab; case AREA -> areasTab; case UNIT -> unitsTab; };
        if (sections.getTabs().contains(requested)) sections.getSelectionModel().select(requested);
        loadSelected();
    }

    private boolean canClose() {
        if (panes.stream().anyMatch(MasterDataPane::isWriting)) return false;
        if (panes.stream().noneMatch(MasterDataPane::hasChanges)) return true;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text("masterdata.discard"), ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(text("masterdata.title")); alert.setHeaderText(null);
        alert.initOwner(sections.getScene().getWindow());
        ThemeManager.apply(alert.getDialogPane().getScene());
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
    private void dispose() { subscriptions.unsubscribe(); panes.forEach(MasterDataPane::dispose); }

    /** All four navigation entries resolve to one tab, before constructing another controller. */
    public static void open(TabPane host, MasterDataKind kind) throws IOException {
        for (Tab tab : host.getTabs()) {
            if (TAB_ID.equals(tab.getId()) && tab.getUserData() instanceof MasterDataController controller) {
                host.getSelectionModel().select(tab); controller.select(kind); return;
            }
        }
        MasterDataController controller = new MasterDataController();
        Tab tab = new Tab(text("masterdata.title"), new OpenFxmlApplication(controller).getPane());
        tab.setId(TAB_ID); tab.setUserData(controller); tab.setGraphic(AppIcon.SETTINGS.graphic());
        tab.setOnCloseRequest(e -> { if (!controller.canClose()) e.consume(); });
        tab.setOnClosed(e -> controller.dispose());
        host.getTabs().add(tab); host.getSelectionModel().select(tab); controller.select(kind);
    }

    /** Quick-add buttons in the item form share the same three-section editor. */
    public static void showWindow(MasterDataKind kind) throws IOException {
        if (window != null && window.isShowing()) {
            windowController.select(kind); window.toFront(); return;
        }
        MasterDataController controller = new MasterDataController();
        Stage stage = new Stage();
        Scene scene = new Scene(new OpenFxmlApplication(controller).getPane(), 1120, 780);
        ThemeManager.apply(scene);
        stage.setTitle(text("masterdata.title")); stage.setScene(scene);
        stage.setMinWidth(880); stage.setMinHeight(680);
        stage.setOnCloseRequest(e -> { if (!controller.canClose()) e.consume(); });
        stage.setOnHidden(e -> { controller.dispose(); window = null; windowController = null; });
        window = stage; windowController = controller;
        controller.select(kind); stage.show();
    }
}
