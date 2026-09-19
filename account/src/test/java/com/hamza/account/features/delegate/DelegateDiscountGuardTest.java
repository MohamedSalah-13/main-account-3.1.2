package com.hamza.account.features.delegate;

import com.hamza.account.document.DocumentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which documents the ceiling is asked of, and what the override permission changes. */
class DelegateDiscountGuardTest {

    private static final BigDecimal GROSS = new BigDecimal("1000");
    private static final BigDecimal OVER = new BigDecimal("150");
    private static final BigDecimal WITHIN = new BigDecimal("50");

    private final Ceilings ceilings = new Ceilings();
    private int permissionAsked;

    private DelegateDiscountGuard guard(boolean mayOverride) {
        return new DelegateDiscountGuard(ceilings, () -> {
            permissionAsked++;
            return mayOverride;
        });
    }

    @Test
    void aSaleAboveItsDelegatesCeilingIsRefused() throws Exception {
        ceilings.stored = new BigDecimal("10");
        assertTrue(guard(false).refuses(DocumentType.SALES, 4, GROSS, BigDecimal.ZERO, OVER));
        assertEquals(4, ceilings.askedEmployee);
    }

    @Test
    void theSameSaleIsAllowedForSomebodyWhoMayOverride() throws Exception {
        ceilings.stored = new BigDecimal("10");
        assertFalse(guard(true).refuses(DocumentType.SALES, 4, GROSS, BigDecimal.ZERO, OVER));
    }

    /** Every shop on the day it upgrades: no ceiling, no refusal, and the session is not asked. */
    @Test
    void aDelegateWithNoCeilingIsNeverRefusedAndThePermissionIsNotRead() throws Exception {
        ceilings.stored = null;
        assertFalse(guard(false).refuses(DocumentType.SALES, 4, GROSS, BigDecimal.ZERO, GROSS));
        assertEquals(0, permissionAsked);
    }

    @Test
    void aSaleInsideTheCeilingDoesNotReadThePermissionEither() throws Exception {
        ceilings.stored = new BigDecimal("10");
        assertFalse(guard(false).refuses(DocumentType.SALES, 4, GROSS, BigDecimal.ZERO, WITHIN));
        assertEquals(0, permissionAsked);
    }

    /** A return gives nothing away, and a purchase has no delegate: the ceiling is not read. */
    @Test
    void onlyASaleIsJudged() throws Exception {
        ceilings.stored = BigDecimal.ZERO;
        for (DocumentType type : DocumentType.values()) {
            if (type != DocumentType.SALES) {
                assertFalse(guard(false).refuses(type, 4, GROSS, BigDecimal.ZERO, OVER), type.name());
            }
        }
        assertEquals(0, ceilings.reads);
    }

    @Test
    void aDocumentNamingNoDelegateIsNotJudged() throws Exception {
        ceilings.stored = BigDecimal.ZERO;
        assertFalse(guard(false).refuses(DocumentType.SALES, 0, GROSS, BigDecimal.ZERO, OVER));
        assertEquals(0, ceilings.reads);
    }

    @Test
    void theGuardBuiltForASavePathWithNoDatabaseRefusesNothing() throws Exception {
        assertFalse(DelegateDiscountGuard.none().refuses(DocumentType.SALES, 4, GROSS, GROSS, GROSS));
    }

    private static final class Ceilings implements DiscountCeilingRepository {
        private BigDecimal stored;
        private int reads;
        private int askedEmployee;

        @Override
        public Optional<DiscountCeiling> ceilingOf(int employeeId) {
            reads++;
            askedEmployee = employeeId;
            return DiscountCeiling.ofStored(stored);
        }

        @Override
        public int write(int employeeId, BigDecimal maxPercent) {
            return 1;
        }
    }
}
