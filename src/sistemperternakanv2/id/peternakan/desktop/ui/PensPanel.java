package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Kelola kandang: kapasitas, okupansi, dan status. */
public final class PensPanel extends JPanel implements MainFrame.Refreshable {
    private final ApiClient client;
    private final javax.swing.table.DefaultTableModel model = Ui.model(
            "Kode", "Nama", "Kapasitas", "Okupansi", "Status", "Catatan", "Cabang");
    private final javax.swing.JTable table = Ui.table(model);
    private List<Map<String, Object>> rows = List.of();

    public PensPanel(ApiClient client, MainFrame frame) {
        this.client = client;
        table.setName("pens.table");
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.BG);
        add(Ui.header("Kandang", "Kelola daftar kandang dan kapasitasnya"), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(Ui.toolbar(Ui.primary("Tambah Kandang", this::create),
                Ui.primary("Lihat Kambing", this::showGoats),
                Ui.button("Edit", this::edit), Ui.button("Muat Ulang", this::refresh)), BorderLayout.NORTH);
        center.add(Ui.scroll(table), BorderLayout.CENTER);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    showGoats();
                }
            }
        });
        add(center, BorderLayout.CENTER);
    }

    private void showGoats() {
        Map<String, Object> pen = selected();
        if (pen == null) {
            Ui.info(this, "Pilih kandang terlebih dahulu.");
            return;
        }
        long penId = ApiClient.lng(pen.get("id"));
        Ui.async(this, "Memuat isi kandang", () -> client.get("/api/pens/" + penId, Map.of()), data -> {
            javax.swing.table.DefaultTableModel goats = Ui.model(
                    "Kode", "Kelamin", "Jenis", "Berat (kg)", "Kesehatan", "Siap Jual", "Masuk Kandang");
            List<Map<String, Object>> list = ApiClient.list(data.get("goats"));
            for (Map<String, Object> goat : list) {
                goats.addRow(new Object[]{
                        ApiClient.str(goat.get("code")),
                        ApiClient.str(goat.get("gender")),
                        ApiClient.str(goat.get("jenis")),
                        ApiClient.number(goat.get("latest_weight")),
                        ApiClient.str(goat.get("health_status")),
                        ApiClient.str(goat.get("sale_readiness")),
                        Ui.formatDate(goat.get("entered_at"))
                });
            }
            javax.swing.JPanel content = new javax.swing.JPanel(new BorderLayout(0, 8));
            content.setOpaque(false);
            content.add(Ui.subtitle("Kandang " + ApiClient.str(data.get("code")) + " - "
                    + ApiClient.str(data.get("name")) + " · " + list.size() + "/"
                    + ApiClient.lng(data.get("capacity")) + " ekor"), BorderLayout.NORTH);
            if (list.isEmpty()) {
                content.add(Ui.area("Belum ada kambing di kandang ini."), BorderLayout.CENTER);
            } else {
                content.add(Ui.scroll(Ui.table(goats)), BorderLayout.CENTER);
            }
            content.setPreferredSize(new java.awt.Dimension(860, 380));
            Dialogs.show(this, "Isi Kandang " + ApiClient.str(data.get("code")), content);
        });
    }

    @Override
    public void refresh() {
        Ui.async(this, "Memuat kandang", () -> client.get("/api/pens", Map.of()).get("items"),
                items -> {
                    rows = ApiClient.list(items);
                    model.setRowCount(0);
                    for (Map<String, Object> pen : rows) {
                        model.addRow(new Object[]{
                                ApiClient.str(pen.get("code")),
                                ApiClient.str(pen.get("name")),
                                ApiClient.lng(pen.get("capacity")),
                                ApiClient.lng(pen.get("occupancy")),
                                ApiClient.str(pen.get("status")),
                                ApiClient.str(pen.get("notes")),
                                ApiClient.str(pen.get("branch_name"))
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
        code.setName("pen.code");
        JTextField name = Ui.field("");
        name.setName("pen.name");
        JTextField capacity = Ui.field("10");
        capacity.setName("pen.capacity");
        JTextField notes = Ui.field("");
        notes.setName("pen.notes");
        Object[] message = {"Kode", code, "Nama", name, "Kapasitas", capacity, "Catatan", notes};
        if (JOptionPane.showConfirmDialog(this, message, "Tambah Kandang",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code.getText().trim());
        body.put("name", name.getText().trim());
        body.put("capacity", capacity.getText().trim());
        body.put("notes", notes.getText().trim());
        Ui.async(this, "Menyimpan kandang", () -> client.post("/api/pens", body), ok -> refresh());
    }

    private void edit() {
        Map<String, Object> pen = selected();
        if (pen == null) {
            Ui.info(this, "Pilih kandang terlebih dahulu.");
            return;
        }
        long penId = ApiClient.lng(pen.get("id"));
        JTextField name = Ui.field(ApiClient.str(pen.get("name")));
        name.setName("pen.edit.name");
        JTextField capacity = Ui.field(ApiClient.str(pen.get("capacity")));
        capacity.setName("pen.edit.capacity");
        JComboBox<String> status = Ui.combo("AKTIF", "NONAKTIF");
        status.setSelectedItem(ApiClient.str(pen.get("status")));
        JTextField notes = Ui.field(ApiClient.str(pen.get("notes")));
        notes.setName("pen.edit.notes");
        Object[] message = {"Nama", name, "Kapasitas", capacity, "Status", status, "Catatan", notes};
        if (JOptionPane.showConfirmDialog(this, message, "Edit Kandang",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name.getText().trim());
        body.put("capacity", capacity.getText().trim());
        body.put("status", String.valueOf(status.getSelectedItem()));
        body.put("notes", notes.getText().trim());
        Ui.async(this, "Menyimpan kandang", () -> client.put("/api/pens/" + penId, body), ok -> refresh());
    }
}
