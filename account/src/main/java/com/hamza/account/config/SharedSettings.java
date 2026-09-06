package com.hamza.account.config;

import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.model.domain.Users;
import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The shop's half of {@link PropertiesName}, held in {@code app_setting} instead of in the
 * Windows profile.
 *
 * <p>Static, because {@link PreferencesSetting} is, and the whole point of putting the
 * routing there is that no caller changes: {@code getSettingBarcodeStart()} is the same
 * call it always was and now answers the same on every till.
 *
 * <p>Three properties it has to have, and each of them is a defect if it does not:
 *
 * <ul>
 *   <li><b>It never blocks a screen.</b> Reads are served from a map. A stale map schedules
 *       a refresh and answers with what it has - a scale barcode must not wait on the
 *       network in the middle of a scan, and a database that has gone away must not freeze
 *       the till.</li>
 *   <li><b>It never fails a read.</b> Anything that goes wrong falls back to
 *       {@code Preferences}, which still holds the last value this machine saw, because
 *       every successful write is mirrored there.</li>
 *   <li><b>It is not installed until there is a database.</b> Until {@link #install} is
 *       called - from the bootstrap, after the pool - every key behaves exactly as it did
 *       before this class existed. The theme and the fonts are read before that point.</li>
 * </ul>
 */
@Log4j2
public final class SharedSettings {

    /** How long a value may be served before the map is refreshed behind the caller. */
    private static final long REFRESH_AFTER_MILLIS = 30_000;

    private static final Map<String, String> VALUES = new ConcurrentHashMap<>();
    private static final AtomicBoolean REFRESHING = new AtomicBoolean();

    private static volatile SharedSettingsStore store;
    private static volatile long loadedAt;
    private static volatile ScheduledExecutorService refresher;

    private SharedSettings() {
    }

    /**
     * Loads the shared values and publishes this machine's own where the shop has none.
     *
     * <p>That publishing step is the upgrade path, and it only looks arbitrary until you
     * remember what a shop upgrading to this build actually is: one computer that has been
     * running for years with the real settings in its profile, about to be joined by a
     * second. The first machine to start after the upgrade writes what it has, and the
     * writes are {@code INSERT IGNORE}, so a second machine starting later with its own
     * defaults cannot overwrite them.
     */
    public static synchronized void install(SharedSettingsStore newStore) {
        store = newStore;
        try {
            VALUES.putAll(newStore.readAll());
            loadedAt = System.currentTimeMillis();
            publishLocalValuesForUnsetKeys(newStore);
        } catch (Exception e) {
            log.warn("Shared settings could not be read; this machine falls back to its own copies", e);
        }
    }

    /** For tests, and for a logout that has to leave nothing of the previous session behind. */
    public static synchronized void uninstall() {
        store = null;
        VALUES.clear();
        loadedAt = 0;
        ScheduledExecutorService running = refresher;
        refresher = null;
        if (running != null) {
            running.shutdownNow();
        }
    }

    /**
     * @return the shop's value for a shared key, or empty when the key is not shared, the
     * store is not installed, or the shop has never set it
     */
    public static Optional<String> read(String key) {
        if (store == null || !SharedSettingKeys.isShared(key)) {
            return Optional.empty();
        }
        refreshIfStale();
        return Optional.ofNullable(VALUES.get(key));
    }

    /**
     * @return {@code true} when the value reached the database - the caller mirrors it into
     * {@code Preferences} either way, so a machine that has lost the database keeps working
     * with what it last knew
     */
    public static boolean write(String key, String value) {
        SharedSettingsStore target = store;
        if (target == null || !SharedSettingKeys.isShared(key)) {
            return false;
        }
        try {
            target.write(key, value, currentUserId());
            VALUES.put(key, value);
            return true;
        } catch (Exception e) {
            log.warn("Could not write the shared setting {}; it stays local to this machine", key, e);
            return false;
        }
    }

    private static void publishLocalValuesForUnsetKeys(SharedSettingsStore target) {
        for (String key : SharedSettingKeys.all()) {
            if (VALUES.containsKey(key)) {
                continue;
            }
            String local = PreferencesSetting.storedValue(key);
            if (local == null) {
                continue;
            }
            try {
                target.writeIfAbsent(key, local, currentUserId());
                VALUES.put(key, local);
                log.info("Published this machine's {} as the shop's value", key);
            } catch (Exception e) {
                log.warn("Could not publish the local value of {}", key, e);
            }
        }
    }

    /**
     * Reloads the whole table on a background thread when the map has aged out. The table
     * holds one row per shared key, so "the whole table" is a query that returns a couple
     * of dozen short strings - cheaper than deciding, per key, whether it is worth asking.
     */
    private static void refreshIfStale() {
        if (System.currentTimeMillis() - loadedAt < REFRESH_AFTER_MILLIS) {
            return;
        }
        if (!REFRESHING.compareAndSet(false, true)) {
            return;
        }
        // Pushed forward before the reload rather than after it, so a database that is
        // refusing connections is retried once per interval and not once per read.
        loadedAt = System.currentTimeMillis();
        executor().execute(() -> {
            try {
                SharedSettingsStore target = store;
                if (target != null) {
                    VALUES.putAll(target.readAll());
                }
            } catch (Exception e) {
                log.debug("Shared settings refresh failed; keeping the values already read", e);
            } finally {
                REFRESHING.set(false);
            }
        });
    }

    private static synchronized ScheduledExecutorService executor() {
        if (refresher == null || refresher.isShutdown()) {
            refresher = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "shared-settings-refresh");
                thread.setDaemon(true);
                return thread;
            });
        }
        return refresher;
    }

    /** Who to record as having changed it, when anybody is signed in yet. */
    private static Integer currentUserId() {
        Users user = CurrentUser.getOrNull();
        return user == null || user.getId() == 0 ? null : user.getId();
    }

    /** Visible for the settings screen: how long a change may take to reach the other tills. */
    public static long refreshSeconds() {
        return TimeUnit.MILLISECONDS.toSeconds(REFRESH_AFTER_MILLIS);
    }
}
