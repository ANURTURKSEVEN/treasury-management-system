package com.gtech.treasury.dao;

import com.gtech.treasury.model.Account;
import com.gtech.treasury.model.CurrencyRate;
import com.gtech.treasury.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * account tablosuna erişim. Hesaplar müşteriye bağlıdır; tür + döviz ile açılır.
 * account_no: 10 haneli otomatik (1000000000 + account_id).
 */
public class AccountDAO {

    /** Banka (hazine) müşterisinin customer_no'su — spot işlemlerde karşı taraf. */
    public static final int BANK_CUSTOMER_NO = 99999999;

    private static final String BASE_SELECT =
            "SELECT a.account_id, a.account_no, a.customer_id, c.customer_no, c.customer_name, "
          + "t.type_name AS account_type, a.currency, a.balance, a.status, a.opened_at "
          + "FROM account a "
          + "JOIN customer c    ON a.customer_id = c.customer_id "
          + "JOIN account_type t ON a.account_type_id = t.type_id ";

    /** Bir müşterinin AKTİF (status=1) hesap sayısı. */
    public int countActiveAccounts(int customerId) {
        String sql = "SELECT COUNT(*) FROM account WHERE customer_id = ? AND status = 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Hesap sayımı hatası: " + e.getMessage());
            ErrorLogDAO.log(e, "Hesap sayımı");
        }
        return 0;
    }

    /**
     * Yeni hesap açar; account_no otomatik atanır.
     * KURAL: Müşterinin ilk (aktif) hesabı ZORUNLU olarak Vadesiz / TRY olmalıdır.
     * İlk hesap açıldıktan sonra diğer tür/dövizler (USD, Yatırım, ...) açılabilir.
     */
    public boolean open(int customerId, String typeName, String currency) {
        // İlk hesap kuralı (güvenlik ağı; UI da ayrıca net mesaj gösterir)
        if (countActiveAccounts(customerId) == 0
                && !("Vadesiz".equalsIgnoreCase(typeName) && "TRY".equalsIgnoreCase(currency))) {
            return false;
        }
        String insert = "INSERT INTO account (customer_id, account_type_id, currency) "
                      + "VALUES (?, (SELECT type_id FROM account_type WHERE type_name = ?), ?)";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);   // INSERT + account_no UPDATE tek transaction
            try (PreparedStatement ps = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, customerId);
                ps.setString(2, typeName);
                ps.setString(3, currency);
                if (ps.executeUpdate() == 0) { conn.rollback(); return false; }

                int newId;
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) { conn.rollback(); return false; }
                    newId = keys.getInt(1);
                }
                try (PreparedStatement up = conn.prepareStatement(
                        "UPDATE account SET account_no = ? WHERE account_id = ?")) {
                    up.setLong(1, 1000000000L + newId);   // 10 haneli hesap no
                    up.setInt(2, newId);
                    up.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("Hesap açılamadı: " + e.getMessage());
            ErrorLogDAO.log(e, "Hesap açma");
        }
        return false;
    }

    /**
     * Hesabı kapatır (soft delete: status = 0).
     * Güvenlik: bakiyesi sıfır olmayan veya aktif kredi/mevduata bağlı hesap kapatılamaz
     * (aksi halde para raporlardan/snapshot'lardan kaybolurdu).
     */
    public boolean close(int accountId) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE account SET status = 0 WHERE account_id = ? AND status = 1 AND balance = 0 "
                   + "AND NOT EXISTS (SELECT 1 FROM lending  l WHERE l.account_id = account.account_id AND l.status IN (0,1)) "
                   + "AND NOT EXISTS (SELECT 1 FROM borrowing b WHERE b.account_id = account.account_id AND b.status = 1)")) {
            ps.setInt(1, accountId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Hesap kapatılamadı: " + e.getMessage());
            ErrorLogDAO.log(e, "Hesap kapatma");
        }
        return false;
    }

    /** Bakiyeye ekleme/çıkarma (delta pozitif=yatır, negatif=çek). */
    public boolean changeBalance(int accountId, double delta) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE account SET balance = balance + ? WHERE account_id = ? AND status = 1")) {
            ps.setDouble(1, delta);
            ps.setInt(2, accountId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Bakiye güncellenemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Bakiye işlemi");
        }
        return false;
    }

    /** Tüm AÇIK hesaplar (personel görünümü) — banka hazine kasası hariç. */
    public List<Account> getAll() {
        return query(BASE_SELECT + "WHERE a.status = 1 AND c.customer_no <> " + BANK_CUSTOMER_NO
                + " ORDER BY a.account_id DESC", 0);
    }

    /** Bir müşterinin açık hesapları (müşteri görünümü). */
    public List<Account> getByCustomer(int customerId) {
        return query(BASE_SELECT + "WHERE a.status = 1 AND a.customer_id = ? ORDER BY a.account_id DESC",
                customerId);
    }

    /** Kriterlere göre arama (müşteri no / tür / döviz). */
    public List<Account> search(String customerNo, String type, String currency) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + "WHERE a.status = 1");
        List<Object> params = new ArrayList<>();
        if (customerNo != null && customerNo.matches("\\d+")) {
            sql.append(" AND c.customer_no = ?");
            params.add(Integer.parseInt(customerNo));
        }
        if (type != null && !type.isBlank()) {
            sql.append(" AND t.type_name = ?");
            params.add(type);
        }
        if (currency != null && !currency.isBlank()) {
            sql.append(" AND a.currency = ?");
            params.add(currency);
        }
        sql.append(" ORDER BY a.account_id DESC");

        List<Account> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("Hesap arama hatası: " + e.getMessage());
            ErrorLogDAO.log(e, "Hesap arama");
        }
        return list;
    }

    /** Hesap numarasına göre tek hesap. */
    public Account getByNo(long accountNo) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(BASE_SELECT + "WHERE a.account_no = ?")) {
            ps.setLong(1, accountNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            System.err.println("Hesap bulunamadı: " + e.getMessage());
            ErrorLogDAO.log(e, "Hesap getir (no)");
        }
        return null;
    }

    // ===================== EFT / HAVALE / FAST (transfer) =====================

    /** Transfer tipi: HAVALE (aynı banka), EFT (başka banka, masraflı), FAST (anlık, ücretsiz). */
    public enum TransferKind { HAVALE, EFT, FAST }

    /** FAST işlem başına üst limit (TL karşılığı) — üstü için EFT gerekir. */
    public static final double FAST_LIMIT_TRY  = 20_000;
    /** Bir müşterinin GÜNLÜK toplam transfer limiti (TL karşılığı). */
    public static final double DAILY_LIMIT_TRY = 500_000;

    /**
     * EFT kademeli masraf tarifesi (TL, internet/mobil bankacılık — gerçeğe yakın).
     * Üst sınır (cap) 44 TL. Havale ve FAST ücretsizdir.
     */
    private static double eftFeeTry(double amountTry) {
        if (amountTry <= 1_000)   return 6.05;
        if (amountTry <= 5_000)   return 9.90;
        if (amountTry <= 10_000)  return 15.40;
        if (amountTry <= 50_000)  return 22.00;
        if (amountTry <= 100_000) return 33.00;
        return 44.00;   // üst sınır
    }

    /** Transfer sonucu (masraf + hedefe geçen tutar). */
    public static class TransferResult {
        public final boolean ok;
        public final String error;        // ok=false ise dolu
        public final double fee;          // kesilen masraf (kaynak döviziyle)
        public final double credited;     // hedefe geçen (havale); EFT'te 0
        public final String srcCurrency;
        public final String tgtCurrency;

        private TransferResult(boolean ok, String error, double fee, double credited,
                               String srcCurrency, String tgtCurrency) {
            this.ok = ok; this.error = error; this.fee = fee; this.credited = credited;
            this.srcCurrency = srcCurrency; this.tgtCurrency = tgtCurrency;
        }
        static TransferResult fail(String e) { return new TransferResult(false, e, 0, 0, null, null); }
        static TransferResult success(double fee, double credited, String s, String t) {
            return new TransferResult(true, null, fee, credited, s, t);
        }
    }

    /**
     * İşlem masrafı — kaynak döviziyle döner.
     * Havale (aynı banka, internet) ücretsizdir; EFT tutara göre kademelidir.
     * Kademe TL karşılığına göre seçilir, sonra kaynak dövizine çevrilir.
     */
    public double calcFee(double amount, String currency, TransferKind kind) {
        if (amount <= 0 || kind != TransferKind.EFT) return 0.0;   // masraf yalnız EFT'te
        double buy = 1.0;
        if (!"TRY".equals(currency)) {
            CurrencyRate r = new RateDAO().getByCurrency(currency);
            if (r != null) buy = r.getBuyRate();
        }
        double feeTry = eftFeeTry(amount * buy);
        double feeSrc = "TRY".equals(currency) ? feeTry : feeTry / buy;
        return Math.round(feeSrc * 100.0) / 100.0;
    }

    /** Bir tutarın TL karşılığı (alış kuruyla). */
    private double toTry(double amount, String currency) {
        if ("TRY".equals(currency)) return amount;
        CurrencyRate r = new RateDAO().getByCurrency(currency);
        return r == null ? amount : amount * r.getBuyRate();
    }

    /** Müşterinin BUGÜN yaptığı transferlerin TL karşılığı toplamı (limit kontrolü). */
    private double todaysTransferredTry(int customerNo) {
        String sql = "SELECT COALESCE(SUM(al.amount * "
                   + "  CASE WHEN al.currency IS NULL OR al.currency = 'TRY' THEN 1 "
                   + "       ELSE COALESCE(cr.buy_rate, 1) END), 0) "
                   + "FROM activity_log al "
                   + "LEFT JOIN currency_rate cr ON cr.currency = al.currency AND cr.status = 1 "
                   + "WHERE al.customer_no = ? AND al.action_type IN ('TRANSFER','EFT','FAST') "
                   + "AND DATE(al.created_at) = CURDATE()";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException e) {
            System.err.println("Günlük limit sorgusu hatası: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Masraflı transfer. Masraf kaynaktan kesilir ve BANKA kasasına (kaynak
     * döviziyle) eklenir.
     *   - Havale : ana para hedef hesaba geçer (farklı dövizde arka plan FX). Ücretsiz.
     *   - EFT    : ana para sistemden çıkar (başka bankaya); banka masrafı (kademeli) alır.
     *   - FAST   : EFT gibi ama ücretsiz; işlem başına FAST_LIMIT_TRY sınırı vardır.
     * Ayrıca günlük toplam transfer limiti (DAILY_LIMIT_TRY) uygulanır.
     * Tümü tek transaction. Başarılıysa hazine snapshot'ı da kaydedilir.
     */
    public TransferResult transferWithFee(long fromNo, long toNo, double amount, TransferKind kind) {
        if (amount <= 0) return TransferResult.fail("Tutar sıfırdan büyük olmalı.");
        Account from = getByNo(fromNo);
        if (from == null) return TransferResult.fail("Gönderen hesap bulunamadı.");

        boolean external = (kind != TransferKind.HAVALE);
        double amountTry = toTry(amount, from.getCurrency());

        // FAST işlem başına limit
        if (kind == TransferKind.FAST && amountTry > FAST_LIMIT_TRY) {
            return TransferResult.fail("FAST limiti aşıldı (işlem başı en fazla "
                    + String.format("%,.0f TL", FAST_LIMIT_TRY) + " karşılığı). Büyük tutar için EFT kullanın.");
        }
        // Günlük toplam transfer limiti
        double todayTry = todaysTransferredTry(from.getCustomerNo());
        if (todayTry + amountTry > DAILY_LIMIT_TRY) {
            return TransferResult.fail("Günlük transfer limiti aşıldı (en fazla "
                    + String.format("%,.0f TL", DAILY_LIMIT_TRY) + " karşılığı). Bugün kullanılan: "
                    + String.format("%,.0f TL", todayTry) + ".");
        }

        double fee = calcFee(amount, from.getCurrency(), kind);
        double total = amount + fee;
        if (from.getBalance() < total) {
            return TransferResult.fail("Yetersiz bakiye. Gerekli: "
                    + String.format("%,.2f %s", total, from.getCurrency())
                    + "  (tutar + " + String.format("%,.2f", fee) + " masraf)");
        }

        // Hedef hesabı çöz: Havale'de zorunlu; EFT/FAST'te girilen numara sistemde
        // varsa o hesaba GERÇEKTEN teslim edilir (aynı banka), yoksa para dışarı çıkar.
        Account to = (toNo > 0) ? getByNo(toNo) : null;
        if (!external) {
            if (to == null) return TransferResult.fail("Alan hesap bulunamadı.");
            if (to.getStatus() != 1) return TransferResult.fail("Alan hesap kapalı.");
            if (to.getAccountId() == from.getAccountId())
                return TransferResult.fail("Gönderen ve alan hesap aynı olamaz.");
        }
        // Teslim yalnızca AÇIK bir iç hesaba yapılır (kapalıysa EFT/FAST'te para dışarı gider).
        boolean deliver = (to != null && to.getStatus() == 1 && to.getAccountId() != from.getAccountId());

        double credited = amount;
        double effRate = 1.0;
        if (deliver && !from.getCurrency().equals(to.getCurrency())) {   // arka plan FX
            RateDAO rd = new RateDAO();
            double tryVal = amount;
            if (!"TRY".equals(from.getCurrency())) {
                CurrencyRate r = rd.getByCurrency(from.getCurrency());
                if (r == null) return TransferResult.fail("Kur bulunamadı: " + from.getCurrency());
                tryVal = amount * r.getBuyRate();
            }
            credited = tryVal;
            if (!"TRY".equals(to.getCurrency())) {
                CurrencyRate r = rd.getByCurrency(to.getCurrency());
                if (r == null) return TransferResult.fail("Kur bulunamadı: " + to.getCurrency());
                credited = tryVal / r.getSellRate();
            }
            effRate = credited / amount;
        }

        String debit  = "UPDATE account SET balance = balance - ? WHERE account_id = ? AND status = 1 AND balance >= ?";
        String credit = "UPDATE account SET balance = balance + ? WHERE account_id = ? AND status = 1";
        String selBank = "SELECT account_id, balance FROM account "
                       + "WHERE customer_id = ? AND currency = ? AND status = 1 ORDER BY account_id LIMIT 1";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int bankId = bankCustomerId(conn);
                if (bankId < 0) { conn.rollback(); return TransferResult.fail("Banka kasası tanımlı değil."); }
                Acc bankSrc = acc(conn, selBank, bankId, from.getCurrency());   // masraf + FX'te kaynak principal
                if (bankSrc == null) { conn.rollback(); return TransferResult.fail("Banka " + from.getCurrency() + " kasası yok."); }

                // Farklı dövizde teslim: banka FX karşı tarafı olur (kaynak dövizi girer, hedef dövizi çıkar)
                boolean fx = deliver && !from.getCurrency().equals(to.getCurrency());
                Acc bankTgt = null;
                if (fx) {
                    bankTgt = acc(conn, selBank, bankId, to.getCurrency());
                    if (bankTgt == null) { conn.rollback(); return TransferResult.fail("Banka " + to.getCurrency() + " kasası yok."); }
                    if (bankTgt.balance < credited) {
                        conn.rollback();
                        return TransferResult.fail("Banka " + to.getCurrency() + " kasası yetersiz (FX için).");
                    }
                }

                // 1) Gönderenden tutar + masraf düş
                try (PreparedStatement ps = conn.prepareStatement(debit)) {
                    ps.setDouble(1, total);
                    ps.setInt(2, from.getAccountId());
                    ps.setDouble(3, total);
                    if (ps.executeUpdate() == 0) { conn.rollback(); return TransferResult.fail("Yetersiz bakiye."); }
                }
                // 2) Masrafı bankaya ekle (kaynak dövizi kasası)
                move(conn, credit, fee, bankSrc.id);
                // 3) Hedef sistemde ise ana parayı ona ekle
                if (deliver) {
                    if (fx) {
                        // Kaynak dövizi bankaya girer, hedef dövizi bankadan çıkar (guard'lı)
                        creditAcc(conn, bankSrc.id, amount);           // ör. TL banka TL kasasına girer
                        if (!debitAcc(conn, bankTgt.id, credited)) {   // ör. USD banka USD kasasından çıkar
                            conn.rollback();
                            return TransferResult.fail("Banka " + to.getCurrency() + " kasası yetersiz (FX için).");
                        }
                    }
                    move(conn, credit, credited, to.getAccountId());   // müşterinin hedef hesabına
                    if (fx) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO spot_transaction "
                              + "(customer_id, buy_currency, sell_currency, buy_amount, sell_amount, rate) "
                              + "VALUES (?, ?, ?, ?, ?, ?)")) {
                            ps.setInt(1, from.getCustomerId());
                            ps.setString(2, from.getCurrency());
                            ps.setString(3, to.getCurrency());
                            ps.setDouble(4, amount);
                            ps.setDouble(5, credited);
                            ps.setDouble(6, effRate);
                            ps.executeUpdate();
                        }
                    }
                    // Alan başka bir müşteriyse ona "para geldi" bildirimi yaz
                    if (from.getCustomerId() != to.getCustomerId()) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO notification (customer_no, title, detail) VALUES (?, ?, ?)")) {
                            ps.setInt(1, to.getCustomerNo());
                            ps.setString(2, "Hesabınıza "
                                    + String.format("%,.2f %s", credited, to.getCurrency()) + " geldi");
                            ps.setString(3, "Gönderen: " + from.getAccountNo() + " (" + from.getCustomerName() + ")"
                                    + " | Alan hesabınız: " + to.getAccountNo()
                                    + " | Tutar: " + String.format("%,.2f %s", credited, to.getCurrency()));
                            ps.executeUpdate();
                        }
                    }
                }
                // Hedef sistemde değilse (deliver=false): ana para dışarı çıkar, kimseye eklenmez.

                conn.commit();
                TreasurySnapshotDAO.record();
                CustomerSnapshotDAO.record(from.getCustomerId());          // gönderen trendi
                if (deliver && from.getCustomerId() != to.getCustomerId()) {
                    CustomerSnapshotDAO.record(to.getCustomerId());        // alan trendi
                }
                return TransferResult.success(fee, deliver ? credited : 0,
                        from.getCurrency(), deliver ? to.getCurrency() : from.getCurrency());
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("Masraflı transfer hatası: " + e.getMessage());
            ErrorLogDAO.log(e, "EFT/Havale");
            return TransferResult.fail("İşlem sırasında hata: " + e.getMessage());
        }
    }

    /** Banka (hazine) kasa hesapları — admin görünümü. */
    public List<Account> getBankAccounts() {
        return query(BASE_SELECT + "WHERE a.status = 1 AND c.customer_no = ? ORDER BY a.currency",
                BANK_CUSTOMER_NO);
    }

    /**
     * Müşteri döviz AL/SAT — karşı taraf BANKA (hazine) hesabıdır.
     * BUY : müşteri döviz alır → TL öder (müşteri TL → banka TL), döviz alır (banka döviz → müşteri döviz)
     * SELL: müşteri döviz satar → döviz verir (müşteri döviz → banka döviz), TL alır (banka TL → müşteri TL)
     * Dört bakiye hareketi + spot_transaction kaydı TEK transaction'da yapılır.
     *
     * @return null = başarılı; aksi halde kullanıcıya gösterilecek hata mesajı.
     */
    public String spotTrade(int customerId, String currency, double amount, double rate, boolean isBuy) {
        if (amount <= 0 || rate <= 0) return "Geçersiz miktar veya kur.";
        double tryAmount = amount * rate;

        String sel = "SELECT account_id, balance FROM account "
                   + "WHERE customer_id = ? AND currency = ? AND status = 1 "
                   + "ORDER BY account_id LIMIT 1";
        String upd = "UPDATE account SET balance = balance + ? WHERE account_id = ?";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int bankId = bankCustomerId(conn);
                if (bankId < 0) { conn.rollback(); return "Banka hazine hesabı tanımlı değil."; }

                Acc custTry = acc(conn, sel, customerId, "TRY");
                Acc custFx  = acc(conn, sel, customerId, currency);
                Acc bankTry = acc(conn, sel, bankId, "TRY");
                Acc bankFx  = acc(conn, sel, bankId, currency);

                if (custTry == null) { conn.rollback(); return "TL (TRY) hesabınız yok. Önce bir TL hesabı açılmalı."; }
                if (custFx == null)  { conn.rollback(); return currency + " hesabınız yok. Önce bir " + currency + " hesabı açılmalı."; }
                if (bankTry == null || bankFx == null) { conn.rollback(); return "Banka hazine hesabı eksik."; }

                // Düşümler ATOMİK + guard'lı (AND balance >= ?): eşzamanlılıkta negatif bakiye olmaz.
                if (isBuy) {
                    if (!debitAcc(conn, custTry.id, tryAmount)) {
                        conn.rollback();
                        return "TL bakiyeniz yetersiz. Gerekli: " + String.format("%,.2f TRY", tryAmount)
                                + " | Mevcut: " + String.format("%,.2f TRY", custTry.balance);
                    }
                    creditAcc(conn, bankTry.id, tryAmount);     // banka TL alır
                    if (!debitAcc(conn, bankFx.id, amount)) { conn.rollback(); return "Banka " + currency + " kasası yetersiz."; }
                    creditAcc(conn, custFx.id, amount);         // müşteri döviz alır
                } else {
                    if (!debitAcc(conn, custFx.id, amount)) {
                        conn.rollback();
                        return currency + " bakiyeniz yetersiz. Gerekli: " + String.format("%,.2f %s", amount, currency)
                                + " | Mevcut: " + String.format("%,.2f %s", custFx.balance, currency);
                    }
                    creditAcc(conn, bankFx.id, amount);         // banka döviz alır
                    if (!debitAcc(conn, bankTry.id, tryAmount)) { conn.rollback(); return "Banka TL kasası yetersiz."; }
                    creditAcc(conn, custTry.id, tryAmount);     // müşteri TL alır
                }

                insertSpot(conn, customerId, isBuy, currency, amount, tryAmount, rate);
                notifyCustomerIfByStaff(conn, customerId, isBuy, currency, amount, tryAmount, rate);
                conn.commit();
                TreasurySnapshotDAO.record();          // banka trendine nokta
                CustomerSnapshotDAO.record(customerId); // müşteri trendine nokta
                return null;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("Spot işlem hatası: " + e.getMessage());
            ErrorLogDAO.log(e, "Spot al/sat");
            return "İşlem sırasında hata oluştu: " + e.getMessage();
        }
    }

    /**
     * Spot işlemi MÜŞTERİNİN KENDİSİ değil de personel (admin/trader) yaptıysa,
     * müşteriye giriş yapınca göreceği bir bildirim yazar.
     */
    private void notifyCustomerIfByStaff(Connection conn, int customerId, boolean isBuy,
                                         String currency, double amount, double tryAmount, double rate)
            throws SQLException {
        int custNo = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT customer_no FROM customer WHERE customer_id = ?")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) custNo = rs.getInt(1);
            }
        }
        if (custNo <= 0) return;

        String actor = com.gtech.treasury.util.Session.getCurrentUsername();
        boolean byStaff = actor == null || !actor.equals(String.valueOf(custNo));
        if (!byStaff) return;   // müşteri kendisi yaptıysa (sonucu zaten gördü) bildirim yok

        String title = isBuy
                ? "Döviz alışı: " + String.format("%,.2f %s", amount, currency) + " hesabınıza eklendi"
                : "Döviz satışı: " + String.format("%,.2f %s", amount, currency) + " hesabınızdan satıldı";
        String detail = (isBuy
                ? "Alınan: " + String.format("%,.2f %s", amount, currency)
                    + " | Ödenen: " + String.format("%,.2f TRY", tryAmount)
                : "Satılan: " + String.format("%,.2f %s", amount, currency)
                    + " | Alınan: " + String.format("%,.2f TRY", tryAmount))
                + " | Kur: " + rate
                + " | İşlemi yapan: " + (actor == null ? "-" : actor);

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO notification (customer_no, title, detail) VALUES (?, ?, ?)")) {
            ps.setInt(1, custNo);
            ps.setString(2, title);
            ps.setString(3, detail);
            ps.executeUpdate();
        }
    }

    // ---- spotTrade yardımcıları ----
    private static class Acc {
        final int id; final double balance;
        Acc(int id, double balance) { this.id = id; this.balance = balance; }
    }

    private Acc acc(Connection conn, String sql, int customerId, String currency) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setString(2, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new Acc(rs.getInt("account_id"), rs.getDouble("balance"));
            }
        }
        return null;
    }

    /** Guard'lı atomik düşüm: yeterli bakiye ve açık hesap yoksa satır güncellenmez (false döner). */
    private boolean debitAcc(Connection conn, int accId, double amt) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE account SET balance = balance - ? WHERE account_id = ? AND status = 1 AND balance >= ?")) {
            ps.setDouble(1, amt); ps.setInt(2, accId); ps.setDouble(3, amt);
            return ps.executeUpdate() > 0;
        }
    }

    /** Ekleme (yalnız açık hesaba). */
    private void creditAcc(Connection conn, int accId, double amt) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE account SET balance = balance + ? WHERE account_id = ? AND status = 1")) {
            ps.setDouble(1, amt); ps.setInt(2, accId); ps.executeUpdate();
        }
    }

    private void move(Connection conn, String sql, double delta, int accountId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, delta);
            ps.setInt(2, accountId);
            ps.executeUpdate();
        }
    }

    private int bankCustomerId(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT customer_id FROM customer WHERE customer_no = ?")) {
            ps.setInt(1, BANK_CUSTOMER_NO);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return -1;
    }

    /** spot_transaction kaydı (banka bakışı: aldığı = buy, verdiği = sell). */
    private void insertSpot(Connection conn, int customerId, boolean isBuy,
                            String currency, double amount, double tryAmount, double rate) throws SQLException {
        String buyCur, sellCur; double buyAmt, sellAmt;
        if (isBuy) {                 // müşteri döviz alır → banka TL alır, döviz verir
            buyCur = "TRY";     buyAmt = tryAmount; sellCur = currency; sellAmt = amount;
        } else {                     // müşteri döviz satar → banka döviz alır, TL verir
            buyCur = currency;  buyAmt = amount;    sellCur = "TRY";    sellAmt = tryAmount;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO spot_transaction "
              + "(customer_id, buy_currency, sell_currency, buy_amount, sell_amount, rate) "
              + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, customerId);
            ps.setString(2, buyCur);
            ps.setString(3, sellCur);
            ps.setDouble(4, buyAmt);
            ps.setDouble(5, sellAmt);
            ps.setDouble(6, rate);
            ps.executeUpdate();
        }
    }

    /** Hesap türlerini getirir (Vadesiz/Mevduat/Yatırım). */
    public List<String> getAccountTypes() {
        List<String> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT type_name FROM account_type ORDER BY type_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(rs.getString("type_name"));
        } catch (SQLException e) {
            System.err.println("Hesap türleri getirilemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Hesap türleri");
        }
        return list;
    }

    private List<Account> query(String sql, int customerId) {
        List<Account> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (customerId > 0) ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("Hesaplar getirilemedi: " + e.getMessage());
            ErrorLogDAO.log(e, "Hesap listeleme");
        }
        return list;
    }

    private Account mapRow(ResultSet rs) throws SQLException {
        return new Account(
                rs.getInt("account_id"),
                rs.getLong("account_no"),
                rs.getInt("customer_id"),
                rs.getInt("customer_no"),
                rs.getString("customer_name"),
                rs.getString("account_type"),
                rs.getString("currency"),
                rs.getDouble("balance"),
                rs.getInt("status"),
                String.valueOf(rs.getTimestamp("opened_at")));
    }
}
