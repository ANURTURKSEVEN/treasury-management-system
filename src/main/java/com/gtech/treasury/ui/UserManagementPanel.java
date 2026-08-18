package com.gtech.treasury.ui;

import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.dao.UserDAO;
import com.gtech.treasury.model.User;
import com.gtech.treasury.util.Notify;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.List;

/**
 * Kullanıcı Yönetimi paneli (dashboard içinde gösterilir). Yalnızca ADMIN.
 */
public class UserManagementPanel extends JPanel {

    private static final String[] ROLES = {"ADMIN", "TRADER", "VIEWER"};

    private final UserDAO userDAO = new UserDAO();
    private final User currentUser;

    private final JTextField usernameField = new JTextField(12);
    private final JPasswordField passwordField = new JPasswordField(12);
    private final JTextField fullNameField = new JTextField(12);
    private final JComboBox<String> roleCombo = new JComboBox<>(ROLES);

    private final UserTableModel tableModel = new UserTableModel();
    private final JTable userTable = new JTable(tableModel);

    public UserManagementPanel(User currentUser) {
        this.currentUser = currentUser;

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Yetki: yalnızca ADMIN kullanıcı yönetebilir (yetki yükseltme engeli)
        if (!"ADMIN".equals(currentUser.getRole())) {
            JLabel warn = new JLabel("Bu ekran yalnızca ADMIN kullanıcılar içindir.", SwingConstants.CENTER);
            warn.setFont(warn.getFont().deriveFont(Font.BOLD, 16f));
            add(warn, BorderLayout.CENTER);
            return;
        }

        add(buildHeader(), BorderLayout.NORTH);
        add(buildTablePanel(), BorderLayout.CENTER);

        loadUsers();
    }

    private JComponent buildHeader() {
        JPanel north = new JPanel(new BorderLayout(0, 8));
        JLabel title = new JLabel("Kullanıcı Yönetimi");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        north.add(title, BorderLayout.NORTH);
        north.add(buildFormPanel(), BorderLayout.CENTER);
        return north;
    }

    private JPanel buildFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Yeni Kullanıcı"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("Kullanıcı Adı:"), gbc);
        gbc.gridx = 1;                panel.add(usernameField, gbc);
        gbc.gridx = 2;                panel.add(new JLabel("Şifre:"), gbc);
        gbc.gridx = 3;                panel.add(passwordField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; panel.add(new JLabel("Ad Soyad:"), gbc);
        gbc.gridx = 1;                panel.add(fullNameField, gbc);
        gbc.gridx = 2;                panel.add(new JLabel("Rol:"), gbc);
        gbc.gridx = 3;                panel.add(roleCombo, gbc);

        JButton saveButton = new JButton("Kullanıcı Ekle");
        saveButton.addActionListener(e -> saveNewUser());
        gbc.gridx = 3; gbc.gridy = 2; gbc.anchor = GridBagConstraints.EAST;
        panel.add(saveButton, gbc);

        return panel;
    }

    private JScrollPane buildTablePanel() {
        userTable.setRowHeight(30);
        userTable.getColumnModel().getColumn(UserTableModel.COL_ACTION).setPreferredWidth(220);
        userTable.getColumnModel().getColumn(UserTableModel.COL_ACTION)
                .setCellRenderer(new ActionRenderer());
        userTable.getColumnModel().getColumn(UserTableModel.COL_ACTION)
                .setCellEditor(new ActionEditor());
        return new JScrollPane(userTable);
    }

    // ---------------- İş mantığı ----------------

    private void loadUsers() {
        tableModel.setData(userDAO.getStaffUsers());   // sadece ADMIN/TRADER/VIEWER
    }

    private void saveNewUser() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String fullName = fullNameField.getText().trim();
        String role = (String) roleCombo.getSelectedItem();

        if (username.isEmpty() || password.isEmpty()) {
            showWarning("Kullanıcı adı ve şifre boş olamaz.");
            return;
        }

        if (userDAO.addUser(username, password, role, fullName)) {
            ActivityLogDAO.log("USER_ADD", 0, "Yeni kullanıcı: " + username,
                    "Rol: " + role + " | Ad: " + fullName);
            usernameField.setText("");
            passwordField.setText("");
            fullNameField.setText("");
            roleCombo.setSelectedIndex(0);
            loadUsers();
        } else {
            showWarning("Kullanıcı eklenemedi (kullanıcı adı zaten var olabilir).");
        }
    }

    private void onChangeRole(int modelRow) {
        User u = tableModel.getUserAt(modelRow);
        String newRole = (String) JOptionPane.showInputDialog(this,
                u.getUsername() + " için yeni rol:", "Rol Değiştir",
                JOptionPane.PLAIN_MESSAGE, null, ROLES, u.getRole());

        if (newRole != null && !newRole.equals(u.getRole())) {
            if (userDAO.updateRole(u.getId(), newRole)) {
                ActivityLogDAO.log("ROLE_CHANGE", 0,
                        "Rol değişti: " + u.getUsername(),
                        u.getRole() + " → " + newRole);
                loadUsers();
            } else {
                showWarning("Rol değiştirilemedi.");
            }
        }
    }

    private void onDelete(int modelRow) {
        User u = tableModel.getUserAt(modelRow);

        if (u.getId() == currentUser.getId()) {
            showWarning("Kendi hesabınızı silemezsiniz.");
            return;
        }

        int answer = JOptionPane.showConfirmDialog(this,
                u.getUsername() + " kullanıcısı silinsin mi?",
                "Silme Onayı", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            if (userDAO.deleteUser(u.getId())) {
                ActivityLogDAO.log("USER_DELETE", 0, "Kullanıcı silindi: " + u.getUsername(),
                        "Rol: " + u.getRole());
                loadUsers();
            } else {
                showWarning("Kullanıcı silinemedi.");
            }
        }
    }

    private void showWarning(String message) {
        Notify.warning(this, message);
    }

    // ---------------- Tablo modeli ----------------
    private static class UserTableModel extends AbstractTableModel {

        static final int COL_ACTION = 4;
        private final String[] columns = {"ID", "Kullanıcı Adı", "Rol", "Ad Soyad", "İşlemler"};
        private List<User> data = new java.util.ArrayList<>();

        void setData(List<User> users) {
            this.data = users;
            fireTableDataChanged();
        }

        User getUserAt(int row) {
            return data.get(row);
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }
        @Override public boolean isCellEditable(int row, int col) { return col == COL_ACTION; }

        @Override
        public Object getValueAt(int row, int col) {
            User u = data.get(row);
            switch (col) {
                case 0: return u.getId();
                case 1: return u.getUsername();
                case 2: return u.getRole();
                case 3: return u.getFullName();
                default: return "";
            }
        }
    }

    private static class ActionRenderer extends JPanel implements TableCellRenderer {
        ActionRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, 4, 2));
            add(new JButton("Rol Değiştir"));
            add(new JButton("Sil"));
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            return this;
        }
    }

    private class ActionEditor extends AbstractCellEditor implements TableCellEditor {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        private int editingRow;

        ActionEditor() {
            JButton roleBtn = new JButton("Rol Değiştir");
            JButton deleteBtn = new JButton("Sil");
            roleBtn.addActionListener(e -> { fireEditingStopped(); onChangeRole(editingRow); });
            deleteBtn.addActionListener(e -> { fireEditingStopped(); onDelete(editingRow); });
            panel.add(roleBtn);
            panel.add(deleteBtn);
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
