package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Penjualan: draft, konfirmasi, persetujuan harga di bawah pokok, pelunasan, invoice. */
public final class SalesPanel extends JPanel implements MainFrame.Refreshable {
    private final ApiClient client;
    private final javax.swing.table.DefaultTableModel model = Ui.model(
            "ID", "No. Dokumen", "Pelanggan", "Tanggal", "Jenis", "Status", "Pembayaran", "Bruto", "Net", "Cabang");
    private final javax.swing.JTable table = Ui.table(model, 7, 8);
    private List<Map<String, Object>> rows = List.of();
    private final JTextField q = Ui.field("");

    public SalesPanel(ApiClient client, MainFrame frame) {
        this.client = client;
        table.setName("sales.table");
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.BG);
        add(Ui.header("Penjualan",
                "Harga per ekor / per kg, persetujuan harga di bawah pokok, piutang"), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        JPanel actions = Ui.toolbar(
                Ui.primary("Penjualan Kambing", this::create),
                Ui.primary("Jual Daging", this::createMeat),
                Ui.button("Konfirmasi", this::confirm),
                Ui.button("Bayar", this::pay),
                Ui.button("Invoice", this::invoice),
                Ui.label("Cari"), q, Ui.button("Cari", this::refresh));
        if (client.owner()) {
            actions.add(Ui.danger("Batalkan", this::cancel));
        }
        center.add(actions, BorderLayout.NORTH);
        center.add(new javax.swing.JScrollPane(table), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("q", q.getText().trim());
        query.put("size", "200");
        Ui.async(this, "Memuat penjualan", () -> client.get("/api/sales", query).get("items"), items -> {
            rows = ApiClient.list(items);
            model.setRowCount(0);
            for (Map<String, Object> sale : rows) {
                model.addRow(new Object[]{
                        ApiClient.lng(sale.get("id")),
                        ApiClient.str(sale.get("doc_no")),
                        ApiClient.str(sale.get("customer_name")),
                        Ui.formatDate(sale.get("sale_date")),
                        ApiClient.str(sale.get("sale_type")),
                        ApiClient.str(sale.get("status")),
                        ApiClient.str(sale.get("payment_status")),
                        sale.get("total_gross"),
                        sale.get("total_net"),
                        ApiClient.str(sale.get("branch_name"))
                });
            }
        });
    }

    private Map<String, Object> selected() {
        int view = table.getSelectedRow();
        return view < 0 ? null : rows.get(view);
    }

    private void create() {
        Ui.async(this, "Memuat pelanggan", () -> client.get("/api/parties",
                        Map.of("role", "CUSTOMER", "size", "200")).get("items"),
                customers -> {
                    List<Map<String, Object>> customerRows = ApiClient.list(customers);
                    if (customerRows.isEmpty()) {
                        Ui.info(this, "Belum ada pelanggan. Tambahkan pelanggan terlebih dahulu.");
                        return;
                    }
                    Ui.async(this, "Memuat kambing siap jual",
                            () -> client.get("/api/goats/sellable", Map.of()).get("items"),
                            goats -> {
                                List<Map<String, Object>> goatRows = ApiClient.list(goats);
                                if (goatRows.isEmpty()) {
                                    Ui.info(this, "Tidak ada kambing siap jual.");
                                    return;
                                }
                                new SaleDialog(this, client, customerRows, goatRows, this::refresh);
                            });
                });
    }

    private void createMeat() {
        Ui.async(this, "Memuat pelanggan", () -> client.get("/api/parties",
                        Map.of("role", "CUSTOMER", "size", "200")).get("items"),
                customers -> {
                    List<Map<String, Object>> customerRows = ApiClient.list(customers);
                    if (customerRows.isEmpty()) {
                        Ui.info(this, "Belum ada pelanggan. Tambahkan pelanggan terlebih dahulu.");
                        return;
                    }
                    Ui.async(this, "Memuat stok daging",
                            () -> client.get("/api/meat/batches", Map.of()), data -> {
                                List<Map<String, Object>> batches = ApiClient.list(data.get("items"));
                                if (batches.isEmpty()) {
                                    Ui.info(this, "Stok daging kosong. Catat pemotongan terlebih dahulu.");
                                    return;
                                }
                                new MeatSaleDialog(this, client, customerRows, batches,
                                        data.get("pricePerKg"), this::refresh);
                            });
                });
    }

    private void confirm() {
        Map<String, Object> sale = selected();
        if (sale == null) {
            Ui.info(this, "Pilih dokumen penjualan.");
            return;
        }
        if (!Ui.confirm(this, "DAGING".equals(ApiClient.str(sale.get("sale_type")))
                ? "Konfirmasi penjualan daging ini? Stok batch dikurangi dan HPP dibekukan."
                : "Konfirmasi penjualan ini? Kambing akan terkunci dan HPP dibekukan.")) {
            return;
        }
        long id = ApiClient.lng(sale.get("id"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", UUID.randomUUID().toString());
        Ui.async(this, "Mengonfirmasi", () -> client.post("/api/sales/" + id + "/confirm", body),
                ok -> refresh());
    }

    private void pay() {
        Map<String, Object> sale = selected();
        if (sale == null) {
            Ui.info(this, "Pilih dokumen penjualan.");
            return;
        }
        long id = ApiClient.lng(sale.get("id"));
        new PaymentDialog(this, client, "SALE", id, sale.get("customer_id"),
                BigDecimalHelper.remaining(sale.get("remaining")), this::refresh);
    }

    private void invoice() {
        Map<String, Object> sale = selected();
        if (sale == null) {
            Ui.info(this, "Pilih dokumen penjualan.");
            return;
        }
        String report = "invoice_penjualan";
        Map<String, String> query = new LinkedHashMap<>();
        query.put("id", ApiClient.str(sale.get("id")));
        query.put("format", "pdf");
        Ui.async(this, "Membuat invoice",
                () -> client.download("/api/reports/" + report + "/file", query),
                download -> FilesUtil.preview(this, download.filename(), download.bytes()));
    }

    private void cancel() {
        Map<String, Object> sale = selected();
        if (sale == null) {
            Ui.info(this, "Pilih dokumen penjualan.");
            return;
        }
        if (!Ui.confirm(this, "DAGING".equals(ApiClient.str(sale.get("sale_type")))
                ? "Batalkan penjualan daging ini? Jurnal direversi dan sisa daging kembali ke batch."
                : "Batalkan penjualan ini? Jurnal akan direversi dan kambing kembali ke karantina.")) {
            return;
        }
        String reason = Ui.input(this, "Alasan pembatalan:", "");
        if (reason == null || reason.isBlank()) {
            return;
        }
        long id = ApiClient.lng(sale.get("id"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason.trim());
        body.put("requestId", UUID.randomUUID().toString());
        Ui.async(this, "Membatalkan", () -> client.post("/api/sales/" + id + "/cancel", body),
                ok -> refresh());
    }

    private static final class BigDecimalHelper {
        static String remaining(Object value) {
            return value == null ? "0" : ApiClient.number(value);
        }
    }
}
