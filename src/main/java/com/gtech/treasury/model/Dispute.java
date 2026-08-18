package com.gtech.treasury.model;

/** İtiraz kaydı (müşteri bir işleme itiraz eder; banka karar verir). */
public class Dispute {
    private final int id;
    private final int customerNo;
    private final String subject;
    private final String customerReason;
    private final String status;      // OPEN / RESOLVED / REJECTED
    private final String resolvedBy;
    private final String resolution;
    private final String createdAt;

    public Dispute(int id, int customerNo, String subject, String customerReason,
                   String status, String resolvedBy, String resolution, String createdAt) {
        this.id = id;
        this.customerNo = customerNo;
        this.subject = subject;
        this.customerReason = customerReason;
        this.status = status;
        this.resolvedBy = resolvedBy;
        this.resolution = resolution;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public int getCustomerNo() { return customerNo; }
    public String getSubject() { return subject; }
    public String getCustomerReason() { return customerReason; }
    public String getStatus() { return status; }
    public String getResolvedBy() { return resolvedBy; }
    public String getResolution() { return resolution; }
    public String getCreatedAt() { return createdAt; }
}
