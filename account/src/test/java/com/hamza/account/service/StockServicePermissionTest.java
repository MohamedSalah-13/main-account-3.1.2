package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.items.StockScope;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.StockDao;
import com.hamza.account.model.domain.Stock;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Who may read the warehouses, asked as the user the defect of 2026-09-20 was seen with: an
 * imported {@code LEGACY_USER_*} role holding the sales permissions and nothing about
 * warehouses. The session is user 2 on purpose - user 1 bypasses every permission, so a test
 * signed in as the administrator would pass with the guard in either place.
 */
class StockServicePermissionTest {

    private static final int ORDINARY_USER = 2;

    private final Stock inUse = new Stock(1, "main", null, true);
    private final Stock switchedOff = new Stock(2, "old branch", null, false);
    private final List<Stock> warehouses = List.of(inUse, switchedOff);
    private StockService service;
    private UserSessionContext session;

    @BeforeEach
    void setUp() throws Exception {
        session = new UserSessionContext();
        ServiceRegistry.register(UserSessionContext.class, session);
        DaoFactory daoFactory = mock(DaoFactory.class);
        StockDao stockDao = mock(StockDao.class);
        when(daoFactory.stockDao()).thenReturn(stockDao);
        when(stockDao.loadAll()).thenReturn(warehouses);
        service = new StockService(daoFactory);
    }

    @Test
    void aCashierHoldingOnlyTheSalesPermissionsIsOfferedTheWarehouses() throws Exception {
        signInWith(AppPermissions.SALES_SHOW, AppPermissions.SALES_CREATE,
                AppPermissions.SALES_UPDATE, AppPermissions.SALES_DELETE);

        assertEquals(warehouses, service.stocksForPicker(StockScope.EVERYONE));
    }

    /** A document being written is offered the warehouses in use; history is offered every one (V77). */
    @Test
    void aDocumentIsOfferedOnlyTheWarehousesInUse() throws Exception {
        signInWith(AppPermissions.SALES_SHOW, AppPermissions.SALES_CREATE);

        assertEquals(List.of(inUse), service.stocksForPicker(StockScope.ACTIVE_ONLY));
        assertEquals(warehouses, service.stocksForPicker(StockScope.EVERYONE));
    }

    @Test
    void theSameCashierIsStillRefusedTheWarehousesScreen() {
        signInWith(AppPermissions.SALES_SHOW, AppPermissions.SALES_CREATE);

        assertThrows(BusinessRuleException.class, service::getStocks);
    }

    @Test
    void stockShowOpensTheWarehousesScreen() throws Exception {
        signInWith(AppPermissions.STOCK_SHOW);

        assertEquals(warehouses, service.getStocks());
    }

    private void signInWith(PermissionKey... permissions) {
        session.signIn(ORDINARY_USER, "soha", Set.of(permissions));
    }
}
