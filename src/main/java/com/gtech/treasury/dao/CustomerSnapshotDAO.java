package com.gtech.treasury.dao;

import com.gtech.treasury.model.ChartSeries;
import com.gtech.treasury.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * customer_snapshot: bir müşterinin toplam TL karşılığının zaman içindeki değeri.
 * Her spot/transfer sonrası kaydedilir; müşteri anasayfasında trend çizgisi olur.
 */
public class CustomerSnapshotDAO {

    /** Verilen müşterinin şu anki toplam TL karşılığını kaydeder. */
    public static void record(int customerId) {
        String sql = "INSERT INTO customer_snapshot (customer_no, total_try) "
                   + "SELECT c.customer_no, "
                   + "  COALESCE(SUM(a.balance * CASE WHEN a.currency = 'TRY' THEN 1 "
                   + "               ELSE COALESCE(cr.buy_rate, 1) END), 0) "
                   + "FROM customer c "
                   + "LEFT JOIN account a ON a.customer_id = c.customer_id AND a.status = 1 "
                   + "LEFT JOIN currency_rate cr ON cr.currency = a.currency AND cr.status = 1 "
                   + "WHERE c.customer_id = ? GROUP BY c.customer_no";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Müşteri snapshot kaydedilemedi: " + e.getMessage());
        }
    }

    /** Bir müşterinin son N snapshot toplamı (eskiden yeniye). */
    public static double[] recentTotals(int customerNo, int limit) {
        String sql = "SELECT total_try FROM ("
                   + "  SELECT id, total_try FROM customer_snapshot WHERE customer_no = ? "
                   + "  ORDER BY id DESC LIMIT ?"
                   + ") x ORDER BY id ASC";
        List<Double> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerNo);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getDouble(1));
            }
        } catch (SQLException e) {
            System.err.println("Müşteri trendi getirilemedi: " + e.getMessage());
        }
        double[] arr = new double[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }

    /** Bir müşterinin son N snapshot'ı: gün etiketi + toplam (eskiden yeniye). */
    public static ChartSeries recentSeries(int customerNo, int limit) {
        String sql = "SELECT total_try, created_at FROM ("
                   + "  SELECT id, total_try, created_at FROM customer_snapshot WHERE customer_no = ? "
                   + "  ORDER BY id DESC LIMIT ?"
                   + ") x ORDER BY id ASC";
        List<Double> vals = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerNo);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    vals.add(rs.getDouble(1));
                    java.sql.Timestamp ts = rs.getTimestamp(2);
                    labels.add(ts == null ? "" : new java.text.SimpleDateFormat("dd.MM").format(ts));
                }
            }
        } catch (SQLException e) {
            System.err.println("Müşteri serisi getirilemedi: " + e.getMessage());
        }
        double[] arr = new double[vals.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = vals.get(i);
        return new ChartSeries(labels.toArray(new String[0]), arr);
    }
}
