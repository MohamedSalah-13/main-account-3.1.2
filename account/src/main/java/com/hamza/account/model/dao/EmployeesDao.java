package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Employees;
import com.hamza.account.type.UsersType;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class EmployeesDao extends AbstractDao<Employees> {

    public static final String COLUMN_NAME = "column_name";
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

    public EmployeesDao(Connection connection) {
        super(connection);
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

    public List<Employees> loadAllDelegate() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(EMPLOYEES).concat(" where job = 4"), this::map);
    }
}
