package com.gtech.treasury.dao;

import com.gtech.treasury.model.MoneyMarketBorrowing;
import com.gtech.treasury.model.MoneyMarketCharge;
import com.gtech.treasury.util.DBConnection;
import com.gtech.treasury.util.ReferenceGenerator;
import com.gtech.treasury.util.Session;
import com.gtech.treasury.util.SwiftMessageService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Para Piyasası borçlanma deal'lerinin veri erişimi (mm_borrowing).
 *   create : deal kaydı + valörde banka kasasına fon girişi (+anapara) — tek transaction.
 *   matureDue : vadesi gelen deal'lerde banka kasasından geri ödeme (−anapara−faiz).
 * Retail vadeli mevduat (BorrowingDAO) akışından tamamen bağımsızdır.
 */
public class MoneyMarketBorrowingDAO {

    private static final String COLS =
            "m.id, m.reference_no, m.counterparty_id, c.customer_no, "
          + "CONCAT(IFNULL(c.customer_name,''),' ',IFNULL(c.surname,'')) AS cp_name, "
          + "m.market_type, m.purpose, m.dealer, m.broker, m.comment, m.bcs, m.currency, "
          + "m.principal, m.interest_rate, m.day_count, m.deal_date, m.value_date, m.maturity_date, "
          + "m.interest_amount, m.tax_amount, m.repayment_amount, m.stopaj_flag, "
          + "m.receiving_account_id, m.repayment_account_id, m.correspondent1_bic, m.correspondent2_bic, "
          + "m.create_swift, m.create_mt320, m.create_mt202, m.status, m.created_by, m.created_at, "
          + "m.settled_at, m.matured_at "
          + "FROM mm_borrowing m LEFT JOIN customer c ON m.counterparty_id = c.customer_id ";

    /** Deal'i kaydeder ve valörde bankanın alacak (kasa) hesabına anaparayı geçirir. */
    public String create(MoneyMarketBorrowing d) {
        if (d.getReceivingAccountId() <= 0) return "Alacak hesabı seçilmedi.";
        if (d.getPrincipal() <= 0) return "Borçlanma tutarı sıfırdan büyük olmalı.";

        String ref = ReferenceGenerator.next("MM", "mm_borrowing", "reference_no");
        d.setReferenceNo(ref);
        d.setStatus("ACTIVE");
        String actor = Session.getCurrentUsername();
        d.setCreatedBy(actor);

        String ins = "INSERT INTO mm_borrowing (reference_no, counterparty_id, market_type, purpose, dealer, "
                + "broker, comment, bcs, currency, principal, interest_rate, day_count, deal_date, value_date, "
                + "maturity_date, interest_amount, tax_amount, repayment_amount, stopaj_flag, receiving_account_id, "
                + "repayment_account_id, correspondent1_bic, correspondent2_bic, create_swift, create_mt320, "
                + "create_mt202, status, created_by, settled_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        int newId = 0;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(ins, Statement.RETURN_GENERATED_KEYS)) {
                    int i = 1;
                    ps.setString(i++, ref);
                    if (d.getCounterpartyId() > 0) ps.setInt(i++, d.getCounterpartyId()); else ps.setNull(i++, java.sql.Types.INTEGER);
                    ps.setString(i++, d.getMarketType());
                    ps.setString(i++, d.getPurpose());
                    ps.setString(i++, d.getDealer());
                    ps.setString(i++, d.getBroker());
                    ps.setString(i++, d.getComment());
                    ps.setString(i++, d.getBcs());
                    ps.setString(i++, d.getCurrency());
                    ps.setDouble(i++, d.getPrincipal());
                    ps.setDouble(i++, d.getInterestRate());
                    ps.setString(i++, d.getDayCount());
                    ps.setString(i++, d.getDealDate());
                    ps.setString(i++, d.getValueDate());
                    ps.setString(i++, d.getMaturityDate());
                    ps.setDouble(i++, d.getInterestAmount());
                    ps.setDouble(i++, d.getTaxAmount());
                    ps.setDouble(i++, d.getRepaymentAmount());
                    ps.setInt(i++, d.isStopaj() ? 1 : 0);
                    ps.setInt(i++, d.getReceivingAccountId());
                    if (d.getRepaymentAccountId() > 0) ps.setInt(i++, d.getRepaymentAccountId()); else ps.setNull(i++, java.sql.Types.INTEGER);
                    ps.setString(i++, d.getCorrespondent1Bic());
                    ps.setString(i++, d.getCorrespondent2Bic());
                    ps.setInt(i++, d.isCreateSwift() ? 1 : 0);
                    ps.setInt(i++, d.isCreateMt320() ? 1 : 0);
                    ps.setInt(i++, d.isCreateMt202() ? 1 : 0);
                    ps.setString(i++, d.getStatus());
                    ps.setString(i++, actor);
                    ps.setString(i++, d.getValueDate());   // settled_at = valör
                    ps.executeUpdate();
                    try (ResultSet gk = ps.getGeneratedKeys()) { if (gk.next()) newId = gk.getInt(1); }
                }
                // Valörde banka kasa hesabına fon girişi (+anapara)
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE account SET balance = balance + ? WHERE account_id = ? AND status = 1")) {
                    ps.setDouble(1, d.getPrincipal());
                    ps.setInt(2, d.getReceivingAccountId());
                    if (ps.executeUpdate() == 0) { conn.rollback(); return "Alacak hesabı bulunamadı/kapalı; deal kaydedilemedi."; }
                }
                // Müşteri ayağı: counterparty sistem müşterisiyse fonu onun hesabından al (−anapara); yetersizse deal kaydedilemez
                int cAcc = custAcc(conn, d.getCounterpartyId(), d.getCurrency());
                if (cAcc > 0 && !debit(conn, cAcc, d.getPrincipal()))
                    { conn.rollback(); return "Karşı kurum hesabında yeterli bakiye yok; borçlanma kaydedilemedi."; }
                // Masraf kalemleri: kaydet; BANKA'nın ödediği (deal döviziyle) kalemler kasadan düşülür
                for (MoneyMarketCharge ch : d.getCharges()) {
                    if (ch == null || ch.getAmount() <= 0) continue;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO mm_charge (mm_id, charge_type, amount, currency, payer, note) VALUES (?,?,?,?,?,?)")) {
                        ps.setInt(1, newId); ps.setString(2, ch.getChargeType()); ps.setDouble(3, ch.getAmount());
                        ps.setString(4, ch.getCurrency()); ps.setString(5, ch.getPayer()); ps.setString(6, ch.getNote());
                        ps.executeUpdate();
                    }
                    if ("BANKA".equals(ch.getPayer()) && d.getCurrency().equals(ch.getCurrency())) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE account SET balance = balance - ? WHERE account_id = ? AND status = 1 AND balance >= ?")) {
                            ps.setDouble(1, ch.getAmount()); ps.setInt(2, d.getReceivingAccountId()); ps.setDouble(3, ch.getAmount());
                            if (ps.executeUpdate() == 0) { conn.rollback(); return "Masraf için kasada yeterli bakiye yok: " + ch.getTypeLabel(); }
                        }
                    }
                }
                conn.commit();
            } catch (SQLException e) { conn.rollback(); throw e; }
        } catch (SQLException e) {
            System.err.println("MM borrowing create hatası: " + e.getMessage());
            ErrorLogDAO.log(e, "MM borrowing create");
            return "İşlem sırasında hata: " + e.getMessage();
        }
        d.setId(newId);

        // Commit sonrası: denetim izi + bildirim + (seçiliyse) SWIFT mesajları
        ActivityLogDAO.log("MM_BORROW_CREATE", d.getCounterpartyNo(), d.getPrincipal(), d.getCurrency(),
                "Para piyasası borçlanma kaydedildi: " + String.format("%,.2f %s", d.getPrincipal(), d.getCurrency()),
                "Ref: " + ref + " | Valör: " + d.getValueDate() + " | Vade: " + d.getMaturityDate()
                        + " | Faiz: " + d.getInterestRate() + " (" + d.getDayCount() + ") | Dealer: " + actor);
        new NotificationDAO().addForStaff("Para piyasası borçlanma kaydedildi",
                "Ref: " + ref + " — " + String.format("%,.2f %s", d.getPrincipal(), d.getCurrency())
                        + " / Vade: " + d.getMaturityDate(),
                "MM_BORROW", ref);
        notifyCustomer(d.getCounterpartyNo(), "Bankaya para piyasası fonu sağladınız (alacaklısınız)",
                "Ref: " + ref + " — " + String.format("%,.2f %s", d.getPrincipal(), d.getCurrency())
                        + " hesabınızdan alındı. Vade: " + d.getMaturityDate()
                        + " — geri ödeme: " + String.format("%,.2f %s", d.getRepaymentAmount(), d.getCurrency()), ref);
        if (d.isCreateSwift() || d.isCreateMt320()) generateSwift(d, "MT320");
        if (d.isCreateSwift() || d.isCreateMt202()) generateSwift(d, "MT202");
        return null;
    }

    /** SWIFT mesajını üretir ve mevcut message tablosuna (personel ortak kutusu) kaydeder. */
    private void generateSwift(MoneyMarketBorrowing d, String type) {
        try {
            String body = "MT320".equals(type) ? SwiftMessageService.buildMT320(d) : SwiftMessageService.buildMT202(d);
            new MessageDAO().send("SYSTEM", "STAFF",
                    type + " — " + d.getReferenceNo(), body, "SWIFT_" + type, d.getReferenceNo());
        } catch (Exception e) {
            ErrorLogDAO.log(e, "SWIFT üretimi " + type);
        }
    }

    /** Vadesi gelen (ACTIVE, maturity<=bugün) deal'leri kapatır: banka kasasından geri ödeme. @return kapatılan adet. */
    public int matureDue() {
        String sel = "SELECT m.id, m.currency, m.repayment_amount, m.repayment_account_id, m.reference_no, "
                   + "m.counterparty_id, c.customer_no FROM mm_borrowing m "
                   + "LEFT JOIN customer c ON m.counterparty_id = c.customer_id "
                   + "WHERE m.status = 'ACTIVE' AND m.maturity_date <= CURDATE()";
        int ok = 0;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sel);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (settleOne(rs.getInt("id"), rs.getString("currency"), rs.getDouble("repayment_amount"),
                        rs.getInt("repayment_account_id"), rs.getString("reference_no"),
                        rs.getInt("counterparty_id"), rs.getInt("customer_no"))) ok++;
            }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "MM matureDue");
        }
        return ok;
    }

    private boolean settleOne(int id, String cur, double repay, int repayAcc, String ref,
                              int counterpartyId, int counterpartyNo) {
        if (repayAcc <= 0) return false;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE account SET balance = balance - ? WHERE account_id = ? AND status = 1 AND balance >= ?")) {
                    ps.setDouble(1, repay); ps.setInt(2, repayAcc); ps.setDouble(3, repay);
                    if (ps.executeUpdate() == 0) { conn.rollback(); return false; }   // kasa yetersiz
                }
                // Müşteri ayağı: alacaklı müşterinin hesabına geri ödeme (+repay)
                int cAcc = custAcc(conn, counterpartyId, cur);
                if (cAcc > 0) move(conn, cAcc, +repay);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE mm_borrowing SET status = 'MATURED', matured_at = CURDATE() WHERE id = ? AND status = 'ACTIVE'")) {
                    ps.setInt(1, id);
                    if (ps.executeUpdate() == 0) { conn.rollback(); return false; }
                }
                conn.commit();
            } catch (SQLException e) { conn.rollback(); throw e; }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "MM settle");
            return false;
        }
        notifyCustomer(counterpartyNo, "Para piyasası alacağınız ödendi",
                "Ref: " + ref + " — " + String.format("%,.2f %s", repay, cur) + " hesabınıza geri ödendi.", ref);
        ActivityLogDAO.log("MM_BORROW_MATURE", counterpartyNo, repay, cur,
                "Para piyasası borçlanma vade sonu geri ödendi: " + String.format("%,.2f %s", repay, cur),
                "Ref: " + ref);
        new NotificationDAO().addForStaff("Para piyasası borçlanma vadesine ulaştı",
                "Ref: " + ref + " geri ödendi: " + String.format("%,.2f %s", repay, cur), "MM_BORROW", ref);
        return true;
    }

    /** Aktif bir deal'i iptal eder: banka kasasına giren anaparayı geri alır (ters kayıt). */
    public String cancel(int id) {
        String cur = null, ref = null; double principal = 0; int recvAcc = 0, cpId = 0, cpNo = 0;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT m.currency, m.principal, m.receiving_account_id, m.reference_no, m.counterparty_id, c.customer_no "
                      + "FROM mm_borrowing m LEFT JOIN customer c ON m.counterparty_id = c.customer_id "
                      + "WHERE m.id = ? AND m.status = 'ACTIVE'")) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return "Sadece AKTİF işlem iptal edilebilir."; }
                        cur = rs.getString("currency"); principal = rs.getDouble("principal");
                        recvAcc = rs.getInt("receiving_account_id"); ref = rs.getString("reference_no");
                        cpId = rs.getInt("counterparty_id"); cpNo = rs.getInt("customer_no");
                    }
                }
                // Kasadan anaparayı geri al (ters kayıt) — bakiye yetersizse iptal edilemez
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE account SET balance = balance - ? WHERE account_id = ? AND status = 1 AND balance >= ?")) {
                    ps.setDouble(1, principal); ps.setInt(2, recvAcc); ps.setDouble(3, principal);
                    if (ps.executeUpdate() == 0) { conn.rollback(); return "Kasada geri alınacak bakiye yok (fon kullanılmış olabilir); iptal edilemez."; }
                }
                // Müşteri ayağı ters kayıt: sağladığı fonu müşteriye iade et (+anapara)
                int cAcc = custAcc(conn, cpId, cur);
                if (cAcc > 0) move(conn, cAcc, +principal);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE mm_borrowing SET status = 'CANCELLED' WHERE id = ? AND status = 'ACTIVE'")) {
                    ps.setInt(1, id);
                    if (ps.executeUpdate() == 0) { conn.rollback(); return "İşlem güncellenemedi."; }
                }
                conn.commit();
            } catch (SQLException e) { conn.rollback(); throw e; }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "MM iptal");
            return "İşlem sırasında hata: " + e.getMessage();
        }
        ActivityLogDAO.log("MM_BORROW_CANCEL", cpNo, principal, cur,
                "Para piyasası borçlanma iptal edildi: " + String.format("%,.2f %s", principal, cur), "Ref: " + ref);
        new NotificationDAO().addForStaff("Para piyasası borçlanma iptal edildi",
                "Ref: " + ref + " iptal edildi; kasadan anapara geri alındı.", "MM_BORROW", ref);
        notifyCustomer(cpNo, "Para piyasası işleminiz iptal edildi",
                "Ref: " + ref + " iptal edildi; sağladığınız fon hesabınıza iade edildi.", ref);
        return null;
    }

    /** Bir deal'in masraf kalemleri (detay ekranı). */
    public List<MoneyMarketCharge> getCharges(int mmId) {
        List<MoneyMarketCharge> list = new ArrayList<>();
        String sql = "SELECT charge_type, amount, currency, payer, note FROM mm_charge WHERE mm_id = ? ORDER BY id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, mmId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MoneyMarketCharge ch = new MoneyMarketCharge(rs.getString("charge_type"), rs.getDouble("amount"),
                            rs.getString("currency"), rs.getString("payer"), rs.getString("note"));
                    ch.setMmId(mmId);
                    list.add(ch);
                }
            }
        } catch (SQLException e) { ErrorLogDAO.log(e, "MM masraf listeleme"); }
        return list;
    }

    /** Aktif bir deal'i değiştirir: eski nakit etkisini geri alıp yeni değerlerle yeniden uygular. */
    public String amend(MoneyMarketBorrowing d) {
        if (d.getId() <= 0) return "Geçersiz işlem.";
        if (d.getReceivingAccountId() <= 0) return "Alacak hesabı seçilmedi.";
        if (d.getPrincipal() <= 0) return "Borçlanma tutarı sıfırdan büyük olmalı.";

        String upd = "UPDATE mm_borrowing SET counterparty_id=?, market_type=?, purpose=?, dealer=?, broker=?, "
                + "comment=?, bcs=?, currency=?, principal=?, interest_rate=?, day_count=?, deal_date=?, value_date=?, "
                + "maturity_date=?, interest_amount=?, tax_amount=?, repayment_amount=?, stopaj_flag=?, "
                + "receiving_account_id=?, repayment_account_id=?, correspondent1_bic=?, correspondent2_bic=?, "
                + "create_swift=?, create_mt320=?, create_mt202=?, settled_at=? WHERE id=? AND status='ACTIVE'";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1) Eski değerleri oku
                int oldRecv = 0, oldCpId = 0; double oldPrincipal = 0; String oldCur = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT principal, receiving_account_id, currency, counterparty_id FROM mm_borrowing WHERE id=? AND status='ACTIVE'")) {
                    ps.setInt(1, d.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return "Sadece AKTİF işlem değiştirilebilir."; }
                        oldPrincipal = rs.getDouble("principal"); oldRecv = rs.getInt("receiving_account_id");
                        oldCur = rs.getString("currency"); oldCpId = rs.getInt("counterparty_id");
                    }
                }
                double oldBankCharges = 0;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COALESCE(SUM(amount),0) FROM mm_charge WHERE mm_id=? AND payer='BANKA'")) {
                    ps.setInt(1, d.getId());
                    try (ResultSet rs = ps.executeQuery()) { if (rs.next()) oldBankCharges = rs.getDouble(1); }
                }
                // 2) Eski nakit etkisini geri al: anaparayı çıkar, BANKA masraflarını iade et
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE account SET balance = balance - ? WHERE account_id=? AND status=1 AND balance >= ?")) {
                    ps.setDouble(1, oldPrincipal); ps.setInt(2, oldRecv); ps.setDouble(3, oldPrincipal);
                    if (ps.executeUpdate() == 0) { conn.rollback(); return "Eski alacak hesabında yeterli bakiye yok (fon kullanılmış olabilir); değişiklik yapılamaz."; }
                }
                if (oldBankCharges > 0) move(conn, oldRecv, +oldBankCharges);
                // Müşteri ayağı ters kayıt: sağladığı eski anaparayı müşteriye iade et (+)
                int oldCAcc = custAcc(conn, oldCpId, oldCur);
                if (oldCAcc > 0) move(conn, oldCAcc, +oldPrincipal);
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM mm_charge WHERE mm_id=?")) {
                    ps.setInt(1, d.getId()); ps.executeUpdate();
                }
                // 3) Yeni nakit etkisini uygula: yeni anaparayı ekle, yeni BANKA masraflarını düş + kalemleri yaz
                move(conn, d.getReceivingAccountId(), +d.getPrincipal());
                // Müşteri ayağı yeni: yeni anaparayı müşteri hesabından al (−, guard)
                int newCAcc = custAcc(conn, d.getCounterpartyId(), d.getCurrency());
                if (newCAcc > 0 && !debit(conn, newCAcc, d.getPrincipal()))
                    { conn.rollback(); return "Karşı kurum hesabında yeterli bakiye yok; değişiklik yapılamaz."; }
                for (MoneyMarketCharge ch : d.getCharges()) {
                    if (ch == null || ch.getAmount() <= 0) continue;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO mm_charge (mm_id, charge_type, amount, currency, payer, note) VALUES (?,?,?,?,?,?)")) {
                        ps.setInt(1, d.getId()); ps.setString(2, ch.getChargeType()); ps.setDouble(3, ch.getAmount());
                        ps.setString(4, ch.getCurrency()); ps.setString(5, ch.getPayer()); ps.setString(6, ch.getNote());
                        ps.executeUpdate();
                    }
                    if ("BANKA".equals(ch.getPayer()) && d.getCurrency().equals(ch.getCurrency())) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE account SET balance = balance - ? WHERE account_id=? AND status=1 AND balance >= ?")) {
                            ps.setDouble(1, ch.getAmount()); ps.setInt(2, d.getReceivingAccountId()); ps.setDouble(3, ch.getAmount());
                            if (ps.executeUpdate() == 0) { conn.rollback(); return "Masraf için kasada yeterli bakiye yok: " + ch.getTypeLabel(); }
                        }
                    }
                }
                // 4) Deal satırını güncelle
                try (PreparedStatement ps = conn.prepareStatement(upd)) {
                    int i = 1;
                    if (d.getCounterpartyId() > 0) ps.setInt(i++, d.getCounterpartyId()); else ps.setNull(i++, java.sql.Types.INTEGER);
                    ps.setString(i++, d.getMarketType()); ps.setString(i++, d.getPurpose()); ps.setString(i++, d.getDealer());
                    ps.setString(i++, d.getBroker()); ps.setString(i++, d.getComment()); ps.setString(i++, d.getBcs());
                    ps.setString(i++, d.getCurrency()); ps.setDouble(i++, d.getPrincipal()); ps.setDouble(i++, d.getInterestRate());
                    ps.setString(i++, d.getDayCount()); ps.setString(i++, d.getDealDate()); ps.setString(i++, d.getValueDate());
                    ps.setString(i++, d.getMaturityDate()); ps.setDouble(i++, d.getInterestAmount()); ps.setDouble(i++, d.getTaxAmount());
                    ps.setDouble(i++, d.getRepaymentAmount()); ps.setInt(i++, d.isStopaj() ? 1 : 0);
                    ps.setInt(i++, d.getReceivingAccountId());
                    if (d.getRepaymentAccountId() > 0) ps.setInt(i++, d.getRepaymentAccountId()); else ps.setNull(i++, java.sql.Types.INTEGER);
                    ps.setString(i++, d.getCorrespondent1Bic()); ps.setString(i++, d.getCorrespondent2Bic());
                    ps.setInt(i++, d.isCreateSwift() ? 1 : 0); ps.setInt(i++, d.isCreateMt320() ? 1 : 0); ps.setInt(i++, d.isCreateMt202() ? 1 : 0);
                    ps.setString(i++, d.getValueDate()); ps.setInt(i++, d.getId());
                    if (ps.executeUpdate() == 0) { conn.rollback(); return "İşlem güncellenemedi."; }
                }
                conn.commit();
            } catch (SQLException e) { conn.rollback(); throw e; }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "MM amend");
            return "İşlem sırasında hata: " + e.getMessage();
        }
        ActivityLogDAO.log("MM_BORROW_AMEND", d.getCounterpartyNo(), d.getPrincipal(), d.getCurrency(),
                "Para piyasası borçlanma değiştirildi: " + String.format("%,.2f %s", d.getPrincipal(), d.getCurrency()),
                "Ref: " + d.getReferenceNo());
        new NotificationDAO().addForStaff("Para piyasası borçlanma değiştirildi",
                "Ref: " + d.getReferenceNo(), "MM_BORROW", d.getReferenceNo());
        notifyCustomer(d.getCounterpartyNo(), "Para piyasası işleminiz güncellendi",
                "Ref: " + d.getReferenceNo() + " — yeni tutar "
                        + String.format("%,.2f %s", d.getPrincipal(), d.getCurrency()), d.getReferenceNo());
        if (d.isCreateSwift() || d.isCreateMt320()) generateSwift(d, "MT320");
        if (d.isCreateSwift() || d.isCreateMt202()) generateSwift(d, "MT202");
        return null;
    }

    /** Hesap bakiyesini delta kadar değiştirir (transaction içi). */
    private void move(Connection conn, int accountId, double delta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE account SET balance = balance + ? WHERE account_id = ? AND status = 1")) {
            ps.setDouble(1, delta); ps.setInt(2, accountId); ps.executeUpdate();
        }
    }

    /** Guard'lı çıkış: yeterli bakiye yoksa false (bakiye eksiye düşmez). */
    private boolean debit(Connection conn, int accountId, double amount) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE account SET balance = balance - ? WHERE account_id = ? AND status = 1 AND balance >= ?")) {
            ps.setDouble(1, amount); ps.setInt(2, accountId); ps.setDouble(3, amount);
            return ps.executeUpdate() > 0;
        }
    }

    /** Counterparty sistem müşterisiyse deal dövizindeki AKTİF hesap id'si (yoksa 0 = harici/interbank). */
    private int custAcc(Connection conn, int customerId, String currency) throws SQLException {
        if (customerId <= 0) return 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT account_id FROM account WHERE customer_id = ? AND currency = ? AND status = 1 "
              + "ORDER BY account_id LIMIT 1")) {
            ps.setInt(1, customerId); ps.setString(2, currency);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    private void notifyCustomer(int customerNo, String title, String detail, String ref) {
        if (customerNo > 0) new NotificationDAO().add(customerNo, title, detail, "MM_BORROW", ref);
    }

    /** Counterparty'si bu müşteri olan tüm borçlanmalar (müşteri gözlem ekranı). */
    public List<MoneyMarketBorrowing> getByCounterpartyNo(int customerNo) {
        List<MoneyMarketBorrowing> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(COLS_SQL("WHERE c.customer_no = ? ORDER BY m.id DESC"))) {
            ps.setInt(1, customerNo);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        } catch (SQLException e) { ErrorLogDAO.log(e, "MM borrowing counterparty listeleme"); }
        return list;
    }

    public List<MoneyMarketBorrowing> getAll() { return query(COLS_SQL("ORDER BY m.id DESC")); }
    public List<MoneyMarketBorrowing> getByStatus(String status) {
        return queryOneParam(COLS_SQL("WHERE m.status = ? ORDER BY m.id DESC"), status);
    }

    private String COLS_SQL(String tail) { return "SELECT " + COLS + tail; }

    private List<MoneyMarketBorrowing> query(String sql) {
        List<MoneyMarketBorrowing> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { ErrorLogDAO.log(e, "MM listeleme"); }
        return list;
    }
    private List<MoneyMarketBorrowing> queryOneParam(String sql, String param) {
        List<MoneyMarketBorrowing> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        } catch (SQLException e) { ErrorLogDAO.log(e, "MM listeleme"); }
        return list;
    }

    private MoneyMarketBorrowing map(ResultSet rs) throws SQLException {
        MoneyMarketBorrowing d = new MoneyMarketBorrowing();
        d.setId(rs.getInt("id"));
        d.setReferenceNo(rs.getString("reference_no"));
        d.setCounterpartyId(rs.getInt("counterparty_id"));
        d.setCounterpartyNo(rs.getInt("customer_no"));
        d.setCounterpartyName(rs.getString("cp_name") == null ? "" : rs.getString("cp_name").trim());
        d.setMarketType(rs.getString("market_type"));
        d.setPurpose(rs.getString("purpose"));
        d.setDealer(rs.getString("dealer"));
        d.setBroker(rs.getString("broker"));
        d.setComment(rs.getString("comment"));
        d.setBcs(rs.getString("bcs"));
        d.setCurrency(rs.getString("currency"));
        d.setPrincipal(rs.getDouble("principal"));
        d.setInterestRate(rs.getDouble("interest_rate"));
        d.setDayCount(rs.getString("day_count"));
        d.setDealDate(String.valueOf(rs.getDate("deal_date")));
        d.setValueDate(String.valueOf(rs.getDate("value_date")));
        d.setMaturityDate(String.valueOf(rs.getDate("maturity_date")));
        d.setInterestAmount(rs.getDouble("interest_amount"));
        d.setTaxAmount(rs.getDouble("tax_amount"));
        d.setRepaymentAmount(rs.getDouble("repayment_amount"));
        d.setStopaj(rs.getInt("stopaj_flag") == 1);
        d.setReceivingAccountId(rs.getInt("receiving_account_id"));
        d.setRepaymentAccountId(rs.getInt("repayment_account_id"));
        d.setCorrespondent1Bic(rs.getString("correspondent1_bic"));
        d.setCorrespondent2Bic(rs.getString("correspondent2_bic"));
        d.setCreateSwift(rs.getInt("create_swift") == 1);
        d.setCreateMt320(rs.getInt("create_mt320") == 1);
        d.setCreateMt202(rs.getInt("create_mt202") == 1);
        d.setStatus(rs.getString("status"));
        d.setCreatedBy(rs.getString("created_by"));
        d.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
        d.setSettledAt(String.valueOf(rs.getDate("settled_at")));
        d.setMaturedAt(String.valueOf(rs.getDate("matured_at")));
        return d;
    }
}
