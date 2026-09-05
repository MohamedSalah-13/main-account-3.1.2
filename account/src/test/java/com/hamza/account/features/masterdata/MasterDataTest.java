package com.hamza.account.features.masterdata;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.model.domain.MainGroups;
import com.hamza.account.model.domain.SubGroups;
import com.hamza.account.service.*;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MasterDataTest {
    private final MasterDataRepository repository = mock(MasterDataRepository.class);
    private final MainGroupService mains = mock(MainGroupService.class);
    private final SupGroupService subs = mock(SupGroupService.class);
    private final AreaService areas = mock(AreaService.class);
    private final UnitsService units = mock(UnitsService.class);
    private final MasterDataService service = new MasterDataService(repository, mains, subs, areas, units);
    private final UserSessionContext session = new UserSessionContext();

    @BeforeEach void setUp() { ServiceRegistry.register(UserSessionContext.class, session); }
    @AfterEach void tearDown() { session.signOut(); }
    private void allow(PermissionKey... keys) { session.signIn(27, "operator", List.of(keys)); }

    @Test void blankNameNeverReachesPersistence() {
        allow(AppPermissions.UNITS_CREATE);
        assertThrows(UserValidationException.class, () -> service.save(MasterDataKind.UNIT, 0, "  ", 0, "1"));
        verifyNoInteractions(repository, units);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "NaN", "Infinity", "1e999", "letters", "0.0001", "100000000000"})
    void invalidFactorsAreRefused(String input) {
        assertThrows(UserValidationException.class, () -> MasterDataForm.parse(MasterDataKind.UNIT, 0, "box", 0, input));
    }

    @Test void emptyFactorDefaultsToOneAndNameIsTrimmed() throws Exception {
        var form = MasterDataForm.parse(MasterDataKind.UNIT, 0, " piece ", 0, "");
        assertEquals("piece", form.name()); assertEquals(1, form.factor());
    }

    @Test void subGroupCannotBeSavedWithoutAParent() {
        assertThrows(UserValidationException.class, () -> MasterDataForm.parse(MasterDataKind.SUB, 0, "juice", 0, ""));
    }

    @Test void nameLengthMatchesTheExistingSchema() throws Exception {
        assertThrows(UserValidationException.class, () -> MasterDataForm.parse(MasterDataKind.MAIN, 0, "x".repeat(51), 0, "1"));
        assertEquals(100, MasterDataForm.parse(MasterDataKind.AREA, 0, "x".repeat(100), 0, "1").name().length());
        assertThrows(UserValidationException.class, () -> MasterDataForm.parse(MasterDataKind.AREA, 0, "x".repeat(101), 0, "1"));
    }

    @Test void updateExcludesItsOwnIdFromDuplicateCheck() throws Exception {
        allow(AppPermissions.UNITS_UPDATE);
        when(units.update(8, "carton", 12)).thenReturn(1);
        assertEquals(1, service.save(MasterDataKind.UNIT, 8, " carton ", 0, "12"));
        verify(repository).nameExists(MasterDataKind.UNIT, "carton", 0, 8);
        verify(units).update(8, "carton", 12);
        verify(units, never()).insert(anyString(), anyDouble());
    }

    @Test void duplicatesOutsideTheVisiblePageAreStillRefused() throws Exception {
        allow(AppPermissions.UNITS_CREATE);
        when(repository.nameExists(MasterDataKind.UNIT, "box", 0, 0)).thenReturn(true);
        assertThrows(UserValidationException.class, () -> service.save(MasterDataKind.UNIT, 0, "box", 0, "12"));
        verifyNoInteractions(units);
    }

    @Test void subGroupUsesTheSelectedParentIdRatherThanItsLabel() throws Exception {
        allow(AppPermissions.SUB_GROUP_CREATE);
        MainGroups parent = new MainGroups(); parent.setId(42); parent.setName("drinks");
        when(mains.getMainGroupsById(42)).thenReturn(parent);
        when(subs.insert(any())).thenReturn(1);
        service.save(MasterDataKind.SUB, 0, "juice", 42, "1");
        var row = ArgumentCaptor.forClass(SubGroups.class);
        verify(subs).insert(row.capture());
        assertSame(parent, row.getValue().getMainGroups());
        verify(repository).nameExists(MasterDataKind.SUB, "juice", 42, 0);
    }

    @Test void missingParentDoesNotCreateAnOrphan() throws Exception {
        allow(AppPermissions.SUB_GROUP_CREATE);
        assertThrows(UserValidationException.class, () -> service.save(MasterDataKind.SUB, 0, "juice", 42, "1"));
        verifyNoInteractions(subs);
    }

    @Test void deniedWritesStopBeforeReadingOrWriting() {
        allow(AppPermissions.UNITS_SHOW);
        assertThrows(Exception.class, () -> service.save(MasterDataKind.UNIT, 0, "box", 0, "12"));
        assertThrows(Exception.class, () -> service.delete(MasterDataKind.UNIT, 8));
        verifyNoInteractions(repository, units);
    }

    @Test void deletionsKeepExistingReferenceProtection() throws Exception {
        allow(AppPermissions.UNITS_DELETE);
        when(units.delete(8)).thenThrow(new UserValidationException("in use"));
        assertThrows(UserValidationException.class, () -> service.delete(MasterDataKind.UNIT, 8));
        verify(units).delete(8); verifyNoInteractions(repository);
    }

    @Test void zeroRowsIsNotReportedAsSaved() throws Exception {
        allow(AppPermissions.UNITS_UPDATE);
        assertThrows(UserValidationException.class, () -> service.save(MasterDataKind.UNIT, 8, "box", 0, "12"));
    }

    @Test void parentPickerIsAvailableToSubGroupReadersOnly() throws Exception {
        allow(AppPermissions.SUB_GROUP_SHOW);
        service.search(MasterDataKind.MAIN, "drink", 0, 0);
        verify(repository).search(MasterDataKind.MAIN, "drink", 0, 0);
        assertThrows(Exception.class, () -> service.search(MasterDataKind.UNIT, "", 0, 0));
    }

    @Test void draftsRemainAttachedToTheirOriginalParent() {
        var drafts = new MasterDataDrafts();
        var first = new MasterDataDrafts.Draft(0, "juice", "1", true);
        drafts.put(4, first);
        assertEquals(MasterDataDrafts.Draft.empty(), drafts.get(5));
        drafts.put(5, new MasterDataDrafts.Draft(9, "water", "1", true));
        assertEquals(first, drafts.get(4));
        drafts.clear(5);
        assertTrue(drafts.hasChanges()); assertEquals(first, drafts.get(4));
        drafts.clear(4); assertFalse(drafts.hasChanges());
    }

    @Test void searchEscapesWildcardsAndDoesNotInterpolateUserInput() {
        assertEquals("%a!_b!%!!%", MasterDataQuery.pattern(" a_b%! "));
        String sql = MasterDataQuery.searchSql(MasterDataKind.SUB);
        assertTrue(sql.contains("AND main_id = ?"));
        assertTrue(sql.endsWith("ORDER BY name, id LIMIT ? OFFSET ?"));
        assertFalse(MasterDataQuery.duplicateSql(MasterDataKind.SUB).contains("main_id"));
        assertFalse(MasterDataQuery.duplicateSql(MasterDataKind.MAIN).contains("main_id"));
        assertTrue(MasterDataQuery.searchSql(MasterDataKind.UNIT).contains("value_d AS factor"));
    }
}
