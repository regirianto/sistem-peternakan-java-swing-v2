package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pembelian: daftar dokumen, konfirmasi, pembatalan, dan penerimaan barang. */
public final class PurchasesPanel extends JPanel implements MainFrame.Refreshable {
    private final ApiClient client;
    private final javax.swing.table.DefaultTableModel model = Ui.model(
            "ID", "No. Dokumen", "Supplier", "Tanggal", "Jatuh Tempo", "Status", "Perolehan", "Dibayar", "Sisa", "Cabang");
    private final javax.swing.JTable table = Ui.table(model, 6, 7, 8);
    private List<Map<String, Object>> rows = List.of();
    private final JTextField q = Ui.field("");

    public PurchasesPanel(ApiClient client, MainFrame frame) {
        this.client = client;
        table.setName("purchases.table");
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.BG);
        add(Ui.header("Pembelian & Penerimaan",
                "Draf pembelian, konfirmasi, penerimaan bertahap, pembayaran utang"), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        JPanel actions = Ui.toolbar(
                Ui.primary("Pembelian Baru", this::create),
                Ui.button("Konfirmasi", this::confirm),
                Ui.button("Detail", this::detail),
                Ui.button("Terima Kambing", this::receive),
                Ui.button("Bayar Utang", this::pay),
                Ui.label("Cari"), q, Ui.button("Cari", this::refresh));
        if (client.owner()) {
            actions.add(Ui.danger("Batal / Tutup Sisa", this::ownerAction));
        }
        center.add(actions, BorderLayout.NORTH);
        center.add(Ui.scroll(table), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("q", q.getText().trim());
        query.put("size", "200");
        Ui.async(this, "Memuat pembelian", () -> client.get("/api/purchases", query).get("items"), items -> {
            rows = ApiClient.list(items);
            model.setRowCount(0);
            for (Map<String, Object> purchase : rows) {
                model.addRow(new Object[]{
                        ApiClient.lng(purchase.get("id")),
                        ApiClient.str(purchase.get("doc_no")),
                        ApiClient.str(purchase.get("supplier_name")),
                        Ui.formatDate(purchase.get("purchase_date")),
                        Ui.formatDate(purchase.get("due_date")),
                        ApiClient.str(purchase.get("status")),
                        purchase.get("received_value"),
                        purchase.get("paid_value"),
                        purchase.get("remaining_payable"),
                        ApiClient.str(purchase.get("branch_name"))
                });
            }
        });
    }

    private Map<String, Object> selected() {
        int view = table.getSelectedRow();
        return view < 0 ? null : rows.get(view);
    }

    private void create() {
        Ui.async(this, "Memuat supplier", () -> client.get("/api/parties",
                        Map.of("role", "SUPPLIER", "size", "200")).get("items"),
                items -> new PurchaseDialog(this, client, ApiClient.list(items), this::refresh));
    }

    private void detail() {
        Map<String, Object> doc = selected();
        if (doc == null) {
            Ui.info(this, "Pilih dokumen pembelian.");
            return;
        }
        long id = ApiClient.lng(doc.get("id"));
        Ui.async(this, "Memuat detail", () -> client.get("/api/purchases/" + id, Map.of()),
                purchase -> {
                    javax.swing.JTextArea area = detailArea(purchase);
                    Dialogs.show(this, "Pembelian " + ApiClient.str(purchase.get("doc_no")), area);
                });
    }

    private javax.swing.JTextArea detailArea(Map<String, Object> purchase) {
        javax.swing.JTextArea area = Ui.area("");
        area.setEditable(false);
        StringBuilder sb = new StringBuilder();
        sb.append("No. ").append(ApiClient.str(purchase.get("doc_no")))
                .append(" | Supplier ").append(ApiClient.str(purchase.get("supplier_name")))
                .append(" | Tanggal ").append(Ui.formatDate(purchase.get("purchase_date")))
                .append(" | Status ").append(ApiClient.str(purchase.get("status"))).append('\n');
        for (Map<String, Object> item : ApiClient.list(purchase.get("items"))) {
            sb.append("  • ").append(ApiClient.str(item.get("description")))
                    .append(" x").append(item.get("qty")).append(" @")
                    .append(Ui.formatMoney(item.get("unit_price")))
                    .append(" diterima ").append(item.get("received_qty")).append('\n');
        }
        for (Map<String, Object> receipt : ApiClient.list(purchase.get("receipts"))) {
            sb.append("  ▸ Penerimaan ").append(ApiClient.str(receipt.get("doc_no")))
                    .append(" ").append(receipt.get("goat_count")).append(" kambing")
                    .append(" per ").append(Ui.formatDate(receipt.get("receipt_date"))).append('\n');
        }
        sb.append("\nPerolehan ").append(Ui.formatMoney(purchase.get("received_value")))
                .append(" | Diproses ").append(Ui.formatMoney(purchase.get("paid_value")))
                .append(" | Sisa ").append(Ui.formatMoney(purchase.get("remaining_payable")));
        area.setText(sb.toString());
        area.setRows(Math.max(6, sb.toString().split("\n").length));
        return area;
    }

    private void confirm() {
        Map<String, Object> doc = selected();
        if (doc == null) {
            Ui.info(this, "Pilih dokumen pembelian.");
            return;
        }
        if (!"DRAFT".equals(ApiClient.str(doc.get("status")))) {
            Ui.info(this, "Hanya pembelian berstatus DRAFT yang dapat dikonfirmasi.");
            return;
        }
        if (!Ui.confirm(this, "Konfirmasi pembelian ini? Setelah dikonfirmasi, kambing dapat diterima.")) {
            return;
        }
        long id = ApiClient.lng(doc.get("id"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", UUID.randomUUID().toString());
        Ui.async(this, "Mengonfirmasi pembelian", () -> client.post("/api/purchases/" + id + "/confirm", body),
                ok -> refresh());
    }

    private void receive() {
        Map<String, Object> doc = selected();
        if (doc == null) {
            Ui.info(this, "Pilih dokumen pembelian yang akan diterima.");
            return;
        }
        String status = ApiClient.str(doc.get("status"));
        String blocker = switch (status) {
            case "DRAFT" -> "Konfirmasi pembelian terlebih dahulu sebelum menerima kambing.";
            case "SELESAI" -> "Pembelian ini sudah selesai diterima.";
            case "DIBATALKAN" -> "Pembelian ini sudah dibatalkan.";
            default -> List.of("DIKONFIRMASI", "DITERIMA_SEBAGIAN").contains(status)
                    ? null : "Status pembelian " + status + " tidak dapat menerima kambing.";
        };
        if (blocker != null) {
            Ui.info(this, blocker);
            return;
        }
        Ui.async(this, "Memuat data", () -> client.get("/api/purchases/" + ApiClient.lng(doc.get("id")), Map.of()),
                purchase -> Ui.async(this, "Memuat kandang", () -> client.get("/api/pens", Map.of()).get("items"),
                        pens -> new ReceiptDialog(this, client, purchase, ApiClient.list(pens), this::refresh)));
    }

    private void pay() {
        Map<String, Object> doc = selected();
        if (doc == null) {
            Ui.info(this, "Pilih dokumen pembelian yang akan dibayar.");
            return;
        }
        new PaymentDialog(this, client, "PURCHASE", ApiClient.lng(doc.get("id")),
                doc.get("supplier_id"), doc.get("remaining_payable"), this::refresh);
    }

    private void ownerAction() {
        Map<String, Object> doc = selected();
        if (doc == null) {
            Ui.info(this, "Pilih dokumen pembelian.");
            return;
        }
        long id = ApiClient.lng(doc.get("id"));
        String[] options = {"Batal Dokumen", "Tutup Sisa Pesanan"};
        int choice = javax.swing.JOptionPane.showOptionDialog(this,
                "Pilih tindakan untuk " + ApiClient.str(doc.get("doc_no")), "Tindakan Owner",
                javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]);
        if (choice < 0) {
            return;
        }
        String reason = Ui.input(this, "Alasan wajib diisi:", "");
        if (reason == null || reason.isBlank()) {
            return;
        }
        String path = choice == 0 ? "/api/purchases/" + id + "/cancel"
                : "/api/purchases/" + id + "/close-remaining";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason.trim());
        body.put("requestId", UUID.randomUUID().toString());
        Ui.async(this, "Memproses", () -> client.post(path, body), ok -> refresh());
    }
}
