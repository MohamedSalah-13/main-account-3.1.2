package com.hamza.account.otherSetting;

import com.hamza.account.model.domain.ItemsModel;
import lombok.Builder;

@Builder
public record BarcodeResult(ItemsModel item, double selPrice, double total, double quantity) {
}
