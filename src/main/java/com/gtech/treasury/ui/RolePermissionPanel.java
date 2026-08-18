package com.gtech.treasury.ui;

import com.gtech.treasury.dao.ActivityLogDAO;
import com.gtech.treasury.dao.PermissionDAO;
import com.gtech.treasury.model.Role;
import com.gtech.treasury.model.Screen;
import com.gtech.treasury.util.Notify;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rol - Menü İlişki Tanımı ekranı (yalnızca ADMIN).
 *
 * Solda rol listesi, sağda o rolün erişebileceği menüler (işaretlenebilir).
 * Rol seç → menüleri işaretle → Kaydet.
 */
public class RolePermissionPanel extends JPanel {

    private final PermissionDAO permissionDAO = new PermissionDAO();

    private final DefaultListModel<Role> roleListModel = new DefaultListModel<>();
    private final JList<Role> roleList = new JList<>(roleListModel);

    // screen_id -> checkbox
    private final Map<Integer, JCheckBox> screenChecks = new LinkedHashMap<>();
    private final JPanel screenPanel = new JPanel();

    private List<Screen> allScreens;

    public RolePermissionPanel() {
        setLayout(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("Rol - Menü İlişki Tanımı");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        add(title, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildRolePanel(), buildScreenPanel());
        split.setResizeWeight(0.4);
        add(split, BorderLayout.CENTER);

        add(buildBottomBar(), BorderLayout.SOUTH);

        loadRoles();
        loadScreens();
    }

    // ---- Sol: Rol Listesi ----
    private JComponent buildRolePanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Rol Listesi"));

        roleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roleList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showPermissionsForSelectedRole();
            }
        });
        panel.add(new JScrollPane(roleList), BorderLayout.CENTER);
        return panel;
    }

    // ---- Sağ: Menü (ekran) işaretleme ----
    private JComponent buildScreenPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Rol - Menü Listesi"));

        screenPanel.setLayout(new BoxLayout(screenPanel, BoxLayout.Y_AXIS));
        screenPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.add(new JScrollPane(screenPanel), BorderLayout.CENTER);
        return panel;
    }

    // ---- Alt: Kaydet ----
    private JComponent buildBottomBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Kaydet");
        saveButton.addActionListener(e -> savePermissions());
        panel.add(saveButton);
        return panel;
    }

    // ---- Veri yükleme ----
    private void loadRoles() {
        roleListModel.clear();
        for (Role r : permissionDAO.getAllRoles()) {
            roleListModel.addElement(r);
        }
    }

    private void loadScreens() {
        allScreens = permissionDAO.getAllScreens();
        screenPanel.removeAll();
        screenChecks.clear();
        for (Screen s : allScreens) {
            JCheckBox cb = new JCheckBox(s.getScreenName() + "   (" + s.getScreenKey() + ")");
            cb.setEnabled(false);   // rol seçilene kadar pasif
            screenChecks.put(s.getScreenId(), cb);
            screenPanel.add(cb);
        }
        screenPanel.revalidate();
        screenPanel.repaint();
    }

    /** Seçili rolün yetkilerini checkbox'lara yansıtır. */
    private void showPermissionsForSelectedRole() {
        Role role = roleList.getSelectedValue();
        if (role == null) return;

        Set<Integer> allowed = permissionDAO.getAllowedScreenIds(role.getRoleId());
        for (Map.Entry<Integer, JCheckBox> entry : screenChecks.entrySet()) {
            JCheckBox cb = entry.getValue();
            cb.setEnabled(true);
            cb.setSelected(allowed.contains(entry.getKey()));
        }
    }

    private void savePermissions() {
        Role role = roleList.getSelectedValue();
        if (role == null) {
            Notify.warning(this, "Önce soldan bir rol seçin.");
            return;
        }

        List<Integer> selected = new ArrayList<>();
        for (Map.Entry<Integer, JCheckBox> entry : screenChecks.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }

        if (permissionDAO.savePermissions(role.getRoleId(), selected)) {
            ActivityLogDAO.log("PERMISSION_UPDATE", 0,
                    "Rol yetkileri güncellendi: " + role.getRoleName(),
                    "Seçili ekran sayısı: " + selected.size());
            Notify.info(this, role.getRoleName() + " rolünün yetkileri kaydedildi. "
                    + "Değişiklik, ilgili kullanıcı bir sonraki girişinde geçerli olur.");
        } else {
            Notify.error(this, "Yetkiler kaydedilemedi.");
        }
    }
}
