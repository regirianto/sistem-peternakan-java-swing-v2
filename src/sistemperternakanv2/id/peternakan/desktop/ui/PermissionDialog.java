package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Matriks permission per role (owner). */
public final class PermissionDialog extends JDialog {
    private final ApiClient client;
    private final Map<String, Object> catalog;
    private final Runnable done;

    PermissionDialog(JPanel parent, ApiClient client, Map<String, Object> catalog, Runnable done) {
        this.client = client;
        this.catalog = catalog;
        this.done = done;
        setTitle("Permission Role");
        setModal(true);
        setSize(720, 560);
        setLocationRelativeTo(parent);

        List<Map<String, Object>> roles = ApiClient.list(catalog.get("roles"));
        List<Map<String, Object>> permissions = ApiClient.list(catalog.get("permissions"));
        List<String> ownerOnly = ApiClient.stringList(catalog.get("ownerOnly"));

        javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
        for (Map<String, Object> role : roles) {
            if ("OWNER".equals(ApiClient.str(role.get("code")))) {
                continue; // role OWNER selalu penuh
            }
            List<String> granted = ApiClient.stringList(role.get("permissions"));
            JPanel pane = new JPanel(new java.awt.GridBagLayout());
            pane.setOpaque(false);
            java.awt.GridBagConstraints g = new java.awt.GridBagConstraints();
            g.gridx = 0;
            g.gridy = 0;
            g.anchor = java.awt.GridBagConstraints.WEST;
            g.insets = new java.awt.Insets(4, 8, 4, 8);
            Map<String, javax.swing.JCheckBox> boxes = new LinkedHashMap<>();
            for (Map<String, Object> permission : permissions) {
                String code = ApiClient.str(permission.get("code"));
                javax.swing.JCheckBox box = new javax.swing.JCheckBox(code + " - "
                        + ApiClient.str(permission.get("description")));
                box.setSelected(granted.contains(code));
                if (ownerOnly.contains(code)) {
                    box.setEnabled(false);
                    box.setSelected(true);
                }
                boxes.put(code, box);
                pane.add(box, g);
                g.gridy++;
            }
            Map<String, javax.swing.JCheckBox> snapshot = boxes;
            javax.swing.JButton save = Ui.primary("Simpan " + ApiClient.str(role.get("code")),
                    () -> this.save(ApiClient.str(role.get("code")), snapshot));
            javax.swing.JPanel header = new JPanel(new java.awt.BorderLayout());
            header.setOpaque(false);
            header.add(save, java.awt.BorderLayout.EAST);
            javax.swing.JPanel tab = new JPanel(new java.awt.BorderLayout());
            tab.setOpaque(false);
            tab.add(header, java.awt.BorderLayout.NORTH);
            tab.add(new javax.swing.JScrollPane(pane), java.awt.BorderLayout.CENTER);
            tabs.addTab(ApiClient.str(role.get("code")), tab);
        }
        add(tabs, java.awt.BorderLayout.CENTER);
        javax.swing.JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.add(Ui.button("Tutup", this::dispose));
        add(actions, java.awt.BorderLayout.SOUTH);
        setVisible(true);
    }

    private void save(String roleCode, Map<String, javax.swing.JCheckBox> boxes) {
        List<String> selected = new java.util.ArrayList<>();
        for (Map.Entry<String, javax.swing.JCheckBox> entry : boxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("permissions", selected);
        Ui.async(this, "Menyimpan permission",
                () -> client.put("/api/users/roles/" + roleCode + "/permissions", body), ok -> {
                    Ui.info(this, "Permission role " + roleCode + " diperbarui. Sesi terdampak kedaluwarsa.");
                    dispose();
                    done.run();
                });
    }
}
