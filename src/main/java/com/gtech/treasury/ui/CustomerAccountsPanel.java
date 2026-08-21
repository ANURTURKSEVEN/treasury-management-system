package com.gtech.treasury.ui;

import com.gtech.treasury.dao.AccountDAO;
import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.dao.ErrorLogDAO;
import com.gtech.treasury.dao.RateDAO;
import com.gtech.treasury.model.Account;
import com.gtech.treasury.model.ActivityLog;
import com.gtech.treasury.model.CurrencyRate;
import com.gtech.treasury.model.Customer;
import com.gtech.treasury.util.PdfService;
import com.gtech.treasury.util.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Müşteri "Hesaplarım" — modern bankacılık kart görünümü.
 * Kartlar: hesap türü + bakiye. Karta tıklayınca hesap detayı ve hareketleri açılır.
 * (Sistemsel alanlar müşteriye gösterilmez; veriler DB'de saklanmaya devam eder.)
 */
public class CustomerAccountsPanel extends JPanel {

    private static final Map<String, String> CUR_ICON = Map.of(
            "TRY", "₺", "USD", "$", "EUR", "€", "GBP", "£");
    private static final Map<String, String> ACT_LABEL = Map.of(
            "TRANSFER", "Para Transferi", "ACCOUNT_DEPOSIT", "Para Yatırma",
            "ACCOUNT_WITHDRAW", "Para Çekme", "SPOT_BUY", "Döviz Alış", "SPOT_SELL", "Döviz Satış");

    private final AccountDAO accountDAO = new AccountDAO();
    private final RateDAO rateDAO = new RateDAO();
    private final ActivityLogDAO activityDAO = new ActivityLogDAO();
    private final Customer customer;

    private final CardLayout cards = new CardLayout();
    private final JPanel container = new JPanel(cards);

    public CustomerAccountsPanel(Customer customer) {
        this.customer = customer;
        setLayout(new BorderLayout());
        container.add(buildList(), "list");
        add(container, BorderLayout.CENTER);
        cards.show(container, "list");

        // Sekmeye her dönüşte (görünür olunca) bakiyeleri tazele
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) { reload(); }
        });
    }

    /** Liste kartını güncel bakiyelerle yeniden kurar. */
    private void reload() {
        container.removeAll();
        container.add(buildList(), "list");
        cards.show(container, "list");
        container.revalidate();
        container.repaint();
    }

    // ================= LİSTE (kartlar) =================
    private JComponent buildList() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(new Color(0xF0F2F5));
        root.setBorder(new EmptyBorder(18, 20, 18, 20));

        List<Account> accounts = accountDAO.getByCustomer(customer.getCustomerId());
        double totalTry = 0;
        for (Account a : accounts) totalTry += toTry(a.getBalance(), a.getCurrency());

        // Üst: Net varlık
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(UITheme.PRIMARY);
        top.setBorder(new EmptyBorder(16, 20, 16, 20));
        JLabel t1 = new JLabel("Net Varlığınız");
        t1.setForeground(new Color(0xC7D2FE));
        JLabel t2 = new JLabel(String.format("%,.2f ₺", totalTry));
        t2.setForeground(Color.WHITE);
        t2.setFont(t2.getFont().deriveFont(Font.BOLD, 26f));
        JPanel tb = new JPanel(); tb.setOpaque(false);
        tb.setLayout(new BoxLayout(tb, BoxLayout.Y_AXIS));
        tb.add(t1); tb.add(Box.createVerticalStrut(4)); tb.add(t2);
        top.add(tb, BorderLayout.WEST);
        root.add(top, BorderLayout.NORTH);

        // Kart ızgarası
        JPanel grid = new JPanel(new GridLayout(0, 3, 14, 14));
        grid.setOpaque(false);
        if (accounts.isEmpty()) {
            JLabel empty = new JLabel("Henüz hesabınız yok. Şube personeli hesap açabilir.");
            empty.setBorder(new EmptyBorder(20, 4, 4, 4));
            root.add(empty, BorderLayout.CENTER);
        } else {
            for (Account a : accounts) grid.add(accountCard(a));
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false);
            wrap.add(grid, BorderLayout.NORTH);
            JScrollPane sp = new JScrollPane(wrap);
            sp.setBorder(null);
            sp.getViewport().setBackground(new Color(0xF0F2F5));
            root.add(sp, BorderLayout.CENTER);
        }
        return root;
    }

    private JComponent accountCard(Account a) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)),
                new EmptyBorder(14, 16, 14, 16)));
        card.setPreferredSize(new Dimension(230, 120));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel type = new JLabel(a.getAccountType() + " Hesabı");
        type.setFont(type.getFont().deriveFont(Font.BOLD, 13f));
        JLabel no = new JLabel(String.valueOf(a.getAccountNo()));
        no.setForeground(new Color(0x6B7280));
        no.setFont(no.getFont().deriveFont(11f));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(type); titles.add(no);
        head.add(titles, BorderLayout.WEST);
        JLabel icon = new JLabel(CUR_ICON.getOrDefault(a.getCurrency(), "🏦"));
        icon.setFont(icon.getFont().deriveFont(20f));
        head.add(icon, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

        JLabel bal = new JLabel(String.format("%,.2f %s", a.getBalance(), a.getCurrency()));
        bal.setFont(bal.getFont().deriveFont(Font.BOLD, 20f));
        bal.setForeground(a.getBalance() < 0 ? new Color(0xC5221F) : new Color(0x1E8E3E));
        JPanel balWrap = new JPanel(new BorderLayout());
        balWrap.setOpaque(false);
        JLabel balTitle = new JLabel("Bakiye");
        balTitle.setForeground(new Color(0x9CA3AF));
        balTitle.setFont(balTitle.getFont().deriveFont(11f));
        balWrap.add(balTitle, BorderLayout.NORTH);
        balWrap.add(bal, BorderLayout.CENTER);
        card.add(balWrap, BorderLayout.CENTER);

        JLabel more = new JLabel("Detay için tıklayın →");
        more.setForeground(UITheme.PRIMARY);
        more.setFont(more.getFont().deriveFont(11f));
        card.add(more, BorderLayout.SOUTH);

        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { showDetail(a); }
        });
        return card;
    }

    // ================= DETAY =================
    private JComponent detailCard;   // önceki detay birikmesin
    private void showDetail(Account a) {
        if (detailCard != null) container.remove(detailCard);
        detailCard = buildDetail(a);
        container.add(detailCard, "detail");
        cards.show(container, "detail");
    }

    private JComponent buildDetail(Account a) {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(new Color(0xF0F2F5));
        root.setBorder(new EmptyBorder(18, 20, 18, 20));

        JButton back = new JButton("←  Hesaplarım");
        back.addActionListener(e -> cards.show(container, "list"));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setOpaque(false);
        top.add(back);
        root.add(top, BorderLayout.NORTH);

        // Hesap özeti
        JPanel summary = new JPanel(new GridBagLayout());
        summary.setBackground(Color.WHITE);
        summary.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE5E7EB)),
                new EmptyBorder(18, 24, 18, 24)));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 8, 5, 8);
        g.anchor = GridBagConstraints.WEST;

        JLabel bal = new JLabel(String.format("%,.2f %s", a.getBalance(), a.getCurrency()));
        bal.setFont(bal.getFont().deriveFont(Font.BOLD, 28f));
        bal.setForeground(a.getBalance() < 0 ? new Color(0xC5221F) : new Color(0x1E8E3E));
        g.gridx = 0; g.gridy = 0; g.gridwidth = 2; summary.add(bal, g);
        g.gridwidth = 1;
        int r = 1;
        r = info(summary, g, r, "Hesap No:", String.valueOf(a.getAccountNo()));
        r = info(summary, g, r, "Hesap Türü:", a.getAccountType() + " Hesabı");
        r = info(summary, g, r, "Döviz:", a.getCurrency());
        r = info(summary, g, r, "Açılış:", a.getOpenedDate() + " " + a.getOpenedTime());
        r = info(summary, g, r, "Durum:", a.getStatus() == 1 ? "Açık" : "Kapalı");

        // Hesap hareketleri
        JPanel movePanel = new JPanel(new BorderLayout(0, 6));
        movePanel.setOpaque(false);

        JLabel mt = new JLabel("Hesap Hareketleri  (satıra çift tıklayarak dekontu görün)");
        mt.setFont(mt.getFont().deriveFont(Font.BOLD, 15f));

        // Tarih aralığı + PDF ekstre araç çubuğu
        List<ActivityLog> hareketler = activityDAO.byAccountNo(a.getAccountNo());
        com.gtech.treasury.util.DatePicker basField =
                new com.gtech.treasury.util.DatePicker(LocalDate.now().withDayOfMonth(1).toString());
        com.gtech.treasury.util.DatePicker bitField =
                new com.gtech.treasury.util.DatePicker(LocalDate.now().toString());
        JButton pdfBtn = new JButton("PDF Ekstre İndir");
        pdfBtn.addActionListener(e -> exportEkstrePdf(a, basField.getText(), bitField.getText(), hareketler));

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        tools.setOpaque(false);
        tools.add(new JLabel("Başlangıç:")); tools.add(basField);
        tools.add(new JLabel("Bitiş:"));     tools.add(bitField);
        tools.add(pdfBtn);

        JPanel moveHead = new JPanel(new BorderLayout());
        moveHead.setOpaque(false);
        moveHead.add(mt, BorderLayout.WEST);
        moveHead.add(tools, BorderLayout.EAST);
        movePanel.add(moveHead, BorderLayout.NORTH);

        MoveModel moveModel = new MoveModel(hareketler);
        JTable moves = new JTable(moveModel);
        moves.setRowHeight(26);
        moves.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = moves.getSelectedRow();
                    if (row >= 0) showReceipt(moveModel.getAt(row));
                }
            }
        });
        JScrollPane msp = new JScrollPane(moves);
        msp.setPreferredSize(new Dimension(600, 190));
        movePanel.add(msp, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.add(summary, BorderLayout.NORTH);
        center.add(movePanel, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);
        return root;
    }

    /** Bir hesap hareketi için dekont penceresi. */
    private void showReceipt(ActivityLog a) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(new EmptyBorder(8, 8, 8, 8));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 8, 5, 8);
        g.anchor = GridBagConstraints.WEST;

        int r = 0;
        JLabel header = new JLabel("İşlem Dekontu");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 18f));
        g.gridx = 0; g.gridy = r++; g.gridwidth = 2; card.add(header, g);
        g.gridwidth = 1;

        r = recRow(card, g, r, "İşlem No:", String.valueOf(a.getId()));
        r = recRow(card, g, r, "Tarih:", a.getDatePart());
        r = recRow(card, g, r, "Saat:", a.getTimePart());
        r = recRow(card, g, r, "İşlem:", ACT_LABEL.getOrDefault(a.getActionType(), a.getActionType()));
        r = recRow(card, g, r, "Tutar:", a.getAmount() > 0
                ? String.format("%,.2f %s", a.getAmount(), a.getCurrency() == null ? "" : a.getCurrency()) : "-");
        r = recRow(card, g, r, "Yapan:", a.getUsername());
        r = recRow(card, g, r, "Açıklama:", a.getDescription());

        g.gridx = 0; g.gridy = r++; g.gridwidth = 2;
        card.add(new JSeparator(), g);
        JLabel dt = new JLabel("Detaylar");
        dt.setFont(dt.getFont().deriveFont(Font.BOLD));
        g.gridy = r++; card.add(dt, g);
        JTextArea details = new JTextArea(a.getDetails() == null ? "-" : a.getDetails(), 4, 40);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setBackground(new Color(0xF3F4F6));
        g.gridy = r++; card.add(new JScrollPane(details), g);

        Object[] secenekler = {"PDF İndir", "Kapat"};
        int secim = JOptionPane.showOptionDialog(this, card,
                "Dekont - İşlem #" + a.getId(),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, secenekler, secenekler[1]);

        if (secim == 0) { // "PDF İndir"
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("dekont_" + a.getId() + ".pdf"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            try {
                PdfService.dekontUret(fc.getSelectedFile(),
                        ACT_LABEL.getOrDefault(a.getActionType(), a.getActionType()),
                        customer.getCustomerNo(),
                        a.getAmount(),
                        a.getCurrency() == null ? "" : a.getCurrency(),
                        a.getDescription(),
                        a.getDatePart() + " " + a.getTimePart());
                JOptionPane.showMessageDialog(this, "Dekont kaydedildi.",
                        "Başarılı", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                ErrorLogDAO.log(ex, "Dekont PDF");
                JOptionPane.showMessageDialog(this, "PDF üretilemedi: " + ex.getMessage(),
                        "Hata", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /** Hesabın hareketlerini tarih aralığına göre süzüp PDF ekstre üretir. */
    private void exportEkstrePdf(Account a, String bas, String bit, List<ActivityLog> tumHareketler) {
        // Tarih süzme (getDatePart "yyyy-MM-dd" → sözlük sırası tarih sırasıyla aynıdır)
        List<ActivityLog> secili = new ArrayList<>();
        for (ActivityLog h : tumHareketler) {
            String d = h.getDatePart();
            if (bas != null && !bas.isBlank() && d.compareTo(bas.trim()) < 0) continue;
            if (bit != null && !bit.isBlank() && d.compareTo(bit.trim()) > 0) continue;
            secili.add(h);
        }

        if (secili.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Seçilen tarih aralığında hareket bulunamadı.",
                    "Bilgi", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("ekstre_" + a.getAccountNo() + "_" + bas + "_" + bit + ".pdf"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File out = fc.getSelectedFile();

        try {
            PdfService.ekstreUret(out, customer, a.getAccountNo(), bas, bit, secili);
            JOptionPane.showMessageDialog(this,
                    "Ekstre kaydedildi:\n" + out.getAbsolutePath(),
                    "Başarılı", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            ErrorLogDAO.log(ex, "Ekstre PDF");
            JOptionPane.showMessageDialog(this,
                    "PDF üretilemedi: " + ex.getMessage(),
                    "Hata", JOptionPane.ERROR_MESSAGE);
        }
    }

    private int recRow(JPanel card, GridBagConstraints g, int row, String label, String value) {
        g.gridx = 0; g.gridy = row;
        JLabel l = new JLabel(label); l.setFont(l.getFont().deriveFont(Font.BOLD));
        card.add(l, g);
        g.gridx = 1;
        card.add(new JLabel(value == null ? "-" : value), g);
        return row + 1;
    }

    private int info(JPanel p, GridBagConstraints g, int row, String label, String value) {
        g.gridx = 0; g.gridy = row;
        JLabel l = new JLabel(label); l.setFont(l.getFont().deriveFont(Font.BOLD));
        p.add(l, g);
        g.gridx = 1;
        p.add(new JLabel(value == null ? "-" : value), g);
        return row + 1;
    }

    private double toTry(double amount, String currency) {
        if ("TRY".equals(currency)) return amount;
        CurrencyRate rr = rateDAO.getByCurrency(currency);
        return rr == null ? amount : amount * rr.getBuyRate();
    }

    // ---- Hareket tablosu ----
    private static class MoveModel extends AbstractTableModel {
        private final String[] cols = {"Tarih", "İşlem", "Tutar", "Açıklama"};
        private final List<ActivityLog> data;
        MoveModel(List<ActivityLog> data) { this.data = data; }
        ActivityLog getAt(int row) { return data.get(row); }
        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Object getValueAt(int row, int col) {
            ActivityLog a = data.get(row);
            switch (col) {
                case 0: return a.getDatePart() + " " + a.getTimePart();
                case 1: return ACT_LABEL.getOrDefault(a.getActionType(), a.getActionType());
                case 2: return a.getAmount() > 0
                        ? String.format("%,.2f %s", a.getAmount(), a.getCurrency() == null ? "" : a.getCurrency()) : "-";
                case 3: return a.getDescription();
                default: return "";
            }
        }
    }
}
