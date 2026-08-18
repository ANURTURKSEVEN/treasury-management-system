package com.gtech.treasury.dao;

import com.gtech.treasury.model.Notification;
import com.gtech.treasury.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * notification tablosuna erişim. Bildirimler müşteriye (customer_no) veya
 * banka personeline (target_role='STAFF') gönderilebilir. Geçmiş tutulur; okundu bilgisi vardır.
 */
public class NotificationDAO {

    private static final String COLS = "id, title, detail, type, ref_no, is_read, created_at";

    // ---------------- Müşteri bildirimleri ----------------

    /** Bir müşteriye INFO bildirimi ekler. */
    public void add(int customerNo, String title, String detail) {
        add(customerNo, title, detail, "INFO", null);
    }

    /** Bir müşteriye tip/ref ile bildirim ekler. */
    public void add(int customerNo, String title, String detail, String type, String refNo) {
        String sql = "INSERT INTO notification (customer_no, title, detail, type, ref_no) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerNo);
            ps.setString(2, title);
            ps.setString(3, detail);
            ps.setString(4, type);
            ps.setString(5, refNo);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Bildirim eklenemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Bildirim ekleme");
        }
    }

    /** Banka personeline (STAFF) bildirim ekler. */
    public void addForStaff(String title, String detail, String type, String refNo) {
        String sql = "INSERT INTO notification (target_role, title, detail, type, ref_no) VALUES ('STAFF', ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, detail);
            ps.setString(3, type);
            ps.setString(4, refNo);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Personel bildirimi eklenemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Personel bildirimi");
        }
    }

    /** Müşterinin okunmamış bildirimleri (girişte özet için). */
    public List<Notification> unread(int customerNo) {
        return query("SELECT " + COLS + " FROM notification WHERE customer_no = ? AND is_read = 0 ORDER BY id DESC",
                customerNo);
    }

    /** Müşterinin TÜM bildirimleri (geçmiş; en yeni önce). */
    public List<Notification> all(int customerNo) {
        return query("SELECT " + COLS + " FROM notification WHERE customer_no = ? ORDER BY id DESC", customerNo);
    }

    /** Müşterinin okunmamış bildirim sayısı (çan rozeti). */
    public int unreadCount(int customerNo) {
        return count("SELECT COUNT(*) FROM notification WHERE customer_no = ? AND is_read = 0", customerNo);
    }

    /** Müşterinin tüm okunmamışlarını okundu yapar. */
    public void markAllRead(int customerNo) {
        exec("UPDATE notification SET is_read = 1 WHERE customer_no = ? AND is_read = 0", customerNo);
    }

    // ---------------- Personel (STAFF) bildirimleri ----------------

    public List<Notification> staffAll() {
        return queryNoParam("SELECT " + COLS + " FROM notification WHERE target_role = 'STAFF' ORDER BY id DESC");
    }

    public int staffUnreadCount() {
        return countNoParam("SELECT COUNT(*) FROM notification WHERE target_role = 'STAFF' AND is_read = 0");
    }

    public void staffMarkAllRead() {
        execNoParam("UPDATE notification SET is_read = 1 WHERE target_role = 'STAFF' AND is_read = 0");
    }

    // ---------------- Ortak ----------------

    /** Tek bir bildirimi okundu yapar. */
    public void markRead(int id) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE notification SET is_read = 1 WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "Bildirim okundu (id)");
        }
    }

    private List<Notification> query(String sql, int customerNo) {
        List<Notification> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "Bildirim listeleme");
        }
        return list;
    }

    private List<Notification> queryNoParam(String sql) {
        List<Notification> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "Bildirim listeleme");
        }
        return list;
    }

    private Notification map(ResultSet rs) throws SQLException {
        Notification n = new Notification(rs.getInt("id"), rs.getString("title"),
                rs.getString("detail"), String.valueOf(rs.getTimestamp("created_at")));
        n.setRead(rs.getInt("is_read") == 1);
        n.setType(rs.getString("type"));
        n.setRefNo(rs.getString("ref_no"));
        return n;
    }

    private int count(String sql, int customerNo) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerNo);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException e) { ErrorLogDAO.log(e, "Bildirim sayımı"); }
        return 0;
    }

    private int countNoParam(String sql) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { ErrorLogDAO.log(e, "Bildirim sayımı"); }
        return 0;
    }

    private void exec(String sql, int customerNo) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerNo);
            ps.executeUpdate();
        } catch (SQLException e) { ErrorLogDAO.log(e, "Bildirim güncelleme"); }
    }

    private void execNoParam(String sql) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) { ErrorLogDAO.log(e, "Bildirim güncelleme"); }
    }
}
