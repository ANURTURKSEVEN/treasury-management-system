package com.gtech.treasury.dao;

import com.gtech.treasury.model.CurrencyRate;
import com.gtech.treasury.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * currency_rate tablosuna erişim. Kurlar versiyonlu ve BLOK (batch) halindedir:
 *   status  = 1 güncel / 0 eski
 *   batch_id = aynı çekimin kimliği (bir çekimdeki tüm kurlar tek blok)
 * Güncelleme TCMB çekiminden gelir; değer değiştiyse tüm kurlar için YENİ bir blok yazılır.
 */
public class RateDAO {

    private static final String COLS =
            "currency, buy_rate, sell_rate, effective_buy, effective_sell, status, batch_id, updated_at";

    /**
     * GÜNCEL kurlar (status = 1) — her para biriminden YALNIZ EN YENİ satır.
     * (Seed yanlışlıkla iki kez çalışsa bile ekranda kur tekrar etmez.)
     */
    public List<CurrencyRate> getAll() {
        return query("SELECT " + COLS + " FROM currency_rate cr WHERE cr.status = 1 "
                   + "AND cr.id = (SELECT MAX(cr2.id) FROM currency_rate cr2 "
                   + "             WHERE cr2.currency = cr.currency AND cr2.status = 1) "
                   + "ORDER BY cr.currency");
    }

    /** Tüm geçmiş, bloklar halinde (en yeni blok önce). */
    public List<CurrencyRate> getHistory() {
        return query("SELECT " + COLS + " FROM currency_rate ORDER BY batch_id DESC, currency");
    }

    /** Tek dövizin GÜNCEL kuru. */
    public CurrencyRate getByCurrency(String currency) {
        String sql = "SELECT " + COLS + " FROM currency_rate "
                   + "WHERE currency = ? AND status = 1 ORDER BY id DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            System.err.println("Kur bulunamadı: " + e.getMessage());
            ErrorLogDAO.log(e, "Kur getir");
        }
        return null;
    }

    /**
     * Kurları BLOK halinde günceller (TCMB çekimi).
     * Herhangi bir kur değiştiyse: mevcut güncel kurların tamamı arşivlenir (status=0)
     * ve verilen kurların tamamı YENİ bir blok (aynı batch_id) olarak eklenir.
     * Hiçbir kur değişmediyse hiçbir şey yapılmaz.
     * @return yeni blok kimliği; değişiklik yoksa 0
     */
    public int updateBatch(Map<String, double[]> rates) {
        // Değişiklik var mı?
        boolean changed = false;
        for (Map.Entry<String, double[]> e : rates.entrySet()) {
            double[] v = e.getValue();
            CurrencyRate cur = getByCurrency(e.getKey());
            if (cur == null
                    || Math.abs(cur.getBuyRate() - v[0]) > 1e-6
                    || Math.abs(cur.getSellRate() - v[1]) > 1e-6
                    || (v.length > 2 && Math.abs(cur.getEffectiveBuy() - v[2]) > 1e-6)
                    || (v.length > 3 && Math.abs(cur.getEffectiveSell() - v[3]) > 1e-6)) {
                changed = true;
                break;
            }
        }
        if (!changed) return 0;

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            int batchId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(MAX(batch_id), 0) + 1 FROM currency_rate");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                batchId = rs.getInt(1);
            }

            // Tüm güncel kurları arşivle
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE currency_rate SET status = 0 WHERE status = 1")) {
                ps.executeUpdate();
            }

            // Yeni bloğu ekle (aynı batch_id)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO currency_rate "
                  + "(currency, buy_rate, sell_rate, effective_buy, effective_sell, status, batch_id) "
                  + "VALUES (?, ?, ?, ?, ?, 1, ?)")) {
                for (Map.Entry<String, double[]> e : rates.entrySet()) {
                    double[] v = e.getValue();
                    ps.setString(1, e.getKey());
                    ps.setDouble(2, v[0]);
                    ps.setDouble(3, v[1]);
                    if (v.length > 2 && v[2] > 0) ps.setDouble(4, v[2]); else ps.setNull(4, java.sql.Types.DECIMAL);
                    if (v.length > 3 && v[3] > 0) ps.setDouble(5, v[3]); else ps.setNull(5, java.sql.Types.DECIMAL);
                    ps.setInt(6, batchId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit();
            return batchId;
        } catch (SQLException e) {
            System.err.println("Kur bloğu güncellenemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Kur blok güncelleme");
        }
        return 0;
    }

    private List<CurrencyRate> query(String sql) {
        List<CurrencyRate> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Kur sorgusu hatası: " + e.getMessage());
            ErrorLogDAO.log(e, "Kur sorgusu");
        }
        return list;
    }

    private CurrencyRate mapRow(ResultSet rs) throws SQLException {
        CurrencyRate r = new CurrencyRate(
                rs.getString("currency"),
                rs.getDouble("buy_rate"),
                rs.getDouble("sell_rate"),
                rs.getInt("status"),
                rs.getInt("batch_id"),
                String.valueOf(rs.getTimestamp("updated_at")));
        r.setEffectiveBuy(rs.getDouble("effective_buy"));
        r.setEffectiveSell(rs.getDouble("effective_sell"));
        return r;
    }
}
