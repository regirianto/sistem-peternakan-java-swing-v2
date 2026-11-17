package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;
import id.peternakan.desktop.api.ApiClientException;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pembayaran: daftar transaksi, piutang, utang, dan pembatalan (owner). */
public final class PaymentsPanel extends JPanel implements MainFrame.Refreshable {
    private final ApiClient client;
    private final javax.swing.table.DefaultTableModel model = Ui.model(
            "ID", "Dokumen", "Arah", "Pihak", "Tanggal", "Nilai", "Sumber", "Cabang", "Status");
    private final javax.swing.JTable table = Ui.table(model, 5);
    private List<Map<String, Object>> rows = List.of();
    private final javax.swing.table.DefaultTableModel receivableModel = Ui.model(
            "No. Penjualan", "Pelanggan", "Tanggal", "Status", "Net", "Dibayar", "Sisa", "Umur (hari)");
    private List<Map<String, Object>> receivables = List.of();
    private final javax.swing.table.DefaultTableModel payableModel = Ui.model(
            "No. Pembelian", "Supplier", "Tanggal", "Nilai Terima", "Dibayar", "Sisa", "Umur");
    private List<Map<String, Object>> payables = List.of();

    public PaymentsPanel(ApiClient client, MainFrame frame) {
        this.client = client;
        table.setName("payments.table");
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.BG);
        add(Ui.header("Pembayaran", "Pelunasan, uang muka, dan pembatalan oleh owner"), BorderLayout.NORTH);

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        JPanel actions = Ui.toolbar(Ui.primary("Pembayaran Baru", this::newPayment));
        if (client.owner()) {
            actions.add(Ui.danger("Batalkan Pembayaran", this::reverse));
        }
        actions.add(Ui.button("Muat Ulang", this::refresh));
        actions.add(Ui.label("Pencarian"));
        JTextField q = Ui.field("");
        actions.add(q);
        actions.add(Ui.button("Cari", this::refresh));

        javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
        tabs.addTab("Daftar", Ui.scroll(table));
        tabs.addTab("Piutang Pelanggan", scroll(receivableModel, 4, 5, 6));
        tabs.addTab("Utang Supplier", scroll(payableModel, 3, 4, 5));
        top.add(actions, BorderLayout.NORTH);
        top.add(tabs, BorderLayout.CENTER);
        add(top, BorderLayout.CENTER);
    }

    private JScrollPane scroll(javax.swing.table.DefaultTableModel modelScroll, int... moneyColumns) {
        return new JScrollPane(Ui.table(modelScroll, moneyColumns));
    }

    @Override
    public void refresh() {
        Ui.async(this, "Memuat pembayaran", () -> client.get("/api/payments", Map.of("size", "300"))
                        .get("items"),
                items -> {
                    rows = ApiClient.list(items);
                    model.setRowCount(0);
                    for (Map<String, Object> row : rows) {
                        model.addRow(new Object[]{
                                ApiClient.lng(row.get("id")),
                                ApiClient.str(row.get("doc_no")),
                                "IN".equals(row.get("direction")) ? "Masuk" : "Keluar",
                                ApiClient.str(row.get("party_name")),
                                Ui.formatDate(row.get("payment_date")),
                                row.get("amount"),
                                ApiClient.str(row.get("source_type")),
                                ApiClient.str(row.get("branch_name")),
                                ApiClient.str(row.get("status"))
                        });
                    }
                });
        Ui.async(this, "Memuat piutang", () -> client.get("/api/payments/receivables", Map.of()).get("items"),
                items -> {
                    receivables = ApiClient.list(items);
                    receivableModel.setRowCount(0);
                    for (Map<String, Object> row : receivables) {
                        receivableModel.addRow(new Object[]{
                                ApiClient.str(row.get("doc_no")),
                                ApiClient.str(row.get("party_name")),
                                Ui.formatDate(row.get("sale_date")),
                                ApiClient.str(row.get("payment_status")),
                                row.get("total_net"), row.get("paid"), row.get("remaining"),
                                row.get("age_days")
                        });
                    }
                });
        Ui.async(this, "Memuat utang", () -> client.get("/api/payments/payables", Map.of()).get("items"),
                items -> {
                    payables = ApiClient.list(items);
                    payableModel.setRowCount(0);
                    for (Map<String, Object> row : payables) {
                        payableModel.addRow(new Object[]{
                                ApiClient.str(row.get("doc_no")),
                                ApiClient.str(row.get("party_name")),
                                Ui.formatDate(row.get("purchase_date")),
                                row.get("received_value"), row.get("paid"), row.get("remaining"),
                                row.get("age_days")
                        });
                    }
                });
    }

    private void newPayment() {
        String[] types = {"SALE", "PURCHASE", "ADVANCE_CUSTOMER", "ADVANCE_SUPPLIER", "REFUND"};
        String type = (String) javax.swing.JOptionPane.showInputDialog(this,
                "Jenis pembayaran:", "Pembayaran Baru", javax.swing.JOptionPane.PLAIN_MESSAGE,
                null, types, types[0]);
        if (type == null) {
            return;
        }
        switch (type) {
            case "SALE" -> Ui.async(this, "Memuat piutang",
                    () -> client.get("/api/payments/receivables", Map.of()).get("items"),
                    items -> new PayPicker(this, client, "SALE", ApiClient.list(items), this::refresh));
            case "PURCHASE" -> Ui.async(this, "Memuat utang",
                    () -> client.get("/api/payments/payables", Map.of()).get("items"),
                    items -> new PayPicker(this, client, "PURCHASE", ApiClient.list(items), this::refresh));
            case "ADVANCE_CUSTOMER", "ADVANCE_SUPPLIER", "REFUND" ->
                    Ui.async(this, "Memuat pihak", () -> client.get("/api/parties",
                                    Map.of("size", "300")).get("items"),
                            parties -> new AdvanceDialog(this, client, type,
                                    ApiClient.list(parties), this::refresh));
            default -> {
            }
        }
    }

    private void reverse() {
        int view = table.getSelectedRow();
        if (view < 0) {
            Ui.info(this, "Pilih pembayaran yang akan dibatalkan.");
            return;
        }
        Map<String, Object> row = rows.get(view);
        if (!"POSTED".equals(ApiClient.str(row.get("status")))) {
            Ui.info(this, "Hanya pembayaran POSTED yang dapat dibatalkan.");
            return;
        }
        if (!Ui.confirm(this, "Batalkan pembayaran ini? Jurnal akan direversi.")) {
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
        Ui.async(this, "Membatalkan pembayaran", () -> client.post("/api/payments/" + id + "/reverse", body),
                ok -> refresh());
    }
}
