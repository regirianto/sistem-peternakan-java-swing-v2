package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI business flow E2E: login lewat form, buat pembelian, konfirmasi,
 * terima kambing, siapkan siap jual, jual, bayar, dan pratinjau laporan.
 */
class UiBusinessFlowE2ETest {

    @Test
    void beliTerimaJualBayarDanLaporanLewatUi() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("E2E_UI")),
                "Set E2E_UI=1 dan jalankan server uji untuk mengaktifkan UI flow E2E.");
        String base = System.getenv().getOrDefault("E2E_BASE_URL", "http://127.0.0.1:8781");
        ApiClient client = new ApiClient(base);

        LoginFrame login = onEdt(() -> {
            LoginFrame frame = new LoginFrame(client);
            frame.setVisible(true);
            return frame;
        });
        try {
            waitFor(() -> findByName(login, "login.username") != null, 8000);
            setText(login, "login.username", "owner");
            setText(login, "login.password", "Owner#2024");
            clickLater(button(login, "Masuk"));

            MainFrame main = (MainFrame) waitForWindow(w -> w instanceof MainFrame && w.isShowing(), 12000);
            assertNotNull(main, "Jendela utama tidak muncul setelah login");
            try {
                waitFor(() -> branchCombo(main) != null && branchCombo(main).getItemCount() >= 3, 8000);

                // ---------- Pembelian baru ----------
                clickLater(button(main, "Pembelian & Penerimaan"));
                JTable purchases = waitForTable(main, "purchases.table", 2);
                int before = rowCount(purchases);
                clickLater(button(main, "Pembelian Baru"));
                JDialog purchaseDialog = waitForDialog("Pembelian Baru", 10000);
                assertNotNull(purchaseDialog, "Dialog Pembelian Baru tidak muncul");
                clickLater(button(purchaseDialog, "Simpan Draf"));
                waitFor(() -> rowCount(purchases) == before + 1, 10000);

                // ---------- Konfirmasi ----------
                confirmWithRetry(button(main, "Konfirmasi"), purchases, 0);
                waitFor(() -> "DIKONFIRMASI".equals(cell(purchases, 0, 5)), 8000);

                // ---------- Penerimaan ----------
                selectRow(purchases, 0);
                clickLater(button(main, "Terima Kambing"));
                JDialog receipt = waitForDialog("Penerimaan", 10000);
                assertNotNull(receipt, "Dialog penerimaan tidak muncul");
                javax.swing.JComboBox<?> jenis = (javax.swing.JComboBox<?>) findByName(receipt, "receipt.jenis");
                assertNotNull(jenis, "Dropdown jenis tidak ada");
                assertEquals(2, jenis.getItemCount(), "Dropdown jenis harus Kambing/Domba");
                comboByPrefix(receipt, "receipt.jenis", "Domba");
                setText(receipt, "receipt.code", "UI-G1");
                setText(receipt, "receipt.weight", "30");
                comboByPrefix(receipt, "receipt.pen", "KD-A");
                click(button(receipt, "Tambah Ekor"));
                clickLater(button(receipt, "Simpan Penerimaan"));
                waitFor(() -> "SELESAI".equals(cell(purchases, 0, 5)), 10000);
                waitFor(() -> "Domba".equals(goatJenis(client, "UI-G1")), 10000);

                // ---------- Tandai siap jual lewat UI ----------
                clickLater(button(main, "Data Kambing"));
                JTable goats = waitForTable(main, "goats.table", 1);
                JDialog goatDialog = null;
                for (int attempt = 0; attempt < 20 && goatDialog == null; attempt++) {
                    int row = rowWith(goats, 0, "UI-G1");
                    if (row >= 0) {
                        selectRow(goats, row);
                        clickLater(button(main, "Detail"));
                        goatDialog = waitForDialog("Kambing UI-G1", 10000);
                    } else {
                        pause(250);
                    }
                }
                assertNotNull(goatDialog, "Detail kambing UI-G1 tidak muncul");
                click(button(goatDialog, "Tandai Siap Jual"));
                waitFor(() -> readiness(client, "UI-G1"), 10000);
                SwingUtilities.invokeAndWait(goatDialog::dispose);

                // ---------- Penjualan ----------
                clickLater(button(main, "Penjualan"));
                JTable sales = waitForTable(main, "sales.table", 1);
                int salesBefore = rowCount(sales);
                clickLater(button(main, "Penjualan Kambing"));
                JDialog saleDialog = waitForDialog("Penjualan Baru", 10000);
                assertNotNull(saleDialog, "Dialog penjualan tidak muncul");
                comboByPrefix(saleDialog, "sale.goat", "UI-G1");
                click(button(saleDialog, "Tambah Baris"));
                JTable saleLines = (JTable) findByName(saleDialog, "sale.table");
                assertNotNull(saleLines);
                SwingUtilities.invokeAndWait(() -> saleLines.setValueAt("3500000", 0, 3));
                clickLater(button(saleDialog, "Simpan Draft"));
                dismissOptionPanes();
                waitFor(() -> rowCount(sales) == salesBefore + 1, 10000);

                // ---------- Konfirmasi dan bayar ----------
                confirmWithRetry(button(main, "Konfirmasi"), sales, 0);
                waitFor(() -> "DIKONFIRMASI".equals(cell(sales, 0, 5)), 10000);
                selectRow(sales, 0);
                clickLater(button(main, "Bayar"));
                JDialog payment = waitForDialog("Pembayaran", 10000);
                assertNotNull(payment, "Dialog pembayaran tidak muncul");
                JTextField amount = (JTextField) findByName(payment, "payment.amount");
                assertNotNull(amount);
                assertTrue(amount.getText().replace(".", "").replace(",", "").length() > 0,
                        "Jumlah pembayaran harus terisi");
                clickLater(button(payment, "Simpan Pembayaran"));
                waitFor(() -> "LUNAS".equals(cell(sales, 0, 6)), 10000);

                // ---------- Laporan ----------
                clickLater(button(main, "Laporan"));
                waitFor(() -> findByName(main, "reports.picker") != null, 8000);
                clickLater(button(main, "Pratinjau PDF"));
                Window preview = waitForWindow(w -> "PdfPreviewDialog".equals(w.getClass().getSimpleName())
                        && w.isShowing(), 15000);
                assertNotNull(preview, "Pratinjau PDF tidak tampil");
                SwingUtilities.invokeAndWait(preview::dispose);

                assertTrue(rowCount(sales) >= salesBefore + 1);
                assertEquals("LUNAS", cell(sales, 0, 6));
            } finally {
                SwingUtilities.invokeAndWait(main::dispose);
            }
        } finally {
            client.logout();
            if (login.isShowing()) {
                SwingUtilities.invokeAndWait(login::dispose);
            }
        }
    }

    private static String goatJenis(ApiClient client, String code) {
        try {
            Object items = client.get("/api/goats", Map.of("q", code, "size", "5")).get("items");
            for (Map<String, Object> goat : ApiClient.list(items)) {
                if (code.equals(ApiClient.str(goat.get("code")))) {
                    return ApiClient.str(goat.get("jenis"));
                }
            }
        } catch (Exception ignored) {
            // Ulangi pada polling berikutnya.
        }
        return "";
    }

    private static boolean readiness(ApiClient client, String code) {
        try {
            Object items = client.get("/api/goats", Map.of("q", code, "size", "5")).get("items");
            for (Map<String, Object> goat : ApiClient.list(items)) {
                if (code.equals(ApiClient.str(goat.get("code")))) {
                    return "SIAP_JUAL".equals(ApiClient.str(goat.get("sale_readiness")));
                }
            }
        } catch (Exception ignored) {
            // Ulangi pada polling berikutnya.
        }
        return false;
    }

    private static JTable waitForTable(Container root, String name, int minimumRows) throws Exception {
        waitFor(() -> {
            JTable table = (JTable) findByName(root, name);
            return table != null && table.getRowCount() >= minimumRows;
        }, 10000);
        return (JTable) findByName(root, name);
    }

    private static int rowCount(JTable table) {
        int[] value = {0};
        try {
            SwingUtilities.invokeAndWait(() -> value[0] = table.getRowCount());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return value[0];
    }

    private static String cell(JTable table, int row, int column) {
        String[] value = {null};
        try {
            SwingUtilities.invokeAndWait(() -> value[0] = String.valueOf(table.getValueAt(row, column)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return value[0];
    }

    private static int rowWith(JTable table, int column, String expected) throws Exception {
        int[] value = {-1};
        SwingUtilities.invokeAndWait(() -> {
            for (int row = 0; row < table.getRowCount(); row++) {
                if (expected.equals(String.valueOf(table.getValueAt(row, column)))) {
                    value[0] = row;
                    return;
                }
            }
        });
        return value[0];
    }

    private static void selectRow(JTable table, int row) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            table.setRowSelectionInterval(row, row);
            table.scrollRectToVisible(table.getCellRect(row, 0, true));
        });
    }

    private static void setText(Container root, String name, String value) throws Exception {
        Component component = findByName(root, name);
        assertNotNull(component, "Komponen tidak ditemukan: " + name);
        SwingUtilities.invokeAndWait(() -> ((JTextField) component).setText(value));
    }

    @SuppressWarnings("unchecked")
    private static void comboByPrefix(Container root, String name, String prefix) throws Exception {
        Component component = findByName(root, name);
        assertNotNull(component, "Combo tidak ditemukan: " + name);
        JComboBox<String> combo = (JComboBox<String>) component;
        int index = -1;
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (String.valueOf(combo.getItemAt(i)).contains(prefix)) {
                index = i;
                break;
            }
        }
        assertTrue(index >= 0, "Item combo tidak ditemukan: " + prefix);
        int selected = index;
        SwingUtilities.invokeAndWait(() -> combo.setSelectedIndex(selected));
    }

    private static JDialog waitForDialog(String titlePrefix, long timeout) throws Exception {
        Window window = waitForWindow(w -> w instanceof JDialog dialog && dialog.isShowing()
                && dialog.getTitle() != null && dialog.getTitle().startsWith(titlePrefix), timeout);
        return (JDialog) window;
    }

    private static void dismissOptionPanes() throws Exception {
        for (int i = 0; i < 10; i++) {
            JDialog dialog = optionPane();
            if (dialog == null) {
                return;
            }
            JButton ok = button(dialog, "OK");
            if (ok == null) {
                ok = button(dialog, "Yes");
            }
            if (ok == null) {
                ok = button(dialog, "Ya");
            }
            if (ok == null) {
                for (JButton candidate : findAll(dialog, JButton.class)) {
                    if (!"Cancel".equals(candidate.getText()) && !"Batal".equals(candidate.getText())) {
                        ok = candidate;
                        break;
                    }
                }
            }
            assertNotNull(ok, "Tombol dialog tidak ditemukan");
            click(ok);
            waitFor(() -> !dialog.isShowing(), 5000);
        }
    }

    private static void confirmWithRetry(JButton confirm, JTable table, int row) throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            dismissOptionPanes();
            selectRow(table, row);
            clickLater(confirm);
            JDialog dialog = waitForOptionPane(2500);
            if (dialog != null && "Konfirmasi".equals(dialog.getTitle())) {
                JButton yes = button(dialog, "Yes");
                if (yes == null) {
                    yes = button(dialog, "Ya");
                }
                if (yes == null) {
                    yes = button(dialog, "OK");
                }
                assertNotNull(yes, "Tombol konfirmasi tidak ditemukan");
                click(yes);
                waitFor(() -> !dialog.isShowing(), 5000);
                return;
            }
            if (dialog != null) {
                dismissOptionPanes();
            }
        }
        throw new IllegalStateException("Dialog konfirmasi tidak muncul setelah percobaan berulang");
    }

    private static JDialog optionPane() {
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && window instanceof JDialog dialog && containsOptionPane(dialog)) {
                return dialog;
            }
        }
        return null;
    }

    private static JDialog waitForOptionPane(long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            JDialog dialog = optionPane();
            if (dialog != null) {
                return dialog;
            }
            pause(100);
        }
        return null;
    }

    private static boolean containsOptionPane(Container root) {
        return !findAll(root, JOptionPane.class).isEmpty();
    }

    private static Window waitForWindow(Predicate<Window> predicate, long timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < deadline) {
            for (Window window : Window.getWindows()) {
                if (predicate.test(window)) {
                    return window;
                }
            }
            pause(100);
        }
        return null;
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

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static JButton button(Container root, String text) {
        JButton fallback = null;
        for (JButton button : findAll(root, JButton.class)) {
            if (text.equals(button.getText())) {
                if (button.isShowing()) {
                    return button;
                }
                fallback = button;
            }
        }
        return fallback;
    }

    private static JComboBox<?> branchCombo(Container root) {
        for (JComboBox<?> combo : findAll(root, JComboBox.class)) {
            if (combo.getItemCount() > 0 && String.valueOf(combo.getItemAt(0)).contains("Cabang")) {
                return combo;
            }
        }
        return null;
    }

    private static Component findByName(Container root, String name) {
        for (Component component : allComponents(root)) {
            if (name.equals(component.getName())) {
                return component;
            }
        }
        return null;
    }

    private static List<Component> allComponents(Container root) {
        List<Component> result = new ArrayList<>();
        collect(root, result);
        return result;
    }

    private static void collect(Container root, List<Component> result) {
        for (Component component : root.getComponents()) {
            result.add(component);
            if (component instanceof Container container) {
                collect(container, result);
            }
        }
    }

    private static <T extends Component> List<T> findAll(Container root, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component component : allComponents(root)) {
            if (type.isInstance(component)) {
                result.add(type.cast(component));
            }
        }
        return result;
    }

    private static void click(JButton button) throws Exception {
        SwingUtilities.invokeAndWait(button::doClick);
    }

    private static void clickLater(JButton button) {
        SwingUtilities.invokeLater(button::doClick);
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> callable) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                value.set(callable.call());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return value.get();
    }
}
