package com.hamza.account.features.scalebarcode;

import com.hamza.account.model.domain.ItemsModel;
import lombok.Builder;

/** One scale barcode, resolved: which item, at what unit price, for what weight and total. */
@Builder
public record ScaleBarcodeReading(ItemsModel item, double selPrice, double total, double quantity) {
}
