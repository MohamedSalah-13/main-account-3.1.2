# 📘 User Shifts System Documentation

> **Last updated:** 2026-04-24
> **Current status:** Phase 1 ✅ — Phase 2 ✅ — Phase 3 ✅ — Phase 4/B ✅ — Phase 4/A 🟡 (in progress)
> **Path:** `docs/shifts.md`

---

## 📑 Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Main Files](#main-files)
4. [Phase 1 — Critical Fixes](#phase-1--critical-fixes)
5. [Phase 2 — Financial Linking](#phase-2--financial-linking)
6. [Phase 4/B — Operation Guard](#phase-4b--operation-guard)
7. [Remaining Phases](#remaining-phases)
8. [Database](#database)
9. [Testing](#testing)
10. [Known Risks](#known-risks)
11. [Roadmap](#roadmap)
12. [Changelog](#changelog)

---

## Overview

The User Shifts System provides:

- Opening a shift with an opening balance.
- Tracking financial activity during the shift:
  - sales
  - sales returns
  - expenses
  - deposits
  - withdrawals
- Closing the shift with automatic variance calculation.
- Generating live X-Reports and closing Z-Reports.
- Preventing financial operations when no shift is open.
- Generating aggregate shift reports for a date range.
- Preparing admin-level shift management and force close flow.

---

## Phase 3 — Reports and Printing ✅

**Goal:** provide live, closing, and aggregate shift reports.

### Completed

| # | Task | Status | Notes |
|---|------|:------:|------|
| 1 | `ShiftReportService.buildXReport(userId)` | ✅ | Live X-Report data is available |
| 2 | `ShiftReportService.buildZReport(shiftId)` | ✅ | Z-Report data is built from a closed shift |
| 3 | `ShiftReportService.buildAggregateReport(from, to, userId)` | ✅ | Returns shifts within a date range |
| 4 | `UserShiftDao.getShiftsBetween(from, to, userId)` | ✅ | Added for aggregate reports |
| 5 | `ShiftReportsController` | ✅ | Added for filtering and printing aggregate reports |
| 6 | `shift-reports-view.fxml` | ✅ | Added UI for aggregate reports |
| 7 | `Print_Reports.printShiftAggregateReport(...)` | ✅ | Uses `JRBeanCollectionDataSource` |
| 8 | `reports/shift-aggregate-A4.jrxml` | ✅ | Added aggregate Jasper template |
| 9 | `reports/shift-x-report-80mm.jrxml` | ✅ | Live shift report template |
| 10 | `reports/shift-z-report-80mm.jrxml` | ✅ | Closing shift report template |

### Result
- Live X-Report is supported.
- Z-Report can be printed after closing a shift.
- Aggregate shift reports can be filtered by date range and user.
- Aggregate reports are rendered from `List<UserShift>` using a proper Jasper bean datasource.

---

## Phase 4/B — Operation Guard ✅

**Goal:** prevent any financial operation without an open shift.

### Completed

| # | Component | Change | Status |
|---|---|---|:------:|
| 1 | `ShiftContext` | `requireOpenShift()` + enforcement toggle | ✅ |
| 2 | `AddExpensesController.insertData()` | Guard added at method start | ✅ |
| 3 | `PosController.saveInvoice()` | Guard added before processing | ✅ |
| 4 | `BuyController2.saveInvoice(boolean print)` | Guard added before validation | ✅ |
| 5 | `PosController.refreshShiftGuardUi()` | Disable pay button + tooltip | ✅ |
| 6 | `AddDepositController.insertData()` | Guard added at method start | ✅ |
| 7 | `AddConvertTreasuryController.insertData()` | Guard added at method start | ✅ |

### Known Limitations
- `btnPay` in POS should still be verified in real usage after shift changes from another window.
- Some additional controllers may still need a review later if new financial save points are added.

---

## Phase 4/A — Permissions and Administration 🟡

**Goal:** prepare admin-level shift control.

### Completed

| # | Task | Status | Notes |
|---|------|:------:|------|
| 1 | Add `SHIFT_MANAGER` enum permission | ✅ | Added to `UserPermissionType` |
| 2 | Add `shift_manager` row to `permission` table | ✅ | Added via migration |
| 3 | Expose `SHIFT_MANAGER` in permissions UI | ✅ | Added to `UserPermissionController` |
| 4 | Add admin shift management screen | ✅ | `AdminShiftsController` + FXML added |
| 5 | Add Force Close flow | ✅ | Force close logic added to admin shift screen |
| 6 | Add entry point from main menu | ✅ | Main menu hook prepared for the screen |

### Current progress
- The admin shift management flow is now available as a functional direction.
- The next session can continue from the admin screen to improve usability and control.

---

## Roadmap

### Ready now

- Open/close shifts with balance and notes
- Automatic financial summary
- Live X-Report + Z-Report on close
- Aggregate shift reports for date ranges
- `ShiftContext` + `ShiftContextLoader`
- Central operation guard
- POS pay button disabled when no shift is open
- Admin shift management screen
- Force close support
- `SHIFT_MANAGER` permission setup

### Next step for the next session

- Continue with **Admin Shift Management enhancements**
  - filtering
  - status-based display
  - user/date search
  - row coloring
  - usability improvements

---

## Changelog

| Date | Phase | Description |
|------|:-----:|---|
| 2026-04-17 | 1 | Critical fixes + domain cleanup + SQL injection fix |
| 2026-04-17 | 2 | Financial linking + ShiftSummary + X/Z reports |
| 2026-04-18 | 4/B | Central operation guard in POS + Invoice + Expenses |
| 2026-04-24 | 3 | Aggregate reports added: DAO, Service, Controller, FXML, Jasper template |
| 2026-04-24 | 4/A | `SHIFT_MANAGER`, permission mapping, admin shifts screen, force close flow |