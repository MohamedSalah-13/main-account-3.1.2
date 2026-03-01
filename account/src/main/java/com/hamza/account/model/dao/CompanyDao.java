package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Company;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import com.hamza.controlsfx.language.Setting_Language;
import lombok.extern.log4j.Log4j2;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Log4j2
public class CompanyDao extends AbstractDao<Company> {

    private final String COMPANY = "company";
    private final String COMP_NAME = "comp_name";
    private final String COMP_TEL = "comp_tel";
    private final String COMP_ADDRESS = "comp_address";
    private final String COMP_TAX = "comp_tax";
    private final String COMP_COMM = "comp_comm";
    private final String COMP_IMAGE = "comp_image";
    private final String COMP_ID = "comp_id";

    CompanyDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<Company> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(COMPANY), this::map);
    }

    @Override
    public int insert(Company company) throws DaoException {
        String query = SqlStatements.insertStatement(COMPANY, COMP_NAME);
        return executeUpdate(query, Setting_Language.COMPANY_NAME);
    }

    @Override
    public int update(Company company) throws DaoException {
        String update = SqlStatements.updateStatement(COMPANY, COMP_ID, COMP_NAME, COMP_TEL, COMP_ADDRESS, COMP_TAX, COMP_COMM, COMP_IMAGE);
        return executeUpdate(update, getData(company));
    }

    @Override
    public Object[] getData(Company company) {
        return new Object[]{company.getName(), company.getTel(), company.getAddress()
                , company.getTax(), company.getCommercial(), company.getImage() == null ? null : company.getImage().length > 0 ? company.getImage() : null
                , company.getId()};
    }

    @Override
    public Company map(ResultSet resultSet) throws DaoException {
        Company company = new Company();
        try {
            company.setId(resultSet.getInt(COMP_ID));
            company.setName(resultSet.getString(COMP_NAME));
            String tel = resultSet.getString(COMP_TEL);
            company.setTel(tel == null ? "" : tel);
            company.setAddress(getNullData(resultSet.getString(COMP_ADDRESS)));
            String tax = resultSet.getString(COMP_TAX);
            company.setTax(getNullData(tax));
            company.setCommercial(getNullData(resultSet.getString(COMP_COMM)));
            Blob blob = resultSet.getBlob(COMP_IMAGE);

            if (blob != null) {
                company.setImage(blob.getBytes(1, (int) blob.length()));
            }
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return company;
    }

    private String getNullData(String string) {
        return string == null ? "" : string;
    }
}
