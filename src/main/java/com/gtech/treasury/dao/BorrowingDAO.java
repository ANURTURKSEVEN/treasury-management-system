package com.gtech.treasury.dao;

import com.gtech.treasury.model.Account;
import com.gtech.treasury.model.Deposit;
import com.gtech.treasury.util.DBConnection;
import com.gtech.treasury.util.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Vadeli Mevduat (Borrowing). Müşteri parasını bankaya yatırır, banka faiz öder.
 * Açılışta anapara banka kasasına girer; kapanışta anapara+faiz müşteriye döner.
 * Sözleşme: SABIT (erken bozmada faiz yok) / ESNEK (erken bozmada kısmi faiz).
 */
public class BorrowingDAO {

    private static final int BANK_CUSTOMER_NO = 99999999;
    private static final double EARLY_FACTOR = 0.5;   // ESNEK erken bozmada faizin oranı

    public enum Contract {
        SABIT("Sabit (vade sonunda tam faiz, erken bozmada faiz yok)"),
        ESNEK("Esnek (erken bozmada geçen süreye kısmi faiz)");
        public final String label;
        Contract(String label) { this.label = label; }
        public static Contract of(String n) { try { return valueOf(n); } catch (Exception e) { return SABIT; } }
    }

    /** Vadeye göre yıllık mevduat faizi (uzun vade daha yüksek). */
    public static double depositRate(int months) {
        if (months <= 3) return 40.0;
        if (months <= 6) return 42.0;
        if (months <= 12) return 45.0;
        return 46.0;
    }
    /** Mevduat faizi stopaj oranı (vadeye göre; kısa vade daha yüksek). */
    public static double depositTaxRate(int months) {
        if (months < 6) return 0.15;    // %15
        if (months < 12) return 0.12;   // %12
        return 0.10;                    // 1 yıl ve üzeri %10
    }

    public static double interestFor(double amount, int months) {
        return Math.round(amount * depositRate(months) / 100.0 * months / 12.0 * 100.0) / 100.0;
    }

    // ---- Aç ----
    /** Mevduat BAŞVURUSU — para çekilmez; bankacı onayına düşer (status = 2). */
    public String apply(Account target, int months, Contract contract, double amount) {
        if (target == null) return "Hesap seçilmedi.";
        if (amount <= 0) return "Tutar sıfırdan büyük olmalı.";
        if (months <= 0) return "Vade geçersiz.";
        if (amount > target.getBalance()) return "Hesap bakiyesi yetersiz. Mevcut: " + String.format("%,.2f %s", target.getBalance(), target.getCurrency());

        double rate = depositRate(months);
        double interest = interestFor(amount, months);
        double total = Math.round((amount + interest) * 100.0) / 100.0;
        String cur = target.getCurrency();
        LocalDate start = LocalDate.now(), maturity = start.plusMonths(months); // onayda güncellenir

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO borrowing (customer_id, account_id, contract_type, currency, amount, interest_rate, "
                   + "term_months, interest_amount, total_return, start_date, maturity_date, status) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 2)")) {
            ps.setInt(1, target.getCustomerId()); ps.setInt(2, target.getAccountId());
            ps.setString(3, contract.name()); ps.setString(4, cur); ps.setDouble(5, amount);
            ps.setDouble(6, rate); ps.setInt(7, months); ps.setDouble(8, interest); ps.setDouble(9, total);
            ps.setString(10, start.toString()); ps.setString(11, maturity.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Mevduat başvuru hatası: " + e.getMessage());
            ErrorLogDAO.log(e, "Mevduat başvuru");
            return "Başvuru kaydedilemedi: " + e.getMessage();
        }
        ActivityLogDAO.log("DEPOSIT_APPLIED", target.getCustomerNo(), amount, cur,
                "Vadeli mevduat başvurusu: " + String.format("%,.2f %s", amount, cur) + " / " + months + " ay",
                contract.label + " | Faiz: %" + rate + " | Onay bekliyor.");
        return null;
    }

    /** Bankacı onayı — parayı çeker, mevduatı açar (status 2 -> 1). Vade onaydan itibaren. */
    public String approve(int id) {
        String sel = "SELECT b.customer_id, b.account_id, b.currency, b.amount, b.term_months, b.total_return, c.customer_no "
                   + "FROM borrowing b JOIN customer c ON b.customer_id = c.customer_id WHERE b.id = ? AND b.status = 2";
        String debit = "UPDATE account SET balance = balance - ? WHERE account_id = ? AND status = 1 AND balance >= ?";
        String upd = "UPDATE account SET balance = balance + ? WHERE account_id = ?";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int custId, accId, months, custNo; String cur; double amount, total;
                try (PreparedStatement ps = conn.prepareStatement(sel)) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return "Onay bekleyen başvuru bulunamadı."; }
                        custId = rs.getInt("customer_id"); accId = rs.getInt("account_id");
                        cur = rs.getString("currency"); amount = rs.getDouble("amount");
                        months = rs.getInt("term_months"); total = rs.getDouble("total_return");
                        custNo = rs.getInt("customer_no");
                    }
                }
                LocalDate start = LocalDate.now(), maturity = start.plusMonths(months);
                try (PreparedStatement ps = conn.prepareStatement(debit)) {
                    ps.setDouble(1, amount); ps.setInt(2, accId); ps.setDouble(3, amount);
                    if (ps.executeUpdate() == 0) { conn.rollback(); return "Müşteri hesabında yeterli bakiye yok."; }
                }
                Integer bankAccId = bankAcc(conn, cur);
                if (bankAccId == null) { conn.rollback(); return "Banka " + cur + " kasası yok."; }
                move(conn, upd, +amount, bankAccId);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE borrowing SET status = 1, start_date = ?, maturity_date = ? WHERE id = ?")) {
                    ps.setString(1, start.toString()); ps.setString(2, maturity.toString()); ps.setInt(3, id);
                    ps.executeUpdate();
                }
                conn.commit();
                ActivityLogDAO.log("DEPOSIT_OPEN", custNo, amount, cur,
                        "Vadeli mevduat onaylandı ve açıldı: " + String.format("%,.2f %s", amount, cur) + " / " + months + " ay",
                        "Vade sonu getiri: " + String.format("%,.2f %s", total, cur) + " | Vade: " + maturity);
                CustomerSnapshotDAO.record(custId);
                TreasurySnapshotDAO.record();
                notifyIfByStaff(custNo, "Vadeli mevduat onaylandı",
                        String.format("%,.2f %s / %d ay | Vade: %s", amount, cur, months, maturity));
                return null;
            } catch (SQLException e) { conn.rollback(); throw e; }
        } catch (SQLException e) {
            System.err.println("Mevduat onay hatası: " + e.getMessage());
            ErrorLogDAO.log(e, "Mevduat onay");
            return "İşlem sırasında hata: " + e.getMessage();
        }
    }

    /** Bankacı reddi (status 2 -> 3). Para hareketi olmaz. */
    public String reject(int id, String reason) {
        String sel = "SELECT c.customer_no, b.amount, b.currency FROM borrowing b "
                   + "JOIN customer c ON b.customer_id = c.customer_id WHERE b.id = ? AND b.status = 2";
        try (Connection conn = DBConnection.getConnection()) {
            int custNo; double amount; String cur;
            try (PreparedStatement ps = conn.prepareStatement(sel)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return "Onay bekleyen başvuru bulunamadı.";
                    custNo = rs.getInt("customer_no"); amount = rs.getDouble("amount"); cur = rs.getString("currency");
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE borrowing SET status = 3, reject_reason = ? WHERE id = ? AND status = 2")) {
                ps.setString(1, reason); ps.setInt(2, id);
                if (ps.executeUpdate() == 0) return "Başvuru güncellenemedi.";
            }
            ActivityLogDAO.log("DEPOSIT_REJECTED", custNo, amount, cur,
                    "Vadeli mevduat başvurusu reddedildi", "Sebep: " + reason);
            notifyIfByStaff(custNo, "Vadeli mevduat başvurusu reddedildi", "Sebep: " + reason);
            return null;
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "Mevduat red");
            return "İşlem sırasında hata: " + e.getMessage();
        }
    }

    public List<Deposit> getPending() { return query(BASE + "WHERE b.status = 2 ORDER BY b.id DESC", 0); }

    public int pendingCount() {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM borrowing WHERE status = 2");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { ErrorLogDAO.log(e, "Mevduat pending sayımı"); }
        return 0;
    }

    // ---- Vade sonu kapat (tam faiz) ----
    public String close(int id) { return settle(id, false); }
    // ---- Erken boz (sözleşmeye göre) ----
    public String breakEarly(int id) { return settle(id, true); }

    private String settle(int id, boolean early) {
        String sel = "SELECT customer_id, account_id, currency, amount, interest_rate, term_months, "
                   + "interest_amount, total_return, contract_type, start_date FROM borrowing WHERE id = ? AND status = 1";
        String upd = "UPDATE account SET balance = balance + ? WHERE account_id = ?";
        String debit = "UPDATE account SET balance = balance - ? WHERE account_id = ? AND status = 1 AND balance >= ?";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int custId, accId, months; String cur, contract, startStr; double amount, rate, fullInterest, total;
                try (PreparedStatement ps = conn.prepareStatement(sel)) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return "Aktif mevduat bulunamadı."; }
                        custId = rs.getInt("customer_id"); accId = rs.getInt("account_id"); cur = rs.getString("currency");
                        amount = rs.getDouble("amount"); rate = rs.getDouble("interest_rate"); months = rs.getInt("term_months");
                        fullInterest = rs.getDouble("interest_amount"); total = rs.getDouble("total_return");
                        contract = rs.getString("contract_type"); startStr = String.valueOf(rs.getDate("start_date"));
                    }
                }

                double interest, payout;
                if (!early) {
                    interest = fullInterest; payout = total;                 // vade sonu: tam faiz
                } else if ("SABIT".equals(contract)) {
                    interest = 0; payout = amount;                            // sabit erken: faizsiz
                } else {
                    long days = 0;
                    try { days = ChronoUnit.DAYS.between(LocalDate.parse(startStr), LocalDate.now()); } catch (Exception ignored) { }
                    if (days < 0) days = 0;
                    interest = Math.round(amount * (rate / 100.0) * EARLY_FACTOR * (days / 365.0) * 100.0) / 100.0;
                    payout = Math.round((amount + interest) * 100.0) / 100.0;  // esnek erken: kısmi faiz
                }

                // STOPAJ: faiz getirisinden gelir vergisi kesintisi (vadeye göre oran)
                double taxRate = depositTaxRate(months);
                double tax = Math.round(interest * taxRate * 100.0) / 100.0;
                double netInterest = Math.round((interest - tax) * 100.0) / 100.0;
                payout = Math.round((amount + netInterest) * 100.0) / 100.0;   // müşteriye net ödenir

                Integer bankAccId = bankAcc(conn, cur);
                if (bankAccId == null) { conn.rollback(); return "Banka " + cur + " kasası yok."; }
                // banka kasasından öder
                try (PreparedStatement ps = conn.prepareStatement(debit)) {
                    ps.setDouble(1, payout); ps.setInt(2, bankAccId); ps.setDouble(3, payout);
                    if (ps.executeUpdate() == 0) { conn.rollback(); return "Banka " + cur + " kasası ödeme için yetersiz."; }
                }
                move(conn, upd, +payout, accId);   // müşteriye anapara(+faiz)
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE borrowing SET status = 0, close_type = ? WHERE id = ?")) {
                    ps.setString(1, early ? "ERKEN" : "VADE"); ps.setInt(2, id);
                    ps.executeUpdate();
                }
                conn.commit();

                int custNo = customerNo(custId);
                ActivityLogDAO.log(early ? "DEPOSIT_BREAK" : "DEPOSIT_CLOSE", custNo, payout, cur,
                        (early ? "Mevduat erken bozuldu: " : "Mevduat vade sonu kapandı: ") + String.format("%,.2f %s", payout, cur),
                        "Mevduat #" + id + " | Brüt faiz: " + String.format("%,.2f %s", interest, cur)
                                + " | Stopaj (%" + String.format("%.0f", taxRate * 100) + "): " + String.format("%,.2f %s", tax, cur)
                                + " | Net faiz: " + String.format("%,.2f %s", netInterest, cur));
                CustomerSnapshotDAO.record(custId);
                TreasurySnapshotDAO.record();
                new NotificationDAO().add(custNo,
                        early ? "Vadeli mevduatınız erken bozuldu" : "Vadeli mevduatınız vade sonunda kapandı",
                        "Hesabınıza " + String.format("%,.2f %s", payout, cur) + " ödendi. "
                                + "Brüt faiz " + String.format("%,.2f %s", interest, cur)
                                + ", stopaj " + String.format("%,.2f %s", tax, cur)
                                + ", net faiz " + String.format("%,.2f %s", netInterest, cur) + ".");
                return null;
            } catch (SQLException e) { conn.rollback(); throw e; }
        } catch (SQLException e) {
            System.err.println("Mevduat kapama hatası: " + e.getMessage());
            ErrorLogDAO.log(e, "Mevduat kapama");
            return "İşlem sırasında hata: " + e.getMessage();
        }
    }

    /** Vadesi dolan mevduatları toplu kapatır (batch). @return kapatılan adet. */
    public int matureDue() {
        List<Integer> ids = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id FROM borrowing WHERE status = 1 AND maturity_date <= CURDATE()");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getInt(1));
        } catch (SQLException e) { System.err.println("Vadesi dolan mevduatlar: " + e.getMessage()); }
        int ok = 0;
        for (int id : ids) if (close(id) == null) ok++;
        return ok;
    }

    /** TEST: mevduatın vadesini bugüne çeker (batch hemen kapatsın diye). */
    /** Personel: aktif mevduatın vade tarihini öne/geri alır. */
    public String updateMaturity(int id, String newDate) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE borrowing SET maturity_date = ? WHERE id = ? AND status = 1")) {
            ps.setString(1, newDate); ps.setInt(2, id);
            if (ps.executeUpdate() == 0) return "Vade güncellenemedi (aktif mevduat bulunamadı).";
            ActivityLogDAO.log("DEPOSIT_MATURITY_EDIT", "Mevduat #" + id + " vade tarihi -> " + newDate);
            return null;
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "Mevduat vade güncelleme");
            return "Güncellenemedi: " + e.getMessage();
        }
    }

    public void pullMaturityToday(int id) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE borrowing SET maturity_date = CURDATE() WHERE id = ? AND status = 1")) {
            ps.setInt(1, id); ps.executeUpdate();
        } catch (SQLException e) { System.err.println("Vade çekme: " + e.getMessage()); }
    }

    // ---- Listeleme ----
    private static final String BASE =
            "SELECT b.id, b.customer_id, c.customer_no, CONCAT(c.customer_name,' ',IFNULL(c.surname,'')) AS musteri, "
          + "a.account_no, b.contract_type, b.currency, b.amount, b.interest_rate, b.term_months, "
          + "b.interest_amount, b.total_return, b.status, b.close_type, b.start_date, b.maturity_date "
          + "FROM borrowing b JOIN customer c ON b.customer_id = c.customer_id "
          + "JOIN account a ON b.account_id = a.account_id ";

    public List<Deposit> getByCustomer(int customerId) { return query(BASE + "WHERE b.customer_id = ? ORDER BY b.id DESC", customerId); }
    public List<Deposit> getAll() { return query(BASE + "ORDER BY b.id DESC", 0); }

    private List<Deposit> query(String sql, int customerId) {
        List<Deposit> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (customerId > 0) ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Deposit(
                            rs.getInt("id"), rs.getInt("customer_id"), rs.getInt("customer_no"), rs.getString("musteri"),
                            rs.getLong("account_no"), rs.getString("contract_type"), rs.getString("currency"),
                            rs.getDouble("amount"), rs.getDouble("interest_rate"), rs.getInt("term_months"),
                            rs.getDouble("interest_amount"), rs.getDouble("total_return"), rs.getInt("status"),
                            rs.getString("close_type"),
                            String.valueOf(rs.getDate("start_date")), String.valueOf(rs.getDate("maturity_date"))));
                }
            }
        } catch (SQLException e) {
            System.err.println("Mevduatlar getirilemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Mevduat listeleme");
        }
        return list;
    }

    // ---- yardımcılar ----
    private Integer bankAcc(Connection conn, String currency) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT a.account_id FROM account a JOIN customer c ON a.customer_id = c.customer_id "
              + "AND c.customer_no = ? WHERE a.currency = ? AND a.status = 1 ORDER BY a.account_id LIMIT 1")) {
            ps.setInt(1, BANK_CUSTOMER_NO); ps.setString(2, currency);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        }
        return null;
    }
    private int customerNo(int customerId) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT customer_no FROM customer WHERE customer_id = ?")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException ignored) { }
        return 0;
    }
    private void notifyIfByStaff(int customerNo, String title, String detail) {
        String actor = Session.getCurrentUsername();
        if (actor == null || !actor.equals(String.valueOf(customerNo))) new NotificationDAO().add(customerNo, title, detail);
    }
    private void move(Connection conn, String sql, double delta, int accountId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) { ps.setDouble(1, delta); ps.setInt(2, accountId); ps.executeUpdate(); }
    }
}
