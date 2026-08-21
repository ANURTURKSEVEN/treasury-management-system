package com.gtech.treasury.model;

/**
 * Para Piyasası (Money Market) borçlanma deal'i — mm_borrowing tablosu.
 * Banka bir karşı kurumdan belirli bir dövizde, valör–vade arası fon alır (borrowing).
 * Çok alanlı bir "deal capture" nesnesi olduğu için değiştirilebilir (setter'lı) tutuldu.
 */
public class MoneyMarketBorrowing implements com.gtech.treasury.util.SwiftDealView {

    private int id;
    private String referenceNo;
    private int counterpartyId;         // customer_id (TUZEL); 0 = yok
    private int counterpartyNo;         // gösterim (join)
    private String counterpartyName;    // gösterim (join)
    private String marketType = "MONEY_MARKET";
    private String purpose;             // ALM / LIQUIDITY / FUNDING / OTHER
    private String dealer;
    private String broker;
    private String comment;
    private String bcs;                 // Taraf Tipi (placeholder)
    private String currency;
    private double principal;
    private double interestRate;        // yıllık %
    private String dayCount = "A/360";
    private String dealDate;            // yyyy-MM-dd
    private String valueDate;
    private String maturityDate;
    private double interestAmount;
    private double taxAmount;
    private double repaymentAmount;
    private boolean stopaj;
    private int receivingAccountId;     // valörde alacaklanan banka kasa hesabı
    private int repaymentAccountId;     // vadede ödeyen banka kasa hesabı
    private String correspondent1Bic;
    private String correspondent2Bic;
    private boolean createSwift;
    private boolean createMt320;
    private boolean createMt202;
    private String status = "ACTIVE";   // ACTIVE / MATURED / CANCELLED
    private String createdBy;
    private String createdAt;
    private String settledAt;
    private String maturedAt;
    private java.util.List<MoneyMarketCharge> charges = new java.util.ArrayList<>();  // create'te kaydedilir

    public int getId() { return id; }
    public void setId(int v) { id = v; }
    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String v) { referenceNo = v; }
    public int getCounterpartyId() { return counterpartyId; }
    public void setCounterpartyId(int v) { counterpartyId = v; }
    public int getCounterpartyNo() { return counterpartyNo; }
    public void setCounterpartyNo(int v) { counterpartyNo = v; }
    public String getCounterpartyName() { return counterpartyName; }
    public void setCounterpartyName(String v) { counterpartyName = v; }
    public String getMarketType() { return marketType; }
    public void setMarketType(String v) { marketType = v; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String v) { purpose = v; }
    public String getDealer() { return dealer; }
    public void setDealer(String v) { dealer = v; }
    public String getBroker() { return broker; }
    public void setBroker(String v) { broker = v; }
    public String getComment() { return comment; }
    public void setComment(String v) { comment = v; }
    public String getBcs() { return bcs; }
    public void setBcs(String v) { bcs = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { currency = v; }
    public double getPrincipal() { return principal; }
    public void setPrincipal(double v) { principal = v; }
    public double getInterestRate() { return interestRate; }
    public void setInterestRate(double v) { interestRate = v; }
    public String getDayCount() { return dayCount; }
    public void setDayCount(String v) { dayCount = v; }
    public String getDealDate() { return dealDate; }
    public void setDealDate(String v) { dealDate = v; }
    public String getValueDate() { return valueDate; }
    public void setValueDate(String v) { valueDate = v; }
    public String getMaturityDate() { return maturityDate; }
    public void setMaturityDate(String v) { maturityDate = v; }
    public double getInterestAmount() { return interestAmount; }
    public void setInterestAmount(double v) { interestAmount = v; }
    public double getTaxAmount() { return taxAmount; }
    public void setTaxAmount(double v) { taxAmount = v; }
    public double getRepaymentAmount() { return repaymentAmount; }
    public void setRepaymentAmount(double v) { repaymentAmount = v; }
    public boolean isStopaj() { return stopaj; }
    public void setStopaj(boolean v) { stopaj = v; }
    public int getReceivingAccountId() { return receivingAccountId; }
    public void setReceivingAccountId(int v) { receivingAccountId = v; }
    public int getRepaymentAccountId() { return repaymentAccountId; }
    public void setRepaymentAccountId(int v) { repaymentAccountId = v; }
    public String getCorrespondent1Bic() { return correspondent1Bic; }
    public void setCorrespondent1Bic(String v) { correspondent1Bic = v; }
    public String getCorrespondent2Bic() { return correspondent2Bic; }
    public void setCorrespondent2Bic(String v) { correspondent2Bic = v; }
    public boolean isCreateSwift() { return createSwift; }
    public void setCreateSwift(boolean v) { createSwift = v; }
    public boolean isCreateMt320() { return createMt320; }
    public void setCreateMt320(boolean v) { createMt320 = v; }
    public boolean isCreateMt202() { return createMt202; }
    public void setCreateMt202(boolean v) { createMt202 = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { createdBy = v; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String v) { createdAt = v; }
    public String getSettledAt() { return settledAt; }
    public void setSettledAt(String v) { settledAt = v; }
    public String getMaturedAt() { return maturedAt; }
    public void setMaturedAt(String v) { maturedAt = v; }

    public java.util.List<MoneyMarketCharge> getCharges() { return charges; }
    public void setCharges(java.util.List<MoneyMarketCharge> v) { charges = (v == null ? new java.util.ArrayList<>() : v); }

    @Override public String getSwiftDirection() { return "B"; }   // banka borç alan

    public String getStatusText() {
        switch (status == null ? "" : status) {
            case "ACTIVE":    return "Aktif (Fon Alındı)";
            case "MATURED":   return "Vade Sonu Kapandı";
            case "CANCELLED": return "İptal";
            default:          return status;
        }
    }
}
