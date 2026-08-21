package com.gtech.treasury.ui;

import com.gtech.treasury.dao.PermissionDAO;
import com.gtech.treasury.model.Message;
import com.gtech.treasury.model.Screen;
import com.gtech.treasury.model.User;
import com.gtech.treasury.util.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Personel ana panosu — sol menü + üstte SEKMELER (JTabbedPane).
 * Bir ekran açılınca yeni sekme olur; diğer ekranlar kapanmaz.
 * Aynı ekran ikinci kez açılmaya çalışılırsa var olan sekmeye geçilir.
 * Sekmeler "×" ile kapatılabilir.
 */
public class DashboardFrame extends JFrame {

    private static final Color SIDEBAR_BG = new Color(0x1F2A44);
    private static final Color SIDEBAR_HOVER = new Color(0x2C3A5E);
    private static final Color STATUS_BG = new Color(0xE9EDF3);

    private static final Map<String, String> ICONS = Map.ofEntries(
            Map.entry("CUSTOMER", "👥"), Map.entry("ACCOUNTS", "🏦"),
            Map.entry("TRANSFER", "🔁"),
            Map.entry("SPOT", "💱"), Map.entry("BORROWING", "📥"),
            Map.entry("LENDING", "📤"), Map.entry("REPORTS", "📊"),
            Map.entry("USER_MGMT", "⚙️"), Map.entry("ROLE_PERM", "🔐"),
            Map.entry("ERROR_LOG", "🐞"));

    private final User currentUser;
    private final PermissionDAO permissionDAO = new PermissionDAO();

    private final JTabbedPane tabs = new JTabbedPane();
    private final Map<String, Component> openTabs = new HashMap<>();   // açık sekmeler (key -> bileşen)

    public DashboardFrame(User currentUser) {
        this.currentUser = currentUser;

        setTitle("Treasury Management System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        UITheme.maximize(this);
        setLayout(new BorderLayout());

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildSidebar(), BorderLayout.WEST);
        add(tabs, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        openTab("home", "🏠 Anasayfa");   // açılışta anasayfa sekmesi
        // Not: Girişteki "bekleyen kredi" açılır uyarısı kaldırıldı; artık her şey Gelen Kutusu'na düşüyor.
    }

    private void checkPendingLoans() {
        int n = new com.gtech.treasury.dao.LendingDAO().pendingCount();
        if (n <= 0) return;
        int r = JOptionPane.showConfirmDialog(this,
                "🔔 " + n + " adet bekleyen kredi başvurusu var.\nKrediler ekranından değerlendirmek ister misiniz?",
                "Kredi Başvuruları", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (r == JOptionPane.YES_OPTION) openTab("LENDING", "💰 Lending");
    }

    // ================= ÜST BAR =================
    private JButton bellButton;

    private boolean handlesDisputes() {
        return "ADMIN".equals(currentUser.getRole()) || "TRADER".equals(currentUser.getRole());
    }

    private JComponent buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(UITheme.PRIMARY);
        bar.setBorder(new EmptyBorder(10, 20, 10, 20));
        JLabel brand = new JLabel("TREASURY MANAGEMENT SYSTEM",
                com.gtech.treasury.util.IconLoader.get("brand", 22), SwingConstants.LEFT);
        brand.setIconTextGap(10);
        brand.setForeground(Color.WHITE);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 16f));
        bar.add(brand, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        right.setOpaque(false);
        // Tüm banka kullanıcıları (admin/trader/viewer) gelen kutusunu görür
        bellButton = new JButton();
        bellButton.setIcon(com.gtech.treasury.util.IconLoader.get("bell", 18));
        bellButton.setToolTipText("Gelen Kutusu / Mesajlar");
        bellButton.setFocusable(false);
        bellButton.setFont(bellButton.getFont().deriveFont(Font.BOLD, 13f));
        bellButton.addActionListener(e -> openInbox());
        right.add(bellButton);
        JLabel who = new JLabel(currentUser.getUsername() + "  •  " + currentUser.getRole());
        who.setForeground(Color.WHITE);
        right.add(who);
        bar.add(right, BorderLayout.EAST);
        refreshBell();
        return bar;
    }

    private void refreshBell() {
        if (bellButton == null) return;
        int n = new com.gtech.treasury.dao.MessageDAO().staffUnreadCount(currentUser.getUsername());
        bellButton.setText(n > 0 ? "🔔 " + n : "🔔");
    }

    /** Gelen kutusu sekmesini açar (aksiyon butonu ilgili onay ekranını açacak). */
    private void openInbox() {
        openTab("INBOX", "📬 Gelen Kutusu");
        refreshBell();
    }

    /** Gelen kutusundaki bir onay mesajını, ilgili başvurunun değerlendirme penceresiyle açar. */
    private void openEvaluate(Message m) {
        int id = -1;
        try { id = Integer.parseInt(m.getRefNo()); } catch (Exception ignored) { }
        boolean loan = "LOAN_APPROVAL".equals(m.getCategory());
        String key = loan ? "LENDING_APPROVAL" : "BORROWING_APPROVAL";
        openTab(key, loan ? "💰 Kredi Onay" : "🏦 Mevduat Onay");   // ekranı aç (varsa öne getirir)
        Component c = openTabs.get(key);
        if (id > 0) {
            if (loan && c instanceof LendingPanel)      ((LendingPanel) c).evaluate(id);
            else if (!loan && c instanceof BorrowingPanel) ((BorrowingPanel) c).evaluate(id);
        }
    }

    /** İtiraz yönetimi: açık itirazları listeler; personel kabul/ret ile sonuçlandırır. */
    private void openDisputeCenter() {
        com.gtech.treasury.dao.DisputeDAO disputeDAO = new com.gtech.treasury.dao.DisputeDAO();
        new com.gtech.treasury.dao.NotificationDAO().staffMarkAllRead();   // açılınca okundu say
        refreshBell();

        java.util.List<com.gtech.treasury.model.Dispute> open = disputeDAO.list("OPEN");
        DefaultListModel<com.gtech.treasury.model.Dispute> lm = new DefaultListModel<>();
        for (com.gtech.treasury.model.Dispute d : open) lm.addElement(d);

        JList<com.gtech.treasury.model.Dispute> list = new JList<>(lm);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                com.gtech.treasury.model.Dispute d = (com.gtech.treasury.model.Dispute) v;
                setText("İtiraz #" + d.getId() + "  •  Müşteri " + d.getCustomerNo() + "  •  " + d.getSubject());
                return this;
            }
        });

        JTextArea detail = new JTextArea(6, 44);
        detail.setEditable(false); detail.setLineWrap(true); detail.setWrapStyleWord(true);
        list.addListSelectionListener(e -> {
            com.gtech.treasury.model.Dispute d = list.getSelectedValue();
            detail.setText(d == null ? "" :
                    "Konu: " + d.getSubject() + "\nMüşteri No: " + d.getCustomerNo()
                    + "\nTarih: " + d.getCreatedAt() + "\n\nMüşteri gerekçesi:\n" + d.getCustomerReason());
        });
        if (!lm.isEmpty()) list.setSelectedIndex(0);

        JButton accept = new JButton("Kabul Et (işlemi incele/geri al)");
        JButton reject = new JButton("Reddet");
        JButton close = new JButton("Kapat");

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        south.add(close); south.add(reject); south.add(accept);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel title = new JLabel("Açık İtirazlar (bankanın kararı)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        content.add(title, BorderLayout.NORTH);
        JScrollPane lsp = new JScrollPane(list); lsp.setPreferredSize(new Dimension(640, 220));
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.add(lsp, BorderLayout.CENTER);
        body.add(new JScrollPane(detail), BorderLayout.SOUTH);
        content.add(body, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);

        JDialog dlg = new JDialog(this, "İtiraz Yönetimi", true);
        dlg.setContentPane(content);
        dlg.pack();
        dlg.setLocationRelativeTo(this);

        close.addActionListener(e -> dlg.dispose());
        java.awt.event.ActionListener decide = ev -> {
            boolean isAccept = ev.getSource() == accept;
            com.gtech.treasury.model.Dispute d = list.getSelectedValue();
            if (d == null) { JOptionPane.showMessageDialog(dlg, "Bir itiraz seçin."); return; }
            String note = (String) JOptionPane.showInputDialog(dlg,
                    (isAccept ? "İtirazı KABUL" : "İtirazı RED") + " gerekçesi / açıklaması:",
                    "Karar — İtiraz #" + d.getId(), JOptionPane.PLAIN_MESSAGE, null, null,
                    isAccept ? "İtiraz haklı bulundu; işlem incelemeye/iptale alındı." : "İtiraz uygun bulunmadı.");
            if (note == null || note.isBlank()) return;
            boolean ok = disputeDAO.resolve(d.getId(), currentUser.getUsername(),
                    isAccept ? "RESOLVED" : "REJECTED", note.trim());
            if (ok) {
                lm.removeElement(d);
                detail.setText("");
                JOptionPane.showMessageDialog(dlg, "İtiraz sonuçlandırıldı ve müşteriye bildirildi.");
            } else {
                JOptionPane.showMessageDialog(dlg, "İşlem yapılamadı (itiraz zaten kapanmış olabilir).");
            }
        };
        accept.addActionListener(decide);
        reject.addActionListener(decide);

        dlg.setVisible(true);
    }

    // ================= SOL MENÜ (ağaç / açılır gruplar) =================
    /** Bir menü satırı: key varsa yaprak (sekme açar), yoksa grup başlığıdır. */
    /** Grup başlığı -> ikon adı (anahtar kelimeye göre). */
    private static String groupIcon(String label) {
        if (label == null) return null;
        if (label.contains("Müşteri")) return "customers";
        if (label.contains("Kur")) return "fx";
        if (label.contains("İşlem")) return "transactions";
        if (label.contains("Rapor")) return "reports";
        if (label.contains("Yönet")) return "settings";
        return null;
    }

    private static class MenuItem {
        final String key, label;
        MenuItem(String key, String label) { this.key = key; this.label = label; }
        @Override public String toString() { return label; }
    }

    private JComponent buildSidebar() {
        Set<String> allowed = new HashSet<>();
        for (Screen s : permissionDAO.getAllowedScreens(currentUser.getRole())) allowed.add(s.getScreenKey());

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        root.add(leaf("🏠 Anasayfa", "home", allowed));
        // Mesajlar (banka içi + müşteri yazışmaları) — yan menüden de erişilebilir
        root.add(new DefaultMutableTreeNode(new MenuItem("INBOX", "📬 Mesajlar")));
        DefaultMutableTreeNode musteri = group("👥 Müşteri İşlemleri",
                leaf("Müşteriler", "CUSTOMER", allowed),
                leaf("Hesaplar", "ACCOUNTS", allowed),
                leaf("Para Transferi", "TRANSFER", allowed));
        if ("ADMIN".equals(currentUser.getRole()) || "TRADER".equals(currentUser.getRole())) {
            musteri.add(new DefaultMutableTreeNode(new MenuItem("DEPOSIT", "Para Yatır / Çek")));
        }
        addGroup(root, musteri);
        DefaultMutableTreeNode islemler = group("🔁 İşlemler");
        if (allowed.contains("BORROWING")) {
            islemler.add(group("🏦 Vadeli Mevduat",
                    new DefaultMutableTreeNode(new MenuItem("BORROWING_APPROVAL", "Mevduat Onay")),
                    new DefaultMutableTreeNode(new MenuItem("BORROWING_ACTIVE", "Aktif Mevduatlar")),
                    new DefaultMutableTreeNode(new MenuItem("BORROWING_CLOSED", "Kapanan Mevduatlar")),
                    new DefaultMutableTreeNode(new MenuItem("BORROWING_REJECTED", "Reddedilen Başvurular"))));
        }
        if (allowed.contains("LENDING")) {
            islemler.add(group("💰 Krediler",
                    new DefaultMutableTreeNode(new MenuItem("LENDING_APPROVAL", "Kredi Onay")),
                    new DefaultMutableTreeNode(new MenuItem("LENDING_DISBURSE", "Kullandırım (Onaylanan)")),
                    new DefaultMutableTreeNode(new MenuItem("LENDING_ACTIVE", "Aktif Krediler")),
                    new DefaultMutableTreeNode(new MenuItem("LENDING_CLOSED", "Ödenen / Kapanan")),
                    new DefaultMutableTreeNode(new MenuItem("LENDING_REJECTED", "Reddedilen Krediler")),
                    new DefaultMutableTreeNode(new MenuItem("LOAN_OVERDUE", "Geciken Krediler"))));
        }
        if (allowed.contains("SPOT")) {   // Kur işlemleri: alt grup (accordion), en üstte
            DefaultMutableTreeNode kur = group("💱 Kur İşlemleri",
                    new DefaultMutableTreeNode(new MenuItem("FX_TRADE", "Spot FX İşlemleri")),
                    new DefaultMutableTreeNode(new MenuItem("FX_WATCH", "Kur Gözlem")));
            if ("ADMIN".equals(currentUser.getRole()) || "TRADER".equals(currentUser.getRole())) {
                kur.add(new DefaultMutableTreeNode(new MenuItem("FX_FIX", "Kur Fixleme")));
            }
            islemler.insert(kur, 0);
        }
        // Para Piyasası (Treasury / Money Market) — Borçlanma + Plasman (borç verme)
        DefaultMutableTreeNode pp = group("💹 Para Piyasası");
        if (allowed.contains("MM_BORROW")) {
            pp.add(new DefaultMutableTreeNode(new MenuItem("MM_BORROW", "Borçlanma Girişi")));
            pp.add(new DefaultMutableTreeNode(new MenuItem("MM_LIST", "Borçlanma İşlemleri")));
        }
        if (allowed.contains("MM_LEND")) {
            pp.add(new DefaultMutableTreeNode(new MenuItem("MM_LEND", "Borç Verme (Plasman) Girişi")));
            pp.add(new DefaultMutableTreeNode(new MenuItem("MM_LEND_LIST", "Plasman İşlemleri")));
        }
        if (pp.getChildCount() > 0) islemler.add(pp);
        // Banka Kasası + Nakit Akışı: artık rol/menü yetkisine göre (Rol Yetkileri ekranından atanır)
        if (allowed.contains("BANK"))
            islemler.add(new DefaultMutableTreeNode(new MenuItem("BANK", "Banka Kasası")));
        if (allowed.contains("CASHFLOW"))
            islemler.add(new DefaultMutableTreeNode(new MenuItem("CASHFLOW", "Nakit Akışı")));
        addGroup(root, islemler);
        addGroup(root, group("📊 Raporlama",
                leaf("Raporlar", "REPORTS", allowed)));
        addGroup(root, group("⚙️ Yönetim",
                leaf("Kullanıcı Yönetimi", "USER_MGMT", allowed),
                leaf("Rol Yetkileri", "ROLE_PERM", allowed),
                leaf("Hata Kayıtları", "ERROR_LOG", allowed)));

        return treeSidebar(root);
    }

    // ---- Ağaç menü yardımcıları ----
    private DefaultMutableTreeNode leaf(String label, String key, Set<String> allowed) {
        if (key != null && !"home".equals(key) && !allowed.contains(key)) return null;   // yetkisiz
        return new DefaultMutableTreeNode(new MenuItem(key, label));
    }

    private DefaultMutableTreeNode group(String label, DefaultMutableTreeNode... children) {
        DefaultMutableTreeNode g = new DefaultMutableTreeNode(new MenuItem(null, label));
        for (DefaultMutableTreeNode c : children) if (c != null) g.add(c);
        return g;
    }

    private void addGroup(DefaultMutableTreeNode root, DefaultMutableTreeNode g) {
        if (g.getChildCount() > 0) root.add(g);   // yetkili çocuğu yoksa grup gösterilmez
    }

    /** Ağacı koyu temalı, sekme açan sol menü olarak kurar. */
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
                    setIcon(com.gtech.treasury.util.IconLoader.get(
                            com.gtech.treasury.util.IconLoader.forKey(((MenuItem) uo).key), 18));
                } else if (uo instanceof String) {
                    setIcon(com.gtech.treasury.util.IconLoader.get(groupIcon((String) uo), 18));
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
        sidebar.setPreferredSize(new Dimension(250, 0));
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
        logout.addActionListener(e -> logout());
        sidebar.add(logout, BorderLayout.SOUTH);
        return sidebar;
    }

    // ================= SEKME AÇMA =================
    /** Ekranı sekme olarak açar; zaten açıksa o sekmeye geçer. */
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

    /** Kapatma (×) düğmeli sekme başlığı. */
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
        close.setToolTipText("Sekmeyi kapat");
        close.addActionListener(e -> { tabs.remove(comp); openTabs.remove(key); });
        p.add(close);
        return p;
    }

    /** Ekran anahtarına karşılık gelen paneli üretir. */
    private JComponent componentFor(String key) {
        switch (key) {
            case "home":      return buildHome();
            case "CUSTOMER":  return new CustomerPanel(currentUser);
            case "ACCOUNTS":  return new AccountsPanel(currentUser);
            case "TRANSFER":  return new TransferPanel(currentUser);
            case "USER_MGMT": return new UserManagementPanel(currentUser);
            case "ROLE_PERM": return new RolePermissionPanel();
            case "ERROR_LOG": return new ErrorLogPanel();
            case "SPOT":      return new SpotPanel(currentUser);
            case "FX_TRADE": {
                boolean canTrade = "ADMIN".equals(currentUser.getRole()) || "TRADER".equals(currentUser.getRole());
                return new SpotTradePanel(canTrade, null, null);
            }
            case "FX_WATCH":
                return new SpotRatePanel("ADMIN".equals(currentUser.getRole()), null);
            case "FX_FIX":    return new FixedRatePanel();
            case "BANK":      return new BankTreasuryPanel();
            case "CASHFLOW":  return new CashflowPanel(null);   // banka kapsamı
            case "DEPOSIT":   return new DepositWithdrawPanel(currentUser);   // bankacı para yatır/çek
            case "BORROWING": return new BorrowingPanel(currentUser);
            case "LENDING":   return new LendingPanel(currentUser);
            case "BORROWING_APPROVAL": return new BorrowingPanel(currentUser, BorrowingPanel.V_APPROVAL);
            case "BORROWING_ACTIVE":   return new BorrowingPanel(currentUser, BorrowingPanel.V_ACTIVE);
            case "BORROWING_CLOSED":   return new BorrowingPanel(currentUser, BorrowingPanel.V_CLOSED);
            case "BORROWING_REJECTED": return new BorrowingPanel(currentUser, BorrowingPanel.V_REJECTED);
            case "LENDING_APPROVAL": return new LendingPanel(currentUser, LendingPanel.V_APPROVAL);
            case "LENDING_DISBURSE": return new LendingPanel(currentUser, LendingPanel.V_APPROVED);
            case "LENDING_ACTIVE":   return new LendingPanel(currentUser, LendingPanel.V_ACTIVE);
            case "LOAN_OVERDUE": return new OverduePanel();
            case "LENDING_CLOSED":   return new LendingPanel(currentUser, LendingPanel.V_CLOSED);
            case "LENDING_REJECTED": return new LendingPanel(currentUser, LendingPanel.V_REJECTED);
            case "REPORTS":   return new ReportsPanel(currentUser);
            case "INBOX":     return new InboxPanel(currentUser, this::openEvaluate);
            case "MM_BORROW": return new MoneyMarketBorrowingPanel(currentUser);
            case "MM_LIST":   return new MoneyMarketListPanel(currentUser);
            case "MM_LEND":      return new MoneyMarketLendingPanel(currentUser);
            case "MM_LEND_LIST": return new MoneyMarketLendingListPanel(currentUser);
            default:          return placeholder(key, "Tanımsız ekran.");
        }
    }

    private JComponent buildHome() {
        // Tüm personel (ADMIN/TRADER/VIEWER) anasayfada aynı bilgiyi görür: banka hazine kasası
        // (salt okunur). Kasayla ilgili İŞLEM menüleri (Banka Kasası / Nakit Akışı) yalnız ADMIN'dedir.
        return new BankTreasuryPanel();
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

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(STATUS_BG);
        bar.setBorder(new EmptyBorder(6, 16, 6, 16));
        bar.add(new JLabel("Kullanıcı: " + currentUser.getUsername()
                + "   •   Rol: " + currentUser.getRole()), BorderLayout.WEST);
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        bar.add(new JLabel("Tarih: " + today), BorderLayout.EAST);
        return bar;
    }

    private void logout() {
        dispose();
        new LoginFrame().setVisible(true);
    }
}
