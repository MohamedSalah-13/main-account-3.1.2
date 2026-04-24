# 📘 User Shifts System Documentation

> **Last updated:** 2026-04-24
> **Current status:** Phase 1 ✅ — Phase 2 ✅ — Phase 3 ✅ — Phase 4/B ✅ — Phase 4/A 🟡 (started)  
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
- Aggregate shift reports can now be filtered by date range and user.
- Aggregate reports are rendered from `List<UserShift>` using a proper Jasper bean datasource.

---

## Phase 4/A — Permissions and Administration 🟡

**Goal:** prepare admin-level shift control.

### Current progress

| # | Task | Status | Notes |
|---|------|:------:|------|
| 1 | Start administrative shift flow | 🟡 | Planning started |
| 2 | Add `SHIFT_MANAGER` permission | ⏳ | Not yet completed |
| 3 | Force Close UI | ⏳ | Not yet completed |
| 4 | Audit log support | ⏳ | Not yet completed |

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

### Next step

- Continue with **Phase 4/A**:
    - add `SHIFT_MANAGER`
    - build admin shift screen
    - add force close action

---

## Changelog

| Date | Phase | Description |
|------|:-----:|---|
| 2026-04-17 | 1 | Critical fixes + domain cleanup + SQL injection fix |
| 2026-04-17 | 2 | Financial linking + ShiftSummary + X/Z reports |
| 2026-04-18 | 4/B | Central operation guard in POS + Invoice + Expenses |
| 2026-04-24 | 3 | Aggregate reports added: DAO, Service, Controller, FXML, Jasper template |