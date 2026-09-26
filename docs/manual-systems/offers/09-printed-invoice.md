---
title: فاتورة البيع التي طبّق عليها العرض
chapter: البيع بعد إعداد العرض
shortcut: TOTAL_SALES
click: DOUBLECLICK_INVOICE_1011 >> PRINT_INVOICE_1011
screenshot: offer-invoice-print
sources: account/src/main/java/com/hamza/account/controller/invoice/ShowInvoiceController.java, account/src/main/java/com/hamza/account/features/offers/OfferEngine.java
---
![معاينة فاتورة PDF محفوظة بعد تطبيق العرض التجريبي](offer-invoice-print)

تُظهر الفاتورة المطبوعة سعر السطر والخصم وصافي المبلغ بعد العرض. يظل العرض وخصمه محفوظين على سطر الفاتورة، ولذلك يمكن تتبع قيمتهما في تفاصيل الفاتورة وتقارير أداء العروض.

النسخة المصورة مثال تجريبي محفوظ في قاعدة البيانات المنفصلة المخصصة لإعداد الدليل. عند الطباعة من شاشة الفواتير يفتح البرنامج معاينة PDF، ومنها يمكنك مراجعة المستند قبل حفظه أو طباعته.
