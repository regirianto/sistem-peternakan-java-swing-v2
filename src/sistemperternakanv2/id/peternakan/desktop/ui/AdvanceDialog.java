package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Uang muka pelanggan/supplier dan refund; pihak dipilih dari daftar mitra. */
final class AdvanceDialog extends JDialog {
    private final ApiClient client;
    private final String type;
    private final List<Map<String, Object>> sources;
    private final Runnable done;

    private JComboBox<String> partyCombo;

    AdvanceDialog(JPanel parent, ApiClient client, String type, List<Map<String, Object>> parties,
                  Runnable done) {
        this.client = client;
        this.type = type;
        this.done = done;
        setTitle(type.equals("ADVANCE_CUSTOMER") ? "Uang Muka Pelanggan"
                : type.equals("ADVANCE_SUPPLIER") ? "Uang Muka Supplier" : "Refund");
        setModal(true);
        setSize(560, 340);
        setLocationRelativeTo(parent);

        String roleCode = type.equals("ADVANCE_SUPPLIER") ? "SUPPLIER" : "CUSTOMER";
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> party : parties) {
            if (ApiClient.stringList(party.get("roles")).contains(roleCode)) {
                filtered.add(party);
            }
        }
        this.sources = filtered;

        partyCombo = new JComboBox<>();
        partyCombo.setName("advance.party");
        for (Map<String, Object> row : sources) {
            partyCombo.addItem(ApiClient.str(row.get("name")));
        }
        JTextField date = Ui.field(Ui.today());
        JTextField amount = Ui.field("");
        amount.setName("advance.amount");
        JTextField notes = Ui.field("");

        JPanel form = new JPanel(new java.awt.GridBagLayout());
        form.setOpaque(false);
        Ui.addRow(form, 0, "Pihak", partyCombo);
        Ui.addRow(form, 1, "Tanggal", date);
        Ui.addRow(form, 2, "Jumlah", amount);
        Ui.addRow(form, 3, "Catatan", notes);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.add(Ui.primary("Simpan", () -> save(date, amount, notes)));
        actions.add(Ui.button("Batal", this::dispose));

        JPanel content = new JPanel(new java.awt.BorderLayout(0, 10));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(form, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        setContentPane(content);
        setVisible(true);
    }

    private void save(JTextField date, JTextField amount, JTextField notes) {
        if (sources.isEmpty()) {
            Ui.error(this, "Belum ada pihak yang sesuai untuk transaksi ini.");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceType", type);
        body.put("paymentDate", date.getText().trim());
        body.put("amount", amount.getText().trim());
        body.put("method", "TUNAI");
        body.put("notes", notes.getText().trim());
        body.put("partyId", ApiClient.lng(sources.get(partyCombo.getSelectedIndex()).get("id")));
        body.put("requestId", UUID.randomUUID().toString());
        Ui.async(this, "Menyimpan", () -> client.post("/api/payments", body), ok -> {
            dispose();
            done.run();
        });
    }
}
