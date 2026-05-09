package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerPurchasedItem;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public record CustomerPurchasedItemsService(DaoFactory daoFactory) {

    public List<CustomerPurchasedItem> filterByDateRange(
            List<CustomerPurchasedItem> source,
            LocalDate from,
            LocalDate to) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        if (from == null || to == null) {
            return source;
        }
        return source.stream()
                .filter(row -> {
                    if (row.getInvoiceDate() == null || row.getInvoiceDate().toString().isBlank()) {
                        return false;
                    }
                    LocalDate rowDate = row.getInvoiceDate();
                    return (rowDate.isEqual(from) || rowDate.isAfter(from))
                            && (rowDate.isEqual(to) || rowDate.isBefore(to));
                })
                .toList();
    }

    public List<CustomerPurchasedItem> filterByItemName(
            List<CustomerPurchasedItem> source,
            String name) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        if (name == null || name.isBlank()) {
            return source;
        }
        String search = name.trim().toLowerCase(Locale.ROOT);
        return source.stream()
                .filter(row -> row.getItemName() != null
                        && row.getItemName().toLowerCase(Locale.ROOT).contains(search))
                .toList();
    }

    public List<CustomerPurchasedItem> sortByDateDescending(List<CustomerPurchasedItem> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .sorted(Comparator.comparing(
                        row -> row.getInvoiceDate(),
                        Comparator.reverseOrder()
                ))
                .toList();
    }

    public double sumTotalSales(List<CustomerPurchasedItem> source) {
        if (source == null || source.isEmpty()) {
            return 0;
        }
        return source.stream().mapToDouble(value -> value.getSellingPrice().doubleValue()).sum();
    }

    public double sumTotalAfterDiscount(List<CustomerPurchasedItem> source) {
        if (source == null || source.isEmpty()) {
            return 0;
        }
        return source.stream().mapToDouble(value -> value.getQuantity().doubleValue()).sum();
    }

    public double sumTotalQuantity(List<CustomerPurchasedItem> source) {
        if (source == null || source.isEmpty()) {
            return 0;
        }
        return source.stream().mapToDouble(value -> value.getQuantity().doubleValue()).sum();
    }
}