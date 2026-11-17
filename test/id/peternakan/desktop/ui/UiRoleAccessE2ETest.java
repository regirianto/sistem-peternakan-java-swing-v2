package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Memeriksa menu dan selector cabang untuk setiap role terhadap layanan nyata.
 * Role yang diuji: owner, admin, petugas Pusat, petugas Cikarang.
 */
class UiRoleAccessE2ETest {

    @Test
    void menuDanCabangSesuaiRole() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("E2E_UI")),
                "Set E2E_UI=1 dan jalankan server uji untuk mengaktifkan UI role E2E.");
        String base = System.getenv().getOrDefault("E2E_BASE_URL", "http://127.0.0.1:8781");

        expectMenus(base, "owner", "Owner#2024", Set.of(
                "Dashboard", "Data Kambing", "Kandang", "Supplier & Pelanggan",
                "Pembelian & Penerimaan", "Penjualan", "Pemotongan", "Persediaan Daging",
                "Pembayaran", "Pakan", "Pengeluaran & Alokasi", "Laporan", "Cabang",
                "Pengguna", "Log Aktivitas", "Pengaturan & Periode"), 3);

        expectMenus(base, "admin", "Admin#2024", Set.of(
                "Dashboard", "Data Kambing", "Kandang", "Supplier & Pelanggan",
                "Pembelian & Penerimaan", "Penjualan", "Pemotongan", "Persediaan Daging",
                "Pembayaran", "Pakan", "Pengeluaran & Alokasi"), 1);

        expectMenus(base, "petugas", "Petugas#2024", Set.of(
                "Dashboard", "Data Kambing", "Pemotongan", "Persediaan Daging", "Pakan"), 1);

        expectMenus(base, "cikarang", "Cikarang#2024", Set.of(
                "Dashboard", "Data Kambing", "Pemotongan", "Persediaan Daging", "Pakan"), 1);
    }

    private static void expectMenus(String base, String username, String password,
                                    Set<String> expected, int branchItems) throws Exception {
        ApiClient client = new ApiClient(base);
        client.login(username, password);
        AtomicReference<MainFrame> holder = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame(client);
            frame.setVisible(true);
            holder.set(frame);
        });
        MainFrame frame = holder.get();
        try {
            waitFor(() -> branchCombo(frame) != null && branchCombo(frame).getItemCount() > 0, 8000);
            Set<String> actual = new LinkedHashSet<>();
            for (JButton button : findAll(frame, JButton.class)) {
                Object key = button.getClientProperty("key");
                if (key != null) {
                    actual.add(button.getText());
                }
            }
            assertEquals(expected, actual, "Menu tidak sesuai untuk role " + username);

            // Buka setiap menu dan pastikan tidak ada dialog kesalahan.
            for (String menu : expected) {
                JButton button = null;
                for (JButton candidate : findAll(frame, JButton.class)) {
                    if (menu.equals(candidate.getText()) && candidate.isShowing()) {
                        button = candidate;
                        break;
                    }
                }
                assertNotNull(button, "Menu tidak dapat diklik: " + menu);
                JButton target = button;
                SwingUtilities.invokeAndWait(target::doClick);
                Thread.sleep(700);
                JDialog error = errorDialog();
                if (error != null) {
                    String message = errorText(error);
                    JButton ok = button(error, "OK");
                    if (ok != null) {
                        SwingUtilities.invokeAndWait(ok::doClick);
                    }
                    fail("Role " + username + " gagal membuka menu " + menu + ": " + message);
                }
            }

            JComboBox<?> combo = branchCombo(frame);
            assertNotNull(combo, "Selector cabang tidak ada untuk " + username);
            if (expected.contains("Cabang")) {
                assertTrue(combo.getItemCount() >= branchItems,
                        "Owner harus melihat semua cabang");
                assertEquals("Semua Cabang", String.valueOf(combo.getItemAt(0)));
            } else {
                assertEquals(branchItems, combo.getItemCount(),
                        "Non-owner hanya melihat cabang yang ditugaskan: " + username);
            }
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
            client.logout();
        }
    }

    private static JDialog errorDialog() {
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && window instanceof JDialog dialog
                    && !findAll(dialog, JOptionPane.class).isEmpty()) {
                return dialog;
            }
        }
        return null;
    }

    private static String errorText(JDialog dialog) {
        List<JOptionPane> panes = findAll(dialog, JOptionPane.class);
        return panes.isEmpty() || panes.get(0).getMessage() == null
                ? "" : String.valueOf(panes.get(0).getMessage());
    }

    private static JButton button(Container root, String text) {
        for (JButton button : findAll(root, JButton.class)) {
            if (text.equals(button.getText())) {
                return button;
            }
        }
        return null;
    }

    private static JComboBox<?> branchCombo(Container root) {
        for (Component component : findAll(root, Component.class)) {
            if ("branch.selector".equals(component.getName()) && component instanceof JComboBox<?> combo) {
                return combo;
            }
        }
        return null;
    }

    private static void waitFor(BooleanSupplier condition, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        assertNotNull(null, "Kondisi UI tidak tercapai");
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
}
