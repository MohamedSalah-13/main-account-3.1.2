package com.hamza.account.features.treasury.statement;

import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.time.LocalDate;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Read-only syntax and mapping proof against the migrated MySQL views. */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class TreasuryStatementDatabaseAcceptanceTest {

    @BeforeAll
    static void connect() throws Exception {
        File configFile = new File("config.xml");
        if (!configFile.isFile()) configFile = new File("../config.xml");
        HashMap<String, String> config = new CryptoDatabaseConfig(
                CryptoDatabaseConfig.resolveConfigKey()).loadAndDecryptConfig(configFile.getAbsolutePath());
        DataSourceProvider.initialize(
                config.get(CryptoDatabaseConfig.HOST), config.get(CryptoDatabaseConfig.PORT),
                config.get(CryptoDatabaseConfig.DBNAME), config.get(CryptoDatabaseConfig.USERNAME),
                config.get(CryptoDatabaseConfig.PASSWORD));
    }

    @AfterAll
    static void disconnect() {
        DataSourceProvider.shutdown();
    }

    @Test
    void pageSummaryAndOptionsExecuteAgainstTheRealSchema() throws Exception {
        JdbcTreasuryStatementRepository repository = new JdbcTreasuryStatementRepository();
        TreasuryStatementFilter filter = new TreasuryStatementFilter(
                LocalDate.of(2000, 1, 1), LocalDate.now().plusDays(1),
                null, null, null, 0, 10);

        assertNotNull(repository.search(filter));
        assertNotNull(repository.summarize(filter));
        assertNotNull(repository.options());
    }
}
