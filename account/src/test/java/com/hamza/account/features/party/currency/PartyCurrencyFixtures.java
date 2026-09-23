package com.hamza.account.features.party.currency;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.events.PartyKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Currencies and an in-memory {@link PartyCurrencies} for the party-currency tests. */
public final class PartyCurrencyFixtures {

    public static final Currency EGP = new Currency(1, "EGP", "جنيه مصري", "ج.م", "L.E.", 2, true, true, 1);
    public static final Currency SAR = new Currency(2, "SAR", "ريال سعودي", "ر.س", "SAR", 2, false, true, 2);
    public static final Currency USD = new Currency(3, "USD", "دولار أمريكي", "$", "", 2, false, true, 3);
    public static final Currency KWD = new Currency(4, "KWD", "دينار كويتي", "د.ك", "", 3, false, true, 4);
    public static final Currency JPY = new Currency(5, "JPY", "ين ياباني", "¥", "", 0, false, true, 5);
    public static final Currency STOPPED = new Currency(6, "TRY", "ليرة تركية", "₺", "", 2, false, false, 6);

    public static final LocalDate DAY = LocalDate.of(2026, 9, 20);

    private PartyCurrencyFixtures() {
    }

    public static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    /** A database of parties, treasuries, rates and documents, held in maps. */
    public static final class Memory implements PartyCurrencies {
        public final Map<Integer, Currency> currencies = new HashMap<>(Map.of(1, EGP, 2, SAR, 3, USD, 4, KWD, 5, JPY,
                6, STOPPED));
        public final Map<String, Integer> partyCurrency = new HashMap<>();
        public final Map<Integer, Integer> treasuryCurrency = new HashMap<>();
        public final Map<String, BigDecimal> rates = new HashMap<>();
        public final Map<String, StoredDocument> documents = new HashMap<>();
        public final Map<String, DocumentAmounts> amounts = new HashMap<>();
        public final Map<String, DocumentTranslation> written = new HashMap<>();
        /** The currency each written document was written in (V83); absent or null for a translation. */
        public final Map<String, Integer> writtenCurrency = new HashMap<>();
        public final Map<Long, PartyMovementFigures> movements = new HashMap<>();
        public final List<String> calls = new ArrayList<>();

        public Memory party(PartyKind kind, int id, Currency currency) {
            partyCurrency.put(kind + ":" + id, currency == null ? null : currency.id());
            return this;
        }

        public Memory treasury(int id, Currency currency) {
            treasuryCurrency.put(id, currency == null ? null : currency.id());
            return this;
        }

        public Memory rate(Currency currency, LocalDate day, String rate) {
            rates.put(currency.id() + "@" + day, new BigDecimal(rate));
            return this;
        }

        public Memory document(DocumentType type, long number, int partyId, LocalDate date, String rate,
                        String total, String discount, String paid) {
            return document(type, number, partyId, date, rate, null, total, discount, paid);
        }

        /** A document written in {@code writtenIn} (V83), or in the base and translated for {@code null}. */
        public Memory document(DocumentType type, long number, int partyId, LocalDate date, String rate,
                               Currency writtenIn, String total, String discount, String paid) {
            documents.put(type + ":" + number,
                    new StoredDocument(partyId, date, rate == null ? null : new BigDecimal(rate),
                            writtenIn == null ? null : writtenIn.id()));
            amounts.put(type + ":" + number,
                    new DocumentAmounts(new BigDecimal(total), new BigDecimal(discount), new BigDecimal(paid)));
            return this;
        }

        @Override
        public Currency ofParty(PartyKind kind, int partyId) {
            Integer id = partyCurrency.get(kind + ":" + partyId);
            return id == null ? null : currencies.get(id);
        }

        @Override
        public Currency ofTreasury(int treasuryId) {
            Integer id = treasuryCurrency.get(treasuryId);
            return id == null ? null : currencies.get(id);
        }

        /** The latest rate dated on the day or before it, as the database answers. */
        @Override
        public BigDecimal rateOn(int currencyId, LocalDate day) {
            calls.add("rateOn:" + currencyId + "@" + day);
            BigDecimal found = null;
            LocalDate best = null;
            for (var entry : rates.entrySet()) {
                String[] parts = entry.getKey().split("@");
                LocalDate dated = LocalDate.parse(parts[1]);
                if (Integer.parseInt(parts[0]) == currencyId && !dated.isAfter(day)
                        && (best == null || dated.isAfter(best))) {
                    best = dated;
                    found = entry.getValue();
                }
            }
            return found;
        }

        @Override
        public void writeMovement(PartyKind kind, long movementId, PartyMovementFigures figures) {
            calls.add("writeMovement:" + movementId);
            movements.put(movementId, figures);
        }

        @Override
        public StoredDocument storedDocument(DocumentType type, long number) {
            return documents.get(type + ":" + number);
        }

        public final Map<String, ForeignHeader> foreignHeaders = new HashMap<>();
        public final Map<String, Map<Integer, WrittenLine>> writtenLines = new HashMap<>();

        @Override
        public ForeignHeader foreignHeader(DocumentType type, long number) {
            return foreignHeaders.get(type + ":" + number);
        }

        @Override
        public Map<Integer, WrittenLine> writtenLines(DocumentType type, long number) {
            return writtenLines.getOrDefault(type + ":" + number, Map.of());
        }

        @Override
        public DocumentAmounts documentAmounts(DocumentType type, long number) {
            return amounts.get(type + ":" + number);
        }

        @Override
        public void writeDocument(DocumentType type, long number, Integer currencyId,
                                  DocumentTranslation translation) {
            calls.add("writeDocument:" + type + ":" + number);
            written.put(type + ":" + number, translation);
            writtenCurrency.put(type + ":" + number, currencyId);
        }
    }
}
