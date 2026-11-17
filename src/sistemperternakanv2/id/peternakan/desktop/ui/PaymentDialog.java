package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Dialog pembayaran pelanggan/supplier dengan idempotensi. */
public final class PaymentDialog extends JDialog {
    private final ApiClient client;
    private final String sourceType;
    private final long sourceId;
    private final Object partyId;
    private final Runnable done;

    public PaymentDialog(JPanel parent, ApiClient client, String sourceType, long sourceId, Object partyId,
                         Object remaining, Runnable done) {
        this.client = client;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.partyId = partyId;
        this.done = done;
        setTitle("Pembayaran");
        setModal(true);
        setSize(560, 340);
        setLocationRelativeTo(parent);

        JTextField date = Ui.field(Ui.today());
        JTextField amount = Ui.field(ApiClient.number(remaining));
        amount.setName("payment.amount");
        JComboBox<String> method = Ui.combo("TUNAI", "TRANSFER");
        JTextField reference = Ui.field("");
        JTextField notes = Ui.field("");

        JPanel form = new JPanel(new java.awt.GridBagLayout());
        form.setOpaque(false);
        Ui.addRow(form, 0, "Dokumen sumber", new javax.swing.JLabel(ApiClient.str(remaining).isBlank()
                ? "-" : sourceType));
        Ui.addRow(form, 1, "Tanggal", date);
        Ui.addRow(form, 2, "Jumlah", amount);
        Ui.addRow(form, 3, "Metode", method);
        Ui.addRow(form, 4, "No. referensi", reference);
        Ui.addRow(form, 5, "Catatan", notes);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.add(Ui.primary("Simpan Pembayaran", () -> save(date, amount, method, reference, notes)));
        actions.add(Ui.button("Batal", this::dispose));

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(form, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        setContentPane(content);
        setVisible(true);
    }

    private void save(JTextField date, JTextField amount, JComboBox<String> method,
                      JTextField reference, JTextField notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceType", sourceType);
        body.put("sourceId", sourceId);
        body.put("paymentDate", date.getText().trim());
        body.put("amount", amount.getText().trim());
        body.put("method", String.valueOf(method.getSelectedItem()));
        body.put("referenceNo", reference.getText().trim());
        body.put("notes", notes.getText().trim());
        if (partyId != null) {
            body.put("partyId", ApiClient.lng(partyId));
        }
        body.put("requestId", UUID.randomUUID().toString());
        Ui.async(this, "Menyimpan pembayaran", () -> client.post("/api/payments", body), ok -> {
            dispose();
            done.run();
        });
    }
}
