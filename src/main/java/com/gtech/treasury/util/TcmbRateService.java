package com.gtech.treasury.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TCMB günlük döviz kurlarını XML'den çeker.
 * Kaynak: https://www.tcmb.gov.tr/kurlar/today.xml
 *   ForexBuying    = Döviz Alış      ForexSelling    = Döviz Satış
 *   BanknoteBuying = Efektif Alış    BanknoteSelling = Efektif Satış
 */
public final class TcmbRateService {

    private static final String URL = "https://www.tcmb.gov.tr/kurlar/today.xml";

    private TcmbRateService() {
    }

    /**
     * İstenen para birimleri için kurları getirir.
     * @return currency -> [dövizAlış, dövizSatış, efektifAlış, efektifSatış]
     *         (döviz alış/satış yoksa atlanır; efektif yoksa 0 kalır)
     */
    public static Map<String, double[]> fetchRates(Collection<String> currencies) throws Exception {
        Map<String, double[]> result = new LinkedHashMap<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try (var in = new URL(URL).openStream()) {
            Document doc = factory.newDocumentBuilder().parse(in);
            NodeList nodes = doc.getElementsByTagName("Currency");

            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                String code = el.getAttribute("CurrencyCode");
                if (!currencies.contains(code)) continue;

                double buy      = parse(text(el, "ForexBuying"));
                double sell     = parse(text(el, "ForexSelling"));
                double effBuy   = parse(text(el, "BanknoteBuying"));
                double effSell  = parse(text(el, "BanknoteSelling"));
                if (buy > 0 && sell > 0) {
                    result.put(code, new double[]{buy, sell, effBuy, effSell});
                }
            }
        }
        return result;
    }

    /** Bir Currency elementinin içindeki tag metnini döndürür. */
    private static String text(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) return "";
        return list.item(0).getTextContent().trim();
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }
}
