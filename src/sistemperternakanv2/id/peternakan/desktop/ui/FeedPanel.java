package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pakan: item, penerimaan, pemakaian, dan mutasi. */
public final class FeedPanel extends JPanel implements MainFrame.Refreshable {
    private final ApiClient client;
    private final javax.swing.table.DefaultTableModel itemModel = Ui.model(
            "Kode", "Nama", "Satuan", "Min. Stok", "Stok", "Harga rata-rata", "Cabang");
    private List<Map<String, Object>> items = List.of();
    private final javax.swing.table.DefaultTableModel moveModel = Ui.model(
            "Tanggal", "Item", "Masuk", "Keluar", "Harga", "Kandang", "Kambing", "Catatan");

    public FeedPanel(ApiClient client, MainFrame frame) {
        this.client = client;
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.BG);
        add(Ui.header("Pakan", "Pengelolaan persediaan pakan dengan rata-rata tertimbang"), BorderLayout.NORTH);

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        JPanel actions = Ui.toolbar(Ui.primary("Penerimaan Pakan", this::receive),
                Ui.button("Pemakaian Pakan", this::usage));
        if (client.can("feed.manage")) {
            actions.add(Ui.button("Item Baru", this::createItem));
        }
        if (client.owner()) {
            actions.add(Ui.button("Nilai Persediaan", this::stockValue));
        }
        actions.add(Ui.button("Muat Ulang", this::refresh));
        top.add(actions, BorderLayout.NORTH);

        javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
        javax.swing.JTable itemTable = Ui.table(itemModel, 5);
        itemTable.setName("feed.items");
        javax.swing.JTable moveTable = Ui.table(moveModel, 4);
        moveTable.setName("feed.moves");
        tabs.addTab("Item", Ui.scroll(itemTable));
        tabs.addTab("Mutasi", Ui.scroll(moveTable));

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        content.add(top, BorderLayout.NORTH);
        content.add(tabs, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        Ui.async(this, "Memuat pakan", () -> client.get("/api/feed/items", Map.of()).get("items"), items -> {
            this.items = ApiClient.list(items);
            itemModel.setRowCount(0);
            for (Map<String, Object> item : this.items) {
                itemModel.addRow(new Object[]{
                        ApiClient.str(item.get("code")),
                        ApiClient.str(item.get("name")),
                        ApiClient.str(item.get("unit")),
                        item.get("min_stock"),
                        item.get("stock_qty"),
                        item.get("avg_cost"),
                        ApiClient.str(item.get("branch_name"))
                });
            }
        });
        Ui.async(this, "Memuat mutasi", () -> client.get("/api/feed/movements", Map.of()).get("items"),
                rows -> {
                    moveModel.setRowCount(0);
                    for (Map<String, Object> row : ApiClient.list(rows)) {
                        moveModel.addRow(new Object[]{
                                Ui.formatDate(row.get("movement_date")),
                                ApiClient.str(row.get("feed_name")),
                                row.get("qty_in"), row.get("qty_out"), row.get("unit_cost"),
                                ApiClient.str(row.get("pen_name")),
                                ApiClient.str(row.get("goat_code")),
                                ApiClient.str(row.get("notes"))
                        });
                    }
                });
    }

    private void createItem() {
        JTextField code = Ui.field("");
        code.setName("feed.code");
        JTextField name = Ui.field("");
        name.setName("feed.name");
        JTextField unit = Ui.field("kg");
        unit.setName("feed.unit");
        JTextField minStock = Ui.field("0");
        minStock.setName("feed.min");
        if (!Dialogs.ok(this, "Item Pakan Baru", Ui.label("Kode"), code, Ui.label("Nama"), name,
                Ui.label("Satuan"), unit, Ui.label("Min. stok"), minStock)) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code.getText().trim());
        body.put("name", name.getText().trim());
        body.put("unit", unit.getText().trim());
        body.put("minStock", minStock.getText().trim());
        Ui.async(this, "Menyimpan item", () -> client.post("/api/feed/items", body), ok -> refresh());
    }

    private void receive() {
        if (items.isEmpty()) {
            Ui.info(this, "Tambahkan item pakan terlebih dahulu.");
            return;
        }
        JComboBox<String> item = new JComboBox<>();
        item.setName("feed.receive.item");
        for (Map<String, Object> row : items) {
            item.addItem(ApiClient.str(row.get("code")) + " - " + ApiClient.str(row.get("name")));
        }
        JTextField date = Ui.field(Ui.today());
        date.setName("feed.receive.date");
        JTextField qty = Ui.field("");
        qty.setName("feed.receive.qty");
        JTextField unitCost = Ui.field("");
        unitCost.setName("feed.receive.cost");
        JTextField notes = Ui.field("");
        notes.setName("feed.receive.notes");
        if (!Dialogs.ok(this, "Penerimaan Pakan", Ui.label("Item"), item,
                Ui.label("Tanggal"), date, Ui.label("Jumlah"), qty,
                Ui.label("Harga satuan"), unitCost, Ui.label("Catatan"), notes)) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("feedItemId", ApiClient.lng(items.get(item.getSelectedIndex()).get("id")));
        body.put("movementDate", date.getText().trim());
        body.put("qty", qty.getText().trim());
        body.put("unitCost", unitCost.getText().trim());
        body.put("notes", notes.getText().trim());
        body.put("requestId", UUID.randomUUID().toString());
        Ui.async(this, "Menyimpan penerimaan", () -> client.post("/api/feed/receive", body),
                ok -> refresh());
    }

    private void usage() {
        if (items.isEmpty()) {
            Ui.info(this, "Tambahkan item pakan terlebih dahulu.");
            return;
        }
        JComboBox<String> item = new JComboBox<>();
        item.setName("feed.usage.item");
        for (Map<String, Object> row : items) {
            item.addItem(ApiClient.str(row.get("code")) + " - " + ApiClient.str(row.get("name")));
        }
        JTextField date = Ui.field(Ui.today());
        date.setName("feed.usage.date");
        JTextField qty = Ui.field("");
        qty.setName("feed.usage.qty");
        JTextField penId = Ui.field("");
        penId.setName("feed.usage.pen");
        JTextField goatId = Ui.field("");
        goatId.setName("feed.usage.goat");
        JTextField notes = Ui.field("");
        if (!Dialogs.ok(this, "Pemakaian Pakan", Ui.label("Item"), item, Ui.label("Tanggal"), date,
                Ui.label("Jumlah"), qty, Ui.label("ID kandang (opsional)"), penId,
                Ui.label("ID kambing (opsional)"), goatId, Ui.label("Catatan"), notes)) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("feedItemId", ApiClient.lng(items.get(item.getSelectedIndex()).get("id")));
        body.put("movementDate", date.getText().trim());
        body.put("qty", qty.getText().trim());
        if (!penId.getText().isBlank()) {
            body.put("penId", Long.parseLong(penId.getText().trim()));
        }
        if (!goatId.getText().isBlank()) {
            body.put("goatId", Long.parseLong(goatId.getText().trim()));
        }
        body.put("notes", notes.getText().trim());
        body.put("requestId", UUID.randomUUID().toString());
        Ui.async(this, "Menyimpan pemakaian", () -> client.post("/api/feed/usage", body), ok -> refresh());
    }

    private void stockValue() {
        Ui.async(this, "Memuat nilai persediaan",
                () -> client.get("/api/feed/stock-value", Map.of()).get("value"),
                value -> JOptionPane.showMessageDialog(this,
                        "Nilai persediaan pakan: " + Ui.formatMoney(value),
                        "Persediaan Pakan", JOptionPane.INFORMATION_MESSAGE));
    }
}
