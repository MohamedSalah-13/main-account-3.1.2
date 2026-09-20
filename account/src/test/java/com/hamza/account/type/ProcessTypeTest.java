package com.hamza.account.type;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every kind of movement the item card can show has a name in all three bundles.
 * <p>
 * {@code MessageKeyArchitectureTest} cannot see these: {@link ProcessType} resolves its label
 * through a field rather than a literal at the call site, so the general scan has nothing to
 * match. {@code LanguageManager.getString} answers a missing key with the key itself and a log
 * warning, which on this screen means a filter combo and a summary card reading
 * {@code item.card.kind.transfer.in} with nothing in the build objecting - the same way ten keys
 * once shipped untranslated in the master-data work. The same check {@code PartyStatementTest}
 * makes for {@code PartyMovementKind}, for the same reason.
 */
class ProcessTypeTest {

    private static final Path BUNDLE_DIR = Path.of("..", "controlsfx", "src", "main", "resources", "i18n");
    private static final String[] BUNDLES = {
            "messages.properties", "messages_ar.properties", "messages_en.properties"};

    @Test
    void everyKindHasANameInEveryBundle() throws IOException {
        List<String> missing = new ArrayList<>();
        for (String bundle : BUNDLES) {
            Properties properties = load(bundle);
            for (ProcessType kind : ProcessType.values()) {
                String value = properties.getProperty(keyOf(kind), "").trim();
                if (value.isEmpty()) {
                    missing.add(bundle + ": " + keyOf(kind));
                }
            }
        }
        assertTrue(missing.isEmpty(), "a movement kind with no name reaches the screen as its own key: " + missing);
    }

    /**
     * The seven the card knows. A kind added here without a row in {@code card_item_view} shows an
     * option that finds nothing; a {@code table_name} added there without a kind here draws rows
     * that belong to no total. This is only half that pair - {@code CardItemDaoStatementsTest}
     * holds the other half - but it fails when somebody adds a constant and stops there.
     */
    @Test
    void namesTheSevenMovementsTheCardCounts() {
        assertEquals(7, ProcessType.values().length);
        assertTrue(List.of(ProcessType.values()).containsAll(List.of(
                ProcessType.PURCHASE, ProcessType.SALES,
                ProcessType.PURCHASE_RETURN, ProcessType.SALES_RETURN,
                ProcessType.TRANSFER_IN, ProcessType.TRANSFER_OUT, ProcessType.STOCK_COUNT)));
    }

    /** Mirrors the constructor argument, which the enum keeps to itself. */
    private static String keyOf(ProcessType kind) {
        return switch (kind) {
            case PURCHASE -> "pur";
            case PURCHASE_RETURN -> "RePur";
            case SALES -> "sales";
            case SALES_RETURN -> "ReSal";
            case TRANSFER_IN -> "item.card.kind.transfer.in";
            case TRANSFER_OUT -> "item.card.kind.transfer.out";
            case STOCK_COUNT -> "item.card.kind.stock.count";
        };
    }

    private static Properties load(String bundle) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(BUNDLE_DIR.resolve(bundle), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
