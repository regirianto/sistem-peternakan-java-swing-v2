package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.Map;

/** Dashboard operasional dan keuangan sesuai izin pengguna. */
public final class DashboardPanel extends JPanel implements MainFrame.Refreshable {
    private final ApiClient client;
    private final JPanel cards = new JPanel(new GridLayout(0, 4, 10, 10));
    private final JPanel tables = new JPanel(new GridLayout(1, 2, 10, 0));

    public DashboardPanel(ApiClient client, MainFrame frame) {
        this.client = client;
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.BG);
        add(Ui.header("Dashboard", "Ringkasan kondisi ternak, keuangan, dan pekerjaan menunggu"),
                BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.add(cards, BorderLayout.NORTH);
        content.add(tables, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        cards.removeAll();
        tables.removeAll();
        Ui.async(this, "Memuat dashboard", () -> client.get("/api/dashboard", Map.of()), data -> {
            List<String> counts = List.of("activeGoats", "readyGoats", "quarantineGoats", "sickGoats",
                    "soldGoats", "deadGoats", "draftPurchases", "draftSales", "pendingApprovals");
            Map<String, String> labels = Map.ofEntries(
                    java.util.Map.entry("activeGoats", "Kambing aktif"),
                    java.util.Map.entry("readyGoats", "Siap jual"),
                    java.util.Map.entry("quarantineGoats", "Karantina"),
                    java.util.Map.entry("sickGoats", "Sakit"),
                    java.util.Map.entry("soldGoats", "Terjual"),
                    java.util.Map.entry("deadGoats", "Mati"),
                    java.util.Map.entry("draftPurchases", "Pembelian draf"),
                    java.util.Map.entry("draftSales", "Penjualan draf"),
                    java.util.Map.entry("pendingApprovals", "Menunggu persetujuan"));
            for (String key : counts) {
                Object value = data.get(key);
                if (value != null) {
                    cards.add(Ui.statCard(labels.get(key), String.valueOf(value)));
                }
            }
            if (client.owner()) {
                for (Map.Entry<String, String> entry : moneyLabels().entrySet()) {
                    Object value = data.get(entry.getKey());
                    if (value != null) {
                        cards.add(Ui.statCard(entry.getValue(), Ui.formatMoney(value)));
                    }
                }
            }
            StringBuilder styles = new StringBuilder("Kandang mendekati penuh\n");
            for (Map<String, Object> row : ApiClient.list(data.get("nearFullPens"))) {
                styles.append("- ").append(ApiClient.str(row.get("name"))).append(' ')
                        .append(row.get("occupancy")).append('/').append(row.get("capacity")).append('\n');
            }
            StringBuilder lowText = new StringBuilder("Pakan menipis\n");
            for (Map<String, Object> row : ApiClient.list(data.get("feedLowStock"))) {
                lowText.append("- ").append(ApiClient.str(row.get("name"))).append(' ')
                        .append(row.get("stock_qty")).append('/').append(row.get("min_stock")).append('\n');
            }
            javax.swing.JTextArea near = Ui.area(styles.toString());
            javax.swing.JTextArea low = Ui.area(lowText.toString());
            javax.swing.JScrollPane lhs = new javax.swing.JScrollPane(near);
            javax.swing.JScrollPane rhs = new javax.swing.JScrollPane(low);
            JPanel left = Ui.card(lhs);
            JPanel right = Ui.card(rhs);
            tables.setLayout(new GridLayout(1, 2, 10, 0));
            tables.add(left);
            tables.add(right);
            cards.revalidate();
            cards.repaint();
            tables.revalidate();
            tables.repaint();
        });
    }

    private Map<String, String> moneyLabels() {
        return java.util.Map.ofEntries(
                java.util.Map.entry("revenue", "Pendapatan"),
                java.util.Map.entry("hpp", "HPP"),
                java.util.Map.entry("expenses", "Pengeluaran"),
                java.util.Map.entry("feedUsage", "Pakan terpakai"),
                java.util.Map.entry("periodResult", "Hasil periode"),
                java.util.Map.entry("receivables", "Piutang"),
                java.util.Map.entry("payables", "Utang"));
    }
}
