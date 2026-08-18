package com.gtech.treasury.model;

import java.time.LocalDate;

/** Bir kredi taksiti (loan_installment). */
public class Installment {
    private final int id;
    private final int seqNo;
    private final String dueDate;
    private final double amount;
    private final int status;       // 0 bekliyor / 1 ödendi
    private final String paidDate;

    public Installment(int id, int seqNo, String dueDate, double amount, int status, String paidDate) {
        this.id = id; this.seqNo = seqNo; this.dueDate = dueDate;
        this.amount = amount; this.status = status; this.paidDate = paidDate;
    }

    public int getId() { return id; }
    public int getSeqNo() { return seqNo; }
    public String getDueDate() { return dueDate; }
    public double getAmount() { return amount; }
    public int getStatus() { return status; }
    public String getPaidDate() { return paidDate; }

    /** Ödendi / Gecikmiş (vadesi geçti, ödenmedi) / Bekliyor. */
    public String getStatusText() {
        if (status == 1) return "Ödendi";
        try {
            if (dueDate != null && LocalDate.parse(dueDate.substring(0, 10)).isBefore(LocalDate.now()))
                return "Gecikmiş";
        } catch (Exception ignored) { }
        return "Bekliyor";
    }
}
