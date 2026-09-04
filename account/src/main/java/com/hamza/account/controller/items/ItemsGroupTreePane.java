package com.hamza.account.controller.items;

import com.hamza.account.config.AppIcon;
import com.hamza.account.model.domain.MainGroups;
import com.hamza.account.model.domain.SubGroups;
import com.hamza.account.service.MainGroupService;
import com.hamza.account.service.SupGroupService;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import lombok.extern.log4j.Log4j2;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/**
 * The group tree on the side of the items screen.
 * <p>
 * Split out of the controller for one reason above the others: <b>it reads the database, and
 * it used to do so on the JavaFX thread</b> - once when the screen opened and again on every
 * {@code GroupsChanged}. The paging beside it was written with great care never to do that,
 * and the tree simply went around it, so a slow connection froze the screen on the way in.
 * Both reads now happen on a background thread and only the assembled tree returns.
 * <p>
 * The groups it reads are handed on to whoever asked, because the filter panel needs the
 * same two lists and a second read would be the same mistake again.
 */
@Log4j2
public final class ItemsGroupTreePane {

    /**
     * One daemon thread shared by every instance, for the same reason
     * {@link PaginationTableSetting} shares one: the tab is opened and closed repeatedly and
     * nothing tells this class when it closes, so a per-instance executor would leak a
     * thread each time.
     */
    private static final ExecutorService READER =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "items-group-tree-reader");
                thread.setDaemon(true);
                return thread;
            });

    private final TreeView<GroupNode> treeView;
    private final MainGroupService mainGroupService;
    private final SupGroupService supGroupService;
    /** Told which group was chosen. Null ids mean the root: every item. */
    private final BiConsumer<Integer, Integer> onGroupSelected;
    /** Handed the two lists as they are read, so nothing else has to read them again. */
    private final BiConsumer<List<MainGroups>, List<SubGroups>> onGroupsLoaded;

    /** True once the tree has been read at all; the pane is hidden by default and loads lazily. */
    private boolean loaded;

    public ItemsGroupTreePane(TreeView<GroupNode> treeView, MainGroupService mainGroupService,
                             SupGroupService supGroupService,
                             BiConsumer<Integer, Integer> onGroupSelected,
                             BiConsumer<List<MainGroups>, List<SubGroups>> onGroupsLoaded) {
        this.treeView = treeView;
        this.mainGroupService = mainGroupService;
        this.supGroupService = supGroupService;
        this.onGroupSelected = onGroupSelected;
        this.onGroupsLoaded = onGroupsLoaded;
    }

    /** What a node in the tree stands for. Null ids on the root mean "no group condition". */
    public record GroupNode(Integer mainGroupId, Integer subGroupId, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Attaches the selection listener once, at setup.
     * <p>
     * Once, and here rather than inside the loader: the old code attached it during the load
     * and carried a boolean to stop itself doing it again, which is a flag standing in for
     * putting the line in the right place.
     */
    public void initialize() {
        treeView.getSelectionModel().selectedItemProperty().addListener((observable, old, selected) -> {
            if (selected == null) return;
            GroupNode node = selected.getValue();
            onGroupSelected.accept(node.mainGroupId(), node.subGroupId());
        });
    }

    /** Reads the groups and rebuilds the tree, unless it has already been read. */
    public void loadOnce() {
        if (loaded) return;
        reload();
    }

    /** Reads the groups and rebuilds the tree, whether or not it has been read before. */
    public void reload() {
        loaded = true;
        READER.execute(() -> {
            try {
                List<MainGroups> mainGroups = mainGroupService.getMainGroupList();
                List<SubGroups> subGroups = supGroupService.getSubGroupsList();
                Platform.runLater(() -> {
                    build(mainGroups, subGroups);
                    onGroupsLoaded.accept(mainGroups, subGroups);
                });
            } catch (Exception e) {
                log.error("Failed to read the item groups for the tree", e);
            }
        });
    }

    private void build(List<MainGroups> mainGroups, List<SubGroups> subGroups) {
        TreeItem<GroupNode> root = new TreeItem<>(new GroupNode(null, null,
                LanguageManager.getInstance().getString("item.group.all")));
        root.setGraphic(icon(AppIcon.MAIN_GROUP, "items-group-root-icon"));
        root.setExpanded(true);

        Map<Integer, TreeItem<GroupNode>> mainNodes = new LinkedHashMap<>();
        for (MainGroups mainGroup : mainGroups) {
            TreeItem<GroupNode> node = new TreeItem<>(
                    new GroupNode(mainGroup.getId(), null, mainGroup.getName()));
            node.setGraphic(icon(AppIcon.MAIN_GROUP, "items-main-group-icon"));
            node.setExpanded(true);
            root.getChildren().add(node);
            mainNodes.put(mainGroup.getId(), node);
        }
        for (SubGroups subGroup : subGroups) {
            if (subGroup.getMainGroups() == null) continue;
            TreeItem<GroupNode> parent = mainNodes.get(subGroup.getMainGroups().getId());
            if (parent == null) continue;
            TreeItem<GroupNode> node = new TreeItem<>(new GroupNode(
                    parent.getValue().mainGroupId(), subGroup.getId(), subGroup.getName()));
            node.setGraphic(icon(AppIcon.SUB_GROUP, "items-sub-group-icon"));
            parent.getChildren().add(node);
        }

        treeView.setRoot(root);
        treeView.getSelectionModel().select(root);
    }

    static FontIcon icon(AppIcon appIcon, String styleClass) {
        FontIcon icon = appIcon.graphic(18);
        icon.getStyleClass().add(styleClass);
        return icon;
    }
}
