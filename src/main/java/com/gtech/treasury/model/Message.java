package com.gtech.treasury.model;

/** Banka gelen kutusundaki / mesajlaşmadaki bir mesajı temsil eder. */
public class Message {

    private final int id;
    private final String sender;      // "SYSTEM" | "STAFF:admin" | "CUSTOMER:10000001"
    private final String recipient;   // "STAFF" | "STAFF:admin" | "CUSTOMER:10000001"
    private final String subject;
    private final String body;
    private final String category;    // INFO | LOAN_APPROVAL | DEPOSIT_APPROVAL | SURVEY
    private final String refNo;       // ilgili kayıt (ör. kredi id) — yoksa null
    private final boolean read;
    private final String createdAt;

    public Message(int id, String sender, String recipient, String subject, String body,
                   String category, String refNo, boolean read, String createdAt) {
        this.id = id;
        this.sender = sender;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.category = category;
        this.refNo = refNo;
        this.read = read;
        this.createdAt = createdAt;
    }

    public int getId()           { return id; }
    public String getSender()    { return sender; }
    public String getRecipient() { return recipient; }
    public String getSubject()   { return subject; }
    public String getBody()      { return body; }
    public String getCategory()  { return category; }
    public String getRefNo()     { return refNo; }
    public boolean isRead()      { return read; }
    public String getCreatedAt() { return createdAt; }

    /** Bu mesaj bir onay işlemi mi? (altında "Değerlendir" butonu çıkacak mı) */
    public boolean isActionable() {
        return "LOAN_APPROVAL".equals(category) || "DEPOSIT_APPROVAL".equals(category);
    }
}