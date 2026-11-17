package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dialog pengeluaran baru: kategori, nilai, pihak, dan sifat alokasi. */
public final class ExpenseDialog extends JDialog {
    private final ApiClient client;
    private final List<Map<String, Object>> categories;
    private final List<Map<String, Object>> parties;
    private final Runnable done;

    private JComboBox<String> categoryCombo;
    private JComboBox<String> partyCombo;

    public ExpenseDialog(JPanel parent, ApiClient client, List<Map<String, Object>> categories,
                         List<Map<String, Object>> parties, Runnable done) {
        this.client = client;
        this.categories = categories;
        this.parties = parties;
        this.done = done;
        setTitle("Pengeluaran Baru");
        setModal(true);
        setSize(560, 480);
        setLocationRelativeTo(parent);

        JComboBox<String> category = new JComboBox<>();
        category.setName("expense.category");
        for (Map<String, Object> row : categories) {
            category.addItem(ApiClient.str(row.get("code")) + " - " + ApiClient.str(row.get("name")));
        }
        this.categoryCombo = category;
        JTextField date = Ui.field(Ui.today());
        date.setName("expense.date");
        JTextField amount = Ui.field("");
        amount.setName("expense.amount");
        JComboBox<String> party = new JComboBox<>();
        party.addItem("");
        for (Map<String, Object> row : parties) {
            party.addItem(ApiClient.str(row.get("name")));
        }
        this.partyCombo = party;
        JTextField purpose = Ui.field("");
        purpose.setName("expense.purpose");
        JTextField notes = Ui.field("");
        notes.setName("expense.notes");
        javax.swing.JCheckBox allocatable = new javax.swing.JCheckBox("Biaya bersama (dialokasikan)");
        JTextField serviceStart = Ui.field(Ui.today());
        JTextField serviceEnd = Ui.field(Ui.today());

        JPanel form = new JPanel(new java.awt.GridBagLayout());
        form.setOpaque(false);
        Ui.addRow(form, 0, "Kategori", category);
        Ui.addRow(form, 1, "Tanggal", date);
        Ui.addRow(form, 2, "Nilai", amount);
        Ui.addRow(form, 3, "Pihak (opsional)", party);
        Ui.addRow(form, 4, "Tujuan", purpose);
        Ui.addRow(form, 5, "Catatan", notes);
        Ui.addRow(form, 6, "Sifat biaya", allocatable);
        Ui.addRow(form, 7, "Mulai layanan", serviceStart);
        Ui.addRow(form, 8, "Akhir layanan", serviceEnd);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.add(Ui.primary("Simpan Draf", () -> save(date, amount, purpose, notes,
                allocatable, serviceStart, serviceEnd)));
        actions.add(Ui.button("Batal", this::dispose));

        JPanel content = new JPanel(new java.awt.BorderLayout(0, 10));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(form, BorderLayout.CENTER);
        content.add(new javax.swing.JScrollPane(form), java.awt.BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        setContentPane(content);
        setVisible(true);
    }

    private void save(JTextField date, JTextField amount, JTextField purpose,
                      JTextField notes, javax.swing.JCheckBox allocatable, JTextField serviceStart,
                      JTextField serviceEnd) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", ApiClient.lng(categories.get(categoryCombo.getSelectedIndex()).get("id")));
        body.put("expenseDate", date.getText().trim());
        body.put("amount", amount.getText().trim());
        if (partyCombo.getSelectedIndex() > 0) {
            body.put("partyId", ApiClient.lng(parties.get(partyCombo.getSelectedIndex() - 1).get("id")));
        }
        body.put("purpose", purpose.getText().trim());
        body.put("notes", notes.getText().trim());
        body.put("allocatable", allocatable.isSelected());
        if (allocatable.isSelected()) {
            body.put("serviceStart", serviceStart.getText().trim());
            body.put("serviceEnd", serviceEnd.getText().trim());
        }
        Ui.async(this, "Menyimpan pengeluaran", () -> client.post("/api/expenses", body), ok -> {
            dispose();
            done.run();
        });
    }
}
