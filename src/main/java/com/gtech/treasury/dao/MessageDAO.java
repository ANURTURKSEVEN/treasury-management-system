package com.gtech.treasury.dao;

import com.gtech.treasury.model.Message;
import com.gtech.treasury.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO {

    private static final String COLS =
            "id, sender, recipient, subject, body, category, ref_no, is_read, created_at";

    /** Yeni mesaj gönder (gelen kutusuna düşer). */
    public void send(String sender, String recipient, String subject, String body,
                     String category, String refNo) {
        String sql = "INSERT INTO message (sender, recipient, subject, body, category, ref_no) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sender);
            ps.setString(2, recipient);
            ps.setString(3, subject);
            ps.setString(4, body);
            ps.setString(5, category);
            ps.setString(6, refNo);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Mesaj gönderilemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Mesaj gönderme");
        }
    }

    /** Banka kullanıcısının gelen kutusu: tüm bankaya ('STAFF') ve kendisine gelenler. */
    public List<Message> staffInbox(String username) {
        String sql = "SELECT " + COLS + " FROM message " +
                     "WHERE recipient = 'STAFF' OR recipient = ? ORDER BY id DESC";
        return query(sql, "STAFF:" + username);
    }

    /** Banka kullanıcısının okunmamış mesaj sayısı. */
    public int staffUnreadCount(String username) {
        String sql = "SELECT COUNT(*) FROM message " +
                     "WHERE (recipient = 'STAFF' OR recipient = ?) AND is_read = 0";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "STAFF:" + username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "Mesaj sayımı");
        }
        return 0;
    }

    /** Bir mesajı okundu işaretle. */
    public void markRead(int id) {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE message SET is_read = 1 WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "Mesaj okundu");
        }
    }
    /** Banka kullanıcısının GÖNDERDİĞİ mesajlar (Gönderilenler kutusu). */
    public List<Message> staffSent(String username) {
        String sql = "SELECT " + COLS + " FROM message WHERE sender = ? ORDER BY id DESC";
        return query(sql, "STAFF:" + username);
    }

    /** Müşterinin gelen kutusu (4b'de kullanılacak). */
    public List<Message> customerInbox(int customerNo) {
        String sql = "SELECT " + COLS + " FROM message WHERE recipient = ? ORDER BY id DESC";
        return query(sql, "CUSTOMER:" + customerNo);
    }
    
    /** Müşterinin okunmamış mesaj sayısı (çan/rozet). */
    public int customerUnreadCount(int customerNo) {
        String sql = "SELECT COUNT(*) FROM message WHERE recipient = ? AND is_read = 0";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "CUSTOMER:" + customerNo);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "Müşteri mesaj sayımı");
        }
        return 0;
    }

    /** Bir müşteriyle yapılan TÜM görüşme (her iki yön; hangi personel yazdıysa dahil). Kronolojik. */
    public List<Message> conversation(int customerNo) {
        String tag = "CUSTOMER:" + customerNo;
        String sql = "SELECT " + COLS + " FROM message WHERE sender = ? OR recipient = ? ORDER BY id ASC";
        List<Message> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tag);
            ps.setString(2, tag);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "Görüşme listeleme");
        }
        return list;
    }

    /** Personel için müşteri görüşmelerinin listesi (son mesaj + okunmamış sayısı). */
    public List<com.gtech.treasury.model.Conversation> customerConversations() {
        String sql =
            "SELECT t.cust_no, t.last_id, t.unread, m.subject, m.created_at " +
            "FROM (SELECT CASE WHEN sender LIKE 'CUSTOMER:%' THEN SUBSTRING(sender,10) " +
            "                  ELSE SUBSTRING(recipient,10) END AS cust_no, " +
            "             MAX(id) AS last_id, " +
            "             SUM(CASE WHEN recipient='STAFF' AND is_read=0 THEN 1 ELSE 0 END) AS unread " +
            "      FROM message WHERE sender LIKE 'CUSTOMER:%' OR recipient LIKE 'CUSTOMER:%' " +
            "      GROUP BY cust_no) t " +
            "JOIN message m ON m.id = t.last_id " +
            "ORDER BY t.last_id DESC";
        List<com.gtech.treasury.model.Conversation> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int no = 0;
                try { no = Integer.parseInt(rs.getString("cust_no")); } catch (Exception ignored) { }
                list.add(new com.gtech.treasury.model.Conversation(
                        no, rs.getString("subject"),
                        String.valueOf(rs.getTimestamp("created_at")),
                        rs.getInt("unread")));
            }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "Görüşme listesi");
        }
        return list;
    }

    /** Bir müşteriden gelen (STAFF ortak kutusundaki) okunmamış mesajları okundu yapar. */
    public void markCustomerThreadRead(int customerNo) {
        String sql = "UPDATE message SET is_read = 1 WHERE recipient = 'STAFF' AND sender = ? AND is_read = 0";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "CUSTOMER:" + customerNo);
            ps.executeUpdate();
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "Görüşme okundu");
        }
    }

    /** ResultSet satırını Message'a çevirir. */
    private Message mapRow(ResultSet rs) throws SQLException {
        return new Message(
                rs.getInt("id"), rs.getString("sender"), rs.getString("recipient"),
                rs.getString("subject"), rs.getString("body"), rs.getString("category"),
                rs.getString("ref_no"), rs.getInt("is_read") == 1,
                String.valueOf(rs.getTimestamp("created_at")));
    }

    /** Ortak yardımcı: sorguyu çalıştırıp Message listesine çevirir. */
    private List<Message> query(String sql, String param) {
        List<Message> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (param != null) ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Message(
                            rs.getInt("id"),
                            rs.getString("sender"),
                            rs.getString("recipient"),
                            rs.getString("subject"),
                            rs.getString("body"),
                            rs.getString("category"),
                            rs.getString("ref_no"),
                            rs.getInt("is_read") == 1,
                            String.valueOf(rs.getTimestamp("created_at"))));
                }
            }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "Mesaj listeleme");
        }
        return list;
    }
}