package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;
import id.peternakan.desktop.api.ApiClientException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Daftar kambing beserta aksi operasional (timbang, kesehatan, pindah, kematian). */
public final class GoatsPanel extends JPanel implements MainFrame.Refreshable {
    private final ApiClient client;
    private final javax.swing.table.DefaultTableModel model = Ui.model(
            "Kode", "Kandang", "Supplier", "Jenis", "Berat (kg)", "Kesehatan", "Kelayakan", "Status", "Masuk", "Cabang");
    private final javax.swing.JTable table = Ui.table(model);
    private List<Map<String, Object>> rows = List.of();
    private final JTextField q = Ui.field("");

    public GoatsPanel(ApiClient client, MainFrame frame) {
        this.client = client;
        table.setName("goats.table");
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.BG);
        add(Ui.header("Data Kambing", "Identitas unik, riwayat, dan kegiatan harian"), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        JPanel actions = Ui.toolbar(
                Ui.primary("Detail", this::detail),
                Ui.button("Timbang", this::weigh),
                Ui.button("Kesehatan", this::health),
                Ui.button("Pindah Kandang", this::move),
                Ui.label("Cari"), q, Ui.button("Cari", this::refresh));
        if (client.owner()) {
            actions.add(Ui.danger("Kematian", this::death));
        }
        center.add(actions, BorderLayout.NORTH);
        JScrollPane scroll = Ui.scroll(table);
        center.add(scroll, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("q", q.getText().trim());
        query.put("size", "200");
        Ui.async(this, "Memuat kambing", () -> client.get("/api/goats", query).get("items"), items -> {
            rows = ApiClient.list(items);
            model.setRowCount(0);
            for (Map<String, Object> goat : rows) {
                model.addRow(new Object[]{
                        ApiClient.str(goat.get("code")),
                        ApiClient.str(goat.get("pen_name")),
                        ApiClient.str(goat.get("supplier_name")),
                        ApiClient.str(goat.get("jenis")),
                        goat.get("latest_weight"),
                        ApiClient.str(goat.get("health_status")),
                        ApiClient.str(goat.get("sale_readiness")),
                        ApiClient.str(goat.get("status")),
                        Ui.formatDate(goat.get("entry_date")),
                        ApiClient.str(goat.get("branch_name"))
                });
            }
        });
    }

    private Map<String, Object> selected() {
        int view = table.getSelectedRow();
        return view < 0 ? null : rows.get(view);
    }

    private Long selectedId() {
        Map<String, Object> goat = selected();
        if (goat == null) {
            Ui.info(this, "Pilih kambing terlebih dahulu.");
            return null;
        }
        return ApiClient.lng(goat.get("id"));
    }

    private void detail() {
        Long id = selectedId();
        if (id == null) {
            return;
        }
        Ui.async(this, "Memuat detail", () -> client.get("/api/goats/" + id, Map.of()),
                goat -> new GoatDialog(this, client, goat));
    }

    private void weigh() {
        Long id = selectedId();
        if (id == null) {
            return;
        }
        JTextField date = Ui.field(Ui.today());
        date.setName("weigh.date");
        JTextField weight = Ui.field("");
        weight.setName("weigh.value");
        JTextField notes = Ui.field("");
        notes.setName("weigh.notes");
        if (!Dialogs.ok(this, "Timbang Kambing", Ui.label("Tanggal"), date,
                Ui.label("Berat (kg)"), weight, Ui.label("Catatan"), notes)) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("measuredAt", date.getText().trim());
        body.put("weight", weight.getText().trim());
        body.put("notes", notes.getText().trim());
        Ui.async(this, "Menyimpan penimbangan", () -> client.post("/api/goats/" + id + "/weights", body),
                ok -> refresh());
    }

    private void health() {
        Long id = selectedId();
        if (id == null) {
            return;
        }
        JTextField date = Ui.field(Ui.today());
        date.setName("health.date");
        JTextField complaint = Ui.field("");
        complaint.setName("health.complaint");
        JTextField action = Ui.field("");
        action.setName("health.action");
        JTextField medication = Ui.field("");
        medication.setName("health.medication");
        JTextField cost = Ui.field("0");
        cost.setName("health.cost");
        JComboBox<String> status = Ui.combo("SEHAT", "SAKIT", "RAWAT", "SEMBUH");
        if (!Dialogs.ok(this, "Catatan Kesehatan", Ui.label("Tanggal"), date,
                Ui.label("Keluhan"), complaint, Ui.label("Tindakan"), action,
                Ui.label("Obat"), medication, Ui.label("Biaya"), cost,
                Ui.label("Status kesehatan"), status)) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("occurredAt", date.getText().trim());
        body.put("complaint", complaint.getText().trim());
        body.put("examination", complaint.getText().trim());
        body.put("action", action.getText().trim());
        body.put("medication", medication.getText().trim());
        body.put("cost", cost.getText().trim());
        body.put("healthStatus", String.valueOf(status.getSelectedItem()));
        Ui.async(this, "Menyimpan catatan kesehatan", () -> client.post("/api/goats/" + id + "/health", body),
                ok -> refresh());
    }

    private void move() {
        Long id = selectedId();
        if (id == null) {
            return;
        }
        Ui.async(this, "Memuat kandang", () -> client.get("/api/pens", Map.of()).get("items"),
                items -> {
                    List<Map<String, Object>> pens = ApiClient.list(items);
                    JComboBox<Object> pen = new JComboBox<>(pens.stream()
                            .map(p -> ApiClient.str(p.get("code")) + " - " + ApiClient.str(p.get("name")))
                            .toArray());
                    pen.setName("move.pen");
                    JTextField date = Ui.field(Ui.today());
                    date.setName("move.date");
                    JTextField reason = Ui.field("");
                    reason.setName("move.reason");
                    if (!Dialogs.ok(this, "Pindah Kandang", Ui.label("Kandang tujuan"), pen,
                            Ui.label("Tanggal"), date, Ui.label("Alasan"), reason)) {
                        return;
                    }
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("penId", ApiClient.lng(pens.get(pen.getSelectedIndex()).get("id")));
                    body.put("date", date.getText().trim());
                    body.put("reason", reason.getText().trim());
                    Ui.async(this, "Memindahkan kambing",
                            () -> client.post("/api/goats/" + id + "/move", body), ok -> refresh());
                });
    }

    private void death() {
        Long id = selectedId();
        if (id == null) {
            return;
        }
        if (!Ui.confirm(this, "Tandai kambing ini mati? Tindakan ini mencatat penghapusan persediaan.")) {
            return;
        }
        JTextField date = Ui.field(Ui.today());
        date.setName("death.date");
        JTextField reason = Ui.field("");
        reason.setName("death.reason");
        if (!Dialogs.ok(this, "Konfirmasi Kematian", Ui.label("Tanggal kematian"), date,
                Ui.label("Alasan / penyebab"), reason)) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deathDate", date.getText().trim());
        body.put("reason", reason.getText().trim());
        body.put("requestId", UUID.randomUUID().toString());
        Ui.async(this, "Merekam kematian", () -> client.post("/api/goats/" + id + "/death", body),
                ok -> refresh());
    }
}
