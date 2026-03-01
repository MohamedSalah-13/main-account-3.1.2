package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CardItems;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record CardItemService(DaoFactory daoFactory) {

    public List<CardItems> cardItemsListByNumItem(int item_id) throws Exception {
        return cardItemsList().stream().filter(cardItems -> cardItems.getNumItem() == item_id).toList();
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
                                    double sumQuantity = cardItems.stream().mapToDouble(CardItems::getQuantity).sum();
                                    return List.of(new CardItems(cardItems.getFirst().getNumItem(), cardItems.getFirst().getNameItem(), sumQuantity));
                                }
                        )));

    }
}
