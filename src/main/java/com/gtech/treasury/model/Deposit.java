package com.gtech.treasury.model;

/** borrowing tablosundaki bir vadeli mevduat kaydı. */
public class Deposit {
    private final int id;
    private final int customerId;
    private final int customerNo;
    private final String customerName;
    private final long accountNo;
    private final String contractType;   // SABIT / ESNEK
    private final String currency;
    private final double amount;
    private final double interestRate;
    private final int termMonths;
    private final double interestAmount;
    private final double totalReturn;
    private final int status;            // 1 aktif / 0 kapandı
    private final String closeType;      // VADE / ERKEN / null
    private final String startDate;
    private final String maturityDate;

    public Deposit(int id, int customerId, int customerNo, String customerName, long accountNo,
                   String contractType, String currency, double amount, double interestRate, int termMonths,
                   double interestAmount, double totalReturn, int status, String closeType,
                   String startDate, String maturityDate) {
        this.id = id; this.customerId = customerId; this.customerNo = customerNo; this.customerName = customerName;
        this.accountNo = accountNo; this.contractType = contractType; this.currency = currency;
        this.amount = amount; this.interestRate = interestRate; this.termMonths = termMonths;
        this.interestAmount = interestAmount; this.totalReturn = totalReturn; this.status = status;
        this.closeType = closeType; this.startDate = startDate; this.maturityDate = maturityDate;
    }

    public int getId() { return id; }
    public int getCustomerId() { return customerId; }
    public int getCustomerNo() { return customerNo; }
    public String getCustomerName() { return customerName; }
    public long getAccountNo() { return accountNo; }
    public String getContractType() { return contractType; }
    public String getContractLabel() { return "ESNEK".equals(contractType) ? "Esnek" : "Sabit"; }
    public String getCurrency() { return currency; }
    public double getAmount() { return amount; }
    public double getInterestRate() { return interestRate; }
    public int getTermMonths() { return termMonths; }
    public double getInterestAmount() { return interestAmount; }
    public double getTotalReturn() { return totalReturn; }
    public int getStatus() { return status; }
    public String getCloseType() { return closeType; }
    public String getStartDate() { return startDate; }
    public String getMaturityDate() { return maturityDate; }

    public String getStatusText() {
        if (status == 2) return "Onay Bekliyor";
        if (status == 3) return "Reddedildi";
        if (status == 1) return "Aktif";
        if ("ERKEN".equals(closeType)) return "Kapandı (Erken)";
        return "Kapandı (Vade)";
    }
}
