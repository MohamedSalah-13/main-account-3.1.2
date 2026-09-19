package com.hamza.account.features.delegate;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every boundary of the ceiling: a rule about money is wrong at its edges or nowhere. */
class DiscountCeilingTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void aStoredNullIsNoCeilingAndAStoredZeroIsOne() {
        assertTrue(DiscountCeiling.ofStored(null).isEmpty());
        assertTrue(DiscountCeiling.ofStored(BigDecimal.ZERO).isPresent(),
                "zero is a delegate who may discount nothing, not the absence of a ceiling");
    }

    @Test
    void exactlyTheCeilingIsInsideIt() {
        DiscountCeiling ten = new DiscountCeiling(bd("10"));
        assertFalse(ten.exceededBy(bd("1000"), BigDecimal.ZERO, bd("100.00")));
        assertTrue(ten.exceededBy(bd("1000"), BigDecimal.ZERO, bd("100.01")));
    }

    /** The reason the ceiling reads the lines: the header alone is walked round through a line. */
    @Test
    void lineDiscountsAndTheHeaderDiscountAreJudgedTogether() {
        DiscountCeiling ten = new DiscountCeiling(bd("10"));
        assertFalse(ten.exceededBy(bd("1000"), bd("60"), bd("40")));
        assertTrue(ten.exceededBy(bd("1000"), bd("60"), bd("41")));
        assertTrue(ten.exceededBy(bd("1000"), bd("101"), BigDecimal.ZERO),
                "a discount taken entirely on the lines is still a discount");
    }

    /**
     * 99,996 of 100,000 shows as 100.00% of a target and is not the target; the same here.
     * 100.04 on 1,000 is 10.004%, which a screen shows as 10.00% - and it is above 10%.
     */
    @Test
    void amountsAreComparedNotTheRoundedPercentage() {
        assertTrue(new DiscountCeiling(bd("10")).exceededBy(bd("1000"), BigDecimal.ZERO, bd("100.04")));
    }

    @Test
    void theAllowedAmountIsRoundedAsMoneyHalfUp() {
        // 7.5% of 66.60 is 4.995
        DiscountCeiling ceiling = new DiscountCeiling(bd("7.5"));
        assertEquals(bd("5.00"), ceiling.allowedOn(bd("66.60")));
        assertFalse(ceiling.exceededBy(bd("66.60"), BigDecimal.ZERO, bd("5.00")));
        assertTrue(ceiling.exceededBy(bd("66.60"), BigDecimal.ZERO, bd("5.01")));
    }

    @Test
    void aCeilingOfZeroAllowsNoDiscountAndADocumentWithNoneIsFine() {
        DiscountCeiling none = new DiscountCeiling(BigDecimal.ZERO);
        assertFalse(none.exceededBy(bd("500"), BigDecimal.ZERO, BigDecimal.ZERO));
        assertFalse(none.exceededBy(bd("500"), null, null));
        assertTrue(none.exceededBy(bd("500"), BigDecimal.ZERO, bd("0.01")));
    }

    @Test
    void aCeilingOfAHundredAllowsTheWholeDocument() {
        assertFalse(new DiscountCeiling(bd("100")).exceededBy(bd("500"), bd("200"), bd("300")));
    }

    @Test
    void aGrossOfNothingAllowsNothing() {
        DiscountCeiling ten = new DiscountCeiling(bd("10"));
        assertEquals(0, ten.allowedOn(BigDecimal.ZERO).signum());
        assertEquals(0, ten.allowedOn(null).signum());
        assertEquals(0, ten.allowedOn(bd("-50")).signum());
    }

    @Test
    void aCeilingOutsideZeroToAHundredIsNotACeiling() {
        assertThrows(IllegalArgumentException.class, () -> new DiscountCeiling(bd("-0.01")));
        assertThrows(IllegalArgumentException.class, () -> new DiscountCeiling(bd("100.01")));
        assertThrows(IllegalArgumentException.class, () -> new DiscountCeiling(null));
    }
}
