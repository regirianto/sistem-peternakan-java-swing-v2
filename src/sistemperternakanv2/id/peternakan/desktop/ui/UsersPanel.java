package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pengelola pengguna dan permission per role (owner). */
public final class UsersPanel extends JPanel implements MainFrame.Refreshable {
    private final ApiClient client;
    private final javax.swing.table.DefaultTableModel model = Ui.model(
            "Username", "Nama", "Role", "Cabang", "Aktif", "Ganti password", "Dibuat");
    private final javax.swing.JTable table = Ui.table(model);
    private List<Map<String, Object>> rows = List.of();

    public UsersPanel(ApiClient client, MainFrame frame) {
        this.client = client;
        table.setName("users.table");
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.BG);
        add(Ui.header("Pengguna", "Akun, role, dan permission per role"), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        JPanel actions = Ui.toolbar(Ui.primary("Pengguna Baru", this::create),
                Ui.button("Edit", this::edit),
                Ui.button("Permission Role", this::permissions));
        center.add(actions, BorderLayout.NORTH);
        center.add(Ui.scroll(table), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        Ui.async(this, "Memuat pengguna", () -> client.get("/api/users", Map.of()), data -> {
            rows = ApiClient.list(data.get("items"));
            model.setRowCount(0);
            for (Map<String, Object> row : rows) {
                model.addRow(new Object[]{
                        ApiClient.str(row.get("username")),
                        ApiClient.str(row.get("full_name")),
                        ApiClient.str(row.get("role_code")),
                        ApiClient.str(row.get("branch_names")),
                        ApiClient.bool(row.get("active")),
                        ApiClient.bool(row.get("must_change_password")),
                        Ui.formatDate(row.get("created_at"))
                });
            }
        });
    }

    private Map<String, Object> selected() {
        int view = table.getSelectedRow();
        return view < 0 ? null : rows.get(view);
    }

    private void create() {
        Ui.async(this, "Memuat cabang", () -> client.get("/api/branches", Map.of()).get("items"), items -> {
            List<Map<String, Object>> branches = ApiClient.list(items);
            JTextField username = Ui.field("");
            username.setName("user.username");
            JPasswordField password = new JPasswordField();
            password.setName("user.password");
            JTextField fullName = Ui.field("");
            fullName.setName("user.fullname");
            JTextField roleCode = Ui.field("PETUGAS");
            roleCode.setName("user.role");
            List<Long> defaultBranch = branches.isEmpty() ? List.of()
                    : List.of(ApiClient.lng(branches.get(0).get("id")));
            Map<Long, javax.swing.JCheckBox> checks = branchChecks(branches, defaultBranch);
            if (!Dialogs.ok(this, "Pengguna Baru", Ui.label("Username"), username,
                    Ui.label("Password (min. 8)"), password,
                    Ui.label("Nama lengkap"), fullName, Ui.label("Kode role"), roleCode,
                    Ui.label("Cabang"), branchPanel(checks))) {
                return;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("username", username.getText().trim());
            body.put("password", String.valueOf(password.getPassword()));
            body.put("fullName", fullName.getText().trim());
            body.put("roleCode", roleCode.getText().trim());
            Ui.async(this, "Membuat pengguna", () -> client.post("/api/users", body),
                    created -> assignBranches(ApiClient.lng(created.get("id")), selectedBranches(checks)));
        });
    }

    private void edit() {
        Map<String, Object> row = selected();
        if (row == null) {
            Ui.info(this, "Pilih pengguna terlebih dahulu.");
            return;
        }
        long id = ApiClient.lng(row.get("id"));
        Ui.async(this, "Memuat cabang", () -> client.get("/api/branches", Map.of()).get("items"), items -> {
            List<Map<String, Object>> branches = ApiClient.list(items);
            List<Long> assigned = ApiClient.stringList(row.get("branch_ids")).stream()
                    .map(Long::parseLong).toList();
            JTextField fullName = Ui.field(ApiClient.str(row.get("full_name")));
            fullName.setName("user.edit.fullname");
            JTextField roleCode = Ui.field(ApiClient.str(row.get("role_code")));
            roleCode.setName("user.edit.role");
            javax.swing.JCheckBox active = new javax.swing.JCheckBox("Aktif",
                    ApiClient.bool(row.get("active")));
            JPasswordField fresh = new JPasswordField();
            Map<Long, javax.swing.JCheckBox> checks = branchChecks(branches, assigned);
            if (!Dialogs.ok(this, "Edit Pengguna", Ui.label("Nama lengkap"), fullName,
                    Ui.label("Kode role"), roleCode, Ui.label("Cabang"), branchPanel(checks),
                    Ui.label("Status"), active, Ui.label("Password baru (opsional)"), fresh)) {
                return;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("fullName", fullName.getText().trim());
            body.put("roleCode", roleCode.getText().trim());
            body.put("active", active.isSelected());
            String next = String.valueOf(fresh.getPassword());
            body.put("newPassword", next.isBlank() ? null : next);
            Ui.async(this, "Menyimpan pengguna", () -> client.put("/api/users/" + id, body),
                    ok -> assignBranches(id, selectedBranches(checks)));
        });
    }

    private Map<Long, javax.swing.JCheckBox> branchChecks(List<Map<String, Object>> branches,
                                                          List<Long> selected) {
        Map<Long, javax.swing.JCheckBox> checks = new LinkedHashMap<>();
        for (Map<String, Object> branch : branches) {
            long id = ApiClient.lng(branch.get("id"));
            checks.put(id, new javax.swing.JCheckBox(ApiClient.str(branch.get("code")) + " - "
                    + ApiClient.str(branch.get("name")), selected.contains(id)));
        }
        return checks;
    }

    private javax.swing.JPanel branchPanel(Map<Long, javax.swing.JCheckBox> checks) {
        javax.swing.JPanel panel = new javax.swing.JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        checks.values().forEach(panel::add);
        return panel;
    }

    private List<Long> selectedBranches(Map<Long, javax.swing.JCheckBox> checks) {
        return checks.entrySet().stream().filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey).toList();
    }

    private void assignBranches(long userId, List<Long> branchIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("branchIds", branchIds);
        Ui.async(this, "Menyimpan akses cabang",
                () -> client.put("/api/users/" + userId + "/branches", body), ok -> refresh());
    }

    private void permissions() {
        Ui.async(this, "Memuat permission", () -> client.get("/api/users/permissions", Map.of()),
                data -> new PermissionDialog(this, client, data, this::refresh));
    }
}
