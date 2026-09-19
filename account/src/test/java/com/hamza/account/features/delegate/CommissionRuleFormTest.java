package com.hamza.account.features.delegate;

import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommissionRuleFormTest {

    private static BigDecimal n(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static List<CommissionTiers.Tier> tiers(String... boxes) throws UserValidationException {
        BigDecimal[] values = new BigDecimal[boxes.length];
        for (int i = 0; i < boxes.length; i++) {
            values[i] = n(boxes[i]);
        }
        return CommissionRuleForm.tiers(values);
    }

    /**
     * The screen opens like this: the first tier seeded with zeros, the other two untouched.
     * It has to read as one tier - a formatter that seeded all six boxes would make three tiers
     * at zero, refused for having no order, on a form nobody typed into.
     */
    @Test
    void untouchedOptionalTiersDoNotExist() throws Exception {
        List<CommissionTiers.Tier> read = tiers("0", "2", null, null, null, null);
        assertEquals(1, read.size());
        assertEquals(new BigDecimal("2"), read.get(0).ratePercent());
    }

    /** Zero is a value somebody can mean: a tier from 0% is "from the first pound". */
    @Test
    void aTypedZeroIsATierAndAnEmptyBoxIsNot() throws Exception {
        assertEquals(2, tiers("0", "0", "50", "1", null, null).size());
    }

    @Test
    void allThreeAreReadInOrder() throws Exception {
        List<CommissionTiers.Tier> read = tiers("50", "1", "80", "2", "100", "3");
        assertEquals(3, read.size());
        assertEquals(new BigDecimal("100"), read.get(2).fromPercent());
    }

    @Test
    void aHalfFilledTierIsRefusedRatherThanGuessed() {
        assertEquals("commission.error.tier.incomplete", assertThrows(UserValidationException.class,
                () -> tiers("0", "2", "80", null, null, null)).getMessage());
        assertEquals("commission.error.tier.incomplete", assertThrows(UserValidationException.class,
                () -> tiers("0", "2", null, "3", null, null)).getMessage());
    }

    /** A third tier with no second would be stored as the second, which is not what the screen showed. */
    @Test
    void aTierAfterAnEmptyOneIsRefused() {
        assertEquals("commission.error.tier.gap", assertThrows(UserValidationException.class,
                () -> tiers("0", "2", null, null, "100", "3")).getMessage());
    }

    @Test
    void anEmptyFormIsRefused() {
        assertEquals("commission.error.tier.count", assertThrows(UserValidationException.class,
                () -> tiers(null, null, null, null, null, null)).getMessage());
    }

    @Test
    void aValueOutOfRangeIsAValidationMessage() {
        assertEquals("commission.error.tier.rate", assertThrows(UserValidationException.class,
                () -> tiers("0", "150", null, null, null, null)).getMessage());
    }
}
