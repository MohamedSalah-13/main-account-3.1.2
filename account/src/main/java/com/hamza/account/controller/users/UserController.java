package com.hamza.account.controller.users;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.interfaces.api.DataTable;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.service.UserPermissionService;
import com.hamza.account.service.UsersService;
import com.hamza.account.table.ActionButtonToolBar;
import com.hamza.account.table.TableInterface;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.OpenApplication;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.button.api.ButtonColumnBoolean;
import com.hamza.controlsfx.button.api.ButtonColumnI;
import com.hamza.controlsfx.button.button_column.ButtonColumn;
import com.hamza.controlsfx.button.button_column.Button_Toggle_Table;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import javafx.beans.property.BooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.TableView;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Log4j2
public class UserController implements TableInterface<Users> {

    private final String title;
    private final Publisher<String> publisherAddUser;
    private final DaoFactory daoFactory;
    private final UsersService usersService;
    private TableView<Users> table;

    public UserController(DaoFactory daoFactory, DataPublisher dataPublisher
            , String title, UsersService usersService) {
        this.daoFactory = daoFactory;
        this.title = title;
        this.publisherAddUser = dataPublisher.getPublisherAddUser();
        this.usersService = usersService;
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

//                TableColumn<Users, String> columnActiveName = new TableColumn<>(Setting_Language.WORD_CASE);
//                columnActiveName.setCellValueFactory(f -> f.getValue().getActivity().typeProperty());
//                table.getColumns().add(columnActiveName);

                // add button evaluation
                table.getColumns().add(new ButtonColumn<>(getButtonColumnI()));
                // add button toggle
                table.getColumns().add(getUsersButtonToggleTable());

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
    public UserPermissionType permAdd() {
        return null;
    }

    @Override
    public UserPermissionType permUpdate() {
        return null;
    }

    @Override
    public UserPermissionType permDelete() {
        return null;
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

    private ButtonColumnI getButtonColumnI() {
        return new ButtonColumnI() {
            @Override
            public void action(int i) throws Exception {
                int id = table.getItems().get(i).getId();
                String name_user = table.getItems().get(i).getUsername();
                new OpenApplication<>(new UserPermissionController(id, name_user, new UserPermissionService(daoFactory)));
            }

            @NotNull
            @Override
            public String columnTitle() {
                return "";
            }

            @Override
            public boolean isButtonDisabled(int index) {
                if (table.getItems().get(index).getId() == 1)
                    return true;
                return !table.getItems().get(index).isActive();
            }

            @NotNull
            @Override
            public String textName() {
                return "";
            }

            @Override
            public Node imageNode() {
                return new ImageDesign(new Image_Setting().evaluation);
            }
        };
    }

    @NotNull
    private Button_Toggle_Table<Users> getUsersButtonToggleTable() {
        return new Button_Toggle_Table<>(new ButtonColumnBoolean() {

            @NotNull
            @Override
            public String textName() {
                return "";
            }

            @Override
            public void action(int index, boolean b) throws Exception {
                int id = table.getItems().get(index).getId();
                if (id != 1) {
                    Users users = new Users();
                    users.setId(id);
                    users.setActive(b);
                    int update = daoFactory.usersDao().updateCase(users);
                    if (update == 1) {
                        publisherAddUser.notifyObservers();
                    }
                }
            }

            @Override
            public boolean selectButton(int index) {
                return table.getItems().get(index).isActive();
            }

            @Override
            public void action(int i) {

            }

            @NotNull
            @Override
            public String columnTitle() {
                return Setting_Language.WORD_CASE;
            }

            @Override
            public boolean isButtonDisabled(int index) {
                return table.getItems().get(index).getId() == 1;
            }
        });
    }

    private void openAddUser(int code) throws Exception {
        new AddForAllApplication(0, new AddUserController(code, publisherAddUser, daoFactory));
    }

}
