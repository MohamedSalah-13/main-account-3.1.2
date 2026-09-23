package com.hamza.account.features.returns.reasons;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.ItemNetLines;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReturnReasonsQueryTest {

    private static long parameters(String sql) {
        return sql.chars().filter(character -> character == '?').count();
    }

    @Test
    @DisplayName("a return is worth its total less its own discount - document_profit's figure for it")
    void aReturnsValue() throws IOException {
        String views = Files.readString(Path.of("src", "main", "resources", "db", "migration", "R__views.sql"),
                StandardCharsets.UTF_8);
        assertTrue(views.contains("-(tsr.total - tsr.discount)"), "document_profit signs a return's net");
        assertEquals("r.total - r.discount", ReturnReasonsQuery.value("r"));
        assertTrue(ReturnReasonsQuery.reasonsSql(ReturnSide.SALES).contains("SUM(r.total - r.discount)"));
        assertTrue(ReturnReasonsQuery.documentsSql(ReturnSide.SALES, true).contains("r.total - r.discount AS value"));
    }

    @Test
    @DisplayName("each side reads its own two tables")
    void eachSidesTables() {
        assertTrue(ReturnReasonsQuery.reasonsSql(ReturnSide.SALES).contains("FROM total_sales_re r"));
        assertTrue(ReturnReasonsQuery.reasonsSql(ReturnSide.PURCHASES).contains("FROM total_buy_re r"));
        assertTrue(ReturnReasonsQuery.documentsNetSql(ReturnSide.SALES).contains("FROM total_sales d"));
        assertTrue(ReturnReasonsQuery.documentsNetSql(ReturnSide.PURCHASES).contains("FROM total_buy d"));
        assertTrue(ReturnReasonsQuery.documentsSql(ReturnSide.SALES, true).contains("LEFT JOIN custom p ON p.id = r.sup_id"));
        assertTrue(ReturnReasonsQuery.documentsSql(ReturnSide.PURCHASES, true)
                .contains("LEFT JOIN suppliers p ON p.id = r.sup_id"));
    }

    @Test
    @DisplayName("an item's line is read the way every item report reads it, in base units")
    void itemsAreTheItemReportsLines() {
        for (ReturnSide side : ReturnSide.values()) {
            String sql = ReturnReasonsQuery.itemsSql(side);
            assertTrue(sql.contains("SUM(" + ItemNetLines.lineAmount(side.returns(), "l") + ")"), side.name());
            assertTrue(sql.contains("SUM(l.quantity * l.type_value)"), side.name());
            assertTrue(sql.contains("JOIN " + side.returns().lineTable() + " l") || sql.contains("FROM "
                    + side.returns().lineTable() + " l"), side.name());
            assertTrue(sql.contains("ON r.id = l." + DocumentTableSpec.LINE_DOCUMENT), side.name());
        }
    }

    @Test
    @DisplayName("a return with no reason is one stored as null or as nothing")
    void withoutAReason() {
        String without = ReturnReasonsQuery.documentsSql(ReturnSide.SALES, true);
        assertTrue(without.contains("(r.return_reason IS NULL OR r.return_reason = '')"));
        assertTrue(ReturnReasonsQuery.documentsSql(ReturnSide.SALES, false).contains("AND r.return_reason = ?"));
    }

    @Test
    @DisplayName("each statement takes the parameters it is bound with, the dates inside it")
    void parametersAndBounds() {
        for (ReturnSide side : ReturnSide.values()) {
            assertEquals(2, parameters(ReturnReasonsQuery.reasonsSql(side)));
            assertEquals(3, parameters(ReturnReasonsQuery.itemsSql(side)));
            assertEquals(2, parameters(ReturnReasonsQuery.documentsSql(side, true)));
            assertEquals(3, parameters(ReturnReasonsQuery.documentsSql(side, false)));
            assertEquals(2, parameters(ReturnReasonsQuery.documentsNetSql(side)));
            assertTrue(ReturnReasonsQuery.reasonsSql(side).contains("WHERE r.invoice_date BETWEEN ? AND ?"));
        }
    }
}
