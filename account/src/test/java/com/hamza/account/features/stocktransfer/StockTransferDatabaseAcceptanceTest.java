package com.hamza.account.features.stocktransfer;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.model.dao.DaoFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
/** Requires a configured MySQL; detailed database fixture is covered by the invoice acceptance suite. */
@EnabledIfSystemProperty(named="account.db.acceptance", matches="true")
class StockTransferDatabaseAcceptanceTest {
 @Test void commandRequiresAnAuthorizedSessionBeforeWriting() {
  UserSessionContext session=new UserSessionContext(); ServiceRegistry.register(UserSessionContext.class,session);
  assertThrows(Exception.class, () -> new StockTransferService(DaoFactory.INSTANCE).transfer(new StockTransferCommand(1,2,LocalDate.now(),List.of(new StockTransferLine(1,1)),null)));
  session.signIn(1,"admin",List.of(AppPermissions.STOCK_TRANSFER_POST));
  assertTrue(session.hasPermission(AppPermissions.STOCK_TRANSFER_POST));
 }
}