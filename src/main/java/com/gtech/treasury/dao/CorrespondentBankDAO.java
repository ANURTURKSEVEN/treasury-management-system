package com.gtech.treasury.dao;

import com.gtech.treasury.model.CorrespondentBank;
import com.gtech.treasury.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** correspondent_bank tablosuna erişim (muhabir banka listesi). */
public class CorrespondentBankDAO {

    public List<CorrespondentBank> getActive() {
        List<CorrespondentBank> list = new ArrayList<>();
        String sql = "SELECT id, bank_name, bic, country FROM correspondent_bank "
                   + "WHERE active = 1 ORDER BY bank_name";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new CorrespondentBank(rs.getInt("id"), rs.getString("bank_name"),
                        rs.getString("bic"), rs.getString("country")));
            }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "Muhabir banka listeleme");
        }
        return list;
    }
}
