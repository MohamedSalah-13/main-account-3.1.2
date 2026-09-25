package com.hamza.account.features.offers;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * One item an offer reached over a period - its row's drawer: base units sold and returned, the offer's
 * discount that stayed given, the net, and - for a reader who may see one - the cost.
 */
public record OfferPerformanceItem(int itemId, String name, String unitName, BigDecimal soldQuantity,
                                   BigDecimal returnedQuantity, BigDecimal discount, BigDecimal net,
                                   BigDecimal cost) {

    public OfferPerformanceItem {
        name = name == null ? "" : name;
        unitName = unitName == null ? "" : unitName;
        soldQuantity = soldQuantity == null ? BigDecimal.ZERO : soldQuantity;
        returnedQuantity = returnedQuantity == null ? BigDecimal.ZERO : returnedQuantity;
        discount = discount == null ? BigDecimal.ZERO : discount;
        net = net == null ? BigDecimal.ZERO : net;
    }

    public BigDecimal netQuantity() {
        return soldQuantity.subtract(returnedQuantity);
    }

    public Optional<BigDecimal> profit() {
        return cost == null ? Optional.empty() : Optional.of(net.subtract(cost));
    }
}
