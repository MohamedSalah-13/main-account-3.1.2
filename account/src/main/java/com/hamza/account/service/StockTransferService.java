package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.StockTransferDao;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.model.domain.StockTransfer;
import com.hamza.account.model.domain.StockTransferListItems;
import com.hamza.controlsfx.database.DaoException;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.List;

public record StockTransferService(DaoFactory daoFactory) {

    public List<StockTransfer> getStockTransferList() throws DaoException {
        return getStockTransferDao().loadAll();
    }

    public StockTransfer getStockTransfersById(int id) throws DaoException {
        validateId(id, "رقم التحويل غير صحيح");
        return getStockTransferDao().getDataById(id);
    }

    public int insertData(StockTransfer stockTransfer) throws DaoException {
        validateStockTransferForInsert(stockTransfer);
        return getStockTransferDao().insert(stockTransfer);
    }

    public int updateData(StockTransfer stockTransfer) throws DaoException {
        throw new DaoException("لا يمكن تعديل تحويل مخزني بعد ترحيله. قم بإلغاء التحويل ثم أنشئ تحويلًا جديدًا.");
    }

    public int deleteTransfer(StockTransfer stockTransfer) throws DaoException {
        throw new DaoException("لا يمكن حذف تحويل مخزني بعد ترحيله. استخدم إلغاء التحويل بتحويل عكسي.");
    }

    public int deleteTransferById(int transferId) throws DaoException {
        validateId(transferId, "رقم التحويل غير صحيح");
        throw new DaoException("لا يمكن حذف تحويل مخزني بعد ترحيله. استخدم إلغاء التحويل بتحويل عكسي.");
    }

    public int cancelTransfer(int transferId, int userId, String reason) throws DaoException {
        validateId(transferId, "رقم التحويل غير صحيح");
        validateId(userId, "رقم المستخدم غير صحيح");

        if (reason == null || reason.trim().isEmpty()) {
            throw new DaoException("يجب إدخال سبب إلغاء التحويل");
        }

        return getStockTransferDao().cancelTransfer(transferId, userId, reason.trim());
    }

    public boolean canCancelTransfer(int transferId) throws DaoException {
        validateId(transferId, "رقم التحويل غير صحيح");

        StockTransfer stockTransfer = getStockTransferDao().getDataById(transferId);

        if (stockTransfer == null) {
            return false;
        }

        return "POSTED".equalsIgnoreCase(stockTransfer.getStatus());
    }

    public StockTransfer stockTransfer(
            int id,
            int stockFrom,
            int stockTo,
            LocalDate date,
            List<StockTransferListItems> transferList
    ) throws DaoException {
        validateId(stockFrom, "رقم المخزن المصدر غير صحيح");
        validateId(stockTo, "رقم المخزن المستلم غير صحيح");

        if (stockFrom == stockTo) {
            throw new DaoException("لا يمكن التحويل إلى نفس المخزن");
        }

        if (date == null) {
            throw new DaoException("يجب اختيار تاريخ التحويل");
        }

        if (transferList == null || transferList.isEmpty()) {
            throw new DaoException("يجب إضافة أصناف للتحويل");
        }

        validateTransferItems(transferList);

        StockTransfer stockTransfer = new StockTransfer();
        stockTransfer.setId(id);
        stockTransfer.setStockFrom(new Stock(stockFrom));
        stockTransfer.setStockTo(new Stock(stockTo));
        stockTransfer.setDate(date);
        stockTransfer.setStatus("POSTED");
        stockTransfer.setTransferListItems(transferList);

        return stockTransfer;
    }

    @NotNull
    private StockTransferDao getStockTransferDao() {
        return daoFactory.stockTransferDao();
    }

    private void validateStockTransferForInsert(StockTransfer stockTransfer) throws DaoException {
        if (stockTransfer == null) {
            throw new DaoException("بيانات التحويل غير صحيحة");
        }

        if (stockTransfer.getStockFrom() == null || stockTransfer.getStockFrom().getId() <= 0) {
            throw new DaoException("يجب اختيار المخزن المصدر");
        }

        if (stockTransfer.getStockTo() == null || stockTransfer.getStockTo().getId() <= 0) {
            throw new DaoException("يجب اختيار المخزن المستلم");
        }

        if (stockTransfer.getStockFrom().getId() == stockTransfer.getStockTo().getId()) {
            throw new DaoException("لا يمكن التحويل إلى نفس المخزن");
        }

        if (stockTransfer.getDate() == null) {
            throw new DaoException("يجب اختيار تاريخ التحويل");
        }

        if (stockTransfer.getTransferListItems() == null || stockTransfer.getTransferListItems().isEmpty()) {
            throw new DaoException("يجب إضافة أصناف للتحويل");
        }

        validateTransferItems(stockTransfer.getTransferListItems());
    }

    private void validateTransferItems(List<StockTransferListItems> transferList) throws DaoException {
        for (StockTransferListItems item : transferList) {
            if (item == null) {
                throw new DaoException("يوجد صنف غير صحيح داخل التحويل");
            }

            if (item.getItem() == null || item.getItem().getId() <= 0) {
                throw new DaoException("يوجد صنف غير صحيح داخل التحويل");
            }

            if (item.getQuantity() <= 0) {
                throw new DaoException("كمية الصنف يجب أن تكون أكبر من صفر: " + item.getItem().getNameItem());
            }
        }
    }

    private void validateId(int id, String message) throws DaoException {
        if (id <= 0) {
            throw new DaoException(message);
        }
    }
}