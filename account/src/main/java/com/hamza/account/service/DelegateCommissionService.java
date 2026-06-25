package com.hamza.account.service;

import com.hamza.account.database.DaoException;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.DelegateCommission;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.util.List;

@Log4j2
public class DelegateCommissionService {

    private final DaoFactory daoFactory;

    public DelegateCommissionService(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    public List<DelegateCommission> getAllCommissions() throws DaoException {
        return daoFactory.delegateCommissionDao().loadAll();
    }

    public List<DelegateCommission> getCommissionsByDelegateId(int delegateId) throws DaoException {
        if (delegateId <= 0) {
            return getAllCommissions();
        }
        return daoFactory.delegateCommissionDao().loadByDelegateId(delegateId);
    }

    public List<DelegateCommission> getCommissionsByPeriod(
            Integer delegateId,
            LocalDate dateFrom,
            LocalDate dateTo
    ) throws DaoException {
        validateDates(dateFrom, dateTo);
        return daoFactory.delegateCommissionDao().loadByPeriod(normalizeId(delegateId), dateFrom, dateTo);
    }

    public DelegateCommission getCommissionById(long id) throws DaoException {
        if (id <= 0) {
            return null;
        }
        return daoFactory.delegateCommissionDao().getDataById(id);
    }

    public int insert(DelegateCommission commission) throws DaoException {
        validate(commission, false);
        return daoFactory.delegateCommissionDao().insert(commission);
    }

    public int update(DelegateCommission commission) throws DaoException {
        validate(commission, true);
        return daoFactory.delegateCommissionDao().update(commission);
    }

    public int deleteById(long id) throws DaoException {
        if (id <= 0) {
            throw new DaoException("اختر العمولة أولاً");
        }
        return daoFactory.delegateCommissionDao().deleteById(id);
    }

    public DelegateCommission calculateCommission(
            int delegateId,
            LocalDate dateFrom,
            LocalDate dateTo,
            int userId
    ) throws DaoException {
        if (delegateId <= 0) {
            throw new DaoException("من فضلك اختر المندوب");
        }

        validateDates(dateFrom, dateTo);

        return daoFactory.delegateCommissionDao()
                .calculateCommission(delegateId, dateFrom, dateTo, userId);
    }

    public int payCommission(
            long commissionId,
            double paidAmount,
            int treasuryId,
            LocalDate paymentDate
    ) throws DaoException {
        if (commissionId <= 0) {
            throw new DaoException("اختر العمولة أولاً");
        }

        if (paidAmount <= 0) {
            throw new DaoException("قيمة الدفع يجب أن تكون أكبر من صفر");
        }

        if (treasuryId <= 0) {
            throw new DaoException("من فضلك اختر الخزينة");
        }

        if (paymentDate == null) {
            paymentDate = LocalDate.now();
        }

        return daoFactory.delegateCommissionDao()
                .payCommission(commissionId, paidAmount, treasuryId, paymentDate);
    }

    public double sumCommissionAmount(List<DelegateCommission> list) {
        return list == null ? 0 : list.stream()
                .mapToDouble(DelegateCommission::getCommissionAmount)
                .sum();
    }

    public double sumPaidAmount(List<DelegateCommission> list) {
        return list == null ? 0 : list.stream()
                .mapToDouble(DelegateCommission::getPaidAmount)
                .sum();
    }

    public double sumRemainingAmount(List<DelegateCommission> list) {
        return sumCommissionAmount(list) - sumPaidAmount(list);
    }

    public List<String> getCommissionTypes() {
        return List.of(
                "SALES_PERCENT",
                "PROFIT_PERCENT",
                "FIXED_PER_INVOICE"
        );
    }

    public List<String> getPaymentStatuses() {
        return List.of(
                "UNPAID",
                "PARTIAL",
                "PAID",
                "CANCELLED"
        );
    }

    private void validate(DelegateCommission commission, boolean update) throws DaoException {
        if (commission == null) {
            throw new DaoException("لا توجد بيانات للحفظ");
        }

        if (update && commission.getId() <= 0) {
            throw new DaoException("اختر العمولة أولاً");
        }

        if (commission.getDelegateId() <= 0) {
            throw new DaoException("من فضلك اختر المندوب");
        }

        if (commission.getCommissionDate() == null) {
            commission.setCommissionDate(LocalDate.now());
        }

        if (commission.getReferenceType() == null || commission.getReferenceType().isBlank()) {
            commission.setReferenceType("PERIOD");
        }

        if (!List.of("SALE", "PERIOD", "TARGET").contains(commission.getReferenceType())) {
            throw new DaoException("نوع المرجع غير صحيح");
        }

        if (commission.getSalesAmount() < 0) {
            throw new DaoException("قيمة المبيعات لا يمكن أن تكون أقل من صفر");
        }

        if (commission.getProfitAmount() < 0) {
            throw new DaoException("قيمة الربح لا يمكن أن تكون أقل من صفر");
        }

        if (commission.getCommissionType() == null || commission.getCommissionType().isBlank()) {
            throw new DaoException("من فضلك اختر نوع العمولة");
        }

        if (!getCommissionTypes().contains(commission.getCommissionType())) {
            throw new DaoException("نوع العمولة غير صحيح");
        }

        if (commission.getCommissionRate() < 0) {
            throw new DaoException("نسبة / قيمة العمولة لا يمكن أن تكون أقل من صفر");
        }

        if (commission.getCommissionAmount() < 0) {
            throw new DaoException("قيمة العمولة لا يمكن أن تكون أقل من صفر");
        }

        if (commission.getPaymentStatus() == null || commission.getPaymentStatus().isBlank()) {
            commission.setPaymentStatus("UNPAID");
        }

        if (!getPaymentStatuses().contains(commission.getPaymentStatus())) {
            throw new DaoException("حالة الدفع غير صحيحة");
        }

        if (commission.getPaidAmount() < 0) {
            throw new DaoException("المدفوع لا يمكن أن يكون أقل من صفر");
        }

        if (commission.getPaidAmount() > commission.getCommissionAmount()) {
            throw new DaoException("المدفوع لا يمكن أن يكون أكبر من قيمة العمولة");
        }
    }

    private void validateDates(LocalDate dateFrom, LocalDate dateTo) throws DaoException {
        if (dateFrom == null) {
            throw new DaoException("من فضلك اختر تاريخ البداية");
        }

        if (dateTo == null) {
            throw new DaoException("من فضلك اختر تاريخ النهاية");
        }

        if (dateTo.isBefore(dateFrom)) {
            throw new DaoException("تاريخ النهاية يجب أن يكون أكبر من أو يساوي تاريخ البداية");
        }
    }

    private Integer normalizeId(Integer id) {
        return id == null || id <= 0 ? null : id;
    }
}
