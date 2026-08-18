package com.gtech.treasury.dao;

import com.gtech.treasury.model.Dispute;
import com.gtech.treasury.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * dispute (itiraz) tablosuna erişim.
 * Müşteri bir işleme itiraz eder -> banka personeline (STAFF) bildirim düşer.
 * Banka çözer/reddeder -> müşteriye sonuç bildirimi gider.
 */
public class DisputeDAO {

    private final NotificationDAO notificationDAO = new NotificationDAO();

    /** Yeni itiraz oluşturur ve banka personeline bildirim gönderir. */
    public int create(int customerNo, String subject, String reason) {
        String sql = "INSERT INTO dispute (customer_no, subject, customer_reason) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, customerNo);
            ps.setString(2, subject);
            ps.setString(3, reason);
            if (ps.executeUpdate() == 0) return 0;
            int id = 0;
            try (ResultSet keys = ps.getGeneratedKeys()) { if (keys.next()) id = keys.getInt(1); }

            notificationDAO.addForStaff(
                    "İtiraz #" + id + " — Müşteri " + customerNo,
                    "Konu: " + subject + "\nMüşteri gerekçesi: " + reason
                            + "\n\nİtirazı Yönetim ekranından değerlendirip sonuçlandırın.",
                    "DISPUTE", String.valueOf(id));
            return id;
        } catch (SQLException e) {
            System.err.println("İtiraz oluşturulamadı: " + e.getMessage());
            ErrorLogDAO.log(e, "İtiraz oluşturma");
        }
        return 0;
    }

    /** Duruma göre itirazlar (status null ise tümü). En yeni önce. */
    public List<Dispute> list(String status) {
        List<Dispute> out = new ArrayList<>();
        String sql = "SELECT id, customer_no, subject, customer_reason, status, resolved_by, resolution, created_at "
                   + "FROM dispute " + (status == null ? "" : "WHERE status = ? ") + "ORDER BY id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (status != null) ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "İtiraz listeleme");
        }
        return out;
    }

    /**
     * İtirazı sonuçlandırır (RESOLVED/REJECTED) ve müşteriye sonuç bildirimi gönderir.
     * @return true başarılı
     */
    public boolean resolve(int id, String staffUser, String status, String resolution) {
        String sql = "UPDATE dispute SET status = ?, resolved_by = ?, resolution = ? WHERE id = ? AND status = 'OPEN'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, staffUser);
            ps.setString(3, resolution);
            ps.setInt(4, id);
            if (ps.executeUpdate() == 0) return false;

            int customerNo = customerNoOf(id);
            if (customerNo > 0) {
                String durum = "RESOLVED".equals(status) ? "kabul edildi" : "reddedildi";
                notificationDAO.add(customerNo,
                        "İtiraz #" + id + " sonuçlandı: " + durum,
                        "Banka değerlendirmesi: " + resolution, "DISPUTE_RESULT", String.valueOf(id));
            }
            return true;
        } catch (SQLException e) {
            System.err.println("İtiraz sonuçlandırılamadı: " + e.getMessage());
            ErrorLogDAO.log(e, "İtiraz çözme");
        }
        return false;
    }

    private int customerNoOf(int disputeId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT customer_no FROM dispute WHERE id = ?")) {
            ps.setInt(1, disputeId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        }
        return 0;
    }

    private Dispute map(ResultSet rs) throws SQLException {
        return new Dispute(
                rs.getInt("id"), rs.getInt("customer_no"), rs.getString("subject"),
                rs.getString("customer_reason"), rs.getString("status"),
                rs.getString("resolved_by"), rs.getString("resolution"),
                String.valueOf(rs.getTimestamp("created_at")));
    }
}
