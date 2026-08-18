package com.gtech.treasury.ui;

import com.gtech.treasury.dao.PermissionDAO;
import com.gtech.treasury.model.Customer;
import com.gtech.treasury.model.Screen;
import com.gtech.treasury.util.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Müşteri panosu — sol tarafta AĞAÇ menü (gruplar ▶/▼ ile açılır), üstte SEKMELER.
 * Yapı:
 *   Bilgilerim
 *     ├ Genel Bilgiler
 *     ├ Hesaplarım (bakiyeler)
 *     └ Ayarlar
 *         └ Bilgileri Düzenle
 *   İşlemler (Para Transferi, Spot FX) | Krediler | Raporlama  — yetkiye göre
 */
public class CustomerDashboardFrame extends JFrame {

    private static final Color SIDEBAR_BG = new Color(0x1F2A44);
    private static final Color SIDEBAR_HOVER = new Color(0x2C3A5E);

    private final Customer customer;
    private final PermissionDAO permissionDAO = new PermissionDAO();

    private final JTabbedPane tabs = new JTabbedPane();
    private final Map<String, Component> openTabs = new HashMap<>();
    private final Map<String, String> titles = new HashMap<>();

    public CustomerDashboardFrame(Customer customer) {
        this.customer = customer;

        titles.put("home", "Genel Bilgiler");
        titles.put("MY_ACCOUNTS", "Hesaplarım");
        titles.put("MY_INFO", "Bilgileri Düzenle");
        titles.put("TRANSFER", "🔁 Para Transferi");
        titles.put("DEPOSIT", "💵 Para Yatır / Çek");
        titles.put("CASHFLOW", "💵 Nakit Akışım");
        titles.put("LENDING", "💰 Kredi (Lending)");
        titles.put("BORROWING", "🏦 Vadeli Mevduat");
        titles.put("SPOT", "💱 Spot FX");
        titles.put("FX_TRADE", "💱 Spot FX İşlemleri");
        titles.put("FX_WATCH", "📈 Kur Gözlem");
        titles.put("BORROWING", "📥 Borrowing");
        titles.put("LENDING", "📤 Lending");
        titles.put("REPORTS", "📊 Raporlar");

        setTitle("Müşteri Paneli - " + customer.getCustomerName());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        UITheme.maximize(this);
        setLayout(new BorderLayout());

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildSidebar(), BorderLayout.WEST);
        add(tabs, BorderLayout.CENTER);

        openTab("home", "🏠 Bilgilerim");

        // Bildirimler artık sağ üstteki ÇAN üzerinden yönetilir (eski açılır pencere kaldırıldı).
        SwingUtilities.invokeLater(this::refreshBell);
    }

    /** Okunmamış bildirimleri (ör. hesabına para gelmesi) girişte gösterir. */
    private void showIncomingNotifications() {
        com.gtech.treasury.dao.NotificationDAO dao = new com.gtech.treasury.dao.NotificationDAO();
        java.util.List<com.gtech.treasury.model.Notification> list = dao.unread(customer.getCustomerNo());
        if (list.isEmpty()) return;

        StringBuilder summary = new StringBuilder("🔔 " + list.size() + " yeni bildiriminiz var:\n\n");
        for (com.gtech.treasury.model.Notification n : list) {
            summary.append("•  ").append(n.getTitle()).append("\n");
        }
        Object[] options = {"Detay", "Kapat"};
        int r = JOptionPane.showOptionDialog(this, summary.toString(), "Bildirimler",
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);

        if (r == 0) {   // Detay
            StringBuilder d = new StringBuilder();
            for (com.gtech.treasury.model.Notification n : list) {
                d.append(n.getTitle()).append("\n")
                 .append(n.getDetail() == null ? "" : n.getDetail()).append("\n")
                 .append("Tarih: ").append(n.getCreatedAt()).append("\n\n");
            }
            JTextArea ta = new JTextArea(d.toString(), 12, 48);
            ta.setEditable(false);
            ta.setLineWrap(true);
            ta.setWrapStyleWord(true);
            ta.setCaretPosition(0);
            JOptionPane.showMessageDialog(this, new JScrollPane(ta),
                    "Bildirim Detayları", JOptionPane.PLAIN_MESSAGE);
        }
        dao.markAllRead(customer.getCustomerNo());
    }

    private JButton bellButton;

    private JComponent buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(UITheme.PRIMARY);
        bar.setBorder(new EmptyBorder(10, 20, 10, 20));
        JLabel brand = new JLabel("Müşteri Paneli",
                com.gtech.treasury.util.IconLoader.get("brand", 22), SwingConstants.LEFT);
        brand.setIconTextGap(10);
        brand.setForeground(Color.WHITE);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 16f));
        bar.add(brand, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        right.setOpaque(false);

        bellButton = new JButton("Bildirimler");
        bellButton.setIcon(com.gtech.treasury.util.IconLoader.get("bell", 18));
        bellButton.setToolTipText("Bildirimler");
        bellButton.setFocusable(false);
        bellButton.setFont(bellButton.getFont().deriveFont(Font.BOLD, 13f));
        bellButton.addActionListener(e -> openNotificationCenter());
        right.add(bellButton);

        JLabel who = new JLabel(customer.getCustomerName() + " " + customer.getSurname()
                + "  •  No: " + customer.getCustomerNo());
        who.setForeground(Color.WHITE);
        right.add(who);

        bar.add(right, BorderLayout.EAST);
        refreshBell();
        return bar;
    }

    /** Çan rozetini okunmamış sayısına göre günceller. */
    private void refreshBell() {
        if (bellButton == null) return;
        int n = new com.gtech.treasury.dao.NotificationDAO().unreadCount(customer.getCustomerNo());
        bellButton.setText(n > 0 ? "Bildirimler (" + n + ")" : "Bildirimler");
    }

    /** Bildirim merkezi: geçmiş bildirimler, okundu/okunmadı, detay ve İtiraz Et. */
    private void openNotificationCenter() {
        com.gtech.treasury.dao.NotificationDAO dao = new com.gtech.treasury.dao.NotificationDAO();
        java.util.List<com.gtech.treasury.model.Notification> items = dao.all(customer.getCustomerNo());

        DefaultListModel<com.gtech.treasury.model.Notification> lm = new DefaultListModel<>();
        for (com.gtech.treasury.model.Notification n : items) lm.addElement(n);

        JList<com.gtech.treasury.model.Notification> list = new JList<>(lm);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                com.gtech.treasury.model.Notification n = (com.gtech.treasury.model.Notification) v;
                setText((n.isRead() ? "○ " : "● ") + n.getTitle() + "   —   " + n.getCreatedAt());
                if (!n.isRead() && !s) setFont(getFont().deriveFont(Font.BOLD));
                return this;
            }
        });

        JTextArea detail = new JTextArea(6, 40);
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);

        JButton markRead = new JButton("Okundu");
        JButton dispute = new JButton("İtiraz Et");
        JButton close = new JButton("Kapat");
        markRead.setEnabled(false); dispute.setEnabled(false);

        list.addListSelectionListener(e -> {
            com.gtech.treasury.model.Notification n = list.getSelectedValue();
            boolean sel = n != null;
            markRead.setEnabled(sel && !n.isRead());
            dispute.setEnabled(sel);
            detail.setText(sel ? (n.getDetail() == null ? "" : n.getDetail()) : "");
        });
        if (!lm.isEmpty()) list.setSelectedIndex(0);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        south.add(close); south.add(markRead); south.add(dispute);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel title = new JLabel("Bildirimler");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        content.add(title, BorderLayout.NORTH);
        JScrollPane lsp = new JScrollPane(list); lsp.setPreferredSize(new Dimension(560, 220));
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.add(lsp, BorderLayout.CENTER);
        body.add(new JScrollPane(detail), BorderLayout.SOUTH);
        content.add(body, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);

        JDialog dlg = new JDialog(this, "Bildirimler", true);
        dlg.setContentPane(content);
        dlg.pack();
        dlg.setLocationRelativeTo(this);

        close.addActionListener(e -> dlg.dispose());
        markRead.addActionListener(e -> {
            com.gtech.treasury.model.Notification n = list.getSelectedValue();
            if (n == null) return;
            dao.markRead(n.getId());
            n.setRead(true);
            list.repaint();
            markRead.setEnabled(false);
            refreshBell();
        });
        dispute.addActionListener(e -> {
            com.gtech.treasury.model.Notification n = list.getSelectedValue();
            if (n == null) return;
            String reason = (String) JOptionPane.showInputDialog(dlg,
                    "Bu işlemi kendiniz yapmadığınızı düşünüyorsanız gerekçenizi yazın.\n"
                            + "İtirazınız bankaya iletilecek ve değerlendirilecektir:",
                    "İtiraz Et — " + n.getTitle(), JOptionPane.PLAIN_MESSAGE, null, null, "");
            if (reason == null || reason.isBlank()) return;
            int did = new com.gtech.treasury.dao.DisputeDAO()
                    .create(customer.getCustomerNo(), n.getTitle(), reason.trim());
            if (did > 0) {
                dao.markRead(n.getId());
                refreshBell();
                JOptionPane.showMessageDialog(dlg,
                        "İtirazınız (No: " + did + ") bankaya iletildi.\n"
                                + "Sonuç bildirim olarak size ulaşacaktır.",
                        "İtiraz Alındı", JOptionPane.INFORMATION_MESSAGE);
                dlg.dispose();
            } else {
                JOptionPane.showMessageDialog(dlg, "İtiraz oluşturulamadı.", "Hata", JOptionPane.ERROR_MESSAGE);
            }
        });

        dlg.setVisible(true);
        refreshBell();
    }

    // ================= AĞAÇ MENÜ =================
    private static class MenuItem {
        final String key, label;
        MenuItem(String key, String label) { this.key = key; this.label = label; }
        @Override public String toString() { return label; }
    }

    private JComponent buildSidebar() {
        Set<String> allowed = new HashSet<>();
        for (Screen s : permissionDAO.getAllowedScreens("CUSTOMER")) allowed.add(s.getScreenKey());

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");

        // Bilgilerim (kendi verileri — her zaman erişilebilir)
        DefaultMutableTreeNode bilgi = group("👤 Bilgilerim",
                leaf("Genel Bilgiler", "home", allowed),
                leaf("Hesaplarım", "MY_ACCOUNTS", allowed),
                group("⚙️ Ayarlar", leaf("Bilgileri Düzenle", "MY_INFO", allowed)));
        root.add(bilgi);

        DefaultMutableTreeNode islemler = group("🔁 İşlemler",
                leaf("Para Transferi", "TRANSFER", allowed),
                new DefaultMutableTreeNode(new MenuItem("DEPOSIT", "Para Yatır / Çek")),
                new DefaultMutableTreeNode(new MenuItem("CASHFLOW", "Nakit Akışım")));
        if (allowed.contains("SPOT")) {   // Kur işlemleri: alt grup (accordion)
            islemler.add(group("💱 Kur İşlemleri",
                    new DefaultMutableTreeNode(new MenuItem("FX_TRADE", "Spot FX İşlemleri")),
                    new DefaultMutableTreeNode(new MenuItem("FX_WATCH", "Kur Gözlem"))));
        }
        addGroup(root, islemler);
        DefaultMutableTreeNode krediMevduat = group("💰 Krediler & Mevduat");
        if (allowed.contains("BORROWING")) {
            krediMevduat.add(group("🏦 Vadeli Mevduat",
                    new DefaultMutableTreeNode(new MenuItem("BORROWING_APPLY", "Başvuru / Bekleyen")),
                    new DefaultMutableTreeNode(new MenuItem("BORROWING_ACTIVE", "Aktif Mevduatlarım")),
                    new DefaultMutableTreeNode(new MenuItem("BORROWING_CLOSED", "Kapananlar")),
                    new DefaultMutableTreeNode(new MenuItem("BORROWING_REJECTED", "Reddedilenler"))));
        }
        if (allowed.contains("LENDING")) {
            krediMevduat.add(group("💰 Kredi",
                    new DefaultMutableTreeNode(new MenuItem("LENDING_APPLY", "Başvuru / Bekleyen")),
                    new DefaultMutableTreeNode(new MenuItem("LENDING_ACTIVE", "Aktif Kredilerim")),
                    new DefaultMutableTreeNode(new MenuItem("LENDING_CLOSED", "Ödenen / Kapanan")),
                    new DefaultMutableTreeNode(new MenuItem("LENDING_REJECTED", "Reddedilenler"))));
        }
        addGroup(root, krediMevduat);

        return treeSidebar(root);
    }

    private DefaultMutableTreeNode leaf(String label, String key, Set<String> allowed) {
        // home / MY_ACCOUNTS / MY_INFO her zaman erişilebilir; gerisi yetkiye bağlı
        boolean always = "home".equals(key) || "MY_ACCOUNTS".equals(key) || "MY_INFO".equals(key);
        if (!always && key != null && !allowed.contains(key)) return null;
        return new DefaultMutableTreeNode(new MenuItem(key, label));
    }

    private DefaultMutableTreeNode group(String label, DefaultMutableTreeNode... children) {
        DefaultMutableTreeNode g = new DefaultMutableTreeNode(new MenuItem(null, label));
        for (DefaultMutableTreeNode c : children) if (c != null) g.add(c);
        return g;
    }

    private void addGroup(DefaultMutableTreeNode root, DefaultMutableTreeNode g) {
        if (g.getChildCount() > 0) root.add(g);
    }

    private JComponent treeSidebar(DefaultMutableTreeNode root) {
        JTree tree = new JTree(root);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(30);
        tree.setBackground(SIDEBAR_BG);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setFont(tree.getFont().deriveFont(14f));

        DefaultTreeCellRenderer r = new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree t, Object value, boolean sel,
                    boolean exp, boolean lf, int row, boolean focus) {
                super.getTreeCellRendererComponent(t, value, sel, exp, lf, row, focus);
                Object uo = (value instanceof DefaultMutableTreeNode)
                        ? ((DefaultMutableTreeNode) value).getUserObject() : null;
                if (uo instanceof MenuItem) {
                    MenuItem mi = (MenuItem) uo;
                    String iconName = (mi.key != null)
                            ? com.gtech.treasury.util.IconLoader.forKey(mi.key)
                            : groupIcon(mi.label);
                    setIcon(com.gtech.treasury.util.IconLoader.get(iconName, 18));
                } else {
                    setIcon(null);
                }
                setText(com.gtech.treasury.util.IconLoader.stripEmoji(getText()));
                return this;
            }
        };
        r.setBackgroundNonSelectionColor(SIDEBAR_BG);
        r.setTextNonSelectionColor(Color.WHITE);
        r.setBackgroundSelectionColor(SIDEBAR_HOVER);
        r.setTextSelectionColor(Color.WHITE);
        r.setBorderSelectionColor(SIDEBAR_HOVER);
        r.setLeafIcon(null); r.setClosedIcon(null); r.setOpenIcon(null);
        tree.setCellRenderer(r);

        tree.addTreeSelectionListener(e -> {
            Object n = tree.getLastSelectedPathComponent();
            if (n instanceof DefaultMutableTreeNode
                    && ((DefaultMutableTreeNode) n).getUserObject() instanceof MenuItem) {
                MenuItem mi = (MenuItem) ((DefaultMutableTreeNode) n).getUserObject();
                if (mi.key != null) openTab(mi.key, mi.label);
            }
        });
        // Menü kapalı başlar; kullanıcı gruba tıkladıkça açılır (accordion).

        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(SIDEBAR_BG);
        sidebar.setPreferredSize(new Dimension(240, 0));
        sidebar.setBorder(new EmptyBorder(10, 6, 10, 6));
        JScrollPane sp = new JScrollPane(tree);
        sp.setBorder(null);
        sp.getViewport().setBackground(SIDEBAR_BG);
        sidebar.add(sp, BorderLayout.CENTER);

        JButton logout = new JButton("Çıkış");
        logout.setIcon(com.gtech.treasury.util.IconLoader.get("logout", 18));
        logout.setIconTextGap(10);
        logout.setForeground(Color.WHITE);
        logout.setBackground(SIDEBAR_BG);
        logout.setBorderPainted(false);
        logout.setFocusPainted(false);
        logout.setHorizontalAlignment(SwingConstants.LEFT);
        logout.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });
        sidebar.add(logout, BorderLayout.SOUTH);
        return sidebar;
    }

    // ================= SEKMELER =================
    private void openScreen(String key) {
        openTab(key, titles.getOrDefault(key, key));
    }

    private static String groupIcon(String label) {
        if (label == null) return null;
        if (label.contains("Bilgi")) return "customers";
        if (label.contains("Ayar")) return "settings";
        if (label.contains("Kur")) return "fx";
        if (label.contains("İşlem")) return "transactions";
        if (label.contains("Kredi") || label.contains("Mevduat")) return "lending";
        return null;
    }

    private void openTab(String key, String title) {
        title = com.gtech.treasury.util.IconLoader.stripEmoji(title);
        Component c = openTabs.get(key);
        if (c == null) {
            c = componentFor(key);
            openTabs.put(key, c);
            tabs.addTab(title, c);
            int idx = tabs.indexOfComponent(c);
            tabs.setTabComponentAt(idx, tabHeader(title, key, c));
        }
        tabs.setSelectedComponent(c);
    }

    private JComponent tabHeader(String title, String key, Component comp) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        p.setOpaque(false);
        p.add(new JLabel(title));
        JButton close = new JButton("×");
        close.setBorder(new EmptyBorder(0, 4, 0, 0));
        close.setContentAreaFilled(false);
        close.setFocusPainted(false);
        close.setMargin(new Insets(0, 0, 0, 0));
        close.setForeground(new Color(0x888888));
        close.addActionListener(e -> { tabs.remove(comp); openTabs.remove(key); });
        p.add(close);
        return p;
    }

    private JComponent componentFor(String key) {
        switch (key) {
            case "home":        return new CustomerHomePanel(customer, this::openScreen);
            case "MY_ACCOUNTS": return new CustomerAccountsPanel(customer);
            case "MY_INFO":     return new CustomerInfoPanel(customer);
            case "TRANSFER":    return new TransferPanel(customer);
            case "DEPOSIT":     return new DepositWithdrawPanel(customer);
            case "CASHFLOW":    return new CashflowPanel(customer);
            case "LENDING":     return new LendingPanel(customer);
            case "BORROWING":   return new BorrowingPanel(customer);
            case "LENDING_APPLY":     return new LendingPanel(customer, LendingPanel.V_APPLY);
            case "LENDING_ACTIVE":    return new LendingPanel(customer, LendingPanel.V_ACTIVE);
            case "LENDING_CLOSED":    return new LendingPanel(customer, LendingPanel.V_CLOSED);
            case "LENDING_REJECTED":  return new LendingPanel(customer, LendingPanel.V_REJECTED);
            case "BORROWING_APPLY":    return new BorrowingPanel(customer, BorrowingPanel.V_APPLY);
            case "BORROWING_ACTIVE":   return new BorrowingPanel(customer, BorrowingPanel.V_ACTIVE);
            case "BORROWING_CLOSED":   return new BorrowingPanel(customer, BorrowingPanel.V_CLOSED);
            case "BORROWING_REJECTED": return new BorrowingPanel(customer, BorrowingPanel.V_REJECTED);
            case "SPOT":        return new SpotPanel(customer);
            case "FX_TRADE":    return new SpotTradePanel(true, customer, null);   // müşteri kendi adına al/sat
            case "FX_WATCH":    return new SpotRatePanel(false, null);             // salt gözlem
            case "REPORTS":     return new CustomerReportsPanel(customer);
            default:            return placeholder(titles.getOrDefault(key, key),
                                     "İşlemleriniz yakında burada listelenecek.");
        }
    }

    private JComponent placeholder(String title, String message) {
        JPanel panel = new JPanel(new GridBagLayout());
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 24f));
        t.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel m = new JLabel(message);
        m.setForeground(new Color(0x6B7280));
        m.setAlignmentX(Component.CENTER_ALIGNMENT);
        box.add(t);
        box.add(Box.createVerticalStrut(8));
        box.add(m);
        panel.add(box);
        return panel;
    }
}
