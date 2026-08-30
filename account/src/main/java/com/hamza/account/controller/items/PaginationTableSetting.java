package com.hamza.account.controller.items;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.service.ItemsService;
import com.hamza.controlsfx.database.DaoException;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.List;

@Log4j2
@RequiredArgsConstructor
public class PaginationTableSetting {

    private final TableView<ItemsModel> tableView;
    private final ItemsService itemsService;
    private final TextField txtSearch;
    private final Pagination pagination;
    private final int ROWS_PER_PAGE = 50;
    private Integer mainGroupId;
    private Integer subGroupId;
    private String searchText = "";
    private boolean initialized;

    public void initializePagination() {
        if (!initialized) {
            initialized = true;
            pagination.setPageFactory(pageIndex -> {
                updateTableView(pageIndex);
                return tableView;
            });
            PauseTransition pause = new PauseTransition(Duration.millis(500));
            txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
                pause.setOnFinished(event -> {
                    searchText = newValue == null ? "" : newValue.trim();
                    refresh();
                });
                pause.playFromStart();
            });
        }
        refresh();
    }

    public void setGroupFilter(Integer mainGroupId, Integer subGroupId) {
        this.mainGroupId = mainGroupId;
        this.subGroupId = subGroupId;
        refresh();
    }

    private void refresh() {
        try {
            if (!searchText.isBlank()) {
                pagination.setPageCount(1);
                pagination.setCurrentPageIndex(0);
                loadDataFromDB();
                return;
            }
            int totalItems = itemsService.getCountItems(mainGroupId, subGroupId);
            pagination.setPageCount(Math.max(1, (totalItems + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE));
            pagination.setCurrentPageIndex(0);
            updateTableView(0);
        } catch (DaoException e) {
            log.error("Failed to refresh the items table", e);
        }
    }

    private void updateTableView(int pageIndex) {
        try {
            int offset = pageIndex * ROWS_PER_PAGE;
            // هنا الكود الحقيقي لجلب البيانات من قاعدة البيانات
            if (!searchText.isBlank()) {
                loadDataFromDB();
                return;
            }
            List<ItemsModel> data = itemsService.getProducts(ROWS_PER_PAGE, offset, mainGroupId, subGroupId);
            tableView.setItems(FXCollections.observableArrayList(data));
        } catch (DaoException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadDataFromDB() throws DaoException {
        var filterItems = itemsService.getFilterItems(searchText).stream()
                .filter(this::matchesSelectedGroup)
                .toList();
        tableView.setItems(FXCollections.observableArrayList(filterItems));
    }

    private boolean matchesSelectedGroup(ItemsModel item) {
        if (subGroupId != null) return item.getSubGroups() != null && item.getSubGroups().getId() == subGroupId;
        return mainGroupId == null || (item.getSubGroups() != null
                && item.getSubGroups().getMainGroups() != null
                && item.getSubGroups().getMainGroups().getId() == mainGroupId);
    }
}
