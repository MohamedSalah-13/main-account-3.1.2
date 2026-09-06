package com.hamza.account.controller.setting;

import com.hamza.account.config.MachineId;
import com.hamza.account.features.workstation.Workstation;
import com.hamza.account.features.workstation.WorkstationService;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import lombok.extern.log4j.Log4j2;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

/**
 * The shop's computers, and which of them takes the backups.
 *
 * <p>It answers the two questions a multi-machine install cannot otherwise ask: <b>is one
 * of the tills running an old build</b> - the state the version gate in
 * {@code DatabaseMigrationService} refuses to start in, and which is far better noticed
 * here than at seven in the morning - and <b>where are the backups actually landing</b>.
 */
@Log4j2
@FxmlPath(pathFile = "include/settingTabWorkstations.fxml")
public class WorkstationsController implements Initializable {

    private static final DateTimeFormatter SEEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final WorkstationService service = new WorkstationService();

    @FXML
    private TableView<Workstation> table;
    @FXML
    private Label lblOwner;
    @FXML
    private Button btnRefresh, btnMakeOwner, btnForget;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        buildColumns();
        refresh();
    }

    private void buildColumns() {
        var machine = Columns.<Workstation>text("workstations.column.machine",
                row -> row.machineName() == null || row.machineName().isBlank()
                        ? row.machineId() : row.machineName());
        var user = Columns.<Workstation>text("workstations.column.user", Workstation::userName);
        var appVersion = Columns.<Workstation>text("workstations.column.app", Workstation::appVersion);
        var schema = Columns.<Workstation>text("workstations.column.schema", Workstation::databaseVersion);
        var seen = Columns.<Workstation>text("workstations.column.seen",
                row -> row.lastSeen() == null ? "" : row.lastSeen().format(SEEN));
        var status = Columns.<Workstation>text("workstations.column.status", this::statusOf);

        table.getColumns().setAll(java.util.List.of(machine, user, appVersion, schema, seen, status));
        table.setPlaceholder(new Label(LanguageManager.getInstance().getString("workstations.empty")));
    }

    /**
     * One column, three facts, because they are read together: is it this computer, is it
     * connected now, and does it own the backups.
     */
    private String statusOf(Workstation row) {
        var lm = LanguageManager.getInstance();
        StringBuilder status = new StringBuilder();
        if (MachineId.current().filter(here -> here.equals(row.machineId())).isPresent()) {
            status.append(lm.getString("workstations.status.this.machine"));
        } else if (row.connectedNow()) {
            status.append(lm.getString("workstations.status.connected"));
        }
        if (row.backupOwner()) {
            if (!status.isEmpty()) {
                status.append(" - ");
            }
            status.append(lm.getString("workstations.status.backup.owner"));
        }
        return status.toString();
    }

    @FXML
    private void refresh() {
        try {
            table.setItems(FXCollections.observableArrayList(service.list()));
            lblOwner.setText(LanguageManager.getInstance().getString("workstations.owner.current",
                    ownerName()));
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("workstations.op.load"), e);
        }
    }

    private String ownerName() {
        return table.getItems().stream()
                .filter(Workstation::backupOwner)
                .map(row -> row.machineName() == null || row.machineName().isBlank()
                        ? row.machineId() : row.machineName())
                .findFirst()
                .orElseGet(() -> LanguageManager.getInstance().getString("workstations.owner.none"));
    }

    @FXML
    private void makeOwner() {
        Workstation selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AllAlerts.alertError(LanguageManager.getInstance()
                    .getString("workstations.error.no.machine.selected"));
            return;
        }
        try {
            service.assignBackupOwner(selected.machineId());
            refresh();
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("workstations.op.owner"), e);
        }
    }

    @FXML
    private void forget() {
        Workstation selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AllAlerts.alertError(LanguageManager.getInstance()
                    .getString("workstations.error.no.machine.selected"));
            return;
        }
        try {
            service.forget(selected.machineId());
            refresh();
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("workstations.op.forget"), e);
        }
    }
}
