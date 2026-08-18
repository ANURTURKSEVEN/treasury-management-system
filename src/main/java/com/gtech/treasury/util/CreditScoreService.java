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
        public String kdsDecision;   // ONAYLA / İNCELE / REDDET
        public List<String> reasons = new ArrayList<>();
    }

    /**
     * @param customerId müşteri
     * @param ev         LendingDAO.evaluate sonucu (limit/uygunluk)
     * @param amount     talep edilen tutar
     */
    public static Result evaluate(int customerId, LendingDAO.Evaluation ev, double amount) {
        Result r = new Result();
        loadHistory(customerId, r);

        // ---- KRS notu (0–1900) ----
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

        // ---- KDS kararı ----
        if (r.overdueInstallments > 0) {
            r.kdsDecision = REDDET;
            r.reasons.add("Gecikmiş taksit var (" + r.overdueInstallments + " adet).");
        } else if (r.krsScore < 600) {
            r.kdsDecision = REDDET;
            r.reasons.add("KRS notu çok düşük (" + r.krsScore + ").");
        } else if (!ev.eligible) {
            r.kdsDecision = INCELE;
            r.reasons.add("Talep uygun üst limitin üzerinde (" + String.format("%,.0f ₺", ev.maxEligible) + ").");
        } else if (r.krsScore < 1000) {
            r.kdsDecision = INCELE;
            r.reasons.add("KRS notu orta seviyede (" + r.krsScore + ") — manuel inceleme önerilir.");
        } else {
            r.kdsDecision = ONAYLA;
            r.reasons.add("KRS notu iyi (" + r.krsScore + ") ve talep limit içinde.");
        }
        if (r.activeLoans > 0) r.reasons.add("Aktif kredi sayısı: " + r.activeLoans
                + " (kalan borç " + String.format("%,.0f ₺", r.outstanding) + ").");
        if (r.totalInstallments > 0) r.reasons.add(String.format(
                "Zamanında ödeme oranı: %%%.0f (%d/%d taksit).",
                r.onTimeRatio * 100, r.paidInstallments, r.totalInstallments));
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
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "KRS/KDS geçmiş");
        }
    }
}
