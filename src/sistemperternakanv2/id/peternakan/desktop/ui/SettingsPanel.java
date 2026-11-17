package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pengaturan profil usaha, periode akuntansi, dan backup (owner). */
public final class SettingsPanel extends JPanel implements MainFrame.Refreshable {
    private final ApiClient client;
    private final javax.swing.table.DefaultTableModel settingsModel = Ui.model("Kunci", "Nilai");
    private final javax.swing.table.DefaultTableModel periodModel = Ui.model("Periode", "Status", "Ditutup");
    private final javax.swing.table.DefaultTableModel backupModel = Ui.model(
            "ID", "Waktu", "Hasil", "Lokasi");

    public SettingsPanel(ApiClient client, MainFrame frame) {
        this.client = client;
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.BG);
        add(Ui.header("Pengaturan Sistem", "Profil usaha, periode akuntansi, dan backup"),
                BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        JPanel actions = Ui.toolbar(
                Ui.button("Edit Pengaturan", this::editSettings),
                Ui.button("Tutup Periode", this::closePeriod),
                Ui.button("Buka Periode", this::reopenPeriod));
        if (client.owner()) {
            actions.add(Ui.primary("Backup Database", this::backup));
        }
        content.add(actions, BorderLayout.NORTH);

        javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
        tabs.addTab("Pengaturan", Ui.scroll(Ui.table(settingsModel)));
        tabs.addTab("Periode Akuntansi", Ui.scroll(Ui.table(periodModel)));
        tabs.addTab("Backup", Ui.scroll(Ui.table(backupModel)));
        content.add(tabs, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        Ui.async(this, "Memuat pengaturan", () -> client.get("/api/settings", Map.of()).get("items"),
                rows -> {
                    settingsModel.setRowCount(0);
                    ApiClient.map(rows).forEach((key, value) ->
                            settingsModel.addRow(new Object[]{key, value}));
                });
        Ui.async(this, "Memuat periode", () -> client.get("/api/periods", Map.of()).get("items"), rows -> {
            periodModel.setRowCount(0);
            for (Map<String, Object> row : ApiClient.list(rows)) {
                periodModel.addRow(new Object[]{
                        row.get("period_code"), row.get("status"),
                        row.get("closed_at") == null ? "" : Ui.formatDate(row.get("closed_at"))
                });
            }
        });
        Ui.async(this, "Memuat backup", () -> client.get("/api/backups", Map.of()).get("items"), rows -> {
            backupModel.setRowCount(0);
            for (Map<String, Object> row : ApiClient.list(rows)) {
                backupModel.addRow(new Object[]{
                        ApiClient.lng(row.get("id")),
                        ApiClient.str(row.get("occurred_at")),
                        ApiClient.str(row.get("result")),
                        ApiClient.str(row.get("location"))
                });
            }
        });
    }

    private void editSettings() {
        Ui.async(this, "Memuat pengaturan", () -> client.get("/api/settings", Map.of()).get("items"),
                rows -> {
                    Map<String, Object> items = ApiClient.map(rows);
                    List<Map.Entry<String, Object>> entries = new java.util.ArrayList<>(items.entrySet());
                    javax.swing.JTextField[] fields = new javax.swing.JTextField[entries.size()];
                    JPanel form = new JPanel(new java.awt.GridBagLayout());
                    form.setOpaque(false);
                    java.awt.GridBagConstraints g = new java.awt.GridBagConstraints();
                    g.insets = new java.awt.Insets(3, 0, 3, 10);
                    g.anchor = java.awt.GridBagConstraints.WEST;
                    for (int i = 0; i < entries.size(); i++) {
                        String key = entries.get(i).getKey();
                        fields[i] = new javax.swing.JTextField(ApiClient.str(entries.get(i).getValue()));
                        fields[i].setName("setting." + key);
                        g.gridx = 0;
                        g.gridy = i;
                        form.add(new javax.swing.JLabel(key), g);
                        g.gridx = 1;
                        g.fill = java.awt.GridBagConstraints.HORIZONTAL;
                        g.weightx = 1;
                        form.add(fields[i], g);
                    }
                    if (!Dialogs.ok(this, "Edit Pengaturan", new javax.swing.JScrollPane(form) {{
                        setPreferredSize(new java.awt.Dimension(500, 400));
                    }})) {
                        return;
                    }
                    Map<String, Object> body = new LinkedHashMap<>();
                    for (int i = 0; i < entries.size(); i++) {
                        body.put(entries.get(i).getKey(), fields[i].getText());
                    }
                    Ui.async(this, "Menyimpan pengaturan", () -> client.put("/api/settings", body),
                            ok -> refresh());
                });
    }

    private void closePeriod() {
        String code = Ui.input(this, "Periode (YYYY-MM):", Ui.monthStart().substring(0, 7));
        if (code == null || code.isBlank()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("periodCode", code.trim());
        Ui.async(this, "Menutup periode", () -> client.post("/api/periods/close", body), ok -> refresh());
    }

    private void reopenPeriod() {
        String code = Ui.input(this, "Periode (YYYY-MM):", Ui.monthStart().substring(0, 7));
        String reason = Ui.input(this, "Alasan membuka kembali:", "");
        if (code == null || reason == null) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("periodCode", code.trim());
        body.put("reason", reason.trim());
        Ui.async(this, "Membuka periode", () -> client.post("/api/periods/reopen", body), ok -> refresh());
    }

    private void backup() {
        Ui.async(this, "Menjalankan backup", () -> client.post("/api/backups", Map.of()),
                result -> {
                    Ui.info(this, "Backup selesai: " + ApiClient.str(result.get("file")));
                    refresh();
                });
    }
}
