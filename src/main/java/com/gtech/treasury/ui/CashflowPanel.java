package com.gtech.treasury.ui;

import com.gtech.treasury.dao.AccountDAO;
import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.dao.RateDAO;
import com.gtech.treasury.model.Account;
import com.gtech.treasury.model.ActivityLog;
import com.gtech.treasury.model.CurrencyRate;
import com.gtech.treasury.model.Customer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nakit Akışı (Cashflow) — dönemsel para giriş/çıkış, net ve kümülatif eğri.
 *   - Müşteri (customer != null): kendi hesaplarına giren/çıkan para.
 *   - Banka  (customer == null) : sisteme giren (yatırma) / çıkan (çekme + EFT/FAST) para.
 */
public class CashflowPanel extends JPanel {

    private static final Color IN_COLOR = new Color(0x1E8E3E);
    private static final Color OUT_COLOR = new Color(0xC5221F);
    private static final Color NET_COLOR = new Color(0x2D6CDF);
    private static final Color BG = new Color(0xF0F2F5);

    private final AccountDAO accountDAO = new AccountDAO();
    private final ActivityLogDAO activityDAO = new ActivityLogDAO();
    private final RateDAO rateDAO = new RateDAO();
    private final Customer customer;   // null = banka

    private final Map<String, CurrencyRate> rateCache = new LinkedHashMap<>();
    private final String[] PERIODS = {"Son 7 Gün", "Son 30 Gün", "Bu Ay", "Tümü"};
    private String period = "Son 30 Gün";

    public CashflowPanel(Customer customer) {
        this.customer = customer;
        setLayout(new BorderLayout());
        setBackground(BG);
        rebuild();
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) { rebuild(); }
        });
    }

    private void rebuild() {
        removeAll();
        add(buildContent(), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private static class Flow { final double tl; final String category;
        Flow(double tl, String category) { this.tl = tl; this.category = category; } }

    private JComponent buildContent() {
        List<ActivityLog> rows = gatherRows();
        java.util.Set<String> myAccounts = new java.util.HashSet<>();
        if (customer != null) {
            for (Account a : accountDAO.getByCustomer(customer.getCustomerId())) {
                myAccounts.add(String.valueOf(a.getAccountNo()));
            }
        }

        // Dönem içindeki hareketleri topla: liste (en yeni üstte) + günlük + kategori + toplamlar
        List<ActivityLog> flows = new ArrayList<>();
        double totalIn = 0, totalOut = 0;
        int countIn = 0, countOut = 0;
        Map<String, double[]> byDay = new java.util.TreeMap<>();  // tarih -> [giriş, çıkış]
        Map<String, double[]> byCat = new LinkedHashMap<>();      // kategori -> [imzalı tutar, adet]
        for (ActivityLog a : rows) {
            if (!inPeriod(a.getDatePart())) continue;
            Flow f = classify(a, myAccounts);
            if (f == null) continue;
            flows.add(a);
            double in = f.tl > 0 ? f.tl : 0, out = f.tl < 0 ? -f.tl : 0;
            totalIn += in; totalOut += out;
            if (in > 0) countIn++; else countOut++;
            byDay.computeIfAbsent(a.getDatePart(), k -> new double[2]);
            byDay.get(a.getDatePart())[0] += in;
            byDay.get(a.getDatePart())[1] += out;
            byCat.computeIfAbsent(f.category, k -> new double[2]);
            byCat.get(f.category)[0] += f.tl;
            byCat.get(f.category)[1] += 1;
        }
        flows.sort((x, y) -> Integer.compare(y.getId(), x.getId()));

        // ---- Üst: satır satır giriş/çıkış listesi ----
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(Color.WHITE);
        list.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)),
                new EmptyBorder(6, 12, 12, 12)));

        JLabel heading = new JLabel(customer != null
                ? "Hesap Hareketleri  —  Giriş / Çıkış"
                : "Banka Nakit Hareketleri  —  Giriş / Çıkış");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 14f));
        heading.setBorder(new EmptyBorder(8, 2, 8, 2));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        list.add(heading);

        if (flows.isEmpty()) {
            JLabel empty = new JLabel("Bu dönemde nakit hareketi yok.");
            empty.setForeground(new Color(0x6B7280));
            empty.setBorder(new EmptyBorder(12, 2, 12, 2));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            list.add(empty);
        } else {
            for (ActivityLog a : flows) {
                Flow f = classify(a, myAccounts);
                list.add(flowRow(a, f));
            }
        }
        list.setAlignmentX(Component.LEFT_ALIGNMENT);

        // ---- Merkez kolon: tablo (üstte) + küçültülmüş kutular + küçültülmüş grafikler ----
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(list);
        center.add(Box.createVerticalStrut(12));
        center.add(buildSummary(totalIn, totalOut, countIn, countOut));
        center.add(Box.createVerticalStrut(12));
        center.add(wrapCard("Günlük Nakit Akışı  (giriş / çıkış + kümülatif net, TL)",
                new FlowChart(buildDays(byDay)), 180));
        center.add(Box.createVerticalStrut(12));
        center.add(wrapCard("Kategori Dağılımı", buildCategories(byCat),
                50 + Math.max(1, byCat.size()) * 30));

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(18, 20, 18, 20));
        root.add(buildHeader(), BorderLayout.NORTH);

        JScrollPane sp = new JScrollPane(center);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getViewport().setBackground(BG);
        root.add(sp, BorderLayout.CENTER);
        return root;
    }

    /** Tek bir giriş/çıkış satırı: renkli ▲/▼ işaret + kategori + tarih | sağda imzalı tutar. */
    private JComponent flowRow(ActivityLog a, Flow f) {
        boolean in = f.tl >= 0;
        Color color = in ? IN_COLOR : OUT_COLOR;

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(Color.WHITE);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xF0F0F0)),
                new EmptyBorder(7, 4, 7, 4)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel mark = new JLabel(in ? "▲" : "▼");
        mark.setForeground(color);
        mark.setFont(mark.getFont().deriveFont(Font.BOLD, 15f));
        mark.setBorder(new EmptyBorder(0, 0, 0, 8));
        row.add(mark, BorderLayout.WEST);

        JLabel name = new JLabel("<html>" + f.category
                + " &nbsp;<font color='#9CA3AF'>• " + a.getDatePart() + " " + a.getTimePart()
                + "</font></html>");
        name.setFont(name.getFont().deriveFont(13f));
        row.add(name, BorderLayout.CENTER);

        JLabel amt = new JLabel((in ? "+" : "−") + String.format("%,.2f ₺", Math.abs(f.tl)));
        amt.setForeground(color);
        amt.setFont(amt.getFont().deriveFont(Font.BOLD, 14f));
        amt.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(amt, BorderLayout.EAST);
        return row;
    }

    // ---- Küçültülmüş özet kutuları: Giriş / Çıkış / Net ----
    private JComponent buildSummary(double totalIn, double totalOut, int cIn, int cOut) {
        double net = totalIn - totalOut;
        JPanel p = new JPanel(new GridLayout(1, 3, 12, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
        p.add(statCard("▲  Toplam Giriş", totalIn, IN_COLOR, cIn + " işlem"));
        p.add(statCard("▼  Toplam Çıkış", totalOut, OUT_COLOR, cOut + " işlem"));
        p.add(statCard("=  Net Akış", net, net >= 0 ? IN_COLOR : OUT_COLOR,
                net >= 0 ? "pozitif" : "negatif"));
        return p;
    }

    private JComponent statCard(String label, double value, Color color, String sub) {
        JPanel c = new JPanel(new BorderLayout(8, 0));
        c.setBackground(Color.WHITE);
        c.setBorder(BorderFactory.createLineBorder(new Color(0xE5E7EB)));
        JPanel stripe = new JPanel();
        stripe.setBackground(color);
        stripe.setPreferredSize(new Dimension(5, 10));
        c.add(stripe, BorderLayout.WEST);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(8, 10, 8, 10));
        JLabel l = new JLabel(label);
        l.setForeground(new Color(0x6B7280));
        l.setFont(l.getFont().deriveFont(Font.BOLD, 12f));
        JLabel v = new JLabel(String.format("%,.2f ₺", value));
        v.setFont(v.getFont().deriveFont(Font.BOLD, 17f));
        v.setForeground(color);
        JLabel s = new JLabel(sub);
        s.setForeground(new Color(0x9CA3AF));
        s.setFont(s.getFont().deriveFont(10f));
        body.add(l); body.add(Box.createVerticalStrut(3)); body.add(v);
        body.add(Box.createVerticalStrut(1)); body.add(s);
        c.add(body, BorderLayout.CENTER);
        return c;
    }

    private JComponent wrapCard(String heading, JComponent inner, int maxH) {
        JPanel c = new JPanel(new BorderLayout(0, 6));
        c.setBackground(Color.WHITE);
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)),
                new EmptyBorder(10, 14, 10, 14)));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, maxH));
        JLabel h = new JLabel(heading);
        h.setFont(h.getFont().deriveFont(Font.BOLD, 13f));
        c.add(h, BorderLayout.NORTH);
        c.add(inner, BorderLayout.CENTER);
        return c;
    }

    // ---- Kategori dağılımı: yatay barlar (kompakt) ----
    private JComponent buildCategories(Map<String, double[]> byCat) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        if (byCat.isEmpty()) {
            p.add(new JLabel("Bu dönemde kayıt yok."));
            return p;
        }
        double maxAbs = 1;
        for (double[] v : byCat.values()) maxAbs = Math.max(maxAbs, Math.abs(v[0]));
        for (Map.Entry<String, double[]> e : byCat.entrySet()) {
            double tl = e.getValue()[0];
            int cnt = (int) e.getValue()[1];
            boolean inflow = tl >= 0;
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(3, 0, 3, 0));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

            JLabel name = new JLabel(e.getKey() + "  (" + cnt + ")");
            name.setFont(name.getFont().deriveFont(12f));
            name.setPreferredSize(new Dimension(200, 18));
            row.add(name, BorderLayout.WEST);

            row.add(new HBar(Math.abs(tl) / maxAbs, inflow ? IN_COLOR : OUT_COLOR), BorderLayout.CENTER);

            JLabel val = new JLabel((inflow ? "+" : "−") + String.format("%,.2f ₺", Math.abs(tl)));
            val.setForeground(inflow ? IN_COLOR : OUT_COLOR);
            val.setFont(val.getFont().deriveFont(Font.BOLD, 12f));
            val.setHorizontalAlignment(SwingConstants.RIGHT);
            val.setPreferredSize(new Dimension(150, 18));
            row.add(val, BorderLayout.EAST);
            p.add(row);
        }
        return p;
    }

    /** Günleri {tarih, giriş, çıkış, kümülatif net} olarak sıralı döndürür (son 20). */
    private List<Object[]> buildDays(Map<String, double[]> byDay) {
        List<Object[]> all = new ArrayList<>();
        double cum = 0;
        for (Map.Entry<String, double[]> e : byDay.entrySet()) {
            double in = e.getValue()[0], out = e.getValue()[1];
            cum += (in - out);
            all.add(new Object[]{e.getKey(), in, out, cum});
        }
        int from = Math.max(0, all.size() - 20);
        return all.subList(from, all.size());
    }

    // ---- Yatay bar (kategori) ----
    private static class HBar extends JPanel {
        private final double frac;
        private final Color color;
        HBar(double frac, Color color) { this.frac = Math.max(0, Math.min(1, frac)); this.color = color; setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = 12, y = (getHeight() - h) / 2;
            g2.setColor(new Color(0xEEF1F5));
            g2.fillRoundRect(0, y, w, h, 8, 8);
            g2.setColor(color);
            g2.fillRoundRect(0, y, Math.max(4, (int) (w * frac)), h, 8, 8);
        }
    }

    // ---- Günlük giriş/çıkış çubukları + kümülatif net çizgisi (kompakt) ----
    private static class FlowChart extends JPanel {
        private final List<Object[]> data;   // {tarih, in, out, cumNet}
        FlowChart(List<Object[]> data) { this.data = data; setPreferredSize(new Dimension(760, 150)); setBackground(Color.WHITE); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (data.isEmpty()) { g.setColor(new Color(0x6B7280)); g.drawString("Bu dönemde nakit hareketi yok.", 12, 24); return; }
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight(), pad = 28, baseY = h - 30, topY = 22;
            double maxBar = 1, minNet = 0, maxNet = 0;
            for (Object[] d : data) {
                maxBar = Math.max(maxBar, Math.max((double) d[1], (double) d[2]));
                minNet = Math.min(minNet, (double) d[3]);
                maxNet = Math.max(maxNet, (double) d[3]);
            }
            double netRange = (maxNet - minNet); if (netRange <= 0) netRange = 1;
            int n = data.size(), slot = (w - 2 * pad) / n, bw = Math.min(14, (slot - 8) / 2);
            g2.setFont(g2.getFont().deriveFont(10f));

            int[] lx = new int[n], ly = new int[n];
            for (int i = 0; i < n; i++) {
                double in = (double) data.get(i)[1], out = (double) data.get(i)[2], net = (double) data.get(i)[3];
                int cx = pad + i * slot + slot / 2;
                int inH = (int) ((baseY - topY) * (in / maxBar));
                int outH = (int) ((baseY - topY) * (out / maxBar));
                g2.setColor(IN_COLOR);  g2.fillRoundRect(cx - bw - 1, baseY - inH, bw, inH, 5, 5);
                g2.setColor(OUT_COLOR); g2.fillRoundRect(cx + 1, baseY - outH, bw, outH, 5, 5);
                g2.setColor(new Color(0x9CA3AF));
                String label = ((String) data.get(i)[0]);
                if (label.length() >= 10) label = label.substring(5);
                g2.drawString(label, cx - g2.getFontMetrics().stringWidth(label) / 2, baseY + 15);
                lx[i] = cx;
                ly[i] = topY + (int) ((baseY - topY) * (1 - (net - minNet) / netRange));
            }
            g2.setStroke(new BasicStroke(2.2f));
            g2.setColor(NET_COLOR);
            for (int i = 0; i < n - 1; i++) g2.drawLine(lx[i], ly[i], lx[i + 1], ly[i + 1]);
            for (int i = 0; i < n; i++) g2.fillOval(lx[i] - 3, ly[i] - 3, 6, 6);
            String last = String.format("net %,.0f ₺", (double) data.get(n - 1)[3]);
            g2.setColor(new Color(0x111111)); g2.setFont(g2.getFont().deriveFont(Font.BOLD, 11f));
            g2.drawString(last, Math.min(lx[n - 1] + 4, w - g2.getFontMetrics().stringWidth(last) - 4), Math.max(14, ly[n - 1] - 8));

            // Lejant
            g2.setColor(IN_COLOR);  g2.fillRect(pad, 5, 10, 10); g2.setColor(new Color(0x374151)); g2.drawString("Giriş", pad + 14, 14);
            g2.setColor(OUT_COLOR); g2.fillRect(pad + 58, 5, 10, 10); g2.setColor(new Color(0x374151)); g2.drawString("Çıkış", pad + 72, 14);
            g2.setColor(NET_COLOR); g2.fillRect(pad + 118, 5, 10, 10); g2.setColor(new Color(0x374151)); g2.drawString("Kümülatif Net", pad + 132, 14);
        }
    }

    // ---- Başlık + dönem seçici ----
    private JComponent buildHeader() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        JLabel title = new JLabel(customer != null
                ? "💵  Nakit Akışım — " + customer.getCustomerName() + " " + customer.getSurname()
                : "💵  Banka Nakit Akışı (sisteme giren / çıkan)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        bar.add(title, BorderLayout.WEST);

        JComboBox<String> pc = new JComboBox<>(PERIODS);
        pc.setSelectedItem(period);
        pc.addActionListener(e -> { period = (String) pc.getSelectedItem(); rebuild(); });
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(new JLabel("Dönem:"));
        right.add(pc);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ---- Veri toplama + sınıflandırma ----
    private List<ActivityLog> gatherRows() {
        if (customer != null) {
            Map<Integer, ActivityLog> map = new LinkedHashMap<>();
            for (Account a : accountDAO.getByCustomer(customer.getCustomerId())) {
                for (ActivityLog al : activityDAO.byAccountNo(a.getAccountNo())) map.put(al.getId(), al);
            }
            return new ArrayList<>(map.values());
        }
        return activityDAO.search("", "", "", "", "", "", "");
    }

    private Flow classify(ActivityLog a, java.util.Set<String> myAccounts) {
        String type = a.getActionType();
        double tl = toTry(a.getAmount(), a.getCurrency());
        if (customer != null) {
            switch (type) {
                case "ACCOUNT_DEPOSIT": return new Flow(+tl, "Para Yatırma");
                case "ACCOUNT_WITHDRAW": return new Flow(-tl, "Para Çekme");
                case "TRANSFER": case "EFT": case "FAST": {
                    boolean incoming = targetIsMine(a.getDescription(), myAccounts);
                    return new Flow(incoming ? +tl : -tl, incoming ? "Gelen Transfer" : "Giden Transfer");
                }
                case "LOAN_GIVEN": return new Flow(+tl, "Kredi Kullanımı");
                case "LOAN_INSTALLMENT": return new Flow(-tl, "Kredi Taksiti");
                case "LOAN_REPAID": return new Flow(-tl, "Kredi Kapama");
                case "DEPOSIT_OPEN": return new Flow(-tl, "Mevduat Yatırma");
                case "DEPOSIT_CLOSE": case "DEPOSIT_BREAK": return new Flow(+tl, "Mevduat Getirisi");
                default: return null;
            }
        } else {
            switch (type) {
                case "ACCOUNT_DEPOSIT": return new Flow(+tl, "Para Yatırma (giriş)");
                case "ACCOUNT_WITHDRAW": return new Flow(-tl, "Para Çekme (çıkış)");
                case "EFT": case "FAST": return new Flow(-tl, "EFT/FAST (dışarı)");
                case "LOAN_GIVEN": return new Flow(-tl, "Kredi Verme (çıkış)");
                case "LOAN_INSTALLMENT": return new Flow(+tl, "Taksit Tahsilatı (giriş)");
                case "LOAN_REPAID": return new Flow(+tl, "Kredi Tahsilatı (giriş)");
                case "DEPOSIT_OPEN": return new Flow(+tl, "Mevduat (giriş)");
                case "DEPOSIT_CLOSE": case "DEPOSIT_BREAK": return new Flow(-tl, "Mevduat İadesi (çıkış)");
                default: return null;
            }
        }
    }

    private boolean targetIsMine(String description, java.util.Set<String> myAccounts) {
        if (description == null) return false;
        int arrow = description.indexOf('→');
        if (arrow < 0) return false;
        String right = description.substring(arrow + 1).trim();
        for (String acc : myAccounts) if (right.contains(acc)) return true;
        return false;
    }

    private double toTry(double amount, String currency) {
        if (currency == null || "TRY".equals(currency)) return amount;
        CurrencyRate r = rateCache.computeIfAbsent(currency, rateDAO::getByCurrency);
        return r == null ? amount : amount * r.getBuyRate();
    }

    private boolean inPeriod(String datePart) {
        if ("Tümü".equals(period) || datePart == null || datePart.length() < 10) return true;
        LocalDate d;
        try { d = LocalDate.parse(datePart.substring(0, 10)); } catch (Exception e) { return true; }
        LocalDate today = LocalDate.now();
        switch (period) {
            case "Son 7 Gün":  return !d.isBefore(today.minusDays(6));
            case "Son 30 Gün": return !d.isBefore(today.minusDays(29));
            case "Bu Ay":      return YearMonth.from(d).equals(YearMonth.from(today));
            default: return true;
        }
    }

}
