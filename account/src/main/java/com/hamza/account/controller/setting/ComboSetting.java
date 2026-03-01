package com.hamza.account.controller.setting;

import com.hamza.account.service.SupGroupService;
import com.hamza.account.service.UnitsService;
import com.hamza.controlsfx.database.DaoException;
import javafx.scene.control.ComboBox;
import lombok.extern.log4j.Log4j2;

import static com.hamza.account.config.PropertiesName.*;

@Log4j2
public class ComboSetting {

    public static void comboSubSetting(ComboBox<String> comboSub, SupGroupService supGroupService, boolean save, ComboBox<String> comboMain) {
        String proSubGroup = getItemsSubGroup();
        if (!proSubGroup.equals("false")) {
            try {
                var subGroupsById = supGroupService.getSubGroupsById(Integer.parseInt(proSubGroup));
                comboSub.getSelectionModel().select(subGroupsById.getName());
                comboMain.getSelectionModel().select(subGroupsById.getMainGroups().getName());
            } catch (DaoException e) {
                log.error(e.getMessage(), e.getCause());
            }
        } else comboSub.getSelectionModel().selectFirst();

        if (save) {
            comboSub.valueProperty().addListener((observableValue, string, t1) -> {
                try {
                    var subGroupsById = supGroupService.getSubGroupsByName(t1);
                    setItemsSubGroup(String.valueOf(subGroupsById.getId()));

                } catch (DaoException e) {
                    log.error(e.getMessage(), e.getCause());
                }
            });
        }
    }

    public static void comboTypeSetting(ComboBox<String> comboType, UnitsService unitsService, boolean save) {
        String proTypGroup = getItemsTypeGroup();
        if (!proTypGroup.equals("false")) {
            try {
                var unitsModelById = unitsService.getUnitsById(Integer.parseInt(proTypGroup));
                comboType.getSelectionModel().select(unitsModelById.getUnit_name());
            } catch (DaoException e) {
                log.error(e.getMessage(), e.getCause());
            }
        } else comboType.getSelectionModel().selectFirst();

        if (save) {
            comboType.valueProperty().addListener((observableValue, string, t1) -> {
                try {
                    var unitsModelById = unitsService.getUnitsByName(t1);
                    setItemsTypeGroup(String.valueOf(unitsModelById.getValue()));
                } catch (DaoException e) {
                    log.error(e.getMessage(), e.getCause());
                }
            });
        }
    }
}
