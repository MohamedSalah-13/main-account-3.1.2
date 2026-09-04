package com.hamza.account.controller.setting;

import com.hamza.account.model.domain.SubGroups;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.service.SupGroupService;
import com.hamza.account.service.UnitsService;
import com.hamza.controlsfx.database.DaoException;
import javafx.scene.control.ComboBox;
import lombok.extern.log4j.Log4j2;

import static com.hamza.account.config.PropertiesName.*;

/**
 * The two "what a new item starts as" settings - its sub group and its unit - shown as
 * combo boxes on more than one screen.
 * <p>
 * Both store <b>the row's id</b>, and {@value #UNSET} means nothing has been chosen. That
 * is worth stating because the unit half used to store {@code UnitsModel.getValue()} -
 * the unit's factor, and a {@code double} - so choosing a unit wrote {@code "12.0"} and
 * the next read of it, an {@code Integer.parseInt}, threw. The throw escaped
 * {@code initialize()} (the catch here covers {@link DaoException} only), which took down
 * the barcode settings tab and the add-item screen with it, permanently, until the stored
 * value was cleared by hand.
 * <p>
 * So a stored value that is not an id is treated as unset rather than trusted, and the
 * bad value is cleared as it is found: an install that already wrote one repairs itself
 * on the next open instead of staying broken.
 */
@Log4j2
public class ComboSetting {

    /** What either setting holds until something is chosen. */
    private static final String UNSET = "false";

    public static void comboSubSetting(ComboBox<String> comboSub, SupGroupService supGroupService, boolean save, ComboBox<String> comboMain) {
        SubGroups saved = savedSubGroup(supGroupService);
        if (saved != null) {
            comboSub.getSelectionModel().select(saved.getName());
            if (comboMain != null && saved.getMainGroups() != null) {
                comboMain.getSelectionModel().select(saved.getMainGroups().getName());
            }
        } else {
            comboSub.getSelectionModel().selectFirst();
        }

        if (save) {
            comboSub.valueProperty().addListener((observableValue, oldName, name) -> {
                if (name == null) return;
                try {
                    SubGroups chosen = supGroupService.getSubGroupsByName(name);
                    if (chosen != null) setItemsSubGroup(String.valueOf(chosen.getId()));
                } catch (DaoException e) {
                    log.error(e.getMessage(), e);
                }
            });
        }
    }

    public static void comboTypeSetting(ComboBox<String> comboType, UnitsService unitsService, boolean save) {
        UnitsModel saved = savedUnit(unitsService);
        if (saved != null) {
            comboType.getSelectionModel().select(saved.getUnit_name());
        } else {
            comboType.getSelectionModel().selectFirst();
        }

        if (save) {
            comboType.valueProperty().addListener((observableValue, oldName, name) -> {
                if (name == null) return;
                try {
                    UnitsModel chosen = unitsService.getUnitsByName(name);
                    // The unit's id. Never getValue(), which is its factor.
                    if (chosen != null) setItemsTypeGroup(String.valueOf(chosen.getUnit_id()));
                } catch (DaoException e) {
                    log.error(e.getMessage(), e);
                }
            });
        }
    }

    private static SubGroups savedSubGroup(SupGroupService supGroupService) {
        Integer id = savedId(getItemsSubGroup(), ComboSetting::clearSubGroup);
        if (id == null) return null;
        try {
            return supGroupService.getSubGroupsById(id);
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    private static UnitsModel savedUnit(UnitsService unitsService) {
        Integer id = savedId(getItemsTypeGroup(), ComboSetting::clearUnit);
        if (id == null) return null;
        try {
            return unitsService.getUnitsById(id);
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    /**
     * The stored id, or {@code null} when nothing is stored - and when what is stored is
     * not an id at all, which is cleared on the way out so it is asked about once.
     */
    private static Integer savedId(String stored, Runnable clear) {
        if (stored == null || stored.isBlank() || UNSET.equals(stored)) return null;
        try {
            return Integer.valueOf(stored.trim());
        } catch (NumberFormatException e) {
            log.warn("Discarding a default-selection setting that is not an id: {}", stored);
            clear.run();
            return null;
        }
    }

    private static void clearSubGroup() {
        setItemsSubGroup(UNSET);
    }

    private static void clearUnit() {
        setItemsTypeGroup(UNSET);
    }
}
