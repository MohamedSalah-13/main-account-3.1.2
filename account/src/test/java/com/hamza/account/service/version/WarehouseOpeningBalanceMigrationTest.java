package com.hamza.account.service.version;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertTrue;
class WarehouseOpeningBalanceMigrationTest {
    private static String read(String name) {
        try (InputStream in = WarehouseOpeningBalanceMigrationTest.class.getClassLoader().getResourceAsStream("db/migration/" + name)) {
            if (in == null) throw new IllegalStateException("Missing migration: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
    @Test
    void v18BackfillsDefaultAndMissingWarehouseRows() {
        String sql = read("V18__warehouse_opening_balances.sql");
        assertTrue(sql.contains("update items_stock ist join items i on i.id = ist.item_id"));
        assertTrue(sql.contains("insert into items_stock (item_id, stock_id, first_balance, current_quantity)"));
        assertTrue(sql.contains("cross join stocks s"));
        assertTrue(sql.contains("left join items_stock ist"));
        assertTrue(sql.contains("where ist.id is null"));
    }
    @Test
    void repeatableViewReadsPerWarehouseOpening() {
        String sql = read("R__views.sql");
        assertTrue(sql.contains("select ist.item_id, ist.stock_id, ist.first_balance"));
        assertTrue(sql.contains("sum(first_balance + quantitypurchase + quantitysalesre"));
        assertTrue(sql.contains("group by item_id"));
    }
}
