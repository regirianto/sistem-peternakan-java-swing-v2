package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Log aktivitas seremonial: filter pembacaan audit. */
public final class AuditPanel extends JPanel implements MainFrame.Refreshable {
    private final ApiClient client;
    private final javax.swing.table.DefaultTableModel model = Ui.model(
            "Waktu", "Pengguna", "Modul", "Aksi", "Entitas", "Referensi", "Alasan");
    private final javax.swing.JTable table = Ui.table(model);
    private final javax.swing.JTextField q = Ui.field("");
    private final javax.swing.JComboBox<String> module = Ui.combo();

    public AuditPanel(ApiClient client, MainFrame frame) {
        this.client = client;
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.BG);
        add(Ui.header("Log Aktivitas", "Riwayat perubahan data dan siapa yang melakukannya"),
                BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(Ui.toolbar(Ui.label("Cari"), q, Ui.label("Modul"), module,
                Ui.button("Cari", this::refresh), Ui.button("Muat Ulang", this::refresh)), BorderLayout.NORTH);
        center.add(Ui.scroll(table), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("q", q.getText().trim());
        if (module.getSelectedIndex() > 0) {
            query.put("module", ApiClient.str(module.getSelectedItem()));
        }
        query.put("size", "200");
        Ui.async(this, "Memuat modul", () -> client.get("/api/audit/modules", Map.of()),
                items -> {
                    this.module.removeAllItems();
                    this.module.addItem("");
                    for (String name : ApiClient.stringList(items)) {
                        this.module.addItem(name);
                    }
                });
        Ui.async(this, "Memuat log", () -> client.get("/api/audit", query).get("items"), rows -> {
            model.setRowCount(0);
            for (Map<String, Object> row : ApiClient.list(rows)) {
                model.addRow(new Object[]{
                        Ui.formatDate(row.get("occurred_at")),
                        ApiClient.str(row.get("username")),
                        ApiClient.str(row.get("module")),
                        ApiClient.str(row.get("action")),
                        ApiClient.str(row.get("entity")),
                        ApiClient.str(row.get("entity_id")),
                        ApiClient.str(row.get("reason"))
                });
            }
        });
    }
}
