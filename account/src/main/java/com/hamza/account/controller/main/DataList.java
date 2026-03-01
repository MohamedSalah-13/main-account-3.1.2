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
//    @Setter
//    @Getter
//    private static List<Total_buy> totalBuys = new ArrayList<>();
//    @Setter
//    @Getter
//    private static List<Total_Sales> totalSales = new ArrayList<>();
//    @Setter
//    @Getter
//    private static List<Total_Buy_Re> totalBuysReturn = new ArrayList<>();
//    @Setter
//    @Getter
//    private static List<Total_Sales_Re> totalSalesReturn = new ArrayList<>();
//    @Setter
//    @Getter
//    private static List<Customers> listCustomers = new ArrayList<>();
//    @Setter
//    @Getter
//    private static List<Suppliers> listSuppliers = new ArrayList<>();
//    @Setter
//    @Getter
//    private static List<Purchase> purchaseList = new ArrayList<>();
//    @Setter
//    @Getter
//    private static List<Sales> salesList = new ArrayList<>();
//    @Setter
//    @Getter
//    private static List<Purchase_Return> purchaseReturnList = new ArrayList<>();
//    @Setter
//    @Getter
//    private static List<Sales_Return> salesReturnList = new ArrayList<>();
//    @Setter
//    @Getter
//    private static List<Processes_Data> processesDataList = new ArrayList<>();
//    @Setter
//    @Getter
//    private static List<Expenses> expensesList = new ArrayList<>();
//    @Setter
//    @Getter
//    private static List<ItemsUnitsModel> itemsUnitsModelList = new ArrayList<>();
//    @Setter
//    @Getter
//    private static List<AddDeposit> addDepositsList = new ArrayList<>();
//    @Setter
//    @Getter
//    private static List<TreasuryModel> treasuryModelList = new ArrayList<>();

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


//    public static void get2TotalBuys() {
//        loadData(
//                () -> listData(daoFactory.totalsPurchaseDao()),
//                DataList::setTotalBuys,
//                "total purchases"
//        );
//    }

//    public static void get2TotalSales() {
//        loadData(
//                () -> listData(daoFactory.totalsSalesDao()),
//                DataList::setTotalSales,
//                "total sales"
//        );
//    }

//    public static void get2TotalBuysReturn() {
//        loadData(
//                () -> listData(daoFactory.totalsBuyReturnDao()),
//                DataList::setTotalBuysReturn,
//                "total purchase returns"
//        );
//    }

//    public static void get2TotalSalesReturn() {
//        loadData(
//                () -> listData(daoFactory.totalsSalesReturnDao()),
//                DataList::setTotalSalesReturn,
//                "total sales returns"
//        );
//    }

//    public static void get2ListCustomers() {
//        loadData(
//                () -> listData(daoFactory.customersDao()),
//                DataList::setListCustomers,
//                "customers"
//        );
//    }

//    public static void get2ListSuppliers() {
//        loadData(
//                () -> listData(daoFactory.getSuppliersDao()),
//                DataList::setListSuppliers,
//                "suppliers"
//        );
//    }

//    public static void get2ItemsUnitsModelList() {
//        loadData(
//                () -> listData(daoFactory.getItemsUnitDao()),
//                DataList::setItemsUnitsModelList,
//                "items units"
//        );
//    }

//    public static void get2PurchaseList() {
//        loadData(
//                () -> listData(daoFactory.purchaseDao()),
//                DataList::setPurchaseList,
//                "purchase"
//        );
//    }

//    public static void get2SalesList() {
//        loadData(
//                () -> listData(daoFactory.salesDao()),
//                DataList::setSalesList,
//                "sales"
//        );
//    }

//    public static void get2SalesReturnList() {
//        loadData(
//                () -> listData(daoFactory.salesReturnsDao()),
//                DataList::setSalesReturnList,
//                "sales return"
//        );
//    }

//    public static void get2PurchaseReturnList() {
//        loadData(
//                () -> listData(daoFactory.purchaseReturnsDao()),
//                DataList::setPurchaseReturnList,
//                "purchase return"
//        );
//    }


//    public static void get2TreasuryModelList() {
//        loadData(
//                () -> listData(daoFactory.treasuryDao()),
//                DataList::setTreasuryModelList,
//                "treasury"
//        );
//    }

//    public static void get2AddDeposit() {
//        loadData(
//                () -> listData(daoFactory.depositDao()),
//                DataList::setAddDepositsList,
//                "deposits"
//        );
//    }

//    public static void get2ExpensesList() {
//        loadData(
//                () -> listData(daoFactory.expensesDao()),
//                DataList::setExpensesList,
//                "expenses"
//        );
//    }

    private void initializeLists() {
        Arrays.stream(DataList.class.getDeclaredFields())
                .filter(field -> List.class.isAssignableFrom(field.getType()))
                .forEach(field -> dataLists.put(field.getType(), new ArrayList<>()));
    }

}