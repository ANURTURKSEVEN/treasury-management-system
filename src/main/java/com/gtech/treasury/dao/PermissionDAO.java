package com.gtech.treasury.dao;

import com.gtech.treasury.model.Role;
import com.gtech.treasury.model.Screen;
import com.gtech.treasury.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rol - Menü (ekran) yetkilerini yöneten DAO.
 * screen ve role_screen tablolarıyla çalışır.
 */
public class PermissionDAO {

    /** Tüm rolleri getirir (Rol Yetkileri ekranındaki sol liste için). */
    public List<Role> getAllRoles() {
        List<Role> list = new ArrayList<>();
        // GERCEK/TUZEL rol değil (müşteri tipi); rol menüsünde gösterilmez.
        String sql = "SELECT role_id, role_name, role_description, role_type FROM role "
                   + "WHERE role_name NOT IN ('GERCEK','TUZEL') ORDER BY role_id";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(new Role(
                        rs.getInt("role_id"),
                        rs.getString("role_name"),
                        rs.getString("role_description"),
                        rs.getString("role_type")));
            }
        } catch (SQLException e) {
            System.err.println("Roller getirilemedi: " + e.getMessage());
        }
        return list;
    }

    /** Tüm ekranları getirir. */
    public List<Screen> getAllScreens() {
        List<Screen> list = new ArrayList<>();
        String sql = "SELECT screen_id, screen_key, screen_name, for_type FROM screen ORDER BY screen_id";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(new Screen(
                        rs.getInt("screen_id"),
                        rs.getString("screen_key"),
                        rs.getString("screen_name"),
                        rs.getString("for_type")));
            }
        } catch (SQLException e) {
            System.err.println("Ekranlar getirilemedi: " + e.getMessage());
        }
        return list;
    }

    /** Belirli bir rolün yetkili olduğu ekran id'leri (Rol Yetkileri ekranı için). */
    public Set<Integer> getAllowedScreenIds(int roleId) {
        Set<Integer> ids = new HashSet<>();
        String sql = "SELECT screen_id FROM role_screen WHERE role_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("screen_id"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Yetkiler getirilemedi: " + e.getMessage());
        }
        return ids;
    }

    /** Rol adına göre erişilebilir ekranlar (menüyü dinamik oluşturmak için). */
    public List<Screen> getAllowedScreens(String roleName) {
        List<Screen> list = new ArrayList<>();
        String sql = "SELECT s.screen_id, s.screen_key, s.screen_name, s.for_type "
                   + "FROM role_screen rs "
                   + "JOIN role r   ON rs.role_id   = r.role_id "
                   + "JOIN screen s ON rs.screen_id = s.screen_id "
                   + "WHERE r.role_name = ? ORDER BY s.screen_id";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, roleName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Screen(
                            rs.getInt("screen_id"),
                            rs.getString("screen_key"),
                            rs.getString("screen_name"),
                            rs.getString("for_type")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Rol ekranları getirilemedi: " + e.getMessage());
        }
        return list;
    }

    /**
     * Bir rolün yetkilerini topluca kaydeder: önce hepsini siler,
     * sonra seçilenleri ekler (tek transaction içinde).
     */
    public boolean savePermissions(int roleId, List<Integer> screenIds) {
        String delete = "DELETE FROM role_screen WHERE role_id = ?";
        String insert = "INSERT INTO role_screen (role_id, screen_id) VALUES (?, ?)";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement(delete)) {
                del.setInt(1, roleId);
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(insert)) {
                for (int screenId : screenIds) {
                    ins.setInt(1, roleId);
                    ins.setInt(2, screenId);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            System.err.println("Yetkiler kaydedilemedi: " + e.getMessage());
        }
        return false;
    }
}
