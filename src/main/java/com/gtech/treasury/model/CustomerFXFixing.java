package com.gtech.treasury.model;

/** Müşteriye özel FX kur fiksasyonu deal'i (customer_fx_fixing). */
public class CustomerFXFixing {

    public static final String T_SATIS = "BANKA_SATIS";   // banka müşteriye FX satar (sell_rate)
    public static final String T_ALIS  = "BANKA_ALIS";    // banka müşteriden FX alır (buy_rate)
    public static final String T_PARITE = "PARITE";       // FX/FX (ileride)

    private int id;
    private String referenceNo;
    private int customerNo;
    private int customerId;
    private String customerName;         // gösterim
    private String transactionType = T_SATIS;
    private String rateType = "DOVIZ";   // DOVIZ / EFEKTIF
    private String currency;             // işlem dövizi
    private String pair;                 // EUR/TRY
    private double amount;
    private double marketRate;
    private double treasuryCost;
    private double spread;
    private double customerBuyRate;
    private double customerSellRate;
    private double pnl;
    private String pnlCurrency = "TRY";
    private String description;
    private String status = "FIXED";     // FIXED / CANCELLED
    private double cancellationRate;
    private double cancellationPnl;
    private String createdBy;
    private String createdAt;
    private String cancelledAt;
    private String executedAt;
    private String executedBy;

    public int getId() { return id; }
    public void setId(int v) { id = v; }
    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String v) { referenceNo = v; }
    public int getCustomerNo() { return customerNo; }
    public void setCustomerNo(int v) { customerNo = v; }
    public int getCustomerId() { return customerId; }
    public void setCustomerId(int v) { customerId = v; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String v) { customerName = v; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String v) { transactionType = v; }
    public String getRateType() { return rateType; }
    public void setRateType(String v) { rateType = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { currency = v; }
    public String getPair() { return pair; }
    public void setPair(String v) { pair = v; }
    public double getAmount() { return amount; }
    public void setAmount(double v) { amount = v; }
    public double getMarketRate() { return marketRate; }
    public void setMarketRate(double v) { marketRate = v; }
    public double getTreasuryCost() { return treasuryCost; }
    public void setTreasuryCost(double v) { treasuryCost = v; }
    public double getSpread() { return spread; }
    public void setSpread(double v) { spread = v; }
    public double getCustomerBuyRate() { return customerBuyRate; }
    public void setCustomerBuyRate(double v) { customerBuyRate = v; }
    public double getCustomerSellRate() { return customerSellRate; }
    public void setCustomerSellRate(double v) { customerSellRate = v; }
    public double getPnl() { return pnl; }
    public void setPnl(double v) { pnl = v; }
    public String getPnlCurrency() { return pnlCurrency; }
    public void setPnlCurrency(String v) { pnlCurrency = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public double getCancellationRate() { return cancellationRate; }
    public void setCancellationRate(double v) { cancellationRate = v; }
    public double getCancellationPnl() { return cancellationPnl; }
    public void setCancellationPnl(double v) { cancellationPnl = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { createdBy = v; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String v) { createdAt = v; }
    public String getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(String v) { cancelledAt = v; }
    public String getExecutedAt() { return executedAt; }
    public void setExecutedAt(String v) { executedAt = v; }
    public String getExecutedBy() { return executedBy; }
    public void setExecutedBy(String v) { executedBy = v; }

    public boolean isBankSell() { return T_SATIS.equals(transactionType); }
    public String getTypeLabel() {
        switch (transactionType == null ? "" : transactionType) {
            case T_SATIS:  return "Banka Satış";
            case T_ALIS:   return "Banka Alış";
            case T_PARITE: return "Parite";
            default:       return transactionType;
        }
    }
    public String getStatusText() {
        if ("CANCELLED".equals(status)) return "İptal";
        if ("EXECUTED".equals(status)) return "İşlendi";
        return "Fixed";
    }
}
