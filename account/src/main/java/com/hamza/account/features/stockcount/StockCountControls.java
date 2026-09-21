package com.hamza.account.features.stockcount;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;

import java.util.function.Predicate;

/**
 * Which of the count screen's controls may be used: the sheet's own state and the reader's
 * permissions, decided together.
 * <p>
 * The screen used to ask the sheet alone. So a reader holding {@code stock.count.show} and nothing
 * more - enough to open the screen and read past counts - was offered Save and Post on the sheet in
 * front of them, scanned a line onto it, and was refused only when pressing Save. Seen on screen,
 * signed in as an ordinary user, 2026-09-21. The permissions are a hint in the {@code isGranted}
 * sense; {@code StockCountService} still asks for each one itself.
 *
 * @param scan       the scan box and the remove-line button: building a sheet nobody may save is work
 *                   thrown away, so they follow Save
 * @param save       {@code stock.count.create}
 * @param post       {@code stock.count.post}
 * @param discard    {@code stock.count.create}, and only a sheet that has been saved
 */
public record StockCountControls(boolean scan, boolean save, boolean post, boolean discard) {

    /**
     * @param editable whether the sheet is still a draft ({@code StockCount.isEditable})
     * @param saved    whether the draft exists in the database - a new one has nothing to discard
     * @param busy     whether a save or a post is running
     * @param granted  {@code AuthorizationGuard::isGranted} in the application
     */
    public static StockCountControls of(boolean editable, boolean saved, boolean busy,
                                        Predicate<PermissionKey> granted) {
        boolean mayCreate = granted.test(AppPermissions.STOCK_COUNT_CREATE);
        boolean mayPost = granted.test(AppPermissions.STOCK_COUNT_POST);
        boolean open = editable && !busy;
        // Scanning is not held while a save runs - it never was - only by the sheet and the key.
        return new StockCountControls(editable && mayCreate, open && mayCreate, open && mayPost,
                open && mayCreate && saved);
    }
}
