package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dialog pembelian baru: pilih supplier, tanggal, item, dan harga. */
public final class PurchaseDialog extends JDialog {
    private final ApiClient client;
    private final List<Map<String, Object>> suppliers;
    private final Runnable done;

    private final javax.swing.table.DefaultTableModel items = new javax.swing.table.DefaultTableModel(
            new Object[]{"Keterangan", "Qty", "Harga"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return true;
        }
    };
    private final JTable table = Ui.table(items, 2);

    public PurchaseDialog(JPanel parent, ApiClient client, List<Map<String, Object>> suppliers,
                          Runnable done) {
        this.client = client;
        this.suppliers = suppliers;
        this.done = done;
        table.setName("purchase.items");
        setTitle("Pembelian Baru");
        setModal(true);
        setSize(720, 480);
        setLocationRelativeTo(parent);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JComboBox<String> supplier = new JComboBox<>();
        supplier.setName("purchase.supplier");
        for (Map<String, Object> supplierRow : suppliers) {
            supplier.addItem(ApiClient.str(supplierRow.get("name")));
        }
        JTextField date = Ui.field(Ui.today());
        date.setName("purchase.date");
        JTextField dueDate = Ui.field("");
        JTextField notes = Ui.field("");
        notes.setName("purchase.notes");
        JPanel form = new JPanel(new java.awt.GridBagLayout());
        form.setOpaque(false);
        Ui.addRow(form, 0, "Supplier", supplier);
        Ui.addRow(form, 1, "Tanggal", date);
        Ui.addRow(form, 2, "Jatuh tempo (opsional)", dueDate);
        Ui.addRow(form, 3, "Catatan", notes);

        addRowInitially();

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.add(Ui.button("+ Baris", this::addRowInitially));
        actions.add(Ui.button("Hapus Baris", this::removeSelected));
        actions.add(Ui.primary("Simpan Draf", () -> save(supplier, date, dueDate, notes)));
        actions.add(Ui.button("Batal", this::dispose));

        content.add(form, BorderLayout.NORTH);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        setContentPane(content);
        setVisible(true);
    }

    private void addRowInitially() {
        items.addRow(new Object[]{"Kambing", 1, 2500000});
    }

    private void removeSelected() {
        int row = table.getSelectedRow();
        if (row >= 0) {
            items.removeRow(row);
        }
    }

    private void save(JComboBox<String> supplier, JTextField date, JTextField dueDate, JTextField notes) {
        Ui.commitEdit(table);
        if (suppliers.isEmpty()) {
            Ui.error(this, "Belum ada supplier. Tambahkan terlebih dahulu.");
            return;
        }
        List<Map<String, Object>> itemList = new ArrayList<>();
        for (int row = 0; row < items.getRowCount(); row++) {
            String description = String.valueOf(items.getValueAt(row, 0)).trim();
            if (description.isBlank()) {
                Ui.error(this, "Keterangan item wajib diisi.");
                return;
            }
            int qty;
            java.math.BigDecimal price;
            try {
                qty = new java.math.BigDecimal(String.valueOf(items.getValueAt(row, 1)).trim()
                        .replace(" ", "").replace(',', '.')).intValueExact();
                price = new java.math.BigDecimal(String.valueOf(items.getValueAt(row, 2)).trim()
                        .replace(" ", "").replace(',', '.'));
            } catch (NumberFormatException | ArithmeticException e) {
                Ui.error(this, "Qty harus bilangan bulat dan harga harus angka.");
                return;
            }
            if (qty <= 0) {
                Ui.error(this, "Qty harus lebih dari 0.");
                return;
            }
            if (price.signum() <= 0) {
                Ui.error(this, "Harga beli harus lebih dari 0.");
                return;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("description", description);
            item.put("qty", qty);
            item.put("unitPrice", price.toPlainString());
            itemList.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("supplierId", ApiClient.lng(suppliers.get(supplier.getSelectedIndex()).get("id")));
        body.put("purchaseDate", date.getText().trim());
        body.put("dueDate", dueDate.getText().trim().isBlank() ? null : dueDate.getText().trim());
        body.put("notes", notes.getText().trim());
        body.put("items", itemList);
        Ui.async(this, "Menyimpan pembelian", () -> client.post("/api/purchases", body), ok -> {
            dispose();
            done.run();
        });
    }
}
