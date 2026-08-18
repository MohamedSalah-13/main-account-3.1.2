package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CardItems;
import com.hamza.account.type.ProcessType;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record CardItemService(DaoFactory daoFactory) {

    /**
     * One item's card for a period, narrowed in SQL rather than in the screen.
     *
     * @param processType the only kind of document to return, or null for all four
     */
    public List<CardItems> cardRows(int itemId, LocalDate from, LocalDate to, ProcessType processType) throws DaoException {
        return daoFactory.cardItemDao().cardRows(itemId, from, to, processType);
    }

    /** What the item's balance was at the end of {@code date}, in base units. */
    public double balanceOn(int itemId, LocalDate date) throws DaoException {
        return daoFactory.cardItemDao().balanceOn(itemId, date, true);
    }

    /** What the item's balance was before {@code date} began, in base units. */
    public double balanceBefore(int itemId, LocalDate date) throws DaoException {
        return daoFactory.cardItemDao().balanceOn(itemId, date, false);
    }

    /** The date of the item's first movement, or null if it has never moved. */
    public LocalDate firstMovementDate(int itemId) throws DaoException {
        return daoFactory.cardItemDao().firstMovementDate(itemId);
    }

    public Map<LocalDate, Double> expiryBalancesByItem(int itemId) throws DaoException {
        return daoFactory.cardItemDao().expiryBalancesByItem(itemId);
    }

    public List<CardItems> cardItemsList() throws Exception {
//        return LoadDataAndList.getCardItemsList();
        return daoFactory.cardItemDao().loadAll();
    }

    public Map<String, List<CardItems>> calculateTotalQuantityByItemAndDelegateSales(LocalDate fromDate, LocalDate toDate, String delegateName) throws Exception {
        return calculateTotalQuantityByItemAndDelegate(fromDate, toDate, delegateName, "sales");
    }

    public Map<String, List<CardItems>> calculateTotalQuantityByItemAndDelegatePurchase(LocalDate fromDate, LocalDate toDate) throws Exception {
        return calculateTotalQuantityByItemAndDelegate(fromDate, toDate, null, "purchase");
    }

    private Map<String, List<CardItems>> calculateTotalQuantityByItemAndDelegate(LocalDate fromDate, LocalDate toDate, String delegateName, String tableName) throws Exception {
        Predicate<CardItems> cardItemsPredicate = delegateName == null ? cardItems -> true : cardItems -> cardItems.getDelegate_name().equals(delegateName);
        var list = cardItemsList().stream()
                .filter(cardItems -> !cardItems.getInvoice_date().isBefore(fromDate)
                        && !cardItems.getInvoice_date().isAfter(toDate))
                .filter(cardItems -> cardItems.getTable_name().equals(tableName))
                .filter(cardItemsPredicate)
                .toList();

        return list.stream()
                .collect(Collectors.groupingBy(CardItems::getNameItem,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                cardItems -> {
                                    // Base units: two lines of one item can be in
                                    // different units, so their raw quantities do not
                                    // add up to anything.
                                    double sumQuantity = cardItems.stream().mapToDouble(row -> Math.abs(row.getBaseQuantity())).sum();
                                    return List.of(new CardItems(cardItems.getFirst().getNumItem(), cardItems.getFirst().getNameItem(), sumQuantity));
                                }
                        )));

    }
}
