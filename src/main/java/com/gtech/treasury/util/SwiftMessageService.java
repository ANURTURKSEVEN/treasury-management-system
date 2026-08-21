package com.gtech.treasury.util;

/**
 * SWIFT mesaj metni üretir (önizleme/kayıt amaçlı — gerçek ağa gönderim yok).
 *   MT320 = Fixed Loan/Deposit Confirmation (borçlanma teyidi)
 *   MT202 = General Financial Institution Transfer (kurumlar arası ödeme talimatı)
 * Alan etiketleri SWIFT standardına uygun seçilmiştir; birebir tam uyum iddiası yoktur.
 */
public final class SwiftMessageService {

    private SwiftMessageService() {}

    /** yyyy-MM-dd -> YYMMDD (SWIFT tarih biçimi). */
    private static String ymd(String d) {
        if (d == null || d.length() < 10) return "";
        return d.substring(2, 4) + d.substring(5, 7) + d.substring(8, 10);
    }
    /** SWIFT tutar biçimi: ondalık ayıracı virgül. */
    private static String amt(double v) {
        return String.format(java.util.Locale.US, "%.2f", v).replace('.', ',');
    }
    /** A/360 -> ACT/360, A/365 -> ACT/365, 30/360 -> 30/360. */
    private static String dcf(String dayCount) {
        if ("A/360".equals(dayCount)) return "ACT/360";
        if ("A/365".equals(dayCount)) return "ACT/365";
        return dayCount == null ? "" : dayCount;
    }

    /** MT320 — sabit borçlanma/plasman teyidi. */
    public static String buildMT320(SwiftDealView d) {
        StringBuilder s = new StringBuilder();
        s.append("{1:F01BANKTRISAXXX0000000000}\n");
        s.append("{2:I320").append(nz(d.getCorrespondent1Bic())).append("N}\n");
        s.append("{4:\n");
        s.append(":15A:\n");
        s.append(":20:").append(d.getReferenceNo()).append("\n");
        s.append(":22A:NEWT\n");                                  // yeni işlem
        s.append(":22B:FIXED\n");                                 // sabit
        s.append(":22C:").append(shortRef(d.getReferenceNo())).append("\n");
        s.append(":17R:").append(nz(d.getSwiftDirection())).append("\n");  // B=borç alan, L=borç veren
        s.append(":30T:").append(ymd(d.getDealDate())).append("\n");     // deal date
        s.append(":30V:").append(ymd(d.getValueDate())).append("\n");    // value date
        s.append(":30P:").append(ymd(d.getMaturityDate())).append("\n"); // maturity
        s.append(":32B:").append(d.getCurrency()).append(amt(d.getPrincipal())).append("\n");
        s.append(":37G:").append(amt(d.getInterestRate())).append("\n"); // faiz oranı
        s.append(":14D:").append(dcf(d.getDayCount())).append("\n");     // day count fraction
        s.append(":34E:").append(d.getCurrency()).append(amt(d.getInterestAmount())).append("\n");
        if (d.getCounterpartyName() != null)
            s.append(":88D:").append(d.getCounterpartyNo()).append(" ").append(d.getCounterpartyName()).append("\n");
        s.append("-}");
        return s.toString();
    }

    /** MT202 — vade sonu ödeme/tahsil (kurumlar arası transfer) talimatı. */
    public static String buildMT202(SwiftDealView d) {
        StringBuilder s = new StringBuilder();
        s.append("{1:F01BANKTRISAXXX0000000000}\n");
        s.append("{2:I202").append(nz(d.getCorrespondent1Bic())).append("N}\n");
        s.append("{4:\n");
        s.append(":20:").append(shortRef(d.getReferenceNo())).append("\n");
        s.append(":21:").append(d.getReferenceNo()).append("\n");        // ilgili referans
        s.append(":32A:").append(ymd(d.getMaturityDate())).append(d.getCurrency()).append(amt(d.getRepaymentAmount())).append("\n");
        s.append(":52A:BANKTRISAXXX\n");                                 // ordering institution (biz)
        if (d.getCorrespondent2Bic() != null && !d.getCorrespondent2Bic().isBlank())
            s.append(":57A:").append(d.getCorrespondent2Bic()).append("\n"); // account with institution
        s.append(":58A:").append(nz(d.getCorrespondent1Bic())).append("\n"); // beneficiary institution
        s.append("-}");
        return s.toString();
    }

    private static String nz(String s) { return (s == null) ? "" : s; }
    private static String shortRef(String ref) {
        if (ref == null) return "";
        return ref.length() > 16 ? ref.substring(ref.length() - 16) : ref;
    }
}
