package com.hamza.account.features.notification;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.ThemeManager;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.notifications.AppNotification;
import com.hamza.controlsfx.notifications.NotificationBell;
import com.hamza.controlsfx.notifications.NotificationCenter;
import com.hamza.controlsfx.notifications.NotificationPreferences;
import com.hamza.controlsfx.notifications.NotificationScheduler;
import com.hamza.controlsfx.notifications.NotificationSeverity;
import com.hamza.controlsfx.notifications.NotificationSource;
import com.hamza.controlsfx.notifications.NotificationToaster;
import com.hamza.controlsfx.notifications.WindowsNotifier;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Wires the notification feature together once, after login.
 * <p>
 * Everything that knows both halves - which rules exist, where the icons live,
 * which stylesheet the panel needs - lives here, so {@code MainScreenController}
 * only has to ask for a bell and the rules only have to describe themselves.
 * <p>
 * Started after login rather than in {@code DownLoadApplication}'s constructor
 * because the rules check user permissions and several of them query the
 * database: neither is meaningful before there is a user, and a query in the
 * constructor delays the login screen.
 */
@Log4j2
public final class NotificationBootstrap {

    private static NotificationBootstrap instance;

    @Getter
    private final NotificationCenter center;
    @Getter
    private final NotificationScheduler scheduler;

    private WindowsNotifier windowsNotifier;

    private NotificationBootstrap() {
        this.center = NotificationCenter.getInstance();
        this.scheduler = new NotificationScheduler(center);
    }

    /**
     * Starts the feature, or returns the running one. Idempotent because logging out
     * and back in re-enters {@code MainScreenController.initialize} in the same JVM,
     * and a second scheduler would double every notification.
     */
    public static synchronized NotificationBootstrap start() {
        if (instance == null) {
            instance = new NotificationBootstrap();
            instance.initialise();
            return instance;
        }

        // Re-entered because someone logged out and back in. The inbox belongs to the
        // session that filled it - the rules run under the signed-in user's
        // permissions, so leaving the entries would show the next cashier what the
        // manager was allowed to see.
        instance.center.clearAll();
        instance.applyPreferences();
        instance.scheduler.runAllNow();
        return instance;
    }

    public static synchronized NotificationBootstrap getIfStarted() {
        return instance;
    }

    public static synchronized void stop() {
        if (instance != null) {
            instance.scheduler.stop();
            if (instance.windowsNotifier != null) {
                instance.windowsNotifier.dispose();
            }
            instance = null;
        }
    }

    private void initialise() {
        sources().forEach(scheduler::register);
        applyPreferences();

        center.addListener(new NotificationToaster(center.policy())
                .position(Pos.BOTTOM_LEFT)
                .sound(NotificationPreferences.isSoundEnabled())
                .graphic(this::iconFor));

        windowsNotifier = new WindowsNotifier(center.policy(), this::trayImage,
                () -> Platform.runLater(() -> windowsNotifier.runPendingAction()));
        center.addListener(windowsNotifier);

        scheduler.start();
    }

    /**
     * The rules this application ships. Add one here and it is scheduled, mutable
     * from the settings screen and visible in the panel - nothing else to change.
     */
    private List<NotificationSource> sources() {
        return List.of(
                new LowStockSource(),
                new CreditLimitSource(),
                new TreasuryBalanceSource(),
                new BackupHealthSource());
    }

    /**
     * Re-reads every stored setting - the global ones, the per-category mutes and
     * channels, and each rule's interval and channel - into the running policy and
     * scheduler. Called by the settings screen after any change, which is what makes
     * a new interval take effect now instead of at the next restart.
     */
    public void applyPreferences() {
        NotificationPreferences.applyTo(center.policy(), NotificationCategories.all());
        NotificationPreferences.applyTo(center.policy(), scheduler);
    }

    /**
     * A bell wired to this centre, ready to drop into a toolbar.
     */
    public Node createBell() {
        return new NotificationBell(center)
                .panelTitle("الإشعارات")
                .scheduler(scheduler)
                .stylesheets(ThemeManager.getBaseStylesheet(), ThemeManager.getStylesheet());
    }

    /**
     * The toast icon. Severity rather than category, so a new category needs no
     * change here; the images module is not visible from {@code controlsfx}, which
     * is why this is a callback rather than something the toaster does itself.
     */
    private Node iconFor(AppNotification notification) {
        Image_Setting images = new Image_Setting();
        var stream = notification.severity().atLeast(NotificationSeverity.ERROR)
                ? images.cancel
                : images.about;
        return stream == null ? null : new ImageDesign(stream, 32);
    }

    /**
     * The AWT image for the tray icon. A separate path from the JavaFX icons above
     * because {@code SystemTray} predates JavaFX and takes {@code java.awt.Image}.
     */
    private java.awt.Image trayImage() {
        try (InputStream stream = new Image_Setting().tools) {
            return stream == null ? null : ImageIO.read(stream);
        } catch (IOException e) {
            log.error("Could not load the tray icon image", e);
            return null;
        }
    }
}
