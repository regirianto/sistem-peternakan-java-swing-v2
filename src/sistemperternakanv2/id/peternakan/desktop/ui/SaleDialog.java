package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.JButton;
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

/** Dialog penjualan baru: pilih pelanggan dan kambing, atur harga. */
public final class SaleDialog extends JDialog {
    private final ApiClient client;
    private final List<Map<String, Object>> customers;
    private final List<Map<String, Object>> sellable;
    private final Runnable done;
    private final List<Long> goatIds = new ArrayList<>();

    private final javax.swing.table.DefaultTableModel lines = new javax.swing.table.DefaultTableModel(
            new Object[]{"Kambing", "Metode", "Berat (kg, opsional)", "Harga"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column != 0;
        }
    };
    private final JTable table = Ui.table(lines, 3);

    public SaleDialog(JPanel parent, ApiClient client, List<Map<String, Object>> customers,
                      List<Map<String, Object>> sellable, Runnable done) {
        this.client = client;
        this.customers = customers;
        this.sellable = sellable;
        this.done = done;
        table.setName("sale.table");
        setTitle("Penjualan Baru");
        setModal(true);
        setSize(760, 520);
        setLocationRelativeTo(parent);

        JComboBox<String> customer = new JComboBox<>();
        customer.setName("sale.customer");
        for (Map<String, Object> row : customers) {
            customer.addItem(ApiClient.str(row.get("name")));
        }
        JComboBox<String> goat = new JComboBox<>();
        goat.setName("sale.goat");
        for (Map<String, Object> row : sellable) {
            goat.addItem(ApiClient.str(row.get("code")) + " - " + Ui.formatMoney(row.get("acquisition_cost")));
        }
        JTextField date = Ui.field(Ui.today());
        JTextField notes = Ui.field("");

        JPanel form = new JPanel(new java.awt.GridBagLayout());
        form.setOpaque(false);
        Ui.addRow(form, 0, "Pelanggan", customer);
        Ui.addRow(form, 1, "Tanggal", date);
        Ui.addRow(form, 2, "Kambing siap jual", goat);
        Ui.addRow(form, 3, "Catatan", notes);

        JButton add = Ui.primary("Tambah Baris", () -> addLine(goat));
        JButton remove = Ui.button("Hapus Baris", () -> {
            int view = table.getSelectedRow();
            if (view >= 0) {
                lines.removeRow(view);
                goatIds.remove(view);
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

    private void addLine(JComboBox<String> goat) {
        if (goat.getSelectedIndex() < 0 || sellable.isEmpty()) {
            Ui.error(this, "Tidak ada kambing siap jual.");
            return;
        }
        Map<String, Object> row = sellable.get(goat.getSelectedIndex());
        long id = ApiClient.lng(row.get("id"));
        if (goatIds.contains(id)) {
            Ui.error(this, "Kambing sudah ada pada daftar jual.");
            return;
        }
        goatIds.add(id);
        lines.addRow(new Object[]{row.get("code"), "PER_EKOR", "", ""});
    }

    private void save(JComboBox<String> customer, JTextField date, JTextField notes) {
        Ui.commitEdit(table);
        if (customers.isEmpty()) {
            Ui.error(this, "Belum ada pelanggan.");
            return;
        }
        if (customer.getSelectedIndex() < 0) {
            Ui.error(this, "Pilih pelanggan terlebih dahulu.");
            return;
        }
        if (goatIds.isEmpty()) {
            Ui.error(this, "Tambahkan minimal satu kambing.");
            return;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (int row = 0; row < lines.getRowCount(); row++) {
            String method = String.valueOf(lines.getValueAt(row, 1)).trim();
            String weightText = String.valueOf(lines.getValueAt(row, 2)).trim();
            String rateText = String.valueOf(lines.getValueAt(row, 3)).trim();
            java.math.BigDecimal rate;
            java.math.BigDecimal weight = null;
            try {
                rate = new java.math.BigDecimal(rateText.replace(" ", "").replace(',', '.'));
                if (!weightText.isBlank()) {
                    weight = new java.math.BigDecimal(weightText.replace(" ", "").replace(',', '.'));
                }
            } catch (NumberFormatException e) {
                Ui.error(this, "Harga dan berat harus berupa angka.");
                return;
            }
            if (rate.signum() <= 0) {
                Ui.error(this, "Harga jual harus lebih dari 0.");
                return;
            }
            if ("PER_KG".equals(method) && (weight == null || weight.signum() <= 0)) {
                Ui.error(this, "Penjualan per kg wajib mengisi berat jual.");
                return;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("goatId", goatIds.get(row));
            item.put("method", method);
            item.put("rate", rate.toPlainString());
            if (weight != null) {
                item.put("weight", weight.toPlainString());
            }
            items.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", ApiClient.lng(customers.get(customer.getSelectedIndex()).get("id")));
        body.put("saleDate", date.getText().trim());
        body.put("notes", notes.getText().trim());
        body.put("items", items);
        Ui.async(this, "Menyimpan draft", () -> client.post("/api/sales", body), ok -> {
            dispose();
            done.run();
            Ui.info(null, "Draft tersimpan. Konfirmasi saja setelah penjualan benar-benar disetujui.");
        });
    }
}
