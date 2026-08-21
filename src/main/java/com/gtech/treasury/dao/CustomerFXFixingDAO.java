package com.gtech.treasury.dao;

import com.gtech.treasury.model.CustomerFXFixing;
import com.gtech.treasury.model.CustomerFixedRate;
import com.gtech.treasury.util.DBConnection;
import com.gtech.treasury.util.FxPricingService;
import com.gtech.treasury.util.ReferenceGenerator;
import com.gtech.treasury.util.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * customer_fx_fixing erişimi. Fix kaydı + iptal + audit; FIXED olunca ilgili yön
 * mevcut customer_fixed_rate'e köprülenir (SpotTradePanel bunu okur).
 */
public class CustomerFXFixingDAO {

    private final CustomerFixedRateDAO fixedDAO = new CustomerFixedRateDAO();

    private static final String COLS =
            "f.id, f.reference_no, f.customer_no, f.customer_id, "
          + "TRIM(CONCAT(IFNULL(c.customer_name,''),' ',IFNULL(c.surname,''))) AS cp_name, "
          + "f.transaction_type, f.rate_type, f.currency, f.pair, f.amount, f.market_rate, f.treasury_cost, "
          + "f.spread, f.customer_buy_rate, f.customer_sell_rate, f.pnl, f.pnl_currency, f.description, "
          + "f.status, f.cancellation_rate, f.cancellation_pnl, f.created_by, f.created_at, f.cancelled_at, "
          + "f.executed_at, f.executed_by "
          + "FROM customer_fx_fixing f LEFT JOIN customer c ON c.customer_no = f.customer_no ";

    /** Fixing'i kaydeder ve ilgili yönü standing fix'e (customer_fixed_rate) köprüler. */
    public String create(CustomerFXFixing d) {
        if (d.getCustomerNo() <= 0) return "Müşteri seçilmedi.";
        if (d.getMarketRate() <= 0) return "Geçerli bir piyasa kuru yok.";
        double cRate = d.isBankSell() ? d.getCustomerSellRate() : d.getCustomerBuyRate();
        if (cRate <= 0) return "Müşteri kuru geçersiz (0/negatif). Spread'i kontrol edin (küçük bir değer olmalı).";

        String ref = ReferenceGenerator.next("FX", "customer_fx_fixing", "reference_no");
        d.setReferenceNo(ref);
        d.setStatus("FIXED");
        d.setCreatedBy(Session.getCurrentUsername());

        String ins = "INSERT INTO customer_fx_fixing (reference_no, customer_no, customer_id, transaction_type, "
                + "rate_type, currency, pair, amount, market_rate, treasury_cost, spread, customer_buy_rate, "
                + "customer_sell_rate, pnl, pnl_currency, description, status, created_by) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(ins, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setString(i++, ref);
            ps.setInt(i++, d.getCustomerNo());
            if (d.getCustomerId() > 0) ps.setInt(i++, d.getCustomerId()); else ps.setNull(i++, java.sql.Types.INTEGER);
            ps.setString(i++, d.getTransactionType());
            ps.setString(i++, d.getRateType());
            ps.setString(i++, d.getCurrency());
            ps.setString(i++, d.getPair());
            ps.setDouble(i++, d.getAmount());
            ps.setDouble(i++, d.getMarketRate());
            ps.setDouble(i++, d.getTreasuryCost());
            ps.setDouble(i++, d.getSpread());
            if (d.getCustomerBuyRate() > 0) ps.setDouble(i++, d.getCustomerBuyRate()); else ps.setNull(i++, java.sql.Types.DECIMAL);
            if (d.getCustomerSellRate() > 0) ps.setDouble(i++, d.getCustomerSellRate()); else ps.setNull(i++, java.sql.Types.DECIMAL);
            ps.setDouble(i++, d.getPnl());
            ps.setString(i++, d.getPnlCurrency());
            ps.setString(i++, d.getDescription());
            ps.setString(i++, d.getStatus());
            ps.setString(i++, d.getCreatedBy());
            ps.executeUpdate();
            try (ResultSet gk = ps.getGeneratedKeys()) { if (gk.next()) d.setId(gk.getInt(1)); }
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "FX fixing create");
            return "Kayıt sırasında hata: " + e.getMessage();
        }

        // Köprü: ilgili yönü standing fix'e yaz (diğer yönü koru)
        bridgeToStandingFix(d);

        double custRate = d.isBankSell() ? d.getCustomerSellRate() : d.getCustomerBuyRate();
        ActivityLogDAO.log("FX_FIX_CREATE", d.getCustomerNo(), d.getAmount(), d.getCurrency(),
                "FX kur fiksasyonu: " + d.getPair() + " " + d.getTypeLabel(),
                "Ref: " + ref + " | Market: " + d.getMarketRate() + " | Spread: " + d.getSpread()
                        + " | Müşteri Kuru: " + custRate + " | P&L: " + d.getPnl() + " " + d.getPnlCurrency());
        // Müşteriye bildirim: referans + koşullar (Gelen Kutusu'na düşer)
        new NotificationDAO().add(d.getCustomerNo(),
                "Size özel kur fixlendi — Ref: " + ref,
                d.getTypeLabel() + "  " + d.getPair()
                        + "\nKur: " + String.format("%,.6f", custRate)
                        + "  |  Tutar: " + String.format("%,.2f %s", d.getAmount(), d.getCurrency())
                        + "\nBu referansı Spot FX ekranındaki 'Fix Referansı ile İşle' ile kullanabilirsiniz."
                        + "\nReferans: " + ref);
        return null;
    }

    private void bridgeToStandingFix(CustomerFXFixing d) {
        CustomerFixedRate ex = fixedDAO.get(d.getCustomerNo(), d.getCurrency());
        double buy = ex != null ? ex.getBuyRate() : 0;
        double sell = ex != null ? ex.getSellRate() : 0;
        if (d.isBankSell()) sell = d.getCustomerSellRate();
        else                buy = d.getCustomerBuyRate();
        fixedDAO.upsert(d.getCustomerNo(), d.getCurrency(), buy, sell);
    }

    /** Fixing'i iptal eder: status CANCELLED + iptal kuru/P&L + standing fix'i pasifleştir. */
    public String cancel(int id, double cancelRate) {
        CustomerFXFixing d = getById(id);
        if (d == null) return "İşlem bulunamadı.";
        if (!"FIXED".equals(d.getStatus())) return "Sadece FIXED işlem iptal edilebilir.";
        double fixRate = d.isBankSell() ? d.getCustomerSellRate() : d.getCustomerBuyRate();
        double cpnl = FxPricingService.cancellationPnl(fixRate, cancelRate, d.isBankSell(), d.getAmount());
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE customer_fx_fixing SET status='CANCELLED', cancellation_rate=?, cancellation_pnl=?, "
                   + "cancelled_at=NOW() WHERE id=? AND status='FIXED'")) {
            ps.setDouble(1, cancelRate); ps.setDouble(2, cpnl); ps.setInt(3, id);
            if (ps.executeUpdate() == 0) return "İşlem güncellenemedi.";
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "FX fixing cancel");
            return "İptal sırasında hata: " + e.getMessage();
        }
        fixedDAO.deactivate(d.getCustomerNo(), d.getCurrency());   // standing fix'i kaldır
        ActivityLogDAO.log("FX_FIX_CANCEL", d.getCustomerNo(), d.getAmount(), d.getCurrency(),
                "FX fiksasyon iptali: " + d.getReferenceNo(),
                "İptal kuru: " + cancelRate + " | İptal P&L: " + cpnl + " " + d.getPnlCurrency());
        new NotificationDAO().add(d.getCustomerNo(),
                "Fix iptal edildi — Ref: " + d.getReferenceNo(),
                d.getPair() + " " + d.getTypeLabel() + " fikslemesi iptal edildi; bu referans artık kullanılamaz.");
        return null;
    }

    public CustomerFXFixing getById(int id) {
        List<CustomerFXFixing> l = query(COLS + "WHERE f.id = ?", id);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Referans no ile fixing getir (referansla işlem için). */
    public CustomerFXFixing getByReference(String ref) {
        List<CustomerFXFixing> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT " + COLS + "WHERE f.reference_no = ?")) {
            ps.setString(1, ref == null ? "" : ref.trim());
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        } catch (SQLException e) { ErrorLogDAO.log(e, "FX fixing getByReference"); }
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * FIXED bir fixing'i referansıyla gerçek spot alım/satıma dönüştürür.
     *   Banka Satış → müşteri döviz alır (spot isBuy=true), kur = customerSellRate
     *   Banka Alış  → müşteri döviz satar (spot isBuy=false), kur = customerBuyRate
     */
    public String execute(int id) {
        CustomerFXFixing d = getById(id);
        if (d == null) return "İşlem bulunamadı.";
        if (!"FIXED".equals(d.getStatus())) return "Yalnız FIXED referans işleme alınabilir (durum: " + d.getStatusText() + ").";
        if (d.getCustomerId() <= 0) return "Müşteri bilgisi eksik (eski kayıt); işleme alınamaz.";
        boolean bankSell = d.isBankSell();
        boolean isBuy = bankSell;   // Banka Satış = müşteri alır
        double rate = bankSell ? d.getCustomerSellRate() : d.getCustomerBuyRate();
        double amount = d.getAmount();
        if (rate <= 0 || amount <= 0) return "Geçersiz kur/tutar.";

        String res = new AccountDAO().spotTrade(d.getCustomerId(), d.getCurrency(), amount, rate, isBuy);
        if (res != null) return res;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE customer_fx_fixing SET status='EXECUTED', executed_at=NOW(), executed_by=? "
                   + "WHERE id=? AND status='FIXED'")) {
            ps.setString(1, Session.getCurrentUsername());
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            ErrorLogDAO.log(e, "FX fixing execute-mark");
            // Para hareketi oldu; işaretleme hatası kritik değil ama loglanır.
        }
        ActivityLogDAO.log("FX_FIX_EXEC", d.getCustomerNo(), amount, d.getCurrency(),
                "FX fiksasyon referansla işlendi: " + d.getReferenceNo(),
                "Yön: " + d.getTypeLabel() + " | Kur: " + rate + " | Tutar: " + amount + " " + d.getCurrency());
        return null;
    }
    public List<CustomerFXFixing> getAll() { return query(COLS + "ORDER BY f.id DESC", 0); }
    public List<CustomerFXFixing> getByCustomer(int customerNo) {
        return query(COLS + "WHERE f.customer_no = ? ORDER BY f.id DESC", customerNo);
    }

    private List<CustomerFXFixing> query(String sql, int param) {
        List<CustomerFXFixing> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT " + sql)) {
            if (param > 0) ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        } catch (SQLException e) { ErrorLogDAO.log(e, "FX fixing listeleme"); }
        return list;
    }

    private CustomerFXFixing map(ResultSet rs) throws SQLException {
        CustomerFXFixing d = new CustomerFXFixing();
        d.setId(rs.getInt("id"));
        d.setReferenceNo(rs.getString("reference_no"));
        d.setCustomerNo(rs.getInt("customer_no"));
        d.setCustomerId(rs.getInt("customer_id"));
        d.setCustomerName(rs.getString("cp_name") == null ? "" : rs.getString("cp_name").trim());
        d.setTransactionType(rs.getString("transaction_type"));
        d.setRateType(rs.getString("rate_type"));
        d.setCurrency(rs.getString("currency"));
        d.setPair(rs.getString("pair"));
        d.setAmount(rs.getDouble("amount"));
        d.setMarketRate(rs.getDouble("market_rate"));
        d.setTreasuryCost(rs.getDouble("treasury_cost"));
        d.setSpread(rs.getDouble("spread"));
        d.setCustomerBuyRate(rs.getDouble("customer_buy_rate"));
        d.setCustomerSellRate(rs.getDouble("customer_sell_rate"));
        d.setPnl(rs.getDouble("pnl"));
        d.setPnlCurrency(rs.getString("pnl_currency"));
        d.setDescription(rs.getString("description"));
        d.setStatus(rs.getString("status"));
        d.setCancellationRate(rs.getDouble("cancellation_rate"));
        d.setCancellationPnl(rs.getDouble("cancellation_pnl"));
        d.setCreatedBy(rs.getString("created_by"));
        d.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
        d.setCancelledAt(String.valueOf(rs.getTimestamp("cancelled_at")));
        d.setExecutedAt(String.valueOf(rs.getTimestamp("executed_at")));
        d.setExecutedBy(rs.getString("executed_by"));
        return d;
    }
}
