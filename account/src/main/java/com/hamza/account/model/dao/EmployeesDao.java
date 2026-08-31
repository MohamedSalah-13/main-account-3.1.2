package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Employees;
import com.hamza.account.type.UsersType;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Blob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EmployeesDao extends AbstractDao<Employees> {

    public static final String COLUMN_NAME = "column_name";
    private static final int FILTER_LIMIT = 50;
    private static final String FILTER_EMPLOYEES_SQL_NUMERIC = """
            SELECT * FROM employees
            WHERE id = ? OR tel = ?
            ORDER BY
                CASE
                    WHEN id = ? THEN 0
                    WHEN tel = ? THEN 1
                    ELSE 2
                END,
                id DESC
            LIMIT %d
            """.formatted(FILTER_LIMIT);
    private static final String FILTER_EMPLOYEES_SQL_TEXT_STARTS = """
            SELECT * FROM employees
            WHERE column_name LIKE ? OR tel LIKE ?
            ORDER BY
                CASE
                    WHEN column_name LIKE ? THEN 0
                    WHEN tel LIKE ? THEN 1
                    ELSE 2
                END,
                id DESC
            LIMIT %d
            """.formatted(FILTER_LIMIT);
    private static final String FILTER_EMPLOYEES_SQL_TEXT_CONTAINS = """
            SELECT * FROM employees
            WHERE column_name LIKE ? OR tel LIKE ?
            ORDER BY id DESC
            LIMIT %d
            """.formatted(FILTER_LIMIT);
    private final String EMPLOYEES = "employees";
    private final String BIRTH_DATE = "birth_date";
    private final String HIRE_DATE = "hire_date";
    private final String SALARY = "salary";
    private final String EMAIL = "email";
    private final String TEL = "tel";
    private final String ADDRESS = "address";
    private final String IMAGE = "image";
    private final String JOB = "job";
    private final String ID = "id";
    private final String USER_ID = "user_id";

    public EmployeesDao() {
        super();
    }

    @Override
    public List<Employees> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(EMPLOYEES), this::map);
    }

    @Override
    public int insert(Employees employees) throws DaoException {
        String insert = SqlStatements.insertStatement(EMPLOYEES, COLUMN_NAME, BIRTH_DATE, HIRE_DATE, SALARY, EMAIL, TEL, ADDRESS, IMAGE, JOB, USER_ID);
        return executeUpdate(insert, getData(employees));
    }

    @Override
    public int update(Employees employees) throws DaoException {
        Object[] strings = new Object[]{employees.nameProperty().get()
                , employees.getBirth_date()
                , employees.getHire_date()
                , employees.getSalary()
                , employees.getEmail()
                , employees.getTel()
                , employees.getAddress()
                , employees.getItem_image() == null ? null : employees.getItem_image()
                , employees.getJob_id().getId()
                , employees.getId()};
        String update = SqlStatements.updateStatement(EMPLOYEES, ID, COLUMN_NAME, BIRTH_DATE, HIRE_DATE, SALARY, EMAIL, TEL, ADDRESS, IMAGE, JOB);
        return executeUpdate(update, strings);
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(EMPLOYEES, ID), id);
    }

    @Override
    public Employees getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(EMPLOYEES, ID), this::map, id);
    }

    @Override
    public Employees getDataByString(String s) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(EMPLOYEES, COLUMN_NAME), this::map, s);
    }

    @Override
    public Object[] getData(Employees employees) throws DaoException {
        return new Object[]{employees.nameProperty().get()
                , employees.getBirth_date()
                , employees.getHire_date()
                , employees.getSalary()
                , employees.getEmail()
                , employees.getTel()
                , employees.getAddress()
                , employees.getItem_image() == null ? null : employees.getItem_image()
                , employees.getJob_id().getId()
                , employees.getUsers().getId()};
    }

    @Override
    public Employees map(ResultSet rs) throws DaoException {
        Employees employees = new Employees();
        try {
            int id = rs.getInt(ID);
            String name = rs.getString(COLUMN_NAME);
            LocalDate birth_date = LocalDate.parse(rs.getDate(BIRTH_DATE).toString());
            LocalDate hire_date = LocalDate.parse(rs.getDate(HIRE_DATE).toString());
            double salary = rs.getDouble(SALARY);
            String email = rs.getString(EMAIL);
            String tel = rs.getString(TEL) == null ? "" : rs.getString(TEL);
            String address = rs.getString(ADDRESS) == null ? "" : rs.getString(ADDRESS);
            Blob blob = rs.getBlob(IMAGE);
            int job = rs.getInt(JOB);

            if (blob != null) {
                employees.setItem_image(blob.getBytes(1, (int) blob.length()));
            }

            employees.setId(id);
            employees.setName(name);
            employees.setBirth_date(birth_date);
            employees.setHire_date(hire_date);
            employees.setSalary(salary);
            employees.setEmail(email);
            employees.setTel(tel);
            employees.setAddress(address);
            employees.setJob_id(UsersType.getUserTypeById(job));

        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return employees;
    }

    /**
     * The delegate job, which is a row in an editable table and a literal in this file.
     * Extracted only so that the two statements below cannot drift apart; that it is a
     * hand-matched id at all is finding 16 of docs/audit-2026-08-31.html and is not fixed
     * here.
     */
    private static final int DELEGATE_JOB = 4;

    public List<Employees> loadAllDelegate() throws DaoException {
        return queryForObjects(
                SqlStatements.selectStatement(EMPLOYEES).concat(" where job = " + DELEGATE_JOB),
                this::map);
    }

    /**
     * Names, without the rest of the row.
     * <p>
     * The combo boxes that need these - the delegate on an invoice, the employee on an
     * expense - used to get them by loading every column of every employee and keeping the
     * name. {@code salary} is one of those columns, so a cashier opening an invoice pulled
     * the whole payroll across the connection to fill a dropdown. Nothing showed it, which
     * is exactly why it lasted: the leak was in what was fetched, not in what was drawn.
     * <p>
     * Ordered by name, which the list it replaces never was. {@code loadAll} is
     * {@code SELECT *} with no {@code ORDER BY}, so the combo's order was whatever the
     * server happened to return - and comparing the two against a real MySQL is how that
     * surfaced: the projection can be answered from an index on the name where
     * {@code SELECT *} walks the primary key, and the same rows came back in a different
     * order. An undefined order is being replaced with a defined one, not a stable order
     * with a different one; alphabetical is what a person picking a name from a dropdown
     * expects, and it cannot drift with the query plan.
     */
    public List<String> loadAllNames() throws DaoException {
        return names("SELECT " + COLUMN_NAME + " FROM " + EMPLOYEES + " ORDER BY " + COLUMN_NAME);
    }

    /** The delegates' names, on the same terms as {@link #loadAllNames()}. */
    public List<String> loadAllDelegateNames() throws DaoException {
        return names("SELECT " + COLUMN_NAME + " FROM " + EMPLOYEES
                + " WHERE job = " + DELEGATE_JOB + " ORDER BY " + COLUMN_NAME);
    }

    private List<String> names(String query) throws DaoException {
        return withConnection(connection -> {
            List<String> names = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(query);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    names.add(rows.getString(1));
                }
            } catch (SQLException e) {
                throw new DaoException(e);
            }
            return names;
        });
    }

    public List<Employees> getFilterEmployees(String searchText) throws DaoException {
        if (searchText == null || searchText.trim().isEmpty()) {
            return queryForObjects("SELECT * FROM employees ORDER BY id DESC LIMIT " + FILTER_LIMIT, this::map);
        }

        String q = searchText.trim();
        boolean numericOnly = q.matches("\\d+");

        // 1) بحث رقمي (ID أو هاتف)
        if (numericOnly) {
            int id = -1;
            try {
                id = Integer.parseInt(q);
            } catch (NumberFormatException ignored) {
            }

            return queryForObjects(FILTER_EMPLOYEES_SQL_NUMERIC, this::map, id, q, id, q);
        }

        // 2) بحث نصي (مرحلتين)
        final String likeStarts = q + "%";
        final String likeContains = "%" + q + "%";

        Map<Integer, Employees> result = new java.util.LinkedHashMap<>(FILTER_LIMIT);

        // المرحلة الأولى: StartsWith
        List<Employees> starts = queryForObjects(
                FILTER_EMPLOYEES_SQL_TEXT_STARTS,
                this::map,
                likeStarts, likeStarts, // WHERE
                likeStarts, likeStarts  // ORDER BY
        );

        for (Employees e : starts) {
            if (e != null) result.putIfAbsent(e.getId(), e);
        }

        // المرحلة الثانية: Contains
        if (result.size() < FILTER_LIMIT) {
            List<Employees> contains = queryForObjects(
                    FILTER_EMPLOYEES_SQL_TEXT_CONTAINS,
                    this::map,
                    likeContains, likeContains // WHERE
            );
            for (Employees e : contains) {
                if (e != null) result.putIfAbsent(e.getId(), e);
                if (result.size() >= FILTER_LIMIT) break;
            }
        }

        return new java.util.ArrayList<>(result.values());
    }

    public List<Employees> getProducts(int rowsPerPage, int offset) throws DaoException {
        return queryForObjects("SELECT * FROM employees ORDER BY id DESC LIMIT ? OFFSET ?", this::map, rowsPerPage, offset);
    }

    public int getCountItems() {
        return queryForIntOrDefault("SELECT COUNT(*) FROM employees", 0);
    }
}
