package com.gtech.treasury.dao;

import com.gtech.treasury.model.User;
import com.gtech.treasury.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * users tablosuna erişimden sorumlu DAO (Data Access Object) sınıfı.
 * Aşama 4 (JDBC) + Aşama 14 (PreparedStatement ile SQL Injection engelleme)
 * + Aşama 15 (DAO Pattern).
 */
public class UserDAO {

    /**
     * Kullanıcı adı ve şifreyi veritabanında kontrol eder.
     *
     * @return giriş doğruysa User nesnesi, yanlışsa null
     */
    public User login(String username, String password) {
        String sql = "SELECT u.id, u.username, r.role_name, u.full_name, u.customer_id "
                   + "FROM users u JOIN role r ON u.role_id = r.role_id "
                   + "WHERE u.username = ? AND u.password = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // PreparedStatement -> SQL Injection'a karşı güvenli
            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("role_name"),
                            rs.getString("full_name"),
                            rs.getInt("customer_id")   // müşteri değilse 0
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Login sırasında veritabanı hatası: " + e.getMessage());
            ErrorLogDAO.log(e, "Personel giriş");
        }
        return null; // kullanıcı bulunamadı / şifre yanlış
    }

    /** Verilen kullanıcı adı (veya müşteri no) users tablosunda var mı? */
    public boolean usernameExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Kullanıcı kontrol hatası: " + e.getMessage());
            ErrorLogDAO.log(e, "Kullanıcı kontrol");
        }
        return false;
    }

    /**
     * Sadece PERSONEL kullanıcıları listeler (ADMIN/TRADER/VIEWER).
     * Müşteriler (role_type = CUSTOMER) hariç tutulur.
     */
    public List<User> getStaffUsers() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT u.id, u.username, r.role_name, u.full_name "
                   + "FROM users u JOIN role r ON u.role_id = r.role_id "
                   + "WHERE r.role_type = 'USER' ORDER BY u.id";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("role_name"),
                        rs.getString("full_name")));
            }
        } catch (SQLException e) {
            System.err.println("Personel getirilirken hata: " + e.getMessage());
            ErrorLogDAO.log(e, "Personel listeleme");
        }
        return list;
    }

    /** Tüm kullanıcıları listeler (şifre hariç). */
    public List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT u.id, u.username, r.role_name, u.full_name "
                   + "FROM users u JOIN role r ON u.role_id = r.role_id ORDER BY u.id";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("role_name"),
                        rs.getString("full_name")));
            }
        } catch (SQLException e) {
            System.err.println("Kullanıcılar getirilirken hata: " + e.getMessage());
        }
        return list;
    }

    /** Yeni kullanıcı ekler. */
    public boolean addUser(String username, String password, String role, String fullName) {
        String sql = "INSERT INTO users (username, password, role_id, full_name) "
                   + "VALUES (?, ?, (SELECT role_id FROM role WHERE role_name = ?), ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, role);
            ps.setString(4, fullName);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Kullanıcı eklenemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Kullanıcı ekleme");
        }
        return false;
    }

    /** Kullanıcının rolünü değiştirir. */
    public boolean updateRole(int userId, String newRole) {
        String sql = "UPDATE users SET role_id = (SELECT role_id FROM role WHERE role_name = ?) "
                   + "WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newRole);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Rol güncellenemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Rol güncelleme");
        }
        return false;
    }

    /** Kullanıcı siler. */
    public boolean deleteUser(int userId) {
        String sql = "DELETE FROM users WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Kullanıcı silinemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Kullanıcı silme");
        }
        return false;
    }
}
