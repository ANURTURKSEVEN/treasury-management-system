package com.gtech.treasury.model;

/**
 * activity_log tablosundaki bir işlem kaydını temsil eder (Raporlar için).
 */
public class ActivityLog {

    private int id;
    private String actionType;
    private String username;
    private int customerNo;      // 0 ise ilgili müşteri yok
    private double amount;       // 0 ise tutar yok (spot dışı işlemler)
    private String currency;     // spot işlem dövizi (yoksa null)
    private String description;
    private String details;
    private String createdAt;

    public ActivityLog(int id, String actionType, String username, int customerNo,
                       double amount, String currency,
                       String description, String details, String createdAt) {
        this.id = id;
        this.actionType = actionType;
        this.username = username;
        this.customerNo = customerNo;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.details = details;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public String getActionType() { return actionType; }
    public String getUsername() { return username; }
    public int getCustomerNo() { return customerNo; }
    public double getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
    public String getDetails() { return details; }
    public String getCreatedAt() { return createdAt; }

    /** createdAt "2026-07-31 10:24:10.0" -> tarih kısmı. */
    public String getDatePart() {
        if (createdAt == null) return "";
        int sp = createdAt.indexOf(' ');
        return sp > 0 ? createdAt.substring(0, sp) : createdAt;
    }

    /** createdAt -> saat kısmı (milisaniyesiz). */
    public String getTimePart() {
        if (createdAt == null) return "";
        int sp = createdAt.indexOf(' ');
        if (sp < 0) return "";
        String t = createdAt.substring(sp + 1);
        int dot = t.indexOf('.');
        return dot > 0 ? t.substring(0, dot) : t;
    }
}
