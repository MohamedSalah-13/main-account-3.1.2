package com.hamza.account.trial;

import com.hamza.account.config.MachineId;
import com.hamza.account.features.about.AboutBuild;
import com.hamza.account.features.license.LicenseClock;
import com.hamza.account.features.license.LicenseService;
import com.hamza.account.features.license.online.LicenceInstaller;
import com.hamza.account.features.license.online.LicenseRefresh;
import com.hamza.account.features.license.online.LicenseServer;
import com.hamza.account.features.license.online.OnlineActivation;
import com.hamza.account.features.notification.AppNotifications;
import com.hamza.account.features.notification.NotificationCategories;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.language.LanguageManager;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Where the licence server's requests meet this install's licence ({@code licensing-server-plan.md} §12,
 * the server's S2). The requests themselves are {@code features/license/online}, which may not know the
 * trial; what they need from it is one thing, handed in here: {@link TrialManager#install}, so a licence
 * that arrives over the network is written only after the same check a chosen file passes.
 * <p>
 * Two entries, and neither is on the start-up path or the JavaFX thread (ق-1):
 * <ul>
 *   <li>{@link #activation()} - the About window's "activate with a code", run by the window in a task.</li>
 *   <li>{@link #afterSignIn()} - two minutes after the sign-in, in the background, once a day at most: a
 *       machine with a server licence asks for its current terms (a renewal arrives by itself); a machine
 *       on the trial with {@link #TRIAL_REMINDER_DAYS} days or fewer left is reminded to activate while it
 *       still can - once the trial is over the program closes before the About window can be opened.</li>
 * </ul>
 */
@Log4j2
public final class OnlineLicensing {

    static final Duration AFTER_SIGN_IN_DELAY = Duration.ofMinutes(2);
    static final int TRIAL_REMINDER_DAYS = 3;
    static final String RENEWED_KEY = "license.online.renewed";
    static final String TRIAL_ENDING_KEY = "license.online.trial.ending";

    /** The day {@link #afterSignIn()} last ran in this process: a second sign-in the same day asks nothing. */
    private static final AtomicReference<LocalDate> lastRun = new AtomicReference<>();

    private OnlineLicensing() {
    }

    /** Activation by purchase code for this workstation, against the real licence server. */
    public static OnlineActivation activation() {
        String version = appVersion();
        return new OnlineActivation(LicenseServer.standard(version), MachineId::current, MachineId::displayName,
                version, installer());
    }

    /** Starts the after-sign-in work on a daemon thread and returns at once. */
    public static void afterSignIn() {
        LocalDate today = LocalDate.now();
        LocalDate previous = lastRun.getAndSet(today);
        if (today.equals(previous)) {
            return;
        }
        Thread thread = new Thread(OnlineLicensing::afterSignInNow, "licence-after-sign-in");
        thread.setDaemon(true);
        thread.start();
    }

    private static void afterSignInNow() {
        try {
            Thread.sleep(AFTER_SIGN_IN_DELAY.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            // Which file licenses the machine does not depend on the day - every state that skips the
            // trial skips it whatever the date - so the computer's own clock is enough to find it.
            Optional<byte[]> held = LicenseService.forThisWorkstation()
                    .licensingFile(() -> LicenseClock.of(LocalDate.now(), null, null));
            if (held.isPresent()) {
                LicenseRefresh.Outcome outcome = new LicenseRefresh(LicenseServer.standard(appVersion()),
                        MachineId::current, () -> held, installer()).run();
                if (outcome == LicenseRefresh.Outcome.RENEWED) {
                    LanguageManager language = LanguageManager.getInstance();
                    AppNotifications.success(RENEWED_KEY, NotificationCategories.SYSTEM,
                            language.getString("license.online.renewed.title"),
                            language.getString("license.online.renewed.message"));
                }
                return;
            }
            remindWhileTheTrialLasts();
        } catch (RuntimeException unexpected) {
            log.warn("The licence was not checked with the licence server after the sign-in", unexpected);
        }
    }

    private static void remindWhileTheTrialLasts() {
        TrialManager.TrialDisplayInfo info = withTrialManager(TrialManager::getDisplayInfo);
        if (!trialEnding(info)) {
            return;
        }
        LanguageManager language = LanguageManager.getInstance();
        AppNotifications.warn(TRIAL_ENDING_KEY, NotificationCategories.SYSTEM,
                language.getString("license.online.trial.ending.title"),
                language.getString("license.online.trial.ending.message", info.daysRemaining));
    }

    /** A trial still running, with {@link #TRIAL_REMINDER_DAYS} days or fewer left, and no licence at all. */
    static boolean trialEnding(TrialManager.TrialDisplayInfo info) {
        return info != null && info.error == null && !info.licenseValid && !info.trialExpired
                && info.daysRemaining != null && info.daysRemaining > 0 && info.daysRemaining <= TRIAL_REMINDER_DAYS;
    }

    /** {@link TrialManager#install} on a pooled connection it gives straight back. */
    static LicenceInstaller installer() {
        return licence -> {
            Connection connection = null;
            try {
                connection = ConnectionManager.acquire();
                return new TrialManager(connection).install(licence);
            } catch (java.sql.SQLException noConnection) {
                throw new IOException("No database connection to judge the licence against", noConnection);
            } finally {
                ConnectionManager.release(connection);
            }
        };
    }

    private static <T> T withTrialManager(java.util.function.Function<TrialManager, T> work) {
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            return work.apply(new TrialManager(connection));
        } catch (java.sql.SQLException noConnection) {
            throw new IllegalStateException(noConnection);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    private static String appVersion() {
        return AboutBuild.current().version().orElse(null);
    }
}
