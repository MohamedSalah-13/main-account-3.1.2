package com.hamza.account.features.returns;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReturnSettlementAdviceTest {

    private static final BigDecimal OWES = BigDecimal.valueOf(750);
    private static final BigDecimal SETTLED = BigDecimal.ZERO;

    @Test
    void warnsAboutADeferredFreeReturnPutOnTheCashCustomer() {
        // The credit this raises is owed to nobody in particular and will never be paid out.
        assertEquals(ReturnSettlementAdvice.Concern.UNCOLLECTABLE_CREDIT,
                ReturnSettlementAdvice.of(false, true, true, null));
    }

    @Test
    void saysNothingAboutADeferredReturnOfAnInvoiceEvenOnThatSameParty() {
        // It names the sale it reverses, so it is reducing a balance rather than inventing one -
        // and a deferred return of a *cash* invoice never gets this far: ReturnGuard refuses it.
        assertEquals(ReturnSettlementAdvice.Concern.NONE,
                ReturnSettlementAdvice.of(true, true, true, null));
    }

    @Test
    void saysNothingAboutADeferredFreeReturnForANamedCustomer() {
        // Crediting a real customer's account without a receipt is an ordinary thing to do.
        assertEquals(ReturnSettlementAdvice.Concern.NONE,
                ReturnSettlementAdvice.of(false, true, false, null));
    }

    @Test
    void warnsWhenCashGoesBackToAPartyWhoStillOwes() {
        assertEquals(ReturnSettlementAdvice.Concern.REFUND_WHILE_OWING,
                ReturnSettlementAdvice.of(true, false, false, OWES));
    }

    @Test
    void theCashWarningDoesNotDependOnNamingAnInvoice() {
        assertEquals(ReturnSettlementAdvice.Concern.REFUND_WHILE_OWING,
                ReturnSettlementAdvice.of(false, false, false, OWES));
    }

    @Test
    void saysNothingWhenThePartyOwesNothing() {
        assertEquals(ReturnSettlementAdvice.Concern.NONE,
                ReturnSettlementAdvice.of(true, false, false, SETTLED));
        assertEquals(ReturnSettlementAdvice.Concern.NONE,
                ReturnSettlementAdvice.of(true, false, false, BigDecimal.valueOf(-40)));
    }

    @Test
    void anUnreadableBalanceIsNoWarningRatherThanAFailedSave() {
        // A cashier without the party permission gets null here; the save must not care.
        assertEquals(ReturnSettlementAdvice.Concern.NONE,
                ReturnSettlementAdvice.of(true, false, false, null));
    }

    @Test
    void theTwoConcernsCannotBothApply() {
        // One is deferred and the other is cash, so a single answer is enough - which is why
        // the screen shows one question rather than queueing two.
        assertEquals(ReturnSettlementAdvice.Concern.UNCOLLECTABLE_CREDIT,
                ReturnSettlementAdvice.of(false, true, true, OWES));
    }

    @Test
    void onlyNoneIsSilentAndOnlyItHasNothingToSay() {
        assertTrue(ReturnSettlementAdvice.Concern.NONE.isSilent());
        assertFalse(ReturnSettlementAdvice.Concern.UNCOLLECTABLE_CREDIT.isSilent());
        assertFalse(ReturnSettlementAdvice.Concern.REFUND_WHILE_OWING.isSilent());
        assertThrows(IllegalStateException.class,
                () -> ReturnSettlementAdvice.Concern.NONE.messageKey());
    }

    /**
     * Both warnings are translated in all three bundles.
     * <p>
     * {@code MessageKeyArchitectureTest} cannot see them: the screen calls
     * {@code getString(concern.messageKey())} - a variable - exactly as the party statement
     * calls its movement kinds, and a missing key would put the literal
     * {@code return.warn.refund.while.owing} in front of a cashier with a customer waiting.
     * Read off disk because the bundles belong to {@code controlsfx}.
     */
    @Test
    void bothWarningsAreTranslatedInEveryBundle() {
        Path bundleDir = Path.of("..", "controlsfx", "src", "main", "resources", "i18n");
        for (String bundleName : new String[]{
                "messages.properties", "messages_ar.properties", "messages_en.properties"}) {
            Properties bundle = read(bundleDir.resolve(bundleName));
            for (ReturnSettlementAdvice.Concern concern : ReturnSettlementAdvice.Concern.values()) {
                if (concern.isSilent()) {
                    continue;
                }
                String value = bundle.getProperty(concern.messageKey());
                assertNotNull(value, concern.messageKey() + " is missing from " + bundleName);
                assertFalse(value.isBlank(), concern.messageKey() + " is blank in " + bundleName);
            }
            // The one that takes an argument has to take it the way String.format reads it -
            // LanguageManager.getString formats with String.format, and a {0} would print
            // literally with the amount dropped.
            String owing = bundle.getProperty(
                    ReturnSettlementAdvice.Concern.REFUND_WHILE_OWING.messageKey());
            assertTrue(owing.contains("%s"), owing + " must carry %s in " + bundleName);
        }
    }

    private static Properties read(Path file) {
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(file)) {
            properties.load(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + file, e);
        }
        return properties;
    }
}
