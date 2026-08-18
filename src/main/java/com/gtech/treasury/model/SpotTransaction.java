package com.gtech.treasury.model;

/**
 * spot_transaction tablosundaki bir işlem kaydını temsil eder.
 * (İşlem geçmişi ekranında gösterim için müşteri adı da tutulur.)
 */
public class SpotTransaction {

    private int id;
    private int customerNo;
    private String customerName;
    private String buyCurrency;
    private double buyAmount;
    private String sellCurrency;
    private double sellAmount;
    private double rate;
    private String transactionDate;

    public SpotTransaction(int id, int customerNo, String customerName,
                           String buyCurrency, double buyAmount,
                           String sellCurrency, double sellAmount,
                           double rate, String transactionDate) {
        this.id = id;
        this.customerNo = customerNo;
        this.customerName = customerName;
        this.buyCurrency = buyCurrency;
        this.buyAmount = buyAmount;
        this.sellCurrency = sellCurrency;
        this.sellAmount = sellAmount;
        this.rate = rate;
        this.transactionDate = transactionDate;
    }

    public int getId() { return id; }
    public int getCustomerNo() { return customerNo; }
    public String getCustomerName() { return customerName; }
    public String getBuyCurrency() { return buyCurrency; }
    public double getBuyAmount() { return buyAmount; }
    public String getSellCurrency() { return sellCurrency; }
    public double getSellAmount() { return sellAmount; }
    public double getRate() { return rate; }
    public String getTransactionDate() { return transactionDate; }
}
