package com.gtech.treasury.ui;

import com.gtech.treasury.dao.MessageDAO;
import com.gtech.treasury.model.Conversation;
import com.gtech.treasury.model.Message;
import com.gtech.treasury.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Banka gelen/gönderilen kutusu + müşteri görüşmeleri (thread).
 * Görüşme modunda bir müşteriyle yapılan TÜM yazışma (müşteri + hangi personel yanıtladıysa)
 * kronolojik görünür; tüm personel aynı görüşmeyi görür ve alttan yanıtlar.
 */
public class InboxPanel extends JPanel {

    private static final int INBOX = 0, SENT = 1, CONV = 2;

    private final MessageDAO dao = new MessageDAO();
    private final User user;
    private final Consumer<Message> onEvaluate;

    private final DefaultListModel<Object> listModel = new DefaultListModel<>();
    private final JList<Object> list = new JList<>(listModel);
    private final JPanel detail = new JPanel(new BorderLayout(0, 8));

    private int mode = INBOX;

    public InboxPanel(User user, Consumer<Message> onEvaluate) {
        this.user = user;
        this.onEvaluate = onEvaluate;
        setLayout(new BorderLayout(10, 0));
        setBorder(new EmptyBorder(12, 16, 12, 16));

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new RowRenderer());
        list.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showSelected(); });
        JScrollPane left = new JScrollPane(list);
        left.setPreferredSize(new Dimension(340, 440));
        add(left, BorderLayout.WEST);

        detail.setBorder(new EmptyBorder(4, 12, 4, 4));
        add(detail, BorderLayout.CENTER);

        // Üst: mod geçişi
        JToggleButton inboxBtn = new JToggleButton("Gelen Kutusu", true);
        JToggleButton sentBtn = new JToggleButton("Gönderilenler");
        JToggleButton convBtn = new JToggleButton("Müşteri Görüşmeleri");
        ButtonGroup grp = new ButtonGroup();
        grp.add(inboxBtn); grp.add(sentBtn); grp.add(convBtn);
        inboxBtn.addActionListener(e -> { mode = INBOX; reload(); });
        sentBtn.addActionListener(e -> { mode = SENT; reload(); });
        convBtn.addActionListener(e -> { mode = CONV; reload(); });
        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        north.add(inboxBtn); north.add(sentBtn); north.add(convBtn);
        add(north, BorderLayout.NORTH);

        JButton compose = new JButton("Yeni Mesaj");
        compose.addActionListener(e -> openCompose(null, null));
        JButton refresh = new JButton("Yenile");
        refresh.addActionListener(e -> reload());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        south.add(compose); south.add(refresh);
        add(south, BorderLayout.SOUTH);

        reload();
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) { reload(); }
        });
    }

    private void openCompose(String replyToTag, String replySubject) {
        new ComposeMessageDialog(SwingUtilities.getWindowAncestor(this),
                "STAFF:" + user.getUsername(), replyToTag, replySubject).setVisible(true);
        reload();
    }

    private void reload() {
        listModel.clear();
        if (mode == CONV) {
            for (Conversation c : dao.customerConversations()) listModel.addElement(c);
        } else {
            java.util.List<Message> items = (mode == SENT)
                    ? dao.staffSent(user.getUsername())
                    : dao.staffInbox(user.getUsername());
            for (Message m : items) listModel.addElement(m);
        }
        detail.removeAll(); detail.revalidate(); detail.repaint();
    }

    private void showSelected() {
        Object sel = list.getSelectedValue();
        if (sel instanceof Conversation) { showConversation((Conversation) sel); return; }
        if (sel instanceof Message) showMessage((Message) sel);
    }

    // ---------- Tek mesaj görünümü (Gelen / Gönderilen) ----------
    private void showMessage(Message m) {
        if (mode == INBOX && !m.isRead()) dao.markRead(m.getId());

        detail.removeAll();
        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel subj = new JLabel(m.getSubject());
        subj.setFont(subj.getFont().deriveFont(Font.BOLD, 16f));
        String party = (mode == SENT) ? ("Kime: " + pretty(m.getRecipient()))
                                      : ("Kimden: " + pretty(m.getSender()));
        JLabel meta = new JLabel(party + "     Tarih: " + m.getCreatedAt());
        meta.setForeground(new Color(0x6B7280));
        head.add(subj); head.add(Box.createVerticalStrut(4)); head.add(meta);
        detail.add(head, BorderLayout.NORTH);

        JTextArea body = new JTextArea(m.getBody() == null ? "" : m.getBody());
        body.setEditable(false); body.setLineWrap(true); body.setWrapStyleWord(true);
        body.setBorder(new EmptyBorder(10, 2, 10, 2));
        detail.add(new JScrollPane(body), BorderLayout.CENTER);

        JPanel actWrap = new JPanel(new FlowLayout(FlowLayout.LEFT));
        if (m.isActionable()) {
            JButton act = new JButton("LOAN_APPROVAL".equals(m.getCategory())
                    ? "Krediyi Değerlendir" : "Mevduatı Değerlendir");
            act.addActionListener(e -> { if (onEvaluate != null) onEvaluate.accept(m); });
            actWrap.add(act);
        }
        if (mode == INBOX && canReplyTo(m.getSender())) {
            JButton reply = new JButton("Yanıtla");
            reply.addActionListener(e -> openCompose(m.getSender(), m.getSubject()));
            actWrap.add(reply);
        }
        if (actWrap.getComponentCount() > 0) detail.add(actWrap, BorderLayout.SOUTH);

        detail.revalidate(); detail.repaint();
        list.repaint();
    }

    // ---------- Görüşme (thread) görünümü ----------
    private void showConversation(Conversation conv) {
        int no = conv.getCustomerNo();
        dao.markCustomerThreadRead(no);   // ortak okundu: tüm personel için

        detail.removeAll();
        JLabel title = new JLabel("Müşteri " + no + " ile görüşme");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setBorder(new EmptyBorder(0, 2, 6, 2));
        detail.add(title, BorderLayout.NORTH);

        // Mesaj balonları (kronolojik)
        JPanel thread = new JPanel();
        thread.setLayout(new BoxLayout(thread, BoxLayout.Y_AXIS));
        thread.setBackground(Color.WHITE);
        String lastSubject = "Görüşme";
        for (Message m : dao.conversation(no)) {
            boolean fromCustomer = m.getSender() != null && m.getSender().startsWith("CUSTOMER:");
            if (fromCustomer && m.getSubject() != null) lastSubject = m.getSubject();
            thread.add(bubble(m, fromCustomer));
            thread.add(Box.createVerticalStrut(6));
        }
        JScrollPane sp = new JScrollPane(thread);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        detail.add(sp, BorderLayout.CENTER);

        // Alt: yanıt kutusu
        final String replySubject = lastSubject;
        JTextArea replyA = new JTextArea(3, 30);
        replyA.setLineWrap(true); replyA.setWrapStyleWord(true);
        JButton send = new JButton("Yanıtla");
        send.addActionListener(e -> {
            String text = replyA.getText().trim();
            if (text.isEmpty()) return;
            String subject = replySubject.startsWith("Re:") ? replySubject : "Re: " + replySubject;
            dao.send("STAFF:" + user.getUsername(), "CUSTOMER:" + no, subject, text, "INFO", null);
            replyA.setText("");
            showConversation(conv);   // thread'i tazele
        });
        JPanel replyBox = new JPanel(new BorderLayout(6, 0));
        replyBox.setBorder(new EmptyBorder(8, 2, 2, 2));
        replyBox.add(new JScrollPane(replyA), BorderLayout.CENTER);
        replyBox.add(send, BorderLayout.EAST);
        detail.add(replyBox, BorderLayout.SOUTH);

        detail.revalidate(); detail.repaint();
        list.repaint();
        SwingUtilities.invokeLater(() -> sp.getVerticalScrollBar().setValue(sp.getVerticalScrollBar().getMaximum()));
    }

    /** Bir mesaj balonu: gönderen etiketi (kalın) + tarih + gövde. */
    private JComponent bubble(Message m, boolean fromCustomer) {
        JPanel b = new JPanel(new BorderLayout());
        b.setBorder(new EmptyBorder(8, 10, 8, 10));
        b.setBackground(fromCustomer ? new Color(0xEFF4FF) : new Color(0xF2F3F5));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel who = new JLabel(pretty(m.getSender()) + "  •  " + m.getCreatedAt());
        who.setForeground(new Color(0x374151));
        who.setFont(who.getFont().deriveFont(Font.BOLD, 12f));
        b.add(who, BorderLayout.NORTH);

        String txt = (m.getSubject() != null ? m.getSubject() + "\n" : "")
                + (m.getBody() == null ? "" : m.getBody());
        JTextArea body = new JTextArea(txt);
        body.setEditable(false); body.setLineWrap(true); body.setWrapStyleWord(true);
        body.setOpaque(false); body.setBorder(new EmptyBorder(4, 0, 0, 0));
        b.add(body, BorderLayout.CENTER);
        return b;
    }

    private boolean canReplyTo(String sender) {
        return sender != null && (sender.startsWith("STAFF:") || sender.startsWith("CUSTOMER:"));
    }

    /** Gönderen/alıcı etiketini okunur yapar (hangi personel yazdığı görünür). */
    private static String pretty(String tag) {
        if (tag == null) return "-";
        if (tag.equals("SYSTEM")) return "Sistem";
        if (tag.equals("STAFF")) return "Banka (ortak kutu)";
        if (tag.startsWith("STAFF:")) return "Banka — " + tag.substring(6);
        if (tag.startsWith("CUSTOMER:")) return "Müşteri " + tag.substring(9);
        return tag;
    }

    // ---------- Sol liste satırı ----------
    private static class RowRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
            super.getListCellRendererComponent(l, v, i, s, f);
            setBorder(new EmptyBorder(6, 8, 6, 8));
            if (v instanceof Conversation) {
                Conversation c = (Conversation) v;
                String head = "Müşteri " + c.getCustomerNo() + (c.getUnread() > 0 ? "  (" + c.getUnread() + ")" : "");
                boolean bold = c.getUnread() > 0;
                setText("<html>" + (bold ? "● <b>" + head + "</b>" : head)
                        + "<br><font color='#6B7280'>" + safe(c.getLastSubject()) + " — " + c.getLastAt()
                        + "</font></html>");
            } else if (v instanceof Message) {
                Message m = (Message) v;
                String subject = safe(m.getSubject());
                if (m.isRead()) {
                    setText("<html>" + subject + "<br><font color='#6B7280'>"
                            + pretty(m.getSender()) + " — " + m.getCreatedAt() + "</font></html>");
                } else {
                    setText("<html>● <b>" + subject + "</b><br><font color='#6B7280'>"
                            + pretty(m.getSender()) + " — " + m.getCreatedAt() + "</font></html>");
                }
            }
            return this;
        }
        private static String safe(String s) { return s == null ? "" : s; }
    }
}
