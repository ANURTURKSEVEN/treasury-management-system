package com.gtech.treasury.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.gtech.treasury.model.Customer;
import com.gtech.treasury.util.DBConnection;

/**
 * customer tablosuna erişimden sorumlu DAO.
 * Tür bilgisi (GERCEK/TUZEL) customer_type tablosuyla JOIN edilerek okunur.
 * Müşteri eklenirken: customer_no otomatik atanır ve users tablosuna
 * giriş kaydı (username = customer_no, rol CUSTOMER) tek transaction'da oluşturulur.
 */
public class CustomerDAO {

    /** Banka (hazine) özel müşterisi — normal müşteri listelerinde gösterilmez. */
    public static final int BANK_CUSTOMER_NO = 99999999;

    // Ortak SELECT: müşteri + tipi (customer_type tablosundan)
    private static final String BASE_SELECT =
            "SELECT c.customer_id, c.customer_no, ct.type_name AS customer_type, "
          + "c.customer_name, c.surname, c.tc, c.phone, c.address, c.status, c.created_at "
          + "FROM customer c JOIN customer_type ct ON c.customer_type_id = ct.type_id ";

    /** Yeni müşteri ekler + users giriş kaydını (username = customer_no) tek transaction'da oluşturur. */
    public boolean addCustomer(Customer customer) {
        String insCustomer =
                "INSERT INTO customer (customer_name, surname, tc, phone, address, role_id, customer_type_id) "
              + "VALUES (?, ?, ?, ?, ?, "
              + "(SELECT role_id FROM role WHERE role_name = 'CUSTOMER'), "
              + "(SELECT type_id FROM customer_type WHERE type_name = ?))";
        String updNo = "UPDATE customer SET customer_no = ? WHERE customer_id = ?";
        String insUser =
                "INSERT INTO users (username, password, role_id, customer_id, full_name) "
              + "VALUES (?, ?, (SELECT role_id FROM role WHERE role_name = 'CUSTOMER'), ?, ?)";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            int newId;
            try (PreparedStatement ps = conn.prepareStatement(insCustomer, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, customer.getCustomerName());
                ps.setString(2, customer.getSurname());
                ps.setString(3, customer.getTc());
                ps.setString(4, customer.getPhone());
                ps.setString(5, customer.getAddress());
                ps.setString(6, customer.getCustomerType());
                if (ps.executeUpdate() == 0) { conn.rollback(); return false; }
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) { conn.rollback(); return false; }
                    newId = keys.getInt(1);
                }
            }

            int newNo = 10000000 + newId;
            try (PreparedStatement up = conn.prepareStatement(updNo)) {
                up.setInt(1, newNo);
                up.setInt(2, newId);
                up.executeUpdate();
            }

            try (PreparedStatement pu = conn.prepareStatement(insUser)) {
                pu.setString(1, String.valueOf(newNo));
                pu.setString(2, customer.getPassword());
                pu.setInt(3, newId);
                pu.setString(4, customer.getCustomerName() + " "
                        + (customer.getSurname() == null ? "" : customer.getSurname()));
                pu.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            System.err.println("Müşteri eklenirken hata oluştu: " + e.getMessage());
            ErrorLogDAO.log(e, "Müşteri ekleme");
        }
        return false;
    }

    /** Tüm AKTİF müşteriler (status = 1). */
    public List<Customer> getAllCustomers() {
        List<Customer> list = new ArrayList<>();
        String sql = BASE_SELECT + "WHERE c.status = 1 AND c.customer_no <> " + BANK_CUSTOMER_NO
                   + " ORDER BY c.customer_id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Müşteriler getirilirken hata: " + e.getMessage());
            ErrorLogDAO.log(e, "Müşteri listeleme");
        }
        return list;
    }

    /** Tüm PASİF müşteriler (status = 0) — geri getirme ekranı için. */
    public List<Customer> getPassiveCustomers() {
        List<Customer> list = new ArrayList<>();
        String sql = BASE_SELECT + "WHERE c.status = 0 AND c.customer_no <> " + BANK_CUSTOMER_NO
                   + " ORDER BY c.customer_id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Pasif müşteriler getirilirken hata: " + e.getMessage());
            ErrorLogDAO.log(e, "Pasif müşteri listeleme");
        }
        return list;
    }

    /**
     * Pasif müşteriyi ve hesaplarını yeniden AKTİF yapar (status = 1).
     * deleteCustomer'ın tersi; ikisi de tek transaction'da.
     */
    public boolean reactivateCustomer(int customerId) {
        String actCustomer = "UPDATE customer SET status = 1 WHERE customer_id = ?";
        String actAccounts = "UPDATE account  SET status = 1 WHERE customer_id = ?";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psC = conn.prepareStatement(actCustomer);
                 PreparedStatement psA = conn.prepareStatement(actAccounts)) {
                psC.setInt(1, customerId);
                if (psC.executeUpdate() == 0) { conn.rollback(); return false; }
                psA.setInt(1, customerId);
                psA.executeUpdate();
                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            System.err.println("Müşteri geri getirilemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Müşteri geri getirme");
        }
        return false;
    }

    /** ID'ye göre tek müşteri (müşteri girişinde bilgilerini yüklemek için). */
    public Customer getCustomerById(int customerId) {
        String sql = BASE_SELECT + "WHERE c.customer_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            System.err.println("Müşteri bulunamadı: " + e.getMessage());
            ErrorLogDAO.log(e, "Müşteri getir (id)");
        }
        return null;
    }

    /** Kriterlere göre arama ("ile başlayan"). */
    public List<Customer> searchByCriteria(String customerNo, String name, String surname,
                                           String tc, String phone) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + "WHERE c.status = 1 AND c.customer_no <> "
                + BANK_CUSTOMER_NO);
        List<Object> params = new ArrayList<>();

        if (customerNo != null && !customerNo.isBlank()) {
            sql.append(" AND CAST(c.customer_no AS CHAR) LIKE ?");
            params.add(customerNo.trim() + "%");
        }
        if (name != null && !name.isBlank()) {
            sql.append(" AND c.customer_name LIKE ?");
            params.add(name.trim() + "%");
        }
        if (surname != null && !surname.isBlank()) {
            sql.append(" AND c.surname LIKE ?");
            params.add(surname.trim() + "%");
        }
        if (tc != null && !tc.isBlank()) {
            sql.append(" AND c.tc LIKE ?");
            params.add(tc.trim() + "%");
        }
        if (phone != null && !phone.isBlank()) {
            sql.append(" AND c.phone LIKE ?");
            params.add(phone.trim() + "%");
        }
        sql.append(" ORDER BY c.customer_id");

        List<Customer> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("Kriterli arama hatası: " + e.getMessage());
            ErrorLogDAO.log(e, "Müşteri arama");
        }
        return list;
    }

    /** Müşteri bilgilerini ve türünü günceller (customer_no değişmez). */
    public boolean updateCustomer(Customer customer) {
        String sql = "UPDATE customer SET "
                   + "customer_name = ?, surname = ?, tc = ?, phone = ?, address = ?, "
                   + "customer_type_id = (SELECT type_id FROM customer_type WHERE type_name = ?) "
                   + "WHERE customer_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customer.getCustomerName());
            ps.setString(2, customer.getSurname());
            ps.setString(3, customer.getTc());
            ps.setString(4, customer.getPhone());
            ps.setString(5, customer.getAddress());
            ps.setString(6, customer.getCustomerType());
            ps.setInt(7, customer.getCustomerId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Müşteri güncellenemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Müşteri güncelleme");
        }
        return false;
    }

    /**
     * Müşteriyi PASİF yapar (soft delete: status = 0) ve ona ait TÜM hesapları da
     * pasifleştirir. İki işlem tek transaction'da yapılır (ya ikisi de olur ya hiçbiri).
     */
    public boolean deleteCustomer(int customerId) {
        String passCustomer = "UPDATE customer SET status = 0 WHERE customer_id = ?";
        String passAccounts = "UPDATE account  SET status = 0 WHERE customer_id = ?";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psC = conn.prepareStatement(passCustomer);
                 PreparedStatement psA = conn.prepareStatement(passAccounts)) {

                psC.setInt(1, customerId);
                if (psC.executeUpdate() == 0) {   // müşteri bulunamadı -> hiçbir şey yapma
                    conn.rollback();
                    return false;
                }
                psA.setInt(1, customerId);
                psA.executeUpdate();              // hesap sayısı 0 olabilir; sorun değil

                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            System.err.println("Müşteri pasifleştirilemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Müşteri silme");
        }
        return false;
    }

    /** Müşteri tiplerini (GERCEK, TUZEL) customer_type tablosundan getirir. */
    public List<String> getCustomerTypes() {
        List<String> types = new ArrayList<>();
        String sql = "SELECT type_name FROM customer_type ORDER BY type_id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) types.add(rs.getString("type_name"));
        } catch (SQLException e) {
            System.err.println("Müşteri tipleri getirilemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Müşteri tipleri");
        }
        return types;
    }

    private Customer mapRow(ResultSet rs) throws SQLException {
        Customer c = new Customer(
                rs.getInt("customer_id"),
                rs.getInt("customer_no"),
                rs.getString("customer_type"),
                rs.getString("customer_name"),
                rs.getString("surname"),
                rs.getString("tc"),
                rs.getString("phone"),
                rs.getString("address"));
        c.setStatus(rs.getInt("status"));
        c.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
        return c;
    }
}
