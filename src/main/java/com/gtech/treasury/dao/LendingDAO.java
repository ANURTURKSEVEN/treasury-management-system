package com.gtech.treasury.dao;

import com.gtech.treasury.model.Account;
import com.gtech.treasury.model.ActivityLog;
import com.gtech.treasury.model.CurrencyRate;
import com.gtech.treasury.model.Installment;
import com.gtech.treasury.model.Lending;
import com.gtech.treasury.model.OverdueInstallment;
import com.gtech.treasury.util.DBConnection;
import com.gtech.treasury.util.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Kredi (Lending): başvuru → değerlendirme → onay/red → geri ödeme.
 * Onayda banka kasasından çıkar, müşteri hesabına eklenir; geri ödemede tersi.
 * Uygunluk, müşterinin bakiyesi + son 90 gün nakit girişine göre hesaplanır.
 */
public class LendingDAO {

    private static final int BANK_CUSTOMER_NO = 99999999;

    /** Kredi türleri: etiket, yıllık faiz %, üst limit (TL), max vade (ay). */
    public enum LoanType {
        IHTIYAC("İhtiyaç Kredisi", 35.0, 500_000, 36),
        TASIT("Taşıt Kredisi", 30.0, 2_000_000, 48),
        KONUT("Konut Kredisi", 24.0, 10_000_000, 120);

        public final String label; public final double rate; public final double maxCap; public final int maxTerm;
        LoanType(String label, double rate, double maxCap, int maxTerm) {
            this.label = label; this.rate = rate; this.maxCap = maxCap; this.maxTerm = maxTerm;
        }
        public static LoanType of(String name) {
            try { return valueOf(name); } catch (Exception e) { return IHTIYAC; }
        }
        public static String labelOf(String name) { return of(name).label; }
    }

    // ---- Annuite ----
    public static double monthlyPayment(double principal, double annualRatePct, int months) {
        if (months <= 0) return 0;
        double r = annualRatePct / 100.0 / 12.0;
        if (r <= 0) return principal / months;
        double pow = Math.pow(1 + r, months);
        return principal * r * pow / (pow - 1);
    }
    public static double totalDue(double principal, double annualRatePct, int months) {
        return Math.round(monthlyPayment(principal, annualRatePct, months) * months * 100.0) / 100.0;
    }
    /** Aylık taksitten geri anaparaya (annuite tersi). */
    private static double principalFromInstallment(double inst, double annualRatePct, int months) {
        if (months <= 0 || inst <= 0) return 0;
        double r = annualRatePct / 100.0 / 12.0;
        if (r <= 0) return inst * months;
        double pow = Math.pow(1 + r, months);
        return inst * (pow - 1) / (r * pow);
    }

    // ---- Kredi puanı / uygunluk değerlendirmesi ----
    public static class Evaluation {
        public final double monthlyIncome, totalBalance, maxEligible;
        public final boolean eligible;
        public final String risk, note;
        Evaluation(double income, double balance, double max, boolean eligible, String risk, String note) {
            this.monthlyIncome = income; this.totalBalance = balance; this.maxEligible = max;
            this.eligible = eligible; this.risk = risk; this.note = note;
        }
    }

    /** Müşterinin talebine göre uygunluk değerlendirmesi. */
    public Evaluation evaluate(int customerId, LoanType type, double amount, double rate, int months) {
        AccountDAO accountDAO = new AccountDAO();
        RateDAO rateDAO = new RateDAO();
        ActivityLogDAO activityDAO = new ActivityLogDAO();

        List<Account> accounts = accountDAO.getByCustomer(customerId);
        double totalBalance = 0;
        java.util.Set<String> myAcc = new java.util.HashSet<>();
        for (Account a : accounts) {
            totalBalance += toTry(rateDAO, a.getBalance(), a.getCurrency());
            myAcc.add(String.valueOf(a.getAccountNo()));
        }

        // Son 90 günün nakit girişi (yatırma + gelen transfer) -> aylık gelir proxy
        java.util.Map<Integer, ActivityLog> rows = new java.util.LinkedHashMap<>();
        for (Account a : accounts) for (ActivityLog al : activityDAO.byAccountNo(a.getAccountNo())) rows.put(al.getId(), al);
        double inflow90 = 0;
        LocalDate cutoff = LocalDate.now().minusDays(90);
        for (ActivityLog al : rows.values()) {
            LocalDate d;
            try { d = LocalDate.parse(al.getDatePart().substring(0, 10)); } catch (Exception e) { continue; }
            if (d.isBefore(cutoff)) continue;
            double tl = toTry(rateDAO, al.getAmount(), al.getCurrency());
            String t = al.getActionType();
            if ("ACCOUNT_DEPOSIT".equals(t)) inflow90 += tl;
            else if (("TRANSFER".equals(t) || "EFT".equals(t) || "FAST".equals(t)) && targetIsMine(al.getDescription(), myAcc)) inflow90 += tl;
        }
        double monthlyIncome = inflow90 / 3.0;

        // Limitler: taksit <= gelirin %50'si; bakiyenin 4 katı; tür üst limiti
        double maxInstallment = 0.5 * monthlyIncome;
        double maxByIncome = principalFromInstallment(maxInstallment, rate, months);
        double maxByBalance = totalBalance * 4.0;
        double maxEligible = Math.min(Math.min(maxByIncome, maxByBalance), type.maxCap);
        maxEligible = Math.max(0, Math.round(maxEligible));

        boolean eligible = amount <= maxEligible && amount > 0;
        double ratio = maxEligible > 0 ? amount / maxEligible : (amount > 0 ? 2 : 0);
        String risk = ratio <= 0.5 ? "Düşük" : ratio <= 1.0 ? "Orta" : "Yüksek";
        String note = eligible
                ? "Talep uygun limit içinde — onaylanabilir."
                : "Talep uygun limitin ÜZERİNDE — riskli. (Gelir/bakiye yetersiz.)";
        return new Evaluation(monthlyIncome, totalBalance, maxEligible, eligible, risk, note);
    }

    // ---- Başvuru (müşteri) ----
    public String apply(Account target, LoanType type, double amount, double rate, int months) {
        if (target == null) return "Hesap seçilmedi.";
        if (amount <= 0) return "Anapara sıfırdan büyük olmalı.";
        if (months <= 0 || months > type.maxTerm) return "Vade en fazla " + type.maxTerm + " ay olabilir.";
        if (amount > type.maxCap) return type.label + " üst limiti: " + String.format("%,.0f", type.maxCap) + " ₺.";

        double monthly = Math.round(monthlyPayment(amount, rate, months) * 100.0) / 100.0;
        double total = Math.round(monthly * months * 100.0) / 100.0;

        // Başvuru anındaki KRS/KDS sonucu — denetim izi için dondurulur
        Evaluation ev = evaluate(target.getCustomerId(), type, amount, rate, months);
        com.gtech.treasury.util.CreditScoreService.Result cs =
                com.gtech.treasury.util.CreditScoreService.evaluate(target.getCustomerId(), ev, amount);

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO lending (customer_id, account_id, loan_type, currency, amount, interest_rate, "
                   + "term_months, monthly_payment, total_due, status, krs_score, krs_band, kds_decision, evaluated_at) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, NOW())")) {
            ps.setInt(1, target.getCustomerId());
            ps.setInt(2, target.getAccountId());
            ps.setString(3, type.name());
            ps.setString(4, target.getCurrency());
            ps.setDouble(5, amount);
            ps.setDouble(6, rate);
            ps.setInt(7, months);
            ps.setDouble(8, monthly);
            ps.setDouble(9, total);
            ps.setInt(10, cs.krsScore);
            ps.setString(11, cs.krsBand);
            ps.setString(12, cs.kdsDecision);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Kredi başvurusu hatası: " + e.getMessage());
            ErrorLogDAO.log(e, "Kredi başvuru");
            return "Başvuru kaydedilemedi: " + e.getMessage();
        }
        ActivityLogDAO.log("LOAN_APPLIED", target.getCustomerNo(), amount, target.getCurrency(),
                type.label + " başvurusu: " + String.format("%,.2f %s", amount, target.getCurrency()) + " / " + months + " ay",
                "Değerlendirme bekliyor. | KRS: " + cs.krsScore + " (" + cs.krsBand + ")"
                        + " | KDS: " + cs.kdsDecision);
        return null;
    }

    // ---- Onay (admin): parayı kullandır ----
    public String approve(int lendingId) {
        String sel = "SELECT customer_id, account_id, currency, amount, term_months, monthly_payment, total_due "
                   + "FROM lending WHERE id = ? AND status = 0";
        String selBank = "SELECT a.account_id, a.balance FROM account a "
                       + "JOIN customer c ON a.customer_id = c.customer_id AND c.customer_no = ? "
                       + "WHERE a.currency = ? AND a.status = 1 ORDER BY a.account_id LIMIT 1";
        String upd = "UPDATE account SET balance = balance + ? WHERE account_id = ?";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int custId, accId, months; String cur; double amount, monthly, total;
                try (PreparedStatement ps = conn.prepareStatement(sel)) {
                    ps.setInt(1, lendingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return "Bekleyen başvuru bulunamadı."; }
                        custId = rs.getInt("customer_id"); accId = rs.getInt("account_id");
                        cur = rs.getString("currency"); amount = rs.getDouble("amount"); months = rs.getInt("term_months");
                        monthly = rs.getDouble("monthly_payment"); total = rs.getDouble("total_due");
                    }
                }
                int bankAccId; double bankBal;
                try (PreparedStatement ps = conn.prepareStatement(selBank)) {
                    ps.setInt(1, BANK_CUSTOMER_NO); ps.setString(2, cur);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return "Banka " + cur + " kasası yok."; }
                        bankAccId = rs.getInt(1); bankBal = rs.getDouble(2);
                    }
                }
                if (bankBal < amount) { conn.rollback(); return "Banka " + cur + " kasası kredi için yetersiz."; }

                // Banka düşümü ATOMİK + guard'lı (eşzamanlı onaylarda negatif kasa olmaz)
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE account SET balance = balance - ? WHERE account_id = ? AND status = 1 AND balance >= ?")) {
                    ps.setDouble(1, amount); ps.setInt(2, bankAccId); ps.setDouble(3, amount);
                    if (ps.executeUpdate() == 0) { conn.rollback(); return "Banka " + cur + " kasası kredi için yetersiz."; }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE account SET balance = balance + ? WHERE account_id = ? AND status = 1")) {
                    ps.setDouble(1, amount); ps.setInt(2, accId);
                    if (ps.executeUpdate() == 0) { conn.rollback(); return "Müşteri hesabı kapalı; kredi kullandırılamadı."; }
                }
                LocalDate start = LocalDate.now(), maturity = start.plusMonths(months);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE lending SET status = 1, start_date = ?, maturity_date = ? WHERE id = ?")) {
                    ps.setString(1, start.toString()); ps.setString(2, maturity.toString()); ps.setInt(3, lendingId);
                    ps.executeUpdate();
                }
                // Taksit planı: her ay bir taksit (son taksit yuvarlama farkını taşır)
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO loan_installment (lending_id, seq_no, due_date, amount) VALUES (?, ?, ?, ?)")) {
                    for (int i = 1; i <= months; i++) {
                        double inst = (i < months) ? monthly
                                : Math.round((total - monthly * (months - 1)) * 100.0) / 100.0;
                        ps.setInt(1, lendingId);
                        ps.setInt(2, i);
                        ps.setString(3, start.plusMonths(i).toString());
                        ps.setDouble(4, inst);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();

                int custNo = customerNo(custId);
                ActivityLogDAO.log("LOAN_GIVEN", custNo, amount, cur,
                        "Kredi onaylandı ve kullandırıldı: " + String.format("%,.2f %s", amount, cur),
                        "Kredi #" + lendingId + " | Vade: " + maturity);
                CustomerSnapshotDAO.record(custId);
                TreasurySnapshotDAO.record();
                new NotificationDAO().add(custNo,
                        "Kredi başvurunuz ONAYLANDI",
                        "Hesabınıza " + String.format("%,.2f %s", amount, cur) + " kredi tanımlandı. (Kredi #" + lendingId + ")");
                return null;
            } catch (SQLException e) { conn.rollback(); throw e; }
        } catch (SQLException e) {
            System.err.println("Kredi onay hatası: " + e.getMessage());
            ErrorLogDAO.log(e, "Kredi onay");
            return "İşlem sırasında hata: " + e.getMessage();
        }
    }

    // ---- Red (admin) ----
    public String reject(int lendingId, String reason) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE lending SET status = 2, reject_reason = ? WHERE id = ? AND status = 0")) {
            ps.setString(1, reason == null ? "-" : reason);
            ps.setInt(2, lendingId);
            if (ps.executeUpdate() == 0) return "Bekleyen başvuru bulunamadı.";
        } catch (SQLException e) {
            System.err.println("Kredi red hatası: " + e.getMessage());
            ErrorLogDAO.log(e, "Kredi red");
            return "İşlem sırasında hata: " + e.getMessage();
        }
        // müşteri no + bildirim
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT customer_id FROM lending WHERE id = ?")) {
            ps.setInt(1, lendingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int custNo = customerNo(rs.getInt(1));
                    ActivityLogDAO.log("LOAN_REJECTED", custNo, 0, null,
                            "Kredi başvurusu reddedildi (Kredi #" + lendingId + ")", "Sebep: " + reason);
                    new NotificationDAO().add(custNo, "Kredi başvurunuz REDDEDİLDİ",
                            "Sebep: " + (reason == null ? "-" : reason) + " (Kredi #" + lendingId + ")");
                }
            }
        } catch (SQLException ignored) { }
        return null;
    }

    // ---- Erken kapama: kalan TÜM taksitleri öde ----
    public String repay(int lendingId) {
        String sel = "SELECT customer_id, account_id, currency FROM lending WHERE id = ? AND status = 1";
        String selRemain = "SELECT COALESCE(SUM(amount),0) FROM loan_installment WHERE lending_id = ? AND status = 0";
        String selBank = "SELECT a.account_id FROM account a "
                       + "JOIN customer c ON a.customer_id = c.customer_id AND c.customer_no = ? "
                       + "WHERE a.currency = ? AND a.status = 1 ORDER BY a.account_id LIMIT 1";
        String debit = "UPDATE account SET balance = balance - ? WHERE account_id = ? AND status = 1 AND balance >= ?";
        String upd = "UPDATE account SET balance = balance + ? WHERE account_id = ?";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int custId, accId; String cur; double remaining;
                try (PreparedStatement ps = conn.prepareStatement(sel)) {
                    ps.setInt(1, lendingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return "Aktif kredi bulunamadı."; }
                        custId = rs.getInt("customer_id"); accId = rs.getInt("account_id"); cur = rs.getString("currency");
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(selRemain)) {
                    ps.setInt(1, lendingId);
                    try (ResultSet rs = ps.executeQuery()) { rs.next(); remaining = rs.getDouble(1); }
                }
                if (remaining <= 0) { conn.rollback(); return "Ödenecek taksit yok."; }

                try (PreparedStatement ps = conn.prepareStatement(debit)) {
                    ps.setDouble(1, remaining); ps.setInt(2, accId); ps.setDouble(3, remaining);
                    if (ps.executeUpdate() == 0) { conn.rollback(); return "Hesap bakiyesi yetersiz. Gerekli: " + String.format("%,.2f %s", remaining, cur); }
                }
                int bankAccId;
                try (PreparedStatement ps = conn.prepareStatement(selBank)) {
                    ps.setInt(1, BANK_CUSTOMER_NO); ps.setString(2, cur);
                    try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) { conn.rollback(); return "Banka kasası yok."; } bankAccId = rs.getInt(1); }
                }
                move(conn, upd, +remaining, bankAccId);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE loan_installment SET status = 1, paid_date = CURDATE() WHERE lending_id = ? AND status = 0")) {
                    ps.setInt(1, lendingId); ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("UPDATE lending SET status = 3 WHERE id = ?")) {
                    ps.setInt(1, lendingId); ps.executeUpdate();
                }
                conn.commit();
                int custNo = customerNo(custId);
                ActivityLogDAO.log("LOAN_REPAID", custNo, remaining, cur,
                        "Kredi erken kapatıldı: " + String.format("%,.2f %s", remaining, cur), "Kredi #" + lendingId);
                CustomerSnapshotDAO.record(custId);
                TreasurySnapshotDAO.record();
                return null;
            } catch (SQLException e) { conn.rollback(); throw e; }
        } catch (SQLException e) {
            System.err.println("Kredi geri ödeme hatası: " + e.getMessage());
            ErrorLogDAO.log(e, "Kredi geri ödeme");
            return "İşlem sırasında hata: " + e.getMessage();
        }
    }

    // ---- Taksitler ----

    /** Aktif ama taksit planı olmayan (eski) krediler için planı geriye dönük üretir. */
    private void ensureInstallments(int lendingId) {
        try (Connection conn = DBConnection.getConnection()) {
            int cnt;
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM loan_installment WHERE lending_id = ?")) {
                ps.setInt(1, lendingId);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); cnt = rs.getInt(1); }
            }
            if (cnt > 0) return;

            int status, months; double monthly, total; String startStr;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT status, term_months, monthly_payment, total_due, start_date FROM lending WHERE id = ?")) {
                ps.setInt(1, lendingId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return;
                    status = rs.getInt("status"); months = rs.getInt("term_months");
                    monthly = rs.getDouble("monthly_payment"); total = rs.getDouble("total_due");
                    java.sql.Date d = rs.getDate("start_date"); startStr = (d == null) ? null : d.toString();
                }
            }
            if (status != 1 || months <= 0) return;
            LocalDate start = (startStr != null) ? LocalDate.parse(startStr) : LocalDate.now();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO loan_installment (lending_id, seq_no, due_date, amount) VALUES (?, ?, ?, ?)")) {
                for (int i = 1; i <= months; i++) {
                    double inst = (i < months) ? monthly : Math.round((total - monthly * (months - 1)) * 100.0) / 100.0;
                    ps.setInt(1, lendingId); ps.setInt(2, i);
                    ps.setString(3, start.plusMonths(i).toString()); ps.setDouble(4, inst);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            System.err.println("Taksit backfill hatası: " + e.getMessage());
        }
    }

    /** Kredi ödeme talimatını değiştir (otomatik/manuel). Aktif kredide çalışır. */
    public String setAutoPay(int lendingId, boolean auto) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE lending SET auto_pay = ? WHERE id = ? AND status = 1")) {
            ps.setInt(1, auto ? 1 : 0); ps.setInt(2, lendingId);
            if (ps.executeUpdate() == 0) return "Güncellenemedi (aktif kredi bulunamadı).";
            ActivityLogDAO.log("LOAN_AUTOPAY", "Kredi #" + lendingId + " ödeme talimatı: "
                    + (auto ? "Otomatik" : "Manuel"));
            return null;
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "Ödeme talimatı güncelleme");
            return "Güncellenemedi: " + e.getMessage();
        }
    }

    /** Personel: ödenmemiş bir taksitin vade tarihini öne/geri alır. */
    public String updateInstallmentDate(int installmentId, String newDate) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE loan_installment SET due_date = ? WHERE id = ? AND status = 0")) {
            ps.setString(1, newDate); ps.setInt(2, installmentId);
            if (ps.executeUpdate() == 0) return "Taksit güncellenemedi (ödenmiş olabilir veya bulunamadı).";
            ActivityLogDAO.log("INSTALLMENT_EDIT", "Taksit #" + installmentId + " vade tarihi -> " + newDate);
            return null;
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "Taksit tarihi güncelleme");
            return "Güncellenemedi: " + e.getMessage();
        }
    }

    public List<Installment> getInstallments(int lendingId) {
        ensureInstallments(lendingId);
        List<Installment> list = new ArrayList<>();
        String sql = "SELECT id, seq_no, due_date, amount, status, paid_date FROM loan_installment "
                   + "WHERE lending_id = ? ORDER BY seq_no";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lendingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Installment(rs.getInt("id"), rs.getInt("seq_no"),
                            String.valueOf(rs.getDate("due_date")), rs.getDouble("amount"),
                            rs.getInt("status"), String.valueOf(rs.getDate("paid_date"))));
                }
            }
        } catch (SQLException e) {
            System.err.println("Taksitler getirilemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Taksit listeleme");
        }
        return list;
    }

    /** Bir taksiti tahsil eder (müşteri hesabından çek, bankaya ekle). Son taksitse krediyi kapatır. */
    /** Gecikme faizi çarpanı: akdi faizin katı (yasal üst sınıra yakın). */
    private static final double LATE_MULT = 1.3;

    public String payInstallment(int installmentId) {
        String selI = "SELECT lending_id, amount, status, due_date FROM loan_installment WHERE id = ?";
        String selL = "SELECT customer_id, account_id, currency, status, interest_rate FROM lending WHERE id = ?";
        String selBank = "SELECT a.account_id FROM account a "
                       + "JOIN customer c ON a.customer_id = c.customer_id AND c.customer_no = ? "
                       + "WHERE a.currency = ? AND a.status = 1 ORDER BY a.account_id LIMIT 1";
        String debit = "UPDATE account SET balance = balance - ? WHERE account_id = ? AND status = 1 AND balance >= ?";
        String upd = "UPDATE account SET balance = balance + ? WHERE account_id = ?";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int lendingId, iStatus; double amount; String dueStr;
                try (PreparedStatement ps = conn.prepareStatement(selI)) {
                    ps.setInt(1, installmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return "Taksit bulunamadı."; }
                        lendingId = rs.getInt("lending_id"); amount = rs.getDouble("amount");
                        iStatus = rs.getInt("status"); dueStr = String.valueOf(rs.getDate("due_date"));
                    }
                }
                if (iStatus == 1) { conn.rollback(); return "Taksit zaten ödendi."; }

                int custId, accId, lStatus; String cur; double rate;
                try (PreparedStatement ps = conn.prepareStatement(selL)) {
                    ps.setInt(1, lendingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return "Kredi bulunamadı."; }
                        custId = rs.getInt("customer_id"); accId = rs.getInt("account_id");
                        cur = rs.getString("currency"); lStatus = rs.getInt("status"); rate = rs.getDouble("interest_rate");
                    }
                }
                if (lStatus != 1) { conn.rollback(); return "Kredi aktif değil."; }

                // GECİKME FAİZİ: vade tarihi geçmişse geçen gün başına gecikme faizi
                long daysLate = 0;
                try {
                    LocalDate due = LocalDate.parse(dueStr);
                    if (LocalDate.now().isAfter(due))
                        daysLate = java.time.temporal.ChronoUnit.DAYS.between(due, LocalDate.now());
                } catch (Exception ignored) { }
                double lateFee = daysLate > 0
                        ? Math.round(amount * (rate / 100.0 / 365.0) * LATE_MULT * daysLate * 100.0) / 100.0 : 0;
                double charge = Math.round((amount + lateFee) * 100.0) / 100.0;

                try (PreparedStatement ps = conn.prepareStatement(debit)) {
                    ps.setDouble(1, charge); ps.setInt(2, accId); ps.setDouble(3, charge);
                    if (ps.executeUpdate() == 0) { conn.rollback(); return "Hesap bakiyesi taksit için yetersiz. Gerekli: " + String.format("%,.2f %s", charge, cur)
                            + (lateFee > 0 ? " (gecikme faizi dahil)" : ""); }
                }
                int bankAccId;
                try (PreparedStatement ps = conn.prepareStatement(selBank)) {
                    ps.setInt(1, BANK_CUSTOMER_NO); ps.setString(2, cur);
                    try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) { conn.rollback(); return "Banka kasası yok."; } bankAccId = rs.getInt(1); }
                }
                move(conn, upd, +charge, bankAccId);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE loan_installment SET status = 1, paid_date = CURDATE() WHERE id = ?")) {
                    ps.setInt(1, installmentId); ps.executeUpdate();
                }
                // Kalan taksit var mı?
                boolean allPaid;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM loan_installment WHERE lending_id = ? AND status = 0")) {
                    ps.setInt(1, lendingId);
                    try (ResultSet rs = ps.executeQuery()) { rs.next(); allPaid = rs.getInt(1) == 0; }
                }
                if (allPaid) {
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE lending SET status = 3 WHERE id = ?")) {
                        ps.setInt(1, lendingId); ps.executeUpdate();
                    }
                }
                conn.commit();

                int custNo = customerNo(custId);
                ActivityLogDAO.log("LOAN_INSTALLMENT", custNo, charge, cur,
                        "Kredi taksit ödemesi: " + String.format("%,.2f %s", charge, cur)
                                + (lateFee > 0 ? " (gecikme faizi dahil)" : ""),
                        "Kredi #" + lendingId + " | Taksit: " + String.format("%,.2f %s", amount, cur)
                                + (lateFee > 0 ? " | Gecikme: " + daysLate + " gün, faiz " + String.format("%,.2f %s", lateFee, cur) : "")
                                + (allPaid ? " | Kredi tamamlandı" : ""));
                CustomerSnapshotDAO.record(custId);
                TreasurySnapshotDAO.record();
                if (allPaid) new NotificationDAO().add(custNo, "Krediniz tamamlandı",
                        "Tüm taksitler ödendi, kredi kapandı. (Kredi #" + lendingId + ")");
                return null;
            } catch (SQLException e) { conn.rollback(); throw e; }
        } catch (SQLException e) {
            System.err.println("Taksit ödeme hatası: " + e.getMessage());
            ErrorLogDAO.log(e, "Taksit ödeme");
            return "İşlem sırasında hata: " + e.getMessage();
        }
    }

    /** Bir kredinin ödenmemiş EN ERKEN taksitini öder. */
    public String payNextInstallment(int lendingId) {
        ensureInstallments(lendingId);
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id FROM loan_installment WHERE lending_id = ? AND status = 0 ORDER BY seq_no LIMIT 1")) {
            ps.setInt(1, lendingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return "Ödenecek taksit yok.";
                return payInstallment(rs.getInt(1));
            }
        } catch (SQLException e) {
            System.err.println("Sonraki taksit hatası: " + e.getMessage());
            return "İşlem sırasında hata: " + e.getMessage();
        }
    }

    /** Vadesi bugün veya geçmiş, ödenmemiş taksitleri toplu tahsil eder (batch). @return tahsil edilen adet. */
    public int collectDue() {
        // Eski aktif kredilerin taksit planını önce üret
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id FROM lending WHERE status = 1");
             ResultSet rs = ps.executeQuery()) {
            List<Integer> active = new ArrayList<>();
            while (rs.next()) active.add(rs.getInt(1));
            for (int id : active) ensureInstallments(id);
        } catch (SQLException e) { System.err.println("Backfill(collectDue): " + e.getMessage()); }

        List<Integer> ids = new ArrayList<>();
        // Yalnızca OTOMATİK ödeme talimatı olan kredilerin taksitleri otomatik tahsil edilir.
        String sql = "SELECT li.id FROM loan_installment li JOIN lending l ON li.lending_id = l.id "
                   + "WHERE li.status = 0 AND l.status = 1 AND l.auto_pay = 1 AND li.due_date <= CURDATE() "
                   + "ORDER BY li.due_date, li.seq_no";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getInt(1));
        } catch (SQLException e) {
            System.err.println("Vadesi gelen taksitler alınamadı: " + e.getMessage());
        }
        int ok = 0;
        for (int id : ids) if (payInstallment(id) == null) ok++;
        return ok;
    }

    /** TEST: bir kredinin ödenmemiş taksitlerinin vadesini bugüne çeker (batch hemen tahsil etsin diye). */
    public void pullDueToday(int lendingId) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE loan_installment SET due_date = CURDATE() WHERE lending_id = ? AND status = 0")) {
            ps.setInt(1, lendingId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Vade çekme hatası: " + e.getMessage());
        }
    }

    // ---- Listeleme ----
    private static final String BASE =
            "SELECT l.id, l.customer_id, c.customer_no, CONCAT(c.customer_name,' ',IFNULL(c.surname,'')) AS musteri, "
          + "a.account_no, l.loan_type, l.currency, l.amount, l.interest_rate, l.term_months, "
          + "l.monthly_payment, l.total_due, l.status, l.reject_reason, l.start_date, l.maturity_date, l.auto_pay "
          + "FROM lending l JOIN customer c ON l.customer_id = c.customer_id "
          + "JOIN account a ON l.account_id = a.account_id ";

    public List<Lending> getByCustomer(int customerId) { return query(BASE + "WHERE l.customer_id = ? ORDER BY l.id DESC", customerId); }
    public List<Lending> getAll() { return query(BASE + "ORDER BY l.id DESC", 0); }
    public List<Lending> getPending() { return query(BASE + "WHERE l.status = 0 ORDER BY l.id DESC", 0); }

    public int pendingCount() {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM lending WHERE status = 0");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { System.err.println("Bekleyen kredi sayısı: " + e.getMessage()); }
        return 0;
    }

    private List<Lending> query(String sql, int customerId) {
        List<Lending> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (customerId > 0) ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Lending(
                            rs.getInt("id"), rs.getInt("customer_id"), rs.getInt("customer_no"), rs.getString("musteri"),
                            rs.getLong("account_no"), rs.getString("loan_type"), rs.getString("currency"),
                            rs.getDouble("amount"), rs.getDouble("interest_rate"), rs.getInt("term_months"),
                            rs.getDouble("monthly_payment"), rs.getDouble("total_due"), rs.getInt("status"),
                            rs.getString("reject_reason"),
                            String.valueOf(rs.getDate("start_date")), String.valueOf(rs.getDate("maturity_date")),
                            rs.getInt("auto_pay")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Krediler getirilemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Kredi listeleme");
        }
        return list;
    }

    // ---- yardımcılar ----
    private double toTry(RateDAO rateDAO, double amount, String currency) {
        if (currency == null || "TRY".equals(currency)) return amount;
        CurrencyRate r = rateDAO.getByCurrency(currency);
        return r == null ? amount : amount * r.getBuyRate();
    }

    private boolean targetIsMine(String description, java.util.Set<String> myAccounts) {
        if (description == null) return false;
        int arrow = description.indexOf('→');
        if (arrow < 0) return false;
        String right = description.substring(arrow + 1).trim();
        for (String acc : myAccounts) if (right.contains(acc)) return true;
        return false;
    }

    private int customerNo(int customerId) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT customer_no FROM customer WHERE customer_id = ?")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException ignored) { }
        return 0;
    }

    private void move(Connection conn, String sql, double delta, int accountId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, delta); ps.setInt(2, accountId); ps.executeUpdate();
        }
    }

    /** Geciken (vadesi geçmiş, ödenmemiş) taksitleri getirir. */
    public List<OverdueInstallment> getOverdue() {
        String sql =
            "SELECT c.customer_no, CONCAT(c.customer_name,' ',IFNULL(c.surname,'')) AS ad, " +
            "       l.id AS lending_id, li.seq_no, li.due_date, li.amount, l.currency, l.interest_rate " +
            "FROM loan_installment li " +
            "JOIN lending l  ON li.lending_id = l.id " +
            "JOIN customer c ON l.customer_id = c.customer_id " +
            "WHERE li.status = 0 AND l.status = 1 AND li.due_date < CURDATE() " +
            "ORDER BY li.due_date";

        List<OverdueInstallment> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new OverdueInstallment(
                        rs.getInt("customer_no"),
                        rs.getString("ad"),
                        rs.getInt("lending_id"),
                        rs.getInt("seq_no"),
                        String.valueOf(rs.getDate("due_date")),
                        rs.getDouble("amount"),
                        rs.getString("currency"),
                        rs.getDouble("interest_rate")));
            }
        } catch (SQLException e) {
            System.err.println("Geciken taksitler getirilemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Geciken taksitler");
        }
        return list;
    }
}
