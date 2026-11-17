package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pemotongan kambing: berat hidup, karkas, daging, tulang, dan posting batch daging. */
public final class SlaughterPanel extends JPanel implements MainFrame.Refreshable {
    private final ApiClient client;
    private final javax.swing.table.DefaultTableModel model = Ui.model(
            "ID", "No. Dokumen", "Kambing", "Tanggal", "Hidup (kg)", "Karkas (kg)", "Daging (kg)",
            "Tulang (kg)", "Dressing %", "Yield %", "Status", "Sisa Daging (kg)", "Cabang");
    private final javax.swing.JTable table = Ui.table(model);
    private List<Map<String, Object>> rows = List.of();
    private final JTextField q = Ui.field("");

    public SlaughterPanel(ApiClient client, MainFrame frame) {
        this.client = client;
        table.setName("slaughter.table");
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.BG);
        add(Ui.header("Pemotongan Kambing",
                "Catat berat hidup, karkas, daging, dan tulang sebelum daging dijual"),
                BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        java.util.List<java.awt.Component> buttons = new java.util.ArrayList<>();
        if (client.can("slaughter.manage")) {
            buttons.add(Ui.primary("Pemotongan Baru", this::create));
            buttons.add(Ui.button("Posting", this::post));
            buttons.add(Ui.danger("Batalkan", this::cancel));
        }
        buttons.add(Ui.label("Cari"));
        buttons.add(q);
        buttons.add(Ui.button("Cari", this::refresh));
        buttons.add(Ui.button("Muat Ulang", this::refresh));
        JPanel actions = Ui.toolbar(buttons.toArray(new java.awt.Component[0]));
        center.add(actions, BorderLayout.NORTH);
        center.add(Ui.scroll(table), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("q", q.getText().trim());
        query.put("size", "200");
        Ui.async(this, "Memuat pemotongan", () -> client.get("/api/slaughter", query).get("items"), items -> {
            rows = ApiClient.list(items);
            model.setRowCount(0);
            for (Map<String, Object> row : rows) {
                model.addRow(new Object[]{
                        ApiClient.lng(row.get("id")),
                        ApiClient.str(row.get("doc_no")),
                        ApiClient.str(row.get("goat_code")),
                        Ui.formatDate(row.get("slaughter_date")),
                        row.get("live_weight_kg"),
                        row.get("carcass_weight_kg"),
                        row.get("meat_weight_kg"),
                        row.get("bone_weight_kg"),
                        row.get("dressing_pct"),
                        row.get("meat_yield_pct"),
                        ApiClient.str(row.get("status")),
                        row.get("qty_remaining_kg"),
                        ApiClient.str(row.get("branch_name"))
                });
            }
        });
    }

    private Map<String, Object> selected() {
        int view = table.getSelectedRow();
        return view < 0 ? null : rows.get(view);
    }

    private void create() {
        Ui.async(this, "Memuat kambing siap potong", () -> client.get("/api/goats/sellable", Map.of()).get("items"),
                goats -> showForm(ApiClient.list(goats)));
    }

    private void showForm(List<Map<String, Object>> goats) {
        if (goats.isEmpty()) {
            Ui.info(this, "Tidak ada kambing siap jual / siap potong.");
            return;
        }
        JComboBox<String> goat = new JComboBox<>();
        goat.setName("slaughter.goat");
        for (Map<String, Object> row : goats) {
            String jenis = ApiClient.str(row.get("jenis"));
            goat.addItem(ApiClient.str(row.get("code")) + (jenis.isBlank() ? "" : " - " + jenis));
        }
        JTextField date = Ui.field(Ui.today());
        date.setName("slaughter.date");
        JTextField live = Ui.field("");
        live.setName("slaughter.live");
        JTextField carcass = Ui.field("");
        carcass.setName("slaughter.carcass");
        JTextField meat = Ui.field("");
        meat.setName("slaughter.meat");
        JTextField bone = Ui.field("0");
        bone.setName("slaughter.bone");
        JTextField offal = Ui.field("");
        offal.setName("slaughter.offal");
        JTextField processing = Ui.field("0");
        processing.setName("slaughter.processing");
        JTextField notes = Ui.field("");

        JPanel form = Ui.form();
        Ui.addRow(form, 0, "Kambing", goat);
        Ui.addRow(form, 1, "Tanggal potong", date);
        Ui.addRow(form, 2, "Berat hidup (kg)", live);
        Ui.addRow(form, 3, "Berat karkas (kg)", carcass);
        Ui.addRow(form, 4, "Berat daging (kg)", meat);
        Ui.addRow(form, 5, "Berat tulang (kg)", bone);
        Ui.addRow(form, 6, "Berat jeroan (kg, opsional)", offal);
        Ui.addRow(form, 7, "Biaya potong", processing);
        Ui.addRow(form, 8, "Catatan", notes);
        if (!Dialogs.ok(this, "Pemotongan Baru", form)) {
            return;
        }
        if (live.getText().isBlank() || carcass.getText().isBlank() || meat.getText().isBlank()) {
            Ui.error(this, "Berat hidup, karkas, dan daging wajib diisi.");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("goatId", ApiClient.lng(goats.get(goat.getSelectedIndex()).get("id")));
        body.put("slaughterDate", date.getText().trim());
        body.put("liveWeight", live.getText().trim());
        body.put("carcassWeight", carcass.getText().trim());
        body.put("meatWeight", meat.getText().trim());
        body.put("boneWeight", bone.getText().trim().isBlank() ? "0" : bone.getText().trim());
        if (!offal.getText().isBlank()) {
            body.put("offalWeight", offal.getText().trim());
        }
        body.put("processingCost", processing.getText().trim().isBlank() ? "0" : processing.getText().trim());
        body.put("notes", notes.getText().trim());
        Ui.async(this, "Menyimpan pemotongan", () -> client.post("/api/slaughter", body), ok -> {
            refresh();
            Ui.info(this, "Draft pemotongan tersimpan. Tekan Posting setelah data benar.");
        });
    }

    private void post() {
        Map<String, Object> row = selected();
        if (row == null) {
            Ui.info(this, "Pilih pemotongan.");
            return;
        }
        if (!Ui.confirm(this, "Posting pemotongan ini? Kambing keluar dari persediaan dan daging masuk stok.")) {
            return;
        }
        long id = ApiClient.lng(row.get("id"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", UUID.randomUUID().toString());
        Ui.async(this, "Memposting pemotongan", () -> client.post("/api/slaughter/" + id + "/post", body),
                ok -> refresh());
    }

    private void cancel() {
        Map<String, Object> row = selected();
        if (row == null) {
            Ui.info(this, "Pilih pemotongan.");
            return;
        }
        if (!Ui.confirm(this, "Batalkan pemotongan ini? Jurnal akan direversi dan kambing kembali aktif.")) {
            return;
        }
        String reason = Ui.input(this, "Alasan pembatalan:", "");
        if (reason == null || reason.isBlank()) {
            return;
        }
        long id = ApiClient.lng(row.get("id"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason.trim());
        body.put("requestId", UUID.randomUUID().toString());
        Ui.async(this, "Membatalkan pemotongan", () -> client.post("/api/slaughter/" + id + "/cancel", body),
                ok -> refresh());
    }
}
