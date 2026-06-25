package com.hamza.account.service;

import com.hamza.account.database.DaoException;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.DelegateProfile;
import lombok.extern.log4j.Log4j2;

import java.util.List;

@Log4j2
public class DelegateService {

    private final DaoFactory daoFactory;

    public DelegateService(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    public List<DelegateProfile> getAllDelegates() throws DaoException {
        return daoFactory.delegateDao().loadAll();
    }

    public List<DelegateProfile> getActiveDelegates() throws DaoException {
        return daoFactory.delegateDao().loadActiveDelegates();
    }

    public List<String> getDelegateNames() throws DaoException {
        return daoFactory.delegateDao().getDelegateNames();
    }

    public DelegateProfile getDelegateByName(String delegateName) throws DaoException {
        if (delegateName == null || delegateName.isBlank()) {
            return null;
        }
        return daoFactory.delegateDao().getDelegateByName(delegateName);
    }

    public DelegateProfile getDelegateByEmployeeId(int employeeId) throws DaoException {
        if (employeeId <= 0) {
            return null;
        }
        return daoFactory.delegateDao().getDelegateByEmployeeId(employeeId);
    }

    public int saveOrUpdate(DelegateProfile delegateProfile) throws DaoException {
        validate(delegateProfile);
        return daoFactory.delegateDao().saveOrUpdate(delegateProfile);
    }

    public int insert(DelegateProfile delegateProfile) throws DaoException {
        validate(delegateProfile);
        return daoFactory.delegateDao().insert(delegateProfile);
    }

    public int update(DelegateProfile delegateProfile) throws DaoException {
        validate(delegateProfile);
        return daoFactory.delegateDao().update(delegateProfile);
    }

    public int deleteByProfileId(int profileId) throws DaoException {
        if (profileId <= 0) {
            throw new DaoException("اختر مندوبًا أولاً");
        }
        return daoFactory.delegateDao().deleteById(profileId);
    }

    public int deleteByEmployeeId(int employeeId) throws DaoException {
        if (employeeId <= 0) {
            throw new DaoException("اختر مندوبًا أولاً");
        }
        return daoFactory.delegateDao().deleteByEmployeeId(employeeId);
    }

    public List<DelegateProfile> search(String text) throws DaoException {
        List<DelegateProfile> delegates = getAllDelegates();

        if (text == null || text.isBlank()) {
            return delegates;
        }

        String searchText = text.trim().toLowerCase();

        return delegates.stream()
                .filter(delegate ->
                        contains(delegate.getDelegateName(), searchText)
                                || contains(delegate.getAreaName(), searchText)
                                || contains(delegate.getSupervisorName(), searchText)
                                || contains(delegate.getTel(), searchText)
                )
                .toList();
    }

    public List<String> getCommissionTypes() {
        return List.of(
                "NONE",
                "SALES_PERCENT",
                "PROFIT_PERCENT",
                "FIXED_PER_INVOICE"
        );
    }

    private void validate(DelegateProfile delegateProfile) throws DaoException {
        if (delegateProfile == null) {
            throw new DaoException("لا توجد بيانات للحفظ");
        }

        if (delegateProfile.getDelegateId() <= 0) {
            throw new DaoException("من فضلك اختر المندوب");
        }

        if (delegateProfile.getCommissionType() == null || delegateProfile.getCommissionType().isBlank()) {
            delegateProfile.setCommissionType("NONE");
        }

        if (!getCommissionTypes().contains(delegateProfile.getCommissionType())) {
            throw new DaoException("نوع العمولة غير صحيح");
        }

        if (delegateProfile.getCommissionValue() < 0) {
            throw new DaoException("قيمة العمولة لا يمكن أن تكون أقل من صفر");
        }

        if (delegateProfile.getCollectionTarget() < 0) {
            throw new DaoException("هدف التحصيل لا يمكن أن يكون أقل من صفر");
        }

        if (delegateProfile.getCreditLimit() < 0) {
            throw new DaoException("حد الائتمان لا يمكن أن يكون أقل من صفر");
        }
    }

    private boolean contains(String source, String searchText) {
        return source != null && source.toLowerCase().contains(searchText);
    }
}
