package com.hamza.account.service;

import com.hamza.account.database.DaoException;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.DelegateTarget;
import lombok.extern.log4j.Log4j2;

import java.util.List;

@Log4j2
public class DelegateTargetService {

    private final DaoFactory daoFactory;

    public DelegateTargetService(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    public List<DelegateTarget> getAllTargets() throws DaoException {
        return daoFactory.delegateTargetDao().loadAll();
    }

    public List<DelegateTarget> getActiveTargets() throws DaoException {
        return daoFactory.delegateTargetDao().loadActiveTargets();
    }

    public List<DelegateTarget> getTargetsByDelegateId(int delegateId) throws DaoException {
        if (delegateId <= 0) {
            return getAllTargets();
        }
        return daoFactory.delegateTargetDao().loadByDelegateId(delegateId);
    }

    public DelegateTarget getTargetById(int id) throws DaoException {
        if (id <= 0) {
            return null;
        }
        return daoFactory.delegateTargetDao().getDataById(id);
    }

    public int insert(DelegateTarget target) throws DaoException {
        validate(target, false);
        return daoFactory.delegateTargetDao().insert(target);
    }

    public int update(DelegateTarget target) throws DaoException {
        validate(target, true);
        return daoFactory.delegateTargetDao().update(target);
    }

    public int deleteById(int id) throws DaoException {
        if (id <= 0) {
            throw new DaoException("اختر الهدف أولاً");
        }
        return daoFactory.delegateTargetDao().deleteById(id);
    }

    public List<DelegateTarget> search(String text) throws DaoException {
        List<DelegateTarget> targets = getAllTargets();

        if (text == null || text.isBlank()) {
            return targets;
        }

        String searchText = text.trim().toLowerCase();

        return targets.stream()
                .filter(target ->
                        contains(target.getTargetName(), searchText)
                                || contains(target.getDelegateName(), searchText)
                                || contains(target.getTargetType(), searchText)
                                || contains(target.getStatus(), searchText)
                )
                .toList();
    }

    public List<String> getTargetTypes() {
        return List.of(
                "SALES_AMOUNT",
                "NET_SALES_AMOUNT",
                "COLLECTION_AMOUNT",
                "PROFIT_AMOUNT",
                "PROFIT_PERCENT",
                "INVOICES_COUNT",
                "CUSTOMERS_COUNT",
                "ITEM_QUANTITY"
        );
    }

    public List<String> getPeriodTypes() {
        return List.of(
                "DAILY",
                "WEEKLY",
                "MONTHLY",
                "QUARTERLY",
                "YEARLY",
                "CUSTOM"
        );
    }

    public List<String> getStatuses() {
        return List.of(
                "ACTIVE",
                "PAUSED",
                "CANCELLED",
                "CLOSED"
        );
    }

    private void validate(DelegateTarget target, boolean update) throws DaoException {
        if (target == null) {
            throw new DaoException("لا توجد بيانات للحفظ");
        }

        if (update && target.getId() <= 0) {
            throw new DaoException("اختر الهدف أولاً");
        }

        if (target.getDelegateId() <= 0) {
            throw new DaoException("من فضلك اختر المندوب");
        }

        if (target.getTargetName() == null || target.getTargetName().isBlank()) {
            throw new DaoException("من فضلك اكتب اسم الهدف");
        }

        if (target.getTargetType() == null || target.getTargetType().isBlank()) {
            throw new DaoException("من فضلك اختر نوع الهدف");
        }

        if (!getTargetTypes().contains(target.getTargetType())) {
            throw new DaoException("نوع الهدف غير صحيح");
        }

        if (target.getPeriodType() == null || target.getPeriodType().isBlank()) {
            target.setPeriodType("MONTHLY");
        }

        if (!getPeriodTypes().contains(target.getPeriodType())) {
            throw new DaoException("نوع الفترة غير صحيح");
        }

        if (target.getPeriodFrom() == null) {
            throw new DaoException("من فضلك اختر تاريخ البداية");
        }

        if (target.getPeriodTo() == null) {
            throw new DaoException("من فضلك اختر تاريخ النهاية");
        }

        if (target.getPeriodTo().isBefore(target.getPeriodFrom())) {
            throw new DaoException("تاريخ النهاية يجب أن يكون أكبر من أو يساوي تاريخ البداية");
        }

        if (target.getStatus() == null || target.getStatus().isBlank()) {
            target.setStatus("ACTIVE");
        }

        if (!getStatuses().contains(target.getStatus())) {
            throw new DaoException("حالة الهدف غير صحيحة");
        }

        validateTargetValue(target);
    }

    private void validateTargetValue(DelegateTarget target) throws DaoException {
        switch (target.getTargetType()) {
            case "SALES_AMOUNT",
                 "NET_SALES_AMOUNT",
                 "COLLECTION_AMOUNT",
                 "PROFIT_AMOUNT" -> {
                if (target.getTargetAmount() <= 0) {
                    throw new DaoException("قيمة الهدف يجب أن تكون أكبر من صفر");
                }
            }

            case "PROFIT_PERCENT" -> {
                if (target.getMinProfitPercent() <= 0) {
                    throw new DaoException("نسبة الربح يجب أن تكون أكبر من صفر");
                }
            }

            case "INVOICES_COUNT",
                 "CUSTOMERS_COUNT" -> {
                if (target.getTargetCount() <= 0) {
                    throw new DaoException("عدد الهدف يجب أن يكون أكبر من صفر");
                }
            }

            case "ITEM_QUANTITY" -> {
                if (target.getTargetQuantity() <= 0) {
                    throw new DaoException("كمية الهدف يجب أن تكون أكبر من صفر");
                }
            }

            default -> throw new DaoException("نوع الهدف غير صحيح");
        }
    }

    private boolean contains(String source, String searchText) {
        return source != null && source.toLowerCase().contains(searchText);
    }
}
