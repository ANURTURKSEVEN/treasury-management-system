package com.gtech.treasury.model;

/**
 * Para Piyasası (Money Market) PLASMAN / borç verme deal'i — mm_lending tablosu.
 * Banka bir karşı kuruma belirli bir dövizde, valör–vade arası fon verir (lending/placement).
 * MoneyMarketBorrowing'in aynasıdır; nakit yönü terstir (kasadan çıkış, vadede giriş).
 */
public class MoneyMarketLending implements com.gtech.treasury.util.SwiftDealView {

    private int id;
    private String referenceNo;
    private int counterpartyId;
    private int counterpartyNo;
    private String counterpartyName;
    private String marketType = "MONEY_MARKET";
    private String purpose;
    private String dealer;
    private String broker;
    private String comment;
    private String bcs;                 // Taraf Tipi (placeholder)
    private String currency;
    private double principal;
    private double interestRate;
    private String dayCount = "A/365";
    private String dealDate;
    private String valueDate;
    private String maturityDate;
    private double interestAmount;
    private double taxAmount;
    private double repaymentAmount;
    private boolean stopaj;
    private int fundingAccountId;       // valörde fonun ÇIKACAĞI banka kasa hesabı
    private int collectionAccountId;    // vadede tahsilin GİRECEĞİ banka kasa hesabı
    private String correspondent1Bic;
    private String correspondent2Bic;
    private boolean createSwift;
    private boolean createMt320;
    private boolean createMt202;
    private String status = "ACTIVE";   // ACTIVE / MATURED / CANCELLED / ROLLED_OVER / EARLY_CLOSED
    private int parentDealId;           // rollover: bu deal'i doğuran eski deal
    private int rolledToId;             // rollover: bu deal'in devredildiği yeni deal
    private String earlyClosedAt;
    private Double penaltyAmount;
    private String createdBy;
    private String createdAt;
    private String settledAt;
    private String maturedAt;
    private java.util.List<MoneyMarketLendingCharge> charges = new java.util.ArrayList<>();

    public int getId() { return id; }
    public void setId(int v) { id = v; }
    @Override public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String v) { referenceNo = v; }
    public int getCounterpartyId() { return counterpartyId; }
    public void setCounterpartyId(int v) { counterpartyId = v; }
    @Override public int getCounterpartyNo() { return counterpartyNo; }
    public void setCounterpartyNo(int v) { counterpartyNo = v; }
    @Override public String getCounterpartyName() { return counterpartyName; }
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
    @Override public String getCurrency() { return currency; }
    public void setCurrency(String v) { currency = v; }
    @Override public double getPrincipal() { return principal; }
    public void setPrincipal(double v) { principal = v; }
    @Override public double getInterestRate() { return interestRate; }
    public void setInterestRate(double v) { interestRate = v; }
    @Override public String getDayCount() { return dayCount; }
    public void setDayCount(String v) { dayCount = v; }
    @Override public String getDealDate() { return dealDate; }
    public void setDealDate(String v) { dealDate = v; }
    @Override public String getValueDate() { return valueDate; }
    public void setValueDate(String v) { valueDate = v; }
    @Override public String getMaturityDate() { return maturityDate; }
    public void setMaturityDate(String v) { maturityDate = v; }
    @Override public double getInterestAmount() { return interestAmount; }
    public void setInterestAmount(double v) { interestAmount = v; }
    public double getTaxAmount() { return taxAmount; }
    public void setTaxAmount(double v) { taxAmount = v; }
    @Override public double getRepaymentAmount() { return repaymentAmount; }
    public void setRepaymentAmount(double v) { repaymentAmount = v; }
    public boolean isStopaj() { return stopaj; }
    public void setStopaj(boolean v) { stopaj = v; }
    public int getFundingAccountId() { return fundingAccountId; }
    public void setFundingAccountId(int v) { fundingAccountId = v; }
    public int getCollectionAccountId() { return collectionAccountId; }
    public void setCollectionAccountId(int v) { collectionAccountId = v; }
    @Override public String getCorrespondent1Bic() { return correspondent1Bic; }
    public void setCorrespondent1Bic(String v) { correspondent1Bic = v; }
    @Override public String getCorrespondent2Bic() { return correspondent2Bic; }
    public void setCorrespondent2Bic(String v) { correspondent2Bic = v; }
    public boolean isCreateSwift() { return createSwift; }
    public void setCreateSwift(boolean v) { createSwift = v; }
    public boolean isCreateMt320() { return createMt320; }
    public void setCreateMt320(boolean v) { createMt320 = v; }
    public boolean isCreateMt202() { return createMt202; }
    public void setCreateMt202(boolean v) { createMt202 = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public int getParentDealId() { return parentDealId; }
    public void setParentDealId(int v) { parentDealId = v; }
    public int getRolledToId() { return rolledToId; }
    public void setRolledToId(int v) { rolledToId = v; }
    public String getEarlyClosedAt() { return earlyClosedAt; }
    public void setEarlyClosedAt(String v) { earlyClosedAt = v; }
    public Double getPenaltyAmount() { return penaltyAmount; }
    public void setPenaltyAmount(Double v) { penaltyAmount = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { createdBy = v; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String v) { createdAt = v; }
    public String getSettledAt() { return settledAt; }
    public void setSettledAt(String v) { settledAt = v; }
    public String getMaturedAt() { return maturedAt; }
    public void setMaturedAt(String v) { maturedAt = v; }

    public java.util.List<MoneyMarketLendingCharge> getCharges() { return charges; }
    public void setCharges(java.util.List<MoneyMarketLendingCharge> v) { charges = (v == null ? new java.util.ArrayList<>() : v); }

    @Override public String getSwiftDirection() { return "L"; }   // banka borç veren

    public String getStatusText() {
        switch (status == null ? "" : status) {
            case "ACTIVE":       return "Aktif (Fon Verildi)";
            case "MATURED":      return "Vade Sonu Tahsil Edildi";
            case "CANCELLED":    return "İptal";
            case "ROLLED_OVER":  return "Vade Uzatıldı (Rollover)";
            case "EARLY_CLOSED": return "Erken Kapatıldı";
            default:             return status;
        }
    }
}
