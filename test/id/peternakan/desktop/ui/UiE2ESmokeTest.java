package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test UI terhadap layanan nyata. Hanya berjalan bila E2E_UI=1 dan
 * server uji tersedia. Fokus: login programatik, semua menu utama dapat
 * dibuka, data tampil, selector cabang mengubah data, dan toolbar penting ada.
 */
class UiE2ESmokeTest {

    @Test
    void semuaMenuUtamaDanSelectorCabang() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("E2E_UI")),
                "Set E2E_UI=1 dan jalankan server uji untuk mengaktifkan UI E2E.");
        String base = System.getenv().getOrDefault("E2E_BASE_URL", "http://127.0.0.1:8781");
        ApiClient client = new ApiClient(base);
        client.login("owner", "Owner#2024");

        AtomicReference<MainFrame> holder = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame(client);
            frame.setVisible(true);
            holder.set(frame);
        });
        MainFrame frame = holder.get();
        try {
            waitFor(() -> branchCombo(frame) != null && branchCombo(frame).getItemCount() >= 3, 8000);
            JComboBox<?> branches = branchCombo(frame);
            assertEquals("Semua Cabang", String.valueOf(branches.getItemAt(0)));
            assertTrue(branches.getItemCount() >= 3);

            String[] menus = {"Dashboard", "Data Kambing", "Kandang", "Supplier & Pelanggan",
                    "Pembelian & Penerimaan", "Penjualan", "Pemotongan", "Persediaan Daging",
                    "Pembayaran", "Pakan", "Pengeluaran & Alokasi", "Laporan", "Cabang",
                    "Pengguna", "Log Aktivitas", "Pengaturan & Periode"};
            for (String menu : menus) {
                JButton button = findButton(frame, menu);
                assertNotNull(button, "Menu tidak ditemukan: " + menu);
                click(button);
                pause(350);
            }

            click(findButton(frame, "Data Kambing"));
            waitForRows(frame, 5);
            assertEquals(5, visibleTable(frame).getRowCount(), "Data kambing demo harus tampil");

            click(findButton(frame, "Kandang"));
            waitForRows(frame, 3);
            assertEquals(3, visibleTable(frame).getRowCount(), "Daftar kandang harus tampil");

            click(findButton(frame, "Pembelian & Penerimaan"));
            waitForRows(frame, 2);
            assertNotNull(findButton(frame, "Pembelian Baru"));
            assertNotNull(findButton(frame, "Konfirmasi"));
            assertNotNull(findButton(frame, "Terima Kambing"));

            click(findButton(frame, "Pembayaran"));
            waitForRows(frame, 1);
            assertNotNull(findButton(frame, "Bayar"));

            // Pilih cabang Cikarang: daftar kambing harus menyusut.
            int cikarang = indexOf(branches, "CIKARANG");
            assertTrue(cikarang > 0, "Cabang Cikarang tidak ada di selector");
            SwingUtilities.invokeAndWait(() -> branches.setSelectedIndex(cikarang));
            click(findButton(frame, "Data Kambing"));
            waitForRowCount(frame, 2);
            assertEquals(2, visibleTable(frame).getRowCount(), "Cabang Cikarang hanya punya 2 kambing");

            // Kembali ke semua cabang.
            SwingUtilities.invokeAndWait(() -> branches.setSelectedIndex(0));
            click(findButton(frame, "Data Kambing"));
            waitForRowCount(frame, 5);
            assertEquals(5, visibleTable(frame).getRowCount(), "Semua cabang menampilkan 5 kambing");
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
            client.logout();
        }
    }

    private static int indexOf(JComboBox<?> combo, String prefix) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (String.valueOf(combo.getItemAt(i)).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private static void click(JButton button) throws Exception {
        SwingUtilities.invokeAndWait(button::doClick);
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void waitFor(BooleanSupplier condition, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            pause(100);
        }
        assertTrue(condition.getAsBoolean(), "Kondisi UI tidak tercapai dalam batas waktu");
    }

    private static void waitForRows(MainFrame frame, int minimum) throws Exception {
        waitFor(() -> visibleTable(frame) != null && visibleTable(frame).getRowCount() >= minimum, 8000);
    }

    private static void waitForRowCount(MainFrame frame, int expected) throws Exception {
        waitFor(() -> visibleTable(frame) != null && visibleTable(frame).getRowCount() == expected, 8000);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static JComboBox<?> branchCombo(Container root) {
        List<JComboBox<?>> combos = findAll(root, (Class) JComboBox.class);
        for (JComboBox<?> combo : combos) {
            if (combo.getItemCount() > 0 && String.valueOf(combo.getItemAt(0)).contains("Cabang")) {
                return combo;
            }
        }
        return combos.isEmpty() ? null : combos.get(0);
    }

    private static JButton findButton(Container root, String text) {
        for (JButton button : findAll(root, JButton.class)) {
            if (text.equals(button.getText())) {
                return button;
            }
        }
        return null;
    }

    private static JTable visibleTable(Container root) {
        for (JTable table : allTables(root)) {
            if (table.isShowing()) {
                return table;
            }
        }
        return null;
    }

    private static List<JTable> allTables(Container root) {
        return findAll(root, JTable.class);
    }

    private static <T extends Component> List<T> findAll(Container root, Class<T> type) {
        List<T> result = new ArrayList<>();
        collect(root, type, result);
        return result;
    }

    private static <T extends Component> void collect(Container root, Class<T> type, List<T> result) {
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)) {
                result.add(type.cast(component));
            }
            if (component instanceof Container container) {
                collect(container, type, result);
            }
        }
    }

    @SuppressWarnings("unused")
    private static List<Window> windows() {
        return List.of(Window.getWindows());
    }
}
