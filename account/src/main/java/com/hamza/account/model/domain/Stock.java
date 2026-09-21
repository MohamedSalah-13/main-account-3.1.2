package com.hamza.account.model.domain;

/**
 * A warehouse: its id, its name, where it is, and whether it is still in use.
 * <p>
 * A plain object since phase E1 of {@code docs/warehouse-plan.md}. It extended
 * {@code DForColumnTable} and held its id and name in JavaFX properties that nothing bound to,
 * and that base class captured whoever was signed in when the object was <em>constructed</em> -
 * which {@code StockDao} then wrote as the warehouse's creator. Who creates a warehouse is the
 * service's to say ({@code StockService.save}), at the moment it saves one.
 *
 * @see com.hamza.account.features.items.StockScope for which warehouses a picker offers
 */
public class Stock {

    private int id;
    private String name;
    private String address;
    /** {@code stocks.is_active} (V77): a switched-off warehouse keeps its history and takes no new movement. */
    private boolean active = true;
    /** Who created it - {@code stocks.user_id}; zero until the service stamps it. */
    private int userId;

    public Stock(int id) {
        this.id = id;
    }

    public Stock(String name) {
        this.name = name;
    }

    public Stock(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public Stock(int id, String name, String address) {
        this(id, name);
        this.address = address;
    }

    public Stock(int id, String name, String address, boolean active) {
        this(id, name, address);
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }
}
