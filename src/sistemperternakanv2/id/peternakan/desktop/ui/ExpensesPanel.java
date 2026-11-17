package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pengeluaran: daftar, buat draf, posting, pembatalan, dan alokasi bersama. */
public final class ExpensesPanel extends JPanel implements MainFrame.Refreshable {
    private final ApiClient client;
    private final javax.swing.table.DefaultTableModel model = Ui.model(
            "ID", "No. Dokumen", "Tanggal", "Kategori", "Nilai", "Status", "Sifat alokasi", "Pihak", "Cabang");
    private final javax.swing.JTable table = Ui.table(model, 4);
    private List<Map<String, Object>> rows = List.of();

    public ExpensesPanel(ApiClient client, MainFrame frame) {
        this.client = client;
        table.setName("expenses.table");
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.BG);
        add(Ui.header("Pengeluaran & Alokasi",
                "Biaya langsung dan biaya bersama yang dialokasikan ke kambing"), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        JPanel actions = Ui.toolbar(Ui.primary("Pengeluaran Baru", this::create),
                Ui.button("Posting", this::post));
        if (client.owner()) {
            actions.add(Ui.danger("Batalkan", this::reverse));
            actions.add(Ui.button("Alokasi", this::allocate));
        }
        actions.add(Ui.button("Muat Ulang", this::refresh));
        center.add(actions, BorderLayout.NORTH);
        center.add(Ui.scroll(table), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        Ui.async(this, "Memuat pengeluaran", () -> client.get("/api/expenses", Map.of("size", "200"))
                .get("items"), items -> {
            rows = ApiClient.list(items);
            model.setRowCount(0);
            for (Map<String, Object> row : rows) {
                model.addRow(new Object[]{
                        ApiClient.lng(row.get("id")),
                        ApiClient.str(row.get("doc_no")),
                        Ui.formatDate(row.get("expense_date")),
                        ApiClient.str(row.get("category_name")),
                        row.get("amount"),
                        ApiClient.str(row.get("status")),
                        ApiClient.bool(row.get("allocatable")) ? "Bersama" : "Langsung",
                        ApiClient.str(row.get("party_name")),
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
        Ui.async(this, "Memuat kategori", () -> client.get("/api/expenses/categories", Map.of()).get("items"),
                categories -> Ui.async(this, "Memuat pihak", () -> client.get("/api/parties",
                        Map.of("size", "300")).get("items"),
                        parties -> new ExpenseDialog(this, client, ApiClient.list(categories),
                                ApiClient.list(parties), this::refresh)));
    }

    private void post() {
        Map<String, Object> expense = selected();
        if (expense == null) {
            Ui.info(this, "Pilih pengeluaran.");
            return;
        }
        if (!"DRAFT".equals(ApiClient.str(expense.get("status")))) {
            Ui.info(this, "Hanya pengeluaran DRAFT yang dapat diposting.");
            return;
        }
        if (!Ui.confirm(this, "Posting pengeluaran ini? Jurnal akan dibuat otomatis.")) {
            return;
        }
        long id = ApiClient.lng(expense.get("id"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", UUID.randomUUID().toString());
        Ui.async(this, "Memposting", () -> client.post("/api/expenses/" + id + "/post", body),
                ok -> refresh());
    }

    private void reverse() {
        Map<String, Object> expense = selected();
        if (expense == null) {
            Ui.info(this, "Pilih pengeluaran.");
            return;
        }
        if (!"POSTED".equals(ApiClient.str(expense.get("status")))) {
            Ui.info(this, "Hanya pengeluaran POSTED yang dapat dibatalkan.");
            return;
        }
        String reason = Ui.input(this, "Alasan pembatalan:", "");
        if (reason == null || reason.isBlank()) {
            return;
        }
        long id = ApiClient.lng(expense.get("id"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason.trim());
        body.put("requestId", UUID.randomUUID().toString());
        Ui.async(this, "Membatalkan pengeluaran", () -> client.post("/api/expenses/" + id + "/reverse", body),
                ok -> refresh());
    }

    private void allocate() {
        Map<String, Object> expense = selected();
        if (expense == null) {
            Ui.info(this, "Pilih pengeluaran sebagai sumber alokasi.");
            return;
        }
        long id = ApiClient.lng(expense.get("id"));
        Map<String, String> query = new LinkedHashMap<>();
        query.put("type", "EXPENSE");
        query.put("id", ApiClient.str(id));
        Ui.async(this, "Memuat alokasi", () -> client.get("/api/allocations/by-document", query),
                result -> {
                    Map<String, Object> source = ApiClient.map(result.get("source"));
                    if (source.isEmpty()) {
                        Ui.info(this, "Pengeluaran ini tidak memiliki sumber alokasi.");
                        return;
                    }
                    long costSourceId = ApiClient.lng(source.get("id"));
                    Ui.async(this, "Menyiapkan alokasi",
                            () -> client.post("/api/allocations/cost-sources/" + costSourceId + "/preview",
                                    Map.of()),
                            preview -> showAllocation(preview, costSourceId));
                });
    }

    private void showAllocation(Map<String, Object> preview, long costSourceId) {
        StringBuilder sb = new StringBuilder();
        sb.append("Nilai sumber: ").append(Ui.formatMoney(preview.get("total"))).append('\n');
        for (Map<String, Object> item : ApiClient.list(preview.get("items"))) {
            sb.append("  • Kambing ").append(ApiClient.str(item.get("goatCode")))
                    .append(" hari ").append(item.get("days"))
                    .append(" nilai ").append(Ui.formatMoney(item.get("amount"))).append('\n');
        }
        javax.swing.JTextArea area = Ui.area(sb.toString());
        area.setRows(Math.max(6, sb.toString().split("\n").length));
        javax.swing.JScrollPane previewScroll = new javax.swing.JScrollPane(area);
        if (!Dialogs.ok(this, "Pratinjau Alokasi", previewScroll)) {
            return;
        }
        Ui.async(this, "Finalisasi alokasi", () -> client.post(
                "/api/allocations/cost-sources/" + costSourceId + "/finalize", Map.of()), ok -> refresh());
    }
}
