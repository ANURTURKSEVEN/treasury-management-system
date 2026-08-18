package com.gtech.treasury.dao;

import com.gtech.treasury.model.SpotTransaction;
import com.gtech.treasury.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * spot_transaction tablosuna erişim (döviz al/sat işlemleri).
 *
 * Kayıt "banka bakışıyla" tutulur:
 *   buy_currency / buy_amount   -> bankanın ALDIĞI
 *   sell_currency / sell_amount -> bankanın VERDİĞİ
 */
public class SpotDAO {

    /** Bir spot işlemi kaydeder. */
    public boolean save(int customerId,
                        String buyCurrency, double buyAmount,
                        String sellCurrency, double sellAmount,
                        double rate) {
        String sql = "INSERT INTO spot_transaction "
                   + "(customer_id, buy_currency, sell_currency, buy_amount, sell_amount, rate) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, customerId);
            ps.setString(2, buyCurrency);
            ps.setString(3, sellCurrency);
            ps.setDouble(4, buyAmount);
            ps.setDouble(5, sellAmount);
            ps.setDouble(6, rate);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Spot işlem kaydedilemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Spot işlem kaydı");
        }
        return false;
    }

    // Ortak SELECT: işlem + müşteri bilgisi
    private static final String BASE_SELECT =
            "SELECT s.id, c.customer_no, c.customer_name, "
          + "s.buy_currency, s.buy_amount, s.sell_currency, s.sell_amount, "
          + "s.rate, s.transaction_date "
          + "FROM spot_transaction s JOIN customer c ON s.customer_id = c.customer_id ";

    /** Tüm işlemleri getirir (en yeni önce) — personel için. */
    public List<SpotTransaction> getAll() {
        return query(BASE_SELECT + "ORDER BY s.id DESC", 0);
    }

    /** Sadece bir müşterinin işlemlerini getirir — müşteri paneli için. */
    public List<SpotTransaction> getByCustomer(int customerId) {
        return query(BASE_SELECT + "WHERE s.customer_id = ? ORDER BY s.id DESC", customerId);
    }

    private List<SpotTransaction> query(String sql, int customerId) {
        List<SpotTransaction> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (customerId > 0) {
                ps.setInt(1, customerId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new SpotTransaction(
                            rs.getInt("id"),
                            rs.getInt("customer_no"),
                            rs.getString("customer_name"),
                            rs.getString("buy_currency"),
                            rs.getDouble("buy_amount"),
                            rs.getString("sell_currency"),
                            rs.getDouble("sell_amount"),
                            rs.getDouble("rate"),
                            String.valueOf(rs.getTimestamp("transaction_date"))));
                }
            }
        } catch (SQLException e) {
            System.err.println("İşlem geçmişi getirilemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Spot işlem listeleme");
        }
        return list;
    }
}

