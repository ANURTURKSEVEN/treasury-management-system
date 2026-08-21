package com.gtech.treasury.dao;

import com.gtech.treasury.model.MoneyMarketLending;
import com.gtech.treasury.model.MoneyMarketLendingCharge;
import com.gtech.treasury.util.DBConnection;
import com.gtech.treasury.util.InterestCalculationService;
import com.gtech.treasury.util.ReferenceGenerator;
import com.gtech.treasury.util.Session;
import com.gtech.treasury.util.SwiftMessageService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Para Piyasası PLASMAN (borç verme) deal'lerinin veri erişimi (mm_lending).
 * MoneyMarketBorrowingDAO'nun aynasıdır; nakit yönü terstir:
 *   create      : valörde banka kasasından fon ÇIKIŞI (−anapara).
 *   matureDue   : vadede banka kasasına GİRİŞ (+geri ödeme).
 *   cancel      : kasaya anaparayı geri getir (+anapara).
 *   rollover    : eski deal'i tahsil edip (kasaya +) yeni bir deal aç (kasadan −); eski deal ROLLED_OVER.
 *   earlyClose  : vadeden önce kapat; işleyen faiz + varsa penalty kasaya girer.
 * Tüm nakit hareketleri tek transaction + rollback ile yürütülür.
 */
public class MoneyMarketLendingDAO {

    private static final String COLS =
            "m.id, m.reference_no, m.counterparty_id, c.customer_no, "
          + "CONCAT(IFNULL(c.customer_name,''),' ',IFNULL(c.surname,'')) AS cp_name, "
          + "m.market_type, m.purpose, m.dealer, m.broker, m.comment, m.bcs, m.currency, "
          + "m.principal, m.interest_rate, m.day_count, m.deal_date, m.value_date, m.maturity_date, "
          + "m.interest_amount, m.tax_amount, m.repayment_amount, m.stopaj_flag, "
          + "m.funding_account_id, m.collection_account_id, m.correspondent1_bic, m.correspondent2_bic, "
          + "m.create_swift, m.create_mt320, m.create_mt202, m.status, m.parent_deal_id, m.rolled_to_id, "
          + "m.early_closed_at, m.penalty_amount, m.created_by, m.created_at, m.settled_at, m.matured_at "
          + "FROM mm_lending m LEFT JOIN customer c ON m.counterparty_id = c.customer_id ";

    private static final String INS =
            "INSERT INTO mm_lending (reference_no, counterparty_id, market_type, purpose, dealer, broker, comment, "
          + "bcs, currency, principal, interest_rate, day_count, deal_date, value_date, maturity_date, "
          + "interest_amount, tax_amount, repayment_amount, stopaj_flag, funding_account_id, collection_account_id, "
          + "correspondent1_bic, correspondent2_bic, create_swift, create_mt320, create_mt202, status, "
          + "parent_deal_id, created_by, settled_at) "
          + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    /** İş kuralı ihlali (rollback + kullanıcı mesajı). */
    private static class Biz extends RuntimeException { Biz(String m) { super(m); } }

    // =============================== CREATE ===============================
    /** Deal'i kaydeder ve valörde banka kasasından anaparayı karşı tarafa verir (−anapara). */
    public String create(MoneyMarketLending d) {
        if (d.getFundingAccountId() <= 0) return "Fon çıkış (borçlandırılan) hesabı seçilmedi.";
        if (d.getPrincipal() <= 0) return "Plasman tutarı sıfırdan büyük olmalı.";

        String ref = ReferenceGenerator.next("MML", "mm_lending", "reference_no");
        String actor = Session.getCurrentUsername();
        d.setReferenceNo(ref);
        d.setStatus("ACTIVE");
        d.setCreatedBy(actor);

        int newId = 0;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                newId = insertDeal(conn, d, actor);
                conn.commit();
            } catch (Biz b) { conn.rollback(); return b.getMessage(); }
            catch (SQLException e) { conn.rollback(); throw e; }
        } catch (SQLException e) {
            System.err.println("MM lending create hatası: " + e.getMessage());
            ErrorLogDAO.log(e, "MM lending create");
            return "İşlem sırasında hata: " + e.getMessage();
        }
        d.setId(newId);

        ActivityLogDAO.log("MM_LEND_CREATE", d.getCounterpartyNo(), d.getPrincipal(), d.getCurrency(),
                "Para piyasası plasman kaydedildi: " + String.format("%,.2f %s", d.getPrincipal(), d.getCurrency()),
                "Ref: " + ref + " | Valör: " + d.getValueDate() + " | Vade: " + d.getMaturityDate()
                        + " | Faiz: " + d.getInterestRate() + " (" + d.getDayCount() + ") | Dealer: " + actor);
        new NotificationDAO().addForStaff("Para piyasası plasman kaydedildi",
                "Ref: " + ref + " — " + String.format("%,.2f %s", d.getPrincipal(), d.getCurrency())
                        + " / Vade: " + d.getMaturityDate(), "MM_LEND", ref);
        notifyCustomer(d.getCounterpartyNo(), "Adınıza para piyasası plasmanı açıldı (borçlusunuz)",
                "Ref: " + ref + " — " + String.format("%,.2f %s", d.getPrincipal(), d.getCurrency())
                        + " hesabınıza aktarıldı. Vade: " + d.getMaturityDate()
                        + " — geri ödeme: " + String.format("%,.2f %s", d.getRepaymentAmount(), d.getCurrency()), ref);
        if (d.isCreateSwift() || d.isCreateMt320()) generateSwift(d, "MT320");
        if (d.isCreateSwift() || d.isCreateMt202()) generateSwift(d, "MT202");
        return null;
    }

    /** Deal satırını + valör fon çıkışını (−anapara) + masraf kalemlerini yazar. @return yeni id. */
    private int insertDeal(Connection conn, MoneyMarketLending d, String actor) throws SQLException {
        int newId = 0;
        try (PreparedStatement ps = conn.prepareStatement(INS, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setString(i++, d.getReferenceNo());
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
            ps.setInt(i++, d.getFundingAccountId());
            if (d.getCollectionAccountId() > 0) ps.setInt(i++, d.getCollectionAccountId()); else ps.setNull(i++, java.sql.Types.INTEGER);
            ps.setString(i++, d.getCorrespondent1Bic());
            ps.setString(i++, d.getCorrespondent2Bic());
            ps.setInt(i++, d.isCreateSwift() ? 1 : 0);
            ps.setInt(i++, d.isCreateMt320() ? 1 : 0);
            ps.setInt(i++, d.isCreateMt202() ? 1 : 0);
            ps.setString(i++, d.getStatus());
            if (d.getParentDealId() > 0) ps.setInt(i++, d.getParentDealId()); else ps.setNull(i++, java.sql.Types.INTEGER);
            ps.setString(i++, actor);
            ps.setString(i++, d.getValueDate());   // settled_at = valör
            ps.executeUpdate();
            try (ResultSet gk = ps.getGeneratedKeys()) { if (gk.next()) newId = gk.getInt(1); }
        }
        // Valörde banka kasasından fon çıkışı (−anapara) — yeterli bakiye yoksa deal kaydedilemez
        if (!debit(conn, d.getFundingAccountId(), d.getPrincipal()))
            throw new Biz("Fon çıkış hesabında yeterli bakiye yok; plasman kaydedilemedi.");
        // Müşteri ayağı: counterparty sistem müşterisiyse fonu onun hesabına teslim et (+anapara)
        int cAcc = custAcc(conn, d.getCounterpartyId(), d.getCurrency());
        if (cAcc > 0) credit(conn, cAcc, d.getPrincipal());
        // Masraf kalemleri: kaydet; BANKA'nın ödediği (deal döviziyle) kalemler kasadan düşülür
        for (MoneyMarketLendingCharge ch : d.getCharges()) {
            if (ch == null || ch.getAmount() <= 0) continue;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO mm_lending_charge (ml_id, charge_type, amount, currency, payer, note) VALUES (?,?,?,?,?,?)")) {
                ps.setInt(1, newId); ps.setString(2, ch.getChargeType()); ps.setDouble(3, ch.getAmount());
                ps.setString(4, ch.getCurrency()); ps.setString(5, ch.getPayer()); ps.setString(6, ch.getNote());
                ps.executeUpdate();
            }
            if ("BANKA".equals(ch.getPayer()) && d.getCurrency().equals(ch.getCurrency())) {
                if (!debit(conn, d.getFundingAccountId(), ch.getAmount()))
                    throw new Biz("Masraf için kasada yeterli bakiye yok: " + ch.getTypeLabel());
            }
        }
        return newId;
    }

    /** SWIFT mesajını üretir ve personel ortak kutusuna (message) kaydeder. */
    private void generateSwift(MoneyMarketLending d, String type) {
        try {
            String body = "MT320".equals(type) ? SwiftMessageService.buildMT320(d) : SwiftMessageService.buildMT202(d);
            new MessageDAO().send("SYSTEM", "STAFF",
                    type + " — " + d.getReferenceNo(), body, "SWIFT_" + type, d.getReferenceNo());
        } catch (Exception e) {
            ErrorLogDAO.log(e, "SWIFT üretimi (lending) " + type);
        }
    }

    // =============================== MATURE ===============================
    /** Vadesi gelen (ACTIVE, maturity<=bugün) plasmanları kapatır: kasaya geri ödeme girer. @return adet. */
    public int matureDue() {
        String sel = "SELECT m.id, m.currency, m.repayment_amount, m.collection_account_id, m.reference_no, "
                   + "m.counterparty_id, c.customer_no FROM mm_lending m "
                   + "LEFT JOIN customer c ON m.counterparty_id = c.customer_id "
                   + "WHERE m.status = 'ACTIVE' AND m.maturity_date <= CURDATE()";
        int ok = 0;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sel);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (settleOne(rs.getInt("id"), rs.getString("currency"), rs.getDouble("repayment_amount"),
                        rs.getInt("collection_account_id"), rs.getString("reference_no"),
                        rs.getInt("counterparty_id"), rs.getInt("customer_no"))) ok++;
            }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "MM lending matureDue");
        }
        return ok;
    }

    private boolean settleOne(int id, String cur, double repay, int collAcc, String ref,
                              int counterpartyId, int counterpartyNo) {
        if (collAcc <= 0) return false;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Müşteri ayağı: borçlu müşteri geri ödemeyi hesabından öder (−geri ödeme). Bakiye yoksa kapatılamaz.
                int cAcc = custAcc(conn, counterpartyId, cur);
                if (cAcc > 0 && !debit(conn, cAcc, repay)) { conn.rollback(); return false; }
                credit(conn, collAcc, repay);   // vadede tahsil: banka kasasına +geri ödeme
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE mm_lending SET status = 'MATURED', matured_at = CURDATE() WHERE id = ? AND status = 'ACTIVE'")) {
                    ps.setInt(1, id);
                    if (ps.executeUpdate() == 0) { conn.rollback(); return false; }
                }
                conn.commit();
            } catch (SQLException e) { conn.rollback(); throw e; }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "MM lending settle");
            return false;
        }
        ActivityLogDAO.log("MM_LEND_MATURE", counterpartyNo, repay, cur,
                "Para piyasası plasman vade sonu tahsil edildi: " + String.format("%,.2f %s", repay, cur),
                "Ref: " + ref);
        new NotificationDAO().addForStaff("Para piyasası plasman vadesine ulaştı",
                "Ref: " + ref + " tahsil edildi: " + String.format("%,.2f %s", repay, cur), "MM_LEND", ref);
        notifyCustomer(counterpartyNo, "Para piyasası borcunuz kapandı",
                "Ref: " + ref + " — geri ödeme " + String.format("%,.2f %s", repay, cur)
                        + " hesabınızdan tahsil edildi.", ref);
        return true;
    }

    /**
     * Müşterinin kendi ödeme ekranından, borçlu olduğu (banka plasmanı) deal'i kendi hesabından ödemesi.
     * Kapanış tarihi bugündür: işleyen faiz hesaplanır (erken ise), tam vade ise repayment kullanılır.
     */
    public String payByCustomer(int dealId, int customerNo) {
        String today = LocalDate.now().toString();
        // earlyClose ile aynı mekanizma (penalty=0); vade geçtiyse repayment tam alınır.
        return settleByCustomerOrBank(dealId, today, 0, customerNo, true);
    }

    // =============================== CANCEL ===============================
    /** Aktif bir plasmanı iptal eder: karşı tarafa verilen anaparayı kasaya geri getirir (+anapara). */
    public String cancel(int id) {
        String cur = null, ref = null; double principal = 0; int fundAcc = 0, cpId = 0, cpNo = 0;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT m.currency, m.principal, m.funding_account_id, m.reference_no, m.counterparty_id, c.customer_no "
                      + "FROM mm_lending m LEFT JOIN customer c ON m.counterparty_id = c.customer_id "
                      + "WHERE m.id = ? AND m.status = 'ACTIVE'")) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return "Sadece AKTİF plasman iptal edilebilir."; }
                        cur = rs.getString("currency"); principal = rs.getDouble("principal");
                        fundAcc = rs.getInt("funding_account_id"); ref = rs.getString("reference_no");
                        cpId = rs.getInt("counterparty_id"); cpNo = rs.getInt("customer_no");
                    }
                }
                // Müşteri ayağı ters kayıt: teslim edilen anaparayı müşteri hesabından geri al (−) — harcanmışsa iptal edilemez
                int cAcc = custAcc(conn, cpId, cur);
                if (cAcc > 0 && !debit(conn, cAcc, principal))
                    { conn.rollback(); return "Müşteri hesabında geri alınacak bakiye yok (fon kullanılmış olabilir); iptal edilemez."; }
                credit(conn, fundAcc, principal);   // fonu banka kasasına geri getir
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE mm_lending SET status = 'CANCELLED' WHERE id = ? AND status = 'ACTIVE'")) {
                    ps.setInt(1, id);
                    if (ps.executeUpdate() == 0) { conn.rollback(); return "İşlem güncellenemedi."; }
                }
                conn.commit();
            } catch (SQLException e) { conn.rollback(); throw e; }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "MM lending iptal");
            return "İşlem sırasında hata: " + e.getMessage();
        }
        ActivityLogDAO.log("MM_LEND_CANCEL", cpNo, principal, cur,
                "Para piyasası plasman iptal edildi: " + String.format("%,.2f %s", principal, cur), "Ref: " + ref);
        new NotificationDAO().addForStaff("Para piyasası plasman iptal edildi",
                "Ref: " + ref + " iptal edildi; anapara kasaya geri alındı.", "MM_LEND", ref);
        notifyCustomer(cpNo, "Para piyasası işleminiz iptal edildi",
                "Ref: " + ref + " iptal edildi; teslim edilen anapara hesabınızdan geri alındı.", ref);
        return null;
    }

    // =============================== AMEND ===============================
    /** Aktif bir plasmanı değiştirir: eski nakit etkisini geri alıp yeni değerlerle yeniden uygular. */
    public String amend(MoneyMarketLending d) {
        if (d.getId() <= 0) return "Geçersiz işlem.";
        if (d.getFundingAccountId() <= 0) return "Fon çıkış hesabı seçilmedi.";
        if (d.getPrincipal() <= 0) return "Plasman tutarı sıfırdan büyük olmalı.";

        String upd = "UPDATE mm_lending SET counterparty_id=?, market_type=?, purpose=?, dealer=?, broker=?, "
                + "comment=?, bcs=?, currency=?, principal=?, interest_rate=?, day_count=?, deal_date=?, value_date=?, "
                + "maturity_date=?, interest_amount=?, tax_amount=?, repayment_amount=?, stopaj_flag=?, "
                + "funding_account_id=?, collection_account_id=?, correspondent1_bic=?, correspondent2_bic=?, "
                + "create_swift=?, create_mt320=?, create_mt202=?, settled_at=? WHERE id=? AND status='ACTIVE'";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int oldFund = 0, oldCpId = 0; double oldPrincipal = 0; String oldCur = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT principal, funding_account_id, currency, counterparty_id FROM mm_lending WHERE id=? AND status='ACTIVE'")) {
                    ps.setInt(1, d.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return "Sadece AKTİF plasman değiştirilebilir."; }
                        oldPrincipal = rs.getDouble("principal"); oldFund = rs.getInt("funding_account_id");
                        oldCur = rs.getString("currency"); oldCpId = rs.getInt("counterparty_id");
                    }
                }
                double oldBankCharges = 0;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COALESCE(SUM(amount),0) FROM mm_lending_charge WHERE ml_id=? AND payer='BANKA'")) {
                    ps.setInt(1, d.getId());
                    try (ResultSet rs = ps.executeQuery()) { if (rs.next()) oldBankCharges = rs.getDouble(1); }
                }
                // Eski nakit etkisini geri al: verilen anaparayı ve BANKA masraflarını kasaya iade et (+)
                credit(conn, oldFund, oldPrincipal);
                if (oldBankCharges > 0) credit(conn, oldFund, oldBankCharges);
                // Müşteri ayağı ters kayıt: eskiden teslim edilen anaparayı müşteri hesabından geri al (−)
                int oldCAcc = custAcc(conn, oldCpId, oldCur);
                if (oldCAcc > 0 && !debit(conn, oldCAcc, oldPrincipal))
                    { conn.rollback(); return "Müşteri hesabında iade edilecek bakiye yok; değişiklik yapılamaz."; }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM mm_lending_charge WHERE ml_id=?")) {
                    ps.setInt(1, d.getId()); ps.executeUpdate();
                }
                // Yeni nakit etkisini uygula: yeni anaparayı kasadan çıkar (guard) + yeni BANKA masraflarını düş + kalemleri yaz
                if (!debit(conn, d.getFundingAccountId(), d.getPrincipal()))
                    { conn.rollback(); return "Yeni fon çıkış hesabında yeterli bakiye yok; değişiklik yapılamaz."; }
                // Müşteri ayağı yeni teslim: yeni anaparayı müşteri hesabına ver (+)
                int newCAcc = custAcc(conn, d.getCounterpartyId(), d.getCurrency());
                if (newCAcc > 0) credit(conn, newCAcc, d.getPrincipal());
                for (MoneyMarketLendingCharge ch : d.getCharges()) {
                    if (ch == null || ch.getAmount() <= 0) continue;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO mm_lending_charge (ml_id, charge_type, amount, currency, payer, note) VALUES (?,?,?,?,?,?)")) {
                        ps.setInt(1, d.getId()); ps.setString(2, ch.getChargeType()); ps.setDouble(3, ch.getAmount());
                        ps.setString(4, ch.getCurrency()); ps.setString(5, ch.getPayer()); ps.setString(6, ch.getNote());
                        ps.executeUpdate();
                    }
                    if ("BANKA".equals(ch.getPayer()) && d.getCurrency().equals(ch.getCurrency())) {
                        if (!debit(conn, d.getFundingAccountId(), ch.getAmount()))
                            { conn.rollback(); return "Masraf için kasada yeterli bakiye yok: " + ch.getTypeLabel(); }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(upd)) {
                    int i = 1;
                    if (d.getCounterpartyId() > 0) ps.setInt(i++, d.getCounterpartyId()); else ps.setNull(i++, java.sql.Types.INTEGER);
                    ps.setString(i++, d.getMarketType()); ps.setString(i++, d.getPurpose()); ps.setString(i++, d.getDealer());
                    ps.setString(i++, d.getBroker()); ps.setString(i++, d.getComment()); ps.setString(i++, d.getBcs());
                    ps.setString(i++, d.getCurrency()); ps.setDouble(i++, d.getPrincipal()); ps.setDouble(i++, d.getInterestRate());
                    ps.setString(i++, d.getDayCount()); ps.setString(i++, d.getDealDate()); ps.setString(i++, d.getValueDate());
                    ps.setString(i++, d.getMaturityDate()); ps.setDouble(i++, d.getInterestAmount()); ps.setDouble(i++, d.getTaxAmount());
                    ps.setDouble(i++, d.getRepaymentAmount()); ps.setInt(i++, d.isStopaj() ? 1 : 0);
                    ps.setInt(i++, d.getFundingAccountId());
                    if (d.getCollectionAccountId() > 0) ps.setInt(i++, d.getCollectionAccountId()); else ps.setNull(i++, java.sql.Types.INTEGER);
                    ps.setString(i++, d.getCorrespondent1Bic()); ps.setString(i++, d.getCorrespondent2Bic());
                    ps.setInt(i++, d.isCreateSwift() ? 1 : 0); ps.setInt(i++, d.isCreateMt320() ? 1 : 0); ps.setInt(i++, d.isCreateMt202() ? 1 : 0);
                    ps.setString(i++, d.getValueDate()); ps.setInt(i++, d.getId());
                    if (ps.executeUpdate() == 0) { conn.rollback(); return "İşlem güncellenemedi."; }
                }
                conn.commit();
            } catch (SQLException e) { conn.rollback(); throw e; }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "MM lending amend");
            return "İşlem sırasında hata: " + e.getMessage();
        }
        ActivityLogDAO.log("MM_LEND_AMEND", d.getCounterpartyNo(), d.getPrincipal(), d.getCurrency(),
                "Para piyasası plasman değiştirildi: " + String.format("%,.2f %s", d.getPrincipal(), d.getCurrency()),
                "Ref: " + d.getReferenceNo());
        new NotificationDAO().addForStaff("Para piyasası plasman değiştirildi",
                "Ref: " + d.getReferenceNo(), "MM_LEND", d.getReferenceNo());
        notifyCustomer(d.getCounterpartyNo(), "Para piyasası işleminiz güncellendi",
                "Ref: " + d.getReferenceNo() + " — yeni tutar "
                        + String.format("%,.2f %s", d.getPrincipal(), d.getCurrency()), d.getReferenceNo());
        if (d.isCreateSwift() || d.isCreateMt320()) generateSwift(d, "MT320");
        if (d.isCreateSwift() || d.isCreateMt202()) generateSwift(d, "MT202");
        return null;
    }

    // =============================== ROLLOVER ===============================
    /**
     * Vade uzatma: eski AKTİF deal tahsil edilmiş (kasaya +geri ödeme) sayılır ve ROLLED_OVER olur;
     * yeni deal (parent_deal_id=eski) açılır (kasadan −yeni anapara). Geçmiş korunur.
     */
    public String rollover(int oldId, MoneyMarketLending newDeal) {
        if (newDeal.getFundingAccountId() <= 0) return "Yeni deal için fon çıkış hesabı seçilmedi.";
        if (newDeal.getPrincipal() <= 0) return "Yeni anapara sıfırdan büyük olmalı.";

        String ref = ReferenceGenerator.next("MML", "mm_lending", "reference_no");
        String actor = Session.getCurrentUsername();
        newDeal.setReferenceNo(ref);
        newDeal.setStatus("ACTIVE");
        newDeal.setParentDealId(oldId);
        newDeal.setCreatedBy(actor);

        String oldRef = null, oldCur = null; double oldRepay = 0; int oldColl = 0, oldCpId = 0;
        int newId = 0;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT reference_no, repayment_amount, collection_account_id, currency, counterparty_id FROM mm_lending "
                      + "WHERE id=? AND status='ACTIVE'")) {
                    ps.setInt(1, oldId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return "Sadece AKTİF plasman rollover edilebilir."; }
                        oldRef = rs.getString("reference_no"); oldRepay = rs.getDouble("repayment_amount");
                        oldColl = rs.getInt("collection_account_id");
                        oldCur = rs.getString("currency"); oldCpId = rs.getInt("counterparty_id");
                    }
                }
                // Eski deal tahsil: borçlu müşteri geri ödemeyi hesabından öder (−); banka kasasına +
                int oldCAcc = custAcc(conn, oldCpId, oldCur);
                if (oldCAcc > 0 && !debit(conn, oldCAcc, oldRepay))
                    { conn.rollback(); return "Müşteri hesabında eski borcu kapatacak bakiye yok; rollover yapılamaz."; }
                if (oldColl > 0) credit(conn, oldColl, oldRepay);   // eski deal tahsil (kasaya +)
                newId = insertDeal(conn, newDeal, actor);            // yeni deal (kasadan −yeni anapara, müşteriye +)
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE mm_lending SET status='ROLLED_OVER', rolled_to_id=?, matured_at=CURDATE() "
                      + "WHERE id=? AND status='ACTIVE'")) {
                    ps.setInt(1, newId); ps.setInt(2, oldId);
                    if (ps.executeUpdate() == 0) { conn.rollback(); return "Eski deal güncellenemedi."; }
                }
                conn.commit();
            } catch (Biz b) { conn.rollback(); return b.getMessage(); }
            catch (SQLException e) { conn.rollback(); throw e; }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "MM lending rollover");
            return "İşlem sırasında hata: " + e.getMessage();
        }
        newDeal.setId(newId);
        ActivityLogDAO.log("MM_LEND_ROLLOVER", newDeal.getCounterpartyNo(), newDeal.getPrincipal(), newDeal.getCurrency(),
                "Para piyasası plasman rollover: " + oldRef + " → " + ref,
                "Eski tahsil: " + String.format("%,.2f %s", oldRepay, newDeal.getCurrency())
                        + " | Yeni anapara: " + String.format("%,.2f %s", newDeal.getPrincipal(), newDeal.getCurrency())
                        + " | Yeni vade: " + newDeal.getMaturityDate());
        new NotificationDAO().addForStaff("Para piyasası plasman rollover edildi",
                oldRef + " → " + ref + " (yeni vade: " + newDeal.getMaturityDate() + ")", "MM_LEND", ref);
        notifyCustomer(newDeal.getCounterpartyNo(), "Para piyasası işleminiz yenilendi (rollover)",
                oldRef + " kapandı, " + ref + " açıldı — yeni vade: " + newDeal.getMaturityDate(), ref);
        if (newDeal.isCreateSwift() || newDeal.isCreateMt320()) generateSwift(newDeal, "MT320");
        if (newDeal.isCreateSwift() || newDeal.isCreateMt202()) generateSwift(newDeal, "MT202");
        return null;
    }

    // =============================== EARLY CLOSE ===============================
    /**
     * Erken kapama: vadeden önce plasmanı kapatır. Kapanış tarihine kadar işleyen faiz hesaplanır,
     * varsa penalty eklenir; toplam tahsil kasaya girer. Deal EARLY_CLOSED olur.
     */
    public String earlyClose(int id, String closeDate, double penalty) {
        return settleByCustomerOrBank(id, closeDate, penalty, 0, false);
    }

    /**
     * Plasmanı kapanış tarihine göre kapatır (banka erken kapama veya müşteri ödemesi).
     *   - closeDate >= vade  → tam geri ödeme, status MATURED.
     *   - closeDate <  vade  → işleyen faiz + penalty, status EARLY_CLOSED.
     * Müşteri ayağı: borçlu müşteri hesabından tahsilat (−collected, guard); banka kasasına +collected.
     * customerInitiated=true ise counterparty müşteri no doğrulanır (kendi deal'ini ödeyebilir).
     */
    private String settleByCustomerOrBank(int id, String closeDate, double penalty,
                                          int customerNo, boolean customerInitiated) {
        if (closeDate == null || closeDate.isBlank()) return "Kapanış tarihi girin.";
        if (penalty < 0) return "Penalty negatif olamaz.";

        String cur = null, ref = null, valueDate = null, dayCount = null, maturity = null;
        double principal = 0, rate = 0; int collAcc = 0, cpId = 0, cpNo = 0; boolean stopaj = false;
        double interest, tax, collected; boolean full;
        try {
            LocalDate cd = LocalDate.parse(closeDate);
            try (Connection conn = DBConnection.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT m.currency, m.principal, m.interest_rate, m.day_count, m.value_date, m.maturity_date, "
                          + "m.stopaj_flag, m.collection_account_id, m.reference_no, m.counterparty_id, c.customer_no "
                          + "FROM mm_lending m LEFT JOIN customer c ON m.counterparty_id=c.customer_id "
                          + "WHERE m.id=? AND m.status='ACTIVE'")) {
                        ps.setInt(1, id);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) { conn.rollback(); return "Sadece AKTİF plasman kapatılabilir."; }
                            cur = rs.getString("currency"); principal = rs.getDouble("principal");
                            rate = rs.getDouble("interest_rate"); dayCount = rs.getString("day_count");
                            valueDate = String.valueOf(rs.getDate("value_date"));
                            maturity = String.valueOf(rs.getDate("maturity_date"));
                            stopaj = rs.getInt("stopaj_flag") == 1; collAcc = rs.getInt("collection_account_id");
                            ref = rs.getString("reference_no"); cpId = rs.getInt("counterparty_id");
                            cpNo = rs.getInt("customer_no");
                        }
                    }
                    if (customerInitiated && cpNo != customerNo) { conn.rollback(); return "Bu işlemi ödeme yetkiniz yok."; }
                    LocalDate vd = LocalDate.parse(valueDate);
                    if (cd.isBefore(vd)) { conn.rollback(); return "Kapanış tarihi valör tarihinden önce olamaz."; }
                    full = !cd.isBefore(LocalDate.parse(maturity));   // vade veya sonrası → tam geri ödeme

                    LocalDate calcTo = full ? LocalDate.parse(maturity) : cd;
                    InterestCalculationService.Result res =
                            InterestCalculationService.calculate(principal, rate, vd, calcTo, dayCount, stopaj);
                    interest = res.interest; tax = res.tax;
                    collected = res.repayment + penalty;

                    // Müşteri ayağı: borçlu müşteri hesabından tahsil (−collected)
                    int cAcc = custAcc(conn, cpId, cur);
                    if (cAcc > 0 && !debit(conn, cAcc, collected))
                        { conn.rollback(); return "Hesabınızda yeterli bakiye yok; ödeme yapılamadı."; }
                    if (collAcc > 0) credit(conn, collAcc, collected);   // banka kasasına tahsil
                    String newStatus = full ? "MATURED" : "EARLY_CLOSED";
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE mm_lending SET status=?, early_closed_at=?, matured_at=?, "
                          + "penalty_amount=?, interest_amount=?, tax_amount=?, repayment_amount=? "
                          + "WHERE id=? AND status='ACTIVE'")) {
                        ps.setString(1, newStatus);
                        if (full) ps.setNull(2, java.sql.Types.DATE); else ps.setString(2, closeDate);
                        ps.setString(3, closeDate); ps.setDouble(4, penalty);
                        ps.setDouble(5, interest); ps.setDouble(6, tax); ps.setDouble(7, collected); ps.setInt(8, id);
                        if (ps.executeUpdate() == 0) { conn.rollback(); return "İşlem güncellenemedi."; }
                    }
                    conn.commit();
                } catch (SQLException e) { conn.rollback(); throw e; }
            }
        } catch (java.time.format.DateTimeParseException pe) {
            return "Kapanış tarihi geçersiz (yyyy-MM-dd).";
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "MM lending settle/earlyClose");
            return "İşlem sırasında hata: " + e.getMessage();
        }
        String action = full ? "MM_LEND_MATURE" : "MM_LEND_EARLY_CLOSE";
        String who = customerInitiated ? "müşteri ödemesi" : (full ? "vade tahsili" : "erken kapama");
        ActivityLogDAO.log(action, cpNo, collected, cur,
                "Para piyasası plasman kapatıldı (" + who + "): " + String.format("%,.2f %s", collected, cur),
                "Ref: " + ref + " | Kapanış: " + closeDate + " | Faiz: " + String.format("%,.2f", interest)
                        + (penalty > 0 ? " | Penalty: " + String.format("%,.2f", penalty) : ""));
        new NotificationDAO().addForStaff("Para piyasası plasman kapatıldı (" + who + ")",
                "Ref: " + ref + " — tahsil: " + String.format("%,.2f %s", collected, cur), "MM_LEND", ref);
        notifyCustomer(cpNo, "Para piyasası borcunuz ödendi",
                "Ref: " + ref + " — " + String.format("%,.2f %s", collected, cur)
                        + " hesabınızdan ödendi ve işlem kapandı.", ref);
        return null;
    }

    // =============================== yardımcı nakit ===============================
    /** Guard'lı çıkış: yeterli bakiye yoksa false döner (bakiye eksiye düşmez). */
    private boolean debit(Connection conn, int accountId, double amount) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE account SET balance = balance - ? WHERE account_id = ? AND status = 1 AND balance >= ?")) {
            ps.setDouble(1, amount); ps.setInt(2, accountId); ps.setDouble(3, amount);
            return ps.executeUpdate() > 0;
        }
    }
    /** Giriş (+). */
    private void credit(Connection conn, int accountId, double amount) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE account SET balance = balance + ? WHERE account_id = ? AND status = 1")) {
            ps.setDouble(1, amount); ps.setInt(2, accountId); ps.executeUpdate();
        }
    }

    /**
     * Counterparty bir sistem müşterisiyse, deal dövizindeki AKTİF hesabının id'sini döner (yoksa 0).
     * 0 → harici/interbank karşı taraf; müşteri ayağı uygulanmaz (yalnız banka kasası hareket eder).
     */
    private int custAcc(Connection conn, int customerId, String currency) throws SQLException {
        if (customerId <= 0) return 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT account_id FROM account WHERE customer_id = ? AND currency = ? AND status = 1 "
              + "ORDER BY account_id LIMIT 1")) {
            ps.setInt(1, customerId); ps.setString(2, currency);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    /** Counterparty müşteriye bildirim (varsa). */
    private void notifyCustomer(int customerNo, String title, String detail, String ref) {
        if (customerNo > 0) new NotificationDAO().add(customerNo, title, detail, "MM_LEND", ref);
    }

    // =============================== okuma ===============================
    public List<MoneyMarketLendingCharge> getCharges(int mlId) {
        List<MoneyMarketLendingCharge> list = new ArrayList<>();
        String sql = "SELECT charge_type, amount, currency, payer, note FROM mm_lending_charge WHERE ml_id = ? ORDER BY id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, mlId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MoneyMarketLendingCharge ch = new MoneyMarketLendingCharge(rs.getString("charge_type"),
                            rs.getDouble("amount"), rs.getString("currency"), rs.getString("payer"), rs.getString("note"));
                    ch.setMlId(mlId);
                    list.add(ch);
                }
            }
        } catch (SQLException e) { ErrorLogDAO.log(e, "MM lending masraf listeleme"); }
        return list;
    }

    public List<MoneyMarketLending> getAll() { return query("SELECT " + COLS + "ORDER BY m.id DESC"); }
    public List<MoneyMarketLending> getByStatus(String status) {
        return queryOneParam("SELECT " + COLS + "WHERE m.status = ? ORDER BY m.id DESC", status);
    }
    /** Counterparty'si bu müşteri olan tüm plasmanlar (müşteri gözlem ekranı). */
    public List<MoneyMarketLending> getByCounterpartyNo(int customerNo) {
        return queryOneParamInt("SELECT " + COLS + "WHERE c.customer_no = ? ORDER BY m.id DESC", customerNo);
    }
    /** Müşterinin ödeyebileceği (AKTİF, borçlu olduğu) plasmanlar. */
    public List<MoneyMarketLending> getPayableByCustomer(int customerNo) {
        return queryOneParamInt("SELECT " + COLS + "WHERE c.customer_no = ? AND m.status = 'ACTIVE' ORDER BY m.maturity_date", customerNo);
    }

    private List<MoneyMarketLending> queryOneParamInt(String sql, int param) {
        List<MoneyMarketLending> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        } catch (SQLException e) { ErrorLogDAO.log(e, "MM lending listeleme"); }
        return list;
    }

    private List<MoneyMarketLending> query(String sql) {
        List<MoneyMarketLending> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { ErrorLogDAO.log(e, "MM lending listeleme"); }
        return list;
    }
    private List<MoneyMarketLending> queryOneParam(String sql, String param) {
        List<MoneyMarketLending> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        } catch (SQLException e) { ErrorLogDAO.log(e, "MM lending listeleme"); }
        return list;
    }

    private MoneyMarketLending map(ResultSet rs) throws SQLException {
        MoneyMarketLending d = new MoneyMarketLending();
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
        d.setFundingAccountId(rs.getInt("funding_account_id"));
        d.setCollectionAccountId(rs.getInt("collection_account_id"));
        d.setCorrespondent1Bic(rs.getString("correspondent1_bic"));
        d.setCorrespondent2Bic(rs.getString("correspondent2_bic"));
        d.setCreateSwift(rs.getInt("create_swift") == 1);
        d.setCreateMt320(rs.getInt("create_mt320") == 1);
        d.setCreateMt202(rs.getInt("create_mt202") == 1);
        d.setStatus(rs.getString("status"));
        d.setParentDealId(rs.getInt("parent_deal_id"));
        d.setRolledToId(rs.getInt("rolled_to_id"));
        d.setEarlyClosedAt(String.valueOf(rs.getDate("early_closed_at")));
        double pen = rs.getDouble("penalty_amount"); d.setPenaltyAmount(rs.wasNull() ? null : pen);
        d.setCreatedBy(rs.getString("created_by"));
        d.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
        d.setSettledAt(String.valueOf(rs.getDate("settled_at")));
        d.setMaturedAt(String.valueOf(rs.getDate("matured_at")));
        return d;
    }
}
