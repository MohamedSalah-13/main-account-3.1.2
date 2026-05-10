package com.hamza.account.model.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ComprehensiveSalesReport {
    private String invoiceNumber;
    private LocalDateTime invoiceDate;
    private String customerName;
    private double grossTotal; // الإجمالي قبل الخصم
    private double discount;   // قيمة الخصم
    private double netTotal;   // الصافي بعد الخصم
    private double payed;      // المبلغ المدفوع
    private double remain;     // المبلغ المتبقي (الآجل)
}