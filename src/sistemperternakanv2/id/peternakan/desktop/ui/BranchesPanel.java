package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Kelola cabang (Pusat, Cikarang) khusus owner. */
public final class BranchesPanel extends JPanel implements MainFrame.Refreshable {
    private final ApiClient client;
    private final javax.swing.table.DefaultTableModel model = Ui.model(
            "Kode", "Nama", "Tipe", "Alamat", "Telepon", "Aktif", "Kandang", "Kambing", "Pengguna");
    private final javax.swing.JTable table = Ui.table(model);
    private List<Map<String, Object>> rows = List.of();

    public BranchesPanel(ApiClient client, MainFrame frame) {
        this.client = client;
        table.setName("branches.table");
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.BG);
        add(Ui.header("Cabang", "Pusat dan cabang operasional. Setiap cabang punya kandang dan pembukuan sendiri."),
                BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(Ui.toolbar(Ui.primary("Cabang Baru", this::create),
                Ui.button("Edit", this::edit), Ui.button("Muat Ulang", this::refresh)), BorderLayout.NORTH);
        center.add(Ui.scroll(table), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        Ui.async(this, "Memuat cabang", () -> client.get("/api/branches/all", Map.of()).get("items"), items -> {
            rows = ApiClient.list(items);
            model.setRowCount(0);
            for (Map<String, Object> branch : rows) {
                model.addRow(new Object[]{
                        ApiClient.str(branch.get("code")),
                        ApiClient.str(branch.get("name")),
                        ApiClient.str(branch.get("branch_type")),
                        ApiClient.str(branch.get("address")),
                        ApiClient.str(branch.get("phone")),
                        ApiClient.bool(branch.get("active")),
                        ApiClient.lng(branch.get("pen_count")),
                        ApiClient.lng(branch.get("goat_count")),
                        ApiClient.lng(branch.get("user_count"))
                });
            }
        });
    }

    private Map<String, Object> selected() {
        int view = table.getSelectedRow();
        return view < 0 ? null : rows.get(view);
    }

    private void create() {
        JTextField code = Ui.field("");
        code.setName("branch.code");
        JTextField name = Ui.field("");
        name.setName("branch.name");
        JComboBox<String> type = Ui.combo("CABANG", "PUSAT");
        type.setName("branch.type");
        JTextField address = Ui.field("");
        address.setName("branch.address");
        JTextField phone = Ui.field("");
        phone.setName("branch.phone");
        if (!Dialogs.ok(this, "Cabang Baru", Ui.label("Kode"), code, Ui.label("Nama"), name,
                Ui.label("Tipe"), type, Ui.label("Alamat"), address, Ui.label("Telepon"), phone)) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code.getText().trim());
        body.put("name", name.getText().trim());
        body.put("type", String.valueOf(type.getSelectedItem()));
        body.put("address", address.getText().trim());
        body.put("phone", phone.getText().trim());
        Ui.async(this, "Menyimpan cabang", () -> client.post("/api/branches", body), ok -> refresh());
    }

    private void edit() {
        Map<String, Object> branch = selected();
        if (branch == null) {
            Ui.info(this, "Pilih cabang terlebih dahulu.");
            return;
        }
        long id = ApiClient.lng(branch.get("id"));
        JTextField name = Ui.field(ApiClient.str(branch.get("name")));
        name.setName("branch.edit.name");
        JComboBox<String> type = Ui.combo("PUSAT", "CABANG");
        type.setSelectedItem(ApiClient.str(branch.get("branch_type")));
        JTextField address = Ui.field(ApiClient.str(branch.get("address")));
        address.setName("branch.edit.address");
        JTextField phone = Ui.field(ApiClient.str(branch.get("phone")));
        phone.setName("branch.edit.phone");
        javax.swing.JCheckBox active = new javax.swing.JCheckBox("Aktif", ApiClient.bool(branch.get("active")));
        if (!Dialogs.ok(this, "Edit Cabang", Ui.label("Nama"), name, Ui.label("Tipe"), type,
                Ui.label("Alamat"), address, Ui.label("Telepon"), phone, Ui.label("Status"), active)) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name.getText().trim());
        body.put("type", String.valueOf(type.getSelectedItem()));
        body.put("address", address.getText().trim());
        body.put("phone", phone.getText().trim());
        body.put("active", active.isSelected());
        Ui.async(this, "Menyimpan cabang", () -> client.put("/api/branches/" + id, body), ok -> refresh());
    }
}
