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

    public void initializePagination() {
        int totalItems = itemsService.getCountItems(); // database.getCount();
        int pageCount = (totalItems / ROWS_PER_PAGE) + 1;
        pagination.setPageCount(pageCount);
        // 3. تحديد ماذا يحدث عند تغيير الصفحة (Factory)
        pagination.setPageFactory((pageIndex) -> {
            updateTableView(pageIndex);
            return tableView; // نعيد الجدول ليتم عرضه داخل صفحة الـ Pagination
        });


        PauseTransition pause = new PauseTransition(Duration.millis(500));
        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            pause.setOnFinished(event -> {
                try {
                    loadDataFromDB(newValue); // لا يتم الاستدعاء إلا بعد التوقف عن الكتابة
                } catch (DaoException e) {
                    log.error(this.getClass().getName(), e.getMessage());
                }
            });
            pause.playFromStart();
        });
    }

    private void updateTableView(int pageIndex) {
        try {
            int offset = pageIndex * ROWS_PER_PAGE;
            // هنا الكود الحقيقي لجلب البيانات من قاعدة البيانات
            List<ItemsModel> data = itemsService.getProducts(ROWS_PER_PAGE, offset);
            tableView.setItems(FXCollections.observableArrayList(data));
        } catch (DaoException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadDataFromDB(String newValue) throws DaoException {
        var filterItems = itemsService.getFilterItems(newValue);
        tableView.setItems(FXCollections.observableArrayList(filterItems));
    }
}
