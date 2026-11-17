package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.Map;

/** Dialog detail kambing: identitas, riwayat, penjualan, dan laba (owner). */
public final class GoatDialog extends JDialog {
    private final ApiClient client;
    private final long goatId;
    private final Map<String, Object> goat;

    public GoatDialog(JPanel parent, ApiClient client, Map<String, Object> goat) {
        this.client = client;
        this.goat = goat;
        this.goatId = ApiClient.lng(goat.get("id"));
        setTitle("Kambing " + ApiClient.str(goat.get("code")));
        setSize(860, 600);
        setLocationRelativeTo(parent);
        setModal(false);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Identitas", identity());
        tabs.addTab("Riwayat", history());
        if (client.owner()) {
            Map<String, Object> history = ApiClient.map(goat.get("history"));
            tabs.addTab("Penjualan", historyPane("Penjualan", history.get("sales"),
                    new String[]{"doc_no", "sale_date", "status", "payment_status", "weight_kg", "rate", "net", "hpp"},
                    new String[]{"No. Dokumen", "Tanggal", "Status", "Pembayaran", "Berat", "Harga", "Net", "HPP"}));
            tabs.addTab("Biaya & Laba", finance());
        }
        add(tabs, BorderLayout.CENTER);
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.add(Ui.button("Tandai Siap Jual", this::markReady));
        actions.add(Ui.button("Tandai Karantina", this::markQuarantine));
        add(actions, BorderLayout.SOUTH);
        setVisible(true);
    }

    private JPanel identity() {
        JPanel panel = new JPanel(new java.awt.GridBagLayout());
        panel.setOpaque(false);
        java.awt.GridBagConstraints g = new java.awt.GridBagConstraints();
        g.insets = new java.awt.Insets(4, 2, 4, 10);
        g.anchor = java.awt.GridBagConstraints.WEST;
        String[][] fields = {
                {"Kode", ApiClient.str(goat.get("code"))},
                {"Supplier", ApiClient.str(goat.get("supplier_name"))},
                {"Jenis", ApiClient.str(goat.get("jenis"))},
                {"Kelamin", ApiClient.str(goat.get("gender"))},
                {"Tanggal masuk", Ui.formatDate(goat.get("entry_date"))},
                {"Berat masuk (kg)", ApiClient.str(goat.get("entry_weight_kg"))},
                {"Status", ApiClient.str(goat.get("status"))},
                {"Kesehatan", ApiClient.str(goat.get("health_status"))},
                {"Kelayakan jual", ApiClient.str(goat.get("sale_readiness"))},
                {"Kandang", ApiClient.str(goat.get("pen_name"))}
        };
        for (int i = 0; i < fields.length; i++) {
            g.gridx = 0;
            g.gridy = i;
            panel.add(Ui.label(fields[i][0]), g);
            g.gridx = 1;
            panel.add(new JLabel(fields[i][1]), g);
        }
        if (!client.owner()) {
            return panel;
        }
        g.gridx = 1;
        g.gridy = fields.length;
        panel.add(new javax.swing.JLabel(Ui.formatMoney(goat.get("acquisition_cost"))), g);
        g.gridy = fields.length + 1;
        panel.add(new javax.swing.JLabel(""), g);
        return addProfitRow(panel, fields.length + 2, g);
    }

    private JPanel addProfitRow(JPanel panel, int row, java.awt.GridBagConstraints g) {
        Map<String, Object> profit = ApiClient.map(goat.get("profit"));
        if (profit.isEmpty()) {
            return panel;
        }
        g.gridx = 0;
        g.gridy = row;
        panel.add(Ui.label("Laba (manajerial)"), g);
        g.gridx = 1;
        panel.add(new JLabel(Ui.formatMoney(profit.get("profit"))), g);
        g.gridy = row + 1;
        panel.add(Ui.label("Margin"), g);
        g.gridx = 1;
        panel.add(new JLabel(ApiClient.str(profit.get("marginPercent")) + " %"), g);
        return panel;
    }

    private JPanel history() {
        Map<String, Object> history = ApiClient.map(goat.get("history"));
        JPanel panel = new JPanel(new java.awt.GridLayout(1, 3, 10, 0));
        panel.setOpaque(false);
        panel.add(historyPane("Penimbangan", history.get("weights"),
                new String[]{"measured_at", "weight_kg", "notes"},
                new String[]{"Tanggal", "Berat", "Catatan"}));
        panel.add(historyPane("Kesehatan", history.get("health"),
                new String[]{"occurred_at", "complaint", "action", "medication"},
                new String[]{"Tanggal", "Keluhan", "Tindakan", "Obat"}));
        panel.add(historyPane("Penempatan Kandang", history.get("placements"),
                new String[]{"entered_at", "exited_at", "pen_name", "reason"},
                new String[]{"Masuk", "Keluar", "Kandang", "Alasan"}));
        return panel;
    }

    private JPanel historyPane(String title, Object data, String[] keys, String[] labels) {
        javax.swing.table.DefaultTableModel model = Ui.model(labels);
        for (Map<String, Object> row : ApiClient.list(data)) {
            Object[] values = new Object[keys.length];
            for (int i = 0; i < keys.length; i++) {
                values[i] = row.get(keys[i]);
            }
            model.addRow(values);
        }
        JPanel panel = Ui.card(new JLabel(""));
        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setOpaque(false);
        content.add(Ui.subtitle(title), BorderLayout.NORTH);
        javax.swing.JTable table = Ui.table(model);
        for (int i = 0; i < keys.length; i++) {
            if (java.util.Set.of("amount", "rate", "net", "hpp").contains(keys[i])) {
                Ui.money(table, i);
            }
        }
        content.add(new javax.swing.JScrollPane(table), BorderLayout.CENTER);
        panel.removeAll();
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel finance() {
        Map<String, Object> profile = ApiClient.map(goat.get("profitLive"));
        Map<String, Object> latest = ApiClient.map(goat.get("profit"));
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);

        JPanel summary = new JPanel(new java.awt.GridLayout(2, 4, 10, 10));
        summary.setOpaque(false);
        summary.add(Ui.statCard("Pendapatan", Ui.formatMoney(profile.get("revenue"))));
        summary.add(Ui.statCard("HPP", Ui.formatMoney(profile.get("hpp"))));
        summary.add(Ui.statCard("Beban langsung", Ui.formatMoney(profile.get("directCost"))));
        summary.add(Ui.statCard("Beban teralokasi", Ui.formatMoney(profile.get("allocatedCost"))));
        summary.add(Ui.statCard("Laba", Ui.formatMoney(profile.get("profit"))));
        summary.add(Ui.statCard("Margin", ApiClient.str(profile.get("marginPercent")) + " %"));
        summary.add(Ui.statCard("Status", ApiClient.str(latest.get("status"))));
        content.add(summary, BorderLayout.NORTH);

        Map<String, Object> history = ApiClient.map(goat.get("history"));
        JPanel detail = new JPanel(new java.awt.GridLayout(1, 2, 10, 0));
        detail.setOpaque(false);
        detail.add(historyPane("Biaya Langsung", history.get("expenses"),
                new String[]{"doc_no", "expense_date", "category_name", "amount", "status"},
                new String[]{"Dokumen", "Tanggal", "Kategori", "Nilai", "Status"}));
        detail.add(historyPane("Alokasi Biaya Bersama", history.get("allocations"),
                new String[]{"period_start", "period_end", "source_type", "days", "amount"},
                new String[]{"Mulai", "Selesai", "Sumber", "Hari", "Nilai"}));
        content.add(detail, BorderLayout.CENTER);
        return content;
    }

    private void markReady() {
        setReadiness("SIAP_JUAL");
    }

    private void markQuarantine() {
        setReadiness("KARANTINA");
    }

    private void setReadiness(String readiness) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("readiness", readiness);
        Ui.async(this, "Menyimpan kelayakan", () -> client.post("/api/goats/" + goatId + "/readiness", body),
                ok -> setVisible(false));
    }
}
