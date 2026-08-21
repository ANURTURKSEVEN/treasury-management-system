package com.gtech.treasury.util;

/**
 * SWIFT mesaj üretimi için deal görünümü. Hem MoneyMarketBorrowing hem
 * MoneyMarketLending bu arayüzü uygular; böylece SwiftMessageService tek
 * uygulamayla iki yönü de (borç alma / borç verme) üretebilir.
 */
public interface SwiftDealView {
    String getReferenceNo();
    String getCorrespondent1Bic();
    String getCorrespondent2Bic();
    String getDealDate();
    String getValueDate();
    String getMaturityDate();
    String getCurrency();
    double getPrincipal();
    double getInterestRate();
    double getInterestAmount();
    double getRepaymentAmount();
    String getDayCount();
    int getCounterpartyNo();
    String getCounterpartyName();

    /** MT320 :17R: yönü — "B" (borç alan/borrower) veya "L" (borç veren/lender). */
    String getSwiftDirection();
}
