package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.type.UsersType;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Error_Text_Show;

import java.time.LocalDate;
import java.util.List;

public record EmployeeService(DaoFactory daoFactory) {

    public List<Employees> getEmployeesList() throws DaoException {
        return daoFactory.employeesDao().loadAll();
    }

    public List<String> getEmployeeNames() throws DaoException {
        return getEmployeesList()
                .stream()
                .map(Employees::getName)
                .toList();
    }

    public List<Employees> getDelegateList() throws DaoException {
        return getEmployeesList().stream()
                .filter(users -> users.getJob_id() == UsersType.DELEGATE)
                .toList();
    }

    public List<String> getDelegateNames() throws DaoException {
        return getDelegateList()
                .stream()
                .map(Employees::getName)
                .toList();
    }

    public Employees getDelegateByName(String name) throws DaoException {
        return daoFactory.employeesDao().getDataByString(name);
    }

    public Employees getDelegateById(int id) throws DaoException {
        return daoFactory.employeesDao().getDataById(id);
    }

    public int deleteEmployee(int id) throws DaoException {
        if (id == 1) throw new DaoException(Error_Text_Show.CANT_DELETE);
        return daoFactory.employeesDao().deleteById(id);
    }

    public int updateEmployee(int id, String name, LocalDate birth_date, LocalDate hire_date, double salary, String email, String tel, String address, UsersType job_id) throws DaoException {
        var employees = new Employees(id, name, birth_date, hire_date, salary, email, tel, address, job_id);
        if (id == 0)
            return daoFactory.employeesDao().insert(employees);

        else return daoFactory.employeesDao().update(employees);
    }
}
