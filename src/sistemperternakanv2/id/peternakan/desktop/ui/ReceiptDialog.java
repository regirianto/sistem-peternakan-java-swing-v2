package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.BoxLayout;
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
import java.util.UUID;

/** Dialog penerimaan kambing: identifikasi setiap ekor dan biaya pengiriman. */
public final class ReceiptDialog extends JDialog {
    private final ApiClient client;
    private final Map<String, Object> purchase;
    private final List<Map<String, Object>> pens;
    private final Runnable done;

    private final List<Long> linePurchaseItemIds = new ArrayList<>();
    private final List<String> lineGoatCodes = new ArrayList<>();
    private final javax.swing.table.DefaultTableModel lines = Ui.model(
            "Baris", "Kambing", "Berat (kg)", "Kandang", "Kelamin", "Jenis", "Status");
    private final JTable linesTable = Ui.table(lines);
    private boolean saving;

    public ReceiptDialog(JPanel parent, ApiClient client, Map<String, Object> purchase,
                         List<Map<String, Object>> pens, Runnable done) {
        this.client = client;
        this.purchase = purchase;
        this.pens = pens;
        this.done = done;
        setTitle("Penerimaan - " + ApiClient.str(purchase.get("doc_no")));
        setModal(true);
        setSize(820, 560);
        setLocationRelativeTo(parent);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        List<Map<String, Object>> items = ApiClient.list(purchase.get("items"));
        List<Map<String, Object>> receivable = new ArrayList<>();
        JComboBox<String> purchaseItem = new JComboBox<>();
        purchaseItem.setName("receipt.item");
        for (Map<String, Object> item : items) {
            long remaining = ApiClient.lng(item.get("qty"))
                    - ApiClient.lng(item.get("received_qty"))
                    - ApiClient.lng(item.get("rejected_qty"));
            if (remaining > 0) {
                receivable.add(item);
                purchaseItem.addItem("Baris " + ApiClient.lng(item.get("line_no"))
                        + " · " + ApiClient.str(item.get("description"))
                        + " · sisa " + remaining
                        + " · @" + Ui.formatMoney(item.get("unit_price")));
            }
        }
        JTextField code = Ui.field("");
        code.setName("receipt.code");
        JTextField weight = Ui.field("");
        weight.setName("receipt.weight");
        JComboBox<String> pen = penCombo();
        pen.setName("receipt.pen");
        JComboBox<String> gender = Ui.combo("JANTAN", "BETINA");
        JComboBox<String> jenis = Ui.combo("Kambing", "Domba");
        jenis.setName("receipt.jenis");
        JTextField age = Ui.field("");
        JTextField marks = Ui.field("");
        JComboBox<String> status = Ui.combo("Sehat", "Mati");
        status.setName("receipt.status");

        JPanel form = new JPanel(new java.awt.GridBagLayout());
        form.setOpaque(false);
        Ui.addRow(form, 0, "Item pembelian", purchaseItem);
        Ui.addRow(form, 1, "Kode kambing", code);
        Ui.addRow(form, 2, "Berat (kg)", weight);
        Ui.addRow(form, 3, "Kandang", pen);
        Ui.addRow(form, 4, "Kelamin", gender);
        Ui.addRow(form, 5, "Jenis", jenis);
        Ui.addRow(form, 6, "Umur (bulan) opsional", age);
        Ui.addRow(form, 7, "Ciri fisik", marks);
        Ui.addRow(form, 8, "Status", status);

        JButton add = Ui.primary("Tambah Ekor", () -> addLine(receivable, purchaseItem, code, weight, pen,
                gender, jenis, age, marks, status));
        JButton remove = Ui.button("Hapus Baris", this::removeLine);
        JTextField date = Ui.field(Ui.today());
        date.setName("receipt.date");
        JTextField shipping = Ui.field("0");
        shipping.setName("receipt.shipping");
        JTextField notes = Ui.field("");
        notes.setName("receipt.notes");
        JPanel top = new JPanel(new java.awt.GridBagLayout());
        top.setOpaque(false);
        Ui.addRow(top, 0, "Tanggal terima", date);
        Ui.addRow(top, 1, "Biaya kirim", shipping);
        Ui.addRow(top, 2, "Catatan", notes);

        JPanel west = new JPanel();
        west.setOpaque(false);
        west.setLayout(new javax.swing.BoxLayout(west, javax.swing.BoxLayout.Y_AXIS));
        west.add(top);
        west.add(form);
        west.add(add);
        west.add(remove);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(Ui.subtitle("Ekor yang diterima"), BorderLayout.NORTH);
        center.add(new JScrollPane(linesTable), BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.add(Ui.primary("Simpan Penerimaan", () -> save(date, shipping, notes)));
        actions.add(Ui.button("Tutup", this::dispose));

        content.add(west, BorderLayout.WEST);
        content.add(center, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        setContentPane(content);
        setVisible(true);
    }

    private JComboBox<String> penCombo() {
        JComboBox<String> combo = new JComboBox<>();
        for (Map<String, Object> pen : pens) {
            combo.addItem(ApiClient.str(pen.get("code")) + " - " + ApiClient.str(pen.get("name")));
        }
        return combo;
    }

    private void addLine(List<Map<String, Object>> items, JComboBox<String> purchaseItem,
                        JTextField code, JTextField weight, JComboBox<String> pen, JComboBox<String> gender,
                        JComboBox<String> jenis, JTextField age, JTextField marks, JComboBox<String> status) {
        if (items.isEmpty() || purchaseItem.getSelectedIndex() < 0
                || purchaseItem.getSelectedIndex() >= items.size()) {
            Ui.error(this, "Item pembelian tidak valid atau sudah diterima semua.");
            return;
        }
        boolean dead = "Mati".equals(String.valueOf(status.getSelectedItem()));
        if (!dead && (code.getText().isBlank() || weight.getText().isBlank() || pens.isEmpty())) {
            Ui.error(this, "Kode kambing, berat, dan kandang wajib diisi untuk status Sehat.");
            return;
        }
        long id = ApiClient.lng(items.get(purchaseItem.getSelectedIndex()).get("id"));
        linePurchaseItemIds.add(id);
        String codeText = dead ? "" : code.getText().trim();
        lineGoatCodes.add(codeText);
        lines.addRow(new Object[]{ApiClient.lng(items.get(purchaseItem.getSelectedIndex()).get("line_no")),
                dead ? "-" : codeText, dead ? "0" : weight.getText().trim(),
                pens.isEmpty() ? "-" : ApiClient.str(pens.get(pen.getSelectedIndex()).get("code")),
                String.valueOf(gender.getSelectedItem()), String.valueOf(jenis.getSelectedItem()),
                dead ? "Mati" : "Sehat"});
        if (!dead) {
            code.setText(codeText + "+" + lines.getRowCount());
        }
    }

    private void removeLine() {
        int row = linesTable.getSelectedRow();
        if (row >= 0) {
            lines.removeRow(row);
            linePurchaseItemIds.remove(row);
            lineGoatCodes.remove(row);
        }
    }

    private void save(JTextField date, JTextField shipping, JTextField notes) {
        if (saving) {
            return;
        }
        if (lines.getRowCount() == 0) {
            Ui.error(this, "Tambahkan minimal satu kambing yang diterima.");
            return;
        }
        String shippingValue = shipping.getText().trim().isBlank() ? "0" : shipping.getText().trim();
        List<Map<String, Object>> goatLines = new ArrayList<>();
        for (int row = 0; row < lines.getRowCount(); row++) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("purchaseItemId", linePurchaseItemIds.get(row));
            boolean dead = "Mati".equalsIgnoreCase(ApiClient.str(lines.getValueAt(row, 6)));
            line.put("status", dead ? "MATI" : "SEHAT");
            if (!dead) {
                line.put("goatCode", lineGoatCodes.get(row));
                line.put("weight", lines.getValueAt(row, 2));
                line.put("penId", pens.get(penIndex(ApiClient.str(lines.getValueAt(row, 3)))).get("id"));
                line.put("gender", lines.getValueAt(row, 4));
                line.put("jenis", lines.getValueAt(row, 5));
                line.put("physicalMarks", "");
            }
            goatLines.add(line);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("purchaseId", ApiClient.lng(purchase.get("id")));
        payload.put("receiptDate", date.getText().trim());
        payload.put("shippingCost", shippingValue);
        payload.put("notes", notes.getText().trim());
        payload.put("items", goatLines);
        payload.put("requestId", UUID.randomUUID().toString());
        saving = true;
        Ui.async(this, "Menyimpan penerimaan", () -> client.post("/api/receipts", payload), ok -> {
            dispose();
            done.run();
        }, () -> saving = false);
    }

    private int penIndex(String code) {
        for (int i = 0; i < pens.size(); i++) {
            if (ApiClient.str(pens.get(i).get("code")).equals(code)) {
                return i;
            }
        }
        return 0;
    }
}
