package com.gtech.treasury.util;

import com.gtech.treasury.dao.ErrorLogDAO;
import com.gtech.treasury.dao.LendingDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * KRS (Kredi Referans Sistemi) ve KDS (Karar Destek Sistemi) simülasyonu.
 *
 * Gerçek bankacılıkta KRS/KDS dış sistemlerden (ör. KKB/Findeks) gelir.
 * Bu projede aynı bilgiler MÜŞTERİNİN KENDİ VERİSİNDEN üretilir:
 *   - KRS notu (0–1900): ödeme geçmişi, gecikme, aktif kredi, borç/varlık oranı.
 *   - KDS kararı: KRS notu + talep/limit uygunluğuna göre Onayla / İncele / Reddet.
 */
public final class CreditScoreService {

    private CreditScoreService() {}

    public static final String ONAYLA = "ONAYLA";
    public static final String INCELE = "İNCELE";
    public static final String REDDET = "REDDET";

    /** Sonuç: KRS + KDS bir arada. */
    public static class Result {
        public int krsScore;
        public String krsBand;
        public int activeLoans;
        public double outstanding;   // kalan borç (TL, ödenmemiş taksit toplamı)
        public int totalInstallments, paidInstallments, overdueInstallments;
        public double onTimeRatio;   // 0..1
        public int overdueLoans;     // gecikmiş taksiti olan ayrı kredi sayısı

        // Hesap hareketliliği (son 90 gün) — müşteri odaklı KDS araştırması
        public int txCount90;        // son 90 gündeki finansal hareket sayısı
        public double inflow90;      // son 90 gün para girişi (TL yaklaşık)
        public double outflow90;     // son 90 gün para çıkışı (TL yaklaşık)
        public int daysSinceLastTx = -1;  // en son hareketten bu yana gün (-1: hareket yok)

        public String kdsDecision;   // ONAYLA / İNCELE / REDDET
        public List<String> reasons = new ArrayList<>();
    }

    /** Geriye dönük uyum: customerNo bilinmiyorsa customerId ile çağrılır (hesap hareketi araştırması atlanır). */
    public static Result evaluate(int customerId, LendingDAO.Evaluation ev, double amount) {
        return evaluate(customerId, 0, ev, amount);
    }

    /**
     * @param customerId müşteri PK (kredi/taksit geçmişi için)
     * @param customerNo müşteri numarası (activity_log hesap hareketleri için; 0 ise atlanır)
     * @param ev         LendingDAO.evaluate sonucu (limit/uygunluk)
     * @param amount     talep edilen tutar
     */
    public static Result evaluate(int customerId, int customerNo, LendingDAO.Evaluation ev, double amount) {
        Result r = new Result();
        loadHistory(customerId, r);
        if (customerNo > 0) loadAccountActivity(customerNo, r);

        // ---- KRS notu (0–1900) ---- (DEĞİŞTİRİLMEDİ)
        double score = 1100;
        if (r.overdueInstallments > 0) score -= 180.0 * r.overdueInstallments;
        if (r.totalInstallments > 0)   score += r.onTimeRatio * 300.0;
        if (r.activeLoans > 1)         score -= 40.0 * (r.activeLoans - 1);

        double assets = Math.max(0, ev.totalBalance);
        if (r.outstanding <= 0) {
            score += 80;                                   // borcu yok
        } else if (assets > 0) {
            double ratio = r.outstanding / assets;         // borç / varlık
            score -= Math.min(300, ratio * 300.0);
        } else {
            score -= 150;                                  // borç var, varlık yok
        }
        score += Math.min(200, assets / 50_000.0 * 100.0); // varlık bonusu (ılımlı)

        r.krsScore = (int) Math.max(0, Math.min(1900, Math.round(score)));
        r.krsBand = band(r.krsScore);

        // ============ KDS — müşteri odaklı araştırma ============
        // Karar KRS'nin yanı sıra müşterinin ödeme geçmişi, açık borcu ve HESAP HAREKETLERİNE bakar.
        // Her durumda (riskli ya da iyi) gerekçeler r.reasons içine yazılır.
        boolean dormant = customerNo > 0 && r.txCount90 == 0;                    // hesap hareketsiz
        boolean cashDrain = r.outflow90 > 0 && r.inflow90 < r.outflow90 * 0.5;   // çıkış girişin çok üstünde

        // --- Karar ---
        if (r.overdueInstallments > 0)      r.kdsDecision = REDDET;
        else if (r.krsScore < 600)          r.kdsDecision = REDDET;
        else if (!ev.eligible)              r.kdsDecision = INCELE;
        else if (r.krsScore < 1000)         r.kdsDecision = INCELE;
        else if (dormant || cashDrain)      r.kdsDecision = INCELE;   // profil iyi ama hareket riski
        else                                r.kdsDecision = ONAYLA;

        // --- Özet başlık ---
        r.reasons.add(ONAYLA.equals(r.kdsDecision)
                ? "Sonuç: Müşteri profili uygun — onay önerilir."
                : REDDET.equals(r.kdsDecision)
                    ? "Sonuç: Yüksek risk — red önerilir."
                    : "Sonuç: Belirsizlik var — manuel inceleme önerilir.");

        // --- 1) Ödeme geçmişi / ödenmeyen kredi ---
        if (r.overdueInstallments > 0)
            r.reasons.add("⚠ Ödenmemiş/geciken taksit: " + r.overdueInstallments + " adet"
                    + (r.overdueLoans > 1 ? " (" + r.overdueLoans + " ayrı kredide)" : "") + ".");
        else if (r.totalInstallments > 0)
            r.reasons.add(String.format("✓ Geçmiş taksitlerde gecikme yok (zamanında ödeme %%%.0f — %d/%d).",
                    r.onTimeRatio * 100, r.paidInstallments, r.totalInstallments));
        else
            r.reasons.add("• Geçmiş kredi/taksit kaydı yok (ilk kredi başvurusu).");

        // --- 2) Mevcut borç yükü ---
        if (r.outstanding <= 0)
            r.reasons.add("✓ Açık (ödenmemiş) kredi borcu bulunmuyor.");
        else
            r.reasons.add("• Aktif kredi: " + r.activeLoans + ", kalan borç "
                    + String.format("%,.0f ₺", r.outstanding) + ".");

        // --- 3) Hesap hareketliliği (son 90 gün) ---
        if (customerNo > 0) {
            if (r.txCount90 == 0)
                r.reasons.add("⚠ Son 90 günde hesap hareketi yok"
                        + (r.daysSinceLastTx >= 0 ? " (son işlem " + r.daysSinceLastTx + " gün önce)" : "")
                        + " — düzenli gelir/kullanım teyit edilemiyor.");
            else {
                r.reasons.add("✓ Son 90 günde " + r.txCount90 + " hesap hareketi (aktif kullanım).");
                if (cashDrain)
                    r.reasons.add("⚠ Para çıkışı girişin belirgin üzerinde (giriş "
                            + String.format("%,.0f ₺", r.inflow90) + " / çıkış "
                            + String.format("%,.0f ₺", r.outflow90) + ").");
                else if (r.inflow90 >= r.outflow90 && r.inflow90 > 0)
                    r.reasons.add("✓ Para giriş/çıkış dengesi olumlu (giriş "
                            + String.format("%,.0f ₺", r.inflow90) + " ≥ çıkış "
                            + String.format("%,.0f ₺", r.outflow90) + ").");
            }
        }

        // --- 4) Talep-limit uyumu ---
        r.reasons.add(ev.eligible
                ? "✓ Talep tutarı uygun üst limit içinde."
                : "⚠ Talep, uygun üst limitin üzerinde (" + String.format("%,.0f ₺", ev.maxEligible) + ").");

        // --- 5) KRS notu ---
        r.reasons.add((r.krsScore >= 1000 ? "✓ " : r.krsScore < 600 ? "⚠ " : "• ")
                + "KRS notu " + r.krsScore + " / 1900 (" + r.krsBand + ").");

        return r;
    }

    private static String band(int s) {
        if (s >= 1500) return "Çok İyi";
        if (s >= 1200) return "İyi";
        if (s >= 900)  return "Orta";
        if (s >= 600)  return "Zayıf";
        return "Riskli";
    }

    /** Müşterinin kredi/taksit geçmişini DB'den doldurur. */
    private static void loadHistory(int customerId, Result r) {
        String qLoans = "SELECT COUNT(*) FROM lending WHERE customer_id = ? AND status = 1";
        String qOutstanding = "SELECT COALESCE(SUM(li.amount),0) FROM loan_installment li "
                + "JOIN lending l ON li.lending_id = l.id "
                + "WHERE l.customer_id = ? AND li.status = 0";
        String qInst = "SELECT COUNT(*) total, "
                + "SUM(CASE WHEN li.status = 1 THEN 1 ELSE 0 END) paid, "
                + "SUM(CASE WHEN li.status = 0 AND li.due_date < CURDATE() THEN 1 ELSE 0 END) overdue, "
                + "SUM(CASE WHEN li.status = 1 AND (li.paid_date IS NULL OR li.paid_date <= li.due_date) THEN 1 ELSE 0 END) ontime "
                + "FROM loan_installment li JOIN lending l ON li.lending_id = l.id WHERE l.customer_id = ?";
        try (Connection c = DBConnection.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(qLoans)) {
                ps.setInt(1, customerId);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) r.activeLoans = rs.getInt(1); }
            }
            try (PreparedStatement ps = c.prepareStatement(qOutstanding)) {
                ps.setInt(1, customerId);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) r.outstanding = rs.getDouble(1); }
            }
            try (PreparedStatement ps = c.prepareStatement(qInst)) {
                ps.setInt(1, customerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        r.totalInstallments = rs.getInt("total");
                        r.paidInstallments = rs.getInt("paid");
                        r.overdueInstallments = rs.getInt("overdue");
                        int ontime = rs.getInt("ontime");
                        r.onTimeRatio = r.totalInstallments > 0 ? (double) ontime / r.totalInstallments : 1.0;
                    }
                }
            }
            // Gecikmiş taksiti olan AYRI kredi sayısı
            String qOverdueLoans = "SELECT COUNT(DISTINCT l.id) FROM loan_installment li "
                    + "JOIN lending l ON li.lending_id = l.id "
                    + "WHERE l.customer_id = ? AND li.status = 0 AND li.due_date < CURDATE()";
            try (PreparedStatement ps = c.prepareStatement(qOverdueLoans)) {
                ps.setInt(1, customerId);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) r.overdueLoans = rs.getInt(1); }
            }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "KRS/KDS geçmiş");
        }
    }

    /**
     * Müşterinin son 90 gündeki HESAP HAREKETLERİNİ (activity_log) araştırır:
     * hareket sayısı, TL yaklaşık para giriş/çıkış toplamı ve son hareket tarihi.
     * Yabancı para tutarlar alış kuruyla TL'ye yaklaşık çevrilir (currency_rate).
     */
    private static void loadAccountActivity(int customerNo, Result r) {
        String conv = "CASE WHEN al.currency IS NULL OR al.currency = 'TRY' THEN 1 "
                    + "ELSE COALESCE(cr.buy_rate, 1) END";
        String inflow  = "('ACCOUNT_DEPOSIT','LOAN_DISBURSED','DEPOSIT_CLOSE','DEPOSIT_BREAK','SPOT_SELL')";
        String outflow = "('ACCOUNT_WITHDRAW','LOAN_INSTALLMENT','LOAN_REPAID','DEPOSIT_APPLIED',"
                       + "'TRANSFER','EFT','FAST','SPOT_BUY')";
        String sql = "SELECT COUNT(*) cnt, "
                + "COALESCE(SUM(CASE WHEN al.action_type IN " + inflow  + " THEN al.amount * " + conv + " ELSE 0 END),0) inflow, "
                + "COALESCE(SUM(CASE WHEN al.action_type IN " + outflow + " THEN al.amount * " + conv + " ELSE 0 END),0) outflow "
                + "FROM activity_log al "
                + "LEFT JOIN currency_rate cr ON cr.currency = al.currency AND cr.status = 1 "
                + "WHERE al.customer_no = ? AND al.amount IS NOT NULL "
                + "AND al.created_at >= DATE_SUB(CURDATE(), INTERVAL 90 DAY)";
        String qLast = "SELECT DATEDIFF(CURDATE(), DATE(MAX(created_at))) FROM activity_log "
                + "WHERE customer_no = ? AND amount IS NOT NULL";
        try (Connection c = DBConnection.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, customerNo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        r.txCount90 = rs.getInt("cnt");
                        r.inflow90 = rs.getDouble("inflow");
                        r.outflow90 = rs.getDouble("outflow");
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(qLast)) {
                ps.setInt(1, customerNo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) { int d = rs.getInt(1); r.daysSinceLastTx = rs.wasNull() ? -1 : d; }
                }
            }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "KDS hesap hareketi");
        }
    }
}
