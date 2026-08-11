package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Company;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import com.hamza.controlsfx.language.Setting_Language;
import lombok.extern.log4j.Log4j2;

import java.sql.Blob;
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

    CompanyDao() {
        super();
    }

    @Override
    public List<Company> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(COMPANY), this::map);
    }

    /**
     * Creates the company row. It used to write nothing but a placeholder name and drop
     * whatever it was handed on the floor, which was only ever true because the one
     * caller passed an empty company; the columns are written now, and the placeholder is
     * the fallback for the name rather than the value.
     */
    @Override
    public int insert(Company company) throws DaoException {
        String query = SqlStatements.insertStatement(COMPANY, COMP_NAME, COMP_TEL, COMP_ADDRESS, COMP_TAX, COMP_COMM, COMP_IMAGE);
        return executeUpdate(query, name(company), company.getTel(), company.getAddress()
                , company.getTax(), company.getCommercial(), image(company));
    }

    @Override
    public int update(Company company) throws DaoException {
        String update = SqlStatements.updateStatement(COMPANY, COMP_ID, COMP_NAME, COMP_TEL, COMP_ADDRESS, COMP_TAX, COMP_COMM, COMP_IMAGE);
        return executeUpdate(update, getData(company));
    }

    @Override
    public Object[] getData(Company company) {
        return new Object[]{name(company), company.getTel(), company.getAddress()
                , company.getTax(), company.getCommercial(), image(company)
                , company.getId()};
    }

    /** {@code comp_name} is {@code NOT NULL}, so a blank name is the seeded label, not a failed insert. */
    private String name(Company company) {
        String name = company.getName();
        return name == null || name.isBlank() ? Setting_Language.COMPANY_NAME : name.trim();
    }

    /** An empty byte array means "no logo" as much as a null does, and the column stores neither. */
    private byte[] image(Company company) {
        byte[] image = company.getImage();
        return image == null || image.length == 0 ? null : image;
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
