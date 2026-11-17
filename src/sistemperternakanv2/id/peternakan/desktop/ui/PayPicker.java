package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import java.util.List;
import java.util.Map;

/** Pilih dokumen piutang/utang yang akan dibayar, lalu buka form pembayaran. */
final class PayPicker extends JDialog {
    private final JPanel parent;
    private final ApiClient client;
    private final String type;
    private final List<Map<String, Object>> records;
    private final Runnable done;

    PayPicker(JPanel parent, ApiClient client, String type, List<Map<String, Object>> records,
              Runnable done) {
        this.parent = parent;
        this.client = client;
        this.type = type;
        this.records = records;
        this.done = done;
        setTitle(type.equals("SALE") ? "Pilih Piutang Pelanggan" : "Pilih Utang Supplier");
        setModal(true);
        setSize(520, 360);
        setLocationRelativeTo(parent);

        if (records.isEmpty()) {
            Ui.info(this, "Belum ada dokumen dengan sisa tagihan.");
            return;
        }
        JComboBox<String> doc = new JComboBox<>();
        for (Map<String, Object> row : records) {
            doc.addItem(ApiClient.str(row.get("doc_no")) + " - " + ApiClient.str(row.get("party_name"))
                    + " (sisa " + Ui.formatMoney(row.get("remaining")) + ")");
        }

        JPanel content = new JPanel(new java.awt.BorderLayout(0, 10));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(Ui.subtitle("Pilih dokumen untuk dibayar"), BorderLayout.NORTH);
        content.add(doc, BorderLayout.CENTER);
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.add(Ui.primary("Lanjut", () -> open(doc)));
        actions.add(Ui.button("Batal", this::dispose));
        content.add(actions, BorderLayout.SOUTH);
        setContentPane(content);
        setVisible(true);
    }

    private void open(JComboBox<String> doc) {
        Map<String, Object> selected = records.get(doc.getSelectedIndex());
        dispose();
        new PaymentDialog(parent, client, type,
                ApiClient.lng(selected.get("id")),
                selected.get("supplier_id") != null
                        ? selected.get("supplier_id")
                        : selected.get("customer_id"),
                selected.get("remaining"), done);
    }
}
