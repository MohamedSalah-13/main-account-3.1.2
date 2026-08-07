package com.hamza.account.controller.setting;

import com.hamza.account.features.notification.NotificationBootstrap;
import com.hamza.account.features.notification.NotificationCategories;
import com.hamza.account.features.notification.StockLevelAlert;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.controlsfx.notifications.NotificationChannel;
import com.hamza.controlsfx.notifications.NotificationPreferences;
import com.hamza.controlsfx.notifications.NotificationScheduler;
import com.hamza.controlsfx.notifications.NotificationSeverity;
import com.hamza.controlsfx.notifications.NotificationSource;
import com.hamza.controlsfx.notifications.WindowsNotifier;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;

import java.net.URL;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * The notifications tab of the settings screen.
 * <p>
 * Writes to {@link NotificationPreferences} and then pushes the whole set into
 * the live policy, so a change takes effect on the next poll instead of the next
 * restart.
 * <p>
 * The category list and the rule list are built from what is actually registered
 * rather than hard-coded here: a new rule appears in this screen without it being
 * edited.
 */
@Log4j2
@FxmlPath(pathFile = "include/settingTabNotifications.fxml")
public class SettingTabNotificationsController implements Initializable {

    private static final int MIN_COOLDOWN_MINUTES = 5;
    private static final int MAX_COOLDOWN_MINUTES = 24 * 60;

    /** One minute is the scheduler's floor; a week is well past any useful check. */
    private static final int MIN_RULE_MINUTES = 1;
    private static final int MAX_RULE_MINUTES = 7 * 24 * 60;

    /** Below this the two cards are narrower than their own labels. */
    private static final double TWO_COLUMN_MIN_WIDTH = 780;

    private boolean isTwoColumns = true;

    @FXML
    private CheckBox checkEnabled;
    @FXML
    private CheckBox checkSound;
    @FXML
    private ComboBox<NotificationSeverity> comboThreshold;
    @FXML
    private ComboBox<NotificationChannel> comboDefaultChannel;
    @FXML
    private Label labelWindowsSupport;
    @FXML
    private Spinner<Integer> spinnerCooldown;
    @FXML
    private CheckBox checkStockOnSale;
    @FXML
    private VBox boxCategories;
    @FXML
    private GridPane gridSources;
    @FXML
    private GridPane layout;
    @FXML
    private VBox cardCategories;
    @FXML
    private VBox cardRules;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        generalSettings();
        categorySettings();
        registeredRules();
        responsiveLayout();
    }

    /**
     * Drops the two columns down to one when the window is too narrow for them.
     * <p>
     * The two-column layout is what stops this screen running off the bottom, but
     * below roughly 780px the cards squeeze until the labels wrap into unreadable
     * columns. Collapsing is the lesser problem: a narrow window can scroll.
     */
    private void responsiveLayout() {
        layout.widthProperty().addListener((observable, oldValue, newValue) ->
                applyColumns(newValue.doubleValue() >= TWO_COLUMN_MIN_WIDTH));
    }

    private void applyColumns(boolean twoColumns) {
        if (twoColumns == isTwoColumns) {
            return;
        }
        isTwoColumns = twoColumns;

        GridPane.setColumnIndex(cardCategories, twoColumns ? 1 : 0);
        GridPane.setRowIndex(cardCategories, twoColumns ? 0 : 1);
        GridPane.setRowIndex(cardRules, twoColumns ? 1 : 2);
        GridPane.setColumnSpan(cardRules, twoColumns ? 2 : 1);
    }

    private void generalSettings() {
        checkEnabled.setSelected(NotificationPreferences.isEnabled());
        checkEnabled.selectedProperty().addListener((observable, oldValue, newValue) -> {
            NotificationPreferences.setEnabled(newValue);
            apply();
        });

        checkSound.setSelected(NotificationPreferences.isSoundEnabled());
        // The toaster reads the sound flag when it is built, so this one really does
        // take effect on the next run rather than immediately.
        checkSound.selectedProperty().addListener((observable, oldValue, newValue) ->
                NotificationPreferences.setSoundEnabled(newValue));

        comboThreshold.getItems().setAll(NotificationSeverity.values());
        comboThreshold.setConverter(new StringConverter<>() {
            @Override
            public String toString(NotificationSeverity severity) {
                return severity == null ? "" : arabicNameOf(severity);
            }

            @Override
            public NotificationSeverity fromString(String string) {
                return null;
            }
        });
        comboThreshold.setValue(NotificationPreferences.getToastThreshold());
        comboThreshold.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                NotificationPreferences.setToastThreshold(newValue);
                apply();
            }
        });

        int cooldown = (int) NotificationPreferences.getCooldown().toMinutes();
        spinnerCooldown.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                MIN_COOLDOWN_MINUTES, MAX_COOLDOWN_MINUTES,
                Math.clamp(cooldown, MIN_COOLDOWN_MINUTES, MAX_COOLDOWN_MINUTES), MIN_COOLDOWN_MINUTES));
        spinnerCooldown.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                NotificationPreferences.setCooldown(Duration.ofMinutes(newValue));
                apply();
            }
        });

        comboDefaultChannel.getItems().setAll(NotificationChannel.values());
        comboDefaultChannel.setConverter(channelConverter("داخل البرنامج"));
        comboDefaultChannel.setValue(NotificationPreferences.getDefaultChannel());
        comboDefaultChannel.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                NotificationPreferences.setDefaultChannel(newValue);
                apply();
            }
        });

        // Said once, up front. A user who picks "Windows" on a machine whose tray is
        // unavailable would otherwise get silence and no explanation.
        boolean windowsAvailable = WindowsNotifier.isAvailable();
        labelWindowsSupport.setText(windowsAvailable
                ? "إشعارات ويندوز متاحة على هذا الجهاز"
                : "إشعارات ويندوز غير متاحة على هذا الجهاز - سيتم العرض داخل البرنامج");
        labelWindowsSupport.setVisible(!windowsAvailable);
        labelWindowsSupport.setManaged(!windowsAvailable);
    }

    private void categorySettings() {
        for (Map.Entry<String, String> category : NotificationCategories.displayNames().entrySet()) {
            String key = category.getKey();
            CheckBox checkBox = new CheckBox(category.getValue());
            checkBox.getStyleClass().add("modern-check-box");
            // Stored as "muted", shown as "enabled" - a settings screen full of
            // negatives is where users misread what they are switching off.
            checkBox.setSelected(!NotificationPreferences.isMuted(key));
            checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                NotificationPreferences.setMuted(key, !newValue);
                apply();
            });
            boxCategories.getChildren().add(checkBox);
        }

        // An event rather than a scheduled rule, so it has no row in the grid below
        // and needs its own switch. It follows the items category for muting and for
        // which channel it appears on.
        checkStockOnSale.setSelected(StockLevelAlert.isEnabled());
        checkStockOnSale.selectedProperty().addListener(
                (observable, oldValue, newValue) -> StockLevelAlert.setEnabled(newValue));
    }

    /**
     * One editable row per registered rule: how often it runs and where its
     * notification appears.
     * <p>
     * Built from what the scheduler is actually running rather than from a
     * hard-coded list, so a new rule shows up here on its own. The state column says
     * when a rule is switched off elsewhere - by its own setting or by the user's
     * permissions - because a screen that lets you set an interval on a rule that
     * never fires is worse than not listing it.
     */
    private void registeredRules() {
        NotificationBootstrap bootstrap = NotificationBootstrap.getIfStarted();
        if (bootstrap == null) {
            gridSources.add(new Label("لم يتم تشغيل نظام الإشعارات بعد"), 0, 0);
            return;
        }

        NotificationScheduler scheduler = bootstrap.getScheduler();
        List<NotificationSource> sources = scheduler.sources().stream()
                .sorted(Comparator.comparing(NotificationSource::displayName))
                .toList();

        if (sources.isEmpty()) {
            gridSources.add(new Label("لا توجد قواعد مسجلة"), 0, 0);
            return;
        }

        gridSources.addRow(0,
                header("القاعدة"), header("كل (دقيقة)"), header("طريقة العرض"), header("الحالة"));

        int row = 1;
        for (NotificationSource source : sources) {
            gridSources.addRow(row++,
                    new Label(source.displayName()),
                    intervalSpinner(scheduler, source),
                    channelCombo(source),
                    stateLabel(source));
        }
    }

    private Label header(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private Label stateLabel(NotificationSource source) {
        Label label = new Label(source.enabled() ? "مفعلة" : "غير مفعلة");
        label.getStyleClass().add("settings-subtitle");
        return label;
    }

    /**
     * Minutes, not a free-text duration: the scheduler refuses anything under a
     * minute anyway, and the spinner's own bounds stop the user setting a value that
     * would silently be clamped.
     */
    private Spinner<Integer> intervalSpinner(NotificationScheduler scheduler, NotificationSource source) {
        int current = (int) scheduler.effectiveInterval(source.id()).toMinutes();
        Spinner<Integer> spinner = new Spinner<>(MIN_RULE_MINUTES, MAX_RULE_MINUTES,
                Math.clamp(current, MIN_RULE_MINUTES, MAX_RULE_MINUTES), 5);
        spinner.setPrefWidth(110);
        spinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                NotificationPreferences.setInterval(source.id(), Duration.ofMinutes(newValue));
                apply();
            }
        });
        return spinner;
    }

    /**
     * A rule may pin its own channel or follow the global setting. "Follow" is an
     * explicit item rather than a blank, because without it there is no way back to
     * the default once a rule has been pinned.
     */
    private ComboBox<NotificationChannel> channelCombo(NotificationSource source) {
        ComboBox<NotificationChannel> combo = new ComboBox<>();
        combo.getItems().add(null);
        combo.getItems().addAll(NotificationChannel.values());
        combo.setConverter(channelConverter("حسب الإعداد العام"));
        combo.setValue(NotificationPreferences.getChannel(source.id()));
        combo.setPrefWidth(170);
        combo.valueProperty().addListener((observable, oldValue, newValue) -> {
            NotificationPreferences.setChannel(source.id(), newValue);
            apply();
        });
        return combo;
    }

    private StringConverter<NotificationChannel> channelConverter(String nullLabel) {
        return new StringConverter<>() {
            @Override
            public String toString(NotificationChannel channel) {
                return channel == null ? nullLabel : channel.displayName();
            }

            @Override
            public NotificationChannel fromString(String string) {
                return null;
            }
        };
    }

    private String arabicNameOf(NotificationSeverity severity) {
        return switch (severity) {
            case INFO -> "معلومة";
            case SUCCESS -> "نجاح";
            case WARNING -> "تحذير";
            case ERROR -> "خطأ";
            case CRITICAL -> "حرج";
        };
    }

    private void apply() {
        NotificationBootstrap bootstrap = NotificationBootstrap.getIfStarted();
        if (bootstrap != null) {
            bootstrap.applyPreferences();
        }
    }
}
