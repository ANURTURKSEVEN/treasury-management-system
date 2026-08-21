package com.gtech.treasury.ui;

import com.gtech.treasury.dao.MessageDAO;
import com.gtech.treasury.dao.UserDAO;
import com.gtech.treasury.model.Customer;
import com.gtech.treasury.model.User;
import com.gtech.treasury.util.CustomerPicker;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Yeniden kullanılabilir "Yeni Mesaj" penceresi.
 * Personel; müşteriye veya başka bir personele normal mesaj ya da memnuniyet anketi gönderir.
 * replyToTag verilirse (ör. "STAFF:trader" / "CUSTOMER:10000001") alıcı önceden seçili gelir.
 */
public class ComposeMessageDialog extends JDialog {

    private final MessageDAO messageDAO = new MessageDAO();
    private final String senderTag;

    private final JRadioButton toCustomer = new JRadioButton("Müşteri", true);
    private final JRadioButton toStaff = new JRadioButton("Personel");
    private final CustomerPicker picker = new CustomerPicker();
    private final JComboBox<StaffItem> staffBox = new JComboBox<>();
    private final JComboBox<String> typeBox = new JComboBox<>(new String[]{"Normal mesaj", "Memnuniyet Anketi"});
    private final JTextField subjectF = new JTextField(30);
    private final JTextArea bodyA = new JTextArea(8, 30);

    /** Sıfırdan yeni mesaj. */
    public ComposeMessageDialog(Window owner, String senderTag) {
        this(owner, senderTag, null, null);
    }

    /** Yanıt/önden dolu mesaj. replyToTag = null ise boş açılır. */
    public ComposeMessageDialog(Window owner, String senderTag, String replyToTag, String replySubject) {
        super(owner, "Yeni Mesaj", ModalityType.APPLICATION_MODAL);
        this.senderTag = senderTag;

        ButtonGroup g = new ButtonGroup();
        g.add(toCustomer); g.add(toStaff);

        for (User u : new UserDAO().getStaffUsers()) {
            staffBox.addItem(new StaffItem(u.getUsername(), u.getFullName(), u.getRole()));
        }
        staffBox.setVisible(false);

        Runnable toggle = () -> {
            picker.setVisible(toCustomer.isSelected());
            staffBox.setVisible(toStaff.isSelected());
        };
        toCustomer.addActionListener(e -> toggle.run());
        toStaff.addActionListener(e -> toggle.run());

        bodyA.setLineWrap(true); bodyA.setWrapStyleWord(true);

        typeBox.addActionListener(e -> {
            if (typeBox.getSelectedIndex() == 1) {
                if (subjectF.getText().isBlank()) subjectF.setText("Memnuniyet Anketi");
                if (bodyA.getText().isBlank())
                    bodyA.setText("Hizmetimizden memnuniyetinizi 1-5 arasında puanlayıp "
                            + "görüşlerinizi paylaşır mısınız? Katılımınız için teşekkürler.");
            }
        });

        // --- Form yerleşimi ---
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(14, 16, 8, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        JPanel recType = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        recType.setOpaque(false);
        recType.add(toCustomer); recType.add(toStaff);
        addRow(form, c, row++, "Alıcı türü:", recType);
        addRow(form, c, row++, "Alıcı:", picker);
        addRow(form, c, row++, "", staffBox);
        addRow(form, c, row++, "Mesaj türü:", typeBox);
        addRow(form, c, row++, "Konu:", subjectF);

        c.gridx = 0; c.gridy = row; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel("Mesaj:"), c);
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.fill = GridBagConstraints.BOTH;
        form.add(new JScrollPane(bodyA), c);

        // --- Butonlar ---
        JButton send = new JButton("Gönder");
        JButton cancel = new JButton("Vazgeç");
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        south.add(cancel); south.add(send);
        cancel.addActionListener(e -> dispose());
        send.addActionListener(e -> onSend());

        // --- Yanıt önden doldurma ---
        if (replyToTag != null) applyReply(replyToTag, replySubject);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    /** replyToTag: "STAFF:kadi" veya "CUSTOMER:no" — alıcıyı seçili + KİLİTLİ getirir, konuyu "Re: ..." yapar. */
    private void applyReply(String replyToTag, String replySubject) {
        if (replySubject != null && !replySubject.isBlank()) {
            subjectF.setText(replySubject.startsWith("Re:") ? replySubject : "Re: " + replySubject);
        }
        if (replyToTag.startsWith("CUSTOMER:")) {
            toCustomer.setSelected(true);
            picker.setVisible(true); staffBox.setVisible(false);
            try { picker.selectByNo(Integer.parseInt(replyToTag.substring("CUSTOMER:".length()))); }
            catch (NumberFormatException ignored) { }
        } else if (replyToTag.startsWith("STAFF:")) {
            toStaff.setSelected(true);
            picker.setVisible(false); staffBox.setVisible(true);
            String uname = replyToTag.substring("STAFF:".length());
            for (int i = 0; i < staffBox.getItemCount(); i++) {
                if (staffBox.getItemAt(i).username.equals(uname)) { staffBox.setSelectedIndex(i); break; }
            }
        }
        // Alıcıyı kilitle: yanıt yalnızca gönderen kişiye
        toCustomer.setEnabled(false);
        toStaff.setEnabled(false);
        staffBox.setEnabled(false);
        setEnabledDeep(picker, false);
    }

    /** Bir bileşeni ve tüm alt bileşenlerini pasifleştirir (CustomerPicker gibi paneller için). */
    private void setEnabledDeep(java.awt.Component comp, boolean enabled) {
        comp.setEnabled(enabled);
        if (comp instanceof java.awt.Container) {
            for (java.awt.Component ch : ((java.awt.Container) comp).getComponents()) {
                setEnabledDeep(ch, enabled);
            }
        }
    }

    private void onSend() {
        String subject = subjectF.getText().trim();
        String body = bodyA.getText().trim();
        if (subject.isEmpty()) { warn("Lütfen bir konu yazın."); return; }

        String recipient;
        if (toCustomer.isSelected()) {
            Customer sel = picker.getSelected();
            if (sel == null) { warn("Lütfen bir müşteri seçin."); return; }
            recipient = "CUSTOMER:" + sel.getCustomerNo();
        } else {
            StaffItem si = (StaffItem) staffBox.getSelectedItem();
            if (si == null) { warn("Lütfen bir personel seçin."); return; }
            recipient = "STAFF:" + si.username;
        }

        String category = (typeBox.getSelectedIndex() == 1) ? "SURVEY" : "INFO";
        messageDAO.send(senderTag, recipient, subject, body, category, null);
        JOptionPane.showMessageDialog(this, "Mesaj gönderildi.", "Bilgi", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }

    private void addRow(JPanel form, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridx = 0; c.gridy = row; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.WEST;
        form.add(new JLabel(label), c);
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, c);
    }

    private void warn(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Uyarı", JOptionPane.WARNING_MESSAGE);
    }

    private static class StaffItem {
        final String username, fullName, role;
        StaffItem(String username, String fullName, String role) {
            this.username = username; this.fullName = fullName; this.role = role;
        }
        @Override public String toString() {
            return username + (fullName != null && !fullName.isBlank() ? " — " + fullName : "")
                    + " (" + role + ")";
        }
    }
}