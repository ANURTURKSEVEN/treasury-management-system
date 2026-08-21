package com.gtech.treasury.util;

import java.time.LocalDate;

/**
 * Para piyasası / basit faiz hesaplama servisi (day-count convention'lı).
 *   Faiz = Anapara × (Yıllık Oran / 100) × Gün / Baz
 *   Geri Ödeme = Anapara + Faiz − Stopaj
 * Desteklenen yöntemler: A/360, A/365, 30/360.
 */
public final class InterestCalculationService {

    private InterestCalculationService() {}

    /** Stopaj oranı — DEMO/placeholder; gerçek oran konfigürasyondan gelmelidir. */
    public static final double STOPAJ_RATE = 0.15;

    public static final String[] DAY_COUNTS = {"A/360", "A/365", "30/360"};

    /** Hesaplama sonucu. */
    public static class Result {
        public final long days;
        public final double interest;
        public final double tax;
        public final double repayment;   // anapara + faiz − stopaj
        Result(long days, double interest, double tax, double repayment) {
            this.days = days; this.interest = interest; this.tax = tax; this.repayment = repayment;
        }
    }

    /** Convention'a göre iki tarih arasındaki gün sayısı. */
    public static long days(LocalDate value, LocalDate maturity, String dayCount) {
        if (value == null || maturity == null) return 0;
        if ("30/360".equals(dayCount)) {
            int d1 = value.getDayOfMonth(), d2 = maturity.getDayOfMonth();
            if (d1 == 31) d1 = 30;
            if (d2 == 31 && d1 == 30) d2 = 30;
            return 360L * (maturity.getYear() - value.getYear())
                    + 30L * (maturity.getMonthValue() - value.getMonthValue())
                    + (d2 - d1);
        }
        // A/360 ve A/365: gerçek (actual) gün farkı
        return java.time.temporal.ChronoUnit.DAYS.between(value, maturity);
    }

    private static double basis(String dayCount) {
        return "A/365".equals(dayCount) ? 365.0 : 360.0;   // A/360 ve 30/360 → 360
    }

    /**
     * Faiz + geri ödeme hesabı.
     * @param stopaj true ise faizden STOPAJ_RATE oranında kesinti uygulanır.
     */
    public static Result calculate(double principal, double annualRatePct,
                                   LocalDate value, LocalDate maturity,
                                   String dayCount, boolean stopaj) {
        long d = days(value, maturity, dayCount);
        double interest = principal * (annualRatePct / 100.0) * (d / basis(dayCount));
        interest = round2(interest);
        double tax = stopaj ? round2(interest * STOPAJ_RATE) : 0.0;
        double repayment = round2(principal + interest - tax);
        return new Result(d, interest, tax, repayment);
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
