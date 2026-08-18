package com.gtech.treasury.dao;

import com.gtech.treasury.model.ActivityLog;
import com.gtech.treasury.util.DBConnection;
import com.gtech.treasury.util.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Sistemdeki her işlemi activity_log tablosuna kaydeder ve raporlar.
 * "Kim yaptı" bilgisi Session'dan alınır.
 */
public class ActivityLogDAO {

    // ---- Yazma ----

    public static void log(String actionType, String description) {
        log(actionType, 0, 0, null, description, null);
    }

    public static void log(String actionType, int customerNo, String description, String details) {
        log(actionType, customerNo, 0, null, description, details);
    }

    /** Tam log (spot işlemler için amount + currency dolu gelir). */
    public static void log(String actionType, int customerNo, double amount, String currency,
                           String description, String details) {
        String sql = "INSERT INTO activity_log "
                   + "(action_type, username, customer_no, amount, currency, description, details) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, actionType);
            ps.setString(2, Session.getCurrentUsername());
            if (customerNo > 0) ps.setInt(3, customerNo); else ps.setNull(3, java.sql.Types.INTEGER);
            if (amount > 0) ps.setDouble(4, amount); else ps.setNull(4, java.sql.Types.DECIMAL);
            if (currency != null) ps.setString(5, currency); else ps.setNull(5, java.sql.Types.VARCHAR);
            ps.setString(6, description);
            ps.setString(7, details);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Aktivite loglanamadı: " + e.getMessage());
        }
    }

    // ---- Okuma (Raporlar) ----

    /**
     * Kriterlere göre işlemleri getirir (en yeni önce). Boş kriter atlanır.
     * @param minAmount "şu değerden büyük" (amount >= X)
     * @param currency  spot işlem dövizi (USD/EUR/GBP) veya boş
     */
    public List<ActivityLog> search(String customerNo, String username, String actionType,
                                    String minAmount, String currency,
                                    String startDate, String endDate) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, action_type, username, customer_no, amount, currency, "
              + "description, details, created_at FROM activity_log WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (customerNo != null && customerNo.matches("\\d+")) {
            sql.append(" AND customer_no = ?");
            params.add(Integer.parseInt(customerNo));
        }
        if (username != null && !username.isBlank()) {
            sql.append(" AND username LIKE ?");
            params.add("%" + username.trim() + "%");
        }
        if (actionType != null && !actionType.isBlank()) {
            sql.append(" AND action_type = ?");
            params.add(actionType.trim());
        }
        if (minAmount != null && minAmount.matches("\\d+(\\.\\d+)?")) {
            sql.append(" AND amount >= ?");
            params.add(Double.parseDouble(minAmount));
        }
        if (currency != null && !currency.isBlank()) {
            sql.append(" AND currency = ?");
            params.add(currency.trim());
        }
        if (startDate != null && !startDate.isBlank()) {
            sql.append(" AND created_at >= ?");
            params.add(startDate.trim() + " 00:00:00");
        }
        if (endDate != null && !endDate.isBlank()) {
            sql.append(" AND created_at <= ?");
            params.add(endDate.trim() + " 23:59:59");
        }
        sql.append(" ORDER BY id DESC");

        List<ActivityLog> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ActivityLog(
                            rs.getInt("id"),
                            rs.getString("action_type"),
                            rs.getString("username"),
                            rs.getInt("customer_no"),
                            rs.getDouble("amount"),
                            rs.getString("currency"),
                            rs.getString("description"),
                            rs.getString("details"),
                            String.valueOf(rs.getTimestamp("created_at"))));
                }
            }
        } catch (SQLException e) {
            System.err.println("Rapor sorgusu hatası: " + e.getMessage());
            ErrorLogDAO.log(e, "Rapor sorgusu");
        }
        return list;
    }

    /** Bir hesabın hareketleri: detayında hesap no geçen işlemler (en yeni önce). */
    public List<ActivityLog> byAccountNo(long accountNo) {
        List<ActivityLog> list = new ArrayList<>();
        String sql = "SELECT id, action_type, username, customer_no, amount, currency, "
                   + "description, details, created_at FROM activity_log "
                   + "WHERE details LIKE ? ORDER BY id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + accountNo + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ActivityLog(
                            rs.getInt("id"), rs.getString("action_type"), rs.getString("username"),
                            rs.getInt("customer_no"), rs.getDouble("amount"), rs.getString("currency"),
                            rs.getString("description"), rs.getString("details"),
                            String.valueOf(rs.getTimestamp("created_at"))));
                }
            }
        } catch (SQLException e) {
            System.err.println("Hesap hareketleri getirilemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Hesap hareketleri");
        }
        return list;
    }
}
