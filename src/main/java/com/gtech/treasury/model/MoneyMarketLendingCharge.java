package com.gtech.treasury.model;

/** Para piyasası PLASMAN deal'ine bağlı masraf/komisyon kalemi (mm_lending_charge). */
public class MoneyMarketLendingCharge {

    public static final String[] TYPES  = {"SWIFT_FEE", "CORRESPONDENT_FEE", "BROKER_FEE", "TXN_FEE", "OTHER"};
    public static final String[] LABELS = {"SWIFT Ücreti", "Muhabir Ücreti", "Broker Komisyonu", "İşlem Ücreti", "Diğer"};
    public static final String[] PAYERS = {"BANKA", "KARSI_KURUM"};

    public static String label(String type) {
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equals(type)) return LABELS[i];
        return type;
    }

    private int id;
    private int mlId;
    private String chargeType;
    private double amount;
    private String currency;
    private String payer = "BANKA";
    private String note;

    public MoneyMarketLendingCharge() {}
    public MoneyMarketLendingCharge(String chargeType, double amount, String currency, String payer, String note) {
        this.chargeType = chargeType; this.amount = amount; this.currency = currency;
        this.payer = payer; this.note = note;
    }

    public int getId() { return id; }
    public void setId(int v) { id = v; }
    public int getMlId() { return mlId; }
    public void setMlId(int v) { mlId = v; }
    public String getChargeType() { return chargeType; }
    public void setChargeType(String v) { chargeType = v; }
    public double getAmount() { return amount; }
    public void setAmount(double v) { amount = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { currency = v; }
    public String getPayer() { return payer; }
    public void setPayer(String v) { payer = v; }
    public String getNote() { return note; }
    public void setNote(String v) { note = v; }

    public String getTypeLabel() { return label(chargeType); }
    public String getPayerLabel() { return "BANKA".equals(payer) ? "Banka" : "Karşı Kurum"; }
}
