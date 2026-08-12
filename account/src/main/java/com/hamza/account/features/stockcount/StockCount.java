package com.hamza.account.features.stockcount;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A physical count of the shop: a dated document with lines, which posts its
 * differences into the stock once and is then read-only.
 * <p>
 * This is what replaces correcting a balance by editing {@code items.first_balance}.
 * That rewrote what the opening balance <em>was</em>, so a report of any earlier
 * period silently changed with it, and nothing anywhere recorded that a correction had
 * been made or by whom. A count is a movement like a purchase or a sale, and the
 * inventory sheet shows it in its own column.
 */
public class StockCount {

    private final List<StockCountLine> lines = new ArrayList<>();

    private int id;
    private int stockId;
    private LocalDate countDate = LocalDate.now();
    private StockCountStatus status = StockCountStatus.DRAFT;
    private String notes = "";
    private LocalDateTime postedAt;
    private int userId = 1;

    public boolean isNew() {
        return id == 0;
    }

    public boolean isPosted() {
        return status.isPosted();
    }

    /**
     * Whether this count may still be changed. Everything the screen disables hangs off
     * this rather than off {@code status} directly, so there is one answer to the
     * question.
     */
    public boolean isEditable() {
        return !isPosted();
    }

    /** The lines that would actually move something. */
    public List<StockCountLine> linesWithDifference() {
        return lines.stream().filter(StockCountLine::hasDifference).toList();
    }

    /** Net movement across the whole sheet, in base units. */
    public double netDifference() {
        return lines.stream().mapToDouble(StockCountLine::difference).sum();
    }

    public List<StockCountLine> getLines() {
        return lines;
    }

    public void setLines(List<StockCountLine> newLines) {
        lines.clear();
        lines.addAll(newLines);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getStockId() {
        return stockId;
    }

    public void setStockId(int stockId) {
        this.stockId = stockId;
    }

    public LocalDate getCountDate() {
        return countDate;
    }

    public void setCountDate(LocalDate countDate) {
        this.countDate = countDate;
    }

    public StockCountStatus getStatus() {
        return status;
    }

    public void setStatus(StockCountStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes == null ? "" : notes;
    }

    public LocalDateTime getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(LocalDateTime postedAt) {
        this.postedAt = postedAt;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }
}
