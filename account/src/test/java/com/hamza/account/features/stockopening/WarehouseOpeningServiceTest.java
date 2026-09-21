package com.hamza.account.features.stockopening;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * What {@link WarehouseOpeningService} decides before it opens a transaction. What it decides inside
 * one - a switched-off warehouse, a figure changed underneath, an item that has moved - reads rows
 * and locks them, and {@code WarehouseOpeningDatabaseAcceptanceTest} holds it on MySQL.
 * <p>
 * Signed in as an ordinary user: user 1 bypasses every permission.
 */
class WarehouseOpeningServiceTest {

    private static final int BRANCH = 2;

    private DaoFactory daoFactory;
    private WarehouseOpeningDao dao;
    private WarehouseOpeningService service;
    private UserSessionContext session;

    @BeforeEach
    void setUp() {
        session = new UserSessionContext();
        ServiceRegistry.register(UserSessionContext.class, session);
        daoFactory = mock(DaoFactory.class);
        dao = mock(WarehouseOpeningDao.class);
        when(daoFactory.warehouseOpeningDao()).thenReturn(dao);
        service = new WarehouseOpeningService(daoFactory);
    }

    @Test
    @DisplayName("reading the list needs stock.show, and nothing is read without it")
    void readingNeedsStockShow() {
        signInWith(AppPermissions.ITEMS_UPDATE);

        assertThrows(BusinessRuleException.class, () -> service.page(WarehouseOpeningFilter.firstPage(BRANCH, null, false)));
        verifyNoInteractions(dao);
    }

    @Test
    @DisplayName("the list is the DAO's page with its count")
    void theListIsRead() throws Exception {
        signInWith(AppPermissions.STOCK_SHOW);
        WarehouseOpeningFilter filter = WarehouseOpeningFilter.firstPage(BRANCH, null, false);
        when(dao.page(filter)).thenReturn(List.of(new WarehouseOpeningRow(1, "a", "a", "u", 0, false)));
        when(dao.count(filter)).thenReturn(1L);

        WarehouseOpeningPage page = service.page(filter);

        assertEquals(1, page.rows().size());
        assertEquals(1, page.items());
    }

    /** The item screen's opening field is under items.update; the same fact entered here is too. */
    @Test
    @DisplayName("saving needs items.update, and touches nothing without it")
    void savingNeedsItemsUpdate() {
        signInWith(AppPermissions.STOCK_SHOW, AppPermissions.STOCK_UPDATE);

        assertThrows(BusinessRuleException.class,
                () -> service.save(BRANCH, List.of(new WarehouseOpeningDraft.Change(1, "a", 0, 5))));
        verifyNoInteractions(daoFactory);
    }

    @Test
    @DisplayName("a figure below zero or beyond the column is refused before anything is read")
    void impossibleFiguresAreRefusedFirst() {
        signInWith(AppPermissions.ITEMS_UPDATE);

        assertThrows(UserValidationException.class,
                () -> service.save(BRANCH, List.of(new WarehouseOpeningDraft.Change(1, "a", 0, -1))));
        assertThrows(UserValidationException.class, () -> service.save(BRANCH,
                List.of(new WarehouseOpeningDraft.Change(1, "a", 0, WarehouseOpeningService.MAX_OPENING + 1))));
        verifyNoInteractions(daoFactory);
    }

    @Test
    @DisplayName("nothing typed is nothing saved, without a transaction")
    void nothingToSave() throws Exception {
        signInWith(AppPermissions.ITEMS_UPDATE);

        assertEquals(0, service.save(BRANCH, List.of()));
        verifyNoInteractions(daoFactory);
    }

    private void signInWith(PermissionKey... permissions) {
        session.signIn(2, "soha", Set.of(permissions));
    }
}
