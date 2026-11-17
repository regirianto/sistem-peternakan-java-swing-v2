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

/** Persediaan daging: batch, sisa kg, HPP/kg, mutasi, susut, dan harga jual per kg. */
public final class MeatStockPanel extends JPanel implements MainFrame.Refreshable {
    private final ApiClient client;
    private final javax.swing.table.DefaultTableModel batchModel = Ui.model(
            "Batch", "Kambing", "Tanggal", "Masuk (kg)", "Sisa (kg)", "HPP/kg", "Nilai Stok", "Status", "Cabang");
    private final javax.swing.table.DefaultTableModel moveModel = Ui.model(
            "Tanggal", "Batch", "Jenis", "Kg", "Nilai", "Penjualan", "Catatan");
    private List<Map<String, Object>> batches = List.of();
    private final JTextField price = Ui.field("");

    public MeatStockPanel(ApiClient client, MainFrame frame) {
        this.client = client;
        price.setName("meat.price");
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.BG);
        add(Ui.header("Persediaan Daging",
                "Stok daging per batch pemotongan, susut, dan harga jual per kg"), BorderLayout.NORTH);

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        java.util.List<java.awt.Component> buttons = new java.util.ArrayList<>();
        if (client.can("slaughter.manage")) {
            buttons.add(Ui.button("Catat Susut", this::waste));
        }
        buttons.add(Ui.button("Muat Ulang", this::refresh));
        JPanel actions = Ui.toolbar(buttons.toArray(new java.awt.Component[0]));
        if (client.owner()) {
            actions.add(Ui.label("Harga jual / kg"));
            price.setPreferredSize(new java.awt.Dimension(140, 30));
            actions.add(price);
            actions.add(Ui.primary("Simpan Harga", this::savePrice));
        }
        top.add(actions, BorderLayout.NORTH);

        javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
        javax.swing.JTable batchTable = Ui.table(batchModel, 5, 6);
        batchTable.setName("meat.batches");
        tabs.addTab("Batch", Ui.scroll(batchTable));
        tabs.addTab("Mutasi", Ui.scroll(Ui.table(moveModel, 4)));

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        content.add(top, BorderLayout.NORTH);
        content.add(tabs, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        Ui.async(this, "Memuat stok daging", () -> client.get("/api/meat/batches", Map.of("open", "all")),
                data -> {
                    batches = ApiClient.list(data.get("items"));
                    batchModel.setRowCount(0);
                    for (Map<String, Object> row : batches) {
                        batchModel.addRow(new Object[]{
                                ApiClient.str(row.get("batch_no")),
                                ApiClient.str(row.get("goat_code")),
                                Ui.formatDate(row.get("received_date")),
                                row.get("qty_in_kg"),
                                row.get("qty_remaining_kg"),
                                row.get("hpp_per_kg"),
                                row.get("qty_remaining_kg") == null || row.get("hpp_per_kg") == null ? null
                                        : new java.math.BigDecimal(String.valueOf(row.get("qty_remaining_kg")))
                                        .multiply(new java.math.BigDecimal(String.valueOf(row.get("hpp_per_kg")))),
                                ApiClient.str(row.get("status")),
                                ApiClient.str(row.get("branch_name"))
                        });
                    }
                    if (client.owner() && data.get("pricePerKg") != null) {
                        price.setText(String.valueOf(data.get("pricePerKg")));
                    }
                });
        Ui.async(this, "Memuat mutasi daging", () -> client.get("/api/meat/movements", Map.of()).get("items"),
                rows -> {
                    moveModel.setRowCount(0);
                    for (Map<String, Object> row : ApiClient.list(rows)) {
                        moveModel.addRow(new Object[]{
                                Ui.formatDate(row.get("movement_date")),
                                ApiClient.str(row.get("batch_no")),
                                ApiClient.str(row.get("movement_type")),
                                row.get("qty_kg"),
                                row.get("total_cost"),
                                ApiClient.str(row.get("sale_doc_no")),
                                ApiClient.str(row.get("notes"))
                        });
                    }
                });
    }

    private void waste() {
        List<Map<String, Object>> open = batches.stream()
                .filter(b -> b.get("qty_remaining_kg") != null
                        && new java.math.BigDecimal(String.valueOf(b.get("qty_remaining_kg"))).signum() > 0)
                .toList();
        if (open.isEmpty()) {
            Ui.info(this, "Tidak ada batch daging dengan sisa stok.");
            return;
        }
        JComboBox<String> batch = new JComboBox<>();
        batch.setName("waste.batch");
        for (Map<String, Object> row : open) {
            batch.addItem(ApiClient.str(row.get("batch_no")) + " - sisa "
                    + ApiClient.str(row.get("qty_remaining_kg")) + " kg");
        }
        JTextField date = Ui.field(Ui.today());
        JTextField qty = Ui.field("");
        qty.setName("waste.qty");
        JTextField notes = Ui.field("");
        notes.setName("waste.notes");
        if (!Dialogs.ok(this, "Catat Susut Daging", Ui.label("Batch"), batch, Ui.label("Tanggal"), date,
                Ui.label("Jumlah (kg)"), qty, Ui.label("Catatan"), notes)) {
            return;
        }
        if (batch.getSelectedIndex() < 0) {
            Ui.error(this, "Pilih batch daging.");
            return;
        }
        Map<String, Object> selectedBatch = open.get(batch.getSelectedIndex());
        java.math.BigDecimal remaining = new java.math.BigDecimal(
                String.valueOf(selectedBatch.get("qty_remaining_kg")));
        java.math.BigDecimal amount;
        try {
            amount = new java.math.BigDecimal(qty.getText().trim().replace(" ", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            Ui.error(this, "Jumlah susut harus berupa angka.");
            return;
        }
        if (amount.signum() <= 0) {
            Ui.error(this, "Jumlah susut harus lebih dari 0.");
            return;
        }
        if (amount.compareTo(remaining) > 0) {
            Ui.error(this, "Jumlah susut melebihi sisa batch ("
                    + remaining.stripTrailingZeros().toPlainString() + " kg).");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("batchId", ApiClient.lng(selectedBatch.get("id")));
        body.put("movementDate", date.getText().trim());
        body.put("qty", amount.toPlainString());
        body.put("notes", notes.getText().trim());
        body.put("requestId", UUID.randomUUID().toString());
        Ui.async(this, "Menyimpan susut", () -> client.post("/api/meat/waste", body), ok -> refresh());
    }

    private void savePrice() {
        String value = price.getText().trim();
        if (value.isBlank()) {
            Ui.error(this, "Harga per kg wajib diisi.");
            return;
        }
        java.math.BigDecimal amount;
        try {
            amount = new java.math.BigDecimal(value.replace(" ", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            Ui.error(this, "Harga per kg harus berupa angka.");
            return;
        }
        if (amount.signum() <= 0) {
            Ui.error(this, "Harga per kg harus lebih dari 0.");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("meat.price_per_kg", amount.toPlainString());
        Ui.async(this, "Menyimpan harga", () -> client.put("/api/settings", body), ok -> {
            refresh();
            JOptionPane.showMessageDialog(this, "Harga daging per kg tersimpan.",
                    "Persediaan Daging", JOptionPane.INFORMATION_MESSAGE);
        });
    }
}
