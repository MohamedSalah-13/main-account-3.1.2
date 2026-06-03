package com.hamza.account.controller.users;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.interfaces.api.DataTable;
import com.hamza.account.model.domain.Users;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.service.UsersService;
import com.hamza.account.table.ActionButtonToolBar;
import com.hamza.account.table.TableInterface;
import com.hamza.account.database.DaoException;
import com.hamza.controlsfx.observer.Publisher;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.TableView;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Log4j2
public class UserController implements TableInterface<Users> {

    private final String title;
    private final Publisher<String> publisherAddUser;
    private final UsersService usersService;
    private TableView<Users> table;

    public UserController(DataPublisher dataPublisher
            , String title) {
        this.title = title;
        this.publisherAddUser = dataPublisher.getPublisherAddUser();
        this.usersService = ServiceRegistry.get(UsersService.class);
    }

    @Override
    public String titleName() {
        return title;
    }

    @Override
    public ActionButtonToolBar<Users> actionButton() {
        return new ActionButtonToolBar<>() {
            @Override
            public void openNew() throws Exception {
                openAddUser(0);
            }

            @Override
            public void update(Users users) throws Exception {
                openAddUser(users.getId());
            }

            @Override
            public int delete(Users users) throws DaoException {
                return usersService.delete(users.getId());
            }

            @Override
            public void afterDelete() {
                UserController.this.publisherAddUser.notifyObservers();
            }
        };
    }

    @Override
    public DataTable<Users> table_data() {
        return new DataTable<>() {
            @Override
            public void getTable(TableView<Users> tableView) {
                UserController.this.table = tableView;
                // dont show column pass
                tableView.setTableMenuButtonVisible(true);
                tableView.getColumns().get(2).setVisible(false);
            }

            @Override
            public List<Users> dataList() throws Exception {
                return usersService.getUsersList();
            }

            @Override
            public @NotNull Class<? super Users> classForColumn() {
                return Users.class;
            }

        };
    }

    @Override
    public BooleanProperty getColumnSelected(Users users) {
        return users.getSelectedRow();
    }

    @Override
    public Publisher<String> publisherTable() {
        return publisherAddUser;
    }


    @Override
    public List<Users> getProducts(int rowsPerPage, int offset) throws Exception {
        return usersService.getProducts(rowsPerPage, offset);
    }

    @Override
    public List<Users> getFilterItems(String newValue) throws DaoException {
        return usersService.getFilterUsers(newValue);
    }

    @Override
    public int getCountItems() {
        return usersService.getCountItems();
    }

    private void openAddUser(int code) throws Exception {
        new AddForAllApplication(0, new AddUserController(code, publisherAddUser));
    }

}
