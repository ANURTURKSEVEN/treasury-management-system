package com.gtech.treasury.model;

/** notification tablosundaki bir bildirim kaydı. */
public class Notification {
    private final int id;
    private final String title;
    private final String detail;
    private final String createdAt;
    private boolean read;
    private String type = "INFO";   // INFO / DISPUTE / DISPUTE_RESULT
    private String refNo;           // ilgili işlem/itiraz referansı

    public Notification(int id, String title, String detail, String createdAt) {
        this.id = id;
        this.title = title;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getDetail() { return detail; }
    public String getCreatedAt() { return createdAt; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getRefNo() { return refNo; }
    public void setRefNo(String refNo) { this.refNo = refNo; }
}
