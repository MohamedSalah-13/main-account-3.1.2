package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.config.DefaultStock;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.items.WarehouseStockDao;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.stockcount.StockCount;
import com.hamza.account.features.stockcount.StockCountDao;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.StockDao;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Switching a warehouse off, and when it is refused (V77) - the rules of
 * {@code StockService.setActive}, apart from the screen and the database. Signed in as an ordinary
 * user: user 1 bypasses the permission, and the permission case would pass whatever the service did.
 */
class StockServiceActivityTest {

    private static final int BRANCH = 7;

    private StockDao stockDao;
    private StockCountDao countDao;
    private WarehouseStockDao warehouseStock;
    private StockService service;
    private UserSessionContext session;

    @BeforeEach
    void setUp() throws Exception {
        session = new UserSessionContext();
        ServiceRegistry.register(UserSessionContext.class, session);
        DaoFactory daoFactory = mock(DaoFactory.class);
        stockDao = mock(StockDao.class);
        countDao = mock(StockCountDao.class);
        warehouseStock = mock(WarehouseStockDao.class);
        when(daoFactory.stockDao()).thenReturn(stockDao);
        when(daoFactory.stockCountDao()).thenReturn(countDao);
        when(daoFactory.warehouseStockDao()).thenReturn(warehouseStock);
        service = new StockService(daoFactory);
        signInWith(AppPermissions.STOCK_UPDATE);
    }

    @Test
    @DisplayName("an empty warehouse with no open count is switched off")
    void anEmptyWarehouseIsSwitchedOff() throws Exception {
        service.setActive(BRANCH, false);

        verify(stockDao).updateActive(BRANCH, false);
    }

    @Test
    @DisplayName("the default warehouse is never switched off - every invoice starts from it")
    void theDefaultIsNeverSwitchedOff() throws Exception {
        assertThrows(BusinessRuleException.class, () -> service.setActive(DefaultStock.ID, false));

        verify(stockDao, never()).updateActive(anyInt(), anyBoolean());
    }

    /** It would stay on the books and out of every picker: the shop moves it out with a transfer first. */
    @Test
    @DisplayName("a warehouse still holding stock is not switched off")
    void aWarehouseHoldingStockIsNotSwitchedOff() throws Exception {
        when(warehouseStock.itemsHolding(BRANCH)).thenReturn(3);

        assertThrows(BusinessRuleException.class, () -> service.setActive(BRANCH, false));

        verify(stockDao, never()).updateActive(anyInt(), anyBoolean());
    }

    /** The draft could then be neither posted nor discarded from the screen, whose picker would not offer it. */
    @Test
    @DisplayName("a warehouse with a draft count open is not switched off")
    void aWarehouseWithADraftIsNotSwitchedOff() throws Exception {
        when(countDao.findOpenDraft(BRANCH)).thenReturn(new StockCount());

        assertThrows(BusinessRuleException.class, () -> service.setActive(BRANCH, false));

        verify(stockDao, never()).updateActive(anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("switching back on is never refused, whatever the warehouse holds")
    void switchingOnIsNeverRefused() throws Exception {
        when(warehouseStock.itemsHolding(BRANCH)).thenReturn(3);
        when(countDao.findOpenDraft(BRANCH)).thenReturn(new StockCount());

        service.setActive(BRANCH, true);

        verify(stockDao).updateActive(BRANCH, true);
    }

    @Test
    @DisplayName("without stock.update nothing is read and nothing is written")
    void thePermissionComesFirst() {
        signInWith(AppPermissions.STOCK_SHOW);

        assertThrows(BusinessRuleException.class, () -> service.setActive(BRANCH, false));

        verifyNoInteractions(stockDao, countDao, warehouseStock);
    }

    private void signInWith(PermissionKey... permissions) {
        session.signIn(2, "soha", Set.of(permissions));
    }
}
