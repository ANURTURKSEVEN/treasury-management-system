package com.gtech.treasury.model;

/**
 * Bir müşteriye özel sabitlenmiş (fixlenmiş) döviz kuru.
 * buyRate / sellRate 0 ise o yön için fix yok (güncel kur kullanılır).
 */
public class CustomerFixedRate {

    private int id;
    private int customerNo;
    private String currency;
    private double buyRate;
    private double sellRate;
    private String customerName;   // gösterim için (join'den gelir; yoksa boş)
    private String createdAt;

    public CustomerFixedRate(int id, int customerNo, String currency,
                             double buyRate, double sellRate,
                             String customerName, String createdAt) {
        this.id = id;
        this.customerNo = customerNo;
        this.currency = currency;
        this.buyRate = buyRate;
        this.sellRate = sellRate;
        this.customerName = customerName;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public int getCustomerNo() { return customerNo; }
    public String getCurrency() { return currency; }
    public double getBuyRate() { return buyRate; }
    public double getSellRate() { return sellRate; }
    public String getCustomerName() { return customerName; }
    public String getCreatedAt() { return createdAt; }

    /** İstenen yön için fix kur var mı? (0/negatif = yok) */
    public boolean hasRate(boolean isBuy) {
        return (isBuy ? buyRate : sellRate) > 0;
    }

    public double rateFor(boolean isBuy) {
        return isBuy ? buyRate : sellRate;
    }
}
