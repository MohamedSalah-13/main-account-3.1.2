package com.hamza.account.controller.invoice;

public class SaveJson {

//    private void deleteFile() {
//        File powerOutageBillsDir = new File(Configs.POWER_OUTAGE_BILLS_PATH);
//        String filename = "bill_" + invNumber + "_" + dataInterface.designInterface().nameTextOfInvoice() + ".json";
//        File outputFile = new File(powerOutageBillsDir, filename);
//        if (outputFile.exists()) {
//            boolean deleted = outputFile.delete();
//            if (!deleted) {
//                log.error("Error deleting file: {}", outputFile.getAbsolutePath());
//            }
//        }
//    }

    /*private boolean saveBillDuringPowerOutage() {
        if (table.getItems().isEmpty()) {
            return false;
        }

        try {
            // Create the power outage bills directory if it doesn't exist
            File powerOutageBillsDir = new File(Configs.POWER_OUTAGE_BILLS_PATH);
            if (!powerOutageBillsDir.exists()) {
                boolean created = powerOutageBillsDir.mkdirs();
                if (!created) {
                    log.error("Failed to create power outage bills directory");
                    return false;
                }
            }

            String invoiceDate = Optional.ofNullable(date.getValue()).map(LocalDate::toString).orElse("");
            double total = DoubleSetting.parseDoubleOrDefault(txtSumTotals.getText());
            double discountValue = DoubleSetting.parseDoubleOrDefault(txtOtherDiscount.getText());
            double paidValue = DoubleSetting.parseDoubleOrDefault(txtPaid.getText());
            String notes = Optional.ofNullable(txtNotes.getText()).orElse("");

            InvoiceType invoiceType = radioCash.isSelected() ? InvoiceType.CASH : InvoiceType.DEFER;
            String treasuryName = Optional.ofNullable(comboTreasury.getSelectionModel().getSelectedItem()).orElse("");
            String employeeName = Optional.ofNullable(comboDelegate.getSelectionModel().getSelectedItem()).orElse("");

            JSONObject jsonObject = new JSONObject();
            // JSON object and values
            jsonObject.put("invNumber", invNumber);
            jsonObject.put("invoiceType", invoiceType.getType());
            jsonObject.put("invoiceDate", invoiceDate);
            jsonObject.put("total", total);
            jsonObject.put("invoiceDiscountType", radioAmount.isSelected() ? "amount" : "rate");
            jsonObject.put("discountValue", discountValue);
            jsonObject.put("paidValue", paidValue);
            jsonObject.put("notes", notes);
            jsonObject.put("stock", Optional.ofNullable(getStockIdBySelectedStock()).map(Stock::getName).orElse(""));
            jsonObject.put("treasury", treasuryName);
            jsonObject.put("employees", employeeName);
            jsonObject.put("name", Optional.ofNullable(textSearchName.get()).orElse(""));
            jsonObject.put("powerOutage", true);
            jsonObject.put("timestamp", System.currentTimeMillis());

            // JSON array and values
            var t1s = listOfItemsPurchase(invNumber);
            var list = modelPrintInvoices;
            JSONArray jsonArray = new JSONArray();
            for (ModelPrintInvoice e : list) {
                JSONObject jsonObjectItems = new JSONObject();
                jsonObjectItems.put("barcode", e.getBarcode());
                jsonObjectItems.put("price", e.getPrice());
                jsonObjectItems.put("quantity", e.getQuantity());
                jsonObjectItems.put("discount", e.getDiscount());
                jsonArray.add(jsonObjectItems);
            }

            jsonObject.put("items", jsonArray);

            // Generate a unique filename with timestamp
//            String timestamp = String.valueOf(System.currentTimeMillis());
            String filename = "bill_" + invNumber + "_" + dataInterface.designInterface().nameTextOfInvoice() + ".json";
            File outputFile = new File(powerOutageBillsDir, filename);

            // Write the JSON object to the file
            try (FileWriter fileWriter = new FileWriter(outputFile)) {
                fileWriter.write(jsonObject.toJSONString());
                fileWriter.flush();
                log.info("Bill saved during power outage: {}", outputFile.getAbsolutePath());
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to save bill during power outage: {}", e.getMessage(), e);
        }

        return false;
    }

    private void checkForPowerOutageBills() {
        try {
            File powerOutageBillsDir = new File(Configs.POWER_OUTAGE_BILLS_PATH);
            if (!powerOutageBillsDir.exists()) {
                return; // No directory, so no bills to process
            }

            File[] files = powerOutageBillsDir.listFiles((dir, name) -> name.startsWith("bill_") && name.endsWith(".json"));
            if (files == null || files.length == 0) {
                return; // No bills to process
            }

            log.info("Found {} bills saved during power outages", files.length);

            // Sort files by timestamp (oldest first)
            Arrays.sort(files, (f1, f2) -> {
                long t1 = Long.parseLong(f1.getName().split("_")[2].replace(".json", ""));
                long t2 = Long.parseLong(f2.getName().split("_")[2].replace(".json", ""));
                return Long.compare(t1, t2);
            });

            // Process each bill
            for (File file : files) {
                try {
                    processPowerOutageBill(file);
                    Thread.sleep(100); // Small delay between processing files
                } catch (Exception e) {
                    log.error("Failed to process power outage bill: {}", file.getName(), e);
                    // Move failed file to error directory
                    File errorDir = new File(powerOutageBillsDir, "errors");
                    if (!errorDir.exists()) {
                        errorDir.mkdirs();
                    }
                    File errorFile = new File(errorDir, file.getName());
                    if (file.renameTo(errorFile)) {
                        log.info("Moved failed bill to errors directory: {}", errorFile.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error checking for power outage bills", e);
            AllAlerts.alertError("حدث خطأ أثناء معالجة الفواتير المحفوظة: " + e.getMessage());
        }
    }

    private void processPowerOutageBill(File file) throws Exception {
        log.info("Processing power outage bill: {}", file.getName());

        JSONParser jsonParser = new JSONParser();
        JSONObject jsonObject = (JSONObject) jsonParser.parse(new FileReader(file));

        // Extract bill data
        long invNumber = (long) jsonObject.get("invNumber");
        String invoiceType = (String) jsonObject.get("invoiceType");
        String invoiceDate = (String) jsonObject.get("invoiceDate");
        double total = (Double) jsonObject.get("total");
        String invoiceDiscountType = (String) jsonObject.get("invoiceDiscountType");
        double discountValue = (Double) jsonObject.get("discountValue");
        double paidValue = (Double) jsonObject.get("paidValue");
        String notes = (String) jsonObject.get("notes");
        String stockName = (String) jsonObject.get("stock");
        String treasuryName = (String) jsonObject.get("treasury");
        String employeesName = (String) jsonObject.get("employees");
        String name = (String) jsonObject.get("name");

        // Set up the invoice data in the UI
        date.setValue(LocalDate.parse(invoiceDate));
        txtOtherDiscount.setText(String.valueOf(discountValue));
        txtPaid.setText(String.valueOf(paidValue));
        comboTreasury.getSelectionModel().select(treasuryName);
        comboStock.getSelectionModel().select(stockName);
        comboDelegate.getSelectionModel().select(employeesName);
        textSearchName.set(name);
        txtNotes.setText(notes);

        // Set invoice type
        var invoiceTypeEnum = InvoiceType.getInvoiceTypeByType(invoiceType);
        radioCash.setSelected(invoiceTypeEnum.equals(InvoiceType.CASH));
        radioDeffer.setSelected(invoiceTypeEnum.equals(InvoiceType.DEFER));

        // Set discount type
        radioAmount.setSelected(invoiceDiscountType.equals("amount"));
        radioRate.setSelected(invoiceDiscountType.equals("rate"));

        // Clear existing items
        myObservableList.clear();

        // Add items to the table
        JSONArray items = (JSONArray) jsonObject.get("items");
        for (Object item : items) {
            JSONObject itemObj = (JSONObject) item;
            String barcode = (String) itemObj.get("barcode");
            double price = (Double) itemObj.get("price");
            double quantity = (Double) itemObj.get("quantity");
            double discount = (Double) itemObj.get("discount");

            // Find the item by barcode
            ItemsModel itemModel = itemsService.getItemByBarcodeAndStockId(barcode, getStockIdBySelectedStock().getId());
            if (itemModel != null) {
                UnitsModel unitsModel = unitsService.getUnitsById(1); // Default unit
                T1 object = invoiceBuy.object_TableData(0, (int) invNumber, itemModel.getId(), price, quantity, discount, price * quantity, unitsModel, itemModel);
                myObservableList.add(object);
            }
        }

        // Try to save the invoice to the database
        try {
            // If successful, delete the file
            if (file.delete()) {
                log.info("Deleted processed power outage bill: {}", file.getName());
            } else {
                log.warn("Failed to delete processed power outage bill: {}", file.getName());
            }

            AllAlerts.alertSaveWithMessage("تم استرجاع الفاتورة المحفوظة أثناء انقطاع الاتصال وحفظها في قاعدة البيانات.");
        } catch (Exception e) {
            log.error("Failed to save recovered bill to database", e);
            AllAlerts.alertError("فشل حفظ الفاتورة المسترجعة في قاعدة البيانات. سيتم المحاولة مرة أخرى في المرة القادمة.");
        }
    }

    private void writeFile() throws Exception {
        String invoiceDate = date.getValue().toString();
        double total = Double.parseDouble(txtSumTotals.getText());
        double discountValue = Double.parseDouble(txtOtherDiscount.getText());
        double paidValue = Double.parseDouble(txtPaid.getText());
        String notes = txtNotes.getText();

        InvoiceType invoiceType = radioCash.isSelected() ? InvoiceType.CASH : InvoiceType.DEFER;
        TreasuryModel treasuryByName = treasuryService.getTreasuryByName(comboTreasury.getSelectionModel().getSelectedItem());
        Employees employees = employeeService.getDelegateByName(comboDelegate.getSelectionModel().getSelectedItem());


        JSONObject jsonObject = new JSONObject();
        //JSON object and values
        jsonObject.put("invNumber", invNumber);
        jsonObject.put("invoiceType", invoiceType.getType());
        jsonObject.put("invoiceDate", invoiceDate);
        jsonObject.put("total", total);
        jsonObject.put("invoiceDiscountType", radioAmount.isSelected() ? "amount" : "rate");
        jsonObject.put("discountValue", discountValue);
        jsonObject.put("paidValue", paidValue);
        jsonObject.put("notes", notes);
        jsonObject.put("stock", getStockIdBySelectedStock().getName());
        jsonObject.put("treasury", treasuryByName.getName());
        jsonObject.put("employees", employees.getName());
        jsonObject.put("name", textSearchName.get());

        //JSON array and values
        var t1s = listOfItemsPurchase(invNumber);
        var list = modelPrintInvoices;
        JSONArray jsonArray = new JSONArray();
        for (int i = 0; i < list.size(); i++) {
            var e = list.get(i);
            JSONObject jsonObjectItems = new JSONObject();
            jsonObjectItems.put("barcode", e.getBarcode());
            jsonObjectItems.put("price", e.getPrice());
            jsonObjectItems.put("quantity", e.getQuantity());
            jsonObjectItems.put("discount", e.getDiscount());
            jsonArray.add(jsonObjectItems);
        }

        jsonObject.put("items", jsonArray);
        // writing the JSONObject into a file(info.json)
        // add chooseFile To save
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(Extensions.EXT_FILTER_JSON);
        String nameFile = "info.json";
        fc.setInitialFileName(nameFile);
        File dirTo = fc.showSaveDialog(null);
        if (dirTo != null) {
            FileWriter fileWriter = new FileWriter(dirTo.getAbsoluteFile());
            fileWriter.write(jsonObject.toJSONString());
            fileWriter.flush();
            AllAlerts.alertSave();
        }
    }

    private void read() throws Exception {
        JSONParser jsonParser = new JSONParser();
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(Extensions.EXT_FILTER_JSON);
        File dirTo = fc.showOpenDialog(null);
        if (dirTo != null) {
            JSONObject person = (JSONObject) jsonParser.parse(new FileReader(dirTo.getAbsoluteFile()));

            double total = (Double) person.get("total");
            long invoiceNumber = (long) person.get("invNumber");
            String name = (String) person.get("name");
            String employees = (String) person.get("employees");

            String invoiceDiscountType = (String) person.get("invoiceDiscountType");
            String notes = (String) person.get("notes");
            String treasury = (String) person.get("treasury");
            String invoiceType = (String) person.get("invoiceType");
            double paidValue = (Double) person.get("paidValue");
            String invoiceDate = (String) person.get("invoiceDate");
            String stock = (String) person.get("stock");
            double discountValue = (Double) person.get("discountValue");
            var invoiceTypeByType = InvoiceType.getInvoiceTypeByType(invoiceType);
            radioCash.setSelected(invoiceTypeByType.equals(InvoiceType.CASH));
            radioDeffer.setSelected(invoiceTypeByType.equals(InvoiceType.DEFER));
            radioAmount.setSelected(invoiceDiscountType.equals("amount"));
            radioRate.setSelected(invoiceDiscountType.equals("rate"));

            txtOtherDiscount.setText(String.valueOf(discountValue));
            txtPaid.setText(String.valueOf(paidValue));
            comboTreasury.getSelectionModel().select(treasury);
            comboStock.getSelectionModel().select(stock);
            date.setValue(LocalDate.parse(invoiceDate));
            txtNotes.setText(notes);

            JSONArray items = (JSONArray) person.get("items");
            for (Object c : items) {
                JSONObject item = (JSONObject) c;
                String barcode = (String) item.get("barcode");
                double price = (Double) item.get("price");
                double quantity = (Double) item.get("quantity");
                double discount = (Double) item.get("discount");
                var itemByItemIdAndStockId = itemsService.getItemByBarcodeAndStockId(barcode, 1);

                UnitsModel unitsModel = unitsService.getUnitsById(1);
                T1 object = invoiceBuy.object_TableData(0, num_invoice_update, 0, price, quantity, discount, price * quantity, unitsModel, itemByItemIdAndStockId);
                myObservableList.add(object);
            }
        }
    }*/
}
