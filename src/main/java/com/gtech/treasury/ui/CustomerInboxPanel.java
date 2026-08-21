package com.gtech.treasury.ui;

import com.gtech.treasury.dao.DisputeDAO;
import com.gtech.treasury.dao.MessageDAO;
import com.gtech.treasury.dao.NotificationDAO;
import com.gtech.treasury.model.Customer;
import com.gtech.treasury.model.Message;
import com.gtech.treasury.model.Notification;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Müşteri BİRLEŞİK gelen kutusu: işlem bildirimleri (notification) + banka mesajları (message)
 * tek listede. İşlem bildirimine "İtiraz Et", banka mesajına "Yanıtla" / "Ankete Katıl";
 * altta "Soru Sor".
 */
public class CustomerInboxPanel extends JPanel {

    /** Görünüm modu: hepsi / yalnız banka mesajları / yalnız işlem bildirimleri. */
    public static final int MODE_ALL = 0, MODE_MESSAGES = 1, MODE_NOTIFICATIONS = 2;

    private final NotificationDAO notifDAO = new NotificationDAO();
    private final MessageDAO msgDAO = new MessageDAO();
    private final Customer customer;
    private final Runnable onChange;              // rozet tazeleme
    private final String senderTag;               // "CUSTOMER:no"
    private final int mode;
    private Consumer<Message> onOpenMessage;      // mesaja tıklanınca Mesajlar ekranına geçiş (opsiyonel)

    private final DefaultListModel<Item> listModel = new DefaultListModel<>();
    private final JList<Item> list = new JList<>(listModel);
    private final JPanel detail = new JPanel(new BorderLayout(0, 8));

    public CustomerInboxPanel(Customer customer, Runnable onChange) {
        this(customer, onChange, MODE_ALL);
    }

    public CustomerInboxPanel(Customer customer, Runnable onChange, int mode) {
        this.customer = customer;
        this.onChange = onChange;
        this.mode = mode;
        this.senderTag = "CUSTOMER:" + customer.getCustomerNo();
        setLayout(new BorderLayout(10, 0));
        setBorder(new EmptyBorder(12, 16, 12, 16));

        // Üstte hangi görünümde olduğumuzu belirten başlık
        JLabel header = new JLabel(mode == MODE_MESSAGES ? "Banka Mesajları"
                : mode == MODE_NOTIFICATIONS ? "İşlem Bildirimleri" : "Bildirimler");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 15f));
        header.setBorder(new EmptyBorder(0, 2, 8, 2));
        add(header, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new ItemRenderer());
        list.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showSelected(); });
        JScrollPane left = new JScrollPane(list);
        left.setPreferredSize(new Dimension(360, 440));
        add(left, BorderLayout.WEST);

        detail.setBorder(new EmptyBorder(4, 12, 4, 4));
        add(detail, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        if (mode != MODE_NOTIFICATIONS) {   // soru sormak yalnız mesaj görünümünde anlamlı
            JButton ask = new JButton("Soru Sor");
            ask.addActionListener(e -> compose("STAFF", "", "QUESTION"));
            south.add(ask);
        }
        JButton refresh = new JButton("Yenile");
        refresh.addActionListener(e -> reload());
        south.add(refresh);
        add(south, BorderLayout.SOUTH);

        reload();
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) { reload(); }
        });
    }

    /** Bir mesaja tıklanınca çağrılacak geçiş davranışını atar (Mesajlar ekranına yönlendirme). */
    public void setOnOpenMessage(Consumer<Message> handler) { this.onOpenMessage = handler; }

    /** Verilen id'li mesajı listede seçip detayını açar (Mesajlar ekranı odaklama için). */
    public void focusMessage(int messageId) {
        for (int i = 0; i < listModel.size(); i++) {
            Item it = listModel.get(i);
            if (it.msg != null && it.msg.getId() == messageId) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    /** Moda göre kaynakları okuyup tarihe göre (en yeni önce) listeler. */
    private void reload() {
        listModel.clear();
        List<Item> items = new ArrayList<>();
        if (mode != MODE_MESSAGES)
            for (Notification n : notifDAO.all(customer.getCustomerNo())) items.add(new Item(n));
        if (mode != MODE_NOTIFICATIONS)
            for (Message m : msgDAO.customerInbox(customer.getCustomerNo())) items.add(new Item(m));
        items.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));   // desc
        for (Item it : items) listModel.addElement(it);
        detail.removeAll(); detail.revalidate(); detail.repaint();
        if (onChange != null) onChange.run();
    }

    private void showSelected() {
        Item it = list.getSelectedValue();
        if (it == null) return;
        // Bildirimler (birleşik) görünümünde bir MESAJA tıklanınca Mesajlar ekranına geçiş yap.
        if (it.msg != null && onOpenMessage != null) {
            Message m = it.msg;
            list.clearSelection();
            onOpenMessage.accept(m);
            return;
        }
        if (!it.isRead()) { it.markRead(); if (onChange != null) onChange.run(); }

        detail.removeAll();

        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel subj = new JLabel(it.title());
        subj.setFont(subj.getFont().deriveFont(Font.BOLD, 16f));
        JLabel meta = new JLabel(it.metaLine());
        meta.setForeground(new Color(0x6B7280));
        head.add(subj); head.add(Box.createVerticalStrut(4)); head.add(meta);
        detail.add(head, BorderLayout.NORTH);

        JTextArea body = new JTextArea(it.body());
        body.setEditable(false); body.setLineWrap(true); body.setWrapStyleWord(true);
        body.setBorder(new EmptyBorder(10, 2, 10, 2));
        detail.add(new JScrollPane(body), BorderLayout.CENTER);

        JPanel actWrap = new JPanel(new FlowLayout(FlowLayout.LEFT));
        if (it.notif != null) {                                   // işlem bildirimi → itiraz
            JButton dispute = new JButton("İtiraz Et");
            dispute.addActionListener(e -> disputeNotification(it.notif));
            actWrap.add(dispute);
        } else {                                                  // banka mesajı
            if ("SURVEY".equals(it.msg.getCategory())) {
                JButton join = new JButton("Ankete Katıl");
                join.addActionListener(e -> answerSurvey(it.msg));
                actWrap.add(join);
            }
            if (it.msg.getSender() != null && it.msg.getSender().startsWith("STAFF:")) {
                JButton reply = new JButton("Yanıtla");
                reply.addActionListener(e -> compose(it.msg.getSender(), it.msg.getSubject(), "QUESTION"));
                actWrap.add(reply);
            }
        }
        if (actWrap.getComponentCount() > 0) detail.add(actWrap, BorderLayout.SOUTH);

        detail.revalidate(); detail.repaint();
        list.repaint();
    }

    /** Bir işlem bildirimine itiraz: kayıt + STAFF ortak kutuya mesaj. */
    private void disputeNotification(Notification n) {
        String reason = (String) JOptionPane.showInputDialog(this,
                "Bu işlemi kendiniz yapmadığınızı düşünüyorsanız gerekçenizi yazın.\n"
                        + "İtirazınız bankaya iletilecek ve değerlendirilecektir:",
                "İtiraz Et — " + n.getTitle(), JOptionPane.PLAIN_MESSAGE, null, null, "");
        if (reason == null || reason.isBlank()) return;
        int did = new DisputeDAO().create(customer.getCustomerNo(), n.getTitle(), reason.trim());
        if (did > 0) {
            msgDAO.send(senderTag, "STAFF", "İtiraz: " + n.getTitle(),
                    "İşleme itiraz edildi.\nMüşteri gerekçesi:\n" + reason.trim(),
                    "DISPUTE", String.valueOf(did));
            JOptionPane.showMessageDialog(this,
                    "İtirazınız (No: " + did + ") bankaya iletildi.\nSonuç size bildirilecektir.",
                    "İtiraz Alındı", JOptionPane.INFORMATION_MESSAGE);
            reload();
        } else {
            JOptionPane.showMessageDialog(this, "İtiraz oluşturulamadı.", "Hata", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Yeni mesaj / yanıt: konu + gövde alıp gönderir. Alıcı sabit. */
    private void compose(String recipient, String replySubject, String category) {
        JTextField subjectF = new JTextField(28);
        if (replySubject != null && !replySubject.isBlank())
            subjectF.setText(replySubject.startsWith("Re:") ? replySubject : "Re: " + replySubject);
        JTextArea bodyA = new JTextArea(7, 28);
        bodyA.setLineWrap(true); bodyA.setWrapStyleWord(true);

        JPanel p = new JPanel(new BorderLayout(6, 6));
        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.add(new JLabel("Alıcı: Banka" + ("STAFF".equals(recipient) ? "" : " (" + prettyTag(recipient) + ")")),
                BorderLayout.NORTH);
        JPanel sp = new JPanel(new BorderLayout(6, 0));
        sp.add(new JLabel("Konu:"), BorderLayout.WEST);
        sp.add(subjectF, BorderLayout.CENTER);
        top.add(sp, BorderLayout.SOUTH);
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(bodyA), BorderLayout.CENTER);

        int r = JOptionPane.showConfirmDialog(this, p,
                "STAFF".equals(recipient) ? "Bankaya Soru Sor" : "Yanıtla",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        String subject = subjectF.getText().trim();
        if (subject.isEmpty()) { JOptionPane.showMessageDialog(this, "Konu boş olamaz."); return; }
        msgDAO.send(senderTag, recipient, subject, bodyA.getText().trim(), category, null);
        JOptionPane.showMessageDialog(this, "Mesajınız bankaya iletildi.");
        reload();
    }

    /** SURVEY mesajına 1-5 puan + yorum ile yanıt. */
    private void answerSurvey(Message m) {
        JComboBox<Integer> score = new JComboBox<>(new Integer[]{1, 2, 3, 4, 5});
        score.setSelectedItem(5);
        JTextArea comment = new JTextArea(5, 26);
        comment.setLineWrap(true); comment.setWrapStyleWord(true);

        JPanel p = new JPanel(new BorderLayout(6, 6));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        top.add(new JLabel("Puanınız (1-5):")); top.add(score);
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(comment), BorderLayout.CENTER);

        int r = JOptionPane.showConfirmDialog(this, p, "Memnuniyet Anketi",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;

        String recipient = (m.getSender() != null && m.getSender().startsWith("STAFF:"))
                ? m.getSender() : "STAFF";
        String body = "Puan: " + score.getSelectedItem() + "/5\nYorum: "
                + (comment.getText().isBlank() ? "-" : comment.getText().trim());
        msgDAO.send(senderTag, recipient, "Anket Yanıtı: " + m.getSubject(), body, "SURVEY_RESPONSE", null);
        JOptionPane.showMessageDialog(this, "Anket yanıtınız için teşekkürler.");
        reload();
    }

    private static String prettyTag(String tag) {
        if (tag == null) return "-";
        if (tag.equals("SYSTEM")) return "Sistem";
        if (tag.equals("STAFF")) return "Banka";
        if (tag.startsWith("STAFF:")) return "Banka (" + tag.substring(6) + ")";
        if (tag.startsWith("CUSTOMER:")) return "Siz";
        return tag;
    }

    // ---- Birleşik liste öğesi: ya bildirim ya mesaj ----
    private static class Item {
        final Notification notif;
        final Message msg;
        Item(Notification n) { this.notif = n; this.msg = null; }
        Item(Message m) { this.notif = null; this.msg = m; }

        boolean isRead()     { return notif != null ? notif.isRead() : msg.isRead(); }
        String createdAt()   { return notif != null ? notif.getCreatedAt() : msg.getCreatedAt(); }
        String title()       { return notif != null ? notif.getTitle() : msg.getSubject(); }
        String body()        {
            if (notif != null) return notif.getDetail() == null ? "" : notif.getDetail();
            return msg.getBody() == null ? "" : msg.getBody();
        }
        String kindLabel()   { return notif != null ? "Bildirim" : prettyTag(msg.getSender()); }
        String metaLine()    { return "Kimden: " + kindLabel() + "     Tarih: " + createdAt(); }

        void markRead() {
            if (notif != null) { new NotificationDAO().markRead(notif.getId()); notif.setRead(true); }
            else { new MessageDAO().markRead(msg.getId()); }
        }
    }

    private static class ItemRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
            super.getListCellRendererComponent(l, v, i, s, f);
            Item it = (Item) v;
            String tag = it.notif != null ? "[Bildirim] " : "[Mesaj] ";
            String title = it.title() == null ? "" : it.title();
            if (it.isRead()) {
                setText("<html><font color='#9AA3AF'>" + tag + "</font>" + title
                        + "<br><font color='#6B7280'>" + it.createdAt() + "</font></html>");
            } else {
                setText("<html>● <font color='#9AA3AF'>" + tag + "</font><b>" + title + "</b>"
                        + "<br><font color='#6B7280'>" + it.createdAt() + "</font></html>");
            }
            setBorder(new EmptyBorder(6, 8, 6, 8));
            return this;
        }
    }
}
