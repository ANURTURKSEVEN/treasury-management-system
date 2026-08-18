package com.gtech.treasury.ui;

import com.gtech.treasury.dao.AccountDAO;
import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.dao.CustomerDAO;
import com.gtech.treasury.dao.CustomerSnapshotDAO;
import com.gtech.treasury.dao.RateDAO;
import com.gtech.treasury.model.Account;
import com.gtech.treasury.model.CurrencyRate;
import com.gtech.treasury.model.Customer;
import com.gtech.treasury.util.Notify;
import com.gtech.treasury.util.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Müşteri anasayfası — mobil bankacılık tarzı.
 * Bölümler: Varlık Özeti + grafik, Hesaplarım (açılır), Bilgilerim (açılır güncelleme),
 * Hızlı İşlemler (transfer/döviz/rapor sekmelerini açar).
 */
public class CustomerHomePanel extends JPanel {

    private final Customer customer;
    private final Consumer<String> navigate;   // sekme açmak için (key)
    private final AccountDAO accountDAO = new AccountDAO();
    private final RateDAO rateDAO = new RateDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();

    private final JPanel body = new JPanel();

    // Grafiklerde döviz başına renk (donut + lejant + çubuk aynı sırayı kullanır)
    private static final Color[] CHART_COLORS = {
            new Color(0x2D6CDF), new Color(0x1E8E3E), new Color(0xF59E0B),
            new Color(0xC5221F), new Color(0x8B5CF6), new Color(0x0EA5E9)};

    public CustomerHomePanel(Customer customer, Consumer<String> navigate) {
        this.customer = customer;
        this.navigate = navigate;

        setLayout(new BorderLayout());
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(12, 16, 12, 16));
        body.setBackground(new Color(0xF0F2F5));

        // Trend grafiği uzun; içerik ekranı aşarsa kaydırılabilir
        JScrollPane sp = new JScrollPane(body);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.setBorder(null);
        add(sp, BorderLayout.CENTER);

        build();

        // Sekmeye her dönüşte (görünür olunca) toplam/grafikleri tazele
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) { build(); }
        });
    }

    private void build() {
        body.removeAll();
        List<Account> accounts = accountDAO.getByCustomer(customer.getCustomerId());

        body.add(buildSummary(accounts));
        body.add(Box.createVerticalStrut(10));
        body.add(buildDistributionCard(accounts));   // donut + lejant + çubuk (TL karşılığı)
        body.add(Box.createVerticalStrut(10));
        body.add(buildTrendCard());                  // varlık trend çizgisi

        body.revalidate();
        body.repaint();
    }

    // ---- Dağılım kartı: donut (TL karşılığı payı) + lejant/çubuk (döviz KENDİ biriminde) ----
    private JComponent buildDistributionCard(List<Account> accounts) {
        Map<String, Double> byCurTl = new LinkedHashMap<>();     // TL karşılığı (donut payı + toplam)
        Map<String, Double> byCurNative = new LinkedHashMap<>(); // kendi dövizinde (görünüm)
        for (Account a : accounts) {
            byCurTl.merge(a.getCurrency(), toTry(a.getBalance(), a.getCurrency()), Double::sum);
            byCurNative.merge(a.getCurrency(), a.getBalance(), Double::sum);
        }

        JPanel card = card();
        card.setLayout(new BorderLayout(0, 12));
        JLabel title = new JLabel("📊  Varlık Dağılımı (her döviz kendi biriminde; pay: TL karşılığı)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        card.add(title, BorderLayout.NORTH);

        JPanel charts = new JPanel(new GridLayout(1, 2, 24, 0));
        charts.setOpaque(false);
        JPanel left = new JPanel(new BorderLayout(14, 0));
        left.setOpaque(false);
        left.add(new DonutChart(byCurTl), BorderLayout.WEST);     // dilim payı TL karşılığına göre
        left.add(legend(byCurNative, byCurTl), BorderLayout.CENTER);
        charts.add(left);
        charts.add(new DistBarChart(byCurNative));                // çubuk: kendi dövizinde
        card.add(charts, BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 240));
        return card;
    }

    // ---- Trend kartı: müşterinin toplam varlığının zaman içindeki değişimi ----
    private JComponent buildTrendCard() {
        JPanel card = card();
        card.setLayout(new BorderLayout(0, 10));
        JLabel title = new JLabel("📈  Varlık Trendi (son günler, TL karşılığı)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        card.add(title, BorderLayout.NORTH);
        card.add(new com.gtech.treasury.util.TrendChart(
                CustomerSnapshotDAO.recentSeries(customer.getCustomerNo(), 30)), BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 360));
        return card;
    }

    /** Lejant: renk + tutar (KENDİ döviziyle) + pay yüzdesi (TL karşılığına göre). */
    private JComponent legend(Map<String, Double> byCurNative, Map<String, Double> byCurTl) {
        double totalTl = byCurTl.values().stream().mapToDouble(Double::doubleValue).sum();
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(Box.createVerticalGlue());
        int i = 0;
        for (Map.Entry<String, Double> e : byCurNative.entrySet()) {
            String cur = e.getKey();
            double pct = totalTl > 0 ? byCurTl.getOrDefault(cur, 0.0) / totalTl * 100 : 0;
            JPanel rowp = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
            rowp.setOpaque(false);
            JPanel sw = new JPanel();
            sw.setPreferredSize(new Dimension(14, 14));
            sw.setBackground(CHART_COLORS[i % CHART_COLORS.length]);
            JLabel l = new JLabel(String.format("%s  —  %,.2f %s  (%.1f%%)", cur, e.getValue(), cur, pct));
            l.setFont(l.getFont().deriveFont(13f));
            rowp.add(sw);
            rowp.add(l);
            p.add(rowp);
            i++;
        }
        if (byCurNative.isEmpty()) p.add(new JLabel("Gösterilecek varlık yok."));
        p.add(Box.createVerticalGlue());
        return p;
    }

    // ---- Varlık özeti (toplam TRY karşılığı) ----
    private JComponent buildSummary(List<Account> accounts) {
        double totalTry = 0;
        for (Account a : accounts) totalTry += toTry(a.getBalance(), a.getCurrency());

        JPanel card = card();
        card.setLayout(new BorderLayout());
        card.setBackground(UITheme.PRIMARY);

        JLabel hello = new JLabel("Hoş geldiniz, " + customer.getCustomerName() + " " + customer.getSurname());
        hello.setForeground(new Color(0xE8EEFF));
        hello.setFont(hello.getFont().deriveFont(14f));

        JLabel total = new JLabel(String.format("%,.2f ₺", totalTry));
        total.setForeground(Color.WHITE);
        total.setFont(total.getFont().deriveFont(Font.BOLD, 24f));

        JLabel sub = new JLabel("Toplam varlık (TL karşılığı) • " + accounts.size() + " hesap");
        sub.setForeground(new Color(0xC7D2FE));
        sub.setFont(sub.getFont().deriveFont(12f));

        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        texts.add(hello);
        texts.add(Box.createVerticalStrut(8));
        texts.add(total);
        texts.add(Box.createVerticalStrut(4));
        texts.add(sub);
        card.add(texts, BorderLayout.WEST);
        return card;
    }

    // ---- Hesap listesi (bakiyelerle) ----
    private JComponent buildAccounts(List<Account> accounts) {
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        if (accounts.isEmpty()) {
            list.add(new JLabel("Henüz hesabınız yok. Şube personeli hesap açabilir."));
        }
        for (Account a : accounts) {
            JPanel rowP = new JPanel(new BorderLayout());
            rowP.setBackground(Color.WHITE);
            rowP.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0xE5E7EB)),
                    new EmptyBorder(10, 14, 10, 14)));
            rowP.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

            JLabel left = new JLabel(a.getAccountNo() + "   •   " + a.getAccountType());
            left.setFont(left.getFont().deriveFont(Font.BOLD, 13f));
            JLabel right = new JLabel(String.format("%,.2f %s", a.getBalance(), a.getCurrency()));
            right.setFont(right.getFont().deriveFont(Font.BOLD, 15f));
            right.setForeground(new Color(0x1E8E3E));

            rowP.add(left, BorderLayout.WEST);
            rowP.add(right, BorderLayout.EAST);
            list.add(rowP);
            list.add(Box.createVerticalStrut(6));
        }
        return list;
    }

    // ---- Yardımcılar ----
    private double toTry(double amount, String currency) {
        if ("TRY".equals(currency)) return amount;
        CurrencyRate r = rateDAO.getByCurrency(currency);
        return r == null ? amount : amount * r.getBuyRate();
    }

    private JPanel card() {
        JPanel c = new JPanel();
        c.setBackground(Color.WHITE);
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)),
                new EmptyBorder(10, 14, 10, 14)));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    /** Donut (halka) grafik — döviz dağılımını yüzde dilimlerle gösterir. */
    private static class DonutChart extends JPanel {
        private final Map<String, Double> data;
        DonutChart(Map<String, Double> data) {
            this.data = data;
            setPreferredSize(new Dimension(150, 150));
            setBackground(Color.WHITE);
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double total = data.values().stream().mapToDouble(Double::doubleValue).sum();
            int w = getWidth(), h = getHeight(), d = Math.min(w, h) - 16;
            int x = (w - d) / 2, y = (h - d) / 2;
            if (total <= 0) { g2.setColor(new Color(0x6B7280)); g2.drawString("Varlık yok", w / 2 - 28, h / 2); return; }
            double start = 90;
            int i = 0;
            for (Map.Entry<String, Double> e : data.entrySet()) {
                double extent = -(e.getValue() / total) * 360.0;
                g2.setColor(CHART_COLORS[i % CHART_COLORS.length]);
                g2.fillArc(x, y, d, d, (int) Math.round(start), (int) Math.round(extent));
                start += extent; i++;
            }
            int hole = (int) (d * 0.60);
            g2.setColor(Color.WHITE);
            g2.fillOval(x + (d - hole) / 2, y + (d - hole) / 2, hole, hole);
            g2.setColor(new Color(0x6B7280)); g2.setFont(g2.getFont().deriveFont(10f));
            String cap = "Toplam";
            g2.drawString(cap, x + d / 2 - g2.getFontMetrics().stringWidth(cap) / 2, y + d / 2 - 6);
            g2.setColor(new Color(0x111111)); g2.setFont(g2.getFont().deriveFont(Font.BOLD, 13f));
            String tt = String.format("%,.0f ₺", total);
            g2.drawString(tt, x + d / 2 - g2.getFontMetrics().stringWidth(tt) / 2, y + d / 2 + 10);
        }
    }

    /** Çok renkli dağılım çubuğu (her döviz TL karşılığı). */
    private static class DistBarChart extends JPanel {
        private final Map<String, Double> data;
        DistBarChart(Map<String, Double> data) {
            this.data = data;
            setPreferredSize(new Dimension(320, 150));
            setBackground(Color.WHITE);
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (data.isEmpty()) { g.setColor(new Color(0x6B7280)); g.drawString("Varlık yok", 12, 24); return; }
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight(), pad = 24, baseY = h - 32;
            double max = data.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
            if (max <= 0) max = 1;
            int n = data.size(), slot = (w - 2 * pad) / n, barW = Math.min(70, slot - 16);
            g2.setFont(g2.getFont().deriveFont(11f));
            int i = 0;
            for (Map.Entry<String, Double> e : data.entrySet()) {
                int x = pad + i * slot + (slot - barW) / 2;
                int barH = (int) ((baseY - 26) * (e.getValue() / max));
                g2.setColor(CHART_COLORS[i % CHART_COLORS.length]);
                g2.fillRoundRect(x, baseY - barH, barW, barH, 8, 8);
                g2.setColor(new Color(0x374151));
                g2.drawString(e.getKey(), x + barW / 2 - g2.getFontMetrics().stringWidth(e.getKey()) / 2, baseY + 16);
                String val = String.format("%,.0f", e.getValue());
                g2.drawString(val, x + barW / 2 - g2.getFontMetrics().stringWidth(val) / 2, baseY - barH - 6);
                i++;
            }
        }
    }

}
