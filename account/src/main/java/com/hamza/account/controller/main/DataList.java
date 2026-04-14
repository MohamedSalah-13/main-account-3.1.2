package com.hamza.account.controller.main;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Log4j2
public class DataList extends DataTask {
    private static final Map<Class<?>, List<?>> dataLists = new ConcurrentHashMap<>();
    private static DaoFactory daoFactory;
    @Setter
    @Getter
    private static List<ItemsModel> itemsModelList = new ArrayList<>();

    public DataList(DaoFactory daoFactory) {
        DataList.daoFactory = daoFactory;
        Field[] declaredFields = DataList.class.getDeclaredFields();
        this.length = declaredFields.length;
        initializeLists();
    }

    /**
     * طريقة عامة لتحميل البيانات مع معالجة الأخطاء
     */
    protected static <T> void loadData(Supplier<List<T>> dataLoader, java.util.function.Consumer<List<T>> dataSetter, String dataName) {
        try {
            List<T> data = dataLoader.get();
            dataSetter.accept(data);
            log.debug("Successfully loaded {}: {} records", dataName, data.size());
        } catch (Exception e) {
            log.error("Error loading {}: {}", dataName, e.getMessage(), e);
            dataSetter.accept(new ArrayList<>());
        }
    }

    public static void get2ItemsLoad() {
        loadData(
                () -> listData(daoFactory.getItemsDao()),
                DataList::setItemsModelList,
                "items"
        );
    }

    private void initializeLists() {
        Arrays.stream(DataList.class.getDeclaredFields())
                .filter(field -> List.class.isAssignableFrom(field.getType()))
                .forEach(field -> dataLists.put(field.getType(), new ArrayList<>()));
    }

}