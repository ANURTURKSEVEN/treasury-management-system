package com.gtech.treasury.ui;

import com.gtech.treasury.dao.AccountDAO;
import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.dao.RateDAO;
import com.gtech.treasury.dao.TreasurySnapshotDAO;
import com.gtech.treasury.model.Account;
import com.gtech.treasury.model.CurrencyRate;
import com.gtech.treasury.util.Notify;
import com.gtech.treasury.util.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Banka (Hazine) Kasası — admin görünümü.
 * Spot al/sat işlemlerinde karşı taraf olan banka hesaplarının bakiyelerini gösterir.
 * Salt okunur; bakiyeler işlemler oldukça değişir (Yenile ile tazelenir).
 */
public class BankTreasuryPanel extends JPanel {

    private static final Map<String, String> CUR_ICON = Map.of(
            "TRY", "₺", "USD", "$", "EUR", "€", "GBP", "£");

    private final AccountDAO accountDAO = new AccountDAO();
    private final RateDAO rateDAO = new RateDAO();

    public BankTreasuryPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(0xF0F2F5));
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

    private JComponent buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(new Color(0xF0F2F5));
        root.setBorder(new EmptyBorder(12, 16, 12, 16));

        List<Account> accounts = accountDAO.getBankAccounts();
        double totalTry = 0;
        for (Account a : accounts) totalTry += toTry(a.getBalance(), a.getCurrency());

        // Üst şerit: toplam hazine (TRY)
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(new Color(0x1F2A44));
        top.setBorder(new EmptyBorder(10, 18, 10, 18));
        JPanel tb = new JPanel(); tb.setOpaque(false);
        tb.setLayout(new BoxLayout(tb, BoxLayout.Y_AXIS));
        JLabel t1 = new JLabel("🏦  Banka Hazine Kasası");
        t1.setForeground(new Color(0xC7D2FE));
        JLabel t2 = new JLabel("Toplam (TRY karşılığı): " + String.format("%,.2f ₺", totalTry));
        t2.setForeground(Color.WHITE);
        t2.setFont(t2.getFont().deriveFont(Font.BOLD, 18f));
        tb.add(t1); tb.add(Box.createVerticalStrut(4)); tb.add(t2);
        top.add(tb, BorderLayout.WEST);
        JButton adjust = new JButton("Kasa Düzenle (＋/−)");
        adjust.addActionListener(e -> openAdjustDialog());
        JButton refresh = new JButton("Yenile");
        refresh.addActionListener(e -> rebuild());
        JPanel rp = new JPanel(new FlowLayout(FlowLayout.RIGHT)); rp.setOpaque(false);
        rp.add(adjust);
        rp.add(refresh);
        top.add(rp, BorderLayout.EAST);
        root.add(top, BorderLayout.NORTH);

        // İçerik: kasa kartları + varlık dağılımı grafiği
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel grid = new JPanel(new GridLayout(0, 4, 14, 14));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (Account a : accounts) grid.add(card(a));
        content.add(grid);
        content.add(Box.createVerticalStrut(10));

        // Döviz -> TL karşılığı dağılım grafiği
        Map<String, Double> byCur = new LinkedHashMap<>();
        for (Account a : accounts) {
            byCur.merge(a.getCurrency(), toTry(a.getBalance(), a.getCurrency()), Double::sum);
        }
        JPanel chartCard = new JPanel(new BorderLayout(0, 8));
        chartCard.setBackground(Color.WHITE);
        chartCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)),
                new EmptyBorder(14, 16, 14, 16)));
        chartCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        chartCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 240));
        JLabel ct = new JLabel("📊  Varlık Dağılımı (döviz bazında TL karşılığı)");
        ct.setFont(ct.getFont().deriveFont(Font.BOLD, 14f));
        chartCard.add(ct, BorderLayout.NORTH);

        JPanel charts = new JPanel(new GridLayout(1, 2, 20, 0));
        charts.setOpaque(false);
        // Sol: donut (yüzde) + lejant
        JPanel left = new JPanel(new BorderLayout(12, 0));
        left.setOpaque(false);
        left.add(new DonutChart(byCur), BorderLayout.WEST);
        left.add(legend(byCur), BorderLayout.CENTER);
        charts.add(left);
        // Sağ: çubuk (TL karşılığı)
        charts.add(new BarChart(byCur));
        chartCard.add(charts, BorderLayout.CENTER);
        content.add(chartCard);

        // Hazine değeri trend (çizgi) grafiği
        content.add(Box.createVerticalStrut(10));
        JPanel trendCard = new JPanel(new BorderLayout(0, 8));
        trendCard.setBackground(Color.WHITE);
        trendCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)),
                new EmptyBorder(14, 16, 14, 16)));
        trendCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        trendCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 360));
        JLabel tt = new JLabel("📈  Hazine Değeri Trendi (son işlemler / günler, TL karşılığı)");
        tt.setFont(tt.getFont().deriveFont(Font.BOLD, 14f));
        trendCard.add(tt, BorderLayout.NORTH);
        trendCard.add(new com.gtech.treasury.util.TrendChart(TreasurySnapshotDAO.recentSeries(30)), BorderLayout.CENTER);
        content.add(trendCard);

        // Trend grafiği uzun; içerik ekranı aşarsa kaydırılabilir
        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getViewport().setBackground(new Color(0xF0F2F5));
        root.add(sp, BorderLayout.CENTER);
        return root;
    }

    // ---- Döviz bazında TL karşılığı çubuk grafik ----
    private static final Color[] CHART_COLORS = {
            new Color(0x2D6CDF), new Color(0x1E8E3E), new Color(0xF59E0B),
            new Color(0xC5221F), new Color(0x8B5CF6), new Color(0x0EA5E9)};

    /** Donut (halka) grafik — döviz dağılımını yüzde dilimler halinde gösterir. */
    private static class DonutChart extends JPanel {
        private final Map<String, Double> data;

        DonutChart(Map<String, Double> data) {
            this.data = data;
            setPreferredSize(new Dimension(150, 150));
            setBackground(Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double total = data.values().stream().mapToDouble(Double::doubleValue).sum();
            int w = getWidth(), h = getHeight();
            int d = Math.min(w, h) - 16;
            int x = (w - d) / 2, y = (h - d) / 2;
            if (total <= 0) { g2.setColor(new Color(0x6B7280)); g2.drawString("Varlık yok", w / 2 - 28, h / 2); return; }

            double start = 90;
            int i = 0;
            for (Map.Entry<String, Double> e : data.entrySet()) {
                double extent = -(e.getValue() / total) * 360.0;
                g2.setColor(CHART_COLORS[i % CHART_COLORS.length]);
                g2.fillArc(x, y, d, d, (int) Math.round(start), (int) Math.round(extent));
                start += extent;
                i++;
            }
            int hole = (int) (d * 0.60);
            g2.setColor(Color.WHITE);
            g2.fillOval(x + (d - hole) / 2, y + (d - hole) / 2, hole, hole);
            g2.setColor(new Color(0x6B7280));
            g2.setFont(g2.getFont().deriveFont(10f));
            String cap = "Toplam";
            g2.drawString(cap, x + d / 2 - g2.getFontMetrics().stringWidth(cap) / 2, y + d / 2 - 6);
            g2.setColor(new Color(0x111111));
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 12f));
            String tt = String.format("%,.0f ₺", total);
            g2.drawString(tt, x + d / 2 - g2.getFontMetrics().stringWidth(tt) / 2, y + d / 2 + 10);
        }
    }

    /** Donut yanındaki renk + tutar + yüzde listesi. */
    private JComponent legend(Map<String, Double> byCur) {
        double total = byCur.values().stream().mapToDouble(Double::doubleValue).sum();
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(Box.createVerticalGlue());
        int i = 0;
        for (Map.Entry<String, Double> e : byCur.entrySet()) {
            double pct = total > 0 ? e.getValue() / total * 100 : 0;
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
            row.setOpaque(false);
            JPanel sw = new JPanel();
            sw.setPreferredSize(new Dimension(14, 14));
            sw.setBackground(CHART_COLORS[i % CHART_COLORS.length]);
            JLabel l = new JLabel(String.format("%s  —  %,.0f ₺  (%.1f%%)", e.getKey(), e.getValue(), pct));
            l.setFont(l.getFont().deriveFont(13f));
            row.add(sw);
            row.add(l);
            p.add(row);
            i++;
        }
        p.add(Box.createVerticalGlue());
        return p;
    }

    private static class BarChart extends JPanel {
        private final Map<String, Double> data;

        BarChart(Map<String, Double> data) {
            this.data = data;
            setPreferredSize(new Dimension(600, 150));
            setBackground(Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (data.isEmpty()) {
                g.setColor(new Color(0x6B7280));
                g.drawString("Gösterilecek varlık yok.", 12, 24);
                return;
            }
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            int pad = 28, baseY = h - 34;
            double max = data.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
            if (max <= 0) max = 1;
            int n = data.size();
            int slot = (w - 2 * pad) / n;
            int barW = Math.min(90, slot - 20);
            g2.setFont(g2.getFont().deriveFont(12f));

            int i = 0;
            for (Map.Entry<String, Double> e : data.entrySet()) {
                int x = pad + i * slot + (slot - barW) / 2;
                int barH = (int) ((baseY - 28) * (e.getValue() / max));
                g2.setColor(CHART_COLORS[i % CHART_COLORS.length]);
                g2.fillRoundRect(x, baseY - barH, barW, barH, 10, 10);
                g2.setColor(new Color(0x374151));
                g2.drawString(e.getKey(), x + barW / 2 - g2.getFontMetrics().stringWidth(e.getKey()) / 2, baseY + 18);
                String val = String.format("%,.0f ₺", e.getValue());
                g2.drawString(val, x + barW / 2 - g2.getFontMetrics().stringWidth(val) / 2, baseY - barH - 6);
                i++;
            }
        }
    }

    private JComponent card(Account a) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)),
                new EmptyBorder(10, 14, 10, 14)));
        card.setPreferredSize(new Dimension(200, 96));

        JLabel cur = new JLabel(CUR_ICON.getOrDefault(a.getCurrency(), "🏦") + "  " + a.getCurrency() + " Kasası");
        cur.setFont(cur.getFont().deriveFont(Font.BOLD, 13f));
        card.add(cur, BorderLayout.NORTH);

        JLabel bal = new JLabel(String.format("%,.2f", a.getBalance()));
        bal.setFont(bal.getFont().deriveFont(Font.BOLD, 18f));
        bal.setForeground(a.getBalance() < 0 ? new Color(0xC5221F) : UITheme.PRIMARY);
        card.add(bal, BorderLayout.CENTER);

        JLabel no = new JLabel("Hesap: " + a.getAccountNo());
        no.setForeground(new Color(0x9CA3AF));
        no.setFont(no.getFont().deriveFont(11f));
        card.add(no, BorderLayout.SOUTH);
        return card;
    }

    private double toTry(double amount, String currency) {
        if ("TRY".equals(currency)) return amount;
        CurrencyRate r = rateDAO.getByCurrency(currency);
        return r == null ? amount : amount * r.getBuyRate();
    }

    /** Banka kasasına (bütçesine) para ekle/çıkar (admin). */
    private void openAdjustDialog() {
        java.util.List<Account> banks = accountDAO.getBankAccounts();
        if (banks.isEmpty()) { Notify.warning(this, "Banka kasası bulunamadı."); return; }

        JComboBox<Account> accCombo = new JComboBox<>();
        for (Account a : banks) accCombo.addItem(a);
        accCombo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v,
                    int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof Account) {
                    Account a = (Account) v;
                    setText(a.getCurrency() + " Kasası  •  " + String.format("%,.2f", a.getBalance()));
                }
                return this;
            }
        });

        JRadioButton add = new JRadioButton("Ekle (＋)", true);
        JRadioButton sub = new JRadioButton("Çıkar (−)");
        ButtonGroup g = new ButtonGroup(); g.add(add); g.add(sub);
        JTextField amount = new JTextField(14);

        JPanel form = new JPanel(new GridLayout(0, 1, 4, 6));
        form.add(new JLabel("Kasa (döviz):"));
        form.add(accCombo);
        JPanel dir = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        dir.add(add); dir.add(Box.createHorizontalStrut(14)); dir.add(sub);
        form.add(dir);
        form.add(new JLabel("Tutar:"));
        form.add(amount);

        int res = JOptionPane.showConfirmDialog(this, form, "Banka Kasası Düzenle",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        Account a = (Account) accCombo.getSelectedItem();
        if (a == null) return;
        double val;
        try { val = Double.parseDouble(amount.getText().trim().replace(',', '.')); }
        catch (Exception e) { Notify.warning(this, "Geçerli bir tutar girin."); return; }
        if (val <= 0) { Notify.warning(this, "Tutar sıfırdan büyük olmalı."); return; }

        boolean addMode = add.isSelected();
        if (!addMode && val > a.getBalance()) {
            Notify.warning(this, "Kasada yeterli bakiye yok. Mevcut: " + String.format("%,.2f %s", a.getBalance(), a.getCurrency()));
            return;
        }

        double delta = addMode ? val : -val;
        if (accountDAO.changeBalance(a.getAccountId(), delta)) {
            ActivityLogDAO.log("BANK_ADJUST",
                    (addMode ? "Banka kasası artırıldı: " : "Banka kasası azaltıldı: ")
                            + String.format("%,.2f %s", val, a.getCurrency())
                            + " | Kasa: " + a.getAccountNo() + " (" + a.getCurrency() + ")");
            TreasurySnapshotDAO.record();
            rebuild();
            Notify.info(this, "Banka kasası güncellendi.\n\n"
                    + a.getCurrency() + " Kasası: " + String.format("%,.2f", val)
                    + (addMode ? " eklendi." : " çıkarıldı."));
        } else {
            Notify.error(this, "İşlem gerçekleştirilemedi.");
        }
    }

}
