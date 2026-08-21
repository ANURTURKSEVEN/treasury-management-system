package com.gtech.treasury.util;

/**
 * FX fiksasyon fiyatlama servisi (yönlü).
 *   Treasury Cost = ilgili yöndeki piyasa kuru (banka satış → market satış, banka alış → market alış).
 *   Banka Satış : customerRate = treasuryCost + spread   (pahalı satar)
 *   Banka Alış  : customerRate = treasuryCost - spread   (ucuz alır)
 *   P&L (TRY)   = spread × amount  (banka lehine; spread 0 ise 0)
 * Not: quote para birimi TRY varsayılır (CUR/TRY). Parite ileride.
 */
public final class FxPricingService {

    private FxPricingService() {}

    public static class Result {
        public final double treasuryCost;   // = marketRate (ilgili yön)
        public final double customerRate;    // müşteriye verilen kur
        public final double pnl;             // tahmini kâr/zarar (quote = TRY)
        Result(double t, double c, double p) { treasuryCost = t; customerRate = c; pnl = p; }
    }

    /**
     * @param marketRate  ilgili yöndeki anlık piyasa kuru (satış için market satış, alış için market alış)
     * @param spread      yayılım (>=0); 0 ise müşteri kuru = market
     * @param bankSell    true = Banka Satış, false = Banka Alış
     * @param amount      işlem tutarı (FX)
     */
    public static Result calculate(double marketRate, double spread, boolean bankSell, double amount) {
        double s = Math.max(0, spread);
        double customerRate = bankSell ? marketRate + s : marketRate - s;
        customerRate = round6(customerRate);
        double pnl = round2(s * amount);   // her iki yönde de banka lehine spread kadar
        return new Result(round6(marketRate), customerRate, pnl);
    }

    /**
     * İptal P&L: ilk fix kuru ile iptal kuru arasındaki farkın işleme etkisi.
     *   Banka Satış : (cancelRate - fixRate) × amount
     *   Banka Alış  : (fixRate - cancelRate) × amount
     */
    public static double cancellationPnl(double fixRate, double cancelRate, boolean bankSell, double amount) {
        double diff = bankSell ? (cancelRate - fixRate) : (fixRate - cancelRate);
        return round2(diff * amount);
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private static double round6(double v) { return Math.round(v * 1_000_000.0) / 1_000_000.0; }
}
