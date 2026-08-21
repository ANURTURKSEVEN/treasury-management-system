package com.gtech.treasury.model;

/** Personel tarafında bir müşteriyle yapılan görüşmenin (thread) özeti. */
public class Conversation {

    private final int customerNo;
    private final String lastSubject;
    private final String lastAt;
    private final int unread;      // müşteriden gelen okunmamış mesaj sayısı

    public Conversation(int customerNo, String lastSubject, String lastAt, int unread) {
        this.customerNo = customerNo;
        this.lastSubject = lastSubject;
        this.lastAt = lastAt;
        this.unread = unread;
    }

    public int getCustomerNo()     { return customerNo; }
    public String getLastSubject() { return lastSubject; }
    public String getLastAt()      { return lastAt; }
    public int getUnread()         { return unread; }
}
