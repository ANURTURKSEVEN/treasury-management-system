package com.gtech.treasury.model;

/**
 * Bir döviz kurunu temsil eder (currency_rate tablosu).
 */
public class CurrencyRate {

    private String currency;      // USD, EUR, GBP
    private double buyRate;       // Döviz Alış (Forex Buying)
    private double sellRate;      // Döviz Satış (Forex Selling)
    private double effectiveBuy;  // Efektif Alış (Banknote Buying)
    private double effectiveSell; // Efektif Satış (Banknote Selling)
    private int status = 1;       // 1 = güncel, 0 = eski
    private int batchId;          // aynı çekimin (blok) kimliği
    private String updatedAt;     // güncelleme zamanı

    public CurrencyRate(String currency, double buyRate, double sellRate, String updatedAt) {
        this.currency = currency;
        this.buyRate = buyRate;
        this.sellRate = sellRate;
        this.updatedAt = updatedAt;
    }

    public CurrencyRate(String currency, double buyRate, double sellRate,
                        int status, int batchId, String updatedAt) {
        this(currency, buyRate, sellRate, updatedAt);
        this.status = status;
        this.batchId = batchId;
    }

    public String getCurrency() { return currency; }
    public double getBuyRate() { return buyRate; }
    public double getSellRate() { return sellRate; }
    public double getEffectiveBuy() { return effectiveBuy; }
    public double getEffectiveSell() { return effectiveSell; }
    public int getStatus() { return status; }
    public int getBatchId() { return batchId; }
    public String getUpdatedAt() { return updatedAt; }

    public void setBuyRate(double buyRate) { this.buyRate = buyRate; }
    public void setSellRate(double sellRate) { this.sellRate = sellRate; }
    public void setEffectiveBuy(double effectiveBuy) { this.effectiveBuy = effectiveBuy; }
    public void setEffectiveSell(double effectiveSell) { this.effectiveSell = effectiveSell; }
}
