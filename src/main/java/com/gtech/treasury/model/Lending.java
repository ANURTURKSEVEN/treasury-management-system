package com.gtech.treasury.model;

/** lending tablosundaki bir kredi kaydı (başvuru → onay/red → geri ödeme). */
public class Lending {

    private final int id;
    private final int customerId;
    private final int customerNo;
    private final String customerName;
    private final long accountNo;
    private final String loanType;        // IHTIYAC / TASIT / KONUT
    private final String currency;
    private final double amount;
    private final double interestRate;
    private final int termMonths;
    private final double monthlyPayment;
    private final double totalDue;
    private final int status;             // 0 başvuru /1 aktif /2 red /3 kapandı
    private final String rejectReason;
    private final String startDate;
    private final String maturityDate;
    private final int autoPay;            // 1 otomatik ödeme talimatı / 0 manuel

    public Lending(int id, int customerId, int customerNo, String customerName, long accountNo, String loanType,
                   String currency, double amount, double interestRate, int termMonths,
                   double monthlyPayment, double totalDue, int status, String rejectReason,
                   String startDate, String maturityDate, int autoPay) {
        this.id = id; this.customerId = customerId; this.customerNo = customerNo; this.customerName = customerName;
        this.accountNo = accountNo; this.loanType = loanType; this.currency = currency;
        this.amount = amount; this.interestRate = interestRate; this.termMonths = termMonths;
        this.monthlyPayment = monthlyPayment; this.totalDue = totalDue; this.status = status;
        this.rejectReason = rejectReason; this.startDate = startDate; this.maturityDate = maturityDate;
        this.autoPay = autoPay;
    }

    public boolean isAutoPay() { return autoPay == 1; }
    public String getPaymentModeText() { return autoPay == 1 ? "Otomatik" : "Manuel"; }

    public int getId() { return id; }
    public int getCustomerId() { return customerId; }
    public int getCustomerNo() { return customerNo; }
    public String getCustomerName() { return customerName; }
    public long getAccountNo() { return accountNo; }
    public String getLoanType() { return loanType; }
    public String getCurrency() { return currency; }
    public double getAmount() { return amount; }
    public double getInterestRate() { return interestRate; }
    public int getTermMonths() { return termMonths; }
    public double getMonthlyPayment() { return monthlyPayment; }
    public double getTotalDue() { return totalDue; }
    public int getStatus() { return status; }
    public String getRejectReason() { return rejectReason; }
    public String getStartDate() { return startDate; }
    public String getMaturityDate() { return maturityDate; }

    public String getStatusText() {
        switch (status) {
            case 0: return "Başvuru";
            case 1: return "Aktif";
            case 2: return "Reddedildi";
            case 3: return "Kapandı";
            default: return "-";
        }
    }
}
