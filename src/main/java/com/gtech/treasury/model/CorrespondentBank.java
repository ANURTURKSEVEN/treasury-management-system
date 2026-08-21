package com.gtech.treasury.model;

/** Muhabir (correspondent) banka — SWIFT mesajlarında karşı/aracı kurum. */
public class CorrespondentBank {
    private final int id;
    private final String bankName;
    private final String bic;
    private final String country;

    public CorrespondentBank(int id, String bankName, String bic, String country) {
        this.id = id; this.bankName = bankName; this.bic = bic; this.country = country;
    }

    public int getId()        { return id; }
    public String getBankName(){ return bankName; }
    public String getBic()    { return bic; }
    public String getCountry(){ return country; }

    /** Combo/liste gösterimi: "COBADEFF001 — COMMERZBANK AG". */
    @Override public String toString() { return bic + " — " + bankName; }
}
