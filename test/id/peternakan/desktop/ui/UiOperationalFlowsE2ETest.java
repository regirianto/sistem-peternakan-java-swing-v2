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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI flow modul operasional: pakan, pengeluaran, pemotongan, penjualan daging,
 * uang muka, dan reverse pembayaran. Prasyarat kambing siap potong dibuat lewat
 * API agar test fokus pada aksi UI yang diuji.
 */
class UiOperationalFlowsE2ETest {

    @Test
    void pakanPengeluaranPemotonganDagingDanPembayaran() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("E2E_UI")),
                "Set E2E_UI=1 dan jalankan server uji untuk mengaktifkan UI flow E2E.");
        String base = System.getenv().getOrDefault("E2E_BASE_URL", "http://127.0.0.1:8781");
        ApiClient client = new ApiClient(base);
        client.login("owner", "Owner#2024");

        String today = java.time.LocalDate.now().toString();
        long supplier = partyId(client, "SUPPLIER");
        long pen = penId(client, "KD-A");
        long goatK3 = goatId(client, "K-003");
        long slaughterGoat = setupReadyGoat(client, supplier, pen, today, "UI-S1");
        long allocExpense = setupAllocatableExpense(client, pen, today);

        AtomicReference<MainFrame> holder = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame(client);
            frame.setVisible(true);
            holder.set(frame);
        });
        MainFrame main = holder.get();
        try {
            waitFor(() -> branchCombo(main) != null && branchCombo(main).getItemCount() >= 3, 8000);

            // ---------------- Pakan ----------------
            clickLater(button(main, "Pakan"));
            JTable feedItems = waitForTable(main, "feed.items", 1);
            int feedBefore = rowCount(feedItems);
            clickLater(button(main, "Item Baru"));
            JDialog feedDialog = waitForDialog("Item Pakan Baru", 8000);
            setText(feedDialog, "feed.code", "UI-F");
            setText(feedDialog, "feed.name", "Pakan UI");
            setText(feedDialog, "feed.unit", "kg");
            setText(feedDialog, "feed.min", "5");
            clickOptionButton(feedDialog, "OK");
            waitFor(() -> rowCount(feedItems) == feedBefore + 1, 10000);

            clickLater(button(main, "Penerimaan Pakan"));
            JDialog receive = waitForDialog("Penerimaan Pakan", 8000);
            comboByPrefix(receive, "feed.receive.item", "UI-F");
            setText(receive, "feed.receive.qty", "40");
            setText(receive, "feed.receive.cost", "10000");
            clickOptionButton(receive, "OK");
            waitFor(() -> stock(client, "UI-F") >= 40, 10000);

            clickLater(button(main, "Pemakaian Pakan"));
            JDialog usage = waitForDialog("Pemakaian Pakan", 8000);
            comboByPrefix(usage, "feed.usage.item", "UI-F");
            setText(usage, "feed.usage.qty", "5");
            setText(usage, "feed.usage.goat", String.valueOf(goatK3));
            clickOptionButton(usage, "OK");
            waitFor(() -> stock(client, "UI-F") == 35, 10000);

            // ---------------- Pengeluaran ----------------
            clickLater(button(main, "Pengeluaran & Alokasi"));
            JTable expenses = waitForTable(main, "expenses.table", 1);
            int expenseBefore = rowCount(expenses);
            clickLater(button(main, "Pengeluaran Baru"));
            JDialog expense = waitForDialog("Pengeluaran Baru", 8000);
            setText(expense, "expense.amount", "100000");
            setText(expense, "expense.purpose", "Operasional UI E2E");
            clickLater(button(expense, "Simpan Draf"));
            waitFor(() -> rowCount(expenses) == expenseBefore + 1, 10000);
            confirmWithRetry(button(main, "Posting"), expenses, 0);
            waitFor(() -> "POSTED".equals(cell(expenses, 0, 5)), 10000);

            // Alokasi biaya bersama lewat UI (sumber disiapkan API)
            int allocRow = rowWith(expenses, 0, String.valueOf(allocExpense));
            assertTrue(allocRow >= 0, "Pengeluaran alokasi tidak ditemukan");
            selectRow(expenses, allocRow);
            clickLater(button(main, "Alokasi"));
            JDialog allocation = waitForOptionPane(10000);
            assertNotNull(allocation, "Pratinjau alokasi tidak muncul");
            assertEquals("Pratinjau Alokasi", allocation.getTitle());
            clickOptionButton(allocation, "OK");
            pause(1000);

            // ---------------- Pemotongan ----------------
            clickLater(button(main, "Pemotongan"));
            JTable slaughters = waitForTable(main, "slaughter.table", 1);
            int slaughterBefore = rowCount(slaughters);
            clickLater(button(main, "Pemotongan Baru"));
            JDialog slaughter = waitForDialog("Pemotongan Baru", 8000);
            comboByPrefix(slaughter, "slaughter.goat", "UI-S1");
            setText(slaughter, "slaughter.live", "30");
            setText(slaughter, "slaughter.carcass", "18");
            setText(slaughter, "slaughter.meat", "13");
            setText(slaughter, "slaughter.bone", "5");
            setText(slaughter, "slaughter.processing", "50000");
            clickOptionButton(slaughter, "OK");
            dismissOptionPanes();
            waitFor(() -> rowCount(slaughters) == slaughterBefore + 1, 10000);
            confirmWithRetry(button(main, "Posting"), slaughters, 0);
            waitFor(() -> "POSTED".equals(cell(slaughters, 0, 10)), 10000);

            // ---------------- Harga & penjualan daging ----------------
            clickLater(button(main, "Persediaan Daging"));
            JTable batches = waitForTable(main, "meat.batches", 1);
            JTextField price = (JTextField) findByName(main, "meat.price");
            assertNotNull(price);
            setText(main, "meat.price", "150000");
            clickLater(button(main, "Simpan Harga"));
            pause(600);

            clickLater(button(main, "Penjualan"));
            JTable sales = waitForTable(main, "sales.table", 1);
            int salesBefore = rowCount(sales);
            clickLater(button(main, "Jual Daging"));
            JDialog meatSale = waitForDialog("Penjualan Daging", 10000);
            assertNotNull(meatSale, "Dialog penjualan daging tidak muncul");
            comboByPrefix(meatSale, "meatsale.batch", "UI-S1");
            setText(meatSale, "meatsale.qty", "2");
            click(button(meatSale, "Tambah Baris"));
            clickLater(button(meatSale, "Simpan Draft"));
            waitFor(() -> rowCount(sales) == salesBefore + 1, 10000);
            confirmWithRetry(button(main, "Konfirmasi"), sales, 0);
            waitFor(() -> "DIKONFIRMASI".equals(cell(sales, 0, 5)), 10000);
            selectRow(sales, 0);
            clickLater(button(main, "Bayar"));
            JDialog payment = waitForDialog("Pembayaran", 8000);
            assertNotNull(payment, "Dialog bayar daging tidak muncul");
            clickLater(button(payment, "Simpan Pembayaran"));
            waitFor(() -> "LUNAS".equals(cell(sales, 0, 6)), 10000);

            // ---------------- Susut daging ----------------
            String batchNo = batchNo(client, "UI-S1");
            double beforeWaste = batchRemaining(client, batchNo);
            clickLater(button(main, "Persediaan Daging"));
            waitForTable(main, "meat.batches", 1);
            clickLater(button(main, "Catat Susut"));
            JDialog waste = waitForDialog("Catat Susut Daging", 8000);
            assertNotNull(waste, "Dialog susut daging tidak muncul");
            comboByPrefix(waste, "waste.batch", batchNo);
            setText(waste, "waste.qty", "1");
            clickOptionButton(waste, "OK");
            waitFor(() -> batchRemaining(client, batchNo) < beforeWaste, 10000);

            // ---------------- Uang muka + reverse ----------------
            clickLater(button(main, "Pembayaran"));
            JTable payments = waitForTable(main, "payments.table", 1);
            int payBefore = rowCount(payments);
            clickLater(button(main, "Pembayaran Baru"));
            JDialog typeDialog = waitForOptionPane(8000);
            assertNotNull(typeDialog, "Dialog jenis pembayaran tidak muncul");
            JComboBox<?> typeCombo = onlyCombo(typeDialog);
            selectItem(typeCombo, "ADVANCE_CUSTOMER");
            clickOptionButton(typeDialog, "OK");

            JDialog advance = waitForDialog("Uang Muka Pelanggan", 8000);
            assertNotNull(advance, "Dialog uang muka tidak muncul");
            setText(advance, "advance.amount", "100000");
            clickLater(button(advance, "Simpan"));
            waitFor(() -> rowCount(payments) == payBefore + 1, 10000);

            confirmWithRetry(button(main, "Batalkan Pembayaran"), payments, 0);
            JDialog reason = waitForOptionPane(8000);
            assertNotNull(reason, "Dialog alasan pembatalan tidak muncul");
            JTextField reasonField = textFields(reason).isEmpty() ? null : textFields(reason).get(0);
            assertNotNull(reasonField, "Kolom alasan tidak ditemukan");
            SwingUtilities.invokeAndWait(() -> reasonField.setText("UI reverse E2E"));
            clickOptionButton(reason, "OK");
            waitFor(() -> "REVERSED".equals(cell(payments, 0, 8)), 10000);

            assertTrue(batches.getRowCount() >= 1);
            assertEquals("REVERSED", cell(payments, 0, 8));
        } finally {
            SwingUtilities.invokeAndWait(main::dispose);
            client.logout();
        }
    }


    @Test
    void masterDataKambingDanPembayaranPembelian() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("E2E_UI")),
                "Set E2E_UI=1 dan jalankan server uji untuk mengaktifkan UI flow E2E.");
        String base = System.getenv().getOrDefault("E2E_BASE_URL", "http://127.0.0.1:8781");
        ApiClient client = new ApiClient(base);
        client.login("owner", "Owner#2024");
        String today = java.time.LocalDate.now().toString();
        long supplier = partyId(client, "SUPPLIER");
        long pen = penId(client, "KD-A");
        long purchaseId = setupPurchase(client, supplier, pen, today, "UI-PAY1");

        AtomicReference<MainFrame> holder = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame(client);
            frame.setVisible(true);
            holder.set(frame);
        });
        MainFrame main = holder.get();
        try {
            waitFor(() -> branchCombo(main) != null && branchCombo(main).getItemCount() >= 3, 8000);

            // ---------------- Kandang ----------------
            clickLater(button(main, "Kandang"));
            JTable pens = waitForTable(main, "pens.table", 3);
            int penBefore = rowCount(pens);
            clickLater(button(main, "Tambah Kandang"));
            JDialog penDialog = waitForDialog("Tambah Kandang", 8000);
            setText(penDialog, "pen.code", "UI-PEN");
            setText(penDialog, "pen.name", "Kandang UI E2E");
            setText(penDialog, "pen.capacity", "8");
            clickOptionButton(penDialog, "OK");
            waitFor(() -> rowCount(pens) == penBefore + 1, 10000);

            // ---------------- Mitra ----------------
            clickLater(button(main, "Supplier & Pelanggan"));
            JTable parties = waitForTable(main, "parties.table", 1);
            int partyBefore = rowCount(parties);
            clickLater(button(main, "Tambah"));
            JDialog partyDialog = waitForDialog("Tambah Mitra", 8000);
            setText(partyDialog, "party.code", "UI-PARTY");
            setText(partyDialog, "party.name", "Mitra UI E2E");
            setText(partyDialog, "party.phone", "0800000000");
            checkBox(partyDialog, "Supplier");
            clickOptionButton(partyDialog, "OK");
            waitFor(() -> rowCount(parties) == partyBefore + 1, 10000);

            // ---------------- Cabang ----------------
            clickLater(button(main, "Cabang"));
            JTable branches = waitForTable(main, "branches.table", 2);
            int branchBefore = rowCount(branches);
            clickLater(button(main, "Cabang Baru"));
            JDialog branchDialog = waitForDialog("Cabang Baru", 8000);
            setText(branchDialog, "branch.code", "UI-BR");
            setText(branchDialog, "branch.name", "Cabang UI E2E");
            setText(branchDialog, "branch.address", "Lokasi UI");
            setText(branchDialog, "branch.phone", "021000");
            clickOptionButton(branchDialog, "OK");
            waitFor(() -> rowCount(branches) == branchBefore + 1, 10000);

            // ---------------- Pengguna ----------------
            clickLater(button(main, "Pengguna"));
            JTable users = waitForTable(main, "users.table", 1);
            int userBefore = rowCount(users);
            clickLater(button(main, "Pengguna Baru"));
            JDialog userDialog = waitForDialog("Pengguna Baru", 10000);
            setText(userDialog, "user.username", "ui_user");
            setText(userDialog, "user.password", "UiUser#2024");
            setText(userDialog, "user.fullname", "Pengguna UI E2E");
            setText(userDialog, "user.role", "PETUGAS");
            clickOptionButton(userDialog, "OK");
            waitFor(() -> rowCount(users) == userBefore + 1, 10000);

            // ---------------- Operasi kambing K-003 ----------------
            clickLater(button(main, "Data Kambing"));
            JTable goats = waitForTable(main, "goats.table", 1);
            int row = rowWith(goats, 0, "K-003");
            assertTrue(row >= 0, "Kambing K-003 tidak ditemukan");
            selectRow(goats, row);

            clickLater(button(main, "Timbang"));
            JDialog weigh = waitForDialog("Timbang Kambing", 8000);
            setText(weigh, "weigh.value", "31");
            clickOptionButton(weigh, "OK");
            pause(800);

            selectRow(goats, rowWith(goats, 0, "K-003"));
            clickLater(button(main, "Kesehatan"));
            JDialog health = waitForDialog("Catatan Kesehatan", 8000);
            setText(health, "health.complaint", "Pemeriksaan UI E2E");
            setText(health, "health.action", "Observasi");
            setText(health, "health.medication", "Vitamin");
            clickOptionButton(health, "OK");
            pause(800);

            selectRow(goats, rowWith(goats, 0, "K-003"));
            clickLater(button(main, "Pindah Kandang"));
            JDialog move = waitForDialog("Pindah Kandang", 8000);
            comboByPrefix(move, "move.pen", "KD-Q");
            setText(move, "move.reason", "Uji UI E2E");
            clickOptionButton(move, "OK");
            pause(800);

            // ---------------- Bayar utang pembelian ----------------
            clickLater(button(main, "Pembelian & Penerimaan"));
            JTable purchases = waitForTable(main, "purchases.table", 2);
            int purchaseRow = rowWith(purchases, 0, String.valueOf(purchaseId));
            assertTrue(purchaseRow >= 0, "Pembelian UI-PAY1 tidak ditemukan");
            selectRow(purchases, purchaseRow);
            clickLater(button(main, "Bayar Utang"));
            JDialog payment = waitForDialog("Pembayaran", 8000);
            assertNotNull(payment, "Dialog bayar utang tidak muncul");
            clickLater(button(payment, "Simpan Pembayaran"));
            waitFor(() -> remainingPayable(client, purchaseId) == 0, 10000);

            // ---------------- Tutup dan buka periode lewat UI ----------------
            clickLater(button(main, "Pengaturan & Periode"));
            clickLater(button(main, "Tutup Periode"));
            inputDialog(java.time.LocalDate.now().toString().substring(0, 7));
            pause(800);
            clickLater(button(main, "Buka Periode"));
            inputDialog(java.time.LocalDate.now().toString().substring(0, 7));
            inputDialog("Buka kembali untuk E2E");
            pause(800);

            // ---------------- Backup lewat UI ----------------
            clickLater(button(main, "Backup Database"));
            JDialog backup = waitForOptionPane(30000);
            assertNotNull(backup, "Dialog hasil backup tidak muncul");
            clickOptionButton(backup, "OK");
        } finally {
            SwingUtilities.invokeAndWait(main::dispose);
            client.logout();
        }
    }


    @Test
    void jualPerKgPembatalanDanEditPengaturan() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("E2E_UI")),
                "Set E2E_UI=1 dan jalankan server uji untuk mengaktifkan UI flow E2E.");
        String base = System.getenv().getOrDefault("E2E_BASE_URL", "http://127.0.0.1:8781");
        ApiClient client = new ApiClient(base);
        client.login("owner", "Owner#2024");
        String today = java.time.LocalDate.now().toString();
        long supplier = partyId(client, "SUPPLIER");
        long pen = penId(client, "KD-A");
        long perKgGoat = setupReadyGoat(client, supplier, pen, today, "UI-KG1");
        long draftPurchase = setupDraftPurchase(client, supplier, today);
        long slaughterGoat = setupReadyGoat(client, supplier, pen, today, "UI-CANCEL");
        long draftSlaughter = setupDraftSlaughter(client, slaughterGoat, today);

        AtomicReference<MainFrame> holder = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame(client);
            frame.setVisible(true);
            holder.set(frame);
        });
        MainFrame main = holder.get();
        try {
            waitFor(() -> branchCombo(main) != null && branchCombo(main).getItemCount() >= 3, 8000);

            // ---------------- Penjualan per kg lalu batalkan ----------------
            clickLater(button(main, "Penjualan"));
            JTable sales = waitForTable(main, "sales.table", 1);
            int salesBefore = rowCount(sales);
            clickLater(button(main, "Penjualan Kambing"));
            JDialog saleDialog = waitForDialog("Penjualan Baru", 10000);
            comboByPrefix(saleDialog, "sale.goat", "UI-KG1");
            click(button(saleDialog, "Tambah Baris"));
            JTable lines = (JTable) findByName(saleDialog, "sale.table");
            assertNotNull(lines);
            SwingUtilities.invokeAndWait(() -> {
                lines.setValueAt("PER_KG", 0, 1);
                lines.setValueAt("30", 0, 2);
                lines.setValueAt("110000", 0, 3);
            });
            clickLater(button(saleDialog, "Simpan Draft"));
            dismissOptionPanes();
            waitFor(() -> rowCount(sales) == salesBefore + 1, 10000);
            confirmWithRetry(button(main, "Konfirmasi"), sales, 0);
            waitFor(() -> "DIKONFIRMASI".equals(cell(sales, 0, 5)), 10000);
            confirmWithRetry(button(main, "Batalkan"), sales, 0);
            inputDialog("Batal jual per kg UI E2E");
            waitFor(() -> "DIBATALKAN".equals(cell(sales, 0, 5)), 10000);

            // ---------------- Batalkan pembelian draft ----------------
            clickLater(button(main, "Pembelian & Penerimaan"));
            JTable purchases = waitForTable(main, "purchases.table", 1);
            int purchaseRow = rowWith(purchases, 0, String.valueOf(draftPurchase));
            assertTrue(purchaseRow >= 0, "Pembelian draft tidak ditemukan");
            selectRow(purchases, purchaseRow);
            clickLater(button(main, "Batal / Tutup Sisa"));
            JDialog action = waitForOptionPane(8000);
            assertNotNull(action, "Dialog tindakan owner tidak muncul");
            clickOptionChoice(action, "Batal Dokumen");
            inputDialog("Batal beli UI E2E");
            waitFor(() -> "DIBATALKAN".equals(cell(purchases, purchaseRow, 5)), 10000);

            // ---------------- Batalkan pemotongan draft ----------------
            clickLater(button(main, "Pemotongan"));
            JTable slaughters = waitForTable(main, "slaughter.table", 1);
            int slaughterRow = rowWith(slaughters, 0, String.valueOf(draftSlaughter));
            assertTrue(slaughterRow >= 0, "Pemotongan draft tidak ditemukan");
            confirmWithRetry(button(main, "Batalkan"), slaughters, slaughterRow);
            inputDialog("Batal potong UI E2E");
            waitFor(() -> "DIBATALKAN".equals(cell(slaughters, slaughterRow, 10)), 10000);

            // ---------------- Edit pengaturan usaha ----------------
            clickLater(button(main, "Pengaturan & Periode"));
            clickLater(button(main, "Edit Pengaturan"));
            JDialog settings = waitForOptionPane(10000);
            assertNotNull(settings, "Dialog edit pengaturan tidak muncul");
            setText(settings, "setting.company.phone", "021-UI-E2E");
            clickOptionButton(settings, "OK");
            waitFor(() -> "021-UI-E2E".equals(client.get("/api/public/info", Map.of()).get("companyPhone")),
                    10000);
        } finally {
            SwingUtilities.invokeAndWait(main::dispose);
            client.logout();
        }
    }


    @Test
    void kematianRefundDanTutupSisa() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("E2E_UI")),
                "Set E2E_UI=1 dan jalankan server uji untuk mengaktifkan UI flow E2E.");
        String base = System.getenv().getOrDefault("E2E_BASE_URL", "http://127.0.0.1:8781");
        ApiClient client = new ApiClient(base);
        client.login("owner", "Owner#2024");
        String today = java.time.LocalDate.now().toString();
        long supplier = partyId(client, "SUPPLIER");
        long pen = penId(client, "KD-A");
        setupReadyGoat(client, supplier, pen, today, "UI-DEAD");
        long closePurchase = setupConfirmedPurchase(client, supplier, today);

        AtomicReference<MainFrame> holder = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame(client);
            frame.setVisible(true);
            holder.set(frame);
        });
        MainFrame main = holder.get();
        try {
            waitFor(() -> branchCombo(main) != null && branchCombo(main).getItemCount() >= 3, 8000);

            // ---------------- Kematian kambing ----------------
            clickLater(button(main, "Data Kambing"));
            JTable goats = waitForTable(main, "goats.table", 1);
            int row = rowWith(goats, 0, "UI-DEAD");
            assertTrue(row >= 0, "Kambing UI-DEAD tidak ditemukan");
            confirmWithRetry(button(main, "Kematian"), goats, row);
            JDialog death = waitForDialog("Konfirmasi Kematian", 8000);
            assertNotNull(death, "Dialog kematian tidak muncul");
            setText(death, "death.reason", "Uji kematian UI E2E");
            clickOptionButton(death, "OK");
            pause(800);
            int deadRow = rowWith(goats, 0, "UI-DEAD");
            assertTrue(deadRow >= 0, "Kambing UI-DEAD hilang dari tabel");
            waitFor(() -> "MATI".equals(cell(goats, deadRow, 7)), 10000);

            // ---------------- Refund ----------------
            clickLater(button(main, "Pembayaran"));
            JTable payments = waitForTable(main, "payments.table", 1);
            int payBefore = rowCount(payments);
            clickLater(button(main, "Pembayaran Baru"));
            JDialog typeDialog = waitForOptionPane(8000);
            assertNotNull(typeDialog, "Dialog jenis pembayaran tidak muncul");
            selectItem(onlyCombo(typeDialog), "REFUND");
            clickOptionButton(typeDialog, "OK");
            JDialog refund = waitForDialog("Refund", 8000);
            assertNotNull(refund, "Dialog refund tidak muncul");
            setText(refund, "advance.amount", "50000");
            clickLater(button(refund, "Simpan"));
            waitFor(() -> rowCount(payments) == payBefore + 1, 10000);

            // ---------------- Tutup sisa pembelian ----------------
            clickLater(button(main, "Pembelian & Penerimaan"));
            JTable purchases = waitForTable(main, "purchases.table", 1);
            int purchaseRow = rowWith(purchases, 0, String.valueOf(closePurchase));
            assertTrue(purchaseRow >= 0, "Pembelian tutup sisa tidak ditemukan");
            selectRow(purchases, purchaseRow);
            clickLater(button(main, "Batal / Tutup Sisa"));
            JDialog action = waitForOptionPane(8000);
            assertNotNull(action, "Dialog tindakan owner tidak muncul");
            clickOptionChoice(action, "Tutup Sisa Pesanan");
            inputDialog("Tutup sisa UI E2E");
            waitFor(() -> "SELESAI".equals(cell(purchases, purchaseRow, 5)), 10000);
        } finally {
            SwingUtilities.invokeAndWait(main::dispose);
            client.logout();
        }
    }


    @Test
    void terimaKambingMatiTidakMasukHitungan() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("E2E_UI")),
                "Set E2E_UI=1 dan jalankan server uji untuk mengaktifkan UI flow E2E.");
        String base = System.getenv().getOrDefault("E2E_BASE_URL", "http://127.0.0.1:8781");
        ApiClient client = new ApiClient(base);
        client.login("owner", "Owner#2024");
        String today = java.time.LocalDate.now().toString();
        long supplier = partyId(client, "SUPPLIER");
        long purchaseId = setupSinglePurchase(client, supplier, today);

        AtomicReference<MainFrame> holder = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame(client);
            frame.setVisible(true);
            holder.set(frame);
        });
        MainFrame main = holder.get();
        try {
            waitFor(() -> branchCombo(main) != null && branchCombo(main).getItemCount() >= 3, 8000);
            clickLater(button(main, "Pembelian & Penerimaan"));
            JTable purchases = waitForTable(main, "purchases.table", 1);
            int row = rowWith(purchases, 0, String.valueOf(purchaseId));
            assertTrue(row >= 0, "Pembelian kambing mati tidak ditemukan");
            selectRow(purchases, row);
            clickLater(button(main, "Terima Kambing"));
            JDialog receipt = waitForDialog("Penerimaan", 10000);
            assertNotNull(receipt, "Dialog penerimaan tidak muncul");
            comboByPrefix(receipt, "receipt.status", "Mati");
            click(button(receipt, "Tambah Ekor"));
            clickLater(button(receipt, "Simpan Penerimaan"));
            waitFor(() -> "SELESAI".equals(cell(purchases, row, 5)), 10000);
            Map<String, Object> purchase = client.get("/api/purchases/" + purchaseId, Map.of());
            assertEquals(0.0, Double.parseDouble(String.valueOf(purchase.get("received_value"))), 0.001);
            assertEquals(0.0, Double.parseDouble(String.valueOf(purchase.get("remaining_payable"))), 0.001);
        } finally {
            SwingUtilities.invokeAndWait(main::dispose);
            client.logout();
        }
    }

    private static long setupSinglePurchase(ApiClient client, long supplier, String today) {
        Map<String, Object> purchase = client.post("/api/purchases", Map.of(
                "supplierId", supplier,
                "purchaseDate", today,
                "notes", "Kambing mati UI E2E",
                "items", List.of(Map.of("description", "Kambing mati UI", "qty", 1,
                        "unitPrice", "1500000"))));
        long id = ApiClient.lng(purchase.get("id"));
        client.post("/api/purchases/" + id + "/confirm", Map.of());
        return id;
    }

    private static long setupConfirmedPurchase(ApiClient client, long supplier, String today) {
        Map<String, Object> purchase = client.post("/api/purchases", Map.of(
                "supplierId", supplier,
                "purchaseDate", today,
                "notes", "Tutup sisa UI E2E",
                "items", List.of(Map.of("description", "Kambing tutup UI", "qty", 2,
                        "unitPrice", "500000"))));
        long id = ApiClient.lng(purchase.get("id"));
        client.post("/api/purchases/" + id + "/confirm", Map.of());
        return id;
    }


    @Test
    void editMasterDataDanEksporLaporan() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("E2E_UI")),
                "Set E2E_UI=1 dan jalankan server uji untuk mengaktifkan UI flow E2E.");
        String base = System.getenv().getOrDefault("E2E_BASE_URL", "http://127.0.0.1:8781");
        ApiClient client = new ApiClient(base);
        client.login("owner", "Owner#2024");
        String today = java.time.LocalDate.now().toString();
        // Gunakan API setup dengan kode unik.
        long supplier = partyId(client, "SUPPLIER");
        long penId = createPen(client, "UI-EDIT-PEN", 1);
        long partyId = createParty(client, "UI-EDIT-PARTY");
        long editBranch = createBranch(client, "UI-EDIT-BR");
        createUser(client, "ui_edit_user");

        AtomicReference<MainFrame> holder = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame(client);
            frame.setVisible(true);
            holder.set(frame);
        });
        MainFrame main = holder.get();
        try {
            waitFor(() -> branchCombo(main) != null && branchCombo(main).getItemCount() >= 3, 8000);

            // ---------------- Edit kandang ----------------
            clickLater(button(main, "Kandang"));
            JTable pens = waitForTable(main, "pens.table", 1);
            int penRow = rowWith(pens, 0, "UI-EDIT-PEN");
            assertTrue(penRow >= 0);
            selectRow(pens, penRow);
            clickLater(button(main, "Edit"));
            JDialog penDialog = waitForDialog("Edit Kandang", 8000);
            setText(penDialog, "pen.edit.name", "Kandang UI Edit");
            clickOptionButton(penDialog, "OK");
            waitFor(() -> "Kandang UI Edit".equals(cell(pens, penRow, 1)), 10000);

            // ---------------- Edit mitra ----------------
            clickLater(button(main, "Supplier & Pelanggan"));
            JTable parties = waitForTable(main, "parties.table", 1);
            int partyRow = rowWith(parties, 0, "UI-EDIT-PARTY");
            assertTrue(partyRow >= 0);
            selectRow(parties, partyRow);
            clickLater(button(main, "Edit"));
            JDialog partyDialog = waitForDialog("Edit Mitra", 8000);
            setText(partyDialog, "party.edit.name", "Mitra UI Edit");
            clickOptionButton(partyDialog, "OK");
            waitFor(() -> "Mitra UI Edit".equals(cell(parties, partyRow, 1)), 10000);

            // ---------------- Edit cabang ----------------
            clickLater(button(main, "Cabang"));
            JTable branches = waitForTable(main, "branches.table", 2);
            int branchRow = rowWith(branches, 0, "UI-EDIT-BR");
            assertTrue(branchRow >= 0);
            selectRow(branches, branchRow);
            clickLater(button(main, "Edit"));
            JDialog branchDialog = waitForDialog("Edit Cabang", 8000);
            setText(branchDialog, "branch.edit.name", "Cabang UI Edit");
            clickOptionButton(branchDialog, "OK");
            waitFor(() -> "Cabang UI Edit".equals(cell(branches, branchRow, 1)), 10000);

            // ---------------- Edit pengguna ----------------
            clickLater(button(main, "Pengguna"));
            JTable users = waitForTable(main, "users.table", 1);
            int userRow = rowWith(users, 0, "ui_edit_user");
            assertTrue(userRow >= 0);
            selectRow(users, userRow);
            clickLater(button(main, "Edit"));
            JDialog userDialog = waitForDialog("Edit Pengguna", 10000);
            setText(userDialog, "user.edit.fullname", "Pengguna UI Edit");
            clickOptionButton(userDialog, "OK");
            waitFor(() -> "Pengguna UI Edit".equals(cell(users, userRow, 1)), 10000);

            // ---------------- Ekspor laporan CSV ----------------
            java.nio.file.Path out = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),
                    "e2e-ui-report.csv");
            java.nio.file.Files.deleteIfExists(out);
            clickLater(button(main, "Laporan"));
            waitFor(() -> findByName(main, "reports.picker") != null, 8000);
            clickLater(button(main, "Ekspor CSV"));
            saveFile(out);
            waitFor(() -> java.nio.file.Files.exists(out) && size(out) > 0, 15000);
            dismissOptionPanes();
            assertTrue(size(out) > 0, "File CSV laporan kosong");
        } finally {
            SwingUtilities.invokeAndWait(main::dispose);
            client.logout();
        }
    }

    private static long createPen(ApiClient client, String code, long branch) {
        return ApiClient.lng(client.post("/api/pens", Map.of(
                "code", code, "name", "Kandang " + code, "capacity", 5, "branchId", branch)).get("id"));
    }

    private static long createParty(ApiClient client, String code) {
        return ApiClient.lng(client.post("/api/parties", Map.of(
                "code", code, "name", "Mitra " + code, "roles", List.of("SUPPLIER"))).get("id"));
    }

    private static long createBranch(ApiClient client, String code) {
        return ApiClient.lng(client.post("/api/branches", Map.of(
                "code", code, "name", "Cabang " + code, "type", "CABANG")).get("id"));
    }

    private static void createUser(ApiClient client, String username) {
        client.post("/api/users", Map.of(
                "username", username, "password", "UiEdit#2024",
                "fullName", "Pengguna " + username, "roleCode", "PETUGAS"));
    }

    private static long size(java.nio.file.Path path) {
        try {
            return java.nio.file.Files.size(path);
        } catch (Exception e) {
            return -1;
        }
    }

    private static void saveFile(java.nio.file.Path out) throws Exception {
        Window window = waitForWindow(w -> w instanceof JDialog dialog && dialog.isShowing()
                && !findAll(dialog, javax.swing.JFileChooser.class).isEmpty(), 15000);
        assertNotNull(window, "Dialog simpan file tidak muncul");
        JDialog dialog = (JDialog) window;
        javax.swing.JFileChooser chooser = findAll(dialog, javax.swing.JFileChooser.class).get(0);
        SwingUtilities.invokeAndWait(() -> {
            chooser.setSelectedFile(out.toFile());
            chooser.approveSelection();
        });
    }

    private static long setupDraftPurchase(ApiClient client, long supplier, String today) {
        Map<String, Object> purchase = client.post("/api/purchases", Map.of(
                "supplierId", supplier,
                "purchaseDate", today,
                "notes", "Draft batal UI E2E",
                "items", List.of(Map.of("description", "Kambing batal UI", "qty", 1,
                        "unitPrice", "500000"))));
        return ApiClient.lng(purchase.get("id"));
    }

    private static long setupDraftSlaughter(ApiClient client, long goat, String today) {
        Map<String, Object> draft = client.post("/api/slaughter", Map.of(
                "goatId", goat,
                "slaughterDate", today,
                "liveWeight", "30",
                "carcassWeight", "18",
                "meatWeight", "13",
                "boneWeight", "5",
                "processingCost", "0",
                "notes", "Draft batal UI E2E"));
        return ApiClient.lng(draft.get("id"));
    }

    private static void clickOptionChoice(Container dialog, String text) throws Exception {
        JButton choice = button(dialog, text);
        assertNotNull(choice, "Pilihan dialog tidak ditemukan: " + text);
        click(choice);
    }

    private static long setupPurchase(ApiClient client, long supplier, long pen, String today, String code) {
        Map<String, Object> purchase = client.post("/api/purchases", Map.of(
                "supplierId", supplier,
                "purchaseDate", today,
                "notes", "Setup pembayaran UI E2E",
                "items", List.of(Map.of("description", "Kambing bayar UI", "qty", 1,
                        "unitPrice", "1000000"))));
        long purchaseId = ApiClient.lng(purchase.get("id"));
        client.post("/api/purchases/" + purchaseId + "/confirm", Map.of());
        Map<String, Object> detail = client.get("/api/purchases/" + purchaseId, Map.of());
        List<Map<String, Object>> items = ApiClient.list(detail.get("items"));
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("purchaseItemId", ApiClient.lng(items.get(0).get("id")));
        line.put("goatCode", code);
        line.put("weight", "28");
        line.put("penId", pen);
        line.put("gender", "JANTAN");
        line.put("condition", "SEHAT");
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("purchaseId", purchaseId);
        receipt.put("receiptDate", today);
        receipt.put("requestId", UUID.randomUUID().toString());
        receipt.put("items", List.of(line));
        client.post("/api/receipts", receipt);
        return purchaseId;
    }

    private static double remainingPayable(ApiClient client, long purchaseId) {
        Map<String, Object> purchase = client.get("/api/purchases/" + purchaseId, Map.of());
        return Double.parseDouble(String.valueOf(purchase.get("remaining_payable")));
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

    private static void inputDialog(String value) throws Exception {
        JDialog dialog = waitForOptionPane(8000);
        assertNotNull(dialog, "Dialog input tidak muncul");
        JTextField field = textFields(dialog).isEmpty() ? null : textFields(dialog).get(0);
        assertNotNull(field, "Kolom input tidak ditemukan");
        SwingUtilities.invokeAndWait(() -> field.setText(value));
        clickOptionButton(dialog, "OK");
        waitFor(() -> !dialog.isShowing(), 5000);
    }

    private static void checkBox(Container root, String text) throws Exception {
        for (javax.swing.JCheckBox box : findAll(root, javax.swing.JCheckBox.class)) {
            if (text.equals(box.getText())) {
                SwingUtilities.invokeAndWait(() -> box.setSelected(true));
                return;
            }
        }
        throw new IllegalStateException("Checkbox tidak ditemukan: " + text);
    }

    private static long setupAllocatableExpense(ApiClient client, long pen, String today) {
        List<Map<String, Object>> categories = ApiClient.list(
                client.get("/api/expenses/categories", Map.of()).get("items"));
        long category = -1;
        for (Map<String, Object> row : categories) {
            if ("LISTRIK".equals(ApiClient.str(row.get("code")))) {
                category = ApiClient.lng(row.get("id"));
            }
        }
        java.time.LocalDate date = java.time.LocalDate.parse(today);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", category);
        body.put("expenseDate", date.minusDays(5).toString());
        body.put("amount", "50000");
        body.put("purpose", "Biaya bersama UI E2E");
        body.put("allocatable", true);
        body.put("serviceStart", date.minusDays(5).toString());
        body.put("serviceEnd", today);
        body.put("scopePenId", pen);
        long id = ApiClient.lng(client.post("/api/expenses", body).get("id"));
        client.post("/api/expenses/" + id + "/post", Map.of("requestId", UUID.randomUUID().toString()));
        return id;
    }

    private static long setupReadyGoat(ApiClient client, long supplier, long pen, String today, String code) {
        Map<String, Object> purchase = client.post("/api/purchases", Map.of(
                "supplierId", supplier,
                "purchaseDate", today,
                "notes", "Setup UI E2E",
                "items", List.of(Map.of("description", "Kambing UI E2E", "qty", 1,
                        "unitPrice", "2000000"))));
        long purchaseId = ApiClient.lng(purchase.get("id"));
        client.post("/api/purchases/" + purchaseId + "/confirm", Map.of());
        Map<String, Object> detail = client.get("/api/purchases/" + purchaseId, Map.of());
        List<Map<String, Object>> items = ApiClient.list(detail.get("items"));
        long itemId = ApiClient.lng(items.get(0).get("id"));
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("purchaseItemId", itemId);
        line.put("goatCode", code);
        line.put("weight", "30");
        line.put("penId", pen);
        line.put("gender", "JANTAN");
        line.put("condition", "SEHAT");
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("purchaseId", purchaseId);
        receipt.put("receiptDate", today);
        receipt.put("requestId", UUID.randomUUID().toString());
        receipt.put("items", List.of(line));
        client.post("/api/receipts", receipt);
        long goatId = goatId(client, code);
        client.post("/api/goats/" + goatId + "/readiness", Map.of("readiness", "SIAP_JUAL"));
        return goatId;
    }

    private static long partyId(ApiClient client, String role) {
        List<Map<String, Object>> items = ApiClient.list(
                client.get("/api/parties", Map.of("role", role, "size", "100")).get("items"));
        assertTrue(!items.isEmpty(), "Pihak dengan role " + role + " tidak ditemukan");
        return ApiClient.lng(items.get(0).get("id"));
    }

    private static long penId(ApiClient client, String code) {
        List<Map<String, Object>> items = ApiClient.list(client.get("/api/pens", Map.of()).get("items"));
        for (Map<String, Object> pen : items) {
            if (code.equals(ApiClient.str(pen.get("code")))) {
                return ApiClient.lng(pen.get("id"));
            }
        }
        throw new IllegalStateException("Kandang tidak ditemukan: " + code);
    }

    private static long goatId(ApiClient client, String code) {
        List<Map<String, Object>> items = ApiClient.list(
                client.get("/api/goats", Map.of("q", code, "size", "10")).get("items"));
        for (Map<String, Object> goat : items) {
            if (code.equals(ApiClient.str(goat.get("code")))) {
                return ApiClient.lng(goat.get("id"));
            }
        }
        throw new IllegalStateException("Kambing tidak ditemukan: " + code);
    }

    private static String batchNo(ApiClient client, String goatCode) {
        List<Map<String, Object>> items = ApiClient.list(
                client.get("/api/meat/batches", Map.of("branchId", "1", "open", "all")).get("items"));
        for (Map<String, Object> batch : items) {
            if (goatCode.equals(ApiClient.str(batch.get("goat_code")))) {
                return ApiClient.str(batch.get("batch_no"));
            }
        }
        throw new IllegalStateException("Batch tidak ditemukan untuk " + goatCode);
    }

    private static double batchRemaining(ApiClient client, String batchNo) {
        List<Map<String, Object>> items = ApiClient.list(
                client.get("/api/meat/batches", Map.of("branchId", "1", "open", "all")).get("items"));
        for (Map<String, Object> batch : items) {
            if (batchNo.equals(ApiClient.str(batch.get("batch_no")))) {
                return Double.parseDouble(String.valueOf(batch.get("qty_remaining_kg")));
            }
        }
        return -1;
    }

    private static double stock(ApiClient client, String code) {
        List<Map<String, Object>> items = ApiClient.list(
                client.get("/api/feed/items", Map.of("branchId", "1")).get("items"));
        for (Map<String, Object> item : items) {
            if (code.equals(ApiClient.str(item.get("code")))) {
                return Double.parseDouble(String.valueOf(item.get("stock_qty")));
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- UI helpers

    private static JTable waitForTable(Container root, String name, int minimumRows) throws Exception {
        waitFor(() -> {
            JTable table = (JTable) findByName(root, name);
            return table != null && table.getRowCount() >= minimumRows;
        }, 12000);
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

    private static void selectItem(JComboBox<?> combo, String value) throws Exception {
        int index = -1;
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (value.equals(String.valueOf(combo.getItemAt(i)))) {
                index = i;
                break;
            }
        }
        assertTrue(index >= 0, "Pilihan tidak ditemukan: " + value);
        int selected = index;
        SwingUtilities.invokeAndWait(() -> combo.setSelectedIndex(selected));
    }

    private static JComboBox<?> onlyCombo(Container root) {
        List<JComboBox> combos = findAll(root, JComboBox.class);
        assertTrue(!combos.isEmpty(), "Combo tidak ditemukan");
        return combos.get(0);
    }

    private static List<JTextField> textFields(Container root) {
        return findAll(root, JTextField.class);
    }

    private static void clickOptionButton(Container dialog, String text) throws Exception {
        JButton button = button(dialog, text);
        assertNotNull(button, "Tombol tidak ditemukan: " + text);
        click(button);
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
        throw new IllegalStateException("Dialog konfirmasi tidak muncul");
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

    private static JDialog waitForDialog(String titlePrefix, long timeout) throws Exception {
        Window window = waitForWindow(w -> w instanceof JDialog dialog && dialog.isShowing()
                && dialog.getTitle() != null && dialog.getTitle().startsWith(titlePrefix), timeout);
        return (JDialog) window;
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
}
