package com.gtech.treasury.util;

import com.gtech.treasury.dao.ErrorLogDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Deal referans numarası üretir: PREFIX-YYYYMMDD-000001 (gün içi sıralı, benzersiz). */
public final class ReferenceGenerator {

    private ReferenceGenerator() {}

    /**
     * @param prefix ör. "MM"
     * @param table  ör. "mm_borrowing" (o günkü kayıt sayısını saymak için)
     * @param column ör. "reference_no"
     */
    public static synchronized String next(String prefix, String table, String column) {
        String day = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String like = prefix + "-" + day + "-%";
        int count = 0;
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + column + " LIKE ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, like);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) count = rs.getInt(1); }
        } catch (Exception e) {
            ErrorLogDAO.log(e, "Referans üretimi");
        }
        return String.format("%s-%s-%06d", prefix, day, count + 1);
    }
}
