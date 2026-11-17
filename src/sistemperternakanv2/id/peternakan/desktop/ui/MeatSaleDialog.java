package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Penjualan daging per kg dengan harga tetap dari pengaturan owner. */
public final class MeatSaleDialog extends JDialog {
    private final ApiClient client;
    private final List<Map<String, Object>> customers;
    private final List<Map<String, Object>> batches;
    private final BigDecimal pricePerKg;
    private final Runnable done;
    private final List<Long> batchIds = new ArrayList<>();
    private final Map<Long, BigDecimal> used = new LinkedHashMap<>();

    private final javax.swing.table.DefaultTableModel lines = new javax.swing.table.DefaultTableModel(
            new Object[]{"Batch", "Kambing", "Kg", "Harga/kg", "Subtotal"}, 0);
    private final JTable table = Ui.table(lines, 3, 4);

    public MeatSaleDialog(JPanel parent, ApiClient client, List<Map<String, Object>> customers,
                          List<Map<String, Object>> batches, Object price, Runnable done) {
        this.client = client;
        this.customers = customers;
        this.batches = batches;
        this.pricePerKg = price == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(price));
        this.done = done;
        table.setName("meatsale.table");
        setTitle("Penjualan Daging");
        setModal(true);
        setSize(760, 520);
        setLocationRelativeTo(parent);

        JComboBox<String> customer = new JComboBox<>();
        customer.setName("meatsale.customer");
        for (Map<String, Object> row : customers) {
            customer.addItem(ApiClient.str(row.get("name")));
        }
        JComboBox<String> batch = new JComboBox<>();
        batch.setName("meatsale.batch");
        for (Map<String, Object> row : batches) {
            batch.addItem(ApiClient.str(row.get("batch_no")) + " - " + ApiClient.str(row.get("goat_code"))
                    + " - sisa " + ApiClient.str(row.get("qty_remaining_kg")) + " kg");
        }
        JTextField date = Ui.field(Ui.today());
        JTextField qty = Ui.field("");
        qty.setName("meatsale.qty");
        JTextField priceLabel = Ui.field(pricePerKg.toPlainString());
        priceLabel.setEditable(false);
        JTextField notes = Ui.field("");

        JPanel form = Ui.form();
        Ui.addRow(form, 0, "Pelanggan", customer);
        Ui.addRow(form, 1, "Tanggal", date);
        Ui.addRow(form, 2, "Batch daging", batch);
        Ui.addRow(form, 3, "Jumlah (kg)", qty);
        Ui.addRow(form, 4, "Harga per kg", priceLabel);
        Ui.addRow(form, 5, "Catatan", notes);

        JButton add = Ui.primary("Tambah Baris", () -> addLine(batch, qty));
        JButton remove = Ui.button("Hapus Baris", () -> {
            int view = table.getSelectedRow();
            if (view >= 0) {
                table.clearSelection();
                batchIds.remove(view);
                lines.removeRow(view);
                recalculateUsed();
            }
        });

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(form, BorderLayout.NORTH);
        content.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel();
        south.setOpaque(false);
        south.add(add);
        south.add(remove);
        south.add(Ui.primary("Simpan Draft", () -> save(customer, date, notes)));
        south.add(Ui.button("Batal", this::dispose));
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        setVisible(true);
    }

    private void addLine(JComboBox<String> batch, JTextField qty) {
        if (batch.getSelectedIndex() < 0 || batches.isEmpty()) {
            Ui.error(this, "Tidak ada batch daging.");
            return;
        }
        if (pricePerKg.signum() <= 0) {
            Ui.error(this, "Harga daging per kg belum diatur owner pada menu Persediaan Daging.");
            return;
        }
        Map<String, Object> row = batches.get(batch.getSelectedIndex());
        long id = ApiClient.lng(row.get("id"));
        BigDecimal available = new BigDecimal(String.valueOf(row.get("qty_remaining_kg")))
                .subtract(used.getOrDefault(id, BigDecimal.ZERO));
        BigDecimal weight;
        try {
            weight = new BigDecimal(qty.getText().trim().replace(" ", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            Ui.error(this, "Jumlah kg tidak valid.");
            return;
        }
        if (weight.signum() <= 0) {
            Ui.error(this, "Jumlah kg harus lebih dari 0.");
            return;
        }
        if (weight.compareTo(available) > 0) {
            Ui.error(this, "Sisa batch hanya " + available.stripTrailingZeros().toPlainString() + " kg.");
            return;
        }
        used.put(id, used.getOrDefault(id, BigDecimal.ZERO).add(weight));
        batchIds.add(id);
        BigDecimal subtotal = weight.multiply(pricePerKg).setScale(2, java.math.RoundingMode.HALF_UP);
        lines.addRow(new Object[]{ApiClient.str(row.get("batch_no")), ApiClient.str(row.get("goat_code")),
                weight, pricePerKg, subtotal});
        qty.setText("");
    }

    /** Hitung ulang pemakaian per batch dari baris yang tersisa agar tidak bisa melebihi sisa stok. */
    private void recalculateUsed() {
        used.clear();
        for (int row = 0; row < lines.getRowCount(); row++) {
            long id = batchIds.get(row);
            BigDecimal weight = new BigDecimal(String.valueOf(lines.getValueAt(row, 2)));
            used.merge(id, weight, BigDecimal::add);
        }
    }

    private void save(JComboBox<String> customer, JTextField date, JTextField notes) {
        if (customers.isEmpty()) {
            Ui.error(this, "Belum ada pelanggan.");
            return;
        }
        if (customer.getSelectedIndex() < 0) {
            Ui.error(this, "Pilih pelanggan terlebih dahulu.");
            return;
        }
        if (batchIds.isEmpty()) {
            Ui.error(this, "Tambahkan minimal satu baris daging.");
            return;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (int row = 0; row < lines.getRowCount(); row++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("itemType", "DAGING");
            item.put("meatBatchId", batchIds.get(row));
            item.put("weight", String.valueOf(lines.getValueAt(row, 2)));
            items.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", ApiClient.lng(customers.get(customer.getSelectedIndex()).get("id")));
        body.put("saleDate", date.getText().trim());
        body.put("notes", notes.getText().trim());
        body.put("items", items);
        Ui.async(this, "Menyimpan draft daging", () -> client.post("/api/sales", body), ok -> {
            dispose();
            done.run();
            Ui.info(null, "Draft penjualan daging tersimpan. Konfirmasi setelah pembeli menerima daging.");
        });
    }
}
