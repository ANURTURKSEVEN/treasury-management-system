package com.gtech.treasury.dao;

import com.gtech.treasury.model.CustomerFixedRate;
import com.gtech.treasury.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * customer_fixed_rate erişimi: müşteriye özel kur ekle/güncelle, sorgula, sil.
 */
public class CustomerFixedRateDAO {

    /** Ekle veya güncelle (müşteri+döviz benzersiz). */
    public boolean upsert(int customerNo, String currency, double buyRate, double sellRate) {
        String sql = "INSERT INTO customer_fixed_rate (customer_no, currency, buy_rate, sell_rate, active) "
                   + "VALUES (?, ?, ?, ?, 1) "
                   + "ON DUPLICATE KEY UPDATE buy_rate = VALUES(buy_rate), "
                   + "sell_rate = VALUES(sell_rate), active = 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerNo);
            ps.setString(2, currency);
            ps.setDouble(3, buyRate);
            ps.setDouble(4, sellRate);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Fix kur kaydedilemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Fix kur upsert");
            return false;
        }
    }

    /** Belirli müşteri + döviz için aktif fix kur (yoksa null). */
    public CustomerFixedRate get(int customerNo, String currency) {
        String sql = "SELECT id, customer_no, currency, buy_rate, sell_rate, created_at "
                   + "FROM customer_fixed_rate WHERE customer_no = ? AND currency = ? AND active = 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerNo);
            ps.setString(2, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs, "");
            }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "Fix kur get");
        }
        return null;
    }

    /** Bir müşterinin tüm aktif fix kurları. */
    public List<CustomerFixedRate> getByCustomer(int customerNo) {
        String sql = "SELECT id, customer_no, currency, buy_rate, sell_rate, created_at "
                   + "FROM customer_fixed_rate WHERE customer_no = ? AND active = 1 ORDER BY currency";
        List<CustomerFixedRate> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs, ""));
            }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "Fix kur getByCustomer");
        }
        return list;
    }

    /** Tüm aktif fix kurlar (müşteri adıyla) — admin listesi. */
    public List<CustomerFixedRate> getAllActive() {
        String sql = "SELECT f.id, f.customer_no, f.currency, f.buy_rate, f.sell_rate, f.created_at, "
                   + "CONCAT(c.customer_name, ' ', c.surname) AS ad "
                   + "FROM customer_fixed_rate f "
                   + "LEFT JOIN customer c ON c.customer_no = f.customer_no "
                   + "WHERE f.active = 1 ORDER BY f.created_at DESC";
        List<CustomerFixedRate> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs, rs.getString("ad")));
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "Fix kur getAllActive");
        }
        return list;
    }

    /** Fix kuru kaldır (pasife al). */
    public boolean deactivate(int customerNo, String currency) {
        String sql = "UPDATE customer_fixed_rate SET active = 0 WHERE customer_no = ? AND currency = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerNo);
            ps.setString(2, currency);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "Fix kur deactivate");
            return false;
        }
    }

    private CustomerFixedRate map(ResultSet rs, String ad) throws SQLException {
        return new CustomerFixedRate(
                rs.getInt("id"),
                rs.getInt("customer_no"),
                rs.getString("currency"),
                rs.getDouble("buy_rate"),
                rs.getDouble("sell_rate"),
                ad,
                String.valueOf(rs.getTimestamp("created_at")));
    }
}
