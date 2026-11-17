package id.peternakan.service;

import id.peternakan.domain.Money;
import id.peternakan.service.api.ApiServer;
import id.peternakan.service.config.AppConfig;
import id.peternakan.service.db.Db;
import id.peternakan.service.repo.AllocationRepository;
import id.peternakan.service.repo.ExpenseRepository;
import id.peternakan.service.repo.FeedRepository;
import id.peternakan.service.repo.GoatRepository;
import id.peternakan.service.repo.MeatRepository;
import id.peternakan.service.repo.PartyRepository;
import id.peternakan.service.repo.PenRepository;
import id.peternakan.service.repo.ProfitRepository;
import id.peternakan.service.repo.PurchaseRepository;
import id.peternakan.service.repo.ReceiptRepository;
import id.peternakan.service.repo.SaleRepository;
import id.peternakan.service.security.AuthService;
import id.peternakan.service.security.SessionInfo;
import id.peternakan.service.svc.AllocationService;
import id.peternakan.service.svc.ExpenseService;
import id.peternakan.service.svc.FeedService;
import id.peternakan.service.svc.GoatService;
import id.peternakan.service.svc.MeatService;
import id.peternakan.service.svc.PartyService;
import id.peternakan.service.svc.PaymentService;
import id.peternakan.service.svc.PenService;
import id.peternakan.service.svc.PurchaseService;
import id.peternakan.service.svc.ReceiptService;
import id.peternakan.service.svc.SaleService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data contoh terpisah dari data produksi. Hanya dimuat lewat perintah
 * {@code demo} dan berhenti jika database sudah memiliki kambing.
 */
public final class DemoData {
    private final Db db;
    private final AuthService auth;
    private final PenService pens;
    private final PartyService parties;
    private final PurchaseService purchases;
    private final ReceiptService receipts;
    private final SaleService sales;
    private final PaymentService payments;
    private final FeedService feed;
    private final ExpenseService expenses;
    private final AllocationService allocations;
    private final GoatService goats;
    private final MeatService meat;
    private final GoatRepository goatRepo;
    private final AllocationRepository allocationRepo;

    public DemoData(Db db, AppConfig config, AuthService auth) {
        this.db = db;
        this.auth = auth;
        GoatRepository goatRepo = new GoatRepository(db);
        PenRepository penRepo = new PenRepository(db);
        PartyRepository partyRepo = new PartyRepository(db);
        PurchaseRepository purchaseRepo = new PurchaseRepository(db);
        ReceiptRepository receiptRepo = new ReceiptRepository(db);
        SaleRepository saleRepo = new SaleRepository(db);
        FeedRepository feedRepo = new FeedRepository(db);
        ExpenseRepository expenseRepo = new ExpenseRepository(db);
        ProfitRepository profitRepo = new ProfitRepository(db);
        this.goatRepo = goatRepo;
        this.allocationRepo = new AllocationRepository(db, goatRepo, profitRepo);
        this.pens = new PenService(db, penRepo);
        this.parties = new PartyService(db, partyRepo);
        this.purchases = new PurchaseService(db, purchaseRepo);
        this.receipts = new ReceiptService(db, receiptRepo, purchaseRepo, goatRepo, penRepo);
        this.sales = new SaleService(db, saleRepo, profitRepo);
        this.payments = new PaymentService(db, new id.peternakan.service.repo.PaymentRepository(db), saleRepo,
                purchaseRepo);
        this.feed = new FeedService(db, feedRepo, allocationRepo);
        this.expenses = new ExpenseService(db, expenseRepo, allocationRepo);
        this.allocations = new AllocationService(db, allocationRepo);
        this.meat = new MeatService(db, new MeatRepository(db));
        this.goats = new GoatService(db, goatRepo, penRepo, profitRepo,
                new id.peternakan.service.repo.AttachmentRepository(db), config);
    }

    public Map<String, Object> load(SessionInfo owner) {
        long existing = db.count("SELECT COUNT(*) FROM goats");
        if (existing > 0) {
            Map<String, Object> skip = new LinkedHashMap<>();
            skip.put("status", "DILEWATI");
            skip.put("alasan", "Database sudah berisi " + existing + " kambing; data contoh tidak dimuat ulang.");
            return skip;
        }

        LocalDate today = LocalDate.now();
        LocalDate entry = today.minusDays(40);
        LocalDate serviceStart = entry;
        LocalDate serviceEnd = today;

        long penA = asLong(pens.create(owner, Map.of("code", "KD-A", "name", "Kandang A",
                "capacity", 20, "notes", "Kandang penggemukan utama")).get("id"));
        long penQ = asLong(pens.create(owner, Map.of("code", "KD-Q", "name", "Kandang Karantina",
                "capacity", 5, "notes", "Kandang observasi kesehatan")).get("id"));

        long supplier = asLong(parties.create(owner, Map.of("code", "SUP-01", "name", "CV Sumber Ternak",
                "phone", "081200000001", "address", "Bandung", "roles", List.of("SUPPLIER"))).get("id"));
        long customer = asLong(parties.create(owner, Map.of("code", "CUS-01", "name", "Bapak Andi",
                "phone", "081200000002", "address", "Jakarta", "roles", List.of("CUSTOMER"))).get("id"));

        long purchaseId = asLong(purchases.create(owner, Map.of(
                "supplierId", supplier,
                "purchaseDate", entry.toString(),
                "notes", "Pembelian awal data contoh",
                "items", List.of(
                        Map.of("description", "Kambing Boer Jantan", "qty", 1, "unitPrice", 2500000),
                        Map.of("description", "Kambing Boer Jantan", "qty", 1, "unitPrice", 2500000),
                        Map.of("description", "Kambing Boer Betina", "qty", 1, "unitPrice", 2500000)
                ))).get("id"));
        purchases.confirm(owner, purchaseId);

        Map<String, Object> purchase = purchases.find(owner, purchaseId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> purchaseItems = (List<Map<String, Object>>) purchase.get("items");

        List<Map<String, Object>> receiptItems = new ArrayList<>();
        String[] codes = {"K-001", "K-002", "K-003"};
        String[] genders = {"JANTAN", "JANTAN", "BETINA"};
        String[] weights = {"30", "28", "32"};
        for (int i = 0; i < codes.length; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("purchaseItemId", purchaseItems.get(i).get("id"));
            item.put("goatCode", codes[i]);
            item.put("weight", weights[i]);
            item.put("penId", penA);
            item.put("gender", genders[i]);
            item.put("jenis", "Kambing");
            item.put("estimatedAge", 8);
            item.put("physicalMarks", "Telinga menggantung, bulu putih kecokelatan");
            item.put("condition", "SEHAT");
            receiptItems.add(item);
        }
        receipts.create(owner, Map.of(
                "purchaseId", purchaseId,
                "receiptDate", entry.toString(),
                "shippingCost", 0,
                "notes", "Penerimaan data contoh",
                "items", receiptItems), ApiServer.newRequestId());

        long goat1 = goatId("K-001");
        long goat2 = goatId("K-002");
        long goat3 = goatId("K-003");

        goats.addWeight(owner, goat1, Map.of("measuredAt", today.minusDays(10).toString(),
                "weight", 34, "notes", "Penimbangan rutin"));
        goats.addWeight(owner, goat1, Map.of("measuredAt", today.toString(), "weight", 35,
                "notes", "Penimbangan sebelum jual"));
        goats.addWeight(owner, goat2, Map.of("measuredAt", today.toString(), "weight", 29,
                "notes", "Pemantauan kesehatan"));
        goats.addWeight(owner, goat3, Map.of("measuredAt", today.toString(), "weight", 33,
                "notes", "Penimbangan rutin"));

        goats.addHealth(owner, goat2, Map.of("occurredAt", today.minusDays(3).toString(),
                "complaint", "Kembung dan nafsu makan menurun", "examination", "Pemeriksaan klinis",
                "action", "Observasi di kandang karantina", "medication", "Obat kembung",
                "cost", 75000, "responsible", "Petugas Kesehatan", "healthStatus", "SAKIT"));
        goats.move(owner, goat2, Map.of("penId", penQ, "date", today.minusDays(3).toString(),
                "reason", "Karantina karena sakit"));

        long konsentrat = asLong(feed.createItem(owner, Map.of("code", "PKN-01", "name", "Konsentrat",
                "unit", "kg", "minStock", 20)).get("id"));
        feed.receive(owner, Map.of("feedItemId", konsentrat, "movementDate", entry.toString(),
                "qty", 100, "unitCost", 10000,
                "notes", "Pembelian pakan data contoh"), ApiServer.newRequestId());
        feed.usage(owner, Map.of("feedItemId", konsentrat, "movementDate", today.minusDays(2).toString(),
                "qty", 30, "goatId", goat2, "notes", "Pakan kambing sakit"),
                ApiServer.newRequestId());

        long sewaCategory = categoryId("SEWA");
        Map<String, Object> expense = expenses.create(owner, Map.of(
                "categoryId", sewaCategory,
                "expenseDate", entry.toString(),
                "amount", 1200000,
                "purpose", "Sewa kandang bulanan",
                "notes", "Biaya bersama data contoh",
                "allocatable", true,
                "serviceStart", serviceStart.toString(),
                "serviceEnd", serviceEnd.toString(),
                "scopePenId", penA));
        long expenseId = asLong(expense.get("id"));
        expenses.post(owner, expenseId, ApiServer.newRequestId());
        Map<String, Object> source = allocationRepo.findCostSource("EXPENSE", expenseId);
        if (source != null) {
            allocations.finalize(owner, asLong(source.get("id")));
        }

        goats.readiness(owner, goat1, Map.of("readiness", "SIAP_JUAL"));
        long saleId = asLong(sales.saveDraft(owner, null, Map.of(
                "customerId", customer,
                "saleDate", today.toString(),
                "notes", "Penjualan data contoh",
                "items", List.of(Map.of("goatId", goat1, "method", "PER_EKOR",
                        "rate", 3500000)))).get("id"));
        sales.confirm(owner, saleId, ApiServer.newRequestId());
        payments.create(owner, Map.of(
                "sourceType", "SALE",
                "direction", "IN",
                "paymentDate", today.toString(),
                "amount", 2000000,
                "sourceId", saleId,
                "method", "TUNAI",
                "notes", "Pembayaran sebagian data contoh"), ApiServer.newRequestId());


        // Cabang Cikarang: kandang, pembelian, penerimaan, penjualan, pakan, biaya, dan pemotongan.
        long branchCikarang = branchId("CIKARANG");
        long penCk = asLong(pens.create(owner, Map.of("code", "CK-1", "name", "Kandang Cikarang",
                "capacity", 15, "branchId", branchCikarang,
                "notes", "Kandang cabang Cikarang")).get("id"));
        LocalDate ckEntry = today.minusDays(20);
        long ckPurchase = asLong(purchases.create(owner, Map.of(
                "supplierId", supplier,
                "purchaseDate", ckEntry.toString(),
                "branchId", branchCikarang,
                "notes", "Pembelian cabang Cikarang",
                "items", List.of(Map.of("description", "Kambing Cikarang", "qty", 2,
                        "unitPrice", 2300000)))).get("id"));
        purchases.confirm(owner, ckPurchase);
        Map<String, Object> ckDoc = purchases.find(owner, ckPurchase);
        List<Map<String, Object>> ckItems = (List<Map<String, Object>>) ckDoc.get("items");
        long ckItemId = asLong(ckItems.get(0).get("id"));
        receipts.create(owner, Map.of(
                "purchaseId", ckPurchase,
                "receiptDate", ckEntry.toString(),
                "shippingCost", 100000,
                "notes", "Penerimaan cabang Cikarang",
                "items", List.of(
                        Map.of("purchaseItemId", ckItemId, "goatCode", "CK-001", "weight", "27",
                                "penId", penCk, "gender", "JANTAN", "jenis", "Kambing",
                                "condition", "SEHAT"),
                        Map.of("purchaseItemId", ckItemId, "goatCode", "CK-002", "weight", "29",
                                "penId", penCk, "gender", "JANTAN", "jenis", "Kambing",
                                "condition", "SEHAT"))), ApiServer.newRequestId());
        long ck1 = goatId("CK-001");
        long ck2 = goatId("CK-002");
        goats.addWeight(owner, ck1, Map.of("measuredAt", today.toString(), "weight", 30,
                "notes", "Timbang cabang Cikarang"));
        goats.addWeight(owner, ck2, Map.of("measuredAt", today.toString(), "weight", 31,
                "notes", "Timbang cabang Cikarang"));
        goats.readiness(owner, ck1, Map.of("readiness", "SIAP_JUAL"));
        goats.readiness(owner, ck2, Map.of("readiness", "SIAP_JUAL"));

        long ckFeed = asLong(feed.createItem(owner, Map.of("code", "PKN-CK", "name", "Konsentrat Cikarang",
                "unit", "kg", "minStock", 15, "branchId", branchCikarang)).get("id"));
        feed.receive(owner, Map.of("feedItemId", ckFeed, "movementDate", ckEntry.toString(),
                "qty", 80, "unitCost", 10500, "notes", "Pakan cabang Cikarang"),
                ApiServer.newRequestId());
        feed.usage(owner, Map.of("feedItemId", ckFeed, "movementDate", today.minusDays(2).toString(),
                "qty", 20, "goatId", ck2, "notes", "Pakan cabang Cikarang"),
                ApiServer.newRequestId());

        long ckExpenseId = asLong(expenses.create(owner, Map.of(
                "categoryId", categoryId("LISTRIK"),
                "expenseDate", ckEntry.toString(),
                "amount", 450000,
                "branchId", branchCikarang,
                "purpose", "Listrik kandang Cikarang",
                "notes", "Biaya cabang Cikarang")).get("id"));
        expenses.post(owner, ckExpenseId, ApiServer.newRequestId());

        long ckSale = asLong(sales.saveDraft(owner, null, Map.of(
                "customerId", customer,
                "saleDate", today.toString(),
                "branchId", branchCikarang,
                "notes", "Penjualan cabang Cikarang",
                "items", List.of(Map.of("goatId", ck1, "method", "PER_EKOR",
                        "rate", 3400000)))).get("id"));
        sales.confirm(owner, ckSale, ApiServer.newRequestId());
        payments.create(owner, Map.of(
                "sourceType", "SALE",
                "direction", "IN",
                "paymentDate", today.toString(),
                "amount", 2000000,
                "branchId", branchCikarang,
                "sourceId", ckSale,
                "method", "TUNAI",
                "notes", "Pembayaran sebagian cabang Cikarang"), ApiServer.newRequestId());

        Map<String, Object> slaughter = meat.saveDraft(owner, null, Map.of(
                "goatId", ck2,
                "slaughterDate", today.toString(),
                "liveWeight", 31,
                "carcassWeight", 18,
                "meatWeight", 13,
                "boneWeight", 5,
                "processingCost", 100000,
                "notes", "Pemotongan cabang Cikarang"));
        long slaughterId = asLong(slaughter.get("id"));
        Map<String, Object> posted = meat.post(owner, slaughterId, ApiServer.newRequestId());
        long batchId = asLong(posted.get("batchId"));
        db.withTx(tx -> {
            tx.update("UPDATE app_settings SET setting_value = '125000' WHERE setting_key = 'meat.price_per_kg'");
            return null;
        });
        long meatSale = asLong(sales.saveDraft(owner, null, Map.of(
                "customerId", customer,
                "saleDate", today.toString(),
                "branchId", branchCikarang,
                "notes", "Penjualan daging cabang Cikarang",
                "items", List.of(Map.of("itemType", "DAGING", "meatBatchId", batchId,
                        "weight", 4)))).get("id"));
        sales.confirm(owner, meatSale, ApiServer.newRequestId());
        payments.create(owner, Map.of(
                "sourceType", "SALE",
                "direction", "IN",
                "paymentDate", today.toString(),
                "amount", 500000,
                "branchId", branchCikarang,
                "sourceId", meatSale,
                "method", "TRANSFER",
                "notes", "Pembayaran daging cabang Cikarang"), ApiServer.newRequestId());

        createDemoUser(owner, "admin", "Admin#2024", "Admin Operasional", "ADMIN");
        createDemoUser(owner, "petugas", "Petugas#2024", "Petugas Kandang", "PETUGAS");
        createDemoUser(owner, "cikarang", "Cikarang#2024", "Petugas Cikarang", "PETUGAS");
        assignBranch("cikarang", "CIKARANG");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pens", "KD-A, KD-Q (Pusat), CK-1 (Cikarang)");
        summary.put("parties", "CV Sumber Ternak, Bapak Andi");
        summary.put("goats", "Pusat: K-001 (terjual), K-002 (sakit/karantina), K-003 (aktif); "
                + "Cikarang: CK-001 (terjual), CK-002 (dipotong)");
        summary.put("sale", "Pusat: K-001 Rp 3.500.000 dibayar Rp 2.000.000; "
                + "Cikarang: CK-001 Rp 3.400.000 dibayar Rp 2.000.000, daging 4 kg");
        summary.put("expense", "Pusat: sewa Rp 1.200.000 dialokasikan; Cikarang: listrik Rp 450.000");
        summary.put("users", "admin/Admin#2024 (Pusat), petugas/Petugas#2024 (Pusat), "
                + "cikarang/Cikarang#2024 (Cikarang)");
        summary.put("catatan", "Data contoh bukan data produksi.");
        return summary;
    }

    private void createDemoUser(SessionInfo owner, String username, String password, String fullName, String role) {
        if (db.count("SELECT COUNT(*) FROM users WHERE username = ?", username) > 0) {
            return;
        }
        auth.createUser(owner, username, password, fullName, role);
        db.withTx(tx -> {
            tx.update("UPDATE users SET must_change_password = 0 WHERE username = ?", username);
            return null;
        });
    }

    private long goatId(String code) {
        Map<String, Object> row = db.queryOne("SELECT id FROM goats WHERE code = ?", code);
        return asLong(row.get("id"));
    }

    private long branchId(String code) {
        return asLong(db.queryOne("SELECT id FROM branches WHERE code = ?", code).get("id"));
    }

    private void assignBranch(String username, String branchCode) {
        db.withTx(tx -> {
            long userId = asLong(tx.queryOne("SELECT id FROM users WHERE username = ?", username).get("id"));
            long branchId = asLong(tx.queryOne("SELECT id FROM branches WHERE code = ?", branchCode).get("id"));
            tx.update("DELETE FROM user_branches WHERE user_id = ?", userId);
            tx.update("INSERT INTO user_branches (user_id, branch_id) VALUES (?, ?)", userId, branchId);
            return null;
        });
    }

    private long categoryId(String code) {
        return asLong(db.queryOne("SELECT id FROM expense_categories WHERE code = ?", code).get("id"));
    }

    private static long asLong(Object value) {
        return ((Number) value).longValue();
    }

    static BigDecimal money(String value) {
        return Money.of(value);
    }
}
