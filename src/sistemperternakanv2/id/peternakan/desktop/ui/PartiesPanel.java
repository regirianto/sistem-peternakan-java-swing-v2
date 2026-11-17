package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;
import id.peternakan.desktop.api.ApiClientException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Kelola data supplier dan pelanggan. */
public final class PartiesPanel extends JPanel implements MainFrame.Refreshable {
    private final ApiClient client;
    private final javax.swing.table.DefaultTableModel model = Ui.model(
            "Kode", "Nama", "Peran", "Telepon", "Alamat", "Aktif", "Catatan");
    private final javax.swing.JTable table = Ui.table(model);
    private final List<Object[]> index = new java.util.ArrayList<>();
    private final JTextField q = Ui.field("");
    private String roleFilter = "";

    public PartiesPanel(ApiClient client, MainFrame frame) {
        this.client = client;
        table.setName("parties.table");
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.BG);
        add(Ui.header("Supplier & Pelanggan", "Data mitra untuk pembelian dan penjualan"), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(Ui.toolbar(
                Ui.primary("Tambah", this::create), Ui.button("Edit", this::edit),
                Ui.label("Cari"), q, Ui.button("Cari", this::refresh),
                Ui.button("Semua", () -> {
                    roleFilter = "";
                    refresh();
                })), BorderLayout.NORTH);
        center.add(Ui.scroll(table), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("role", roleFilter);
        query.put("q", q.getText().trim());
        query.put("size", "200");
        Ui.async(this, "Memuat mitra", () -> client.get("/api/parties", query).get("items"), items -> {
            index.clear();
            model.setRowCount(0);
            for (Map<String, Object> party : ApiClient.list(items)) {
                List<String> roles = ApiClient.stringList(party.get("roles"));
                Object[] row = new Object[]{
                        ApiClient.str(party.get("code")),
                        ApiClient.str(party.get("name")),
                        String.join(", ", roles),
                        ApiClient.str(party.get("phone")),
                        ApiClient.str(party.get("address")),
                        Boolean.TRUE.equals(party.get("active")) ? "Aktif" : "Non-aktif",
                        ApiClient.str(party.get("notes"))
                };
                index.add(new Object[]{ApiClient.lng(party.get("id")), roles});
                model.addRow(row);
            }
        });
    }

    private int selectedRow() {
        return table.getSelectedRow();
    }

    private void create() {
        JTextField code = Ui.field("");
        code.setName("party.code");
        JTextField name = Ui.field("");
        name.setName("party.name");
        JTextField phone = Ui.field("");
        phone.setName("party.phone");
        JTextField address = Ui.field("");
        address.setName("party.address");
        JTextField notes = Ui.field("");
        notes.setName("party.notes");
        javax.swing.JCheckBox supplier = new javax.swing.JCheckBox("Supplier");
        javax.swing.JCheckBox customer = new javax.swing.JCheckBox("Pelanggan");
        java.util.List<Object> list = java.util.Arrays.asList("Kode", code, "Nama", name,
                "Peran", supplier, customer, "Telepon", phone, "Alamat", address, "Catatan", notes);
        if (JOptionPane.showConfirmDialog(this, list.toArray(), "Tambah Mitra",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        Map<String, Object> body = body(code.getText().trim(), name.getText().trim(), supplier,
                customer, phone.getText().trim(), address.getText().trim(), notes.getText().trim());
        Ui.async(this, "Menyimpan mitra", () -> client.post("/api/parties", body), ok -> refresh());
    }

    private void edit() {
        int row = selectedRow();
        if (row < 0) {
            Ui.info(this, "Pilih mitra terlebih dahulu.");
            return;
        }
        long id = ((Number) index.get(row)[0]).longValue();
        Map<String, Object> party = findParty(id);
        if (party == null) {
            return;
        }
        JTextField code = Ui.field(ApiClient.str(party.get("code")));
        code.setEnabled(false);
        JTextField name = Ui.field(ApiClient.str(party.get("name")));
        name.setName("party.edit.name");
        JTextField phone = Ui.field(ApiClient.str(party.get("phone")));
        phone.setName("party.edit.phone");
        JTextField address = Ui.field(ApiClient.str(party.get("address")));
        address.setName("party.edit.address");
        JTextField notes = Ui.field(ApiClient.str(party.get("notes")));
        notes.setName("party.edit.notes");
        javax.swing.JCheckBox supplier = new javax.swing.JCheckBox("Supplier");
        javax.swing.JCheckBox customer = new javax.swing.JCheckBox("Pelanggan");
        supplier.setSelected(hasRole(party, "SUPPLIER"));
        customer.setSelected(hasRole(party, "CUSTOMER"));
        java.util.List<Object> list = java.util.Arrays.asList("Kode", code, "Nama", name,
                "Peran", supplier, customer, "Telepon", phone, "Alamat", address, "Catatan", notes);
        if (JOptionPane.showConfirmDialog(this, list.toArray(), "Edit Mitra",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        Map<String, Object> body = body(code.getText().trim(), name.getText().trim(), supplier,
                customer, phone.getText().trim(), address.getText().trim(), notes.getText().trim());
        Ui.async(this, "Menyimpan mitra", () -> client.put("/api/parties/" + id, body), ok -> refresh());
    }

    private boolean hasRole(Map<String, Object> party, String role) {
        return ApiClient.stringList(party.get("roles")).contains(role);
    }

    private Map<String, Object> body(String code, String name, javax.swing.JCheckBox supplier,
                                     javax.swing.JCheckBox customer, String phone, String address,
                                     String notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("name", name);
        java.util.List<String> rolesList = new java.util.ArrayList<>();
        if (supplier.isSelected()) {
            rolesList.add("SUPPLIER");
        }
        if (customer.isSelected()) {
            rolesList.add("CUSTOMER");
        }
        body.put("roles", rolesList);
        body.put("phone", phone);
        body.put("address", address);
        body.put("notes", notes);
        return body;
    }

    private Map<String, Object> findParty(long id) {
        try {
            return client.get("/api/parties/" + id, Map.of());
        } catch (ApiClientException e) {
            return null;
        }
    }
}
