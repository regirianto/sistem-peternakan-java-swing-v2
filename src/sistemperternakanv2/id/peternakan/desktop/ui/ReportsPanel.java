package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pratinjau, unduh, dan ekspor laporan PDF/CSV dengan rentang periode. */
public final class ReportsPanel extends JPanel implements MainFrame.Refreshable {
    private final ApiClient client;
    private final String[][] reports = {
            {"laporan_persediaan", "Laporan Persediaan Kambing"},
            {"laba_rugi_per_kambing", "Laba Rugi per Kambing"},
            {"rekap_keuntungan_kerugian", "Rekap Keuntungan & Kerugian"},
            {"laba_rugi_periode", "Laba Rugi Periode"},
            {"utang_supplier", "Utang Supplier"},
            {"piutang_pelanggan", "Piutang Pelanggan"},
            {"rekap_operasional", "Rekap Operasional"}
    };

    public ReportsPanel(ApiClient client, MainFrame frame) {
        this.client = client;
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.BG);
        add(Ui.header("Laporan", "Berkas PDF/CSV dihasilkan dari template internal"),
                BorderLayout.NORTH);

        javax.swing.JComboBox<String> picker = new javax.swing.JComboBox<>();
        picker.setName("reports.picker");
        for (String[] report : reports) {
            picker.addItem(report[1]);
        }
        javax.swing.JTextField from = Ui.field(Ui.monthStart());
        from.setName("reports.from");
        javax.swing.JTextField to = Ui.field(Ui.today());
        to.setName("reports.to");
        javax.swing.JLabel note = Ui.label(
                "Untuk Invoice/kuitansi, gunakan menu Penjualan dan Pembayaran pada panel terkait.");
        javax.swing.JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.add(picker);
        actions.add(Ui.label("Dari"));
        actions.add(from);
        actions.add(Ui.label("s.d."));
        actions.add(to);
        actions.add(Ui.primary("Pratinjau PDF", () -> generate(picker, from, to, "pdf", false)));
        actions.add(Ui.button("Ekspor PDF", () -> generate(picker, from, to, "pdf", true)));
        actions.add(Ui.button("Ekspor CSV", () -> generate(picker, from, to, "csv", true)));

        javax.swing.JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(actions, BorderLayout.NORTH);
        center.add(note, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        // tidak perambat: tabel pada panel lain
    }

    private String key(javax.swing.JComboBox<String> picker) {
        return reports[picker.getSelectedIndex() < 0 ? 0 : picker.getSelectedIndex()][0];
    }

    private void generate(javax.swing.JComboBox<String> picker, javax.swing.JTextField from,
                          javax.swing.JTextField to, String format, boolean save) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("from", from.getText().trim());
        query.put("to", to.getText().trim());
        query.put("format", format);
        String key = key(picker);
        Ui.async(this, "Membuat laporan", () -> client.download("/api/reports/" + key + "/file", query),
                download -> {
                    if ("pdf".equalsIgnoreCase(format) && !save) {
                        FilesUtil.preview(this, download.filename(), download.bytes());
                    } else {
                        FilesUtil.save(this, download.filename(), download.bytes());
                    }
                });
    }
}
