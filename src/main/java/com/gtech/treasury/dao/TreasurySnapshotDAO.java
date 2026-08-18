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
 * treasury_snapshot: bankanın toplam TL karşılığının zaman içindeki değeri.
 * Her spot işlemden sonra bir nokta kaydedilir; Banka Varlıkları ekranı
 * bunu trend (çizgi) grafiği olarak gösterir.
 */
public class TreasurySnapshotDAO {

    /** Bankanın (customer_no 99999999) şu anki toplam TL karşılığını kaydeder. */
    public static void record() {
        String sql = "INSERT INTO treasury_snapshot (total_try) "
                   + "SELECT COALESCE(SUM(a.balance * CASE WHEN a.currency = 'TRY' THEN 1 "
                   + "                    ELSE COALESCE(cr.buy_rate, 1) END), 0) "
                   + "FROM account a "
                   + "JOIN customer c ON a.customer_id = c.customer_id AND c.customer_no = 99999999 "
                   + "LEFT JOIN currency_rate cr ON cr.currency = a.currency AND cr.status = 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Hazine snapshot kaydedilemedi: " + e.getMessage());
        }
    }

    /** Son N snapshot'ın toplamları (eskiden yeniye sıralı). */
    public static double[] recentTotals(int limit) {
        String sql = "SELECT total_try FROM ("
                   + "  SELECT id, total_try FROM treasury_snapshot ORDER BY id DESC LIMIT ?"
                   + ") x ORDER BY id ASC";
        List<Double> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getDouble(1));
            }
        } catch (SQLException e) {
            System.err.println("Hazine geçmişi getirilemedi: " + e.getMessage());
        }
        double[] arr = new double[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }

    /** Son N snapshot: gün etiketi + toplam (eskiden yeniye sıralı) — gerçek eksenli grafik için. */
    public static ChartSeries recentSeries(int limit) {
        String sql = "SELECT total_try, created_at FROM ("
                   + "  SELECT id, total_try, created_at FROM treasury_snapshot ORDER BY id DESC LIMIT ?"
                   + ") x ORDER BY id ASC";
        List<Double> vals = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    vals.add(rs.getDouble(1));
                    java.sql.Timestamp ts = rs.getTimestamp(2);
                    labels.add(ts == null ? "" : new java.text.SimpleDateFormat("dd.MM").format(ts));
                }
            }
        } catch (SQLException e) {
            System.err.println("Hazine serisi getirilemedi: " + e.getMessage());
        }
        double[] arr = new double[vals.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = vals.get(i);
        return new ChartSeries(labels.toArray(new String[0]), arr);
    }
}
