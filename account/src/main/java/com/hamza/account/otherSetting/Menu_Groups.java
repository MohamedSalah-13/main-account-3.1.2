package com.hamza.account.otherSetting;

import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.menu.ActionTable;
import com.hamza.controlsfx.menu.ContextMenuTree;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class Menu_Groups extends TreeCell<String> {

    private final ContextMenuTree menuForRootGroups;
    private ContextMenuTree menu_main_group;
    private TextField textField;

    public Menu_Groups(TreeItem<String> rootItem) {
        menuForRootGroups = new ContextMenuTree(null, rootItem, true, new ActionTable() {
            @Override
            public void actionAdd() {

            }

            @Override
            public void actionUpdate() {

            }

            @Override
            public void actionDelete() {

            }

            @Override
            public void actionRefresh() {

            }
        });

    }

    private void menu_main_group_setting(TreeItem<String> rootItem) {
        menu_main_group = new ContextMenuTree(null, rootItem, true, new ActionTable() {
            @Override
            public void actionAdd() {

            }

            @Override
            public void actionUpdate() {

            }

            @Override
            public void actionDelete() {

            }

            @Override
            public void actionRefresh() {

            }
        });
        // إضافة مجموعة فرعية
    }

    private String getString() {
        return getItem() == null ? "" : getItem();
    }

    private void createTextField() throws DaoException {
        textField = new TextField(getString());
        textField.setOnKeyReleased((KeyEvent t) -> {
            if (t.getCode() == KeyCode.ENTER) {
                boolean saveOrUpdate = false;
                commitEdit(textField.getText());
            } else if (t.getCode() == KeyCode.ESCAPE) {
                cancelEdit();
            }
        });
    }

    @Override
    public void startEdit() {
        super.startEdit();
        if (textField == null) {
            try {
                createTextField();
            } catch (DaoException e) {
                log.error(e);
            }
        }
        setText(null);
        setGraphic(textField);
        textField.selectAll();
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setText(getItem());
        setGraphic(getTreeItem().getGraphic());
    }

    @Override
    public void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        // TreeView في حال تم إلغاؤه قلنا أنه سيتم مسحه من الـ
        if (empty) {
            setText(null);
            setGraphic(null);
        }
        // في حال تم التفاعل معه بأي طريقة
        else {
            // مكانه ثم تحديث النافذة لإظهار النص الجديد TextField في حال كان يتم تعديل نصه سيتم وضع النص الذي تم إدخاله في الـ
            if (isEditing()) {
                if (textField != null) {
                    textField.setText(getString());
                }
                setText(null);
                setGraphic(textField);
            }
            // في حال تم النقر عليه بواسطة زر الفأرة الأيمن
            else {
                // سيتم تحديث النافذة لضمان أن يتم إظهاره بشكل صحيح
                setText(getString());
                setGraphic(getTreeItem().getGraphic());

                // TreeView ثم سيتم إظهار القائمة الخاصة بالشركة إذا تم النقر على أول شيء في الـ
                if (getTreeItem().getParent() == null) {
                    setContextMenu(menuForRootGroups);
                }
                // TreeView أو سيتم إظهار القائمة الخاصة بالأقسام إذا تم النقر على أي شيء موضوع تحت الشركة في الـ
                else if (getTreeItem().getParent().getParent() == null) {
                    setContextMenu(menu_main_group);
                }

            }
        }
    }
}
