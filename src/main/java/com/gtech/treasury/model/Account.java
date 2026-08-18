package com.gtech.treasury.model;

/**
 * Bir müşteri hesabını temsil eder (account tablosu).
 */
public class Account {

    private int accountId;
    private long accountNo;
    private int customerId;
    private int customerNo;       // gösterim için (join)
    private String customerName;  // gösterim için (join)
    private String accountType;   // Vadesiz / Mevduat / Yatırım
    private String currency;      // TRY, USD, EUR, GBP
    private double balance;
    private int status;           // 1 açık, 0 kapalı
    private String openedAt;

    public Account(int accountId, long accountNo, int customerId, int customerNo,
                   String customerName, String accountType, String currency,
                   double balance, int status, String openedAt) {
        this.accountId = accountId;
        this.accountNo = accountNo;
        this.customerId = customerId;
        this.customerNo = customerNo;
        this.customerName = customerName;
        this.accountType = accountType;
        this.currency = currency;
        this.balance = balance;
        this.status = status;
        this.openedAt = openedAt;
    }

    public int getAccountId() { return accountId; }
    public long getAccountNo() { return accountNo; }
    public int getCustomerId() { return customerId; }
    public int getCustomerNo() { return customerNo; }
    public String getCustomerName() { return customerName; }
    public String getAccountType() { return accountType; }
    public String getCurrency() { return currency; }
    public double getBalance() { return balance; }
    public int getStatus() { return status; }
    public String getOpenedAt() { return openedAt; }

    /** openedAt "2026-08-01 10:24:10.0" -> tarih kısmı. */
    public String getOpenedDate() {
        if (openedAt == null) return "";
        int sp = openedAt.indexOf(' ');
        return sp > 0 ? openedAt.substring(0, sp) : openedAt;
    }

    /** openedAt -> saat kısmı (milisaniyesiz). */
    public String getOpenedTime() {
        if (openedAt == null) return "";
        int sp = openedAt.indexOf(' ');
        if (sp < 0) return "";
        String t = openedAt.substring(sp + 1);
        int dot = t.indexOf('.');
        return dot > 0 ? t.substring(0, dot) : t;
    }
}
