package com.gtech.treasury.ui;

import com.gtech.treasury.dao.AccountDAO;
import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.dao.ErrorLogDAO;
import com.gtech.treasury.dao.RateDAO;
import com.gtech.treasury.dao.TreasurySnapshotDAO;
import com.gtech.treasury.util.PdfService;

import java.io.File;
import com.gtech.treasury.model.Account;
import com.gtech.treasury.model.ActivityLog;
import com.gtech.treasury.model.CurrencyRate;
import com.gtech.treasury.util.Notify;
import com.gtech.treasury.util.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
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
    private final ActivityLogDAO activityDAO = new ActivityLogDAO();

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
        card.setPreferredSize(new Dimension(200, 110));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel cur = new JLabel(CUR_ICON.getOrDefault(a.getCurrency(), "🏦") + "  " + a.getCurrency() + " Kasası");
        cur.setFont(cur.getFont().deriveFont(Font.BOLD, 13f));
        card.add(cur, BorderLayout.NORTH);

        JLabel bal = new JLabel(String.format("%,.2f", a.getBalance()));
        bal.setFont(bal.getFont().deriveFont(Font.BOLD, 18f));
        bal.setForeground(a.getBalance() < 0 ? new Color(0xC5221F) : UITheme.PRIMARY);
        card.add(bal, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        JLabel no = new JLabel("Hesap: " + a.getAccountNo());
        no.setForeground(new Color(0x9CA3AF));
        no.setFont(no.getFont().deriveFont(11f));
        bottom.add(no, BorderLayout.WEST);
        JLabel more = new JLabel("Detay →");
        more.setForeground(UITheme.PRIMARY);
        more.setFont(more.getFont().deriveFont(11f));
        bottom.add(more, BorderLayout.EAST);
        card.add(bottom, BorderLayout.SOUTH);

        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { showDetail(a); }
        });
        return card;
    }

    private double toTry(double amount, String currency) {
        if ("TRY".equals(currency)) return amount;
        CurrencyRate r = rateDAO.getByCurrency(currency);
        return r == null ? amount : amount * r.getBuyRate();
    }

    // ================= KASA DETAYI (hesap bilgisi + giriş/çıkış hareketleri) =================
    private void showDetail(Account a) {
        removeAll();
        add(buildDetail(a), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private JComponent buildDetail(Account a) {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(new Color(0xF0F2F5));
        root.setBorder(new EmptyBorder(14, 16, 14, 16));

        JButton back = new JButton("←  Banka Kasası");
        back.addActionListener(e -> rebuild());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setOpaque(false);
        top.add(back);
        root.add(top, BorderLayout.NORTH);

        // Hesap özeti
        JPanel summary = new JPanel(new GridBagLayout());
        summary.setBackground(Color.WHITE);
        summary.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)), new EmptyBorder(18, 24, 18, 24)));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 8, 5, 8); g.anchor = GridBagConstraints.WEST;
        JLabel bal = new JLabel(String.format("%,.2f %s", a.getBalance(), a.getCurrency()));
        bal.setFont(bal.getFont().deriveFont(Font.BOLD, 26f));
        bal.setForeground(a.getBalance() < 0 ? new Color(0xC5221F) : UITheme.PRIMARY);
        g.gridx = 0; g.gridy = 0; g.gridwidth = 2; summary.add(bal, g); g.gridwidth = 1;
        int r = 1;
        r = info(summary, g, r, "Kasa:", a.getCurrency() + " Kasası");
        r = info(summary, g, r, "Hesap No:", String.valueOf(a.getAccountNo()));
        r = info(summary, g, r, "Döviz:", a.getCurrency());
        r = info(summary, g, r, "TL Karşılığı:", String.format("%,.2f ₺", toTry(a.getBalance(), a.getCurrency())));
        r = info(summary, g, r, "Durum:", a.getStatus() == 1 ? "Açık" : "Kapalı");

        // Hareketler (bu dövizin kasa giriş/çıkışları)
        List<ActivityLog> all = activityDAO.search("", "", "", "", a.getCurrency(), "", "");
        List<ActivityLog> flows = new ArrayList<>();
        for (ActivityLog al : all) if (bankLabel(al.getActionType()) != null) flows.add(al);

        JLabel mt = new JLabel("Kasa Hareketleri  —  " + a.getCurrency()
                + "  (giriş / çıkış — satıra çift tıklayarak dekontu görün)");
        mt.setFont(mt.getFont().deriveFont(Font.BOLD, 15f));

        BankMoveModel moveModel = new BankMoveModel(flows);
        JTable table = new JTable(moveModel);
        table.setRowHeight(26);
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0) showReceipt(moveModel.getAt(table.convertRowIndexToModel(row)));
                }
            }
        });
        JScrollPane sp = new JScrollPane(table);
        sp.setPreferredSize(new Dimension(700, 260));

        JPanel movePanel = new JPanel(new BorderLayout(0, 6));
        movePanel.setOpaque(false);
        movePanel.add(mt, BorderLayout.NORTH);
        movePanel.add(sp, BorderLayout.CENTER);
        if (flows.isEmpty()) {
            JLabel empty = new JLabel("Bu kasada henüz hareket yok.");
            empty.setForeground(new Color(0x6B7280));
            empty.setBorder(new EmptyBorder(8, 2, 2, 2));
            movePanel.add(empty, BorderLayout.SOUTH);
        }

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.add(summary, BorderLayout.NORTH);
        center.add(movePanel, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);
        return root;
    }

    private int info(JPanel p, GridBagConstraints g, int row, String label, String value) {
        g.gridx = 0; g.gridy = row;
        JLabel l = new JLabel(label); l.setFont(l.getFont().deriveFont(Font.BOLD));
        p.add(l, g);
        g.gridx = 1;
        p.add(new JLabel(value == null ? "-" : value), g);
        return row + 1;
    }

    /** Banka kasası perspektifinden hareket etiketi (null = kasayı etkilemeyen işlem, gösterilmez). */
    private static String bankLabel(String type) {
        if (type == null) return null;
        switch (type) {
            case "ACCOUNT_DEPOSIT":  return "Para Yatırma (giriş)";
            case "ACCOUNT_WITHDRAW": return "Para Çekme (çıkış)";
            case "EFT": case "FAST": return "EFT/FAST (dışarı)";
            case "LOAN_GIVEN": case "LOAN_DISBURSED": return "Kredi Verme (çıkış)";
            case "LOAN_INSTALLMENT": return "Taksit Tahsilatı (giriş)";
            case "LOAN_REPAID":      return "Kredi Tahsilatı (giriş)";
            case "DEPOSIT_OPEN":     return "Mevduat (giriş)";
            case "DEPOSIT_CLOSE": case "DEPOSIT_BREAK": return "Mevduat İadesi (çıkış)";
            case "MM_BORROW_CREATE": return "Para Piyasası Borçlanma (giriş)";
            case "MM_BORROW_MATURE": return "PP Borçlanma Geri Ödeme (çıkış)";
            case "MM_BORROW_CANCEL": return "PP Borçlanma İptal (çıkış)";
            case "MM_LEND_CREATE":   return "Para Piyasası Plasman (çıkış)";
            case "MM_LEND_MATURE":   return "PP Plasman Tahsil (giriş)";
            case "MM_LEND_CANCEL":   return "PP Plasman İptal (giriş)";
            case "MM_LEND_EARLY_CLOSE": return "PP Plasman Erken Kapama (giriş)";
            default: return null;
        }
    }

    /** true = kasaya giriş (+), false = çıkış (−). */
    private static boolean bankIsInflow(String type) {
        switch (type) {
            case "ACCOUNT_DEPOSIT": case "LOAN_INSTALLMENT": case "LOAN_REPAID": case "DEPOSIT_OPEN":
            case "MM_BORROW_CREATE":
            case "MM_LEND_MATURE": case "MM_LEND_CANCEL": case "MM_LEND_EARLY_CLOSE":
                return true;
            default:
                return false;   // withdraw, EFT/FAST, kredi verme, mevduat iadesi
        }
    }

    /** Bir kasa hareketi için dekont penceresi + PDF indirme (müşteri ekranındaki akışın aynısı). */
    private void showReceipt(ActivityLog a) {
        String islem = bankLabel(a.getActionType());
        if (islem == null) islem = a.getActionType();

        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(new EmptyBorder(8, 8, 8, 8));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 8, 5, 8); g.anchor = GridBagConstraints.WEST;

        int r = 0;
        JLabel header = new JLabel("Banka Kasa Dekontu");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 18f));
        g.gridx = 0; g.gridy = r++; g.gridwidth = 2; card.add(header, g); g.gridwidth = 1;

        boolean in = bankIsInflow(a.getActionType());
        r = recRow(card, g, r, "İşlem No:", String.valueOf(a.getId()));
        r = recRow(card, g, r, "Tarih:", a.getDatePart() + " " + a.getTimePart());
        r = recRow(card, g, r, "İşlem:", islem);
        r = recRow(card, g, r, "Yön:", in ? "Giriş (+)" : "Çıkış (−)");
        r = recRow(card, g, r, "Tutar:", (in ? "+" : "−") + String.format("%,.2f %s",
                a.getAmount(), a.getCurrency() == null ? "" : a.getCurrency()));
        r = recRow(card, g, r, "Yapan:", a.getUsername());
        r = recRow(card, g, r, "Açıklama:", a.getDescription());

        g.gridx = 0; g.gridy = r++; g.gridwidth = 2; card.add(new JSeparator(), g);
        JLabel dt = new JLabel("Detaylar"); dt.setFont(dt.getFont().deriveFont(Font.BOLD));
        g.gridy = r++; card.add(dt, g);
        JTextArea details = new JTextArea(a.getDetails() == null ? "-" : a.getDetails(), 4, 40);
        details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true);
        details.setBackground(new Color(0xF3F4F6));
        g.gridy = r++; card.add(new JScrollPane(details), g);

        Object[] secenekler = {"PDF İndir", "Kapat"};
        int secim = JOptionPane.showOptionDialog(this, card, "Dekont - İşlem #" + a.getId(),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, secenekler, secenekler[1]);
        if (secim != 0) return;   // "PDF İndir" değilse çık

        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("kasa_dekont_" + a.getId() + ".pdf"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            PdfService.dekontUret(fc.getSelectedFile(), islem, AccountDAO.BANK_CUSTOMER_NO,
                    a.getAmount(), a.getCurrency() == null ? "" : a.getCurrency(),
                    a.getDescription(), a.getDatePart() + " " + a.getTimePart());
            Notify.info(this, "Dekont kaydedildi:\n" + fc.getSelectedFile().getAbsolutePath());
        } catch (Exception ex) {
            ErrorLogDAO.log(ex, "Banka kasa dekont PDF");
            Notify.error(this, "PDF üretilemedi: " + ex.getMessage());
        }
    }

    /** Dekont kartı için etiket/değer satırı. */
    private int recRow(JPanel p, GridBagConstraints g, int row, String label, String value) {
        g.gridx = 0; g.gridy = row;
        JLabel l = new JLabel(label); l.setFont(l.getFont().deriveFont(Font.BOLD)); p.add(l, g);
        g.gridx = 1; p.add(new JLabel(value == null ? "-" : value), g);
        return row + 1;
    }

    private static class BankMoveModel extends AbstractTableModel {
        private final String[] cols = {"Tarih", "İşlem", "Tutar", "Açıklama"};
        private final List<ActivityLog> data;
        BankMoveModel(List<ActivityLog> data) { this.data = data; }
        ActivityLog getAt(int r) { return data.get(r); }
        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Object getValueAt(int row, int col) {
            ActivityLog a = data.get(row);
            switch (col) {
                case 0: return a.getDatePart() + " " + a.getTimePart();
                case 1: return bankLabel(a.getActionType());
                case 2: {
                    boolean in = bankIsInflow(a.getActionType());
                    return (in ? "+" : "−") + String.format("%,.2f %s",
                            a.getAmount(), a.getCurrency() == null ? "" : a.getCurrency());
                }
                case 3: return a.getDescription();
                default: return "";
            }
        }
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
