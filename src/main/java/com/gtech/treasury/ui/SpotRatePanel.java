package com.gtech.treasury.ui;

import com.gtech.treasury.dao.RateDAO;
import com.gtech.treasury.model.CurrencyRate;
import com.gtech.treasury.util.UITheme;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Kur Ekranı — 3 döviz için alış/satış kuru manuel düzenlenir ve gözlenir.
 * Rol: ADMIN/TRADER düzenleyebilir; VIEWER sadece gözlemler.
 */
public class SpotRatePanel extends JPanel {

    private static final Color BUY_COLOR = new Color(0x1E8E3E);   // alış - yeşil
    private static final Color SELL_COLOR = new Color(0xC5221F);  // satış - kırmızı

    private final RateDAO rateDAO = new RateDAO();
    private final boolean canEdit;
    private final RateTableModel tableModel;
    private final JTable table;
    private final Runnable goToTradeAction;

    public SpotRatePanel(boolean canEdit, Runnable goToTradeAction) {
        this.goToTradeAction = goToTradeAction;
        this.canEdit = canEdit;

        this.tableModel = new RateTableModel(canEdit);
        this.table = new JTable(tableModel);

        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildTableCard(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        loadRates();
        startAutoReload();   // DB'den periyodik yeniden yükle (kurları batch günceller)
    }

    private javax.swing.Timer autoTimer;

    /**
     * Kurları TCMB'den ÇEKMEZ — o işi batch (RateUpdateJob) yapar.
     * Bu ekran sadece DB'deki güncel kuru gösterir; batch güncelleyince
     * ekran açıksa 60 sn içinde tabloya yansısın diye DB'den yeniden yükler.
     */
    private void startAutoReload() {
        autoTimer = new javax.swing.Timer(60_000, e -> loadRates());
        autoTimer.start();
    }

    @Override
    public void removeNotify() {
        if (autoTimer != null) autoTimer.stop();   // ekran kapanınca zamanlayıcıyı durdur
        super.removeNotify();
    }

    private JComponent buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        JLabel title = new JLabel("💱  Güncel Döviz Kurları");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JLabel hint = new JLabel("Kurlar TCMB'den otomatik güncellenir (her iş günü 15:30). Salt görüntüleme.");
        hint.setForeground(new Color(0x6B7280));
        p.add(title, BorderLayout.NORTH);
        p.add(hint, BorderLayout.SOUTH);
        return p;
    }

    private JComponent buildTableCard() {
        table.setRowHeight(36);
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(0xE5E7EB));
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, 13f));
        table.setFont(table.getFont().deriveFont(14f));

        // Döviz: ortalı + kalın
        table.getColumnModel().getColumn(0).setCellRenderer(centered(Font.BOLD, null));
        // Döviz Alış / Efektif Alış: sağa hizalı, yeşil
        table.getColumnModel().getColumn(1).setCellRenderer(number(BUY_COLOR));
        table.getColumnModel().getColumn(3).setCellRenderer(number(BUY_COLOR));
        // Döviz Satış / Efektif Satış: sağa hizalı, kırmızı
        table.getColumnModel().getColumn(2).setCellRenderer(number(SELL_COLOR));
        table.getColumnModel().getColumn(4).setCellRenderer(number(SELL_COLOR));
        // Son güncelleme: ortalı, gri
        table.getColumnModel().getColumn(5).setCellRenderer(centered(Font.PLAIN, new Color(0x6B7280)));

        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(new Color(0xE5E7EB)));
        return sp;
    }

    private JComponent buildButtons() {
        JPanel panel = new JPanel(new BorderLayout());

        // "Al/Sat'a Git" yalnızca eski birleşik ekranda anlamlı; ayrı menü ekranında gizli
        if (goToTradeAction != null) {
            JButton goTrade = new JButton("Döviz Al / Sat'a Git  →");
            UITheme.stylePrimary(goTrade);
            goTrade.addActionListener(e -> goToTradeAction.run());
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
            left.add(goTrade);
            panel.add(left, BorderLayout.WEST);
        }

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton history = new JButton("Kur Geçmişi");
        history.addActionListener(e -> openHistory());
        right.add(history);

        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    /** Kur geçmişini BLOKLAR halinde gösteren pencere. */
    private void openHistory() {
        DefaultTableModel model = new DefaultTableModel(
                new String[]{"Blok", "Döviz", "Döviz Alış", "Döviz Satış",
                             "Efektif Alış", "Efektif Satış", "Durum", "Tarih", "Saat"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (CurrencyRate r : rateDAO.getHistory()) {
            model.addRow(new Object[]{
                    "#" + r.getBatchId(),
                    r.getCurrency(),
                    String.format("%,.4f", r.getBuyRate()),
                    String.format("%,.4f", r.getSellRate()),
                    r.getEffectiveBuy() > 0 ? String.format("%,.4f", r.getEffectiveBuy()) : "-",
                    r.getEffectiveSell() > 0 ? String.format("%,.4f", r.getEffectiveSell()) : "-",
                    r.getStatus() == 1 ? "Güncel" : "Eski",
                    datePart(r.getUpdatedAt()),
                    timePart(r.getUpdatedAt())});
        }
        JTable t = new JTable(model);
        t.setRowHeight(26);
        // Yatay kaydırma için: sütunlar kendi genişliğinde kalsın, sığmazsa scroll çıksın
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {55, 60, 95, 95, 95, 95, 70, 110, 85};
        for (int i = 0; i < widths.length && i < t.getColumnCount(); i++) {
            t.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        JScrollPane sp = new JScrollPane(t,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sp.setPreferredSize(new Dimension(820, 200));
        JOptionPane.showMessageDialog(this, sp,
                "Kur Geçmişi (aynı Blok = aynı çekimdeki kurlar)", JOptionPane.PLAIN_MESSAGE);
    }

    /** "2026-08-04 15:30:00.0" -> "2026-08-04" */
    private static String datePart(String ts) {
        if (ts == null) return "-";
        int sp = ts.indexOf(' ');
        return sp > 0 ? ts.substring(0, sp) : ts;
    }

    /** "2026-08-04 15:30:00.0" -> "15:30:00" (milisaniyesiz) */
    private static String timePart(String ts) {
        if (ts == null) return "-";
        int sp = ts.indexOf(' ');
        if (sp < 0) return "-";
        String time = ts.substring(sp + 1);
        int dot = time.indexOf('.');
        return dot > 0 ? time.substring(0, dot) : time;
    }

    // Sağa hizalı, renkli, 4 ondalık sayı hücresi
    private DefaultTableCellRenderer number(Color color) {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                if (value instanceof Number) {
                    setText(String.format("%,.4f", ((Number) value).doubleValue()));
                } else {
                    super.setValue(value);
                }
            }
        };
        r.setHorizontalAlignment(SwingConstants.RIGHT);
        r.setForeground(color);
        r.setFont(r.getFont().deriveFont(Font.BOLD, 14f));
        return r;
    }

    private DefaultTableCellRenderer centered(int style, Color color) {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer();
        r.setHorizontalAlignment(SwingConstants.CENTER);
        if (color != null) r.setForeground(color);
        r.setFont(r.getFont().deriveFont(style, 14f));
        return r;
    }

    private void loadRates() {
        tableModel.setData(rateDAO.getAll());
    }

    // ---- Tablo modeli (salt görüntüleme) ----
    private static class RateTableModel extends AbstractTableModel {
        private final String[] columns =
                {"Döviz", "Döviz Alış", "Döviz Satış", "Efektif Alış", "Efektif Satış", "Son Güncelleme"};
        private List<CurrencyRate> data = new java.util.ArrayList<>();

        RateTableModel(boolean editable) { }   // parametre uyumluluk için

        void setData(List<CurrencyRate> rates) { this.data = rates; fireTableDataChanged(); }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int c) { return columns[c]; }
        @Override public boolean isCellEditable(int row, int col) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            CurrencyRate r = data.get(row);
            switch (col) {
                case 0: return r.getCurrency();
                case 1: return r.getBuyRate();
                case 2: return r.getSellRate();
                case 3: return r.getEffectiveBuy();
                case 4: return r.getEffectiveSell();
                case 5: return r.getUpdatedAt();
                default: return "";
            }
        }
    }
}
