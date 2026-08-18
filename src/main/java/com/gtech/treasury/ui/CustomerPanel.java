package com.gtech.treasury.ui;

import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.dao.CustomerDAO;
import com.gtech.treasury.model.Customer;
import com.gtech.treasury.model.User;
import com.gtech.treasury.util.Notify;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.List;

/**
 * Müşteri Yönetimi paneli.
 *
 * Düzen:
 *  - Üstte  : Filtreler (kriter arama) + Sorgula
 *  - Ortada : sonuç tablosu (Müşteri No, Tür, ... + satır içi Güncelle/Sil)
 *  - Altta  : "Yeni Müşteri Kaydet" (ayrı kayıt ekranı; GERÇEK/TÜZEL seçimi)
 *
 * Rol kuralları:
 *  - VIEWER : sadece arama ve listeleme
 *  - TRADER : + ekleme + güncelleme (silme YOK)
 *  - ADMIN  : tüm işlemler
 */
public class CustomerPanel extends JPanel {

    private final CustomerDAO customerDAO = new CustomerDAO();
    private final User currentUser;

    private final boolean canAdd;
    private final boolean canUpdate;
    private final boolean canDelete;

    // Filtre alanları
    private final JTextField fNo = new JTextField(10);
    private final JTextField fName = new JTextField(12);
    private final JTextField fSurname = new JTextField(12);
    private final JTextField fTc = new JTextField(12);
    private final JTextField fPhone = new JTextField(12);

    private final CustomerTableModel tableModel;
    private final JTable customerTable;

    private JDialog searchDialog;   // "Müşteri Ara" pop-up'ı

    public CustomerPanel(User currentUser) {
        this.currentUser = currentUser;

        String role = currentUser.getRole();
        this.canAdd    = "ADMIN".equals(role) || "TRADER".equals(role);
        this.canUpdate = "ADMIN".equals(role) || "TRADER".equals(role);
        this.canDelete = "ADMIN".equals(role);

        this.tableModel = new CustomerTableModel(canUpdate || canDelete);
        this.customerTable = new JTable(tableModel);

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildTablePanel(), BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);

        loadCustomers(customerDAO.getAllCustomers());
    }

    // ================= ÜST: Başlık + "Sorgu Kriterleri" (birincil Müşteri No + ≡) =================
    private JComponent buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(0, 8));
        JLabel title = new JLabel("Müşteriler");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        bar.add(title, BorderLayout.NORTH);

        JPanel crit = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        crit.setBorder(com.gtech.treasury.util.CBStyle.criteriaBorder());
        crit.add(new JLabel("Müşteri No"));
        fNo.setColumns(12);
        fNo.addActionListener(e -> doSearch());   // Enter ile ara
        // ≡ butonu detaylı arama pop-up'ını açar
        crit.add(com.gtech.treasury.util.CBStyle.withLookup(fNo, this::openSearchDialog));
        JButton clear = new JButton("Temizle");
        clear.addActionListener(e -> clearFilters());
        crit.add(clear);
        bar.add(crit, BorderLayout.WEST);
        return bar;
    }

    /** Detaylı arama pop-up'ı: Müşteri No hariç kriterler (Ad/Soyad/TC/Telefon). Modeless. */
    private void openSearchDialog() {
        if (searchDialog == null) {
            JPanel form = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(6, 6, 6, 6);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            form.setBorder(com.gtech.treasury.util.CBStyle.criteriaBorder());
            int row = 0;
            addFormRow(form, gbc, row++, "Ad", fName);
            addFormRow(form, gbc, row++, "Soyad", fSurname);
            addFormRow(form, gbc, row++, "TC", fTc);
            addFormRow(form, gbc, row++, "Telefon", fPhone);

            // Enter ile de sorgula
            for (JTextField f : new JTextField[]{fName, fSurname, fTc, fPhone}) {
                f.addActionListener(e -> doSearch());
            }

            JButton searchBtn = new JButton("Sorgula");
            searchBtn.addActionListener(e -> doSearch());
            JButton clearBtn = new JButton("Temizle");
            clearBtn.addActionListener(e -> clearFilters());
            JButton closeBtn = new JButton("Kapat");
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            actions.add(clearBtn);
            actions.add(closeBtn);
            actions.add(searchBtn);

            JPanel content = new JPanel(new BorderLayout(8, 8));
            content.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
            content.add(form, BorderLayout.CENTER);
            content.add(actions, BorderLayout.SOUTH);

            searchDialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Müşteri Ara (Detaylı)");
            searchDialog.setModal(false);   // arka planda tablo görünsün
            closeBtn.addActionListener(e -> searchDialog.setVisible(false));
            searchDialog.setContentPane(content);
            searchDialog.pack();
            searchDialog.setLocationRelativeTo(this);
        }
        searchDialog.setVisible(true);
        searchDialog.toFront();
    }

    // ================= ORTA: Tablo =================
    private JComponent buildTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));

        JLabel title = new JLabel("Müşteri Kayıtları");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        panel.add(title, BorderLayout.NORTH);

        customerTable.setRowHeight(32);
        if (canUpdate || canDelete) {
            javax.swing.table.TableColumn actionCol =
                    customerTable.getColumnModel().getColumn(CustomerTableModel.COL_ACTION);
            // İki buton (Güncelle + Sil) yan yana sığsın diye yeterli genişlik.
            int width = (canUpdate && canDelete) ? 175 : 100;
            actionCol.setMinWidth(width);
            actionCol.setPreferredWidth(width);
            actionCol.setCellRenderer(new ActionRenderer(canUpdate, canDelete));
            actionCol.setCellEditor(new ActionEditor(canUpdate, canDelete));
        }

        panel.add(new JScrollPane(customerTable), BorderLayout.CENTER);
        return panel;
    }

    // ================= ALT: Yeni Müşteri Kaydet =================
    private JComponent buildBottomBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        if (canDelete) {   // yalnız ADMIN: pasif müşterileri görüntüle + geri getir
            JButton passiveButton = new JButton("Pasif Müşteriler");
            passiveButton.addActionListener(e -> openPassiveDialog());
            panel.add(passiveButton);
        }
        if (canAdd) {
            JButton addButton = new JButton("＋ Yeni Müşteri Kaydet");
            addButton.addActionListener(e -> openAddDialog());
            panel.add(addButton);
        }
        return panel;
    }

    /** Pasif (silinmiş) müşterileri listeler; seçileni müşteri + hesaplarıyla geri getirir. */
    private void openPassiveDialog() {
        String[] cols = {"Müşteri No", "Tür", "Ad", "Soyad", "TC", "Telefon"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        List<Customer> passive = customerDAO.getPassiveCustomers();
        for (Customer c : passive) {
            model.addRow(new Object[]{c.getCustomerNo(), c.getCustomerType(),
                    c.getCustomerName(), c.getSurname(), c.getTc(), c.getPhone()});
        }

        JTable pt = new JTable(model);
        pt.setRowHeight(26);
        pt.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane sp = new JScrollPane(pt);
        sp.setPreferredSize(new Dimension(660, 190));

        JButton restore = new JButton("Geri Getir");
        JButton close = new JButton("Kapat");
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(close);
        south.add(restore);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel title = new JLabel("Pasif Müşteriler");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        content.add(title, BorderLayout.NORTH);
        content.add(sp, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);

        JDialog dialog = new JDialog((Window) SwingUtilities.getWindowAncestor(this),
                "Pasif Müşteriler", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(this);

        close.addActionListener(e -> dialog.dispose());
        restore.addActionListener(e -> {
            int viewRow = pt.getSelectedRow();
            if (viewRow < 0) { showWarning("Önce bir müşteri seçin."); return; }
            Customer c = passive.get(pt.convertRowIndexToModel(viewRow));
            int ans = JOptionPane.showConfirmDialog(dialog,
                    c.getCustomerName() + " " + c.getSurname() + " geri getirilsin mi?\n"
                            + "Müşteri ve hesapları yeniden aktif olacaktır.",
                    "Geri Getir", JOptionPane.YES_NO_OPTION);
            if (ans == JOptionPane.YES_OPTION) {
                if (customerDAO.reactivateCustomer(c.getCustomerId())) {
                    ActivityLogDAO.log("CUSTOMER_REACTIVATE", c.getCustomerNo(),
                            "Müşteri ve hesapları yeniden aktif edildi: "
                                    + c.getCustomerName() + " " + c.getSurname(),
                            "Müşteri No: " + c.getCustomerNo());
                    dialog.dispose();
                    refreshTable();   // ana liste (aktifler) güncellensin
                } else {
                    showWarning("Müşteri geri getirilemedi.");
                }
            }
        });

        dialog.setVisible(true);
    }

    // ================= İş mantığı =================

    private void doSearch() {
        loadCustomers(customerDAO.searchByCriteria(
                fNo.getText(), fName.getText(), fSurname.getText(),
                fTc.getText(), fPhone.getText()));
    }

    private void clearFilters() {
        fNo.setText("");
        fName.setText("");
        fSurname.setText("");
        fTc.setText("");
        fPhone.setText("");
        loadCustomers(customerDAO.getAllCustomers());
    }

    private void refreshTable() {
        doSearch();
    }

    private void loadCustomers(List<Customer> customers) {
        tableModel.setData(customers);
    }

    /** Yeni müşteri kayıt ekranı — en başta GERÇEK/TÜZEL seçimi. */
    private void openAddDialog() {
        JComboBox<String> typeCombo = new JComboBox<>(
                customerDAO.getCustomerTypes().toArray(new String[0]));
        JTextField dName = new JTextField(16);
        JTextField dSurname = new JTextField(16);
        JTextField dTc = new JTextField(16);
        JTextField dPhone = new JTextField(16);
        JTextField dAddress = new JTextField(16);
        JPasswordField dPassword = new JPasswordField(16);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addFormRow(form, gbc, row++, "Müşteri Türü:", typeCombo);   // ilk başta
        addFormRow(form, gbc, row++, "Ad:", dName);
        addFormRow(form, gbc, row++, "Soyad:", dSurname);
        addFormRow(form, gbc, row++, "TC:", dTc);
        addFormRow(form, gbc, row++, "Telefon:", dPhone);
        addFormRow(form, gbc, row++, "Adres:", dAddress);
        addFormRow(form, gbc, row++, "Şifre:", dPassword);         // müşteri giriş şifresi

        JDialog dialog = new JDialog(
                (Window) SwingUtilities.getWindowAncestor(this),
                "Yeni Müşteri Kaydı", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(8, 8));
        ((JComponent) dialog.getContentPane()).setBorder(
                BorderFactory.createEmptyBorder(12, 12, 12, 12));
        dialog.add(form, BorderLayout.CENTER);

        JButton saveBtn = new JButton("Kaydet");
        JButton cancelBtn = new JButton("İptal");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(cancelBtn);
        actions.add(saveBtn);
        dialog.add(actions, BorderLayout.SOUTH);

        cancelBtn.addActionListener(e -> dialog.dispose());
        saveBtn.addActionListener(e -> {
            String name = dName.getText().trim();
            String tc = dTc.getText().trim();
            if (name.isEmpty()) {
                showWarning("Ad boş olamaz.");
                return;
            }
            if (!tc.matches("\\d{11}")) {
                showWarning("TC 11 haneli ve sadece rakam olmalı.");
                return;
            }
            String password = new String(dPassword.getPassword());
            if (password.isEmpty()) {
                showWarning("Şifre boş olamaz (müşteri bu şifreyle giriş yapacak).");
                return;
            }
            String type = (String) typeCombo.getSelectedItem();
            Customer c = new Customer(type, name, dSurname.getText().trim(), tc,
                    dPhone.getText().trim(), dAddress.getText().trim(), password);
            if (customerDAO.addCustomer(c)) {
                dialog.dispose();
                List<Customer> all = customerDAO.getAllCustomers();
                loadCustomers(all);
                int no = all.stream().filter(x -> x.getTc().equals(tc))
                        .mapToInt(Customer::getCustomerNo).findFirst().orElse(0);
                ActivityLogDAO.log("CUSTOMER_ADD", no,
                        "Yeni müşteri kaydı: " + name + " " + dSurname.getText().trim(),
                        "Tür: " + type + " | TC: " + tc + " | Tel: " + dPhone.getText().trim());
            } else {
                showWarning("Müşteri kaydedilemedi (TC zaten kayıtlı olabilir).");
            }
        });

        dialog.getRootPane().setDefaultButton(saveBtn);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void addFormRow(JPanel form, GridBagConstraints gbc, int row,
                            String label, JComponent field) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(label), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(field, gbc);
    }

    private void onUpdate(int modelRow) {
        if (!canUpdate) return;
        showUpdateDialog(tableModel.getCustomerAt(modelRow));
    }

    private void onDelete(int modelRow) {
        if (!canDelete) return;
        Customer c = tableModel.getCustomerAt(modelRow);
        int answer = JOptionPane.showConfirmDialog(this,
                c.getCustomerName() + " " + c.getSurname() + " pasife alınsın mı?\n"
                        + "Müşteriye ait tüm hesaplar da pasif yapılacaktır.",
                "Silme Onayı", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            if (customerDAO.deleteCustomer(c.getCustomerId())) {
                ActivityLogDAO.log("CUSTOMER_DELETE", c.getCustomerNo(),
                        "Müşteri ve hesapları pasife alındı: " + c.getCustomerName() + " " + c.getSurname(),
                        "Müşteri No: " + c.getCustomerNo() + " | TC: " + c.getTc());
                refreshTable();
            } else {
                showWarning("Müşteri silinemedi.");
            }
        }
    }

    private void showUpdateDialog(Customer c) {
        JComboBox<String> typeCombo = new JComboBox<>(
                customerDAO.getCustomerTypes().toArray(new String[0]));
        typeCombo.setSelectedItem(c.getCustomerType());
        JTextField dName = new JTextField(c.getCustomerName());
        JTextField dSurname = new JTextField(c.getSurname());
        JTextField dTc = new JTextField(c.getTc());
        JTextField dPhone = new JTextField(c.getPhone());
        JTextField dAddress = new JTextField(c.getAddress());

        JPanel panel = new JPanel(new GridLayout(0, 2, 4, 4));
        panel.add(new JLabel("Müşteri Türü:")); panel.add(typeCombo);
        panel.add(new JLabel("Ad:"));      panel.add(dName);
        panel.add(new JLabel("Soyad:"));   panel.add(dSurname);
        panel.add(new JLabel("TC:"));      panel.add(dTc);
        panel.add(new JLabel("Telefon:")); panel.add(dPhone);
        panel.add(new JLabel("Adres:"));   panel.add(dAddress);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Müşteri Güncelle (No: " + c.getCustomerNo() + ")",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String tc = dTc.getText().trim();
            if (dName.getText().trim().isEmpty()) {
                showWarning("Ad boş olamaz.");
                return;
            }
            if (!tc.matches("\\d{11}")) {
                showWarning("TC 11 haneli ve sadece rakam olmalı.");
                return;
            }
            Customer updated = new Customer(
                    c.getCustomerId(),
                    c.getCustomerNo(),
                    (String) typeCombo.getSelectedItem(),
                    dName.getText().trim(),
                    dSurname.getText().trim(),
                    tc,
                    dPhone.getText().trim(),
                    dAddress.getText().trim());
            if (customerDAO.updateCustomer(updated)) {
                ActivityLogDAO.log("CUSTOMER_UPDATE", updated.getCustomerNo(),
                        "Müşteri güncellendi: " + updated.getCustomerName() + " " + updated.getSurname(),
                        "Müşteri No: " + updated.getCustomerNo() + " | Tür: " + updated.getCustomerType()
                                + " | TC: " + updated.getTc());
                refreshTable();
            } else {
                showWarning("Müşteri güncellenemedi.");
            }
        }
    }

    private void showWarning(String message) {
        Notify.warning(this, message);
    }

    // ================= Tablo modeli =================
    private static class CustomerTableModel extends AbstractTableModel {

        static final int COL_ACTION = 9;
        private final String[] columns =
                {"Müşteri No", "Tür", "Ad", "Soyad", "TC", "Telefon", "Adres",
                 "Kayıt Tarihi", "Kayıt Saati", "İşlemler"};
        private final boolean actionEditable;
        private List<Customer> data = new java.util.ArrayList<>();

        CustomerTableModel(boolean actionEditable) {
            this.actionEditable = actionEditable;
        }

        void setData(List<Customer> customers) {
            this.data = customers;
            fireTableDataChanged();
        }

        Customer getCustomerAt(int row) {
            return data.get(row);
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }
        @Override public boolean isCellEditable(int row, int col) {
            return col == COL_ACTION && actionEditable;
        }

        @Override
        public Object getValueAt(int row, int col) {
            Customer c = data.get(row);
            switch (col) {
                case 0: return c.getCustomerNo();
                case 1: return c.getCustomerType();
                case 2: return c.getCustomerName();
                case 3: return c.getSurname();
                case 4: return c.getTc();
                case 5: return c.getPhone();
                case 6: return c.getAddress();
                case 7: return c.getCreatedDate();
                case 8: return c.getCreatedTime();
                default: return "";
            }
        }
    }

    /** İşlem kolonu için küçük/kompakt buton (iki buton yan yana sığsın). */
    private static JButton actionButton(String text) {
        JButton b = new JButton(text);
        b.setMargin(new Insets(1, 6, 1, 6));
        b.setFont(b.getFont().deriveFont(11f));
        b.setFocusable(false);
        return b;
    }

    // ================= İşlemler kolonu: görünüm =================
    private static class ActionRenderer extends JPanel implements TableCellRenderer {
        ActionRenderer(boolean showUpdate, boolean showDelete) {
            setLayout(new FlowLayout(FlowLayout.CENTER, 6, 1));
            if (showUpdate) add(actionButton("Güncelle"));
            if (showDelete) add(actionButton("Sil"));
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            return this;
        }
    }

    // ================= İşlemler kolonu: tıklama =================
    private class ActionEditor extends AbstractCellEditor implements TableCellEditor {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 1));
        private int editingRow;

        ActionEditor(boolean showUpdate, boolean showDelete) {
            if (showUpdate) {
                JButton updateBtn = actionButton("Güncelle");
                updateBtn.addActionListener(e -> { fireEditingStopped(); onUpdate(editingRow); });
                panel.add(updateBtn);
            }
            if (showDelete) {
                JButton deleteBtn = actionButton("Sil");
                deleteBtn.addActionListener(e -> { fireEditingStopped(); onDelete(editingRow); });
                panel.add(deleteBtn);
            }
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            editingRow = table.convertRowIndexToModel(row);
            return panel;
        }

        @Override
        public Object getCellEditorValue() { return ""; }
    }
}
