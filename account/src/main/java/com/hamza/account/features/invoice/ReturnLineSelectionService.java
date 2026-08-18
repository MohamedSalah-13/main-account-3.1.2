package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.returns.ReturnableRepository;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.service.ItemUnits;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What a "return from this invoice" picker needs: every line a source invoice sold or
 * bought, resolved to real items and units, each carrying what remains returnable of
 * its item across the whole invoice. No JavaFX control anywhere in it, in the shape
 * {@code InvoiceItemSelectionService} already is.
 */
public final class ReturnLineSelectionService {

    private final DocumentType returnType;
    private final ReturnableRepository repository;
    private final ItemLookup itemLookup;

    public ReturnLineSelectionService(DocumentType returnType,
                                      ReturnableRepository repository,
                                      ItemLookup itemLookup) {
        if (!Objects.requireNonNull(returnType, "returnType").isReturn()) {
            throw new IllegalArgumentException(returnType + " is not a return");
        }
        this.returnType = returnType;
        this.repository = Objects.requireNonNull(repository, "repository");
        this.itemLookup = Objects.requireNonNull(itemLookup, "itemLookup");
    }

    /**
     * Every pickable line of {@code sourceInvoiceNumber}, in the order the source
     * invoice itself lists them. Refuses a source that does not exist or that
     * recorded no lines at all, rather than handing a picker dialog an empty table
     * and no explanation.
     */
    public List<ReturnableLineSelection> selectableLines(int sourceInvoiceNumber)
            throws DaoException {
        DocumentType sourceType = returnType.reverses();
        if (sourceInvoiceNumber <= 0
                || !repository.sourceExists(sourceType, sourceInvoiceNumber)) {
            throw new BusinessRuleException(message("return.error.source.not.found"));
        }
        List<ReturnableRepository.SourceLineRow> rawLines =
                repository.rawLines(sourceType, sourceInvoiceNumber);
        if (rawLines.isEmpty()) {
            throw new BusinessRuleException(message("return.error.source.no.lines"));
        }

        Map<Integer, Double> soldByItem = new LinkedHashMap<>();
        for (ReturnableRepository.SourceLineRow row : rawLines) {
            soldByItem.merge(row.itemId(), row.quantity() * row.typeValue(), Double::sum);
        }
        Map<Integer, Double> alreadyReturned =
                repository.alreadyReturnedBaseQuantities(returnType, sourceInvoiceNumber, 0);

        List<ReturnableLineSelection> selections = new ArrayList<>(rawLines.size());
        for (ReturnableRepository.SourceLineRow row : rawLines) {
            ItemsModel item = itemLookup.byId(row.itemId());
            if (item == null) {
                // The item itself was deleted since the sale - nothing left to offer.
                continue;
            }
            UnitsModel unit = unitOf(item, row.unitId(), row.typeValue());
            double soldBase = soldByItem.getOrDefault(row.itemId(), 0.0);
            double returnedBase = alreadyReturned.getOrDefault(row.itemId(), 0.0);
            double remainingBase = Math.max(0.0, soldBase - returnedBase);
            selections.add(new ReturnableLineSelection(row.lineId(), item, unit,
                    row.quantity(), row.price(), row.discount(), row.buyPrice(),
                    remainingBase, row.expirationDate()));
        }
        return selections;
    }

    /**
     * The delegate to default the return to - the one on the source sale, not
     * whichever the return screen currently has selected. Empty for a purchase
     * return, which has no delegate at all, and for a sales invoice with none on
     * file.
     */
    public java.util.Optional<Integer> sourceDelegateId(int sourceInvoiceNumber)
            throws DaoException {
        if (returnType != DocumentType.SALES_RETURN || sourceInvoiceNumber <= 0) {
            return java.util.Optional.empty();
        }
        return repository.sourceDelegateId(sourceInvoiceNumber);
    }

    /**
     * The customer or supplier the source invoice was with - what the return must be
     * booked to. Prefilled by the picker so the ordinary flow simply lands on the
     * right party; {@code ReturnGuard} is what refuses the wrong one.
     */
    public java.util.Optional<Integer> sourcePartyId(int sourceInvoiceNumber)
            throws DaoException {
        if (sourceInvoiceNumber <= 0) {
            return java.util.Optional.empty();
        }
        return repository.sourcePartyId(returnType.reverses(), sourceInvoiceNumber);
    }

    private static UnitsModel unitOf(ItemsModel item, int unitId, double typeValue) {
        return ItemUnits.unitsFor(item).stream()
                .filter(candidate -> candidate.getUnit_id() == unitId)
                .findFirst()
                // The line's own unit was renamed or removed since the sale -
                // reconstruct one from what the line itself recorded rather than
                // losing the row over it.
                .orElseGet(() -> new UnitsModel(unitId, "#" + unitId, typeValue));
    }

    private static String message(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }

    public interface ItemLookup {
        ItemsModel byId(int itemId) throws DaoException;
    }
}
