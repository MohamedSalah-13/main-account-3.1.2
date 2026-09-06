package com.hamza.account.config;

import com.hamza.controlsfx.database.DaoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a till reads when the shop has an answer, and what it reads when it cannot ask.
 * <p>
 * The store is faked rather than mocked: {@link SharedSettingsStore} is three methods over
 * one table, and a fake that keeps a map is both shorter than the stubbing and able to
 * show the write arriving.
 */
class SharedSettingsTest {

    /** A key nothing else writes, so the developer's own Preferences cannot colour a run. */
    private static final String KEY = SharedSettingKeys.BACKUP_OWNER_MACHINE;

    private static class FakeStore extends SharedSettingsStore {
        final Map<String, String> rows = new LinkedHashMap<>();
        boolean broken;
        int reads;

        @Override
        public Map<String, String> readAll() throws DaoException {
            reads++;
            if (broken) {
                throw new DaoException("no database");
            }
            return new LinkedHashMap<>(rows);
        }

        @Override
        public void write(String key, String value, Integer userId) throws DaoException {
            if (broken) {
                throw new DaoException("no database");
            }
            rows.put(key, value);
        }

        @Override
        public void writeIfAbsent(String key, String value, Integer userId) throws DaoException {
            if (broken) {
                throw new DaoException("no database");
            }
            rows.putIfAbsent(key, value);
        }
    }

    @AfterEach
    void tearDown() {
        SharedSettings.uninstall();
    }

    @Test
    @DisplayName("with no store installed, nothing is shared - the theme is read before the pool exists")
    void notInstalledReadsNothing() {
        assertTrue(SharedSettings.read(KEY).isEmpty());
        assertFalse(SharedSettings.write(KEY, "anything"));
    }

    @Test
    @DisplayName("a value the shop has set is what every machine reads")
    void readsTheShopsValue() {
        FakeStore store = new FakeStore();
        store.rows.put(KEY, "machine-a");
        SharedSettings.install(store);

        assertEquals("machine-a", SharedSettings.read(KEY).orElse(null));
    }

    @Test
    @DisplayName("a write reaches the database and is answered from then on")
    void writesThrough() {
        FakeStore store = new FakeStore();
        SharedSettings.install(store);

        assertTrue(SharedSettings.write(KEY, "machine-b"));
        assertEquals("machine-b", store.rows.get(KEY));
        assertEquals("machine-b", SharedSettings.read(KEY).orElse(null));
    }

    @Test
    @DisplayName("a key that is not shared is left to the machine it is on")
    void unsharedKeysAreUntouched() {
        FakeStore store = new FakeStore();
        SharedSettings.install(store);

        assertTrue(SharedSettings.read("price.check.stock").isEmpty());
        assertFalse(SharedSettings.write("price.check.stock", "3"));
        assertFalse(store.rows.containsKey("price.check.stock"));
    }

    /**
     * The failure that matters: a till whose network has gone. It must keep selling, with
     * the last values it saw, and a write that cannot land must say so rather than
     * pretending.
     */
    @Test
    @DisplayName("a database that cannot be reached is not an error the user meets")
    void survivesAnUnreachableDatabase() {
        FakeStore store = new FakeStore();
        store.broken = true;
        SharedSettings.install(store);

        assertTrue(SharedSettings.read(KEY).isEmpty());
        assertFalse(SharedSettings.write(KEY, "machine-c"));
    }

    @Test
    @DisplayName("reads are served from memory, not from a query per scan")
    void readsDoNotQueryPerCall() {
        FakeStore store = new FakeStore();
        store.rows.put(KEY, "machine-a");
        SharedSettings.install(store);
        int afterInstall = store.reads;

        for (int i = 0; i < 500; i++) {
            SharedSettings.read(KEY);
        }

        assertEquals(afterInstall, store.reads,
                "a barcode scan must not wait on the network; the refresh is time-based and asynchronous");
    }
}
