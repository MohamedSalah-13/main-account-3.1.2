package com.hamza.account.controller.setting;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.SharedSettingKeys;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tooltip;

/**
 * Says on the screen whether a setting belongs to the shop or to this computer.
 *
 * <p>Until now nothing did, and the two are indistinguishable while you are looking at
 * them. A manager turning off "sell below zero" is changing a rule for every till in the
 * building; a manager choosing a printer is changing one desk. Both are a tick box in the
 * same list, in the same tab, and the only difference is a line in
 * {@link SharedSettingKeys} that nobody at the counter can see.
 *
 * <p>That mistake runs both ways and both ways hurt. Believing a shared setting is local
 * means changing the shop's rules by accident; believing a local setting is shared means
 * setting something once and wondering for a week why the other till still does the old
 * thing.
 *
 * <p>So the shared ones are marked, and the mark carries its own explanation in a
 * tooltip - there is no legend to find, because a legend is one more thing that has to be
 * noticed. Local settings are left unmarked: they are the majority, and marking both
 * would make the screen say something about every row instead of about the few that
 * matter.
 *
 * <p>The mark is applied in code rather than in FXML on purpose: which keys are shared is
 * decided in one Java list, and an FXML file that repeated that decision would be a second
 * copy of it, free to disagree.
 */
public final class SettingScope {

    private SettingScope() {
    }

    /**
     * Marks a control whose setting is the shop's.
     *
     * <p>Refuses a key that is not actually shared, rather than marking it anyway: a wrong
     * mark here is a lie told confidently, and the screen is the only place a user can
     * learn this from.
     *
     * @param control the control the user changes - a {@code Labeled} also gets the icon,
     *                everything else gets the tooltip alone
     * @param key     the key from {@link SharedSettingKeys}, never a literal
     * @return the same control, so it can be marked inline where it is set up
     */
    public static <T extends Control> T shared(T control, String key) {
        if (!SharedSettingKeys.isShared(key)) {
            throw new IllegalArgumentException(
                    "Not a shared setting, so it must not be marked as one: " + key);
        }
        if (control == null) {
            return null;
        }

        control.getStyleClass().add("shared-setting");
        control.setTooltip(new Tooltip(LanguageManager.getInstance().getString("settings.scope.shared.tip")));

        if (control instanceof Labeled labeled && labeled.getGraphic() == null) {
            labeled.setGraphic(AppIcon.SHARED_SETTING.graphic());
        }
        return control;
    }
}
